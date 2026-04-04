package com.assistantcore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AppUserSyncRequest(
  @NotBlank String supabaseUserId,
  @NotBlank @Email String email,
  String fullName
) {}
