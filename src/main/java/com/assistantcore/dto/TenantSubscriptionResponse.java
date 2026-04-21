package com.assistantcore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantSubscriptionResponse(
  UUID subscriptionId,
  String provider,
  String providerCustomerId,
  String status,
  Instant periodStart,
  Instant periodEnd,
  boolean billingManaged,
  String planCode,
  String planName,
  BigDecimal priceMonthly,
  PlanEntitlementsResponse entitlements
) {}
