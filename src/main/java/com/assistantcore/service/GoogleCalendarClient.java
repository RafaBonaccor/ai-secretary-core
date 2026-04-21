package com.assistantcore.service;

import com.assistantcore.dto.GoogleCalendarItemResponse;
import com.assistantcore.dto.GoogleCalendarEventMutationRequest;
import com.assistantcore.dto.GoogleCalendarEventResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GoogleCalendarClient {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarClient.class);

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GoogleCalendarClient(
    RestClient.Builder restClientBuilder,
    ObjectMapper objectMapper,
    @Value("${app.google.oauth.client-id:}") String clientId,
    @Value("${app.google.oauth.client-secret:}") String clientSecret,
    @Value("${app.google.oauth.redirect-uri:http://127.0.0.1:8090/api/v1/oauth/google/calendar/callback}") String redirectUri
  ) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
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
      .build()
      .encode()
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

  public GoogleTokenExchangeResult refreshAccessToken(String refreshToken) {
    ensureConfigured();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("refresh_token", refreshToken);
    form.add("grant_type", "refresh_token");

    @SuppressWarnings("unchecked")
    Map<String, Object> response = restClient
      .post()
      .uri("https://oauth2.googleapis.com/token")
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(Map.class);

    if (response == null || response.get("access_token") == null) {
      throw new IllegalStateException("Google refresh token exchange did not return an access token");
    }

    String accessToken = stringValue(response.get("access_token"));
    String refreshedRefreshToken = stringValue(response.get("refresh_token"));
    Integer expiresIn = integerValue(response.get("expires_in"));
    Instant expiresAt = expiresIn == null ? null : Instant.now().plusSeconds(expiresIn);
    return new GoogleTokenExchangeResult(
      accessToken,
      refreshedRefreshToken == null || refreshedRefreshToken.isBlank() ? refreshToken : refreshedRefreshToken,
      expiresAt
    );
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

  public List<GoogleCalendarEventResponse> fetchEvents(
    String accessToken,
    String calendarId,
    Instant timeMin,
    Instant timeMax,
    Integer maxResults
  ) {
    UriComponentsBuilder builder = UriComponentsBuilder
      .fromUriString("https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events")
      .queryParam("singleEvents", "true")
      .queryParam("orderBy", "startTime");

    if (timeMin != null) {
      builder.queryParam("timeMin", timeMin.toString());
    }
    if (timeMax != null) {
      builder.queryParam("timeMax", timeMax.toString());
    }
    if (maxResults != null && maxResults > 0) {
      builder.queryParam("maxResults", maxResults);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> response = restClient
      .get()
      .uri(builder.buildAndExpand(Map.of("calendarId", calendarId)).encode().toUri())
      .header("Authorization", "Bearer " + accessToken)
      .retrieve()
      .body(Map.class);

    if (response == null || response.get("items") == null) {
      throw new IllegalStateException("Google events list response was empty");
    }

    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
    List<GoogleCalendarEventResponse> events = new ArrayList<>();
    for (Map<String, Object> item : items) {
      events.add(toEventResponse(item));
    }
    return events;
  }

  public GoogleCalendarEventResponse createEvent(
    String accessToken,
    String calendarId,
    GoogleCalendarEventMutationRequest request
  ) {
    List<Map<String, Object>> payloadAttempts = List.of(
      buildEventPayload(request, false, true, true),
      buildEventPayload(request, false, false, true),
      buildEventPayload(request, false, false, false)
    );

    IllegalStateException lastBadRequest = null;
    for (int attempt = 0; attempt < payloadAttempts.size(); attempt++) {
      Map<String, Object> payload = payloadAttempts.get(attempt);
      try {
        Map<String, Object> response = executeJsonRequest(
          HttpRequest.newBuilder(eventCollectionUri(calendarId))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload))),
          Map.class
        );

        if (response == null) {
          throw new IllegalStateException("Google create event response was empty");
        }

        if (attempt > 0) {
          log.warn("Google Calendar createEvent succeeded only after simplified payload attempt {}", attempt + 1);
        }
        return toEventResponse(response);
      } catch (GoogleApiBadRequestException badRequest) {
        lastBadRequest = new IllegalStateException("Google Calendar createEvent bad request: " + badRequest.responseBody());
        log.warn(
          "Google Calendar createEvent bad request on attempt {} with payload keys={}: {}",
          attempt + 1,
          payload.keySet(),
          badRequest.responseBody()
        );
      }
    }

    throw lastBadRequest == null ? new IllegalStateException("Google create event failed without response") : lastBadRequest;
  }

  public GoogleCalendarEventResponse updateEvent(
    String accessToken,
    String calendarId,
    String eventId,
    GoogleCalendarEventMutationRequest request
  ) {
    Map<String, Object> response = executeJsonRequest(
      HttpRequest.newBuilder(eventItemUri(calendarId, eventId))
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
        .method("PATCH", HttpRequest.BodyPublishers.ofString(writeJson(buildEventPayload(request, true, true, true)))),
      Map.class
    );

    if (response == null) {
      throw new IllegalStateException("Google update event response was empty");
    }

    return toEventResponse(response);
  }

  public void deleteEvent(String accessToken, String calendarId, String eventId) {
    restClient
      .delete()
      .uri(eventItemUri(calendarId, eventId))
      .header("Authorization", "Bearer " + accessToken)
      .retrieve()
      .toBodilessEntity();
  }

  public void revokeToken(String token) {
    if (!hasText(token)) {
      return;
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", token.trim());

    try {
      restClient
        .post()
        .uri("https://oauth2.googleapis.com/revoke")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
    } catch (HttpClientErrorException exception) {
      log.warn("Google OAuth token revoke returned {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
    } catch (Exception exception) {
      log.warn("Google OAuth token revoke failed: {}", exception.getMessage());
    }
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

  private URI eventCollectionUri(String calendarId) {
    return URI.create("https://www.googleapis.com/calendar/v3/calendars/" + encodePathSegment(calendarId) + "/events");
  }

  private URI eventItemUri(String calendarId, String eventId) {
    return URI.create(
      "https://www.googleapis.com/calendar/v3/calendars/" +
      encodePathSegment(calendarId) +
      "/events/" +
      encodePathSegment(eventId)
    );
  }

  private Map<String, Object> buildEventPayload(
    GoogleCalendarEventMutationRequest request,
    boolean partial,
    boolean includePrivateMetadata,
    boolean includeDescription
  ) {
    Map<String, Object> payload = new LinkedHashMap<>();

    if (!partial || hasText(request.summary())) {
      payload.put("summary", request.summary());
    }
    if (includeDescription && (!partial || hasText(request.description()))) {
      payload.put("description", request.description());
    }
    if (!partial || hasText(request.startDateTime())) {
      payload.put("start", buildDateTimeBlock(request.startDateTime(), request.timeZone()));
    }
    if (!partial || hasText(request.endDateTime())) {
      payload.put("end", buildDateTimeBlock(request.endDateTime(), request.timeZone()));
    }
    if (includePrivateMetadata && request.privateMetadata() != null && !request.privateMetadata().isEmpty()) {
      payload.put("extendedProperties", Map.of("private", request.privateMetadata()));
    }

    return payload;
  }

  private Map<String, Object> buildDateTimeBlock(String dateTime, String timeZone) {
    Map<String, Object> block = new LinkedHashMap<>();
    block.put("dateTime", normalizeGoogleDateTime(dateTime));
    if (hasText(timeZone)) {
      block.put("timeZone", timeZone);
    }
    return block;
  }

  private String normalizeGoogleDateTime(String value) {
    if (!hasText(value)) {
      return value;
    }

    try {
      return OffsetDateTime.parse(value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    } catch (DateTimeParseException ignored) {}

    try {
      return Instant.parse(value).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    } catch (DateTimeParseException ignored) {}

    try {
      return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    } catch (DateTimeParseException ignored) {}

    return value;
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize Google Calendar payload", exception);
    }
  }

  private String encodePathSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private <T> T executeJsonRequest(HttpRequest.Builder requestBuilder, Class<T> responseType) {
    try {
      HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        if (response.statusCode() == 400) {
          throw new GoogleApiBadRequestException(response.body(), response.headers().map());
        }
        throw new IllegalStateException("Google API request failed: " + response.statusCode() + " " + response.body());
      }
      return objectMapper.readValue(response.body(), responseType);
    } catch (GoogleApiBadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute Google API request", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private GoogleCalendarEventResponse toEventResponse(Map<String, Object> item) {
    Map<String, Object> start = (Map<String, Object>) item.get("start");
    Map<String, Object> end = (Map<String, Object>) item.get("end");
    Map<String, String> privateMetadata = extractPrivateMetadata((Map<String, Object>) item.get("extendedProperties"));

    return new GoogleCalendarEventResponse(
      stringValue(item.get("id")),
      stringValue(item.get("summary")),
      stringValue(item.get("description")),
      extractDateValue(start),
      extractDateValue(end),
      stringValue(item.get("status")),
      stringValue(item.get("htmlLink")),
      privateMetadata
    );
  }

  private String extractDateValue(Map<String, Object> block) {
    if (block == null) {
      return null;
    }
    String dateTime = stringValue(block.get("dateTime"));
    return dateTime != null ? dateTime : stringValue(block.get("date"));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> extractPrivateMetadata(Map<String, Object> extendedProperties) {
    if (extendedProperties == null) {
      return Map.of();
    }

    Object rawPrivate = extendedProperties.get("private");
    if (!(rawPrivate instanceof Map<?, ?> privateMap) || privateMap.isEmpty()) {
      return Map.of();
    }

    Map<String, String> metadata = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : privateMap.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      metadata.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
    }
    return metadata;
  }

  public record GoogleTokenExchangeResult(String accessToken, String refreshToken, Instant expiresAt) {}

  private static final class GoogleApiBadRequestException extends RuntimeException {
    private final String responseBody;
    private final Map<String, List<String>> headers;

    private GoogleApiBadRequestException(String responseBody, Map<String, List<String>> headers) {
      super(responseBody);
      this.responseBody = responseBody;
      this.headers = headers;
    }

    private String responseBody() {
      return responseBody;
    }

    private Map<String, List<String>> headers() {
      return headers;
    }
  }
}
