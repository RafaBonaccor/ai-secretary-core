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
@Table(name = "conversations")
public class Conversation {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channel_instance_id", nullable = false)
  private ChannelInstance channelInstance;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contact_id", nullable = false)
  private Contact contact;

  @Column(nullable = false)
  private String status;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  @Column(name = "last_inbound_text", columnDefinition = "text")
  private String lastInboundText;

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
  public Contact getContact() { return contact; }
  public void setContact(Contact contact) { this.contact = contact; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getLastMessageAt() { return lastMessageAt; }
  public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
  public String getLastInboundText() { return lastInboundText; }
  public void setLastInboundText(String lastInboundText) { this.lastInboundText = lastInboundText; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
