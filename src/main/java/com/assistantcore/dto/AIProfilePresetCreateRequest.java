package com.assistantcore.dto;

import jakarta.validation.constraints.NotBlank;

public record AIProfilePresetCreateRequest(
  @NotBlank String presetKey,
  String name,
  String slug,
  String language,
  String model,
  String voice,
  String welcomeMessage,
  String additionalInstructions,
  Boolean isDefault,
  Boolean active
) {}
