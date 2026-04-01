package com.assistantcore.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record AIProfileCreateRequest(
  @NotBlank String name,
  @NotBlank String slug,
  @NotBlank String profileType,
  @NotBlank String businessType,
  @NotBlank String language,
  @NotBlank String model,
  @NotBlank String systemPrompt,
  BigDecimal temperature,
  String voice,
  String welcomeMessage,
  String toolsJson,
  String configJson,
  Boolean isDefault,
  Boolean active
) {}
