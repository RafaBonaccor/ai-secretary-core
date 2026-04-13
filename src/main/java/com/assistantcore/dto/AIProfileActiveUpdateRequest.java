package com.assistantcore.dto;

import jakarta.validation.constraints.NotNull;

public record AIProfileActiveUpdateRequest(@NotNull Boolean active) {}
