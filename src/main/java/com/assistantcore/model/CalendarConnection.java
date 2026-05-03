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
@Table(name = "calendar_connections", schema = "assistant_core")
public class CalendarConnection {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(nullable = false)
  private String provider;

  @Column(name = "google_account_email")
  private String providerAccountEmail;

  @Column(name = "google_calendar_id")
  private String providerCalendarId;

  @Column(name = "google_calendar_name")
  private String calendarName;

  @Column(nullable = false)
  private String status;

  @Column(name = "sync_mode", nullable = false)
  private String syncMode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getProviderAccountEmail() { return providerAccountEmail; }
  public void setProviderAccountEmail(String providerAccountEmail) { this.providerAccountEmail = providerAccountEmail; }
  public String getProviderCalendarId() { return providerCalendarId; }
  public void setProviderCalendarId(String providerCalendarId) { this.providerCalendarId = providerCalendarId; }
  public String getCalendarName() { return calendarName; }
  public void setCalendarName(String calendarName) { this.calendarName = calendarName; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSyncMode() { return syncMode; }
  public void setSyncMode(String syncMode) { this.syncMode = syncMode; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
