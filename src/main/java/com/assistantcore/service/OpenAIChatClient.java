package com.assistantcore.service;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAIChatClient {

  private final RestClient restClient;
  private final String model;

  public OpenAIChatClient(
    RestClient.Builder restClientBuilder,
    @Value("${app.openai.base-url:http://host.docker.internal:8000/v1}") String baseUrl,
    @Value("${app.openai.api-key:local-test-key}") String apiKey,
    @Value("${app.openai.model:gpt-5.4}") String model
  ) {
    this.model = model;
    this.restClient =
      restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @SuppressWarnings("unchecked")
  public String createReply(String systemPrompt, String userMessage) {
    Map<String, Object> response = restClient
      .post()
      .uri("/chat/completions")
      .body(
        Map.of(
          "model",
          model,
          "messages",
          List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userMessage)
          ),
          "temperature",
          0.3
        )
      )
      .retrieve()
      .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("OpenAI-compatible response was empty");
    }

    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    if (choices == null || choices.isEmpty()) {
      throw new IllegalStateException("OpenAI-compatible response did not return choices");
    }

    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
    if (message == null || message.get("content") == null) {
      throw new IllegalStateException("OpenAI-compatible response did not return message content");
    }

    return String.valueOf(message.get("content")).trim();
  }
}
