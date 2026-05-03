package com.assistantcore.service;

import com.assistantcore.dto.CalendarEventMutationRequest;
import com.assistantcore.dto.CalendarEventResponse;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.InternalCalendarEvent;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.InternalCalendarEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalCalendarEventService implements CalendarEventService {

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private static final String CANCELLED_STATUS = "cancelled";

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final InternalCalendarEventRepository internalCalendarEventRepository;
  private final ObjectMapper objectMapper;

  public InternalCalendarEventService(
    CalendarConnectionRepository calendarConnectionRepository,
    InternalCalendarEventRepository internalCalendarEventRepository,
    ObjectMapper objectMapper
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.internalCalendarEventRepository = internalCalendarEventRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CalendarEventResponse> listEvents(UUID tenantId, Instant timeMin, Instant timeMax, Integer maxResults) {
    return listEventsByConnection(requireConnectedConnection(tenantId).getId(), timeMin, timeMax, maxResults);
  }

  @Transactional(readOnly = true)
  public List<CalendarEventResponse> listEventsByConnection(UUID connectionId, Instant timeMin, Instant timeMax, Integer maxResults) {
    CalendarConnection connection = getConnection(connectionId);
    Instant resolvedTimeMin = timeMin == null ? Instant.now() : timeMin;
    Instant resolvedTimeMax = timeMax == null ? resolvedTimeMin.plus(14, ChronoUnit.DAYS) : timeMax;
    int resolvedMaxResults = maxResults == null || maxResults <= 0 ? 20 : Math.min(maxResults, 250);

    return internalCalendarEventRepository
      .findByCalendarConnectionIdAndStatusNotIgnoreCaseAndEndAtAfterAndStartAtBeforeOrderByStartAtAsc(
        connection.getId(),
        CANCELLED_STATUS,
        resolvedTimeMin,
        resolvedTimeMax
      )
      .stream()
      .limit(resolvedMaxResults)
      .map(event -> toResponse(event, connection))
      .toList();
  }

  @Override
  @Transactional
  public CalendarEventResponse createEvent(UUID tenantId, CalendarEventMutationRequest request) {
    return createEventForConnection(requireConnectedConnection(tenantId).getId(), request);
  }

  @Transactional
  public CalendarEventResponse createEventForConnection(UUID connectionId, CalendarEventMutationRequest request) {
    if (!hasText(request.summary()) || !hasText(request.startDateTime()) || !hasText(request.endDateTime())) {
      throw new IllegalArgumentException("summary, startDateTime and endDateTime are required to create a calendar event");
    }

    CalendarConnection connection = getConnection(connectionId);
    Instant startAt = parseToInstant(request.startDateTime(), request.timeZone());
    Instant endAt = parseToInstant(request.endDateTime(), request.timeZone());
    if (!endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("endDateTime must be after startDateTime");
    }

    Map<String, String> metadata = mergeMetadata(Map.of(), request);
    Instant now = Instant.now();

    InternalCalendarEvent event = new InternalCalendarEvent();
    event.setId(UUID.randomUUID());
    event.setCalendarConnection(connection);
    event.setStatus("confirmed");
    event.setSource(normalizeOrDefault(request.source(), "manual"));
    event.setSummary(request.summary().trim());
    event.setDescription(blankToNull(request.description()));
    event.setStartAt(startAt);
    event.setEndAt(endAt);
    event.setTimezone(blankToNull(request.timeZone()));
    event.setCustomerName(blankToNull(firstNonBlank(request.customerName(), metadata.get("customerName"))));
    event.setCustomerPhone(blankToNull(firstNonBlank(request.customerPhone(), metadata.get("customerPhone"))));
    event.setCustomerRemoteJid(blankToNull(firstNonBlank(request.customerRemoteJid(), metadata.get("customerRemoteJid"))));
    event.setAppointmentTypeSlug(blankToNull(firstNonBlank(request.appointmentType(), metadata.get("appointmentType"))));
    event.setPrivateMetadataJson(writeMetadata(metadata));
    event.setCreatedAt(now);
    event.setUpdatedAt(now);

    return toResponse(internalCalendarEventRepository.save(event), connection);
  }

  @Override
  @Transactional
  public CalendarEventResponse updateEvent(UUID tenantId, String eventId, CalendarEventMutationRequest request) {
    InternalCalendarEvent event = internalCalendarEventRepository.findByCalendarConnectionTenantIdAndId(tenantId, parseEventId(eventId))
      .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + eventId));

    return updateEventEntity(event, request);
  }

  @Transactional
  public CalendarEventResponse updateEventForConnection(UUID connectionId, String eventId, CalendarEventMutationRequest request) {
    InternalCalendarEvent event = internalCalendarEventRepository.findByCalendarConnectionIdAndId(connectionId, parseEventId(eventId))
      .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + eventId));

    return updateEventEntity(event, request);
  }

  @Override
  @Transactional
  public void deleteEvent(UUID tenantId, String eventId) {
    InternalCalendarEvent event = internalCalendarEventRepository.findByCalendarConnectionTenantIdAndId(tenantId, parseEventId(eventId))
      .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + eventId));
    cancelEvent(event);
  }

  @Transactional
  public void deleteEventForConnection(UUID connectionId, String eventId) {
    InternalCalendarEvent event = internalCalendarEventRepository.findByCalendarConnectionIdAndId(connectionId, parseEventId(eventId))
      .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + eventId));
    cancelEvent(event);
  }

  @Override
  @Transactional(readOnly = true)
  public UpcomingEventsContext buildUpcomingEventsContext(UUID tenantId) {
    CalendarConnection connection = calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .orElse(null);

    if (connection == null) {
      return new UpcomingEventsContext(false, null, List.of());
    }

    List<CalendarEventResponse> events = listEventsByConnection(connection.getId(), Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS), 8);
    return new UpcomingEventsContext(true, connection.getCalendarName(), events);
  }

  private CalendarEventResponse updateEventEntity(InternalCalendarEvent event, CalendarEventMutationRequest request) {
    if (hasText(request.summary())) {
      event.setSummary(request.summary().trim());
    }
    if (hasText(request.description())) {
      event.setDescription(request.description().trim());
    }
    if (hasText(request.startDateTime())) {
      event.setStartAt(parseToInstant(request.startDateTime(), firstNonBlank(request.timeZone(), event.getTimezone())));
    }
    if (hasText(request.endDateTime())) {
      event.setEndAt(parseToInstant(request.endDateTime(), firstNonBlank(request.timeZone(), event.getTimezone())));
    }
    if (!event.getEndAt().isAfter(event.getStartAt())) {
      throw new IllegalArgumentException("endDateTime must be after startDateTime");
    }
    if (hasText(request.timeZone())) {
      event.setTimezone(request.timeZone().trim());
    }
    if (hasText(request.source())) {
      event.setSource(normalizeOrDefault(request.source(), event.getSource()));
    }
    if (hasText(request.customerName())) {
      event.setCustomerName(request.customerName().trim());
    }
    if (hasText(request.customerPhone())) {
      event.setCustomerPhone(request.customerPhone().trim());
    }
    if (hasText(request.customerRemoteJid())) {
      event.setCustomerRemoteJid(request.customerRemoteJid().trim());
    }
    if (hasText(request.appointmentType())) {
      event.setAppointmentTypeSlug(request.appointmentType().trim());
    }

    Map<String, String> metadata = mergeMetadata(readMetadata(event.getPrivateMetadataJson()), request);
    event.setPrivateMetadataJson(writeMetadata(metadata));
    event.setUpdatedAt(Instant.now());

    return toResponse(internalCalendarEventRepository.save(event), event.getCalendarConnection());
  }

  private void cancelEvent(InternalCalendarEvent event) {
    event.setStatus(CANCELLED_STATUS);
    event.setUpdatedAt(Instant.now());
    internalCalendarEventRepository.save(event);
  }

  private CalendarConnection requireConnectedConnection(UUID tenantId) {
    return calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .orElseThrow(() -> new EntityNotFoundException("No connected internal calendar found for tenant: " + tenantId));
  }

  private CalendarConnection getConnection(UUID connectionId) {
    return calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));
  }

  private CalendarEventResponse toResponse(InternalCalendarEvent event, CalendarConnection connection) {
    Map<String, String> metadata = readMetadata(event.getPrivateMetadataJson());
    if (hasText(event.getCustomerPhone())) {
      metadata.put("customerPhone", event.getCustomerPhone().trim());
    }
    if (hasText(event.getCustomerName())) {
      metadata.put("customerName", event.getCustomerName().trim());
    }
    if (hasText(event.getCustomerRemoteJid())) {
      metadata.put("customerRemoteJid", event.getCustomerRemoteJid().trim());
    }
    if (hasText(event.getAppointmentTypeSlug())) {
      metadata.put("appointmentType", event.getAppointmentTypeSlug().trim());
    }
    if (hasText(event.getSource())) {
      metadata.put("source", event.getSource().trim());
    }

    return new CalendarEventResponse(
      event.getId().toString(),
      event.getSummary(),
      event.getDescription(),
      formatDateTime(event.getStartAt(), firstNonBlank(event.getTimezone(), tenantTimezone(connection))),
      formatDateTime(event.getEndAt(), firstNonBlank(event.getTimezone(), tenantTimezone(connection))),
      event.getStatus(),
      event.getSource(),
      event.getCustomerName(),
      event.getCustomerPhone(),
      event.getCustomerRemoteJid(),
      event.getAppointmentTypeSlug(),
      metadata
    );
  }

  private Map<String, String> mergeMetadata(Map<String, String> existing, CalendarEventMutationRequest request) {
    Map<String, String> metadata = new LinkedHashMap<>(existing);
    if (request.privateMetadata() != null) {
      request.privateMetadata().forEach((key, value) -> {
        if (hasText(key) && hasText(value)) {
          metadata.put(key.trim(), value.trim());
        }
      });
    }
    if (hasText(request.customerName())) {
      metadata.put("customerName", request.customerName().trim());
    }
    if (hasText(request.customerPhone())) {
      metadata.put("customerPhone", request.customerPhone().trim());
    }
    if (hasText(request.customerRemoteJid())) {
      metadata.put("customerRemoteJid", request.customerRemoteJid().trim());
    }
    if (hasText(request.appointmentType())) {
      metadata.put("appointmentType", request.appointmentType().trim());
    }
    if (hasText(request.source())) {
      metadata.put("source", normalizeOrDefault(request.source(), "manual"));
    }
    return metadata;
  }

  private Map<String, String> readMetadata(String value) {
    if (!hasText(value)) {
      return new LinkedHashMap<>();
    }

    try {
      return new LinkedHashMap<>(objectMapper.readValue(value, STRING_MAP));
    } catch (Exception exception) {
      return new LinkedHashMap<>();
    }
  }

  private String writeMetadata(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize calendar event metadata", exception);
    }
  }

  private Instant parseToInstant(String value, String timeZone) {
    if (!hasText(value)) {
      throw new IllegalArgumentException("Calendar date-time value is required");
    }

    try {
      return OffsetDateTime.parse(value.trim()).toInstant();
    } catch (DateTimeParseException ignored) {}

    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ignored) {}

    try {
      ZoneId zone = resolveZone(timeZone);
      return LocalDateTime.parse(value.trim()).atZone(zone).toInstant();
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Calendar date-time must be a valid ISO 8601 value");
    }
  }

  private String formatDateTime(Instant value, String timeZone) {
    return value.atZone(resolveZone(timeZone)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private ZoneId resolveZone(String timeZone) {
    try {
      return hasText(timeZone) ? ZoneId.of(timeZone.trim()) : ZoneOffset.UTC;
    } catch (Exception exception) {
      return ZoneOffset.UTC;
    }
  }

  private UUID parseEventId(String eventId) {
    try {
      return UUID.fromString(eventId.trim());
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid calendar event id: " + eventId);
    }
  }

  private String tenantTimezone(CalendarConnection connection) {
    return connection.getTenant() == null ? null : connection.getTenant().getTimezone();
  }

  private String normalizeOrDefault(String value, String fallback) {
    return hasText(value)
      ? value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_')
      : fallback;
  }

  private String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private String firstNonBlank(String first, String second) {
    return hasText(first) ? first.trim() : hasText(second) ? second.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
