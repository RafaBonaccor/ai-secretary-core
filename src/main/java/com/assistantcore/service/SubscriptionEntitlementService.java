package com.assistantcore.service;

import com.assistantcore.dto.PlanEntitlementsResponse;
import com.assistantcore.dto.TenantSubscriptionResponse;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.Plan;
import com.assistantcore.model.Subscription;
import com.assistantcore.repository.AIProfileRepository;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.PlanRepository;
import com.assistantcore.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionEntitlementService {

  private final SubscriptionRepository subscriptionRepository;
  private final CalendarConnectionRepository calendarConnectionRepository;
  private final AIProfileRepository aiProfileRepository;
  private final PlanRepository planRepository;

  public SubscriptionEntitlementService(
    SubscriptionRepository subscriptionRepository,
    CalendarConnectionRepository calendarConnectionRepository,
    AIProfileRepository aiProfileRepository,
    PlanRepository planRepository
  ) {
    this.subscriptionRepository = subscriptionRepository;
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.aiProfileRepository = aiProfileRepository;
    this.planRepository = planRepository;
  }

  public TenantSubscriptionResponse getTenantSubscription(UUID tenantId) {
    ResolvedSubscription resolved = resolveSubscription(tenantId);
    return toResponse(resolved);
  }

  public void requireCalendarFeatureForTenant(UUID tenantId) {
    Plan plan = resolveSubscription(tenantId).plan();
    if (!resolveCalendarEnabled(plan)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar integration is not available on the current plan");
    }
  }

  public void requireCalendarFeatureForConnection(UUID connectionId) {
    CalendarConnection connection = calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));

    requireCalendarFeatureForTenant(connection.getTenant().getId());
  }

  public void requireInboxFeatureForTenant(UUID tenantId) {
    Plan plan = resolveSubscription(tenantId).plan();
    if (!plan.isInboxEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inbox is not available on the current plan");
    }
  }

  public void requireCustomPromptFeatureForTenant(UUID tenantId) {
    Plan plan = resolveSubscription(tenantId).plan();
    if (!plan.isCustomPromptEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Custom prompts are not available on the current plan");
    }
  }

  public void requireAiProfileCreationAllowed(UUID tenantId) {
    Plan plan = resolveSubscription(tenantId).plan();
    int maxAiProfiles = safeLimit(plan.getMaxAiProfiles(), 1);
    long existingProfiles = aiProfileRepository.countByTenantId(tenantId);

    if (existingProfiles >= maxAiProfiles) {
      throw new ResponseStatusException(
        HttpStatus.FORBIDDEN,
        "The current plan allows up to " + maxAiProfiles + " AI profile(s)"
      );
    }
  }

  private TenantSubscriptionResponse toResponse(ResolvedSubscription resolved) {
    Subscription subscription = resolved.subscription();
    Plan plan = resolved.plan();

    return new TenantSubscriptionResponse(
      subscription == null ? null : subscription.getId(),
      subscription == null ? "legacy" : subscription.getProvider(),
      subscription == null ? null : subscription.getProviderCustomerId(),
      subscription == null ? "active" : subscription.getStatus(),
      subscription == null ? null : subscription.getPeriodStart(),
      subscription == null ? null : subscription.getPeriodEnd(),
      resolved.billingManaged(),
      plan.getCode(),
      plan.getName(),
      plan.getPriceMonthly(),
      new PlanEntitlementsResponse(
        plan.getMessageLimit(),
        plan.getAudioLimit(),
        plan.getAutomationLimit(),
        plan.getMaxWhatsappNumbers(),
        plan.getMaxAiProfiles(),
        plan.getMaxTeamMembers(),
        resolveCalendarEnabled(plan),
        plan.isInboxEnabled(),
        plan.isCustomPromptEnabled(),
        plan.isAdvancedAutomationEnabled(),
        plan.isRealtimeVoiceEnabled(),
        plan.isFutureFeaturesEnabled(),
        plan.isPrioritySupportEnabled()
      )
    );
  }

  private ResolvedSubscription resolveSubscription(UUID tenantId) {
    return subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
      .map(subscription -> new ResolvedSubscription(subscription, resolveEffectivePlan(subscription), isBillingManaged(subscription)))
      .orElseGet(() -> new ResolvedSubscription(null, resolveFreeFallbackPlan(), false));
  }

  private boolean isBillingManaged(Subscription subscription) {
    if (subscription == null || subscription.getProvider() == null) {
      return false;
    }

    return !"mock".equalsIgnoreCase(subscription.getProvider());
  }

  private Plan resolveFreeFallbackPlan() {
    return planRepository.findByCode("trial").orElseGet(this::buildFreeFallbackPlan);
  }

  private Plan resolveEffectivePlan(Subscription subscription) {
    if (subscription == null) {
      return resolveFreeFallbackPlan();
    }

    if ("mock".equalsIgnoreCase(subscription.getProvider())) {
      Plan subscriptionPlan = subscription.getPlan();
      String planCode = subscriptionPlan == null || subscriptionPlan.getCode() == null
        ? ""
        : subscriptionPlan.getCode().trim().toLowerCase();

      if (!"trial".equals(planCode)) {
        return resolveFreeFallbackPlan();
      }
    }

    return subscription.getPlan() == null ? resolveFreeFallbackPlan() : subscription.getPlan();
  }

  private Plan buildFreeFallbackPlan() {
    Plan plan = new Plan();
    plan.setId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    plan.setCode("trial");
    plan.setName("Free");
    plan.setPriceMonthly(BigDecimal.ZERO);
    plan.setMessageLimit(500);
    plan.setAudioLimit(0);
    plan.setAutomationLimit(0);
    plan.setMaxWhatsappNumbers(1);
    plan.setMaxAiProfiles(1);
    plan.setMaxTeamMembers(1);
    plan.setCalendarEnabled(false);
    plan.setInboxEnabled(true);
    plan.setCustomPromptEnabled(true);
    plan.setAdvancedAutomationEnabled(false);
    plan.setRealtimeVoiceEnabled(false);
    plan.setFutureFeaturesEnabled(false);
    plan.setPrioritySupportEnabled(false);
    plan.setCreatedAt(Instant.EPOCH);
    plan.setUpdatedAt(Instant.EPOCH);
    return plan;
  }

  private int safeLimit(Integer value, int fallback) {
    if (value == null || value <= 0) {
      return fallback;
    }

    return value;
  }

  private boolean resolveCalendarEnabled(Plan plan) {
    String code = plan.getCode() == null ? "" : plan.getCode().trim().toLowerCase();

    return switch (code) {
      case "trial" -> false;
      case "starter" -> true;
      default -> plan.isCalendarEnabled();
    };
  }

  private record ResolvedSubscription(Subscription subscription, Plan plan, boolean billingManaged) {}
}
