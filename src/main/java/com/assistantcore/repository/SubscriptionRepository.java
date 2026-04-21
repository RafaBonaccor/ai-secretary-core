package com.assistantcore.repository;

import com.assistantcore.model.Subscription;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  @EntityGraph(attributePaths = {"tenant", "plan"})
  Optional<Subscription> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  @EntityGraph(attributePaths = {"tenant", "plan"})
  Optional<Subscription> findFirstByProviderSubscriptionIdOrderByCreatedAtDesc(String providerSubscriptionId);
}
