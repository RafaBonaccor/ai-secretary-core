package com.assistantcore.service;

import com.assistantcore.dto.StripeSubscriptionSyncRequest;
import com.assistantcore.model.Plan;
import com.assistantcore.model.Subscription;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.PlanRepository;
import com.assistantcore.repository.SubscriptionRepository;
import com.assistantcore.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BillingSubscriptionSyncService {

  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final TenantRepository tenantRepository;

  public BillingSubscriptionSyncService(
    SubscriptionRepository subscriptionRepository,
    PlanRepository planRepository,
    TenantRepository tenantRepository
  ) {
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.tenantRepository = tenantRepository;
  }

  @Transactional
  public void syncStripeSubscription(StripeSubscriptionSyncRequest request) {
    if (request == null || request.tenantId() == null) {
      throw new IllegalArgumentException("Tenant id is required");
    }
    if (!StringUtils.hasText(request.planCode())) {
      throw new IllegalArgumentException("Plan code is required");
    }
    if (!StringUtils.hasText(request.providerSubscriptionId())) {
      throw new IllegalArgumentException("Provider subscription id is required");
    }
    if (!StringUtils.hasText(request.status())) {
      throw new IllegalArgumentException("Subscription status is required");
    }

    Tenant tenant = tenantRepository.findById(request.tenantId())
      .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + request.tenantId()));

    Plan plan = planRepository.findByCode(request.planCode())
      .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + request.planCode()));

    Instant now = Instant.now();
    Subscription subscription = subscriptionRepository
      .findFirstByProviderSubscriptionIdOrderByCreatedAtDesc(request.providerSubscriptionId())
      .or(() -> subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(request.tenantId()))
      .orElseGet(() -> {
        Subscription created = new Subscription();
        created.setId(UUID.randomUUID());
        created.setCreatedAt(now);
        return created;
      });

    subscription.setTenant(tenant);
    subscription.setPlan(plan);
    subscription.setProvider("stripe");
    subscription.setProviderCustomerId(trimToNull(request.providerCustomerId()));
    subscription.setProviderSubscriptionId(request.providerSubscriptionId().trim());
    subscription.setProviderPriceId(trimToNull(request.providerPriceId()));
    subscription.setStatus(request.status().trim());
    subscription.setPeriodStart(request.periodStart() == null ? now : request.periodStart());
    subscription.setPeriodEnd(request.periodEnd() == null ? now : request.periodEnd());
    subscription.setUpdatedAt(now);

    subscriptionRepository.save(subscription);
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }

    return value.trim();
  }
}
