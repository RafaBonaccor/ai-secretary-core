package com.assistantcore.service;

import com.assistantcore.dto.AppUserResponse;
import com.assistantcore.dto.AppUserSyncRequest;
import com.assistantcore.dto.TenantMembershipResponse;
import com.assistantcore.model.AppUser;
import com.assistantcore.model.Tenant;
import com.assistantcore.model.TenantUserMembership;
import com.assistantcore.repository.AppUserRepository;
import com.assistantcore.repository.TenantRepository;
import com.assistantcore.repository.TenantUserMembershipRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

  private final AppUserRepository appUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantUserMembershipRepository tenantUserMembershipRepository;

  public AppUserService(
    AppUserRepository appUserRepository,
    TenantRepository tenantRepository,
    TenantUserMembershipRepository tenantUserMembershipRepository
  ) {
    this.appUserRepository = appUserRepository;
    this.tenantRepository = tenantRepository;
    this.tenantUserMembershipRepository = tenantUserMembershipRepository;
  }

  @Transactional
  public AppUserResponse sync(AppUserSyncRequest request) {
    Instant now = Instant.now();

    AppUser appUser = appUserRepository.findBySupabaseUserId(request.supabaseUserId()).orElseGet(() -> {
      AppUser created = new AppUser();
      created.setId(UUID.randomUUID());
      created.setSupabaseUserId(request.supabaseUserId().trim());
      created.setStatus("active");
      created.setCreatedAt(now);
      return created;
    });

    appUser.setEmail(request.email().trim().toLowerCase());
    appUser.setFullName(request.fullName() == null || request.fullName().isBlank() ? null : request.fullName().trim());
    appUser.setStatus("active");
    appUser.setUpdatedAt(now);

    AppUser saved = appUserRepository.save(appUser);
    return toResponse(saved);
  }

  @Transactional
  public void ensureTenantOwner(UUID tenantId, String supabaseUserId, String email, String fullName) {
    if (supabaseUserId == null || supabaseUserId.isBlank() || email == null || email.isBlank()) {
      return;
    }

    AppUserResponse response = sync(new AppUserSyncRequest(supabaseUserId, email, fullName));
    AppUser appUser = appUserRepository.findById(response.id())
      .orElseThrow(() -> new IllegalStateException("Failed to reload synced app user"));
    Tenant tenant = tenantRepository.findById(tenantId)
      .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    tenantUserMembershipRepository.findByTenantAndAppUser(tenant, appUser).orElseGet(() -> {
      Instant now = Instant.now();
      TenantUserMembership membership = new TenantUserMembership();
      membership.setId(UUID.randomUUID());
      membership.setTenant(tenant);
      membership.setAppUser(appUser);
      membership.setRole("owner");
      membership.setCreatedAt(now);
      membership.setUpdatedAt(now);
      return tenantUserMembershipRepository.save(membership);
    });
  }

  @Transactional(readOnly = true)
  public List<TenantMembershipResponse> listMemberships(String supabaseUserId) {
    AppUser appUser = appUserRepository.findBySupabaseUserId(supabaseUserId)
      .orElseThrow(() -> new IllegalArgumentException("App user not found for supabaseUserId=" + supabaseUserId));

    return tenantUserMembershipRepository.findAllByAppUser(appUser).stream()
      .map(membership -> new TenantMembershipResponse(
        membership.getTenant().getId(),
        membership.getTenant().getName(),
        membership.getTenant().getSlug(),
        membership.getRole()
      ))
      .toList();
  }

  private AppUserResponse toResponse(AppUser appUser) {
    return new AppUserResponse(
      appUser.getId(),
      appUser.getSupabaseUserId(),
      appUser.getEmail(),
      appUser.getFullName(),
      appUser.getStatus()
    );
  }
}
