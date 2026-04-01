package com.assistantcore.dto;

import java.util.UUID;

public record MockOnboardingResponse(
  UUID tenantId,
  UUID subscriptionId,
  UUID channelInstanceId,
  String businessName,
  String planCode,
  String normalizedPhoneNumber,
  String instanceName,
  boolean evolutionInstanceCreated,
  boolean evolutionConnectRequested,
  String channelStatus,
  String nextStep
) {}
