package com.assistantcore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AppointmentTypeRequest(
  @NotBlank String name,
  @NotBlank String slug,
  @NotNull @Min(5) Integer durationMinutes,
  Integer bufferBeforeMinutes,
  Integer bufferAfterMinutes,
  BigDecimal priceAmount,
  String currency,
  Boolean active,
  String description
) {}
