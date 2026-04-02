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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvolutionMessageOrchestrator {

  private final ChannelInstanceRepository channelInstanceRepository;
  private final OpenAIChatClient openAIChatClient;
  private final ConversationService conversationService;
  private final CalendarConfigurationService calendarConfigurationService;
  private final IntentDetectionService intentDetectionService;
  private final WhatsAppProviderRouter whatsAppProviderRouter;
  private final ObjectMapper objectMapper;

  public EvolutionMessageOrchestrator(
    ChannelInstanceRepository channelInstanceRepository,
    OpenAIChatClient openAIChatClient,
    ConversationService conversationService,
    CalendarConfigurationService calendarConfigurationService,
    IntentDetectionService intentDetectionService,
    WhatsAppProviderRouter whatsAppProviderRouter,
    ObjectMapper objectMapper
  ) {
    this.channelInstanceRepository = channelInstanceRepository;
    this.openAIChatClient = openAIChatClient;
    this.conversationService = conversationService;
    this.calendarConfigurationService = calendarConfigurationService;
    this.intentDetectionService = intentDetectionService;
    this.whatsAppProviderRouter = whatsAppProviderRouter;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public WebhookProcessingResult process(EvolutionMessageWebhookRequest request) {
    if (!"MESSAGES_UPSERT".equals(normalizeEventName(request.event()))) {
      return WebhookProcessingResult.ignored();
    }

    String resolvedInstanceName = request.resolvedInstanceName();
    if (resolvedInstanceName == null || resolvedInstanceName.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    ChannelInstance channelInstance = channelInstanceRepository.findByInstanceName(resolvedInstanceName)
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found for instanceName: " + resolvedInstanceName));

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

    String pushName = textOrNull(data.path("pushName"));
    String number = remoteJid.replaceAll("[^0-9]", "");
    if (number.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    String providerMessageId = textOrNull(data.path("key").path("id"));
    String messageType = detectMessageType(data.path("message"));
    var conversationContext = conversationService.registerInboundMessage(
      channelInstance,
      remoteJid,
      number,
      pushName,
      userMessage,
      messageType,
      providerMessageId,
      data,
      java.time.Instant.now()
    );

    IntentDetectionService.IntentResult intentResult = intentDetectionService.detect(userMessage);
    String effectiveSystemPrompt = buildEffectiveSystemPrompt(channelInstance.getTenant(), aiProfile);
    String effectiveUserPrompt = buildEffectiveUserPrompt(
      channelInstance,
      aiProfile,
      conversationContext.conversation().getId(),
      pushName,
      intentResult,
      userMessage
    );
    String reply = openAIChatClient.createReply(effectiveSystemPrompt, effectiveUserPrompt);
    if (reply.isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    try {
      whatsAppProviderRouter.forChannel(channelInstance).sendText(channelInstance, number, reply);
      conversationService.registerOutboundMessage(
        channelInstance,
        conversationContext.contact(),
        conversationContext.conversation(),
        reply,
        "text"
      );
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

  private String normalizeEventName(String value) {
    return nullToEmpty(value).trim().replace('.', '_').replace('-', '_').toUpperCase();
  }

  private String detectMessageType(JsonNode messageNode) {
    if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
      return "unknown";
    }
    if (messageNode.has("conversation")) {
      return "conversation";
    }
    if (messageNode.has("extendedTextMessage")) {
      return "extended_text";
    }
    if (messageNode.has("audioMessage")) {
      return "audio";
    }
    if (messageNode.has("imageMessage")) {
      return "image";
    }
    if (messageNode.has("videoMessage")) {
      return "video";
    }
    if (messageNode.has("documentMessage")) {
      return "document";
    }
    return "unknown";
  }

  private String buildEffectiveSystemPrompt(Tenant tenant, AIProfile aiProfile) {
    List<String> businessContextLines = formatBusinessContext(tenant.getBusinessContextJson());
    String promptBody = aiProfile.getSystemPrompt() == null ? "" : aiProfile.getSystemPrompt().trim();
    String welcomeMessage = aiProfile.getWelcomeMessage() == null ? "" : aiProfile.getWelcomeMessage().trim();

    List<String> sections = new ArrayList<>();
    sections.add("Voce atua como secretaria virtual no WhatsApp para um negocio brasileiro.");
    sections.add("Objetivo principal: atender, qualificar, orientar e converter o cliente para agendamento ou proximo passo.");

    if (!promptBody.isBlank()) {
      sections.add("Identidade e especializacao:\n" + promptBody);
    }

    sections.add(
      """
      Regras operacionais:
      - Responda sempre em portugues do Brasil.
      - So use informacoes presentes no contexto do negocio e na conversa.
      - Nunca invente horarios, disponibilidade, precos, procedimentos ou politicas.
      - Se faltar dado essencial, faca perguntas curtas e objetivas.
      - Priorize avancar a conversa para agendamento, remarcacao, confirmacao ou encaminhamento humano.
      - Em geral faca no maximo 1 ou 2 perguntas por resposta.
      - Mantenha tom humano, educado, profissional e natural de WhatsApp.
      - Evite respostas longas, roboticas, cheias de marketing ou com listas desnecessarias.
      - Se o cliente demonstrar intencao de marcar, colete somente o minimo necessario para o proximo passo.
      - Se nao houver base suficiente para responder algo sensivel, diga isso claramente e ofereca continuidade.
      """
    );

    sections.add(
      """
      Formato de resposta:
      - Respostas curtas, claras e faceis de enviar no WhatsApp.
      - Sem markdown, sem titulos e sem explicacoes internas.
      - So use emoji se o tom do negocio justificar claramente.
      - Nunca mencione prompt, sistema, modelo ou regras internas.
      """
    );

    if (!businessContextLines.isEmpty()) {
      sections.add("Contexto do negocio:\n" + bulletList(businessContextLines));
    }

    if (!welcomeMessage.isBlank()) {
      sections.add("Estilo de saudacao preferido:\n- " + welcomeMessage);
    }

    return String.join("\n\n", sections).trim();
  }

  private String buildEffectiveUserPrompt(
    ChannelInstance channelInstance,
    AIProfile aiProfile,
    java.util.UUID conversationId,
    String pushName,
    IntentDetectionService.IntentResult intentResult,
    String userMessage
  ) {
    List<String> sections = new ArrayList<>();
    sections.add("Canal atual: WhatsApp");
    sections.add("Tipo de perfil: " + safeValue(aiProfile.getProfileType()));
    sections.add("Tipo de negocio: " + safeValue(aiProfile.getBusinessType()));
    sections.add("Intento detectado: " + safeValue(intentResult.intent()));

    if (intentResult.requestedWindow() != null) {
      sections.add("Periodo solicitado pelo cliente: " + intentResult.requestedWindow());
    }

    if (pushName != null && !pushName.isBlank()) {
      sections.add("Nome do cliente: " + pushName.trim());
    }

    sections.add("Instancia do canal: " + safeValue(channelInstance.getInstanceName()));

    CalendarConfigurationService.CalendarPromptContext calendarContext = calendarConfigurationService.buildPromptContext(channelInstance.getTenant().getId());
    sections.add("Status do calendario: " + safeValue(calendarContext.status()));
    if (calendarContext.calendarName() != null && !calendarContext.calendarName().isBlank()) {
      sections.add("Calendario configurado: " + calendarContext.calendarName());
    }
    if (!calendarContext.workingHours().isEmpty()) {
      sections.add(
        "Horario de funcionamento:\n" +
        calendarContext.workingHours().stream().map(line -> "- " + line).collect(Collectors.joining("\n"))
      );
    }
    if (!calendarContext.appointmentTypes().isEmpty()) {
      sections.add(
        "Tipos de atendimento:\n" +
        calendarContext.appointmentTypes().stream().map(line -> "- " + line).collect(Collectors.joining("\n"))
      );
    }

    List<String> transcript = conversationService.recentTranscript(conversationId);
    if (!transcript.isEmpty()) {
      sections.add("Historico recente da conversa:\n" + transcript.stream().map(line -> "- " + line).collect(Collectors.joining("\n")));
    }

    sections.add("Mensagem atual do cliente:\n" + userMessage.trim());

    if (intentResult.needsCalendarCheck()) {
      sections.add(
        """
        Regra adicional para agenda:
        - Se o cliente estiver pedindo disponibilidade, aja como secretaria de agenda.
        - Use o horario de funcionamento e os tipos de atendimento como contexto.
        - Se o calendario nao estiver conectado de verdade, nao confirme disponibilidade real.
        - Quando faltar dado como servico, data, faixa horaria ou duracao, pergunte de forma curta.
        """
      );
    }

    sections.add(
      """
      Tarefa:
      Responda como a secretaria ideal para este negocio. Seja coerente com o contexto,
      conduza para o proximo passo e, se faltar informacao essencial, pergunte somente o necessario.
      """
    );

    return String.join("\n\n", sections).trim();
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
        String value = formatContextValue(entry.getValue());
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

  private String formatContextValue(Object value) {
    if (value instanceof List<?> listValue) {
      return listValue.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).collect(Collectors.joining(", "));
    }

    String normalized = String.valueOf(value).trim();
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      return normalized.substring(1, normalized.length() - 1).replace("\"", "").trim();
    }
    return normalized;
  }

  private String bulletList(List<String> lines) {
    return lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
  }

  private String safeValue(String value) {
    return value == null || value.isBlank() ? "not_informed" : value.trim();
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
