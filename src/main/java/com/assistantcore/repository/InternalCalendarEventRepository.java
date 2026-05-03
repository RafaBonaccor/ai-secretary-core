package com.assistantcore.repository;

import com.assistantcore.model.InternalCalendarEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalCalendarEventRepository extends JpaRepository<InternalCalendarEvent, UUID> {
  List<InternalCalendarEvent> findByCalendarConnectionIdAndStatusNotIgnoreCaseAndEndAtAfterAndStartAtBeforeOrderByStartAtAsc(
    UUID calendarConnectionId,
    String status,
    Instant endAfter,
    Instant startBefore
  );

  Optional<InternalCalendarEvent> findByCalendarConnectionTenantIdAndId(UUID tenantId, UUID id);

  Optional<InternalCalendarEvent> findByCalendarConnectionIdAndId(UUID calendarConnectionId, UUID id);
}
