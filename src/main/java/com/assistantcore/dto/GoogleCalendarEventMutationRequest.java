package com.assistantcore.dto;

import java.util.Map;

public record GoogleCalendarEventMutationRequest(
  String summary,
  String description,
  String startDateTime,
  String endDateTime,
  String timeZone,
  Map<String, String> privateMetadata
) {}
