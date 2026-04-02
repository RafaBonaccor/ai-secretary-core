package com.assistantcore.dto;

import jakarta.validation.constraints.NotBlank;

public record CalendarConnectionCreateRequest(
  @NotBlank String googleAccountEmail,
  @NotBlank String googleCalendarId,
  @NotBlank String googleCalendarName,
  String syncMode
) {}
