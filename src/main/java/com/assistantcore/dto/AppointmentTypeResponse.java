package com.assistantcore.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AppointmentTypeResponse(
  UUID id,
  String name,
  String slug,
  Integer durationMinutes,
  Integer bufferBeforeMinutes,
  Integer bufferAfterMinutes,
  BigDecimal priceAmount,
  String currency,
  boolean active,
  String description
) {}
