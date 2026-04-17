package com.assistantcore.service;

import com.assistantcore.model.AppUser;
import com.assistantcore.model.CalendarConnection;
import com.assistantcore.model.ChannelInstance;
import com.assistantcore.model.Tenant;
import com.assistantcore.repository.AppUserRepository;
import com.assistantcore.repository.CalendarConnectionRepository;
import com.assistantcore.repository.ChannelInstanceRepository;
import com.assistantcore.repository.TenantRepository;
import com.assistantcore.repository.TenantUserMembershipRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppAuthorizationService {

  public static final String APP_USER_HEADER = "X-App-User-Id";

  private final AppUserRepository appUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantUserMembershipRepository tenantUserMembershipRepository;
  private final ChannelInstanceRepository channelInstanceRepository;
  private final CalendarConnectionRepository calendarConnectionRepository;

  public AppAuthorizationService(
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

  public void requireAuthenticatedAppRequest() {
    requireCurrentSupabaseUserId();
  }

  public void requireSameSupabaseUser(String requestedSupabaseUserId) {
    String currentSupabaseUserId = requireCurrentSupabaseUserId();
    if (!currentSupabaseUserId.equals(requestedSupabaseUserId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access another app user");
    }
  }

  @Transactional(readOnly = true)
  public void requireTenantMembership(UUID tenantId) {
    AppUser appUser = requireCurrentAppUser();
    Tenant tenant = tenantRepository.findById(tenantId)
      .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

    tenantUserMembershipRepository.findByTenantAndAppUser(tenant, appUser)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this tenant"));
  }

  @Transactional(readOnly = true)
  public void requireChannelInstanceAccess(UUID channelInstanceId) {
    ChannelInstance channelInstance = channelInstanceRepository.findById(channelInstanceId)
      .orElseThrow(() -> new EntityNotFoundException("Channel instance not found: " + channelInstanceId));
    requireTenantMembership(channelInstance.getTenant().getId());
  }

  @Transactional(readOnly = true)
  public void requireCalendarConnectionAccess(UUID connectionId) {
    CalendarConnection connection = calendarConnectionRepository.findById(connectionId)
      .orElseThrow(() -> new EntityNotFoundException("Calendar connection not found: " + connectionId));
    requireTenantMembership(connection.getTenant().getId());
  }

  private AppUser requireCurrentAppUser() {
    String supabaseUserId = requireCurrentSupabaseUserId();
    return appUserRepository.findBySupabaseUserId(supabaseUserId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "App user is not synced"));
  }

  private String requireCurrentSupabaseUserId() {
    HttpServletRequest request = currentRequest();
    String supabaseUserId = request.getHeader(APP_USER_HEADER);

    if (!StringUtils.hasText(supabaseUserId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing app user context");
    }

    return supabaseUserId.trim();
  }

  private HttpServletRequest currentRequest() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Request context is not available");
    }

    return attributes.getRequest();
  }
}
