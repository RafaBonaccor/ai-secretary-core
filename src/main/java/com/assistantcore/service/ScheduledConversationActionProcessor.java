package com.assistantcore.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledConversationActionProcessor {

  private final InboxService inboxService;

  public ScheduledConversationActionProcessor(InboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Scheduled(fixedDelayString = "${app.inbox.scheduled-actions.poll-delay-ms:30000}")
  public void dispatchDueActions() {
    inboxService.dispatchDueActions();
  }
}
