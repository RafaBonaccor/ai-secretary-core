package com.assistantcore.repository;

import com.assistantcore.model.AppUser;
import com.assistantcore.model.Tenant;
import com.assistantcore.model.TenantUserMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserMembershipRepository extends JpaRepository<TenantUserMembership, UUID> {
  Optional<TenantUserMembership> findByTenantAndAppUser(Tenant tenant, AppUser appUser);
  List<TenantUserMembership> findAllByAppUser(AppUser appUser);
}
