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
@Table(name = "appointment_types")
public class AppointmentType {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "calendar_connection_id", nullable = false)
  private CalendarConnection calendarConnection;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(name = "duration_minutes", nullable = false)
  private Integer durationMinutes;

  @Column(name = "buffer_before_minutes", nullable = false)
  private Integer bufferBeforeMinutes;

  @Column(name = "buffer_after_minutes", nullable = false)
  private Integer bufferAfterMinutes;

  @Column(name = "price_amount", precision = 10, scale = 2)
  private BigDecimal priceAmount;

  @Column
  private String currency;

  @Column(nullable = false)
  private boolean active;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CalendarConnection getCalendarConnection() { return calendarConnection; }
  public void setCalendarConnection(CalendarConnection calendarConnection) { this.calendarConnection = calendarConnection; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public Integer getDurationMinutes() { return durationMinutes; }
  public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
  public Integer getBufferBeforeMinutes() { return bufferBeforeMinutes; }
  public void setBufferBeforeMinutes(Integer bufferBeforeMinutes) { this.bufferBeforeMinutes = bufferBeforeMinutes; }
  public Integer getBufferAfterMinutes() { return bufferAfterMinutes; }
  public void setBufferAfterMinutes(Integer bufferAfterMinutes) { this.bufferAfterMinutes = bufferAfterMinutes; }
  public BigDecimal getPriceAmount() { return priceAmount; }
  public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
