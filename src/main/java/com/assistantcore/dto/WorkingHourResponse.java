package com.assistantcore.dto;

import java.util.UUID;

public record WorkingHourResponse(
  UUID id,
  Integer weekday,
  boolean enabled,
  String startTime,
  String endTime,
  String breakStartTime,
  String breakEndTime,
  Integer slotIntervalMinutes,
  Integer bufferBeforeMinutes,
  Integer bufferAfterMinutes
) {}
