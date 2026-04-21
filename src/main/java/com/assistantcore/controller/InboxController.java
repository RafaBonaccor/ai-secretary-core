package com.assistantcore.controller;

import com.assistantcore.dto.InboxConversationResponse;
import com.assistantcore.dto.InboxMessageResponse;
import com.assistantcore.dto.InboxSendMessageRequest;
import com.assistantcore.dto.ScheduledConversationActionRequest;
import com.assistantcore.dto.ScheduledConversationActionResponse;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.InboxService;
import com.assistantcore.service.SubscriptionEntitlementService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inbox")
public class InboxController {

  private final InboxService inboxService;
  private final AppAuthorizationService appAuthorizationService;
  private final SubscriptionEntitlementService subscriptionEntitlementService;

  public InboxController(
    InboxService inboxService,
    AppAuthorizationService appAuthorizationService,
    SubscriptionEntitlementService subscriptionEntitlementService
  ) {
    this.inboxService = inboxService;
    this.appAuthorizationService = appAuthorizationService;
    this.subscriptionEntitlementService = subscriptionEntitlementService;
  }

  @GetMapping("/conversations")
  public List<InboxConversationResponse> listConversations(@PathVariable UUID tenantId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.listConversations(tenantId);
  }

  @GetMapping("/conversations/{conversationId}/messages")
  public List<InboxMessageResponse> listMessages(@PathVariable UUID tenantId, @PathVariable UUID conversationId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.listMessages(tenantId, conversationId);
  }

  @PostMapping("/conversations/{conversationId}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public InboxMessageResponse sendMessage(
    @PathVariable UUID tenantId,
    @PathVariable UUID conversationId,
    @RequestBody InboxSendMessageRequest request
  ) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.sendManualMessage(tenantId, conversationId, request);
  }

  @GetMapping("/scheduled-actions")
  public List<ScheduledConversationActionResponse> listScheduledActions(
    @PathVariable UUID tenantId,
    @RequestParam(required = false) UUID conversationId
  ) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.listScheduledActions(tenantId, conversationId);
  }

  @PostMapping("/conversations/{conversationId}/scheduled-actions")
  @ResponseStatus(HttpStatus.CREATED)
  public ScheduledConversationActionResponse scheduleAction(
    @PathVariable UUID tenantId,
    @PathVariable UUID conversationId,
    @RequestBody ScheduledConversationActionRequest request
  ) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.scheduleAction(tenantId, conversationId, request);
  }

  @DeleteMapping("/scheduled-actions/{actionId}")
  public ScheduledConversationActionResponse cancelAction(@PathVariable UUID tenantId, @PathVariable UUID actionId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireInboxFeatureForTenant(tenantId);
    return inboxService.cancelScheduledAction(tenantId, actionId);
  }
}
