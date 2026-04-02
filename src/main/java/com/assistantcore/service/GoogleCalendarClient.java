package com.assistantcore.service;

import com.assistantcore.dto.GoogleCalendarItemResponse;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GoogleCalendarClient {

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GoogleCalendarClient(
    RestClient.Builder restClientBuilder,
    @Value("${app.google.oauth.client-id:}") String clientId,
    @Value("${app.google.oauth.client-secret:}") String clientSecret,
    @Value("${app.google.oauth.redirect-uri:http://127.0.0.1:8090/api/v1/oauth/google/calendar/callback}") String redirectUri
  ) {
    this.restClient = restClientBuilder.build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  public void ensureConfigured() {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Google OAuth is not configured. Set GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET."
      );
    }
  }

  public String authorizationUrl(String stateToken) {
    ensureConfigured();

    return UriComponentsBuilder
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
      .queryParam("state", stateToken)
      .build(true)
      .toUriString();
  }

  public GoogleTokenExchangeResult exchangeCodeForToken(String code) {
    ensureConfigured();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("redirect_uri", redirectUri);
    form.add("grant_type", "authorization_code");

    @SuppressWarnings("unchecked")
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

    String accessToken = stringValue(response.get("access_token"));
    String refreshToken = stringValue(response.get("refresh_token"));
    Integer expiresIn = integerValue(response.get("expires_in"));
    Instant expiresAt = expiresIn == null ? null : Instant.now().plusSeconds(expiresIn);
    return new GoogleTokenExchangeResult(accessToken, refreshToken, expiresAt);
  }

  public String getUserEmail(String accessToken) {
    @SuppressWarnings("unchecked")
    Map<String, Object> response = restClient
      .get()
      .uri("https://www.googleapis.com/oauth2/v3/userinfo")
      .header("Authorization", "Bearer " + accessToken)
      .retrieve()
      .body(Map.class);

    if (response == null) {
      throw new IllegalStateException("Google user info response was empty");
    }

    return stringValue(response.get("email"));
  }

  public List<GoogleCalendarItemResponse> fetchCalendars(String accessToken) {
    @SuppressWarnings("unchecked")
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

  public String redirectUri() {
    return redirectUri;
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

  public record GoogleTokenExchangeResult(String accessToken, String refreshToken, Instant expiresAt) {}
}
