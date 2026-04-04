package com.assistantcore.dto;

import java.util.UUID;

public record TenantMembershipResponse(
  UUID tenantId,
  String tenantName,
  String tenantSlug,
  String role
) {}
