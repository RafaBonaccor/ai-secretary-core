package com.assistantcore.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromptSafetyService {

  private static final int DEFAULT_MAX_LENGTH = 1500;
  private static final int STRICT_MAX_LENGTH = 1500;
  private static final int BUSINESS_CONTEXT_TOTAL_MAX_LENGTH = 6000;
  private static final Map<String, Integer> FIELD_MAX_LENGTHS = Map.ofEntries(
    Map.entry("businessName", 120),
    Map.entry("brandName", 120),
    Map.entry("businessType", 80),
    Map.entry("ownerName", 120),
    Map.entry("city", 120),
    Map.entry("neighborhood", 120),
    Map.entry("address", 240),
    Map.entry("workingHours", 800),
    Map.entry("services", 1200),
    Map.entry("specialties", 800),
    Map.entry("targetAudience", 800),
    Map.entry("priceNotes", 1000),
    Map.entry("bookingPolicy", 1500),
    Map.entry("cancellationPolicy", 1200),
    Map.entry("toneOfVoice", 400),
    Map.entry("greetingStyle", 500),
    Map.entry("assistantBehaviorPrompt", 1500),
    Map.entry("instagramHandle", 80),
    Map.entry("additionalContext", 1500),
    Map.entry("timezone", 80)
  );
  private static final Set<String> PROMPT_BUDGET_FIELDS = Set.of(
    "businessName",
    "brandName",
    "businessType",
    "ownerName",
    "city",
    "neighborhood",
    "address",
    "workingHours",
    "services",
    "specialties",
    "targetAudience",
    "priceNotes",
    "bookingPolicy",
    "cancellationPolicy",
    "toneOfVoice",
    "greetingStyle",
    "assistantBehaviorPrompt",
    "instagramHandle",
    "additionalContext"
  );
  private static final List<Pattern> STRICT_BLOCKED_PATTERNS = List.of(
    Pattern.compile("(?i)ignore\\s+.*(instruction|instructions|prompt|system|developer|assistant)"),
    Pattern.compile("(?i)(ignore|disregard|forget|override|bypass)\\s+.*(rule|rules|policy|policies|instruction|instructions|prompt)"),
    Pattern.compile("(?i)(ignore|desconsidere|esqueca|substitua|ignore|olvida|dimentica|ignora)\\s+.*(instruc|regra|regole|rule|prompt|sistema|system)"),
    Pattern.compile("(?i)(system|developer)\\s+prompt"),
    Pattern.compile("(?i)(prompt\\s+do\\s+sistema|prompt\\s+de\\s+sistema|prompt\\s+di\\s+sistema)"),
    Pattern.compile("(?i)reveal\\s+.*(prompt|instruction|rules|system)"),
    Pattern.compile("(?i)(revele|mostrar|mostre|muestra|mostra|exiba)\\s+.*(prompt|regra|regole|rule|rules|sistema|system|ocult)"),
    Pattern.compile("(?i)show\\s+.*(prompt|instruction|rules|hidden)"),
    Pattern.compile("(?i)chain\\s+of\\s+thought"),
    Pattern.compile("(?i)(thought\\s+process|reasoning\\s+trace|step\\s+by\\s+step\\s+reasoning)"),
    Pattern.compile("(?i)(cadeia\\s+de\\s+raciocinio|corrente\\s+di\\s+pensiero|cadena\\s+de\\s+pensamiento)"),
    Pattern.compile("(?i)prompt\\s+injection"),
    Pattern.compile("(?i)jailbreak"),
    Pattern.compile("(?i)(developer\\s+mode|modo\\s+desenvolvedor|modo\\s+developer|modalita\\s+sviluppatore|modo\\s+desarrollador)"),
    Pattern.compile("(?i)tool\\s*call"),
    Pattern.compile("(?i)function\\s*call"),
    Pattern.compile("(?i)(ferramenta|herramienta|strumento)\\s*(call|chamada|llamada)"),
    Pattern.compile("(?i)role\\s*:\\s*(system|developer|assistant|tool|function)"),
    Pattern.compile("(?i)(system|developer|assistant|tool|function)\\s*:\\s"),
    Pattern.compile("(?i)<\\s*/?\\s*(system|developer|assistant|tool|function)[^>]*>")
  );

  public String sanitizeContextField(String fieldKey, String value) {
    return sanitizeInput(value, maxLengthFor(fieldKey, DEFAULT_MAX_LENGTH), false, fieldKey);
  }

  public String sanitizeAssistantBehaviorPrompt(String value) {
    return sanitizeInput(value, maxLengthFor("assistantBehaviorPrompt", STRICT_MAX_LENGTH), true, "assistantBehaviorPrompt");
  }

  public String sanitizeForPromptRendering(String value, boolean strict) {
    return sanitizeForRendering(value, strict ? STRICT_MAX_LENGTH : DEFAULT_MAX_LENGTH, strict);
  }

  public void validateBusinessContextBudget(Map<String, Object> context) {
    int totalLength = 0;

    for (String fieldKey : PROMPT_BUDGET_FIELDS) {
      Object value = context.get(fieldKey);
      if (value == null) {
        continue;
      }

      String normalized = String.valueOf(value).trim();
      if (normalized.isBlank()) {
        continue;
      }

      totalLength += normalized.length();
    }

    if (totalLength > BUSINESS_CONTEXT_TOTAL_MAX_LENGTH) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Business context exceeds the total limit of " + BUSINESS_CONTEXT_TOTAL_MAX_LENGTH + " characters"
      );
    }
  }

  private String sanitizeInput(String value, int maxLength, boolean strict, String fieldKey) {
    if (value == null) {
      return null;
    }

    String joined = normalizeAndJoin(value, strict);
    if (joined == null) {
      return null;
    }

    if (joined.length() > maxLength) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        displayFieldName(fieldKey) + " exceeds the maximum length of " + maxLength + " characters"
      );
    }

    return joined;
  }

  private String sanitizeForRendering(String value, int maxLength, boolean strict) {
    String joined = normalizeAndJoin(value, strict);
    if (joined == null) {
      return null;
    }

    return joined.length() <= maxLength ? joined : joined.substring(0, maxLength).trim();
  }

  private String normalizeAndJoin(String value, boolean strict) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
      .replace('\u00A0', ' ')
      .replace("\r\n", "\n")
      .replace('\r', '\n');

    normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\n\t]]+", " ");

    List<String> safeLines = new ArrayList<>();
    for (String rawLine : normalized.split("\n")) {
      String line = rawLine.replaceAll("\\s+", " ").trim();
      if (line.isBlank()) {
        continue;
      }

      line = stripDangerousTokens(line);
      if (line.isBlank()) {
        continue;
      }

      if (strict && isBlocked(line)) {
        continue;
      }

      safeLines.add(line);
    }

    String joined = String.join("\n", safeLines).trim();
    if (joined.isBlank()) {
      return null;
    }

    return joined;
  }

  private boolean isBlocked(String line) {
    String normalized = line.toLowerCase(Locale.ROOT);
    return STRICT_BLOCKED_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
  }

  private String stripDangerousTokens(String line) {
    String sanitized = line
      .replace("```", " ")
      .replace("<", " ")
      .replace(">", " ")
      .replace("{", " ")
      .replace("}", " ")
      .replace("[", " ")
      .replace("]", " ")
      .replaceAll("\\s+", " ")
      .trim();

    return sanitized;
  }

  private int maxLengthFor(String fieldKey, int fallback) {
    if (fieldKey == null || fieldKey.isBlank()) {
      return fallback;
    }

    return FIELD_MAX_LENGTHS.getOrDefault(fieldKey, fallback);
  }

  private String displayFieldName(String fieldKey) {
    if (fieldKey == null || fieldKey.isBlank()) {
      return "Field";
    }

    return switch (fieldKey) {
      case "businessName" -> "Business name";
      case "brandName" -> "Brand name";
      case "businessType" -> "Business type";
      case "ownerName" -> "Owner name";
      case "city" -> "City";
      case "neighborhood" -> "Neighborhood";
      case "address" -> "Address";
      case "workingHours" -> "Working hours";
      case "services" -> "Services";
      case "specialties" -> "Specialties";
      case "targetAudience" -> "Target audience";
      case "priceNotes" -> "Price notes";
      case "bookingPolicy" -> "Booking policy";
      case "cancellationPolicy" -> "Cancellation policy";
      case "toneOfVoice" -> "Tone of voice";
      case "greetingStyle" -> "Greeting style";
      case "assistantBehaviorPrompt" -> "Assistant instructions";
      case "instagramHandle" -> "Instagram handle";
      case "additionalContext" -> "Additional context";
      case "timezone" -> "Timezone";
      default -> "Field";
    };
  }
}
