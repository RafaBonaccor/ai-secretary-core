package com.assistantcore.dto;

import java.util.UUID;

public record MockOnboardingResponse(
  UUID tenantId,
  UUID subscriptionId,
  UUID channelInstanceId,
  UUID aiProfileId,
  String businessName,
  String planCode,
  String normalizedPhoneNumber,
  String instanceName,
  String aiProfileName,
  boolean evolutionInstanceCreated,
  boolean evolutionConnectRequested,
  String channelStatus,
  String nextStep
) {}
