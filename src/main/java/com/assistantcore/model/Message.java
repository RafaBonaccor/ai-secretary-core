package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages", schema = "assistant_core")
public class Message {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channel_instance_id", nullable = false)
  private ChannelInstance channelInstance;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contact_id", nullable = false)
  private Contact contact;

  @Column(name = "provider_message_id")
  private String providerMessageId;

  @Column(nullable = false)
  private String direction;

  @Column(name = "message_type", nullable = false)
  private String messageType;

  @Column(columnDefinition = "text")
  private String body;

  @Column(name = "raw_json", columnDefinition = "text")
  private String rawJson;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public ChannelInstance getChannelInstance() { return channelInstance; }
  public void setChannelInstance(ChannelInstance channelInstance) { this.channelInstance = channelInstance; }
  public Conversation getConversation() { return conversation; }
  public void setConversation(Conversation conversation) { this.conversation = conversation; }
  public Contact getContact() { return contact; }
  public void setContact(Contact contact) { this.contact = contact; }
  public String getProviderMessageId() { return providerMessageId; }
  public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
  public String getDirection() { return direction; }
  public void setDirection(String direction) { this.direction = direction; }
  public String getMessageType() { return messageType; }
  public void setMessageType(String messageType) { this.messageType = messageType; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getRawJson() { return rawJson; }
  public void setRawJson(String rawJson) { this.rawJson = rawJson; }
  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
