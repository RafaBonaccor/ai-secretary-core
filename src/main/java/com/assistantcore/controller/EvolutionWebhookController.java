package com.assistantcore.controller;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.service.EvolutionMessageOrchestrator;
import com.assistantcore.service.WhatsAppConnectionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/evolution")
public class EvolutionWebhookController {

  private final EvolutionMessageOrchestrator evolutionMessageOrchestrator;
  private final WhatsAppConnectionService whatsAppConnectionService;

  public EvolutionWebhookController(
    EvolutionMessageOrchestrator evolutionMessageOrchestrator,
    WhatsAppConnectionService whatsAppConnectionService
  ) {
    this.evolutionMessageOrchestrator = evolutionMessageOrchestrator;
    this.whatsAppConnectionService = whatsAppConnectionService;
  }

  @PostMapping("/messages")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> receiveMessage(@RequestBody EvolutionMessageWebhookRequest request) {
    whatsAppConnectionService.syncPairingState(request);
    var result = evolutionMessageOrchestrator.process(request);
    String resolvedInstanceName = request.resolvedInstanceName();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("accepted", true);
    response.put("event", request.event() == null ? "" : request.event());
    response.put("instanceName", resolvedInstanceName == null ? "" : resolvedInstanceName);
    response.put("replySent", result.replySent());
    response.put("replyPreview", result.replyPreview() == null ? "" : result.replyPreview());
    response.put("sendError", result.sendError() == null ? "" : result.sendError());
    response.put("receivedAt", Instant.now().toString());
    return response;
  }
}
