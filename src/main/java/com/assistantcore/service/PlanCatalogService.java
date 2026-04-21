package com.assistantcore.service;

import com.assistantcore.dto.PlanCatalogItemResponse;
import com.assistantcore.dto.PlanEntitlementsResponse;
import com.assistantcore.model.Plan;
import com.assistantcore.repository.PlanRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanCatalogService {

  private final PlanRepository planRepository;

  public PlanCatalogService(PlanRepository planRepository) {
    this.planRepository = planRepository;
  }

  @Transactional(readOnly = true)
  public List<PlanCatalogItemResponse> listPlans() {
    return planRepository.findAllByOrderByPriceMonthlyAsc().stream()
      .sorted(Comparator.comparingInt(plan -> planSortOrder(plan.getCode())))
      .map(this::toResponse)
      .toList();
  }

  private PlanCatalogItemResponse toResponse(Plan plan) {
    return new PlanCatalogItemResponse(
      plan.getCode(),
      plan.getName(),
      plan.getPriceMonthly(),
      new PlanEntitlementsResponse(
        plan.getMessageLimit(),
        plan.getAudioLimit(),
        plan.getAutomationLimit(),
        plan.getMaxWhatsappNumbers(),
        plan.getMaxAiProfiles(),
        plan.getMaxTeamMembers(),
        resolveCalendarEnabled(plan),
        plan.isInboxEnabled(),
        plan.isCustomPromptEnabled(),
        plan.isAdvancedAutomationEnabled(),
        plan.isRealtimeVoiceEnabled(),
        plan.isFutureFeaturesEnabled(),
        plan.isPrioritySupportEnabled()
      )
    );
  }

  private int planSortOrder(String code) {
    return switch (code == null ? "" : code.trim().toLowerCase()) {
      case "trial" -> 0;
      case "starter" -> 1;
      case "pro" -> 2;
      case "scale" -> 3;
      default -> 99;
    };
  }

  private boolean resolveCalendarEnabled(Plan plan) {
    String code = plan.getCode() == null ? "" : plan.getCode().trim().toLowerCase();

    return switch (code) {
      case "trial" -> false;
      case "starter" -> true;
      default -> plan.isCalendarEnabled();
    };
  }
}
