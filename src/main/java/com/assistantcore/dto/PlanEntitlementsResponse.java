package com.assistantcore.dto;

public record PlanEntitlementsResponse(
  Integer messageLimit,
  Integer audioLimit,
  Integer automationLimit,
  Integer maxWhatsappNumbers,
  Integer maxAiProfiles,
  Integer maxTeamMembers,
  boolean calendarEnabled,
  boolean inboxEnabled,
  boolean customPromptEnabled,
  boolean advancedAutomationEnabled,
  boolean realtimeVoiceEnabled,
  boolean futureFeaturesEnabled,
  boolean prioritySupportEnabled
) {}
