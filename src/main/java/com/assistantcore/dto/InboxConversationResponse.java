package com.assistantcore.dto;

import java.time.Instant;
import java.util.UUID;

public record InboxConversationResponse(
  UUID conversationId,
  UUID contactId,
  UUID channelInstanceId,
  String contactName,
  String phoneNumber,
  String remoteJid,
  String status,
  Instant lastMessageAt,
  String lastMessagePreview,
  String lastMessageDirection
) {}
