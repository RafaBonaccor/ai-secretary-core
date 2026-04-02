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
@Table(name = "contacts")
public class Contact {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channel_instance_id", nullable = false)
  private ChannelInstance channelInstance;

  @Column(name = "phone_number", nullable = false)
  private String phoneNumber;

  @Column(name = "remote_jid", nullable = false)
  private String remoteJid;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "push_name")
  private String pushName;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

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
  public String getPhoneNumber() { return phoneNumber; }
  public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
  public String getRemoteJid() { return remoteJid; }
  public void setRemoteJid(String remoteJid) { this.remoteJid = remoteJid; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getPushName() { return pushName; }
  public void setPushName(String pushName) { this.pushName = pushName; }
  public Instant getFirstSeenAt() { return firstSeenAt; }
  public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
