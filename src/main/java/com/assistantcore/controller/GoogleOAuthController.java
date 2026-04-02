package com.assistantcore.controller;

import com.assistantcore.dto.GoogleCalendarItemResponse;
import com.assistantcore.dto.GoogleOAuthCallbackResponse;
import com.assistantcore.dto.GoogleOAuthStartResponse;
import com.assistantcore.service.GoogleOAuthService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oauth/google/calendar")
public class GoogleOAuthController {

  private final GoogleOAuthService googleOAuthService;

  public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
    this.googleOAuthService = googleOAuthService;
  }

  @GetMapping("/start/{connectionId}")
  public GoogleOAuthStartResponse start(@PathVariable UUID connectionId) {
    return googleOAuthService.startCalendarOAuth(connectionId);
  }

  @GetMapping("/callback")
  public GoogleOAuthCallbackResponse callback(@RequestParam String code, @RequestParam String state) {
    return googleOAuthService.handleCalendarCallback(code, state);
  }

  @GetMapping("/available-calendars/{connectionId}")
  public List<GoogleCalendarItemResponse> availableCalendars(@PathVariable UUID connectionId) {
    return googleOAuthService.listAvailableCalendars(connectionId);
  }
}
