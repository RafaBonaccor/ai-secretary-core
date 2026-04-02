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
@Table(name = "oauth_states")
public class OAuthState {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String provider;

  @Column(name = "state_token", nullable = false, unique = true)
  private String stateToken;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "calendar_connection_id")
  private CalendarConnection calendarConnection;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getStateToken() { return stateToken; }
  public void setStateToken(String stateToken) { this.stateToken = stateToken; }
  public CalendarConnection getCalendarConnection() { return calendarConnection; }
  public void setCalendarConnection(CalendarConnection calendarConnection) { this.calendarConnection = calendarConnection; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getConsumedAt() { return consumedAt; }
  public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
