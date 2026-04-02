package com.assistantcore.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IntentDetectionService {

  private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\b");

  public IntentResult detect(String rawMessage) {
    String message = normalize(rawMessage);
    String intent = detectIntent(message);
    String requestedWindow = extractRequestedWindow(message);
    boolean needsCalendarCheck = switch (intent) {
      case "check_availability", "create_appointment", "reschedule_appointment" -> true;
      default -> false;
    };
    return new IntentResult(intent, requestedWindow, needsCalendarCheck);
  }

  private String detectIntent(String message) {
    if (containsAny(message, "cancelar", "desmarcar", "nao vou poder", "nao poderei")) {
      return "cancel_appointment";
    }
    if (containsAny(message, "remarcar", "mudar horario", "trocar horario", "reagendar")) {
      return "reschedule_appointment";
    }
    if (containsAny(message, "disponibilidade", "tem horario", "tem vaga", "algum horario", "alguma vaga", "horario livre", "agenda livre")) {
      return "check_availability";
    }
    if (containsAny(message, "agendar", "marcar", "quero uma consulta", "quero marcar", "preciso marcar")) {
      return "create_appointment";
    }
    if (containsAny(message, "quanto custa", "qual o valor", "preco", "preço", "orcamento", "orçamento")) {
      return "pricing_question";
    }
    return "general_inquiry";
  }

  private String extractRequestedWindow(String message) {
    List<String> parts = new ArrayList<>();

    for (String marker : List.of("hoje", "amanha", "depois de amanha", "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo")) {
      if (message.contains(marker)) {
        parts.add(marker);
      }
    }

    for (String marker : List.of("manha", "de manha", "tarde", "a tarde", "noite", "fim da tarde", "inicio da tarde")) {
      if (message.contains(marker)) {
        parts.add(marker);
      }
    }

    Matcher matcher = TIME_PATTERN.matcher(message);
    List<String> times = new ArrayList<>();
    while (matcher.find()) {
      String hour = matcher.group(1);
      String minutes = matcher.group(2) == null ? "00" : matcher.group(2);
      times.add(hour + ":" + minutes);
      if (times.size() == 2) {
        break;
      }
    }
    if (!times.isEmpty()) {
      parts.add("horarios " + String.join(" - ", times));
    }

    return parts.isEmpty() ? null : String.join(", ", parts);
  }

  private boolean containsAny(String message, String... values) {
    for (String value : values) {
      if (message.contains(value)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String message) {
    return message == null ? "" : message.toLowerCase(Locale.ROOT).replace('ç', 'c');
  }

  public record IntentResult(String intent, String requestedWindow, boolean needsCalendarCheck) {}
}
