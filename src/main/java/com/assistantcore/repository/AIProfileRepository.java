package com.assistantcore.repository;

import com.assistantcore.model.AIProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIProfileRepository extends JpaRepository<AIProfile, UUID> {
  List<AIProfile> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
  Optional<AIProfile> findByTenantIdAndName(UUID tenantId, String name);
  Optional<AIProfile> findByTenantIdAndSlug(UUID tenantId, String slug);
  long countByTenantId(UUID tenantId);
}
