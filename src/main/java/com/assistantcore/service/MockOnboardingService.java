package com.assistantcore.service;

import com.assistantcore.dto.MockOnboardingRequest;
import com.assistantcore.dto.MockOnboardingResponse;
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

@Service
public class MockOnboardingService {

  private final TenantRepository tenantRepository;
  private final PlanRepository planRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ChannelInstanceRepository channelInstanceRepository;
  private final EvolutionInstanceClient evolutionInstanceClient;
  private final ObjectMapper objectMapper;

  public MockOnboardingService(
    TenantRepository tenantRepository,
    PlanRepository planRepository,
    SubscriptionRepository subscriptionRepository,
    ChannelInstanceRepository channelInstanceRepository,
    EvolutionInstanceClient evolutionInstanceClient,
    ObjectMapper objectMapper
  ) {
    this.tenantRepository = tenantRepository;
    this.planRepository = planRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.channelInstanceRepository = channelInstanceRepository;
    this.evolutionInstanceClient = evolutionInstanceClient;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public MockOnboardingResponse create(MockOnboardingRequest request) {
    String normalizedPhoneNumber = request.phoneNumber().replaceAll("\\D", "");
    String planCode = request.planCode() == null || request.planCode().isBlank() ? "starter" : request.planCode().trim().toLowerCase(Locale.ROOT);
    String timezone = request.timezone() == null || request.timezone().isBlank() ? "Europe/Rome" : request.timezone().trim();
    String slugBase = slugify(request.businessName());
    String slug = ensureUniqueSlug(slugBase);
    boolean autoCreateInstance = request.autoCreateInstance() == null || request.autoCreateInstance();
    boolean autoConnect = request.autoConnect() != null && request.autoConnect();
    Instant now = Instant.now();

    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setName(request.businessName().trim());
    tenant.setSlug(slug);
    tenant.setStatus("active");
    tenant.setTimezone(timezone);
    tenant.setBusinessContextJson(buildBusinessContextJson(request, timezone));
    tenant.setCreatedAt(now);
    tenant.setUpdatedAt(now);
    tenantRepository.save(tenant);

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
    channelInstance.setProviderType("evolution");
    channelInstance.setChannelType("whatsapp");
    channelInstance.setPhoneNumber(normalizedPhoneNumber);
    channelInstance.setDisplayName(request.ownerName() == null || request.ownerName().isBlank() ? request.businessName().trim() : request.ownerName().trim());
    channelInstance.setInstanceName("tenant-" + tenant.getId().toString().substring(0, 8));
    channelInstance.setStatus("pending_connection");
    channelInstance.setCreatedAt(now);
    channelInstance.setUpdatedAt(now);
    channelInstanceRepository.save(channelInstance);

    boolean evolutionInstanceCreated = false;
    boolean evolutionConnectRequested = false;

    if (autoCreateInstance) {
      boolean instanceExists = evolutionInstanceClient.instanceExists(channelInstance.getInstanceName());
      if (!instanceExists) {
        evolutionInstanceClient.createInstance(channelInstance.getInstanceName());
        evolutionInstanceCreated = true;
      }
      channelInstance.setStatus(autoConnect ? "connection_requested" : "instance_created");
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }

    if (autoConnect) {
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
      tenant.getName(),
      plan.getCode(),
      normalizedPhoneNumber,
      channelInstance.getInstanceName(),
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
    plan.setName(capitalize(code));
    plan.setPriceMonthly(BigDecimal.ZERO);
    plan.setMessageLimit(1000);
    plan.setAudioLimit(100);
    plan.setAutomationLimit(10);
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

  private String buildBusinessContextJson(MockOnboardingRequest request, String timezone) {
    Map<String, Object> context = new LinkedHashMap<>();
    putIfHasText(context, "businessName", request.businessName());
    putIfHasText(context, "brandName", request.brandName());
    putIfHasText(context, "businessType", request.businessType());
    putIfHasText(context, "ownerName", request.ownerName());
    putIfHasText(context, "city", request.city());
    putIfHasText(context, "neighborhood", request.neighborhood());
    putIfHasText(context, "address", request.address());
    putIfHasText(context, "workingHours", request.workingHours());
    putIfHasText(context, "services", request.services());
    putIfHasText(context, "specialties", request.specialties());
    putIfHasText(context, "targetAudience", request.targetAudience());
    putIfHasText(context, "priceNotes", request.priceNotes());
    putIfHasText(context, "bookingPolicy", request.bookingPolicy());
    putIfHasText(context, "cancellationPolicy", request.cancellationPolicy());
    putIfHasText(context, "toneOfVoice", request.toneOfVoice());
    putIfHasText(context, "greetingStyle", request.greetingStyle());
    putIfHasText(context, "instagramHandle", request.instagramHandle());
    putIfHasText(context, "additionalContext", request.additionalContext());
    context.put("timezone", timezone);

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
}
