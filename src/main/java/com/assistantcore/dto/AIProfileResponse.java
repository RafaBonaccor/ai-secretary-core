package com.assistantcore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AIProfileResponse(
  UUID id,
  UUID tenantId,
  String name,
  String slug,
  String profileType,
  String businessType,
  String language,
  String model,
  String systemPrompt,
  BigDecimal temperature,
  String voice,
  String welcomeMessage,
  String toolsJson,
  String configJson,
  boolean isDefault,
  boolean active,
  Instant createdAt,
  Instant updatedAt
) {}
