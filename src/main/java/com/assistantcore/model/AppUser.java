package com.assistantcore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users", schema = "assistant_core")
public class AppUser {

  @Id
  private UUID id;

  @Column(name = "supabase_user_id", nullable = false, unique = true)
  private String supabaseUserId;

  @Column(nullable = false)
  private String email;

  @Column(name = "full_name")
  private String fullName;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getSupabaseUserId() { return supabaseUserId; }
  public void setSupabaseUserId(String supabaseUserId) { this.supabaseUserId = supabaseUserId; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
