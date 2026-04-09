package com.assistantcore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAIChatClient {

  private static final int MAX_TOOL_ROUNDS = 5;
  private static final Logger log = LoggerFactory.getLogger(OpenAIChatClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String model;

  public OpenAIChatClient(
    RestClient.Builder restClientBuilder,
    ObjectMapper objectMapper,
    @Value("${app.openai.base-url:http://host.docker.internal:8000/v1}") String baseUrl,
    @Value("${app.openai.api-key:local-test-key}") String apiKey,
    @Value("${app.openai.model:gpt-5.4}") String model
  ) {
    this.objectMapper = objectMapper;
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
    Map<String, Object> response = requestCompletion(List.of(message("system", systemPrompt), message("user", userMessage)), List.of());
    return extractMessageContent(extractAssistantMessage(response));
  }

  public String createReplyWithTools(
    String systemPrompt,
    String userMessage,
    List<ToolDefinition> tools,
    ToolExecutor toolExecutor
  ) {
    if (tools == null || tools.isEmpty() || toolExecutor == null) {
      return createReply(systemPrompt, userMessage);
    }

    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(message("system", systemPrompt));
    messages.add(message("user", userMessage));

    List<Map<String, Object>> requestTools = tools.stream().map(this::toRequestTool).toList();

    for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
      Map<String, Object> response = requestCompletion(messages, requestTools);
      Map<String, Object> assistantMessage = extractAssistantMessage(response);
      List<Map<String, Object>> toolCalls = extractToolCalls(assistantMessage);

      if (toolCalls.isEmpty()) {
        return extractMessageContent(assistantMessage);
      }

      messages.add(copyAssistantMessage(assistantMessage, toolCalls));

      for (Map<String, Object> rawToolCall : toolCalls) {
        ToolInvocation toolInvocation = parseToolInvocation(rawToolCall);
        String toolResult;
        try {
          toolResult = toolExecutor.execute(toolInvocation.name(), toolInvocation.arguments());
          if (toolResult == null || toolResult.isBlank()) {
            toolResult = serialize(Map.of("success", true));
          }
        } catch (Exception exception) {
          log.warn(
            "Tool execution failed: toolName={}, arguments={}",
            toolInvocation.name(),
            toolInvocation.arguments(),
            exception
          );
          toolResult =
            serialize(
              Map.of(
                "success",
                false,
                "error",
                exception.getMessage() == null || exception.getMessage().isBlank()
                  ? exception.getClass().getSimpleName()
                  : exception.getMessage()
              )
            );
        }

        Map<String, Object> toolMessage = new LinkedHashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolInvocation.id());
        toolMessage.put("content", toolResult);
        messages.add(toolMessage);
      }
    }

    throw new IllegalStateException("OpenAI-compatible response exceeded maximum tool execution rounds");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> requestCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("messages", messages);
    payload.put("temperature", 0.3);
    if (tools != null && !tools.isEmpty()) {
      payload.put("tools", tools);
      payload.put("tool_choice", "auto");
    }

    Map<String, Object> response = restClient
      .post()
      .uri("/chat/completions")
      .body(payload)
      .retrieve()
      .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("OpenAI-compatible response was empty");
    }

    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    if (choices == null || choices.isEmpty()) {
      throw new IllegalStateException("OpenAI-compatible response did not return choices");
    }

    return response;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractAssistantMessage(Map<String, Object> response) {
    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    if (choices == null || choices.isEmpty()) {
      throw new IllegalStateException("OpenAI-compatible response did not return choices");
    }

    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
    if (message == null) {
      throw new IllegalStateException("OpenAI-compatible response did not return a message");
    }
    return message;
  }

  private Map<String, Object> message(String role, String content) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("role", role);
    item.put("content", content);
    return item;
  }

  private Map<String, Object> toRequestTool(ToolDefinition definition) {
    return Map.of(
      "type",
      "function",
      "function",
      Map.of(
        "name",
        definition.name(),
        "description",
        definition.description(),
        "parameters",
        definition.parameters()
      )
    );
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractToolCalls(Map<String, Object> assistantMessage) {
    Object rawToolCalls = assistantMessage.get("tool_calls");
    if (!(rawToolCalls instanceof List<?> rawList) || rawList.isEmpty()) {
      return List.of();
    }

    List<Map<String, Object>> toolCalls = new ArrayList<>();
    for (Object rawItem : rawList) {
      if (rawItem instanceof Map<?, ?> rawMap) {
        toolCalls.add((Map<String, Object>) rawMap);
      }
    }
    return toolCalls;
  }

  private Map<String, Object> copyAssistantMessage(Map<String, Object> assistantMessage, List<Map<String, Object>> toolCalls) {
    Map<String, Object> copy = new LinkedHashMap<>();
    copy.put("role", "assistant");
    copy.put("content", assistantMessage.get("content"));
    copy.put("tool_calls", toolCalls);
    return copy;
  }

  @SuppressWarnings("unchecked")
  private ToolInvocation parseToolInvocation(Map<String, Object> rawToolCall) {
    String toolCallId = String.valueOf(rawToolCall.get("id"));
    Map<String, Object> function = (Map<String, Object>) rawToolCall.get("function");
    if (function == null || function.get("name") == null) {
      throw new IllegalStateException("OpenAI-compatible tool call did not return a function name");
    }

    String name = String.valueOf(function.get("name"));
    Object rawArguments = function.get("arguments");
    if (rawArguments == null || String.valueOf(rawArguments).isBlank()) {
      return new ToolInvocation(toolCallId, name, Map.of());
    }

    try {
      Map<String, Object> arguments = objectMapper.readValue(
        String.valueOf(rawArguments),
        new TypeReference<Map<String, Object>>() {}
      );
      return new ToolInvocation(toolCallId, name, arguments == null ? Map.of() : arguments);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OpenAI-compatible tool call arguments were not valid JSON", exception);
    }
  }

  private String extractMessageContent(Map<String, Object> message) {
    Object content = message.get("content");
    if (content == null) {
      return "";
    }
    if (content instanceof String stringContent) {
      return stringContent.trim();
    }
    return String.valueOf(content).trim();
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize tool result", exception);
    }
  }

  public record ToolDefinition(String name, String description, Map<String, Object> parameters) {}

  @FunctionalInterface
  public interface ToolExecutor {
    String execute(String toolName, Map<String, Object> arguments);
  }

  private record ToolInvocation(String id, String name, Map<String, Object> arguments) {}
}
