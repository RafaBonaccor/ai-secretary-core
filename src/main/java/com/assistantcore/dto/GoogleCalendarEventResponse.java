package com.assistantcore.dto;

import java.util.Map;

public record GoogleCalendarEventResponse(
  String id,
  String summary,
  String description,
  String startDateTime,
  String endDateTime,
  String status,
  String htmlLink,
  Map<String, String> privateMetadata
) {}
