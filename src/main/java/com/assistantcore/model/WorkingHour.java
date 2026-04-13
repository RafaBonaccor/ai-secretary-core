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
@Table(name = "working_hours", schema = "assistant_core")
public class WorkingHour {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "calendar_connection_id", nullable = false)
  private CalendarConnection calendarConnection;

  @Column(nullable = false)
  private Integer weekday;

  @Column(name = "is_enabled", nullable = false)
  private boolean enabled;

  @Column(name = "start_time")
  private String startTime;

  @Column(name = "end_time")
  private String endTime;

  @Column(name = "break_start_time")
  private String breakStartTime;

  @Column(name = "break_end_time")
  private String breakEndTime;

  @Column(name = "slot_interval_minutes", nullable = false)
  private Integer slotIntervalMinutes;

  @Column(name = "buffer_before_minutes", nullable = false)
  private Integer bufferBeforeMinutes;

  @Column(name = "buffer_after_minutes", nullable = false)
  private Integer bufferAfterMinutes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CalendarConnection getCalendarConnection() { return calendarConnection; }
  public void setCalendarConnection(CalendarConnection calendarConnection) { this.calendarConnection = calendarConnection; }
  public Integer getWeekday() { return weekday; }
  public void setWeekday(Integer weekday) { this.weekday = weekday; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getStartTime() { return startTime; }
  public void setStartTime(String startTime) { this.startTime = startTime; }
  public String getEndTime() { return endTime; }
  public void setEndTime(String endTime) { this.endTime = endTime; }
  public String getBreakStartTime() { return breakStartTime; }
  public void setBreakStartTime(String breakStartTime) { this.breakStartTime = breakStartTime; }
  public String getBreakEndTime() { return breakEndTime; }
  public void setBreakEndTime(String breakEndTime) { this.breakEndTime = breakEndTime; }
  public Integer getSlotIntervalMinutes() { return slotIntervalMinutes; }
  public void setSlotIntervalMinutes(Integer slotIntervalMinutes) { this.slotIntervalMinutes = slotIntervalMinutes; }
  public Integer getBufferBeforeMinutes() { return bufferBeforeMinutes; }
  public void setBufferBeforeMinutes(Integer bufferBeforeMinutes) { this.bufferBeforeMinutes = bufferBeforeMinutes; }
  public Integer getBufferAfterMinutes() { return bufferAfterMinutes; }
  public void setBufferAfterMinutes(Integer bufferAfterMinutes) { this.bufferAfterMinutes = bufferAfterMinutes; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
