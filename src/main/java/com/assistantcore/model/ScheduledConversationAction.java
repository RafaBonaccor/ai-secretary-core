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
@Table(name = "scheduled_conversation_actions", schema = "assistant_core")
public class ScheduledConversationAction {

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

  @Column(name = "action_type", nullable = false)
  private String actionType;

  @Column
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "scheduled_for", nullable = false)
  private Instant scheduledFor;

  @Column(nullable = false)
  private String status;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

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
  public String getActionType() { return actionType; }
  public void setActionType(String actionType) { this.actionType = actionType; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public Instant getScheduledFor() { return scheduledFor; }
  public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getExecutedAt() { return executedAt; }
  public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
