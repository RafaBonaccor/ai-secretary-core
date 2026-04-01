package com.assistantcore.dto;

import java.util.List;

public record AIProfilePresetResponse(
  String key,
  String displayName,
  String profileType,
  String businessType,
  String language,
  String description,
  String suggestedModel,
  String suggestedVoice,
  List<String> tools
) {}
