package com.assistantcore.service;

import com.assistantcore.dto.AIProfileCreateRequest;
import com.assistantcore.dto.AIProfilePresetCreateRequest;
import com.assistantcore.dto.AIProfilePresetResponse;
import com.assistantcore.dto.AIProfileResponse;
import com.assistantcore.model.AIProfile;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.AIProfileRepository;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.assistantcore.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AIProfileService {

  private final AIProfileRepository aiProfileRepository;
  private final TenantRepository tenantRepository;
  private final ChannelInstanceRepository channelInstanceRepository;

  private static final Map<String, AIProfilePresetDefinition> PRESETS = Map.of(
    "appointment_secretary_clinic",
    new AIProfilePresetDefinition(
      "appointment_secretary_clinic",
      "Secretaria de Clinica",
      "appointment_secretary",
      "medical_clinic",
      "pt-BR",
      "Recepcao e agendamento para clinicas e consultorios.",
      "gpt-5.4",
      "alloy",
      "[\"check_availability\",\"create_appointment\",\"reschedule_appointment\",\"cancel_appointment\",\"confirm_appointment\"]",
      """
      Voce e uma secretaria virtual de clinica no Brasil.
      Seu papel e responder em portugues do Brasil, atender com educacao, coletar dados minimos para agendamento,
      confirmar horarios, remarcar, cancelar e reduzir faltas.
      Nunca invente disponibilidade. Se faltar informacao, pergunte somente o essencial.
      """,
      "{\"channel\":\"whatsapp\",\"country\":\"BR\",\"goal\":\"appointments\"}"
    ),
    "appointment_secretary_dental",
    new AIProfilePresetDefinition(
      "appointment_secretary_dental",
      "Secretaria Odontologica",
      "appointment_secretary",
      "dental_clinic",
      "pt-BR",
      "Agendamento e confirmacao para clinicas odontologicas.",
      "gpt-5.4",
      "alloy",
      "[\"check_availability\",\"create_appointment\",\"reschedule_appointment\",\"cancel_appointment\",\"confirm_appointment\"]",
      """
      Voce e uma secretaria virtual de clinica odontologica no Brasil.
      Seu foco e organizar consultas, retornos e confirmacoes com linguagem clara e acolhedora.
      Sempre confirme procedimento, data, horario e dados de contato antes de concluir.
      """,
      "{\"channel\":\"whatsapp\",\"country\":\"BR\",\"goal\":\"appointments\"}"
    ),
    "appointment_secretary_aesthetics",
    new AIProfilePresetDefinition(
      "appointment_secretary_aesthetics",
      "Secretaria de Estetica",
      "appointment_secretary",
      "aesthetics_clinic",
      "pt-BR",
      "Atendimento e agendamento para estetica e beleza.",
      "gpt-5.4",
      "alloy",
      "[\"check_availability\",\"create_appointment\",\"reschedule_appointment\",\"cancel_appointment\",\"lead_followup\"]",
      """
      Voce e uma secretaria virtual de clinica de estetica no Brasil.
      Atenda com simpatia e linguagem comercial leve, ajudando a converter interesse em agendamento.
      Nao pressione o cliente, mas conduza a conversa para data e horario quando houver interesse.
      """,
      "{\"channel\":\"whatsapp\",\"country\":\"BR\",\"goal\":\"booking_and_conversion\"}"
    ),
    "sales_assistant_real_estate",
    new AIProfilePresetDefinition(
      "sales_assistant_real_estate",
      "Assistente Comercial Imobiliario",
      "sales_assistant",
      "real_estate",
      "pt-BR",
      "Qualificacao de leads e agendamento de visitas.",
      "gpt-5.4",
      "alloy",
      "[\"qualify_lead\",\"schedule_visit\",\"capture_contact\",\"lead_followup\"]",
      """
      Voce e um assistente comercial imobiliario no Brasil.
      Seu objetivo e qualificar leads, entender interesse, faixa de preco, regiao e encaminhar para visita.
      Seja objetivo, cordial e orientado a conversao.
      """,
      "{\"channel\":\"whatsapp\",\"country\":\"BR\",\"goal\":\"sales_conversion\"}"
    ),
    "customer_support_retail",
    new AIProfilePresetDefinition(
      "customer_support_retail",
      "Atendimento ao Cliente Varejo",
      "customer_support",
      "retail",
      "pt-BR",
      "Suporte e atendimento de lojas e pequenos negocios.",
      "gpt-5.4-mini",
      "alloy",
      "[\"answer_faq\",\"capture_contact\",\"handoff_human\"]",
      """
      Voce e um assistente de atendimento ao cliente para varejo no Brasil.
      Responda de forma simples, amigavel e objetiva.
      Quando o caso exigir decisao humana, deixe isso claro e faca o handoff.
      """,
      "{\"channel\":\"whatsapp\",\"country\":\"BR\",\"goal\":\"support\"}"
    )
  );

  public AIProfileService(
    AIProfileRepository aiProfileRepository,
    TenantRepository tenantRepository,
    ChannelInstanceRepository channelInstanceRepository
  ) {
    this.aiProfileRepository = aiProfileRepository;
    this.tenantRepository = tenantRepository;
    this.channelInstanceRepository = channelInstanceRepository;
  }

  @Transactional(readOnly = true)
  public List<AIProfileResponse> listByTenant(UUID tenantId) {
    return aiProfileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<AIProfilePresetResponse> listPresets() {
    return PRESETS.values().stream().map(this::toPresetResponse).sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName())).toList();
  }

  @Transactional
  public AIProfileResponse createCustom(UUID tenantId, AIProfileCreateRequest request) {
    Tenant tenant = getTenant(tenantId);
    String slug = normalizeSlug(request.slug());
    ensureSlugAvailable(tenantId, slug);

    AIProfile profile = new AIProfile();
    profile.setId(UUID.randomUUID());
    profile.setTenant(tenant);
    profile.setName(request.name().trim());
    profile.setSlug(slug);
    profile.setProfileType(normalizeKey(request.profileType()));
    profile.setBusinessType(normalizeKey(request.businessType()));
    profile.setLanguage(normalizeLanguage(request.language()));
    profile.setModel(request.model().trim());
    profile.setSystemPrompt(request.systemPrompt().trim());
    profile.setTemperature(request.temperature() == null ? new BigDecimal("0.30") : request.temperature());
    profile.setVoice(blankToNull(request.voice()));
    profile.setWelcomeMessage(blankToNull(request.welcomeMessage()));
    profile.setToolsJson(defaultJson(request.toolsJson(), "[]"));
    profile.setConfigJson(defaultJson(request.configJson(), "{}"));
    profile.setDefault(request.isDefault() != null && request.isDefault());
    profile.setActive(request.active() == null || request.active());
    Instant now = Instant.now();
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);

    if (profile.isDefault()) {
      unsetTenantDefaults(tenantId);
    }

    return toResponse(aiProfileRepository.save(profile));
  }

  @Transactional
  public AIProfileResponse createFromPreset(UUID tenantId, AIProfilePresetCreateRequest request) {
    Tenant tenant = getTenant(tenantId);
    AIProfilePresetDefinition preset = PRESETS.get(request.presetKey());
    if (preset == null) {
      throw new IllegalArgumentException("Unknown preset: " + request.presetKey());
    }

    String name = hasText(request.name()) ? request.name().trim() : preset.displayName();
    String slug = normalizeSlug(hasText(request.slug()) ? request.slug() : preset.key());
    ensureSlugAvailable(tenantId, slug);

    String systemPrompt = preset.systemPrompt();
    if (hasText(request.additionalInstructions())) {
      systemPrompt = systemPrompt + "\n\nAdditional business instructions:\n" + request.additionalInstructions().trim();
    }

    AIProfile profile = new AIProfile();
    profile.setId(UUID.randomUUID());
    profile.setTenant(tenant);
    profile.setName(name);
    profile.setSlug(slug);
    profile.setProfileType(preset.profileType());
    profile.setBusinessType(preset.businessType());
    profile.setLanguage(normalizeLanguage(hasText(request.language()) ? request.language() : preset.language()));
    profile.setModel(hasText(request.model()) ? request.model().trim() : preset.suggestedModel());
    profile.setSystemPrompt(systemPrompt);
    profile.setTemperature(new BigDecimal("0.30"));
    profile.setVoice(hasText(request.voice()) ? request.voice().trim() : preset.suggestedVoice());
    profile.setWelcomeMessage(hasText(request.welcomeMessage()) ? request.welcomeMessage().trim() : defaultWelcomeMessage(preset));
    profile.setToolsJson(preset.toolsJson());
    profile.setConfigJson(preset.configJson());
    profile.setDefault(request.isDefault() != null && request.isDefault());
    profile.setActive(request.active() == null || request.active());
    Instant now = Instant.now();
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);

    if (profile.isDefault()) {
      unsetTenantDefaults(tenantId);
    }

    return toResponse(aiProfileRepository.save(profile));
  }

  @Transactional
  public AIProfileResponse createDefaultAppointmentSecretary(UUID tenantId) {
    return aiProfileRepository.findByTenantIdAndSlug(tenantId, "appointment-secretary")
      .map(this::toResponse)
      .orElseGet(() ->
        createFromPreset(
          tenantId,
          new AIProfilePresetCreateRequest(
            "appointment_secretary_clinic",
            "Appointment Secretary",
            "appointment-secretary",
            "pt-BR",
            null,
            null,
            null,
            null,
            true,
            true
          )
        )
      );
  }

  @Transactional
  public AIProfileResponse assignProfileToChannel(UUID channelInstanceId, UUID profileId) {
    ChannelInstance channelInstance = channelInstanceRepository.findById(channelInstanceId)
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found: " + channelInstanceId));

    AIProfile profile = aiProfileRepository.findById(profileId)
      .orElseThrow(() -> new EntityNotFoundException("AI profile not found: " + profileId));

    if (!channelInstance.getTenant().getId().equals(profile.getTenant().getId())) {
      throw new IllegalArgumentException("Channel instance and AI profile must belong to the same tenant");
    }

    channelInstance.setAiProfile(profile);
    channelInstance.setUpdatedAt(Instant.now());
    channelInstanceRepository.save(channelInstance);

    return toResponse(profile);
  }

  private Tenant getTenant(UUID tenantId) {
    return tenantRepository.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
  }

  private void ensureSlugAvailable(UUID tenantId, String slug) {
    if (aiProfileRepository.findByTenantIdAndSlug(tenantId, slug).isPresent()) {
      throw new IllegalArgumentException("AI profile slug already exists for tenant: " + slug);
    }
  }

  private void unsetTenantDefaults(UUID tenantId) {
    aiProfileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).forEach(profile -> {
      if (profile.isDefault()) {
        profile.setDefault(false);
        profile.setUpdatedAt(Instant.now());
        aiProfileRepository.save(profile);
      }
    });
  }

  private String normalizeSlug(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (slug.isBlank()) {
      throw new IllegalArgumentException("AI profile slug cannot be blank");
    }
    return slug;
  }

  private String normalizeKey(String value) {
    return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }

  private String normalizeLanguage(String value) {
    return hasText(value) ? value.trim() : "pt-BR";
  }

  private String defaultJson(String value, String fallback) {
    return hasText(value) ? value.trim() : fallback;
  }

  private String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String defaultWelcomeMessage(AIProfilePresetDefinition preset) {
    return switch (preset.profileType()) {
      case "appointment_secretary" -> "Ola. Posso te ajudar com agendamento, remarcacao ou confirmacao.";
      case "sales_assistant" -> "Ola. Posso te ajudar a encontrar a melhor opcao e agendar o proximo passo.";
      default -> "Ola. Como posso te ajudar hoje?";
    };
  }

  private AIProfilePresetResponse toPresetResponse(AIProfilePresetDefinition preset) {
    return new AIProfilePresetResponse(
      preset.key(),
      preset.displayName(),
      preset.profileType(),
      preset.businessType(),
      preset.language(),
      preset.description(),
      preset.suggestedModel(),
      preset.suggestedVoice(),
      parseTools(preset.toolsJson())
    );
  }

  private List<String> parseTools(String toolsJson) {
    return List.of(toolsJson.replace("[", "").replace("]", "").replace("\"", "").split(","))
      .stream()
      .map(String::trim)
      .filter(tool -> !tool.isBlank())
      .toList();
  }

  private AIProfileResponse toResponse(AIProfile profile) {
    return new AIProfileResponse(
      profile.getId(),
      profile.getTenant().getId(),
      profile.getName(),
      profile.getSlug(),
      profile.getProfileType(),
      profile.getBusinessType(),
      profile.getLanguage(),
      profile.getModel(),
      profile.getSystemPrompt(),
      profile.getTemperature(),
      profile.getVoice(),
      profile.getWelcomeMessage(),
      profile.getToolsJson(),
      profile.getConfigJson(),
      profile.isDefault(),
      profile.isActive(),
      profile.getCreatedAt(),
      profile.getUpdatedAt()
    );
  }

  private record AIProfilePresetDefinition(
    String key,
    String displayName,
    String profileType,
    String businessType,
    String language,
    String description,
    String suggestedModel,
    String suggestedVoice,
    String toolsJson,
    String systemPrompt,
    String configJson
  ) {}
}
