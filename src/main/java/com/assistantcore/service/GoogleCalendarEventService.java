package com.assistantcore.service;

import com.assistantcore.dto.GoogleCalendarEventMutationRequest;
import com.assistantcore.dto.GoogleCalendarEventResponse;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.repository.CalendarConnectionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarEventService {

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final GoogleCalendarAccessService googleCalendarAccessService;
  private final GoogleCalendarClient googleCalendarClient;

  public GoogleCalendarEventService(
    CalendarConnectionRepository calendarConnectionRepository,
    GoogleCalendarAccessService googleCalendarAccessService,
    GoogleCalendarClient googleCalendarClient
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.googleCalendarAccessService = googleCalendarAccessService;
    this.googleCalendarClient = googleCalendarClient;
  }

  @Transactional(readOnly = true)
  public List<GoogleCalendarEventResponse> listEvents(UUID tenantId, Instant timeMin, Instant timeMax, Integer maxResults) {
    CalendarConnection connection = requireConnectedConnection(tenantId);
    String accessToken = googleCalendarAccessService.requireValidAccessToken(connection.getId());

    Instant resolvedTimeMin = timeMin == null ? Instant.now() : timeMin;
    Instant resolvedTimeMax = timeMax == null ? resolvedTimeMin.plus(14, ChronoUnit.DAYS) : timeMax;
    int resolvedMaxResults = maxResults == null || maxResults <= 0 ? 10 : Math.min(maxResults, 50);

    return googleCalendarClient.fetchEvents(
      accessToken,
      resolvedCalendarId(connection),
      resolvedTimeMin,
      resolvedTimeMax,
      resolvedMaxResults
    );
  }

  @Transactional
  public GoogleCalendarEventResponse createEvent(UUID tenantId, GoogleCalendarEventMutationRequest request) {
    if (!hasText(request.summary()) || !hasText(request.startDateTime()) || !hasText(request.endDateTime())) {
      throw new IllegalArgumentException("summary, startDateTime and endDateTime are required to create a calendar event");
    }

    CalendarConnection connection = requireConnectedConnection(tenantId);
    String accessToken = googleCalendarAccessService.requireValidAccessToken(connection.getId());
    return googleCalendarClient.createEvent(accessToken, resolvedCalendarId(connection), request);
  }

  @Transactional
  public GoogleCalendarEventResponse updateEvent(UUID tenantId, String eventId, GoogleCalendarEventMutationRequest request) {
    if (!hasText(eventId)) {
      throw new IllegalArgumentException("eventId is required to update a calendar event");
    }

    CalendarConnection connection = requireConnectedConnection(tenantId);
    String accessToken = googleCalendarAccessService.requireValidAccessToken(connection.getId());
    return googleCalendarClient.updateEvent(accessToken, resolvedCalendarId(connection), eventId.trim(), request);
  }

  @Transactional
  public void deleteEvent(UUID tenantId, String eventId) {
    if (!hasText(eventId)) {
      throw new IllegalArgumentException("eventId is required to delete a calendar event");
    }

    CalendarConnection connection = requireConnectedConnection(tenantId);
    String accessToken = googleCalendarAccessService.requireValidAccessToken(connection.getId());
    googleCalendarClient.deleteEvent(accessToken, resolvedCalendarId(connection), eventId.trim());
  }

  @Transactional(readOnly = true)
  public UpcomingEventsContext buildUpcomingEventsContext(UUID tenantId) {
    CalendarConnection connection = calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .orElse(null);

    if (connection == null) {
      return new UpcomingEventsContext(false, null, List.of());
    }

    try {
      List<GoogleCalendarEventResponse> events = listEvents(tenantId, Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS), 8);
      return new UpcomingEventsContext(true, connection.getCalendarName(), events);
    } catch (Exception exception) {
      return new UpcomingEventsContext(true, connection.getCalendarName(), List.of());
    }
  }

  private CalendarConnection requireConnectedConnection(UUID tenantId) {
    return calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .orElseThrow(() -> new EntityNotFoundException("No connected Google Calendar found for tenant: " + tenantId));
  }

  private String resolvedCalendarId(CalendarConnection connection) {
    return hasText(connection.getProviderCalendarId()) ? connection.getProviderCalendarId().trim() : "primary";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record UpcomingEventsContext(
    boolean connected,
    String calendarName,
    List<GoogleCalendarEventResponse> events
  ) {}
}
