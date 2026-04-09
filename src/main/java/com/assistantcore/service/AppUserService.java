package com.assistantcore.service;

import com.assistantcore.dto.AppUserResponse;
import com.assistantcore.dto.AppUserSyncRequest;
import com.assistantcore.dto.AppUserWorkspaceResponse;
import com.assistantcore.dto.TenantMembershipResponse;
import com.assistantcore.model.AppUser;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Tenant;
import com.assistantcore.model.TenantUserMembership;
import com.assistantcore.repository.AppUserRepository;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.ChannelInstanceRepository;
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
  private final ChannelInstanceRepository channelInstanceRepository;
  private final CalendarConnectionRepository calendarConnectionRepository;

  public AppUserService(
    AppUserRepository appUserRepository,
    TenantRepository tenantRepository,
    TenantUserMembershipRepository tenantUserMembershipRepository,
    ChannelInstanceRepository channelInstanceRepository,
    CalendarConnectionRepository calendarConnectionRepository
  ) {
    this.appUserRepository = appUserRepository;
    this.tenantRepository = tenantRepository;
    this.tenantUserMembershipRepository = tenantUserMembershipRepository;
    this.channelInstanceRepository = channelInstanceRepository;
    this.calendarConnectionRepository = calendarConnectionRepository;
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

  @Transactional(readOnly = true)
  public AppUserWorkspaceResponse loadWorkspace(String supabaseUserId) {
    AppUser appUser = appUserRepository.findBySupabaseUserId(supabaseUserId).orElse(null);
    if (appUser == null) {
      return emptyWorkspace();
    }

    TenantUserMembership membership = tenantUserMembershipRepository.findAllByAppUser(appUser).stream()
      .sorted((left, right) -> membershipRank(left.getRole()) == membershipRank(right.getRole())
        ? left.getCreatedAt().compareTo(right.getCreatedAt())
        : Integer.compare(membershipRank(left.getRole()), membershipRank(right.getRole())))
      .findFirst()
      .orElse(null);

    if (membership == null) {
      return emptyWorkspace();
    }

    Tenant tenant = membership.getTenant();
    ChannelInstance channelInstance = channelInstanceRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenant.getId()).orElse(null);
    CalendarConnection calendarConnection = calendarConnectionRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
      .findFirst()
      .orElse(null);

    return new AppUserWorkspaceResponse(
      true,
      tenant.getId(),
      tenant.getName(),
      tenant.getSlug(),
      membership.getRole(),
      channelInstance == null ? null : channelInstance.getId(),
      channelInstance == null ? null : channelInstance.getStatus(),
      channelInstance == null ? null : channelInstance.getPhoneNumber(),
      channelInstance == null ? null : channelInstance.getInstanceName(),
      calendarConnection == null ? null : calendarConnection.getId(),
      calendarConnection == null ? null : calendarConnection.getStatus(),
      calendarConnection == null ? null : calendarConnection.getGoogleAccountEmail(),
      calendarConnection == null ? null : calendarConnection.getGoogleCalendarName()
    );
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

  private int membershipRank(String role) {
    if ("owner".equalsIgnoreCase(role)) {
      return 0;
    }
    if ("admin".equalsIgnoreCase(role)) {
      return 1;
    }
    return 2;
  }

  private AppUserWorkspaceResponse emptyWorkspace() {
    return new AppUserWorkspaceResponse(false, null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
