package com.assistantcore.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record EvolutionMessageWebhookRequest(
  String event,
  String instanceName,
  JsonNode data
) {}
