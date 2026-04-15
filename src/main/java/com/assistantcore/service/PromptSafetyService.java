package com.assistantcore.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PromptSafetyService {

  private static final int DEFAULT_MAX_LENGTH = 1200;
  private static final int STRICT_MAX_LENGTH = 2200;
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

  public String sanitizeContextField(String value) {
    return sanitize(value, DEFAULT_MAX_LENGTH, false);
  }

  public String sanitizeAssistantBehaviorPrompt(String value) {
    return sanitize(value, STRICT_MAX_LENGTH, true);
  }

  public String sanitizeForPromptRendering(String value, boolean strict) {
    return sanitize(value, strict ? STRICT_MAX_LENGTH : DEFAULT_MAX_LENGTH, strict);
  }

  private String sanitize(String value, int maxLength, boolean strict) {
    if (value == null) {
      return null;
    }

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

    return joined.length() <= maxLength ? joined : joined.substring(0, maxLength).trim();
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
}
