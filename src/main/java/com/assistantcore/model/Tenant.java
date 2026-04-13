package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants", schema = "assistant_core")
public class Tenant {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String timezone;

  @Column(name = "business_context_json", nullable = false, columnDefinition = "text")
  private String businessContextJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String timezone) { this.timezone = timezone; }
  public String getBusinessContextJson() { return businessContextJson; }
  public void setBusinessContextJson(String businessContextJson) { this.businessContextJson = businessContextJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
