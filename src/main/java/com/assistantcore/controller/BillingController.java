package com.assistantcore.controller;

import com.assistantcore.dto.StripeSubscriptionSyncRequest;
import com.assistantcore.service.BillingSubscriptionSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/billing")
public class BillingController {

  private final BillingSubscriptionSyncService billingSubscriptionSyncService;

  public BillingController(BillingSubscriptionSyncService billingSubscriptionSyncService) {
    this.billingSubscriptionSyncService = billingSubscriptionSyncService;
  }

  @PostMapping("/subscriptions/sync")
  public ResponseEntity<Void> syncStripeSubscription(@RequestBody StripeSubscriptionSyncRequest request) {
    billingSubscriptionSyncService.syncStripeSubscription(request);
    return ResponseEntity.noContent().build();
  }
}
