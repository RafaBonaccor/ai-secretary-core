package com.assistantcore.service;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.model.AIProfile;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvolutionMessageOrchestrator {

  private final ChannelInstanceRepository channelInstanceRepository;
  private final EvolutionInstanceClient evolutionInstanceClient;
  private final OpenAIChatClient openAIChatClient;
  private final ObjectMapper objectMapper;

  public EvolutionMessageOrchestrator(
    ChannelInstanceRepository channelInstanceRepository,
    EvolutionInstanceClient evolutionInstanceClient,
    OpenAIChatClient openAIChatClient,
    ObjectMapper objectMapper
  ) {
    this.channelInstanceRepository = channelInstanceRepository;
    this.evolutionInstanceClient = evolutionInstanceClient;
    this.openAIChatClient = openAIChatClient;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public WebhookProcessingResult process(EvolutionMessageWebhookRequest request) {
    if (!"MESSAGES_UPSERT".equalsIgnoreCase(nullToEmpty(request.event()))) {
      return WebhookProcessingResult.ignored();
    }

    ChannelInstance channelInstance = channelInstanceRepository.findByInstanceName(request.instanceName())
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found for instanceName: " + request.instanceName()));

    AIProfile aiProfile = channelInstance.getAiProfile();
    if (aiProfile == null || !aiProfile.isActive()) {
      return WebhookProcessingResult.ignored();
    }

    JsonNode data = request.data();
    if (data == null) {
      return WebhookProcessingResult.ignored();
    }

    boolean fromMe = data.path("key").path("fromMe").asBoolean(false);
    if (fromMe) {
      return WebhookProcessingResult.ignored();
    }

    String remoteJid = textOrNull(data.path("key").path("remoteJid"));
    if (remoteJid == null || remoteJid.endsWith("@g.us")) {
      return WebhookProcessingResult.ignored();
    }

    String userMessage = extractTextMessage(data.path("message"));
    if (userMessage == null || userMessage.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    String number = remoteJid.replaceAll("[^0-9]", "");
    if (number.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    String effectiveSystemPrompt = buildEffectiveSystemPrompt(channelInstance.getTenant(), aiProfile);
    String reply = openAIChatClient.createReply(effectiveSystemPrompt, userMessage);
    if (reply.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    try {
      evolutionInstanceClient.sendText(channelInstance.getInstanceName(), number, reply);
      return new WebhookProcessingResult(true, reply, null);
    } catch (Exception exception) {
      return new WebhookProcessingResult(false, reply, exception.getMessage());
    }
  }

  private String extractTextMessage(JsonNode messageNode) {
    String conversation = textOrNull(messageNode.path("conversation"));
    if (conversation != null) {
      return conversation;
    }

    String extendedText = textOrNull(messageNode.path("extendedTextMessage").path("text"));
    if (extendedText != null) {
      return extendedText;
    }

    return null;
  }

  private String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String buildEffectiveSystemPrompt(Tenant tenant, AIProfile aiProfile) {
    StringBuilder prompt = new StringBuilder();
    prompt.append(aiProfile.getSystemPrompt().trim());

    List<String> contextLines = formatBusinessContext(tenant.getBusinessContextJson());
    if (!contextLines.isEmpty()) {
      prompt.append("\n\nBusiness context:\n");
      contextLines.forEach(line -> prompt.append("- ").append(line).append('\n'));
    }

    if (aiProfile.getWelcomeMessage() != null && !aiProfile.getWelcomeMessage().isBlank()) {
      prompt.append("\nWelcome message style:\n");
      prompt.append(aiProfile.getWelcomeMessage().trim()).append('\n');
    }

    return prompt.toString().trim();
  }

  @SuppressWarnings("unchecked")
  private List<String> formatBusinessContext(String businessContextJson) {
    List<String> lines = new ArrayList<>();
    if (businessContextJson == null || businessContextJson.isBlank() || "{}".equals(businessContextJson.trim())) {
      return lines;
    }

    try {
      Map<String, Object> context = objectMapper.readValue(businessContextJson, Map.class);
      Iterator<Map.Entry<String, Object>> iterator = context.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        if (entry.getValue() == null) {
          continue;
        }
        String value = String.valueOf(entry.getValue()).trim();
        if (value.isBlank()) {
          continue;
        }
        lines.add(labelFor(entry.getKey()) + ": " + value);
      }
    } catch (Exception ignored) {
      lines.add("raw_context: " + businessContextJson);
    }

    return lines;
  }

  private String labelFor(String key) {
    return switch (key) {
      case "businessName" -> "business_name";
      case "brandName" -> "brand_name";
      case "businessType" -> "business_type";
      case "ownerName" -> "owner_name";
      case "city" -> "city";
      case "neighborhood" -> "neighborhood";
      case "address" -> "address";
      case "workingHours" -> "working_hours";
      case "services" -> "services";
      case "specialties" -> "specialties";
      case "targetAudience" -> "target_audience";
      case "priceNotes" -> "price_notes";
      case "bookingPolicy" -> "booking_policy";
      case "cancellationPolicy" -> "cancellation_policy";
      case "toneOfVoice" -> "tone_of_voice";
      case "greetingStyle" -> "greeting_style";
      case "instagramHandle" -> "instagram_handle";
      case "additionalContext" -> "additional_context";
      case "timezone" -> "timezone";
      default -> key;
    };
  }

  public record WebhookProcessingResult(boolean replySent, String replyPreview, String sendError) {
    public static WebhookProcessingResult ignored() {
      return new WebhookProcessingResult(false, null, null);
    }
  }
}
