package com.assistantcore.service;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfficialEmailPolicyService {

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private static final Set<String> ALLOWED_DOMAINS = Set.of(
    "gmail.com",
    "googlemail.com",
    "outlook.com",
    "hotmail.com",
    "live.com",
    "msn.com",
    "icloud.com",
    "me.com",
    "mac.com",
    "yahoo.com",
    "ymail.com",
    "rocketmail.com",
    "proton.me",
    "protonmail.com",
    "pm.me",
    "aol.com",
    "gmx.com",
    "gmx.net",
    "zoho.com",
    "uol.com.br",
    "bol.com.br"
  );

  public String requireOfficialEmail(String value) {
    String normalized = normalize(value);
    if (!isOfficialEmail(normalized)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Use an email from an official provider such as Gmail, Outlook, Hotmail, Live, iCloud, Yahoo or Proton"
      );
    }

    return normalized;
  }

  public boolean isOfficialEmail(String value) {
    String normalized = normalize(value);
    if (!EMAIL_PATTERN.matcher(normalized).matches()) {
      return false;
    }

    int separatorIndex = normalized.lastIndexOf('@');
    if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
      return false;
    }

    String domain = normalized.substring(separatorIndex + 1);
    return ALLOWED_DOMAINS.stream().anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
