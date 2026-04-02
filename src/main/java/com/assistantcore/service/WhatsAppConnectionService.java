package com.assistantcore.service;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.dto.WhatsAppConnectionResponse;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppConnectionService {

  private final ChannelInstanceRepository channelInstanceRepository;
  private final EvolutionInstanceClient evolutionInstanceClient;
  private final ObjectMapper objectMapper;
  private final String evolutionWebhookUrl;
  private final Map<String, CachedPairingState> pairingStateCache = new ConcurrentHashMap<>();

  public WhatsAppConnectionService(
    ChannelInstanceRepository channelInstanceRepository,
    EvolutionInstanceClient evolutionInstanceClient,
    ObjectMapper objectMapper,
    @Value("${app.evolution.webhook-base-url}") String evolutionWebhookBaseUrl
  ) {
    this.channelInstanceRepository = channelInstanceRepository;
    this.evolutionInstanceClient = evolutionInstanceClient;
    this.objectMapper = objectMapper;
    this.evolutionWebhookUrl = evolutionWebhookBaseUrl.endsWith("/")
      ? evolutionWebhookBaseUrl + "api/v1/webhooks/evolution/messages"
      : evolutionWebhookBaseUrl + "/api/v1/webhooks/evolution/messages";
  }

  @Transactional
  public WhatsAppConnectionResponse startPairing(UUID channelInstanceId) {
    ChannelInstance channelInstance = getChannelInstance(channelInstanceId);
    boolean instanceCreated = false;

    if (!evolutionInstanceClient.instanceExists(channelInstance.getInstanceName())) {
      evolutionInstanceClient.createInstance(channelInstance.getInstanceName());
      instanceCreated = true;
      channelInstance.setStatus("instance_created");
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }

    boolean webhookConfigured = ensureMessageWebhook(channelInstance.getInstanceName());
    Map<String, Object> response = evolutionInstanceClient.connectInstance(channelInstance.getInstanceName(), channelInstance.getPhoneNumber());
    String state = normalizeState(extractState(response));

    channelInstance.setStatus(state == null || state.isBlank() ? "connection_requested" : state);
    channelInstance.setUpdatedAt(Instant.now());
    channelInstanceRepository.save(channelInstance);

    return toResponse(channelInstance, instanceCreated, webhookConfigured, response);
  }

  @Transactional
  public WhatsAppConnectionResponse getPairingState(UUID channelInstanceId) {
    ChannelInstance channelInstance = getChannelInstance(channelInstanceId);
    boolean webhookConfigured = ensureMessageWebhook(channelInstance.getInstanceName());
    Map<String, Object> response = evolutionInstanceClient.getConnectionState(channelInstance.getInstanceName());
    String state = normalizeState(extractState(response));
    if (state != null && !state.equals(channelInstance.getStatus())) {
      channelInstance.setStatus(state);
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }
    return toResponse(channelInstance, false, webhookConfigured, response);
  }

  @Transactional
  public void syncPairingState(EvolutionMessageWebhookRequest request) {
    String instanceName = request.resolvedInstanceName();
    if (instanceName == null || instanceName.isBlank()) {
      return;
    }

    ChannelInstance channelInstance = channelInstanceRepository.findByInstanceName(instanceName).orElse(null);
    if (channelInstance == null) {
      return;
    }

    String normalizedEvent = normalizeEventName(request.event());
    if ("QRCODE_UPDATED".equals(normalizedEvent)) {
      handleQrCodeUpdated(channelInstance, request.data());
      return;
    }

    if ("CONNECTION_UPDATE".equals(normalizedEvent)) {
      handleConnectionUpdated(channelInstance, request.data());
    }
  }

  private ChannelInstance getChannelInstance(UUID channelInstanceId) {
    return channelInstanceRepository.findById(channelInstanceId)
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found: " + channelInstanceId));
  }

  @SuppressWarnings("unchecked")
  private String extractState(Map<String, Object> payload) {
    Object instance = payload.get("instance");
    if (instance instanceof Map<?, ?> map) {
      Object state = ((Map<String, Object>) map).get("state");
      if (state == null) {
        state = ((Map<String, Object>) map).get("status");
      }
      return state == null ? null : String.valueOf(state);
    }
    return null;
  }

  private String normalizeState(String state) {
    if (state == null || state.isBlank()) {
      return null;
    }
    return switch (state.trim().toLowerCase()) {
      case "close", "closed", "refused" -> "disconnected";
      case "open", "opened" -> "connected";
      default -> state.trim().toLowerCase();
    };
  }

  @SuppressWarnings("unchecked")
  private boolean ensureMessageWebhook(String instanceName) {
    Map<String, Object> webhook = evolutionInstanceClient.getWebhook(instanceName);
    if (webhook.isEmpty()) {
      evolutionInstanceClient.setMessageWebhook(instanceName, evolutionWebhookUrl);
      return true;
    }

    String existingUrl = webhook.get("url") == null ? null : String.valueOf(webhook.get("url"));
    boolean enabled = webhook.get("enabled") instanceof Boolean value && value;
    boolean missingEvents = webhookEventsMissing(webhook);

    if (!enabled || existingUrl == null || !existingUrl.equals(evolutionWebhookUrl) || missingEvents) {
      evolutionInstanceClient.setMessageWebhook(instanceName, evolutionWebhookUrl);
    }

    return true;
  }

  private boolean webhookEventsMissing(Map<String, Object> webhook) {
    Object events = webhook.get("events");
    if (!(events instanceof Iterable<?> iterable)) {
      return true;
    }

    boolean hasMessagesUpsert = false;
    boolean hasQrCodeUpdated = false;
    boolean hasConnectionUpdate = false;

    for (Object event : iterable) {
      String normalized = normalizeEventName(event == null ? null : String.valueOf(event));
      if ("MESSAGES_UPSERT".equals(normalized)) {
        hasMessagesUpsert = true;
      } else if ("QRCODE_UPDATED".equals(normalized)) {
        hasQrCodeUpdated = true;
      } else if ("CONNECTION_UPDATE".equals(normalized)) {
        hasConnectionUpdate = true;
      }
    }

    return !(hasMessagesUpsert && hasQrCodeUpdated && hasConnectionUpdate);
  }

  @SuppressWarnings("unchecked")
  private WhatsAppConnectionResponse toResponse(
    ChannelInstance channelInstance,
    boolean instanceCreated,
    boolean webhookConfigured,
    Map<String, Object> payload
  ) {
    Map<String, Object> instance = payload.get("instance") instanceof Map<?, ?> map
      ? (Map<String, Object>) map
      : Map.of();
    Map<String, Object> qrCode = payload.get("qrcode") instanceof Map<?, ?> map
      ? (Map<String, Object>) map
      : Map.of();

    String state = instance.get("state") == null ? String.valueOf(instance.getOrDefault("status", channelInstance.getStatus())) : String.valueOf(instance.get("state"));
    state = normalizeState(state);
    String pairingCode = firstNonBlank(
      valueAsString(qrCode.get("pairingCode")),
      valueAsString(payload.get("pairingCode"))
    );
    String qrCodeText = firstNonBlank(
      valueAsString(qrCode.get("code")),
      valueAsString(payload.get("code"))
    );
    String qrCodeBase64 = firstNonBlank(
      valueAsString(qrCode.get("base64")),
      valueAsString(payload.get("base64"))
    );

    CachedPairingState cachedPairingState = pairingStateCache.get(channelInstance.getInstanceName());
    boolean canReuseCachedPairing = "connection_requested".equals(state) || "connecting".equals(state);

    if (canReuseCachedPairing && cachedPairingState != null) {
      pairingCode = firstNonBlank(pairingCode, cachedPairingState.pairingCode());
      qrCodeText = firstNonBlank(qrCodeText, cachedPairingState.qrCodeText());
      qrCodeBase64 = firstNonBlank(qrCodeBase64, cachedPairingState.qrCodeBase64());
    }

    if (pairingCode != null || qrCodeText != null || qrCodeBase64 != null) {
      pairingStateCache.put(
        channelInstance.getInstanceName(),
        new CachedPairingState(pairingCode, qrCodeText, qrCodeBase64, Instant.now())
      );
    }

    if ("connected".equals(state) || "disconnected".equals(state)) {
      pairingStateCache.remove(channelInstance.getInstanceName());
    }

    boolean reconnectRequired = "disconnected".equals(state);
    String nextAction = reconnectRequired
      ? "WhatsApp disconnected. Start pairing again to reconnect the device."
      : "connected".equals(state)
      ? "WhatsApp connected and ready to receive messages."
      : qrCodeBase64 != null || pairingCode != null
      ? "Finish the WhatsApp pairing with QR code or pairing code."
      : "Waiting for WhatsApp connection state update.";

    return new WhatsAppConnectionResponse(
      channelInstance.getId(),
      channelInstance.getInstanceName(),
      channelInstance.getPhoneNumber(),
      state,
      instanceCreated,
      webhookConfigured,
      reconnectRequired,
      pairingCode,
      qrCodeText,
      qrCodeBase64,
      nextAction,
      writeJson(payload)
    );
  }

  private String valueAsString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private void handleQrCodeUpdated(ChannelInstance channelInstance, JsonNode data) {
    JsonNode qrCode = data == null || data.isMissingNode() || data.isNull() ? null : data.path("qrcode");
    if (qrCode == null || qrCode.isMissingNode() || qrCode.isNull()) {
      qrCode = data;
    }

    String pairingCode = textOrNull(qrCode == null ? null : qrCode.path("pairingCode"));
    String qrCodeText = textOrNull(qrCode == null ? null : qrCode.path("code"));
    String qrCodeBase64 = textOrNull(qrCode == null ? null : qrCode.path("base64"));

    if (pairingCode == null && qrCodeText == null && qrCodeBase64 == null) {
      return;
    }

    pairingStateCache.put(
      channelInstance.getInstanceName(),
      new CachedPairingState(pairingCode, qrCodeText, qrCodeBase64, Instant.now())
    );

    if (!"connecting".equals(channelInstance.getStatus()) && !"connection_requested".equals(channelInstance.getStatus())) {
      channelInstance.setStatus("connecting");
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }
  }

  private void handleConnectionUpdated(ChannelInstance channelInstance, JsonNode data) {
    String rawState = textOrNull(data == null ? null : data.path("state"));
    String state = normalizeState(rawState);
    if (state == null) {
      return;
    }

    if (!state.equals(channelInstance.getStatus())) {
      channelInstance.setStatus(state);
      channelInstance.setUpdatedAt(Instant.now());
      channelInstanceRepository.save(channelInstance);
    }

    if ("connected".equals(state) || "disconnected".equals(state)) {
      pairingStateCache.remove(channelInstance.getInstanceName());
    }
  }

  private String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }

    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private String normalizeEventName(String event) {
    if (event == null || event.isBlank()) {
      return "";
    }

    return event.trim().replace('.', '_').replace('-', '_').toUpperCase();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }

  private record CachedPairingState(
    String pairingCode,
    String qrCodeText,
    String qrCodeBase64,
    Instant updatedAt
  ) {}
}
