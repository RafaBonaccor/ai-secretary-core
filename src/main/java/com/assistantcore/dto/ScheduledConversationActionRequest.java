package com.assistantcore.dto;

public record ScheduledConversationActionRequest(
  String actionType,
  String title,
  String body,
  String scheduledFor
) {}
