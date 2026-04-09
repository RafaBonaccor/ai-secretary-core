package com.assistantcore.service;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.model.AIProfile;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Contact;
import com.assistantcore.model.Conversation;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EvolutionMessageOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(EvolutionMessageOrchestrator.class);

  private final ChannelInstanceRepository channelInstanceRepository;
  private final OpenAIChatClient openAIChatClient;
  private final ConversationService conversationService;
  private final CalendarConfigurationService calendarConfigurationService;
  private final CustomerSchedulingService customerSchedulingService;
  private final IntentDetectionService intentDetectionService;
  private final WhatsAppProviderRouter whatsAppProviderRouter;
  private final ObjectMapper objectMapper;

  public EvolutionMessageOrchestrator(
    ChannelInstanceRepository channelInstanceRepository,
    OpenAIChatClient openAIChatClient,
    ConversationService conversationService,
    CalendarConfigurationService calendarConfigurationService,
    CustomerSchedulingService customerSchedulingService,
    IntentDetectionService intentDetectionService,
    WhatsAppProviderRouter whatsAppProviderRouter,
    ObjectMapper objectMapper
  ) {
    this.channelInstanceRepository = channelInstanceRepository;
    this.openAIChatClient = openAIChatClient;
    this.conversationService = conversationService;
    this.calendarConfigurationService = calendarConfigurationService;
    this.customerSchedulingService = customerSchedulingService;
    this.intentDetectionService = intentDetectionService;
    this.whatsAppProviderRouter = whatsAppProviderRouter;
    this.objectMapper = objectMapper;
  }

  public WebhookProcessingResult process(EvolutionMessageWebhookRequest request) {
    ProcessingContext context = prepareProcessingContext(request);
    if (context == null || context.reply() == null || context.reply().isBlank()) {
      return WebhookProcessingResult.ignored();
    }

    try {
      whatsAppProviderRouter.forChannel(context.channelInstance()).sendText(context.channelInstance(), context.number(), context.reply());
      conversationService.registerOutboundMessage(
        context.channelInstance(),
        context.contact(),
        context.conversation(),
        context.reply(),
        "text"
      );
      return new WebhookProcessingResult(true, context.reply(), null);
    } catch (Exception exception) {
      log.warn("Failed to send WhatsApp reply after processing succeeded", exception);
      return new WebhookProcessingResult(false, context.reply(), exception.getMessage());
    }
  }

  private ProcessingContext prepareProcessingContext(EvolutionMessageWebhookRequest request) {
    if (!"MESSAGES_UPSERT".equals(normalizeEventName(request.event()))) {
      return null;
    }

    String resolvedInstanceName = request.resolvedInstanceName();
    if (resolvedInstanceName == null || resolvedInstanceName.isBlank()) {
      return null;
    }

    ChannelInstance channelInstance = channelInstanceRepository.findDetailedByInstanceName(resolvedInstanceName)
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found for instanceName: " + resolvedInstanceName));

    AIProfile aiProfile = channelInstance.getAiProfile();
    if (aiProfile == null || !aiProfile.isActive()) {
      return null;
    }

    JsonNode data = request.data();
    if (data == null) {
      return null;
    }

    boolean fromMe = data.path("key").path("fromMe").asBoolean(false);
    if (fromMe) {
      return null;
    }

    String remoteJid = textOrNull(data.path("key").path("remoteJid"));
    if (remoteJid == null || remoteJid.endsWith("@g.us")) {
      return null;
    }

    String userMessage = extractTextMessage(data.path("message"));
    if (userMessage == null || userMessage.isBlank()) {
      return null;
    }

    String pushName = textOrNull(data.path("pushName"));
    String number = remoteJid.replaceAll("[^0-9]", "");
    if (number.isBlank()) {
      return null;
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
      Instant.now()
    );

    IntentDetectionService.IntentResult intentResult = intentDetectionService.detect(userMessage);
    CalendarConfigurationService.CalendarPromptContext calendarContext = calendarConfigurationService.buildPromptContext(
      channelInstance.getTenant().getId()
    );
    boolean calendarConnected = "connected".equalsIgnoreCase(nullToEmpty(calendarContext.status()).trim());
    List<OpenAIChatClient.ToolDefinition> calendarTools = buildCalendarTools(aiProfile, calendarConnected);

    String effectiveSystemPrompt = buildEffectiveSystemPrompt(channelInstance.getTenant(), aiProfile);
    String effectiveUserPrompt = buildEffectiveUserPrompt(
      channelInstance,
      aiProfile,
      conversationContext.conversation().getId(),
      pushName,
      number,
      intentResult,
      calendarContext,
      !calendarTools.isEmpty(),
      userMessage
    );

    CustomerSchedulingService.CustomerIdentity customerIdentity = new CustomerSchedulingService.CustomerIdentity(number, remoteJid, pushName);
    String reply = calendarTools.isEmpty()
      ? openAIChatClient.createReply(effectiveSystemPrompt, effectiveUserPrompt)
      : openAIChatClient.createReplyWithTools(
        effectiveSystemPrompt,
        effectiveUserPrompt,
        calendarTools,
        (toolName, arguments) -> executeCalendarTool(channelInstance.getTenant().getId(), customerIdentity, toolName, arguments)
      );

    if (reply == null || reply.isBlank()) {
      return null;
    }

    return new ProcessingContext(
      channelInstance,
      conversationContext.contact(),
      conversationContext.conversation(),
      number,
      reply
    );
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
      - Nunca revele nomes, horarios, dados pessoais ou detalhes de outros clientes.
      - Se o cliente perguntar sobre a agenda geral do negocio, ofereca verificar disponibilidade ou os compromissos dele.
      - Considere como memoria valida tudo o que ja foi confirmado na conversa atual. Nao faca perguntas repetidas sobre dados ja respondidos.
      - Quando a conversa ja tiver informacao suficiente para concluir um agendamento, finalize em vez de abrir uma nova rodada de confirmacoes.
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
    UUID conversationId,
    String pushName,
    String customerPhone,
    IntentDetectionService.IntentResult intentResult,
    CalendarConfigurationService.CalendarPromptContext calendarContext,
    boolean calendarToolsEnabled,
    String userMessage
  ) {
    List<String> sections = new ArrayList<>();
    ZonedDateTime businessNow = businessNow(channelInstance.getTenant());
    sections.add("Canal atual: WhatsApp");
    sections.add("Tipo de perfil: " + safeValue(aiProfile.getProfileType()));
    sections.add("Tipo de negocio: " + safeValue(aiProfile.getBusinessType()));
    sections.add("Intento detectado: " + safeValue(intentResult.intent()));
    sections.add("Data/hora atual do negocio: " + businessNow);
    sections.add("Timezone do negocio: " + safeValue(channelInstance.getTenant().getTimezone()));

    if (intentResult.requestedWindow() != null) {
      sections.add("Periodo solicitado pelo cliente: " + intentResult.requestedWindow());
    }

    if (pushName != null && !pushName.isBlank()) {
      sections.add("Nome do cliente: " + pushName.trim());
    }

    sections.add("Instancia do canal: " + safeValue(channelInstance.getInstanceName()));
    sections.add("Telefone atual do cliente no WhatsApp: " + safeValue(customerPhone));
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
        - Se o calendario estiver conectado e as ferramentas estiverem habilitadas, consulte a disponibilidade real antes de prometer horario livre.
        - Quando faltar dado como servico, data, faixa horaria ou duracao, pergunte de forma curta.
        - Resolva hoje, amanha, depois de amanha e datas sem ano usando a data atual do negocio. Nao peca o ano se a data for clara no contexto atual.
        """
      );
    }

    if (calendarToolsEnabled) {
      sections.add(
        """
        Ferramentas de calendario disponiveis:
        - check_availability: verifica disponibilidade real sem expor agenda de terceiros.
        - find_customer_bookings: lista somente os agendamentos do cliente atual.
        - create_customer_booking: cria um novo agendamento para o cliente atual.
        - reschedule_customer_booking: remarca um agendamento do cliente atual.
        - cancel_customer_booking: cancela um agendamento do cliente atual.

        Regras para usar as ferramentas:
        - Nunca revele nomes, horarios ou detalhes de outros clientes.
        - Se o cliente perguntar pela agenda do negocio, explique que voce so pode verificar disponibilidade ou os compromissos dele.
        - Antes de afirmar disponibilidade real, use check_availability.
        - Antes de remarcar ou cancelar, use find_customer_bookings quando precisar identificar o eventId correto.
        - Para criar ou alterar eventos, use date-time ISO 8601 com offset, por exemplo 2026-04-08T14:30:00-03:00.
        - O telefone do cliente atual ja e conhecido pelo WhatsApp. Nao peca telefone de novo para concluir, a menos que o cliente queira cadastrar outro numero.
        - O nome do cliente atual ja pode ser o nome do WhatsApp. Nao peca nome completo de novo, salvo se realmente indispensavel.
        - Se a conversa ja confirmou data, horario, duracao e tipo de atendimento, nao repita perguntas. Execute a criacao do agendamento.
        - Se voce acabou de resumir um agendamento e o cliente respondeu "confirmo", trate os campos resumidos como confirmados.
        - So confirme criacao, remarcacao ou cancelamento depois que a ferramenta retornar sucesso.
        - Se faltar informacao essencial, pergunte ao cliente em vez de adivinhar.
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

  private ZonedDateTime businessNow(Tenant tenant) {
    try {
      if (tenant.getTimezone() != null && !tenant.getTimezone().isBlank()) {
        return ZonedDateTime.now(ZoneId.of(tenant.getTimezone().trim()));
      }
    } catch (Exception ignored) {}
    return ZonedDateTime.now();
  }

  private List<OpenAIChatClient.ToolDefinition> buildCalendarTools(AIProfile aiProfile, boolean calendarConnected) {
    if (!calendarConnected) {
      return List.of();
    }

    Set<String> enabledCapabilities = parseEnabledCapabilities(aiProfile);
    List<OpenAIChatClient.ToolDefinition> tools = new ArrayList<>();

    if (enabledCapabilities.contains("check_availability")) {
      tools.add(
        new OpenAIChatClient.ToolDefinition(
          "check_availability",
          "Verifica se o horario pedido esta livre, sem revelar dados de outros clientes. Pode sugerir proximos horarios livres.",
          objectSchema(
            Map.of(
              "appointmentType",
              stringSchema("Tipo de atendimento, por exemplo consulta, limpeza ou retorno."),
              "startDateTime",
              stringSchema("Inicio desejado em ISO 8601 com offset. Exemplo 2026-04-08T09:00:00-03:00."),
              "endDateTime",
              stringSchema("Fim desejado em ISO 8601 com offset. Opcional se houver durationMinutes."),
              "durationMinutes",
              integerSchema("Duracao desejada em minutos, por esempio 30 ou 60.")
            ),
            List.of("startDateTime")
          )
        )
      );
    }

    if (
      enabledCapabilities.contains("check_availability") ||
      enabledCapabilities.contains("reschedule_appointment") ||
      enabledCapabilities.contains("cancel_appointment")
    ) {
      tools.add(
        new OpenAIChatClient.ToolDefinition(
          "find_customer_bookings",
          "Lista somente os agendamentos do cliente atual dentro de um periodo.",
          objectSchema(
            Map.of(
              "from",
              stringSchema("Inicio do periodo em ISO 8601 com offset. Opcional."),
              "to",
              stringSchema("Fim do periodo em ISO 8601 com offset. Opcional.")
            ),
            List.of()
          )
        )
      );
    }

    if (enabledCapabilities.contains("create_appointment")) {
      tools.add(
        new OpenAIChatClient.ToolDefinition(
          "create_customer_booking",
          "Cria um agendamento do cliente atual no Google Calendar.",
          objectSchema(
            Map.of(
              "appointmentType",
              stringSchema("Tipo de atendimento, por exemplo consulta, limpeza ou retorno."),
              "startDateTime",
              stringSchema("Inicio em ISO 8601 com offset. Exemplo 2026-04-08T14:30:00-03:00."),
              "endDateTime",
              stringSchema("Fim em ISO 8601 com offset. Opcional se houver durationMinutes."),
              "durationMinutes",
              integerSchema("Duracao em minutos, por exemplo 30 ou 60."),
              "notes",
              stringSchema("Observacoes curtas do atendimento.")
            ),
            List.of("startDateTime")
          )
        )
      );
    }

    if (enabledCapabilities.contains("reschedule_appointment")) {
      tools.add(
        new OpenAIChatClient.ToolDefinition(
          "reschedule_customer_booking",
          "Remarca um agendamento do cliente atual sem expor agenda de terceiros.",
          objectSchema(
            Map.of(
              "eventId",
              stringSchema("ID exato do agendamento do cliente atual."),
              "appointmentType",
              stringSchema("Tipo de atendimento, se precisar ajustar o tipo."),
              "startDateTime",
              stringSchema("Novo inicio em ISO 8601 com offset."),
              "endDateTime",
              stringSchema("Novo fim em ISO 8601 com offset. Opcional se houver durationMinutes."),
              "durationMinutes",
              integerSchema("Nova duracao em minutos, se necessario."),
              "notes",
              stringSchema("Observacoes atualizadas, se necessario.")
            ),
            List.of("eventId", "startDateTime")
          )
        )
      );
    }

    if (enabledCapabilities.contains("cancel_appointment")) {
      tools.add(
        new OpenAIChatClient.ToolDefinition(
          "cancel_customer_booking",
          "Cancela um agendamento do cliente atual.",
          objectSchema(
            Map.of("eventId", stringSchema("ID exato do agendamento do cliente atual.")),
            List.of("eventId")
          )
        )
      );
    }

    return tools;
  }

  private Set<String> parseEnabledCapabilities(AIProfile aiProfile) {
    Set<String> capabilities = new LinkedHashSet<>();
    if (aiProfile.getToolsJson() != null && !aiProfile.getToolsJson().isBlank()) {
      try {
        JsonNode toolsNode = objectMapper.readTree(aiProfile.getToolsJson());
        if (toolsNode.isArray()) {
          for (JsonNode item : toolsNode) {
            if (item.isTextual()) {
              capabilities.add(item.asText().trim().toLowerCase());
            }
          }
        }
      } catch (Exception ignored) {
        for (String item : aiProfile.getToolsJson().replace("[", "").replace("]", "").replace("\"", "").split(",")) {
          if (!item.isBlank()) {
            capabilities.add(item.trim().toLowerCase());
          }
        }
      }
    }

    if ("appointment_secretary".equalsIgnoreCase(nullToEmpty(aiProfile.getProfileType()).trim())) {
      capabilities.add("check_availability");
      capabilities.add("create_appointment");
      capabilities.add("reschedule_appointment");
      capabilities.add("cancel_appointment");
    }

    if (capabilities.contains("list_calendar_events")) {
      capabilities.add("check_availability");
    }
    if (capabilities.contains("create_calendar_event")) {
      capabilities.add("create_appointment");
    }
    if (capabilities.contains("update_calendar_event") || capabilities.contains("modify_calendar_event")) {
      capabilities.add("reschedule_appointment");
    }
    if (capabilities.contains("delete_calendar_event")) {
      capabilities.add("cancel_appointment");
    }

    return capabilities;
  }

  private String executeCalendarTool(
    UUID tenantId,
    CustomerSchedulingService.CustomerIdentity customerIdentity,
    String toolName,
    Map<String, Object> arguments
  ) {
    return switch (toolName) {
      case "check_availability" -> writeJson(buildCheckAvailabilityResult(tenantId, arguments));
      case "find_customer_bookings" -> writeJson(buildFindCustomerBookingsResult(tenantId, customerIdentity, arguments));
      case "create_customer_booking" -> writeJson(buildCreateCustomerBookingResult(tenantId, customerIdentity, arguments));
      case "reschedule_customer_booking" -> writeJson(
        buildRescheduleCustomerBookingResult(tenantId, customerIdentity, arguments)
      );
      case "cancel_customer_booking" -> writeJson(buildCancelCustomerBookingResult(tenantId, customerIdentity, arguments));
      default -> writeJson(Map.of("success", false, "error", "Unknown calendar tool: " + toolName));
    };
  }

  private Map<String, Object> buildCheckAvailabilityResult(UUID tenantId, Map<String, Object> arguments) {
    CustomerSchedulingService.AvailabilityResult availability = customerSchedulingService.checkAvailability(
      tenantId,
      new CustomerSchedulingService.AvailabilityRequest(
        stringArgument(arguments, "appointmentType"),
        requiredStringArgument(arguments, "startDateTime"),
        stringArgument(arguments, "endDateTime"),
        integerArgument(arguments, "durationMinutes")
      )
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("available", availability.available());
    result.put("reason", availability.reason());
    result.put("requestedStartDateTime", availability.requestedStartDateTime());
    result.put("requestedEndDateTime", availability.requestedEndDateTime());
    result.put("suggestedSlots", availability.suggestedSlots());
    return result;
  }

  private Map<String, Object> buildFindCustomerBookingsResult(
    UUID tenantId,
    CustomerSchedulingService.CustomerIdentity customerIdentity,
    Map<String, Object> arguments
  ) {
    List<CustomerSchedulingService.CustomerBooking> bookings = customerSchedulingService.listCustomerBookings(
      tenantId,
      customerIdentity,
      instantArgument(arguments, "from"),
      instantArgument(arguments, "to")
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("bookings", bookings);
    return result;
  }

  private Map<String, Object> buildCreateCustomerBookingResult(
    UUID tenantId,
    CustomerSchedulingService.CustomerIdentity customerIdentity,
    Map<String, Object> arguments
  ) {
    CustomerSchedulingService.CustomerBooking booking = customerSchedulingService.createCustomerBooking(
      tenantId,
      customerIdentity,
      new CustomerSchedulingService.BookingRequest(
        stringArgument(arguments, "appointmentType"),
        requiredStringArgument(arguments, "startDateTime"),
        stringArgument(arguments, "endDateTime"),
        integerArgument(arguments, "durationMinutes"),
        stringArgument(arguments, "notes")
      )
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("action", "created");
    result.put("booking", booking);
    return result;
  }

  private Map<String, Object> buildRescheduleCustomerBookingResult(
    UUID tenantId,
    CustomerSchedulingService.CustomerIdentity customerIdentity,
    Map<String, Object> arguments
  ) {
    CustomerSchedulingService.CustomerBooking booking = customerSchedulingService.rescheduleCustomerBooking(
      tenantId,
      customerIdentity,
      new CustomerSchedulingService.RescheduleRequest(
        requiredStringArgument(arguments, "eventId"),
        stringArgument(arguments, "appointmentType"),
        requiredStringArgument(arguments, "startDateTime"),
        stringArgument(arguments, "endDateTime"),
        integerArgument(arguments, "durationMinutes"),
        stringArgument(arguments, "notes")
      )
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("action", "rescheduled");
    result.put("booking", booking);
    return result;
  }

  private Map<String, Object> buildCancelCustomerBookingResult(
    UUID tenantId,
    CustomerSchedulingService.CustomerIdentity customerIdentity,
    Map<String, Object> arguments
  ) {
    String eventId = requiredStringArgument(arguments, "eventId");
    customerSchedulingService.cancelCustomerBooking(tenantId, customerIdentity, eventId);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("action", "cancelled");
    result.put("eventId", eventId);
    return result;
  }

  private String stringArgument(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }

  private String requiredStringArgument(Map<String, Object> arguments, String key) {
    String value = stringArgument(arguments, key);
    if (value == null) {
      throw new IllegalArgumentException(key + " is required");
    }
    return value;
  }

  private Integer integerArgument(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(key + " must be an integer");
    }
  }

  private Instant instantArgument(Map<String, Object> arguments, String key) {
    String value = stringArgument(arguments, key);
    if (value == null) {
      return null;
    }

    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      try {
        return OffsetDateTime.parse(value).toInstant();
      } catch (DateTimeParseException exception) {
        throw new IllegalArgumentException(key + " must be a valid ISO 8601 date-time with offset");
      }
    }
  }

  private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("additionalProperties", false);
    schema.put("required", required);
    return schema;
  }

  private Map<String, Object> stringSchema(String description) {
    return Map.of("type", "string", "description", description);
  }

  private Map<String, Object> integerSchema(String description) {
    return Map.of("type", "integer", "description", description);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize calendar tool result", exception);
    }
  }

  public record WebhookProcessingResult(boolean replySent, String replyPreview, String sendError) {
    public static WebhookProcessingResult ignored() {
      return new WebhookProcessingResult(false, null, null);
    }
  }

  private record ProcessingContext(
    ChannelInstance channelInstance,
    Contact contact,
    Conversation conversation,
    String number,
    String reply
  ) {}
}
