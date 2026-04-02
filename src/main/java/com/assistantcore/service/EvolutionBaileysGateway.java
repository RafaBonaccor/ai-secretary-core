package com.assistantcore.service;

import com.assistantcore.model.ChannelInstance;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvolutionBaileysGateway implements WhatsAppProviderGateway {

  private final EvolutionInstanceClient evolutionInstanceClient;

  public EvolutionBaileysGateway(EvolutionInstanceClient evolutionInstanceClient) {
    this.evolutionInstanceClient = evolutionInstanceClient;
  }

  @Override
  public boolean supports(ChannelInstance channelInstance) {
    String providerType = normalize(channelInstance.getProviderType());
    return providerType.equals("evolution") || providerType.equals("evolution_baileys") || providerType.equals("whatsapp_baileys");
  }

  @Override
  public boolean requiresPairing(ChannelInstance channelInstance) {
    return true;
  }

  @Override
  public boolean ensureInstance(ChannelInstance channelInstance) {
    if (evolutionInstanceClient.instanceExists(channelInstance.getInstanceName())) {
      return false;
    }

    evolutionInstanceClient.createBaileysInstance(channelInstance.getInstanceName());
    return true;
  }

  @Override
  public Map<String, Object> startConnection(ChannelInstance channelInstance) {
    return evolutionInstanceClient.connectInstance(channelInstance.getInstanceName(), channelInstance.getPhoneNumber());
  }

  @Override
  public Map<String, Object> getConnectionState(ChannelInstance channelInstance) {
    return evolutionInstanceClient.getConnectionState(channelInstance.getInstanceName());
  }

  @Override
  public void sendText(ChannelInstance channelInstance, String number, String text) {
    evolutionInstanceClient.sendText(channelInstance.getInstanceName(), number, text);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
