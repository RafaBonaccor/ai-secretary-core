package com.assistantcore.repository;

import com.assistantcore.model.CalendarConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarConnectionRepository extends JpaRepository<CalendarConnection, UUID> {
  List<CalendarConnection> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<CalendarConnection> findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(UUID tenantId, String status);
}
