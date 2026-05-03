package com.assistantcore.controller;

import com.assistantcore.dto.AIProfileCreateRequest;
import com.assistantcore.dto.AIProfileActiveUpdateRequest;
import com.assistantcore.dto.AIProfilePresetCreateRequest;
import com.assistantcore.dto.AIProfilePresetResponse;
import com.assistantcore.dto.AIProfileResponse;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.AIProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-profiles")
public class AIProfileController {

  private final AIProfileService aiProfileService;
  private final AppAuthorizationService appAuthorizationService;

  public AIProfileController(AIProfileService aiProfileService, AppAuthorizationService appAuthorizationService) {
    this.aiProfileService = aiProfileService;
    this.appAuthorizationService = appAuthorizationService;
  }

  @GetMapping("/presets")
  public List<AIProfilePresetResponse> listPresets() {
    return aiProfileService.listPresets();
  }

  @GetMapping("/tenant/{tenantId}")
  public List<AIProfileResponse> listByTenant(@PathVariable UUID tenantId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    return aiProfileService.listByTenant(tenantId);
  }

  @PostMapping("/tenant/{tenantId}")
  @ResponseStatus(HttpStatus.CREATED)
  public AIProfileResponse createCustom(@PathVariable UUID tenantId, @Valid @RequestBody AIProfileCreateRequest request) {
    appAuthorizationService.requireTenantMembership(tenantId);
    return aiProfileService.createCustom(tenantId, request);
  }

  @PostMapping("/tenant/{tenantId}/from-preset")
  @ResponseStatus(HttpStatus.CREATED)
  public AIProfileResponse createFromPreset(@PathVariable UUID tenantId, @Valid @RequestBody AIProfilePresetCreateRequest request) {
    appAuthorizationService.requireTenantMembership(tenantId);
    return aiProfileService.createFromPreset(tenantId, request);
  }

  @PostMapping("/tenant/{tenantId}/appointment-secretary/default")
  @ResponseStatus(HttpStatus.CREATED)
  public AIProfileResponse createDefaultAppointmentSecretary(@PathVariable UUID tenantId) {
    appAuthorizationService.requireTenantMembership(tenantId);
    return aiProfileService.createDefaultAppointmentSecretary(tenantId);
  }

  @PostMapping("/channel-instances/{channelInstanceId}/assign/{profileId}")
  public AIProfileResponse assignToChannel(@PathVariable UUID channelInstanceId, @PathVariable UUID profileId) {
    appAuthorizationService.requireChannelInstanceAccess(channelInstanceId);
    return aiProfileService.assignProfileToChannel(channelInstanceId, profileId);
  }

  @PutMapping("/channel-instances/{channelInstanceId}/active")
  public AIProfileResponse updateAssignedProfileActiveState(
    @PathVariable UUID channelInstanceId,
    @Valid @RequestBody AIProfileActiveUpdateRequest request
  ) {
    appAuthorizationService.requireChannelInstanceAccess(channelInstanceId);
    return aiProfileService.updateAssignedProfileActiveState(channelInstanceId, request.active());
  }
}
