package com.assistantcore.dto;

import java.time.Instant;
import java.util.UUID;

public record InboxMessageResponse(
  UUID messageId,
  String direction,
  String messageType,
  String body,
  String providerMessageId,
  Instant sentAt
) {}
