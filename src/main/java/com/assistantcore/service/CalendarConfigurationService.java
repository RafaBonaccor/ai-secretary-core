package com.assistantcore.service;

import com.assistantcore.dto.AppointmentTypeRequest;
import com.assistantcore.dto.AppointmentTypeResponse;
import com.assistantcore.dto.AppointmentTypesUpsertRequest;
import com.assistantcore.dto.CalendarConnectionCreateRequest;
import com.assistantcore.dto.CalendarConnectionResponse;
import com.assistantcore.dto.WorkingHourRequest;
import com.assistantcore.dto.WorkingHourResponse;
import com.assistantcore.dto.WorkingHoursUpsertRequest;
import com.assistantcore.model.AppointmentType;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.Tenant;
import com.assistantcore.model.WorkingHour;
import com.assistantcore.repository.AppointmentTypeRepository;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.TenantRepository;
import com.assistantcore.repository.WorkingHourRepository;
import jakarta.persistence.EntityNotFoundException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarConfigurationService {

  private static final Map<Integer, String> WEEKDAY_LABELS = Map.of(
    0, "domingo",
    1, "segunda",
    2, "terca",
    3, "quarta",
    4, "quinta",
    5, "sexta",
    6, "sabado"
  );

  private final CalendarConnectionRepository calendarConnectionRepository;
  private final WorkingHourRepository workingHourRepository;
  private final AppointmentTypeRepository appointmentTypeRepository;
  private final TenantRepository tenantRepository;
  private final OfficialEmailPolicyService officialEmailPolicyService;
  private final GoogleCalendarCredentialService googleCalendarCredentialService;
  private final GoogleCalendarClient googleCalendarClient;

  public CalendarConfigurationService(
    CalendarConnectionRepository calendarConnectionRepository,
    WorkingHourRepository workingHourRepository,
    AppointmentTypeRepository appointmentTypeRepository,
    TenantRepository tenantRepository,
    OfficialEmailPolicyService officialEmailPolicyService,
    GoogleCalendarCredentialService googleCalendarCredentialService,
    GoogleCalendarClient googleCalendarClient
  ) {
    this.calendarConnectionRepository = calendarConnectionRepository;
    this.workingHourRepository = workingHourRepository;
    this.appointmentTypeRepository = appointmentTypeRepository;
    this.tenantRepository = tenantRepository;
    this.officialEmailPolicyService = officialEmailPolicyService;
    this.googleCalendarCredentialService = googleCalendarCredentialService;
    this.googleCalendarClient = googleCalendarClient;
  }

  @Transactional(readOnly = true)
  public List<CalendarConnectionResponse> listByTenant(UUID tenantId) {
    return calendarConnectionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public CalendarConnectionResponse createGoogleConnection(UUID tenantId, CalendarConnectionCreateRequest request) {
    Tenant tenant = tenantRepository.findById(tenantId)
      .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

    CalendarConnection connection = new CalendarConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenant(tenant);
    connection.setProvider("google_calendar");
    connection.setGoogleAccountEmail(officialEmailPolicyService.requireOfficialEmail(request.googleAccountEmail()));
    connection.setGoogleCalendarId(request.googleCalendarId().trim());
    connection.setGoogleCalendarName(request.googleCalendarName().trim());
    connection.setStatus("pending_oauth");
    connection.setSyncMode(hasText(request.syncMode()) ? normalizeKey(request.syncMode()) : "manual");
    connection.setCreatedAt(Instant.now());
    connection.setUpdatedAt(Instant.now());

    return toResponse(calendarConnectionRepository.save(connection));
  }

  @Transactional
  public CalendarConnectionResponse replaceWorkingHours(UUID connectionId, WorkingHoursUpsertRequest request) {
    CalendarConnection connection = getConnection(connectionId);
    List<WorkingHour> existing = workingHourRepository.findByCalendarConnectionIdOrderByWeekdayAsc(connectionId);
    if (!existing.isEmpty()) {
      workingHourRepository.deleteAll(existing);
    }

    Instant now = Instant.now();
    List<WorkingHour> items = new ArrayList<>();
    for (WorkingHourRequest item : request.workingHours()) {
      WorkingHour workingHour = new WorkingHour();
      workingHour.setId(UUID.randomUUID());
      workingHour.setCalendarConnection(connection);
      workingHour.setWeekday(item.weekday());
      workingHour.setEnabled(item.enabled() == null || item.enabled());
      workingHour.setStartTime(blankToNull(item.startTime()));
      workingHour.setEndTime(blankToNull(item.endTime()));
      workingHour.setBreakStartTime(blankToNull(item.breakStartTime()));
      workingHour.setBreakEndTime(blankToNull(item.breakEndTime()));
      workingHour.setSlotIntervalMinutes(item.slotIntervalMinutes() == null ? 30 : item.slotIntervalMinutes());
      workingHour.setBufferBeforeMinutes(item.bufferBeforeMinutes() == null ? 0 : item.bufferBeforeMinutes());
      workingHour.setBufferAfterMinutes(item.bufferAfterMinutes() == null ? 0 : item.bufferAfterMinutes());
      workingHour.setCreatedAt(now);
      workingHour.setUpdatedAt(now);
      items.add(workingHour);
    }

    workingHourRepository.saveAll(items);
    touch(connection);
    return toResponse(connection);
  }

  @Transactional
  public CalendarConnectionResponse replaceAppointmentTypes(UUID connectionId, AppointmentTypesUpsertRequest request) {
    CalendarConnection connection = getConnection(connectionId);
    List<AppointmentType> existing = appointmentTypeRepository.findByCalendarConnectionIdOrderByCreatedAtAsc(connectionId);
    if (!existing.isEmpty()) {
      appointmentTypeRepository.deleteAll(existing);
    }

    Instant now = Instant.now();
    List<AppointmentType> items = new ArrayList<>();
    for (AppointmentTypeRequest item : request.appointmentTypes()) {
      AppointmentType appointmentType = new AppointmentType();
      appointmentType.setId(UUID.randomUUID());
      appointmentType.setCalendarConnection(connection);
      appointmentType.setName(item.name().trim());
      appointmentType.setSlug(normalizeSlug(item.slug()));
      appointmentType.setDurationMinutes(item.durationMinutes());
      appointmentType.setBufferBeforeMinutes(item.bufferBeforeMinutes() == null ? 0 : item.bufferBeforeMinutes());
      appointmentType.setBufferAfterMinutes(item.bufferAfterMinutes() == null ? 0 : item.bufferAfterMinutes());
      appointmentType.setPriceAmount(item.priceAmount());
      appointmentType.setCurrency(hasText(item.currency()) ? item.currency().trim().toUpperCase(Locale.ROOT) : "BRL");
      appointmentType.setActive(item.active() == null || item.active());
      appointmentType.setDescription(blankToNull(item.description()));
      appointmentType.setCreatedAt(now);
      appointmentType.setUpdatedAt(now);
      items.add(appointmentType);
    }

    appointmentTypeRepository.saveAll(items);
    touch(connection);
    return toResponse(connection);
  }

  @Transactional
  public CalendarConnectionResponse disconnectGoogle(UUID connectionId) {
    CalendarConnection connection = getConnection(connectionId);

    googleCalendarCredentialService.findMaterial(connectionId).ifPresent(material -> {
      String revokeCandidate = hasText(material.refreshToken()) ? material.refreshToken() : material.accessToken();
      googleCalendarClient.revokeToken(revokeCandidate);
    });
    googleCalendarCredentialService.deleteByConnectionId(connectionId);

    connection.setStatus("disconnected");
    connection.setUpdatedAt(Instant.now());
    return toResponse(calendarConnectionRepository.save(connection));
  }

  @Transactional(readOnly = true)
  public CalendarPromptContext buildPromptContext(UUID tenantId) {
    return calendarConnectionRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "connected")
      .map(connection -> {
        List<String> workingHours = workingHourRepository.findByCalendarConnectionIdOrderByWeekdayAsc(connection.getId())
          .stream()
          .map(this::formatWorkingHour)
          .toList();
        List<String> appointmentTypes = appointmentTypeRepository.findByCalendarConnectionIdOrderByCreatedAtAsc(connection.getId())
          .stream()
          .filter(AppointmentType::isActive)
          .map(this::formatAppointmentType)
          .toList();
        return new CalendarPromptContext(true, "connected", connection.getGoogleCalendarName(), workingHours, appointmentTypes);
      })
      .orElseGet(() -> {
        List<CalendarConnection> connections = calendarConnectionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (connections.isEmpty()) {
          return new CalendarPromptContext(false, "not_connected", null, List.of(), List.of());
        }
        CalendarConnection latest = connections.get(0);
        return new CalendarPromptContext(false, latest.getStatus(), latest.getGoogleCalendarName(), List.of(), List.of());
      });
  }

  private CalendarConnection getConnection(UUID connectionId) {
    return calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));
  }

  private void touch(CalendarConnection connection) {
    connection.setUpdatedAt(Instant.now());
    calendarConnectionRepository.save(connection);
  }

  private String formatWorkingHour(WorkingHour item) {
    if (!item.isEnabled()) {
      return WEEKDAY_LABELS.getOrDefault(item.getWeekday(), "dia_" + item.getWeekday()) + ": fechado";
    }

    StringBuilder builder = new StringBuilder();
    builder.append(WEEKDAY_LABELS.getOrDefault(item.getWeekday(), "dia_" + item.getWeekday()));
    builder.append(": ").append(nullSafe(item.getStartTime(), "--")).append(" - ").append(nullSafe(item.getEndTime(), "--"));
    if (hasText(item.getBreakStartTime()) && hasText(item.getBreakEndTime())) {
      builder.append(" (pausa ").append(item.getBreakStartTime()).append(" - ").append(item.getBreakEndTime()).append(")");
    }
    builder.append(", slot ").append(item.getSlotIntervalMinutes()).append(" min");
    if (item.getBufferBeforeMinutes() > 0 || item.getBufferAfterMinutes() > 0) {
      builder.append(", buffer ").append(item.getBufferBeforeMinutes()).append("/").append(item.getBufferAfterMinutes()).append(" min");
    }
    return builder.toString();
  }

  private String formatAppointmentType(AppointmentType item) {
    StringBuilder builder = new StringBuilder();
    builder.append(item.getName()).append(": ").append(item.getDurationMinutes()).append(" min");
    if (item.getPriceAmount() != null) {
      builder.append(", preco ").append(item.getCurrency() == null ? "BRL" : item.getCurrency()).append(" ").append(item.getPriceAmount());
    }
    if (hasText(item.getDescription())) {
      builder.append(", ").append(item.getDescription().trim());
    }
    return builder.toString();
  }

  private CalendarConnectionResponse toResponse(CalendarConnection connection) {
    List<WorkingHourResponse> workingHours = workingHourRepository.findByCalendarConnectionIdOrderByWeekdayAsc(connection.getId())
      .stream()
      .map(item -> new WorkingHourResponse(
        item.getId(),
        item.getWeekday(),
        item.isEnabled(),
        item.getStartTime(),
        item.getEndTime(),
        item.getBreakStartTime(),
        item.getBreakEndTime(),
        item.getSlotIntervalMinutes(),
        item.getBufferBeforeMinutes(),
        item.getBufferAfterMinutes()
      ))
      .toList();

    List<AppointmentTypeResponse> appointmentTypes = appointmentTypeRepository.findByCalendarConnectionIdOrderByCreatedAtAsc(connection.getId())
      .stream()
      .map(item -> new AppointmentTypeResponse(
        item.getId(),
        item.getName(),
        item.getSlug(),
        item.getDurationMinutes(),
        item.getBufferBeforeMinutes(),
        item.getBufferAfterMinutes(),
        item.getPriceAmount(),
        item.getCurrency(),
        item.isActive(),
        item.getDescription()
      ))
      .toList();

    return new CalendarConnectionResponse(
      connection.getId(),
      connection.getTenant().getId(),
      connection.getProvider(),
      connection.getGoogleAccountEmail(),
      connection.getGoogleCalendarId(),
      connection.getGoogleCalendarName(),
      connection.getStatus(),
      connection.getSyncMode(),
      connection.getCreatedAt(),
      connection.getUpdatedAt(),
      workingHours,
      appointmentTypes
    );
  }

  private String normalizeSlug(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String normalizeKey(String value) {
    return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }

  private String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String nullSafe(String value, String fallback) {
    return hasText(value) ? value.trim() : fallback;
  }

  public record CalendarPromptContext(
    boolean connected,
    String status,
    String calendarName,
    List<String> workingHours,
    List<String> appointmentTypes
  ) {}
}
