package com.assistantcore.controller;

import com.assistantcore.dto.TenantBusinessContextResponse;
import com.assistantcore.dto.TenantBusinessContextUpdateRequest;
import com.assistantcore.service.TenantBusinessContextService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantBusinessContextController {

  private final TenantBusinessContextService tenantBusinessContextService;

  public TenantBusinessContextController(TenantBusinessContextService tenantBusinessContextService) {
    this.tenantBusinessContextService = tenantBusinessContextService;
  }

  @GetMapping("/{tenantId}/business-context")
  public TenantBusinessContextResponse getBusinessContext(@PathVariable UUID tenantId) {
    return tenantBusinessContextService.getBusinessContext(tenantId);
  }

  @PutMapping("/{tenantId}/business-context")
  public TenantBusinessContextResponse updateBusinessContext(
    @PathVariable UUID tenantId,
    @RequestBody TenantBusinessContextUpdateRequest request
  ) {
    return tenantBusinessContextService.updateBusinessContext(tenantId, request);
  }
}
