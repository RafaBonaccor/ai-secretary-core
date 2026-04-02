package com.assistantcore.repository;

import com.assistantcore.model.WorkingHour;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHourRepository extends JpaRepository<WorkingHour, UUID> {
  List<WorkingHour> findByCalendarConnectionIdOrderByWeekdayAsc(UUID calendarConnectionId);
}
