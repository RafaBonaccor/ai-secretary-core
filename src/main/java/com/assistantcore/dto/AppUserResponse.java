package com.assistantcore.dto;

import java.util.UUID;

public record AppUserResponse(
  UUID id,
  String supabaseUserId,
  String email,
  String fullName,
  String status
) {}
