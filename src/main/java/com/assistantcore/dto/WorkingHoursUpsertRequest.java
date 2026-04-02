package com.assistantcore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WorkingHoursUpsertRequest(@Valid @NotEmpty List<WorkingHourRequest> workingHours) {}
