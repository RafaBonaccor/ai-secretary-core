package com.assistantcore.controller;

import com.assistantcore.dto.EvolutionMessageWebhookRequest;
import com.assistantcore.service.EvolutionMessageOrchestrator;
import java.time.Instant;
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

  public EvolutionWebhookController(EvolutionMessageOrchestrator evolutionMessageOrchestrator) {
    this.evolutionMessageOrchestrator = evolutionMessageOrchestrator;
  }

  @PostMapping("/messages")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> receiveMessage(@RequestBody EvolutionMessageWebhookRequest request) {
    var result = evolutionMessageOrchestrator.process(request);

    return Map.of(
      "accepted", true,
      "event", request.event(),
      "instanceName", request.instanceName(),
      "replySent", result.replySent(),
      "replyPreview", result.replyPreview() == null ? "" : result.replyPreview(),
      "sendError", result.sendError() == null ? "" : result.sendError(),
      "receivedAt", Instant.now().toString()
    );
  }
}
