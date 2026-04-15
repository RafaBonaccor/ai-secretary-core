package com.assistantcore.service;

import com.assistantcore.dto.TenantBusinessContextResponse;
import com.assistantcore.dto.TenantBusinessContextUpdateRequest;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.TenantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBusinessContextService {

  private final TenantRepository tenantRepository;
  private final ObjectMapper objectMapper;
  private final PromptSafetyService promptSafetyService;

  public TenantBusinessContextService(
    TenantRepository tenantRepository,
    ObjectMapper objectMapper,
    PromptSafetyService promptSafetyService
  ) {
    this.tenantRepository = tenantRepository;
    this.objectMapper = objectMapper;
    this.promptSafetyService = promptSafetyService;
  }

  @Transactional(readOnly = true)
  public TenantBusinessContextResponse getBusinessContext(UUID tenantId) {
    Tenant tenant = getTenant(tenantId);
    return toResponse(tenant, readContext(tenant.getBusinessContextJson()));
  }

  @Transactional
  public TenantBusinessContextResponse updateBusinessContext(UUID tenantId, TenantBusinessContextUpdateRequest request) {
    Tenant tenant = getTenant(tenantId);
    Map<String, Object> context = readContext(tenant.getBusinessContextJson());

    if (hasText(request.businessName())) {
      tenant.setName(request.businessName().trim());
    }
    if (hasText(request.timezone())) {
      tenant.setTimezone(request.timezone().trim());
    }

    mergeField(context, "brandName", request.brandName(), false);
    mergeField(context, "businessType", request.businessType(), false);
    mergeField(context, "ownerName", request.ownerName(), false);
    mergeField(context, "city", request.city(), false);
    mergeField(context, "neighborhood", request.neighborhood(), false);
    mergeField(context, "address", request.address(), false);
    mergeField(context, "workingHours", request.workingHours(), false);
    mergeField(context, "services", request.services(), false);
    mergeField(context, "specialties", request.specialties(), false);
    mergeField(context, "targetAudience", request.targetAudience(), false);
    mergeField(context, "priceNotes", request.priceNotes(), false);
    mergeField(context, "bookingPolicy", request.bookingPolicy(), false);
    mergeField(context, "cancellationPolicy", request.cancellationPolicy(), false);
    mergeField(context, "toneOfVoice", request.toneOfVoice(), false);
    mergeField(context, "greetingStyle", request.greetingStyle(), false);
    mergeField(context, "assistantBehaviorPrompt", request.assistantBehaviorPrompt(), true);
    mergeField(context, "instagramHandle", request.instagramHandle(), false);
    mergeField(context, "additionalContext", request.additionalContext(), false);

    context.put("businessName", tenant.getName());
    context.put("timezone", tenant.getTimezone());

    tenant.setBusinessContextJson(writeContext(context));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);

    return toResponse(tenant, context);
  }

  private Tenant getTenant(UUID tenantId) {
    return tenantRepository.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
  }

  private Map<String, Object> readContext(String businessContextJson) {
    if (businessContextJson == null || businessContextJson.isBlank()) {
      return new LinkedHashMap<>();
    }

    try {
      return objectMapper.readValue(businessContextJson, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ignored) {
      return new LinkedHashMap<>();
    }
  }

  private String writeContext(Map<String, Object> context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize tenant business context", exception);
    }
  }

  private void mergeField(Map<String, Object> context, String key, String value, boolean strictPromptField) {
    if (value == null) {
      return;
    }

    String normalized = strictPromptField
      ? promptSafetyService.sanitizeAssistantBehaviorPrompt(value)
      : promptSafetyService.sanitizeContextField(value);
    if (normalized == null || normalized.isEmpty()) {
      context.remove(key);
      return;
    }

    context.put(key, normalized);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String contextValue(Map<String, Object> context, String key) {
    Object value = context.get(key);
    return value == null ? null : String.valueOf(value).trim();
  }

  private TenantBusinessContextResponse toResponse(Tenant tenant, Map<String, Object> context) {
    return new TenantBusinessContextResponse(
      tenant.getId(),
      tenant.getName(),
      contextValue(context, "brandName"),
      contextValue(context, "businessType"),
      contextValue(context, "ownerName"),
      contextValue(context, "city"),
      contextValue(context, "neighborhood"),
      contextValue(context, "address"),
      contextValue(context, "workingHours"),
      contextValue(context, "services"),
      contextValue(context, "specialties"),
      contextValue(context, "targetAudience"),
      contextValue(context, "priceNotes"),
      contextValue(context, "bookingPolicy"),
      contextValue(context, "cancellationPolicy"),
      contextValue(context, "toneOfVoice"),
      contextValue(context, "greetingStyle"),
      contextValue(context, "assistantBehaviorPrompt"),
      contextValue(context, "instagramHandle"),
      contextValue(context, "additionalContext"),
      tenant.getTimezone()
    );
  }
}
