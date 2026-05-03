package com.assistantcore.service;

import com.assistantcore.dto.GoogleCalendarItemResponse;
import com.assistantcore.dto.GoogleOAuthCallbackResponse;
import com.assistantcore.dto.GoogleOAuthStartResponse;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.OAuthState;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.OAuthStateRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOAuthService {

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final OAuthStateRepository oAuthStateRepository;
  private final GoogleCalendarClient googleCalendarClient;
  private final GoogleCalendarAccessService googleCalendarAccessService;
  private final GoogleCalendarCredentialService googleCalendarCredentialService;
  private final GoogleOAuthTokenCipher googleOAuthTokenCipher;
  private final OfficialEmailPolicyService officialEmailPolicyService;

  public GoogleOAuthService(
    CalendarConnectionRepository calendarConnectionRepository,
    OAuthStateRepository oAuthStateRepository,
    GoogleCalendarClient googleCalendarClient,
    GoogleCalendarAccessService googleCalendarAccessService,
    GoogleCalendarCredentialService googleCalendarCredentialService,
    GoogleOAuthTokenCipher googleOAuthTokenCipher,
    OfficialEmailPolicyService officialEmailPolicyService
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.oAuthStateRepository = oAuthStateRepository;
    this.googleCalendarClient = googleCalendarClient;
    this.googleCalendarAccessService = googleCalendarAccessService;
    this.googleCalendarCredentialService = googleCalendarCredentialService;
    this.googleOAuthTokenCipher = googleOAuthTokenCipher;
    this.officialEmailPolicyService = officialEmailPolicyService;
  }

  @Transactional
  public GoogleOAuthStartResponse startCalendarOAuth(UUID connectionId) {
    ensureConfigured();

    CalendarConnection connection = calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));

    OAuthState state = new OAuthState();
    state.setId(UUID.randomUUID());
    state.setProvider("google_calendar");
    state.setStateToken(UUID.randomUUID().toString());
    state.setCalendarConnection(connection);
    state.setCreatedAt(Instant.now());
    state.setExpiresAt(Instant.now().plusSeconds(600));
    oAuthStateRepository.save(state);

    String authorizationUrl = googleCalendarClient.authorizationUrl(state.getStateToken());

    return new GoogleOAuthStartResponse(
      "google_calendar",
      authorizationUrl,
      state.getStateToken(),
      googleCalendarClient.redirectUri()
    );
  }

  @Transactional
  public GoogleOAuthCallbackResponse handleCalendarCallback(String code, String stateToken) {
    ensureConfigured();

    OAuthState state = oAuthStateRepository.findByStateToken(stateToken)
      .orElseThrow(() -> new EntityNotFoundException("OAuth state not found"));

    if (state.getConsumedAt() != null) {
      throw new IllegalStateException("OAuth state already consumed");
    }
    if (state.getExpiresAt().isBefore(Instant.now())) {
      throw new IllegalStateException("OAuth state expired");
    }

    CalendarConnection connection = state.getCalendarConnection();
    if (connection == null) {
      throw new IllegalStateException("OAuth state is not linked to a calendar connection");
    }

    GoogleCalendarClient.GoogleTokenExchangeResult tokenResult = googleCalendarClient.exchangeCodeForToken(code);
    String email = officialEmailPolicyService.requireOfficialEmail(googleCalendarClient.getUserEmail(tokenResult.accessToken()));
    List<GoogleCalendarItemResponse> calendars = googleCalendarClient.fetchCalendars(tokenResult.accessToken());
    GoogleCalendarItemResponse selected = calendars
      .stream()
      .filter(item -> item.id().equals(connection.getProviderCalendarId()))
      .findFirst()
      .orElseGet(() -> calendars.stream().filter(GoogleCalendarItemResponse::primary).findFirst().orElse(null));

    googleCalendarCredentialService.store(
      connection,
      tokenResult.accessToken(),
      tokenResult.refreshToken(),
      tokenResult.expiresAt()
    );
    connection.setProviderAccountEmail(email);
    if (selected != null) {
      connection.setProviderCalendarId(selected.id());
      connection.setCalendarName(selected.summary());
      connection.setStatus("connected");
    } else {
      connection.setStatus("calendar_selection_required");
    }
    connection.setUpdatedAt(Instant.now());
    calendarConnectionRepository.save(connection);

    state.setConsumedAt(Instant.now());
    oAuthStateRepository.save(state);

    return new GoogleOAuthCallbackResponse(
      connection.getId(),
      "google_calendar",
      connection.getStatus(),
      connection.getProviderAccountEmail(),
      connection.getProviderCalendarId(),
      connection.getCalendarName(),
      calendars
    );
  }

  @Transactional(readOnly = true)
  public List<GoogleCalendarItemResponse> listAvailableCalendars(UUID connectionId) {
    CalendarConnection connection = calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));

    String accessToken = googleCalendarAccessService.requireValidAccessToken(connection.getId());
    return googleCalendarClient.fetchCalendars(accessToken);
  }

  private void ensureConfigured() {
    googleCalendarClient.ensureConfigured();
    googleOAuthTokenCipher.ensureConfigured();
  }
}
