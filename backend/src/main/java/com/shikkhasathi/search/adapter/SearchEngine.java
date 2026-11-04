package com.shikkhasathi.search.adapter;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Meilisearch from the {@code search} integration; when no host is set, search uses the Postgres FTS fallback. */
@Service
public class SearchEngine {
  private final IntegrationService integrations;
  private final HttpTransport http;
  private final JsonMapper json;

  public SearchEngine(IntegrationService integrations, HttpTransport http, JsonMapper json) {
    this.integrations = integrations;
    this.http = http;
    this.json = json;
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("search", (c, opts) -> {
      String host = c.get("host");
      if (host == null || host.isEmpty()) return new IntegrationService.TestResult(false, "Meilisearch host দিন (না দিলে Postgres fallback চলবে)");
      var m = new MeiliClient(host, c.get("master_key"), http, json);
      m.health();
      m.configure();
      return new IntegrationService.TestResult(true, "Meilisearch সংযোগ ঠিক আছে — index settings আপডেট হয়েছে");
    });
  }

  /** Client when a host is configured, else empty. */
  public Optional<MeiliClient> meili() {
    var c = integrations.get("search");
    String host = c.get("host");
    return host == null || host.isEmpty() ? Optional.empty() : Optional.of(new MeiliClient(host, c.get("master_key"), http, json));
  }

  /** True when no Meilisearch is configured — callers search with Postgres full-text instead. */
  public boolean usePostgresFallback() {
    return meili().isEmpty();
  }
}
