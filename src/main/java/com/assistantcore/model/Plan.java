package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans", schema = "assistant_core")
public class Plan {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "price_monthly", nullable = false)
  private BigDecimal priceMonthly;

  @Column(name = "message_limit", nullable = false)
  private Integer messageLimit;

  @Column(name = "audio_limit", nullable = false)
  private Integer audioLimit;

  @Column(name = "automation_limit", nullable = false)
  private Integer automationLimit;

  @Column(name = "max_whatsapp_numbers", nullable = false)
  private Integer maxWhatsappNumbers;

  @Column(name = "max_ai_profiles", nullable = false)
  private Integer maxAiProfiles;

  @Column(name = "max_team_members", nullable = false)
  private Integer maxTeamMembers;

  @Column(name = "calendar_enabled", nullable = false)
  private boolean calendarEnabled;

  @Column(name = "inbox_enabled", nullable = false)
  private boolean inboxEnabled;

  @Column(name = "custom_prompt_enabled", nullable = false)
  private boolean customPromptEnabled;

  @Column(name = "advanced_automation_enabled", nullable = false)
  private boolean advancedAutomationEnabled;

  @Column(name = "realtime_voice_enabled", nullable = false)
  private boolean realtimeVoiceEnabled;

  @Column(name = "future_features_enabled", nullable = false)
  private boolean futureFeaturesEnabled;

  @Column(name = "priority_support_enabled", nullable = false)
  private boolean prioritySupportEnabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public BigDecimal getPriceMonthly() { return priceMonthly; }
  public void setPriceMonthly(BigDecimal priceMonthly) { this.priceMonthly = priceMonthly; }
  public Integer getMessageLimit() { return messageLimit; }
  public void setMessageLimit(Integer messageLimit) { this.messageLimit = messageLimit; }
  public Integer getAudioLimit() { return audioLimit; }
  public void setAudioLimit(Integer audioLimit) { this.audioLimit = audioLimit; }
  public Integer getAutomationLimit() { return automationLimit; }
  public void setAutomationLimit(Integer automationLimit) { this.automationLimit = automationLimit; }
  public Integer getMaxWhatsappNumbers() { return maxWhatsappNumbers; }
  public void setMaxWhatsappNumbers(Integer maxWhatsappNumbers) { this.maxWhatsappNumbers = maxWhatsappNumbers; }
  public Integer getMaxAiProfiles() { return maxAiProfiles; }
  public void setMaxAiProfiles(Integer maxAiProfiles) { this.maxAiProfiles = maxAiProfiles; }
  public Integer getMaxTeamMembers() { return maxTeamMembers; }
  public void setMaxTeamMembers(Integer maxTeamMembers) { this.maxTeamMembers = maxTeamMembers; }
  public boolean isCalendarEnabled() { return calendarEnabled; }
  public void setCalendarEnabled(boolean calendarEnabled) { this.calendarEnabled = calendarEnabled; }
  public boolean isInboxEnabled() { return inboxEnabled; }
  public void setInboxEnabled(boolean inboxEnabled) { this.inboxEnabled = inboxEnabled; }
  public boolean isCustomPromptEnabled() { return customPromptEnabled; }
  public void setCustomPromptEnabled(boolean customPromptEnabled) { this.customPromptEnabled = customPromptEnabled; }
  public boolean isAdvancedAutomationEnabled() { return advancedAutomationEnabled; }
  public void setAdvancedAutomationEnabled(boolean advancedAutomationEnabled) { this.advancedAutomationEnabled = advancedAutomationEnabled; }
  public boolean isRealtimeVoiceEnabled() { return realtimeVoiceEnabled; }
  public void setRealtimeVoiceEnabled(boolean realtimeVoiceEnabled) { this.realtimeVoiceEnabled = realtimeVoiceEnabled; }
  public boolean isFutureFeaturesEnabled() { return futureFeaturesEnabled; }
  public void setFutureFeaturesEnabled(boolean futureFeaturesEnabled) { this.futureFeaturesEnabled = futureFeaturesEnabled; }
  public boolean isPrioritySupportEnabled() { return prioritySupportEnabled; }
  public void setPrioritySupportEnabled(boolean prioritySupportEnabled) { this.prioritySupportEnabled = prioritySupportEnabled; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
