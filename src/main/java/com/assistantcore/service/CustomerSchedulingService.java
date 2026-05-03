package com.assistantcore.service;

import com.assistantcore.dto.CalendarEventMutationRequest;
import com.assistantcore.dto.CalendarEventResponse;
import com.assistantcore.model.AppointmentType;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.WorkingHour;
import com.assistantcore.repository.AppointmentTypeRepository;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.WorkingHourRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSchedulingService {

  private static final int DEFAULT_DURATION_MINUTES = 30;
  private static final int DEFAULT_SLOT_INTERVAL_MINUTES = 30;
  private static final int MAX_LOOKAHEAD_DAYS = 14;

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final WorkingHourRepository workingHourRepository;
  private final AppointmentTypeRepository appointmentTypeRepository;
  private final CalendarEventService calendarEventService;

  public CustomerSchedulingService(
    CalendarConnectionRepository calendarConnectionRepository,
    WorkingHourRepository workingHourRepository,
    AppointmentTypeRepository appointmentTypeRepository,
    CalendarEventService calendarEventService
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.workingHourRepository = workingHourRepository;
    this.appointmentTypeRepository = appointmentTypeRepository;
    this.calendarEventService = calendarEventService;
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public AvailabilityResult checkAvailability(UUID tenantId, AvailabilityRequest request) {
    CalendarConnection connection = requireConnectedConnection(tenantId);
    ZoneId businessZone = resolveBusinessZone(connection);
    OffsetDateTime desiredStart = requireDateTime(request.startDateTime(), "startDateTime", businessZone);
    int durationMinutes = resolveDurationMinutes(connection, request.appointmentType(), request.durationMinutes());
    OffsetDateTime desiredEnd = resolveEndDateTime(desiredStart, request.endDateTime(), durationMinutes, businessZone);

    List<CalendarEventResponse> events = calendarEventService.listEvents(
      tenantId,
      startOfDay(desiredStart).toInstant(),
      startOfDay(desiredStart).plusDays(MAX_LOOKAHEAD_DAYS).toInstant(),
      250
    );

    boolean withinWorkingHours = isWithinWorkingHours(connection, desiredStart, desiredEnd);
    boolean conflicts = hasConflict(events, desiredStart, desiredEnd, null);
    boolean available = withinWorkingHours && !conflicts;

    List<SuggestedSlot> suggestedSlots = available
      ? List.of()
      : suggestSlots(connection, events, desiredStart, durationMinutes, 3, null);

    String reason = available
      ? "available"
      : !withinWorkingHours
        ? "outside_working_hours"
        : "time_slot_unavailable";

    return new AvailabilityResult(
      available,
      reason,
      availabilityMessage(reason, suggestedSlots),
      desiredStart.toString(),
      desiredEnd.toString(),
      suggestedSlots
    );
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public List<CustomerBooking> listCustomerBookings(UUID tenantId, CustomerIdentity customerIdentity, Instant from, Instant to) {
    List<CalendarEventResponse> events = calendarEventService.listEvents(
      tenantId,
      from == null ? Instant.now().minus(30, ChronoUnit.DAYS) : from,
      to == null ? Instant.now().plus(180, ChronoUnit.DAYS) : to,
      250
    );

    return events
      .stream()
      .filter(event -> isOwnedByCustomer(event, customerIdentity))
      .sorted(Comparator.comparing(event -> parseEventStart(event).orElse(Instant.MAX)))
      .map(this::toCustomerBooking)
      .toList();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CustomerBooking createCustomerBooking(UUID tenantId, CustomerIdentity customerIdentity, BookingRequest request) {
    CalendarConnection connection = requireConnectedConnection(tenantId);
    ZoneId businessZone = resolveBusinessZone(connection);
    OffsetDateTime desiredStart = requireDateTime(request.startDateTime(), "startDateTime", businessZone);
    int durationMinutes = resolveDurationMinutes(connection, request.appointmentType(), request.durationMinutes());
    OffsetDateTime desiredEnd = resolveEndDateTime(desiredStart, request.endDateTime(), durationMinutes, businessZone);

    List<CalendarEventResponse> events = calendarEventService.listEvents(
      tenantId,
      startOfDay(desiredStart).toInstant(),
      startOfDay(desiredStart).plusDays(MAX_LOOKAHEAD_DAYS).toInstant(),
      250
    );
    ensureSlotAvailable(connection, events, desiredStart, desiredEnd, null);

    CalendarEventResponse created = calendarEventService.createEvent(
      tenantId,
      new CalendarEventMutationRequest(
        eventSummary(customerIdentity, request.appointmentType()),
        buildDescription(customerIdentity, request.notes(), request.appointmentType()),
        desiredStart.toString(),
        desiredEnd.toString(),
        connection.getTenant() == null ? null : connection.getTenant().getTimezone(),
        customerIdentity.customerName(),
        customerIdentity.phoneNumber(),
        customerIdentity.remoteJid(),
        request.appointmentType(),
        "whatsapp_assistant",
        buildMetadata(customerIdentity, tenantId, request.appointmentType())
      )
    );

    return toCustomerBooking(created);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CustomerBooking rescheduleCustomerBooking(UUID tenantId, CustomerIdentity customerIdentity, RescheduleRequest request) {
    CalendarConnection connection = requireConnectedConnection(tenantId);
    CalendarEventResponse existing = requireOwnedEvent(tenantId, customerIdentity, request.eventId());
    ZoneId businessZone = resolveBusinessZone(connection);

    OffsetDateTime currentStart = requireDateTime(existing.startDateTime(), "existing.startDateTime", businessZone);
    OffsetDateTime currentEnd = requireDateTime(existing.endDateTime(), "existing.endDateTime", businessZone);
    int existingDuration = Math.max(1, (int) ChronoUnit.MINUTES.between(currentStart, currentEnd));

    OffsetDateTime desiredStart = requireDateTime(request.startDateTime(), "startDateTime", businessZone);
    int durationMinutes = request.durationMinutes() == null || request.durationMinutes() <= 0 ? existingDuration : request.durationMinutes();
    OffsetDateTime desiredEnd = resolveEndDateTime(desiredStart, request.endDateTime(), durationMinutes, businessZone);

    List<CalendarEventResponse> events = calendarEventService.listEvents(
      tenantId,
      startOfDay(desiredStart).toInstant(),
      startOfDay(desiredStart).plusDays(MAX_LOOKAHEAD_DAYS).toInstant(),
      250
    );
    ensureSlotAvailable(connection, events, desiredStart, desiredEnd, existing.id());

    CalendarEventResponse updated = calendarEventService.updateEvent(
      tenantId,
      existing.id(),
      new CalendarEventMutationRequest(
        null,
        request.notes(),
        desiredStart.toString(),
        desiredEnd.toString(),
        connection.getTenant() == null ? null : connection.getTenant().getTimezone(),
        customerIdentity.customerName(),
        customerIdentity.phoneNumber(),
        customerIdentity.remoteJid(),
        request.appointmentType(),
        "whatsapp_assistant",
        buildMetadata(customerIdentity, tenantId, request.appointmentType())
      )
    );

    return toCustomerBooking(updated);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void cancelCustomerBooking(UUID tenantId, CustomerIdentity customerIdentity, String eventId) {
    CalendarEventResponse existing = requireOwnedEvent(tenantId, customerIdentity, eventId);
    calendarEventService.deleteEvent(tenantId, existing.id());
  }

  private CalendarConnection requireConnectedConnection(UUID tenantId) {
    return calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .orElseThrow(() -> new EntityNotFoundException("No connected calendar found for tenant: " + tenantId));
  }

  private CalendarEventResponse requireOwnedEvent(UUID tenantId, CustomerIdentity customerIdentity, String eventId) {
    if (eventId == null || eventId.isBlank()) {
      throw new IllegalArgumentException("eventId is required");
    }

    return calendarEventService.listEvents(
      tenantId,
      Instant.now().minus(60, ChronoUnit.DAYS),
      Instant.now().plus(365, ChronoUnit.DAYS),
      250
    )
      .stream()
      .filter(event -> eventId.trim().equals(event.id()))
      .filter(event -> isOwnedByCustomer(event, customerIdentity))
      .findFirst()
      .orElseThrow(() -> new EntityNotFoundException("Customer booking not found: " + eventId));
  }

  private int resolveDurationMinutes(CalendarConnection connection, String appointmentType, Integer requestedDurationMinutes) {
    if (requestedDurationMinutes != null && requestedDurationMinutes > 0) {
      return requestedDurationMinutes;
    }

    if (appointmentType != null && !appointmentType.isBlank()) {
      String normalized = normalize(appointmentType);
      Optional<AppointmentType> matched = appointmentTypeRepository.findByCalendarConnectionIdOrderByCreatedAtAsc(connection.getId())
        .stream()
        .filter(AppointmentType::isActive)
        .filter(item -> normalize(item.getSlug()).equals(normalized) || normalize(item.getName()).equals(normalized))
        .findFirst();
      if (matched.isPresent()) {
        return matched.get().getDurationMinutes();
      }
    }

    return DEFAULT_DURATION_MINUTES;
  }

  private void ensureSlotAvailable(
    CalendarConnection connection,
    List<CalendarEventResponse> events,
    OffsetDateTime desiredStart,
    OffsetDateTime desiredEnd,
    String ignoredEventId
  ) {
    boolean withinWorkingHours = isWithinWorkingHours(connection, desiredStart, desiredEnd);
    boolean conflicts = hasConflict(events, desiredStart, desiredEnd, ignoredEventId);
    if (!withinWorkingHours || conflicts) {
      String reason = !withinWorkingHours ? "outside_working_hours" : "time_slot_unavailable";
      List<SuggestedSlot> suggestedSlots = suggestSlots(connection, events, desiredStart, (int) ChronoUnit.MINUTES.between(desiredStart, desiredEnd), 3, ignoredEventId);
      AvailabilityResult availability = new AvailabilityResult(
        false,
        reason,
        availabilityMessage(reason, suggestedSlots),
        desiredStart.toString(),
        desiredEnd.toString(),
        suggestedSlots
      );
      throw new SlotUnavailableException(availability);
    }
  }

  private String availabilityMessage(String reason, List<SuggestedSlot> suggestedSlots) {
    if ("available".equals(reason)) {
      return "Esse horario esta disponivel.";
    }
    if ("outside_working_hours".equals(reason)) {
      return suggestedSlots == null || suggestedSlots.isEmpty()
        ? "Esse horario esta fora do horario de atendimento."
        : "Esse horario esta fora do horario de atendimento. Temos estas opcoes disponiveis:";
    }
    return suggestedSlots == null || suggestedSlots.isEmpty()
      ? "Esse horario nao esta disponivel."
      : "Esse horario nao esta disponivel. Temos estas opcoes disponiveis:";
  }

  private boolean isWithinWorkingHours(CalendarConnection connection, OffsetDateTime desiredStart, OffsetDateTime desiredEnd) {
    List<WorkingHour> workingHours = workingHourRepository.findByCalendarConnectionIdOrderByWeekdayAsc(connection.getId());
    if (workingHours.isEmpty()) {
      return true;
    }

    int weekday = toWeekday(desiredStart.getDayOfWeek());
    WorkingHour dayHours = workingHours.stream()
      .filter(item -> item.isEnabled() && item.getWeekday() != null && item.getWeekday() == weekday)
      .findFirst()
      .orElse(null);
    if (dayHours == null) {
      return false;
    }

    LocalTime start = desiredStart.toLocalTime();
    LocalTime end = desiredEnd.toLocalTime();
    LocalTime open = parseLocalTime(dayHours.getStartTime());
    LocalTime close = parseLocalTime(dayHours.getEndTime());

    if (open != null && start.isBefore(open)) {
      return false;
    }
    if (close != null && end.isAfter(close)) {
      return false;
    }

    LocalTime breakStart = parseLocalTime(dayHours.getBreakStartTime());
    LocalTime breakEnd = parseLocalTime(dayHours.getBreakEndTime());
    if (breakStart != null && breakEnd != null && start.isBefore(breakEnd) && end.isAfter(breakStart)) {
      return false;
    }

    return true;
  }

  private boolean hasConflict(
    List<CalendarEventResponse> events,
    OffsetDateTime desiredStart,
    OffsetDateTime desiredEnd,
    String ignoredEventId
  ) {
    return events.stream().anyMatch(event -> {
      if (ignoredEventId != null && ignoredEventId.equals(event.id())) {
        return false;
      }

      OffsetDateTime eventStart = parseOffsetDateTime(event.startDateTime(), desiredStart.getOffset()).orElse(null);
      OffsetDateTime eventEnd = parseOffsetDateTime(event.endDateTime(), desiredStart.getOffset()).orElse(null);
      if (eventStart == null || eventEnd == null) {
        return false;
      }

      return desiredStart.isBefore(eventEnd) && desiredEnd.isAfter(eventStart);
    });
  }

  private List<SuggestedSlot> suggestSlots(
    CalendarConnection connection,
    List<CalendarEventResponse> events,
    OffsetDateTime anchor,
    int durationMinutes,
    int limit,
    String ignoredEventId
  ) {
    List<WorkingHour> workingHours = workingHourRepository.findByCalendarConnectionIdOrderByWeekdayAsc(connection.getId());
    if (workingHours.isEmpty()) {
      return List.of();
    }

    List<SuggestedSlot> suggestions = new ArrayList<>();
    for (int dayOffset = 0; dayOffset < MAX_LOOKAHEAD_DAYS && suggestions.size() < limit; dayOffset++) {
      LocalDate date = anchor.toLocalDate().plusDays(dayOffset);
      int weekday = toWeekday(date.getDayOfWeek());
      WorkingHour dayHours = workingHours.stream()
        .filter(item -> item.isEnabled() && item.getWeekday() != null && item.getWeekday() == weekday)
        .findFirst()
        .orElse(null);
      if (dayHours == null) {
        continue;
      }

      LocalTime open = parseLocalTime(dayHours.getStartTime());
      LocalTime close = parseLocalTime(dayHours.getEndTime());
      if (open == null || close == null || !close.isAfter(open)) {
        continue;
      }

      int slotIntervalMinutes = dayHours.getSlotIntervalMinutes() == null || dayHours.getSlotIntervalMinutes() <= 0
        ? DEFAULT_SLOT_INTERVAL_MINUTES
        : dayHours.getSlotIntervalMinutes();

      OffsetDateTime slotCursor = OffsetDateTime.of(date, open, anchor.getOffset());
      if (dayOffset == 0 && slotCursor.isBefore(anchor)) {
        slotCursor = roundUp(anchor, slotIntervalMinutes);
      }

      OffsetDateTime closeDateTime = OffsetDateTime.of(date, close, anchor.getOffset());
      while (!slotCursor.plusMinutes(durationMinutes).isAfter(closeDateTime) && suggestions.size() < limit) {
        OffsetDateTime slotEnd = slotCursor.plusMinutes(durationMinutes);
        if (isWithinWorkingHours(connection, slotCursor, slotEnd) && !hasConflict(events, slotCursor, slotEnd, ignoredEventId)) {
          suggestions.add(new SuggestedSlot(slotCursor.toString(), slotEnd.toString()));
        }
        slotCursor = slotCursor.plusMinutes(slotIntervalMinutes);
      }
    }

    return suggestions;
  }

  private OffsetDateTime roundUp(OffsetDateTime value, int intervalMinutes) {
    int minute = value.getMinute();
    int remainder = minute % intervalMinutes;
    if (remainder == 0 && value.getSecond() == 0 && value.getNano() == 0) {
      return value;
    }

    int minutesToAdd = remainder == 0 ? intervalMinutes : intervalMinutes - remainder;
    return value
      .plusMinutes(minutesToAdd)
      .withSecond(0)
      .withNano(0);
  }

  private String eventSummary(CustomerIdentity customerIdentity, String appointmentType) {
    String base = appointmentType != null && !appointmentType.isBlank() ? appointmentType.trim() : "Atendimento";
    if (customerIdentity.customerName() != null && !customerIdentity.customerName().isBlank()) {
      return base + " - " + customerIdentity.customerName().trim();
    }
    return base + " - " + customerIdentity.phoneNumber();
  }

  private String buildDescription(CustomerIdentity customerIdentity, String notes, String appointmentType) {
    List<String> lines = new ArrayList<>();
    if (appointmentType != null && !appointmentType.isBlank()) {
      lines.add("Tipo: " + appointmentType.trim());
    }
    lines.add("Cliente: " + displayName(customerIdentity));
    lines.add("Telefone: " + customerIdentity.phoneNumber());
    if (customerIdentity.remoteJid() != null && !customerIdentity.remoteJid().isBlank()) {
      lines.add("WhatsApp JID: " + customerIdentity.remoteJid().trim());
    }
    if (notes != null && !notes.isBlank()) {
      lines.add("Observacoes: " + notes.trim());
    }
    return String.join("\n", lines);
  }

  private Map<String, String> buildMetadata(CustomerIdentity customerIdentity, UUID tenantId, String appointmentType) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "whatsapp_assistant");
    metadata.put("tenantId", tenantId.toString());
    metadata.put("customerPhone", customerIdentity.phoneNumber());
    metadata.put("customerName", displayName(customerIdentity));
    if (customerIdentity.remoteJid() != null && !customerIdentity.remoteJid().isBlank()) {
      metadata.put("customerRemoteJid", customerIdentity.remoteJid().trim());
    }
    if (appointmentType != null && !appointmentType.isBlank()) {
      metadata.put("appointmentType", appointmentType.trim());
    }
    return metadata;
  }

  private boolean isOwnedByCustomer(CalendarEventResponse event, CustomerIdentity customerIdentity) {
    Map<String, String> metadata = event.privateMetadata();
    if (metadata != null && !metadata.isEmpty()) {
      String metadataPhone = metadata.get("customerPhone");
      String metadataRemoteJid = metadata.get("customerRemoteJid");
      if (
        customerIdentity.phoneNumber().equals(metadataPhone) ||
        (customerIdentity.remoteJid() != null && customerIdentity.remoteJid().equals(metadataRemoteJid))
      ) {
        return true;
      }
    }

    String description = event.description() == null ? "" : event.description();
    if (description.contains("Telefone: " + customerIdentity.phoneNumber())) {
      return true;
    }

    return customerIdentity.remoteJid() != null &&
      !customerIdentity.remoteJid().isBlank() &&
      description.contains("WhatsApp JID: " + customerIdentity.remoteJid().trim());
  }

  private CustomerBooking toCustomerBooking(CalendarEventResponse event) {
    return new CustomerBooking(event.id(), event.summary(), event.startDateTime(), event.endDateTime(), event.status());
  }

  private Optional<Instant> parseEventStart(CalendarEventResponse event) {
    return parseOffsetDateTime(event.startDateTime(), ZoneOffset.UTC).map(OffsetDateTime::toInstant);
  }

  private OffsetDateTime requireDateTime(String value, String fieldName, ZoneId businessZone) {
    return parseOffsetDateTime(value, businessZone)
      .orElseThrow(() -> new IllegalArgumentException(fieldName + " must be a valid ISO 8601 date-time with offset"));
  }

  private OffsetDateTime resolveEndDateTime(OffsetDateTime start, String endDateTime, int durationMinutes, ZoneId businessZone) {
    return parseOffsetDateTime(endDateTime, businessZone).orElse(start.plusMinutes(durationMinutes));
  }

  private Optional<OffsetDateTime> parseOffsetDateTime(String value, ZoneId businessZone) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    try {
      return Optional.of(OffsetDateTime.parse(value));
    } catch (DateTimeParseException ignored) {
      try {
        return Optional.of(Instant.parse(value).atOffset(ZoneOffset.UTC));
      } catch (DateTimeParseException exception) {
        try {
          ZoneId effectiveZone = businessZone == null ? ZoneOffset.UTC : businessZone;
          return Optional.of(LocalDateTime.parse(value).atZone(effectiveZone).toOffsetDateTime());
        } catch (DateTimeParseException localDateTimeException) {
          return Optional.empty();
        }
      }
    }
  }

  private ZoneId resolveBusinessZone(CalendarConnection connection) {
    try {
      if (
        connection.getTenant() != null &&
        connection.getTenant().getTimezone() != null &&
        !connection.getTenant().getTimezone().isBlank()
      ) {
        return ZoneId.of(connection.getTenant().getTimezone().trim());
      }
    } catch (Exception ignored) {}
    return ZoneOffset.UTC;
  }

  private OffsetDateTime startOfDay(OffsetDateTime value) {
    return value.toLocalDate().atStartOfDay().atOffset(value.getOffset());
  }

  private LocalTime parseLocalTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalTime.parse(value.trim());
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private int toWeekday(DayOfWeek dayOfWeek) {
    return switch (dayOfWeek) {
      case MONDAY -> 1;
      case TUESDAY -> 2;
      case WEDNESDAY -> 3;
      case THURSDAY -> 4;
      case FRIDAY -> 5;
      case SATURDAY -> 6;
      case SUNDAY -> 7;
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
  }

  private String displayName(CustomerIdentity customerIdentity) {
    return customerIdentity.customerName() != null && !customerIdentity.customerName().isBlank()
      ? customerIdentity.customerName().trim()
      : customerIdentity.phoneNumber();
  }

  public record CustomerIdentity(String phoneNumber, String remoteJid, String customerName) {}

  public record AvailabilityRequest(
    String appointmentType,
    String startDateTime,
    String endDateTime,
    Integer durationMinutes
  ) {}

  public record AvailabilityResult(
    boolean available,
    String reason,
    String message,
    String requestedStartDateTime,
    String requestedEndDateTime,
    List<SuggestedSlot> suggestedSlots
  ) {}

  public record SuggestedSlot(String startDateTime, String endDateTime) {}

  public record BookingRequest(
    String appointmentType,
    String startDateTime,
    String endDateTime,
    Integer durationMinutes,
    String notes
  ) {}

  public record RescheduleRequest(
    String eventId,
    String appointmentType,
    String startDateTime,
    String endDateTime,
    Integer durationMinutes,
    String notes
  ) {}

  public record CustomerBooking(
    String eventId,
    String summary,
    String startDateTime,
    String endDateTime,
    String status
  ) {}

  public static class SlotUnavailableException extends IllegalArgumentException {

    private final AvailabilityResult availability;

    public SlotUnavailableException(AvailabilityResult availability) {
      super(availability == null ? "Requested slot is unavailable" : availability.message());
      this.availability = availability;
    }

    public AvailabilityResult availability() {
      return availability;
    }
  }
}
