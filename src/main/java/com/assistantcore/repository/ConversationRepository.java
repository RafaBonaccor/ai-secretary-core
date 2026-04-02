package com.assistantcore.repository;

import com.assistantcore.model.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  Optional<Conversation> findByChannelInstanceIdAndContactId(UUID channelInstanceId, UUID contactId);
}
