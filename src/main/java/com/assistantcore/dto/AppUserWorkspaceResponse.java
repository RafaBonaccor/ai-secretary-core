package com.assistantcore.dto;

import java.util.UUID;

public record AppUserWorkspaceResponse(
  boolean hasWorkspace,
  UUID tenantId,
  String tenantName,
  String tenantSlug,
  String role,
  UUID channelInstanceId,
  String channelInstanceStatus,
  UUID aiProfileId,
  Boolean aiProfileActive,
  String phoneNumber,
  String instanceName,
  UUID calendarConnectionId,
  String calendarConnectionStatus,
  String googleAccountEmail,
  String googleCalendarName
) {}
