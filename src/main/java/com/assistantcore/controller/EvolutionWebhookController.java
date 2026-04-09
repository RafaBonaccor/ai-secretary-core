package com.assistantcore.controller;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.service.EvolutionMessageOrchestrator;
import com.assistantcore.service.WhatsAppConnectionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/evolution")
public class EvolutionWebhookController {

  private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookController.class);

  private final EvolutionMessageOrchestrator evolutionMessageOrchestrator;
  private final WhatsAppConnectionService whatsAppConnectionService;
  private final Executor webhookTaskExecutor;

  public EvolutionWebhookController(
    EvolutionMessageOrchestrator evolutionMessageOrchestrator,
    WhatsAppConnectionService whatsAppConnectionService,
    @Qualifier("webhookTaskExecutor") Executor webhookTaskExecutor
  ) {
    this.evolutionMessageOrchestrator = evolutionMessageOrchestrator;
    this.whatsAppConnectionService = whatsAppConnectionService;
    this.webhookTaskExecutor = webhookTaskExecutor;
  }

  @PostMapping("/messages")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> receiveMessage(@RequestBody EvolutionMessageWebhookRequest request) {
    String resolvedInstanceName = request.resolvedInstanceName();

    webhookTaskExecutor.execute(() -> {
      try {
        whatsAppConnectionService.syncPairingState(request);
      } catch (Exception exception) {
        log.warn("Failed to sync WhatsApp pairing state asynchronously", exception);
      }

      try {
        evolutionMessageOrchestrator.process(request);
      } catch (Exception exception) {
        log.error("Failed to process Evolution webhook asynchronously", exception);
      }
    });

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("accepted", true);
    response.put("queued", true);
    response.put("event", request.event() == null ? "" : request.event());
    response.put("instanceName", resolvedInstanceName == null ? "" : resolvedInstanceName);
    response.put("replySent", false);
    response.put("replyPreview", "");
    response.put("sendError", "");
    response.put("receivedAt", Instant.now().toString());
    return response;
  }
}
