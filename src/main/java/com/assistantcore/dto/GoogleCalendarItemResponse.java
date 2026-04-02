package com.assistantcore.dto;

public record GoogleCalendarItemResponse(
  String id,
  String summary,
  String timeZone,
  boolean primary,
  boolean selected
) {}
