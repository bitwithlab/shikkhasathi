package com.shikkhasathi.integration.service;

import com.shikkhasathi.common.ApiException;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalog of admin-editable integrations (fields, env fallbacks, required keys) — {@code integrations.json}.
 * Single source for the Admin → Integrations screen (served by the API).
 */
@Component
public class IntegrationCatalog {
  public record Field(String key, String label, String type, String env, String placeholder, String help, List<String> options, String defaultValue) {
    public boolean secret() {
      return "secret".equals(type);
    }
  }

  public record Def(String provider, String title, String description, List<String> requiredKeys, boolean testable, List<Field> fields) {}

  private final List<Def> defs;

  public IntegrationCatalog(JsonMapper json) throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/integrations.json")) {
      this.defs = List.of(json.readValue(in, Def[].class));
    }
  }

  public List<Def> all() {
    return defs;
  }

  public Def get(String provider) {
    return defs.stream().filter(d -> d.provider().equals(provider)).findFirst().orElseThrow(() -> ApiException.badRequest("Unknown integration"));
  }
}
