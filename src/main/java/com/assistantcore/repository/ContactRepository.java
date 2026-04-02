package com.assistantcore.repository;

import com.assistantcore.model.Contact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
  Optional<Contact> findByChannelInstanceIdAndRemoteJid(UUID channelInstanceId, String remoteJid);
}
