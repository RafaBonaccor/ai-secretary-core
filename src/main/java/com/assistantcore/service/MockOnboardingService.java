package com.assistantcore.service;

import com.assistantcore.dto.MockOnboardingRequest;
import com.assistantcore.dto.MockOnboardingResponse;
import com.assistantcore.dto.AIProfileResponse;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Plan;
import com.assistantcore.model.Subscription;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.assistantcore.repository.PlanRepository;
import com.assistantcore.repository.SubscriptionRepository;
import com.assistantcore.repository.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MockOnboardingService {

  private final TenantRepository tenantRepository;
  private final PlanRepository planRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ChannelInstanceRepository channelInstanceRepository;
  private final EvolutionInstanceClient evolutionInstanceClient;
  private final AIProfileService aiProfileService;
  private final AppUserService appUserService;
  private final ObjectMapper objectMapper;
  private final PromptSafetyService promptSafetyService;

  public MockOnboardingService(
    TenantRepository tenantRepository,
    PlanRepository planRepository,
    SubscriptionRepository subscriptionRepository,
    ChannelInstanceRepository channelInstanceRepository,
    EvolutionInstanceClient evolutionInstanceClient,
    AIProfileService aiProfileService,
    AppUserService appUserService,
    ObjectMapper objectMapper,
    PromptSafetyService promptSafetyService
  ) {
    this.tenantRepository = tenantRepository;
    this.planRepository = planRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.channelInstanceRepository = channelInstanceRepository;
    this.evolutionInstanceClient = evolutionInstanceClient;
    this.aiProfileService = aiProfileService;
    this.appUserService = appUserService;
    this.objectMapper = objectMapper;
    this.promptSafetyService = promptSafetyService;
  }

  @Transactional
  public MockOnboardingResponse create(MockOnboardingRequest request) {
    String sanitizedBusinessName = sanitizeRequiredContextField("businessName", request.businessName());
    String normalizedPhoneNumber = request.phoneNumber() == null ? "" : request.phoneNumber().replaceAll("\\D", "");
    // New tenants must always start on the free plan. Paid plans are assigned only after Stripe confirmation.
    String planCode = "trial";
    String timezone = sanitizeOptionalContextField("timezone", request.timezone());
    if (timezone == null) {
      timezone = "Europe/Rome";
    }
    String slugBase = slugify(sanitizedBusinessName);
    String slug = ensureUniqueSlug(slugBase);
    boolean autoCreateInstance = request.autoCreateInstance() == null || request.autoCreateInstance();
    boolean autoConnect = request.autoConnect() != null && request.autoConnect();
    Instant now = Instant.now();

    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setName(sanitizedBusinessName);
    tenant.setSlug(slug);
    tenant.setStatus("active");
    tenant.setTimezone(timezone);
    tenant.setBusinessContextJson(buildBusinessContextJson(request, timezone));
    tenant.setCreatedAt(now);
    tenant.setUpdatedAt(now);
    tenantRepository.save(tenant);
    appUserService.ensureTenantOwner(
      tenant.getId(),
      request.ownerSupabaseUserId(),
      request.ownerEmail(),
      request.ownerFullName()
    );

    Plan plan = planRepository.findByCode(planCode).orElseGet(() -> createDefaultPlan(planCode, now));

    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setTenant(tenant);
    subscription.setPlan(plan);
    subscription.setProvider("mock");
    subscription.setProviderSubscriptionId("mock-" + tenant.getId());
    subscription.setStatus("active");
    subscription.setPeriodStart(now);
    subscription.setPeriodEnd(now.plus(30, ChronoUnit.DAYS));
    subscription.setCreatedAt(now);
    subscription.setUpdatedAt(now);
    subscriptionRepository.save(subscription);

    ChannelInstance channelInstance = new ChannelInstance();
    channelInstance.setId(UUID.randomUUID());
    channelInstance.setTenant(tenant);
    channelInstance.setProviderType("evolution_baileys");
    channelInstance.setChannelType("whatsapp");
    channelInstance.setPhoneNumber(normalizedPhoneNumber);
    channelInstance.setDisplayName(request.ownerName() == null || request.ownerName().isBlank() ? request.businessName().trim() : request.ownerName().trim());
    channelInstance.setInstanceName("tenant-" + tenant.getId().toString().substring(0, 8));
    channelInstance.setStatus("pending_connection");
    channelInstance.setCreatedAt(now);
    channelInstance.setUpdatedAt(now);
    channelInstanceRepository.save(channelInstance);

    AIProfileResponse defaultProfile = aiProfileService.ensureDefaultProfileForTenant(tenant.getId(), request.businessType());
    aiProfileService.assignProfileToChannel(channelInstance.getId(), defaultProfile.id());

    boolean evolutionInstanceCreated = false;
    boolean evolutionConnectRequested = false;

    if (autoCreateInstance) {
      boolean instanceExists = evolutionInstanceClient.instanceExists(channelInstance.getInstanceName());
      if (!instanceExists) {
        evolutionInstanceClient.createBaileysInstance(channelInstance.getInstanceName());
        evolutionInstanceCreated = true;
      }
      channelInstance.setStatus(autoConnect ? "connection_requested" : "instance_created");
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }

    if (autoConnect) {
      if (normalizedPhoneNumber.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required when autoConnect is enabled");
      }
      evolutionInstanceClient.connectInstance(channelInstance.getInstanceName(), normalizedPhoneNumber);
      evolutionConnectRequested = true;
      channelInstance.setStatus("connection_requested");
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }

    return new MockOnboardingResponse(
      tenant.getId(),
      subscription.getId(),
      channelInstance.getId(),
      defaultProfile.id(),
      tenant.getName(),
      plan.getCode(),
      normalizedPhoneNumber,
      channelInstance.getInstanceName(),
      defaultProfile.name(),
      evolutionInstanceCreated,
      evolutionConnectRequested,
      channelInstance.getStatus(),
      autoConnect
        ? "Open the pairing flow in evolution-api and finish the WhatsApp connection"
        : "Instance prepared in evolution-api. Next step: request connect/pairing for WhatsApp"
    );
  }

  private Plan createDefaultPlan(String code, Instant now) {
    Plan plan = new Plan();
    plan.setId(UUID.randomUUID());
    plan.setCode(code);
    applyPlanDefaults(plan, code);
    plan.setCreatedAt(now);
    plan.setUpdatedAt(now);
    return planRepository.save(plan);
  }

  private String ensureUniqueSlug(String baseSlug) {
    String candidate = baseSlug;
    int counter = 2;
    while (tenantRepository.findBySlug(candidate).isPresent()) {
      candidate = baseSlug + "-" + counter;
      counter++;
    }
    return candidate;
  }

  private String slugify(String input) {
    String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return slug.isBlank() ? "tenant" : slug;
  }

  private String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
  }

  private void applyPlanDefaults(Plan plan, String rawCode) {
    String code = rawCode == null ? "trial" : rawCode.trim().toLowerCase(Locale.ROOT);

    switch (code) {
      case "trial" -> {
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
      }
      case "starter" -> {
        plan.setName("Starter");
        plan.setPriceMonthly(new BigDecimal("49.00"));
        plan.setMessageLimit(2000);
        plan.setAudioLimit(0);
        plan.setAutomationLimit(1);
        plan.setMaxWhatsappNumbers(1);
        plan.setMaxAiProfiles(1);
        plan.setMaxTeamMembers(1);
        plan.setCalendarEnabled(true);
        plan.setInboxEnabled(true);
        plan.setCustomPromptEnabled(true);
        plan.setAdvancedAutomationEnabled(false);
        plan.setRealtimeVoiceEnabled(false);
        plan.setFutureFeaturesEnabled(false);
        plan.setPrioritySupportEnabled(false);
      }
      case "pro" -> {
        plan.setName("Pro");
        plan.setPriceMonthly(new BigDecimal("99.00"));
        plan.setMessageLimit(8000);
        plan.setAudioLimit(200);
        plan.setAutomationLimit(5);
        plan.setMaxWhatsappNumbers(2);
        plan.setMaxAiProfiles(3);
        plan.setMaxTeamMembers(3);
        plan.setCalendarEnabled(true);
        plan.setInboxEnabled(true);
        plan.setCustomPromptEnabled(true);
        plan.setAdvancedAutomationEnabled(false);
        plan.setRealtimeVoiceEnabled(false);
        plan.setFutureFeaturesEnabled(false);
        plan.setPrioritySupportEnabled(false);
      }
      case "scale" -> {
        plan.setName("Scale");
        plan.setPriceMonthly(new BigDecimal("199.00"));
        plan.setMessageLimit(25000);
        plan.setAudioLimit(1000);
        plan.setAutomationLimit(50);
        plan.setMaxWhatsappNumbers(10);
        plan.setMaxAiProfiles(20);
        plan.setMaxTeamMembers(10);
        plan.setCalendarEnabled(true);
        plan.setInboxEnabled(true);
        plan.setCustomPromptEnabled(true);
        plan.setAdvancedAutomationEnabled(true);
        plan.setRealtimeVoiceEnabled(true);
        plan.setFutureFeaturesEnabled(true);
        plan.setPrioritySupportEnabled(true);
      }
      default -> {
        plan.setName(capitalize(code));
        plan.setPriceMonthly(new BigDecimal("49.00"));
        plan.setMessageLimit(2000);
        plan.setAudioLimit(0);
        plan.setAutomationLimit(1);
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
      }
    }
  }

  private String buildBusinessContextJson(MockOnboardingRequest request, String timezone) {
    Map<String, Object> context = new LinkedHashMap<>();
    putIfHasText(context, "businessName", sanitizeRequiredContextField("businessName", request.businessName()));
    putIfHasText(context, "brandName", sanitizeOptionalContextField("brandName", request.brandName()));
    putIfHasText(context, "businessType", sanitizeOptionalContextField("businessType", request.businessType()));
    putIfHasText(context, "ownerName", sanitizeOptionalContextField("ownerName", request.ownerName()));
    putIfHasText(context, "city", sanitizeOptionalContextField("city", request.city()));
    putIfHasText(context, "neighborhood", sanitizeOptionalContextField("neighborhood", request.neighborhood()));
    putIfHasText(context, "address", sanitizeOptionalContextField("address", request.address()));
    putIfHasText(context, "workingHours", sanitizeOptionalContextField("workingHours", request.workingHours()));
    putIfHasText(context, "services", sanitizeOptionalContextField("services", request.services()));
    putIfHasText(context, "specialties", sanitizeOptionalContextField("specialties", request.specialties()));
    putIfHasText(context, "targetAudience", sanitizeOptionalContextField("targetAudience", request.targetAudience()));
    putIfHasText(context, "priceNotes", sanitizeOptionalContextField("priceNotes", request.priceNotes()));
    putIfHasText(context, "bookingPolicy", sanitizeOptionalContextField("bookingPolicy", request.bookingPolicy()));
    putIfHasText(context, "cancellationPolicy", sanitizeOptionalContextField("cancellationPolicy", request.cancellationPolicy()));
    putIfHasText(context, "toneOfVoice", sanitizeOptionalContextField("toneOfVoice", request.toneOfVoice()));
    putIfHasText(context, "greetingStyle", sanitizeOptionalContextField("greetingStyle", request.greetingStyle()));
    putIfHasText(context, "instagramHandle", sanitizeOptionalContextField("instagramHandle", request.instagramHandle()));
    putIfHasText(context, "additionalContext", sanitizeOptionalContextField("additionalContext", request.additionalContext()));
    context.put("timezone", timezone);
    promptSafetyService.validateBusinessContextBudget(context);

    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize tenant business context", exception);
    }
  }

  private void putIfHasText(Map<String, Object> target, String key, String value) {
    if (value != null && !value.isBlank()) {
      target.put(key, value.trim());
    }
  }

  private String sanitizeOptionalContextField(String fieldKey, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return promptSafetyService.sanitizeContextField(fieldKey, value);
  }

  private String sanitizeRequiredContextField(String fieldKey, String value) {
    String sanitized = sanitizeOptionalContextField(fieldKey, value);
    if (sanitized == null || sanitized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldKey + " is required");
    }

    return sanitized;
  }
}
