package com.assistantcore.service;

import com.assistantcore.model.ChannelInstance;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WhatsAppBusinessGateway implements WhatsAppProviderGateway {

  private final EvolutionInstanceClient evolutionInstanceClient;
  private final String businessAccessToken;

  public WhatsAppBusinessGateway(
    EvolutionInstanceClient evolutionInstanceClient,
    @Value("${app.whatsapp-business.access-token:}") String businessAccessToken
  ) {
    this.evolutionInstanceClient = evolutionInstanceClient;
    this.businessAccessToken = businessAccessToken;
  }

  @Override
  public boolean supports(ChannelInstance channelInstance) {
    String providerType = normalize(channelInstance.getProviderType());
    return providerType.equals("whatsapp_business") || providerType.equals("whatsapp_business_api") || providerType.equals("whatsapp_cloud_api");
  }

  @Override
  public boolean requiresPairing(ChannelInstance channelInstance) {
    return false;
  }

  @Override
  public boolean ensureInstance(ChannelInstance channelInstance) {
    if (evolutionInstanceClient.instanceExists(channelInstance.getInstanceName())) {
      return false;
    }

    String phoneNumberId = channelInstance.getProviderExternalId();
    if (phoneNumberId == null || phoneNumberId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerExternalId is required for WhatsApp Business channels");
    }
    if (businessAccessToken == null || businessAccessToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WHATSAPP_BUSINESS_ACCESS_TOKEN is not configured");
    }

    evolutionInstanceClient.createBusinessInstance(channelInstance.getInstanceName(), phoneNumberId, businessAccessToken);
    return true;
  }

  @Override
  public Map<String, Object> startConnection(ChannelInstance channelInstance) {
    ensureInstance(channelInstance);
    return evolutionInstanceClient.getConnectionState(channelInstance.getInstanceName());
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
