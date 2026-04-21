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
@Table(name = "subscriptions", schema = "assistant_core")
public class Subscription {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_subscription_id")
  private String providerSubscriptionId;

  @Column(name = "provider_customer_id")
  private String providerCustomerId;

  @Column(name = "provider_price_id")
  private String providerPriceId;

  @Column(nullable = false)
  private String status;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "period_end", nullable = false)
  private Instant periodEnd;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Tenant getTenant() { return tenant; }
  public void setTenant(Tenant tenant) { this.tenant = tenant; }
  public Plan getPlan() { return plan; }
  public void setPlan(Plan plan) { this.plan = plan; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getProviderSubscriptionId() { return providerSubscriptionId; }
  public void setProviderSubscriptionId(String providerSubscriptionId) { this.providerSubscriptionId = providerSubscriptionId; }
  public String getProviderCustomerId() { return providerCustomerId; }
  public void setProviderCustomerId(String providerCustomerId) { this.providerCustomerId = providerCustomerId; }
  public String getProviderPriceId() { return providerPriceId; }
  public void setProviderPriceId(String providerPriceId) { this.providerPriceId = providerPriceId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getPeriodStart() { return periodStart; }
  public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }
  public Instant getPeriodEnd() { return periodEnd; }
  public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
