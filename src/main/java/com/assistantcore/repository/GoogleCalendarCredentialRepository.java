package com.assistantcore.repository;

import com.assistantcore.model.GoogleCalendarCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarCredentialRepository extends JpaRepository<GoogleCalendarCredential, UUID> {
  Optional<GoogleCalendarCredential> findByCalendarConnectionId(UUID calendarConnectionId);

  void deleteByCalendarConnectionId(UUID calendarConnectionId);
}
