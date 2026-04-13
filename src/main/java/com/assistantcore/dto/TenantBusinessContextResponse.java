package com.assistantcore.dto;

import java.util.UUID;

public record TenantBusinessContextResponse(
  UUID tenantId,
  String businessName,
  String brandName,
  String businessType,
  String ownerName,
  String city,
  String neighborhood,
  String address,
  String workingHours,
  String services,
  String specialties,
  String targetAudience,
  String priceNotes,
  String bookingPolicy,
  String cancellationPolicy,
  String toneOfVoice,
  String greetingStyle,
  String instagramHandle,
  String additionalContext,
  String timezone
) {}
