package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
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
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
