package com.assistantcore.service;

import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.GoogleCalendarCredential;
import com.assistantcore.repository.GoogleCalendarCredentialRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarCredentialService {

  private final GoogleCalendarCredentialRepository googleCalendarCredentialRepository;
  private final GoogleOAuthTokenCipher googleOAuthTokenCipher;

  public GoogleCalendarCredentialService(
    GoogleCalendarCredentialRepository googleCalendarCredentialRepository,
    GoogleOAuthTokenCipher googleOAuthTokenCipher
  ) {
    this.googleCalendarCredentialRepository = googleCalendarCredentialRepository;
    this.googleOAuthTokenCipher = googleOAuthTokenCipher;
  }

  @Transactional
  public void store(CalendarConnection connection, String accessToken, String refreshToken, Instant tokenExpiresAt) {
    GoogleCalendarCredential credential = googleCalendarCredentialRepository.findByCalendarConnectionId(connection.getId())
      .orElseGet(() -> {
        GoogleCalendarCredential item = new GoogleCalendarCredential();
        item.setId(UUID.randomUUID());
        item.setCalendarConnection(connection);
        item.setCreatedAt(Instant.now());
        return item;
      });

    credential.setEncryptedAccessToken(googleOAuthTokenCipher.encrypt(accessToken));
    if (refreshToken != null && !refreshToken.isBlank()) {
      credential.setEncryptedRefreshToken(googleOAuthTokenCipher.encrypt(refreshToken));
    }
    credential.setTokenExpiresAt(tokenExpiresAt);
    credential.setUpdatedAt(Instant.now());
    googleCalendarCredentialRepository.save(credential);
  }

  @Transactional(readOnly = true)
  public String requireAccessToken(UUID connectionId) {
    GoogleCalendarCredential credential = googleCalendarCredentialRepository.findByCalendarConnectionId(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Google OAuth credential not found for calendar connection: " + connectionId));

    return googleOAuthTokenCipher.decrypt(credential.getEncryptedAccessToken());
  }

  @Transactional(readOnly = true)
  public Optional<Instant> tokenExpiresAt(UUID connectionId) {
    return googleCalendarCredentialRepository.findByCalendarConnectionId(connectionId)
      .map(GoogleCalendarCredential::getTokenExpiresAt);
  }

  @Transactional(readOnly = true)
  public CredentialMaterial requireMaterial(UUID connectionId) {
    return findMaterial(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Google OAuth credential not found for calendar connection: " + connectionId));
  }

  @Transactional(readOnly = true)
  public Optional<CredentialMaterial> findMaterial(UUID connectionId) {
    return googleCalendarCredentialRepository.findByCalendarConnectionId(connectionId)
      .map(credential -> new CredentialMaterial(
        googleOAuthTokenCipher.decrypt(credential.getEncryptedAccessToken()),
        googleOAuthTokenCipher.decrypt(credential.getEncryptedRefreshToken()),
        credential.getTokenExpiresAt()
      ));
  }

  @Transactional
  public void updateTokens(UUID connectionId, String accessToken, String refreshToken, Instant tokenExpiresAt) {
    GoogleCalendarCredential credential = googleCalendarCredentialRepository.findByCalendarConnectionId(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Google OAuth credential not found for calendar connection: " + connectionId));

    credential.setEncryptedAccessToken(googleOAuthTokenCipher.encrypt(accessToken));
    if (refreshToken != null && !refreshToken.isBlank()) {
      credential.setEncryptedRefreshToken(googleOAuthTokenCipher.encrypt(refreshToken));
    }
    credential.setTokenExpiresAt(tokenExpiresAt);
    credential.setUpdatedAt(Instant.now());
    googleCalendarCredentialRepository.save(credential);
  }

  @Transactional
  public void deleteByConnectionId(UUID connectionId) {
    googleCalendarCredentialRepository.deleteByCalendarConnectionId(connectionId);
  }

  public record CredentialMaterial(String accessToken, String refreshToken, Instant expiresAt) {}
}
