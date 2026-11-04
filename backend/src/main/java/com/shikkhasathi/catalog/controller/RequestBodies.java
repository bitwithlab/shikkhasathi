package com.shikkhasathi.catalog.controller;

import com.shikkhasathi.common.ApiException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Parses a JSON body the way the previous API's Zod schemas did: {@code .trim()} on the listed string fields before
 * validation, required keys (even when nullable), "optional but not nullable" keys, and a record of which keys were
 * sent (PATCH bodies only change those). Failures are 422 with {@code [{path:"body.x", message}]}.
 */
@Component
public class RequestBodies {
  private final JsonMapper json;
  private final Validator validator;

  public RequestBodies(JsonMapper json, Validator validator) {
    this.json = json;
    this.validator = validator;
  }

  /** Field rules for one schema. */
  public record Spec(Set<String> trim, Set<String> required, Set<String> notNull) {
    public static Spec of() {
      return new Spec(Set.of(), Set.of(), Set.of());
    }

    public Spec trim(String... f) { return new Spec(Set.of(f), required, notNull); }
    public Spec required(String... f) { return new Spec(trim, Set.of(f), notNull); }
    public Spec notNull(String... f) { return new Spec(trim, required, Set.of(f)); }
  }

  public record Parsed<T>(T value, Set<String> present) {
    public boolean has(String key) {
      return present.contains(key);
    }
  }

  public <T> Parsed<T> parse(JsonNode body, Class<T> type, Spec spec) {
    return parse(body, type, spec, "body");
  }

  /** PATCH body ({@code patchOf}): every key optional, so "required" (@NotNull) constraints are dropped. */
  public <T> Parsed<T> parse(JsonNode body, Class<T> type, Spec spec, boolean patch) {
    return parse(body, type, spec, "body", patch);
  }

  public <T> Parsed<T> parse(JsonNode body, Class<T> type, Spec spec, String path) {
    return parse(body, type, spec, path, false);
  }

  private <T> Parsed<T> parse(JsonNode body, Class<T> type, Spec spec, String path, boolean patch) {
    ObjectNode node;
    if (body == null || body.isNull() || body.isMissingNode()) node = json.createObjectNode();
    else if (body instanceof ObjectNode o) node = o.deepCopy();
    else throw fail(List.of(Map.of("path", path, "message", "Invalid input: expected object")));

    List<Map<String, String>> errors = new ArrayList<>();
    for (String f : spec.trim()) {
      JsonNode v = node.get(f);
      if (v != null && v.isString()) node.set(f, StringNode.valueOf(v.asString().strip()));
    }
    for (String f : spec.required().stream().sorted().toList())
      if (!node.has(f)) errors.add(Map.of("path", path + "." + f, "message", "Invalid input: expected value, received undefined"));
    for (String f : spec.notNull().stream().sorted().toList())
      if (node.has(f) && node.get(f).isNull()) errors.add(Map.of("path", path + "." + f, "message", "Invalid input: received null"));
    if (!errors.isEmpty()) throw fail(errors);

    T value;
    try {
      value = json.treeToValue(node, type);
    } catch (JacksonException e) {
      throw fail(List.of(Map.of("path", path, "message", e.getOriginalMessage() == null ? "Invalid input" : e.getOriginalMessage())));
    }
    var violations = validator.validate(value).stream()
        .filter(v -> !(patch && v.getConstraintDescriptor().getAnnotation() instanceof jakarta.validation.constraints.NotNull
            && !v.getPropertyPath().toString().contains("[")))
        .toList();
    if (!violations.isEmpty()) {
      var details = violations.stream()
          .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
          .map(v -> Map.of("path", path + "." + v.getPropertyPath(), "message", v.getMessage()))
          .toList();
      throw fail(details);
    }
    Set<String> present = new LinkedHashSet<>();
    node.propertyNames().forEach(present::add);
    return new Parsed<>(value, present);
  }

  private static ApiException fail(List<Map<String, String>> details) {
    return ApiException.validation(details.get(0).get("message"), details);
  }
}
