package com.assistantcore.repository;

import com.assistantcore.model.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
  List<Message> findTop16ByConversationIdOrderBySentAtDesc(UUID conversationId);

  List<Message> findTop200ByConversationIdOrderBySentAtDesc(UUID conversationId);
}
