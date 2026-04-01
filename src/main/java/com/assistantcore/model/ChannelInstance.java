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
@Table(name = "channel_instances")
public class ChannelInstance {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "provider_type", nullable = false)
  private String providerType;

  @Column(name = "channel_type", nullable = false)
  private String channelType;

  @Column(name = "phone_number", nullable = false)
  private String phoneNumber;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "instance_name", nullable = false, unique = true)
  private String instanceName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ai_profile_id")
  private AIProfile aiProfile;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public String getProviderType() { return providerType; }
  public void setProviderType(String providerType) { this.providerType = providerType; }
  public String getChannelType() { return channelType; }
  public void setChannelType(String channelType) { this.channelType = channelType; }
  public String getPhoneNumber() { return phoneNumber; }
  public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getInstanceName() { return instanceName; }
  public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
  public AIProfile getAiProfile() { return aiProfile; }
  public void setAiProfile(AIProfile aiProfile) { this.aiProfile = aiProfile; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
