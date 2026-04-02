package com.assistantcore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WorkingHourRequest(
  @NotNull @Min(0) @Max(6) Integer weekday,
  Boolean enabled,
  String startTime,
  String endTime,
  String breakStartTime,
  String breakEndTime,
  Integer slotIntervalMinutes,
  Integer bufferBeforeMinutes,
  Integer bufferAfterMinutes
) {}
