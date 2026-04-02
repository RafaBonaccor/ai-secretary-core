package com.assistantcore.service;

import com.assistantcore.dto.GoogleCalendarItemResponse;
import com.assistantcore.dto.GoogleOAuthCallbackResponse;
import com.assistantcore.dto.GoogleOAuthStartResponse;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.OAuthState;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.OAuthStateRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;

@Service
public class GoogleOAuthService {

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final OAuthStateRepository oAuthStateRepository;
  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GoogleOAuthService(
    CalendarConnectionRepository calendarConnectionRepository,
    OAuthStateRepository oAuthStateRepository,
    RestClient.Builder restClientBuilder,
    @Value("${app.google.oauth.client-id:}") String clientId,
    @Value("${app.google.oauth.client-secret:}") String clientSecret,
    @Value("${app.google.oauth.redirect-uri:http://127.0.0.1:8090/api/v1/oauth/google/calendar/callback}") String redirectUri
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.oAuthStateRepository = oAuthStateRepository;
    this.restClient = restClientBuilder.build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
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

    String authorizationUrl = UriComponentsBuilder
      .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
      .queryParam("client_id", clientId)
      .queryParam("redirect_uri", redirectUri)
      .queryParam("response_type", "code")
      .queryParam("scope", String.join(
        " ",
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/calendar.readonly",
        "https://www.googleapis.com/auth/calendar.events"
      ))
      .queryParam("access_type", "offline")
      .queryParam("prompt", "consent")
      .queryParam("include_granted_scopes", "true")
      .queryParam("state", state.getStateToken())
      .build(true)
      .toUriString();

    return new GoogleOAuthStartResponse("google_calendar", authorizationUrl, state.getStateToken(), redirectUri);
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

    Map<String, Object> tokenResponse = exchangeCodeForToken(code);
    String accessToken = stringValue(tokenResponse.get("access_token"));
    String refreshToken = stringValue(tokenResponse.get("refresh_token"));
    Integer expiresIn = integerValue(tokenResponse.get("expires_in"));

    Map<String, Object> userInfo = getUserInfo(accessToken);
    String email = stringValue(userInfo.get("email"));

    List<GoogleCalendarItemResponse> calendars = fetchCalendars(accessToken);
    GoogleCalendarItemResponse selected = calendars
      .stream()
      .filter(item -> item.id().equals(connection.getGoogleCalendarId()))
      .findFirst()
      .orElseGet(() -> calendars.stream().filter(GoogleCalendarItemResponse::primary).findFirst().orElse(null));

    connection.setAccessToken(accessToken);
    if (refreshToken != null && !refreshToken.isBlank()) {
      connection.setRefreshToken(refreshToken);
    }
    connection.setTokenExpiresAt(expiresIn == null ? null : Instant.now().plusSeconds(expiresIn));
    connection.setGoogleAccountEmail(email);
    if (selected != null) {
      connection.setGoogleCalendarId(selected.id());
      connection.setGoogleCalendarName(selected.summary());
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
      connection.getGoogleAccountEmail(),
      connection.getGoogleCalendarId(),
      connection.getGoogleCalendarName(),
      calendars
    );
  }

  @Transactional(readOnly = true)
  public List<GoogleCalendarItemResponse> listAvailableCalendars(UUID connectionId) {
    CalendarConnection connection = calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));

    if (connection.getAccessToken() == null || connection.getAccessToken().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar connection is not authenticated with Google yet");
    }

    return fetchCalendars(connection.getAccessToken());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> exchangeCodeForToken(String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("redirect_uri", redirectUri);
    form.add("grant_type", "authorization_code");

    Map<String, Object> response = restClient
      .post()
      .uri("https://oauth2.googleapis.com/token")
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(Map.class);

    if (response == null || response.get("access_token") == null) {
      throw new IllegalStateException("Google token exchange did not return an access token");
    }
    return response;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getUserInfo(String accessToken) {
    Map<String, Object> response = restClient
      .get()
      .uri("https://www.googleapis.com/oauth2/v3/userinfo")
      .header("Authorization", "Bearer " + accessToken)
      .retrieve()
      .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("Google user info response was empty");
    }
    return response;
  }

  @SuppressWarnings("unchecked")
  private List<GoogleCalendarItemResponse> fetchCalendars(String accessToken) {
    Map<String, Object> response = restClient
      .get()
      .uri(URI.create("https://www.googleapis.com/calendar/v3/users/me/calendarList"))
      .header("Authorization", "Bearer " + accessToken)
      .retrieve()
      .body(Map.class);

    if (response == null || response.get("items") == null) {
      throw new IllegalStateException("Google calendar list response was empty");
    }

    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
    return items
      .stream()
      .map(item -> new GoogleCalendarItemResponse(
        stringValue(item.get("id")),
        stringValue(item.get("summary")),
        stringValue(item.get("timeZone")),
        Boolean.TRUE.equals(item.get("primary")),
        Boolean.TRUE.equals(item.get("selected"))
      ))
      .toList();
  }

  private void ensureConfigured() {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Google OAuth is not configured. Set GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET."
      );
    }
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Integer integerValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }
}
