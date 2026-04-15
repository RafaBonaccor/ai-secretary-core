package com.assistantcore.dto;

import java.time.Instant;
import java.util.UUID;

public record ScheduledConversationActionResponse(
  UUID id,
  UUID conversationId,
  UUID contactId,
  String contactName,
  String phoneNumber,
  String actionType,
  String title,
  String body,
  Instant scheduledFor,
  String status,
  Instant executedAt,
  Instant cancelledAt,
  String errorMessage
) {}
