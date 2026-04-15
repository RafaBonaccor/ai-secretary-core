package com.assistantcore.service;

import com.assistantcore.dto.InboxConversationResponse;
import com.assistantcore.dto.InboxMessageResponse;
import com.assistantcore.dto.InboxSendMessageRequest;
import com.assistantcore.dto.ScheduledConversationActionRequest;
import com.assistantcore.dto.ScheduledConversationActionResponse;
import com.assistantcore.model.Contact;
import com.assistantcore.model.Conversation;
import com.assistantcore.model.Message;
import com.assistantcore.model.ScheduledConversationAction;
import com.assistantcore.repository.ConversationRepository;
import com.assistantcore.repository.MessageRepository;
import com.assistantcore.repository.ScheduledConversationActionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final ScheduledConversationActionRepository scheduledConversationActionRepository;
  private final WhatsAppProviderRouter whatsAppProviderRouter;
  private final ConversationService conversationService;

  public InboxService(
    ConversationRepository conversationRepository,
    MessageRepository messageRepository,
    ScheduledConversationActionRepository scheduledConversationActionRepository,
    WhatsAppProviderRouter whatsAppProviderRouter,
    ConversationService conversationService
  ) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.scheduledConversationActionRepository = scheduledConversationActionRepository;
    this.whatsAppProviderRouter = whatsAppProviderRouter;
    this.conversationService = conversationService;
  }

  @Transactional(readOnly = true)
  public List<InboxConversationResponse> listConversations(UUID tenantId) {
    return conversationRepository.findTop100ByTenantIdOrderByLastMessageAtDesc(tenantId)
      .stream()
      .map(this::toConversationResponse)
      .toList();
  }

  @Transactional(readOnly = true)
  public List<InboxMessageResponse> listMessages(UUID tenantId, UUID conversationId) {
    Conversation conversation = requireConversation(tenantId, conversationId);
    return messageRepository.findTop200ByConversationIdOrderBySentAtDesc(conversation.getId())
      .stream()
      .sorted(Comparator.comparing(Message::getSentAt))
      .map(this::toMessageResponse)
      .toList();
  }

  @Transactional
  public InboxMessageResponse sendManualMessage(UUID tenantId, UUID conversationId, InboxSendMessageRequest request) {
    Conversation conversation = requireConversation(tenantId, conversationId);
    String body = normalizeMessageBody(request == null ? null : request.body());
    if (body == null) {
      throw new IllegalArgumentException("body is required");
    }

    Contact contact = conversation.getContact();
    whatsAppProviderRouter.forChannel(conversation.getChannelInstance())
      .sendText(conversation.getChannelInstance(), contact.getPhoneNumber(), body);

    conversationService.registerOutboundMessage(conversation.getChannelInstance(), contact, conversation, body, "text");
    Message message = messageRepository.findTop200ByConversationIdOrderBySentAtDesc(conversation.getId())
      .stream()
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Outbound message was not persisted"));
    return toMessageResponse(message);
  }

  @Transactional(readOnly = true)
  public List<ScheduledConversationActionResponse> listScheduledActions(UUID tenantId, UUID conversationId) {
    List<ScheduledConversationAction> actions = conversationId == null
      ? scheduledConversationActionRepository.findTop100ByTenantIdOrderByScheduledForDesc(tenantId)
      : scheduledConversationActionRepository.findTop100ByTenantIdAndConversationIdOrderByScheduledForDesc(tenantId, conversationId);

    return actions.stream().map(this::toScheduledActionResponse).toList();
  }

  @Transactional
  public ScheduledConversationActionResponse scheduleAction(
    UUID tenantId,
    UUID conversationId,
    ScheduledConversationActionRequest request
  ) {
    Conversation conversation = requireConversation(tenantId, conversationId);
    String body = normalizeMessageBody(request == null ? null : request.body());
    if (body == null) {
      throw new IllegalArgumentException("body is required");
    }

    Instant scheduledFor = requireScheduledFor(
      request == null ? null : request.scheduledFor(),
      resolveBusinessZone(conversation)
    );

    Instant now = Instant.now();
    ScheduledConversationAction action = new ScheduledConversationAction();
    action.setId(UUID.randomUUID());
    action.setTenant(conversation.getTenant());
    action.setChannelInstance(conversation.getChannelInstance());
    action.setConversation(conversation);
    action.setContact(conversation.getContact());
    action.setActionType(normalizeActionType(request == null ? null : request.actionType()));
    action.setTitle(normalizeTitle(request == null ? null : request.title()));
    action.setBody(body);
    action.setScheduledFor(scheduledFor);
    action.setStatus("scheduled");
    action.setCreatedAt(now);
    action.setUpdatedAt(now);
    return toScheduledActionResponse(scheduledConversationActionRepository.save(action));
  }

  @Transactional
  public ScheduledConversationActionResponse cancelScheduledAction(UUID tenantId, UUID actionId) {
    ScheduledConversationAction action = scheduledConversationActionRepository.findByIdAndTenantId(actionId, tenantId)
      .orElseThrow(() -> new EntityNotFoundException("Scheduled action not found: " + actionId));

    if (!"sent".equalsIgnoreCase(action.getStatus())) {
      action.setStatus("cancelled");
      action.setCancelledAt(Instant.now());
      action.setUpdatedAt(Instant.now());
      action = scheduledConversationActionRepository.save(action);
    }

    return toScheduledActionResponse(action);
  }

  @Transactional
  public void dispatchDueActions() {
    List<ScheduledConversationAction> dueActions = scheduledConversationActionRepository
      .findTop20ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc("scheduled", Instant.now());

    for (ScheduledConversationAction action : dueActions) {
      processScheduledAction(action.getId());
    }
  }

  @Transactional
  public void processScheduledAction(UUID actionId) {
    ScheduledConversationAction action = scheduledConversationActionRepository.findDetailedById(actionId)
      .orElseThrow(() -> new EntityNotFoundException("Scheduled action not found: " + actionId));

    if (!"scheduled".equalsIgnoreCase(action.getStatus())) {
      return;
    }

    action.setStatus("processing");
    action.setUpdatedAt(Instant.now());
    scheduledConversationActionRepository.save(action);

    try {
      whatsAppProviderRouter.forChannel(action.getChannelInstance())
        .sendText(action.getChannelInstance(), action.getContact().getPhoneNumber(), action.getBody());
      conversationService.registerOutboundMessage(
        action.getChannelInstance(),
        action.getContact(),
        action.getConversation(),
        action.getBody(),
        "text"
      );

      action.setStatus("sent");
      action.setExecutedAt(Instant.now());
      action.setErrorMessage(null);
      action.setUpdatedAt(Instant.now());
      scheduledConversationActionRepository.save(action);
    } catch (Exception exception) {
      action.setStatus("failed");
      action.setErrorMessage(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
      action.setUpdatedAt(Instant.now());
      scheduledConversationActionRepository.save(action);
    }
  }

  private Conversation requireConversation(UUID tenantId, UUID conversationId) {
    if (conversationId == null) {
      throw new IllegalArgumentException("conversationId is required");
    }

    return conversationRepository.findByIdAndTenantId(conversationId, tenantId)
      .orElseThrow(() -> new EntityNotFoundException("Conversation not found: " + conversationId));
  }

  private InboxConversationResponse toConversationResponse(Conversation conversation) {
    Contact contact = conversation.getContact();
    return new InboxConversationResponse(
      conversation.getId(),
      contact.getId(),
      conversation.getChannelInstance().getId(),
      displayName(contact),
      contact.getPhoneNumber(),
      contact.getRemoteJid(),
      conversation.getStatus(),
      conversation.getLastMessageAt(),
      conversation.getLastMessagePreview(),
      conversation.getLastMessageDirection()
    );
  }

  private InboxMessageResponse toMessageResponse(Message message) {
    return new InboxMessageResponse(
      message.getId(),
      message.getDirection(),
      message.getMessageType(),
      message.getBody(),
      message.getProviderMessageId(),
      message.getSentAt()
    );
  }

  private ScheduledConversationActionResponse toScheduledActionResponse(ScheduledConversationAction action) {
    Contact contact = action.getContact();
    return new ScheduledConversationActionResponse(
      action.getId(),
      action.getConversation().getId(),
      contact.getId(),
      displayName(contact),
      contact.getPhoneNumber(),
      action.getActionType(),
      action.getTitle(),
      action.getBody(),
      action.getScheduledFor(),
      action.getStatus(),
      action.getExecutedAt(),
      action.getCancelledAt(),
      action.getErrorMessage()
    );
  }

  private String displayName(Contact contact) {
    if (contact == null) {
      return "Contato";
    }
    if (contact.getDisplayName() != null && !contact.getDisplayName().isBlank()) {
      return contact.getDisplayName().trim();
    }
    if (contact.getPushName() != null && !contact.getPushName().isBlank()) {
      return contact.getPushName().trim();
    }
    return contact.getPhoneNumber();
  }

  private String normalizeMessageBody(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (normalized.isBlank()) {
      return null;
    }

    return normalized.length() <= 4000 ? normalized : normalized.substring(0, 4000).trim();
  }

  private String normalizeTitle(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    return normalized.length() <= 120 ? normalized : normalized.substring(0, 120).trim();
  }

  private String normalizeActionType(String value) {
    if (value == null || value.isBlank()) {
      return "reminder";
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    return switch (normalized) {
      case "reminder", "follow_up", "manual_action" -> normalized;
      default -> "reminder";
    };
  }

  private Instant requireScheduledFor(String value, ZoneId businessZone) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("scheduledFor is required");
    }

    try {
      return OffsetDateTime.parse(value.trim()).toInstant();
    } catch (DateTimeParseException ignored) {
      try {
        return Instant.parse(value.trim());
      } catch (DateTimeParseException exception) {
        try {
          ZoneId effectiveZone = businessZone == null ? ZoneOffset.UTC : businessZone;
          return LocalDateTime.parse(value.trim()).atZone(effectiveZone).toInstant();
        } catch (DateTimeParseException localDateTimeException) {
          throw new IllegalArgumentException("scheduledFor must be a valid ISO 8601 date-time");
        }
      }
    }
  }

  private ZoneId resolveBusinessZone(Conversation conversation) {
    try {
      if (
        conversation != null &&
        conversation.getTenant() != null &&
        conversation.getTenant().getTimezone() != null &&
        !conversation.getTenant().getTimezone().isBlank()
      ) {
        return ZoneId.of(conversation.getTenant().getTimezone().trim());
      }
    } catch (Exception ignored) {}
    return ZoneOffset.UTC;
  }
}
