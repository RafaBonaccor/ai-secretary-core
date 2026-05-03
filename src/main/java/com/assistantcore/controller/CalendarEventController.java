package com.assistantcore.controller;

import com.assistantcore.dto.CalendarEventMutationRequest;
import com.assistantcore.dto.CalendarEventResponse;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.InternalCalendarEventService;
import com.assistantcore.service.SubscriptionEntitlementService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar-connections")
public class CalendarEventController {

  private final InternalCalendarEventService internalCalendarEventService;
  private final AppAuthorizationService appAuthorizationService;
  private final SubscriptionEntitlementService subscriptionEntitlementService;

  public CalendarEventController(
    InternalCalendarEventService internalCalendarEventService,
    AppAuthorizationService appAuthorizationService,
    SubscriptionEntitlementService subscriptionEntitlementService
  ) {
    this.internalCalendarEventService = internalCalendarEventService;
    this.appAuthorizationService = appAuthorizationService;
    this.subscriptionEntitlementService = subscriptionEntitlementService;
  }

  @GetMapping("/{connectionId}/events")
  public List<CalendarEventResponse> listEvents(
    @PathVariable UUID connectionId,
    @RequestParam(required = false) String timeMin,
    @RequestParam(required = false) String timeMax,
    @RequestParam(required = false) Integer maxResults
  ) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return internalCalendarEventService.listEventsByConnection(connectionId, parseInstant(timeMin), parseInstant(timeMax), maxResults);
  }

  @PostMapping("/{connectionId}/events")
  @ResponseStatus(HttpStatus.CREATED)
  public CalendarEventResponse createEvent(@PathVariable UUID connectionId, @RequestBody CalendarEventMutationRequest request) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return internalCalendarEventService.createEventForConnection(connectionId, request);
  }

  @PutMapping("/{connectionId}/events/{eventId}")
  public CalendarEventResponse updateEvent(
    @PathVariable UUID connectionId,
    @PathVariable String eventId,
    @RequestBody CalendarEventMutationRequest request
  ) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return internalCalendarEventService.updateEventForConnection(connectionId, eventId, request);
  }

  @DeleteMapping("/{connectionId}/events/{eventId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable UUID connectionId, @PathVariable String eventId) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    internalCalendarEventService.deleteEventForConnection(connectionId, eventId);
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Instant.parse(value.trim());
  }
}
