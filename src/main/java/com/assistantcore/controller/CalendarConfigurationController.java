package com.assistantcore.controller;

import com.assistantcore.dto.AppointmentTypesUpsertRequest;
import com.assistantcore.dto.CalendarConnectionCreateRequest;
import com.assistantcore.dto.CalendarConnectionResponse;
import com.assistantcore.dto.WorkingHoursUpsertRequest;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.CalendarConfigurationService;
import com.assistantcore.service.SubscriptionEntitlementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar-connections")
public class CalendarConfigurationController {

  private final CalendarConfigurationService calendarConfigurationService;
  private final AppAuthorizationService appAuthorizationService;
  private final SubscriptionEntitlementService subscriptionEntitlementService;

  public CalendarConfigurationController(
    CalendarConfigurationService calendarConfigurationService,
    AppAuthorizationService appAuthorizationService,
    SubscriptionEntitlementService subscriptionEntitlementService
  ) {
    this.calendarConfigurationService = calendarConfigurationService;
    this.appAuthorizationService = appAuthorizationService;
    this.subscriptionEntitlementService = subscriptionEntitlementService;
  }

  @GetMapping("/tenant/{tenantId}")
  public List<CalendarConnectionResponse> listByTenant(@PathVariable UUID tenantId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireCalendarFeatureForTenant(tenantId);
    return calendarConfigurationService.listByTenant(tenantId);
  }

  @PostMapping("/tenant/{tenantId}/google/manual")
  @ResponseStatus(HttpStatus.CREATED)
  public CalendarConnectionResponse createGoogleConnection(@PathVariable UUID tenantId, @Valid @RequestBody CalendarConnectionCreateRequest request) {
    appAuthorizationService.requireTenantMembership(tenantId);
    subscriptionEntitlementService.requireCalendarFeatureForTenant(tenantId);
    return calendarConfigurationService.createGoogleConnection(tenantId, request);
  }

  @PutMapping("/{connectionId}/working-hours")
  public CalendarConnectionResponse replaceWorkingHours(@PathVariable UUID connectionId, @Valid @RequestBody WorkingHoursUpsertRequest request) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return calendarConfigurationService.replaceWorkingHours(connectionId, request);
  }

  @PutMapping("/{connectionId}/appointment-types")
  public CalendarConnectionResponse replaceAppointmentTypes(@PathVariable UUID connectionId, @Valid @RequestBody AppointmentTypesUpsertRequest request) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return calendarConfigurationService.replaceAppointmentTypes(connectionId, request);
  }

  @DeleteMapping("/{connectionId}/google")
  public CalendarConnectionResponse disconnectGoogle(@PathVariable UUID connectionId) {
    appAuthorizationService.requireCalendarConnectionAccess(connectionId);
    subscriptionEntitlementService.requireCalendarFeatureForConnection(connectionId);
    return calendarConfigurationService.disconnectGoogle(connectionId);
  }
}
