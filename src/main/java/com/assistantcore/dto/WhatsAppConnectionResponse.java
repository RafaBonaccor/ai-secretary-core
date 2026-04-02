package com.assistantcore.dto;

import java.util.UUID;

public record WhatsAppConnectionResponse(
  UUID channelInstanceId,
  String instanceName,
  String phoneNumber,
  String state,
  boolean instanceCreated,
  boolean webhookConfigured,
  boolean reconnectRequired,
  String pairingCode,
  String qrCode,
  String qrCodeBase64,
  String nextAction,
  String rawPayload
) {}
