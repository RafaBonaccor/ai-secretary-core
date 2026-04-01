package com.assistantcore.repository;

import com.assistantcore.model.Plan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
  Optional<Plan> findByCode(String code);
}
