package com.assistantcore.dto;

public record GoogleOAuthStartResponse(
  String provider,
  String authorizationUrl,
  String state,
  String redirectUri
) {}
