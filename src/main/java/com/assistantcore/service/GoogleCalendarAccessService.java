package com.assistantcore.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarAccessService {

  private static final long EXPIRY_SKEW_SECONDS = 60;

  private final GoogleCalendarCredentialService googleCalendarCredentialService;
  private final GoogleCalendarClient googleCalendarClient;

  public GoogleCalendarAccessService(
    GoogleCalendarCredentialService googleCalendarCredentialService,
    GoogleCalendarClient googleCalendarClient
  ) {
    this.googleCalendarCredentialService = googleCalendarCredentialService;
    this.googleCalendarClient = googleCalendarClient;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String requireValidAccessToken(UUID connectionId) {
    GoogleCalendarCredentialService.CredentialMaterial material = googleCalendarCredentialService.requireMaterial(connectionId);

    if (material.expiresAt() == null || material.expiresAt().isAfter(Instant.now().plusSeconds(EXPIRY_SKEW_SECONDS))) {
      return material.accessToken();
    }

    if (material.refreshToken() == null || material.refreshToken().isBlank()) {
      return material.accessToken();
    }

    GoogleCalendarClient.GoogleTokenExchangeResult refreshed = googleCalendarClient.refreshAccessToken(material.refreshToken());
    googleCalendarCredentialService.updateTokens(
      connectionId,
      refreshed.accessToken(),
      refreshed.refreshToken(),
      refreshed.expiresAt()
    );
    return refreshed.accessToken();
  }
}
