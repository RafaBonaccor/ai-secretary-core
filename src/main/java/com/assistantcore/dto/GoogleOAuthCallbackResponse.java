package com.assistantcore.dto;

import java.util.List;
import java.util.UUID;

public record GoogleOAuthCallbackResponse(
  UUID calendarConnectionId,
  String provider,
  String status,
  String googleAccountEmail,
  String selectedCalendarId,
  String selectedCalendarName,
  List<GoogleCalendarItemResponse> availableCalendars
) {}
