package com.assistantcore.controller;

import com.assistantcore.dto.AppUserResponse;
import com.assistantcore.dto.AppUserSyncRequest;
import com.assistantcore.dto.AppUserWorkspaceResponse;
import com.assistantcore.dto.TenantMembershipResponse;
import com.assistantcore.service.AppUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app-users")
public class AppUserController {

  private final AppUserService appUserService;

  public AppUserController(AppUserService appUserService) {
    this.appUserService = appUserService;
  }

  @PostMapping("/sync")
  public AppUserResponse sync(@Valid @RequestBody AppUserSyncRequest request) {
    return appUserService.sync(request);
  }

  @GetMapping("/{supabaseUserId}/memberships")
  public List<TenantMembershipResponse> listMemberships(@PathVariable String supabaseUserId) {
    return appUserService.listMemberships(supabaseUserId);
  }

  @GetMapping("/{supabaseUserId}/workspace")
  public AppUserWorkspaceResponse workspace(@PathVariable String supabaseUserId) {
    return appUserService.loadWorkspace(supabaseUserId);
  }
}
