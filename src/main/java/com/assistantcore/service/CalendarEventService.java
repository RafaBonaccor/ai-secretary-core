package com.assistantcore.service;

import com.assistantcore.dto.CalendarEventMutationRequest;
import com.assistantcore.dto.CalendarEventResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarEventService {
  List<CalendarEventResponse> listEvents(UUID tenantId, Instant timeMin, Instant timeMax, Integer maxResults);

  CalendarEventResponse createEvent(UUID tenantId, CalendarEventMutationRequest request);

  CalendarEventResponse updateEvent(UUID tenantId, String eventId, CalendarEventMutationRequest request);

  void deleteEvent(UUID tenantId, String eventId);

  UpcomingEventsContext buildUpcomingEventsContext(UUID tenantId);

  record UpcomingEventsContext(
    boolean connected,
    String calendarName,
    List<CalendarEventResponse> events
  ) {}
}
