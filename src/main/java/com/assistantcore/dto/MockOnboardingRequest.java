package com.assistantcore.dto;

import jakarta.validation.constraints.NotBlank;

public record MockOnboardingRequest(
  @NotBlank String businessName,
  String phoneNumber,
  String ownerName,
  String ownerSupabaseUserId,
  String ownerEmail,
  String ownerFullName,
  String planCode,
  String timezone,
  String businessType,
  String brandName,
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
  Boolean autoCreateInstance,
  Boolean autoConnect
) {}
