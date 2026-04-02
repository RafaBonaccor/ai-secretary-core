package com.assistantcore.service;

import com.assistantcore.model.ChannelInstance;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppProviderRouter {

  private final List<WhatsAppProviderGateway> gateways;

  public WhatsAppProviderRouter(List<WhatsAppProviderGateway> gateways) {
    this.gateways = gateways;
  }

  public WhatsAppProviderGateway forChannel(ChannelInstance channelInstance) {
    return gateways.stream()
      .filter(gateway -> gateway.supports(channelInstance))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No WhatsApp provider gateway for providerType: " + channelInstance.getProviderType()));
  }
}
