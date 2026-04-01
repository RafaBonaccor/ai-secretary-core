package com.assistantcore.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EvolutionInstanceClient {

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

  public void connectInstance(String instanceName, String phoneNumber) {
    restClient
      .get()
      .uri(uriBuilder ->
        uriBuilder.path("/instance/connect/{instanceName}").queryParam("number", phoneNumber).build(instanceName)
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
