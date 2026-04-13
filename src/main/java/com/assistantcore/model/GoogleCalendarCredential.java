package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "google_calendar_credentials", schema = "assistant_core")
public class GoogleCalendarCredential {

  @Id
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "calendar_connection_id", nullable = false, unique = true)
  private CalendarConnection calendarConnection;

  @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "text")
  private String encryptedAccessToken;

  @Column(name = "encrypted_refresh_token", columnDefinition = "text")
  private String encryptedRefreshToken;

  @Column(name = "token_expires_at")
  private Instant tokenExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CalendarConnection getCalendarConnection() { return calendarConnection; }
  public void setCalendarConnection(CalendarConnection calendarConnection) { this.calendarConnection = calendarConnection; }
  public String getEncryptedAccessToken() { return encryptedAccessToken; }
  public void setEncryptedAccessToken(String encryptedAccessToken) { this.encryptedAccessToken = encryptedAccessToken; }
  public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
  public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }
  public Instant getTokenExpiresAt() { return tokenExpiresAt; }
  public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
