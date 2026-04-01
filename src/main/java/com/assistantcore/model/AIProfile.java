package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_profiles")
public class AIProfile {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(name = "profile_type", nullable = false)
  private String profileType;

  @Column(name = "business_type", nullable = false)
  private String businessType;

  @Column(nullable = false)
  private String language;

  @Column(nullable = false)
  private String model;

  @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
  private String systemPrompt;

  @Column(nullable = false)
  private BigDecimal temperature;

  @Column
  private String voice;

  @Column(name = "welcome_message", columnDefinition = "text")
  private String welcomeMessage;

  @Column(name = "tools_json", columnDefinition = "text")
  private String toolsJson;

  @Column(name = "config_json", nullable = false, columnDefinition = "text")
  private String configJson;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getProfileType() { return profileType; }
  public void setProfileType(String profileType) { this.profileType = profileType; }
  public String getBusinessType() { return businessType; }
  public void setBusinessType(String businessType) { this.businessType = businessType; }
  public String getLanguage() { return language; }
  public void setLanguage(String language) { this.language = language; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public String getSystemPrompt() { return systemPrompt; }
  public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
  public BigDecimal getTemperature() { return temperature; }
  public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
  public String getVoice() { return voice; }
  public void setVoice(String voice) { this.voice = voice; }
  public String getWelcomeMessage() { return welcomeMessage; }
  public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
  public String getToolsJson() { return toolsJson; }
  public void setToolsJson(String toolsJson) { this.toolsJson = toolsJson; }
  public String getConfigJson() { return configJson; }
  public void setConfigJson(String configJson) { this.configJson = configJson; }
  public boolean isDefault() { return isDefault; }
  public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
