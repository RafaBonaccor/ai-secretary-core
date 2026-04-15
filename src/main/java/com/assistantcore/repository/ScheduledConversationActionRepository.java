package com.assistantcore.repository;

import com.assistantcore.model.ScheduledConversationAction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledConversationActionRepository extends JpaRepository<ScheduledConversationAction, UUID> {

  @EntityGraph(attributePaths = {"contact", "conversation", "channelInstance"})
  List<ScheduledConversationAction> findTop100ByTenantIdOrderByScheduledForDesc(UUID tenantId);

  @EntityGraph(attributePaths = {"contact", "conversation", "channelInstance"})
  List<ScheduledConversationAction> findTop100ByTenantIdAndConversationIdOrderByScheduledForDesc(UUID tenantId, UUID conversationId);

  @EntityGraph(attributePaths = {"contact", "conversation", "channelInstance"})
  Optional<ScheduledConversationAction> findByIdAndTenantId(UUID id, UUID tenantId);

  @EntityGraph(attributePaths = {"contact", "conversation", "channelInstance"})
  List<ScheduledConversationAction> findTop20ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(String status, Instant scheduledFor);

  @EntityGraph(attributePaths = {"contact", "conversation", "channelInstance"})
  Optional<ScheduledConversationAction> findDetailedById(UUID id);
}
