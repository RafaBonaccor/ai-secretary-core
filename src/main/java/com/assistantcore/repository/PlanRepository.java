package com.assistantcore.repository;

import com.assistantcore.model.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
  List<Plan> findAllByOrderByPriceMonthlyAsc();
  Optional<Plan> findByCode(String code);
}
