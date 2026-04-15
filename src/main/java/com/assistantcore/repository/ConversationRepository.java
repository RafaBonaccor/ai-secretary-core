package com.assistantcore.repository;

import com.assistantcore.model.Conversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  Optional<Conversation> findByChannelInstanceIdAndContactId(UUID channelInstanceId, UUID contactId);

  @EntityGraph(attributePaths = {"contact", "channelInstance"})
  List<Conversation> findTop100ByTenantIdOrderByLastMessageAtDesc(UUID tenantId);

  @EntityGraph(attributePaths = {"contact", "channelInstance"})
  Optional<Conversation> findByIdAndTenantId(UUID id, UUID tenantId);
}
