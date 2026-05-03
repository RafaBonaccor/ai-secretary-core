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
@Table(name = "calendar_events", schema = "assistant_core")
public class InternalCalendarEvent {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "calendar_connection_id", nullable = false)
  private CalendarConnection calendarConnection;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private String summary;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Column
  private String timezone;

  @Column(name = "customer_name")
  private String customerName;

  @Column(name = "customer_phone")
  private String customerPhone;

  @Column(name = "customer_remote_jid")
  private String customerRemoteJid;

  @Column(name = "appointment_type_slug")
  private String appointmentTypeSlug;

  @Column(name = "private_metadata_json", columnDefinition = "text")
  private String privateMetadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CalendarConnection getCalendarConnection() { return calendarConnection; }
  public void setCalendarConnection(CalendarConnection calendarConnection) { this.calendarConnection = calendarConnection; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Instant getStartAt() { return startAt; }
  public void setStartAt(Instant startAt) { this.startAt = startAt; }
  public Instant getEndAt() { return endAt; }
  public void setEndAt(Instant endAt) { this.endAt = endAt; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String timezone) { this.timezone = timezone; }
  public String getCustomerName() { return customerName; }
  public void setCustomerName(String customerName) { this.customerName = customerName; }
  public String getCustomerPhone() { return customerPhone; }
  public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
  public String getCustomerRemoteJid() { return customerRemoteJid; }
  public void setCustomerRemoteJid(String customerRemoteJid) { this.customerRemoteJid = customerRemoteJid; }
  public String getAppointmentTypeSlug() { return appointmentTypeSlug; }
  public void setAppointmentTypeSlug(String appointmentTypeSlug) { this.appointmentTypeSlug = appointmentTypeSlug; }
  public String getPrivateMetadataJson() { return privateMetadataJson; }
  public void setPrivateMetadataJson(String privateMetadataJson) { this.privateMetadataJson = privateMetadataJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
