package com.assistantcore.dto;

import jakarta.validation.constraints.NotBlank;

public record CalendarConnectionCreateRequest(@NotBlank String calendarName, String syncMode) {}
