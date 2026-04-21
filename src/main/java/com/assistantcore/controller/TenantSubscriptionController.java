package com.assistantcore.controller;

import com.assistantcore.dto.TenantSubscriptionResponse;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.SubscriptionEntitlementService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantSubscriptionController {

  private final SubscriptionEntitlementService subscriptionEntitlementService;
  private final AppAuthorizationService appAuthorizationService;

  public TenantSubscriptionController(
    SubscriptionEntitlementService subscriptionEntitlementService,
    AppAuthorizationService appAuthorizationService
  ) {
    this.subscriptionEntitlementService = subscriptionEntitlementService;
    this.appAuthorizationService = appAuthorizationService;
  }

  @GetMapping("/{tenantId}/subscription")
  public TenantSubscriptionResponse getTenantSubscription(@PathVariable UUID tenantId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    return subscriptionEntitlementService.getTenantSubscription(tenantId);
  }
}
