package com.assistantcore.service;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EvolutionInstanceClient {

  private static final Set<String> REQUIRED_WEBHOOK_EVENTS = Set.of("MESSAGES_UPSERT", "QRCODE_UPDATED", "CONNECTION_UPDATE");

  private final RestClient restClient;

  public EvolutionInstanceClient(
    RestClient.Builder restClientBuilder,
    @Value("${app.evolution.base-url}") String baseUrl,
    @Value("${app.evolution.api-key}") String apiKey
  ) {
    this.restClient =
      restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader("apikey", apiKey)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  public boolean instanceExists(String instanceName) {
    try {
      restClient.get().uri("/instance/connectionState/{instanceName}", instanceName).retrieve().toBodilessEntity();
      return true;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return false;
      }
      throw exception;
    }
  }

  public void createInstance(String instanceName) {
    restClient
      .post()
      .uri("/instance/create")
      .body(Map.of("instanceName", instanceName, "integration", "WHATSAPP-BAILEYS", "qrcode", true))
      .retrieve()
      .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> connectInstance(String instanceName, String phoneNumber) {
    Map<String, Object> response = restClient
      .get()
      .uri(uriBuilder ->
        uriBuilder.path("/instance/connect/{instanceName}").queryParam("number", phoneNumber).build(instanceName)
      )
      .retrieve()
      .body(Map.class);

    return response == null ? Map.of() : response;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getConnectionState(String instanceName) {
    Map<String, Object> response = restClient
      .get()
      .uri("/instance/connectionState/{instanceName}", instanceName)
      .retrieve()
      .body(Map.class);

    return response == null ? Map.of() : response;
  }

  public void deleteInstance(String instanceName) {
    restClient.delete().uri("/instance/delete/{instanceName}", instanceName).retrieve().toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getWebhook(String instanceName) {
    try {
      Map<String, Object> response = restClient
        .get()
        .uri("/webhook/find/{instanceName}", instanceName)
        .retrieve()
        .body(Map.class);

      return response == null ? Map.of() : response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return Map.of();
      }
      throw exception;
    }
  }

  public void setMessageWebhook(String instanceName, String webhookUrl) {
    restClient
      .post()
      .uri("/webhook/set/{instanceName}", instanceName)
      .body(
        Map.of(
          "webhook",
          Map.of(
            "enabled",
            true,
            "url",
            webhookUrl,
            "events",
            REQUIRED_WEBHOOK_EVENTS.toArray(String[]::new),
            "byEvents",
            false,
            "base64",
            true
          )
        )
      )
      .retrieve()
      .toBodilessEntity();
  }

  public void sendText(String instanceName, String number, String text) {
    restClient
      .post()
      .uri("/message/sendText/{instanceName}", instanceName)
      .body(Map.of("number", number, "text", text))
      .retrieve()
      .toBodilessEntity();
  }
}
