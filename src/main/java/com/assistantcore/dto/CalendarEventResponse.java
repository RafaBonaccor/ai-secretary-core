package com.assistantcore.dto;

import java.util.Map;

public record CalendarEventResponse(
  String id,
  String summary,
  String description,
  String startDateTime,
  String endDateTime,
  String status,
  String source,
  String customerName,
  String customerPhone,
  String customerRemoteJid,
  String appointmentType,
  Map<String, String> privateMetadata
) {}
