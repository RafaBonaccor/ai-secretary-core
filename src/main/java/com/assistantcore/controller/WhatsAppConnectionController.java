package com.assistantcore.controller;

import com.assistantcore.dto.WhatsAppConnectionResponse;
import com.assistantcore.service.WhatsAppConnectionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channel-instances")
public class WhatsAppConnectionController {

  private final WhatsAppConnectionService whatsAppConnectionService;

  public WhatsAppConnectionController(WhatsAppConnectionService whatsAppConnectionService) {
    this.whatsAppConnectionService = whatsAppConnectionService;
  }

  @PostMapping("/{channelInstanceId}/whatsapp/connect")
  public WhatsAppConnectionResponse startPairing(@PathVariable UUID channelInstanceId) {
    return whatsAppConnectionService.startPairing(channelInstanceId);
  }

  @GetMapping("/{channelInstanceId}/whatsapp/state")
  public WhatsAppConnectionResponse getPairingState(@PathVariable UUID channelInstanceId) {
    return whatsAppConnectionService.getPairingState(channelInstanceId);
  }
}
