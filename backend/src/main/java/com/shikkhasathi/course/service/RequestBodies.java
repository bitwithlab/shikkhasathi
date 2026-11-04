package com.shikkhasathi.course.service;

import com.shikkhasathi.common.ApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a JSON request body the way the previous Zod contracts did, for bodies where presence matters (PATCH
 * semantics: an absent key is left alone, an explicit {@code null} clears a nullable field). Validation failures
 * are 422 with {@code details: [{path: "body.<field>", message}]}, like Spring's own @Valid handling.
 */
@Component
public class RequestBodies {
  private final JsonMapper json;
  private final Validator validator;

  public RequestBodies(JsonMapper json, Validator validator) {
    this.json = json;
    this.validator = validator;
  }

  /**
   * @param nullable top-level keys that may be {@code null}; any other key sent as {@code null} is rejected
   * @param required top-level keys that must be present (even when their value may be null)
   */
  public <T> T read(JsonNode raw, Class<T> type, Collection<String> nullable, Collection<String> required) {
    if (raw == null || !raw.isObject()) throw invalid("body", "Invalid input: expected object");
    for (String k : required) if (!raw.has(k)) throw invalid("body." + k, "Invalid input: expected value, received undefined");
    for (var e : raw.properties()) {
      if (e.getValue().isNull() && !nullable.contains(e.getKey()) && isComponent(type, e.getKey()))
        throw invalid("body." + e.getKey(), "Invalid input: received null");
    }
    T v;
    try {
      v = json.treeToValue(raw, type);
    } catch (RuntimeException e) {
      throw invalid("body", "Invalid input");
    }
    validate(v);
    return v;
  }

  public <T> T read(JsonNode raw, Class<T> type, Collection<String> nullable) {
    return read(raw, type, nullable, Set.of());
  }

  public void validate(Object v) {
    Set<ConstraintViolation<Object>> violations = validator.validate(v);
    if (violations.isEmpty()) return;
    List<Map<String, String>> details = new ArrayList<>();
    violations.stream()
        .sorted(Comparator.comparing(c -> String.valueOf(c.getPropertyPath())))
        .forEach(c -> {
          Map<String, String> d = new LinkedHashMap<>();
          d.put("path", "body." + c.getPropertyPath());
          d.put("message", c.getMessage());
          details.add(d);
        });
    throw ApiException.validation(details.get(0).get("message"), details);
  }

  /** True when the key was sent (with any value, null included). */
  public static boolean has(JsonNode raw, String key) {
    return raw != null && raw.has(key);
  }

  private static boolean isComponent(Class<?> type, String name) {
    if (!type.isRecord()) return true;
    for (var c : type.getRecordComponents()) if (c.getName().equals(name)) return true;
    return false;
  }

  private static ApiException invalid(String path, String message) {
    return ApiException.validation(message, List.of(Map.of("path", path, "message", message)));
  }
}
