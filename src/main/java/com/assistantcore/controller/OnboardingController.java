package com.assistantcore.controller;

import com.assistantcore.dto.MockOnboardingRequest;
import com.assistantcore.dto.MockOnboardingResponse;
import com.assistantcore.service.AppAuthorizationService;
import com.assistantcore.service.MockOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

  private final MockOnboardingService mockOnboardingService;
  private final AppAuthorizationService appAuthorizationService;

  public OnboardingController(
    MockOnboardingService mockOnboardingService,
    AppAuthorizationService appAuthorizationService
  ) {
    this.mockOnboardingService = mockOnboardingService;
    this.appAuthorizationService = appAuthorizationService;
  }

  @PostMapping("/mock")
  @ResponseStatus(HttpStatus.CREATED)
  public MockOnboardingResponse createMock(@Valid @RequestBody MockOnboardingRequest request) {
    appAuthorizationService.requireSameSupabaseUser(request.ownerSupabaseUserId());
    return mockOnboardingService.create(request);
  }
}
