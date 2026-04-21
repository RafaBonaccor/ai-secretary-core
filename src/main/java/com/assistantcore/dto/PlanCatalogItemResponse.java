package com.assistantcore.dto;

import java.math.BigDecimal;

public record PlanCatalogItemResponse(
  String code,
  String name,
  BigDecimal priceMonthly,
  PlanEntitlementsResponse entitlements
) {}
