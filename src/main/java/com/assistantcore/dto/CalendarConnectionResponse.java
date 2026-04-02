package com.assistantcore.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CalendarConnectionResponse(
  UUID id,
  UUID tenantId,
  String provider,
  String googleAccountEmail,
  String googleCalendarId,
  String googleCalendarName,
  String status,
  String syncMode,
  Instant createdAt,
  Instant updatedAt,
  List<WorkingHourResponse> workingHours,
  List<AppointmentTypeResponse> appointmentTypes
) {}
