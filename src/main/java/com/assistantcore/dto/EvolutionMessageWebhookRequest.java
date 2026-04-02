package com.assistantcore.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record EvolutionMessageWebhookRequest(
  String event,
  String instanceName,
  String instance,
  JsonNode data
) {
  public String resolvedInstanceName() {
    if (instanceName != null && !instanceName.isBlank()) {
      return instanceName;
    }

    if (instance != null && !instance.isBlank()) {
      return instance;
    }

    return null;
  }
}
