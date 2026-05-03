package com.assistantcore.dto;

import java.util.Map;

public record CalendarEventMutationRequest(
  String summary,
  String description,
  String startDateTime,
  String endDateTime,
  String timeZone,
  String customerName,
  String customerPhone,
  String customerRemoteJid,
  String appointmentType,
  String source,
  Map<String, String> privateMetadata
) {}
