package com.assistantcore.dto;

import java.time.Instant;
import java.util.UUID;

public record StripeSubscriptionSyncRequest(
  UUID tenantId,
  String planCode,
  String providerCustomerId,
  String providerSubscriptionId,
  String providerPriceId,
  String status,
  Instant periodStart,
  Instant periodEnd
) {}
