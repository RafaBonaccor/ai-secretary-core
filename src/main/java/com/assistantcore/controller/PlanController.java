package com.assistantcore.controller;

import com.assistantcore.dto.PlanCatalogItemResponse;
import com.assistantcore.service.PlanCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

  private final PlanCatalogService planCatalogService;

  public PlanController(PlanCatalogService planCatalogService) {
    this.planCatalogService = planCatalogService;
  }

  @GetMapping
  public List<PlanCatalogItemResponse> listPlans() {
    return planCatalogService.listPlans();
  }
}
