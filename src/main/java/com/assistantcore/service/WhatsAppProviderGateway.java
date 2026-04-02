package com.assistantcore.service;

import com.assistantcore.dto.WhatsAppConnectionResponse;
import com.assistantcore.model.ChannelInstance;
import java.util.Map;

public interface WhatsAppProviderGateway {
  boolean supports(ChannelInstance channelInstance);
  boolean requiresPairing(ChannelInstance channelInstance);
  boolean ensureInstance(ChannelInstance channelInstance);
  Map<String, Object> startConnection(ChannelInstance channelInstance);
  Map<String, Object> getConnectionState(ChannelInstance channelInstance);
  void sendText(ChannelInstance channelInstance, String number, String text);
}
