package com.assistantcore.service;

import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Contact;
import com.assistantcore.model.Conversation;
import com.assistantcore.model.Message;
import com.assistantcore.repository.ContactRepository;
import com.assistantcore.repository.ConversationRepository;
import com.assistantcore.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private final ContactRepository contactRepository;
  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final ObjectMapper objectMapper;

  public ConversationService(
    ContactRepository contactRepository,
    ConversationRepository conversationRepository,
    MessageRepository messageRepository,
    ObjectMapper objectMapper
  ) {
    this.contactRepository = contactRepository;
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ConversationContext registerInboundMessage(
    ChannelInstance channelInstance,
    String remoteJid,
    String phoneNumber,
    String pushName,
    String body,
    String messageType,
    String providerMessageId,
    JsonNode rawPayload,
    Instant sentAt
  ) {
    Instant now = Instant.now();

    Contact contact = contactRepository.findByChannelInstanceIdAndRemoteJid(channelInstance.getId(), remoteJid).orElseGet(() -> {
      Contact created = new Contact();
      created.setId(UUID.randomUUID());
      created.setTenant(channelInstance.getTenant());
      created.setChannelInstance(channelInstance);
      created.setPhoneNumber(phoneNumber);
      created.setRemoteJid(remoteJid);
      created.setFirstSeenAt(now);
      created.setCreatedAt(now);
      created.setUpdatedAt(now);
      return created;
    });

    contact.setPhoneNumber(phoneNumber);
    contact.setDisplayName(pushName);
    contact.setPushName(pushName);
    contact.setLastSeenAt(sentAt);
    contact.setUpdatedAt(now);
    contact = contactRepository.save(contact);
    Contact savedContact = contact;

    Conversation conversation = conversationRepository.findByChannelInstanceIdAndContactId(channelInstance.getId(), savedContact.getId()).orElseGet(() -> {
      Conversation created = new Conversation();
      created.setId(UUID.randomUUID());
      created.setTenant(channelInstance.getTenant());
      created.setChannelInstance(channelInstance);
      created.setContact(savedContact);
      created.setStatus("open");
      created.setCreatedAt(now);
      created.setUpdatedAt(now);
      return created;
    });

    conversation.setStatus("open");
    conversation.setLastMessageAt(sentAt);
    conversation.setLastInboundText(body);
    conversation.setUpdatedAt(now);
    conversation = conversationRepository.save(conversation);

    Message message = new Message();
    message.setId(UUID.randomUUID());
    message.setTenant(channelInstance.getTenant());
    message.setChannelInstance(channelInstance);
    message.setConversation(conversation);
    message.setContact(contact);
    message.setProviderMessageId(providerMessageId);
    message.setDirection("inbound");
    message.setMessageType(messageType);
    message.setBody(body);
    message.setRawJson(writeJson(rawPayload));
    message.setSentAt(sentAt);
    message.setCreatedAt(now);
    messageRepository.save(message);

    return new ConversationContext(contact, conversation);
  }

  @Transactional
  public void registerOutboundMessage(
    ChannelInstance channelInstance,
    Contact contact,
    Conversation conversation,
    String body,
    String messageType
  ) {
    Instant now = Instant.now();

    conversation.setLastMessageAt(now);
    conversation.setUpdatedAt(now);
    conversationRepository.save(conversation);

    Message message = new Message();
    message.setId(UUID.randomUUID());
    message.setTenant(channelInstance.getTenant());
    message.setChannelInstance(channelInstance);
    message.setConversation(conversation);
    message.setContact(contact);
    message.setDirection("outbound");
    message.setMessageType(messageType);
    message.setBody(body);
    message.setRawJson("{}");
    message.setSentAt(now);
    message.setCreatedAt(now);
    messageRepository.save(message);
  }

  @Transactional(readOnly = true)
  public List<String> recentTranscript(UUID conversationId) {
    return messageRepository
      .findTop16ByConversationIdOrderBySentAtDesc(conversationId)
      .stream()
      .sorted(Comparator.comparing(Message::getSentAt))
      .map(message -> {
        String speaker = "inbound".equalsIgnoreCase(message.getDirection()) ? "Cliente" : "Secretaria";
        String body = message.getBody() == null ? "" : message.getBody().trim();
        return speaker + ": " + body;
      })
      .filter(line -> !line.endsWith(":"))
      .toList();
  }

  private String writeJson(JsonNode rawPayload) {
    try {
      return rawPayload == null ? "{}" : objectMapper.writeValueAsString(rawPayload);
    } catch (Exception exception) {
      return "{}";
    }
  }

  public record ConversationContext(Contact contact, Conversation conversation) {}
}
