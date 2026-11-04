package com.shikkhasathi.integration.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.integration.entity.IntegrationSetting;
import com.shikkhasathi.integration.entity.IntegrationTest;
import com.shikkhasathi.integration.repository.IntegrationSettingRepository;
import com.shikkhasathi.integration.repository.IntegrationTestRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration settings (CLAUDE.md §0B): effective value = Admin panel (AES-256-GCM in {@code integration_settings})
 * → env var → field default → unset (Fake adapter outside production). Cached ~60 s; invalidated on save.
 * Secrets are write-only: the API only ever returns {@code ••••1234}.
 */
@Service
public class IntegrationService {
  private static final long CACHE_MS = 60_000;

  public record Resolved(Map<String, String> values, Map<String, String> sources, long at) {}

  public record FieldState(String key, String value, String source, boolean isSecret, String updatedAt) {}

  public record LastTest(boolean ok, String message, String at) {}

  public record State(String provider, String mode, List<FieldState> fields, LastTest lastTest) {}

  public record TestResult(boolean ok, String message) {}

  /** values, opts(to, actorId) → result. Registered by each adapter for the "সংযোগ পরীক্ষা করুন" button. */
  public interface Tester extends BiFunction<Map<String, String>, TestOptions, TestResult> {}

  public record TestOptions(String to, UUID actorId) {}

  private final IntegrationCatalog catalog;
  private final IntegrationSettingRepository repo;
  private final IntegrationTestRepository tests;
  private final Crypto crypto;
  private final AuditService audit;
  private final Environment env;
  private final AppProperties props;
  private final JsonMapper json;
  private final Map<String, Resolved> cache = new ConcurrentHashMap<>();
  private final Map<String, Tester> testers = new ConcurrentHashMap<>();
  /** test hook: env overrides without touching process env */
  private final Map<String, String> envOverrides = new ConcurrentHashMap<>();

  public IntegrationService(IntegrationCatalog catalog, IntegrationSettingRepository repo, IntegrationTestRepository tests, Crypto crypto, AuditService audit, Environment env, AppProperties props, JsonMapper json) {
    this.catalog = catalog;
    this.repo = repo;
    this.tests = tests;
    this.crypto = crypto;
    this.audit = audit;
    this.env = env;
    this.props = props;
    this.json = json;
  }

  public IntegrationCatalog catalog() {
    return catalog;
  }

  private String envValue(String name) {
    if (name == null) return null;
    if (envOverrides.containsKey(name)) return envOverrides.get(name);
    String v = env.getProperty(name);
    return v == null || v.isEmpty() ? null : v;
  }

  /** Tests only: set (or clear with null) an env fallback value. */
  public void setEnvOverride(String name, String value) {
    if (value == null) envOverrides.remove(name);
    else envOverrides.put(name, value);
    invalidate(null);
  }

  private Resolved resolve(String provider) {
    Resolved hit = cache.get(provider);
    if (hit != null && System.currentTimeMillis() - hit.at() < CACHE_MS) return hit;
    var def = catalog.get(provider);
    var rows = repo.findByProvider(provider);
    Map<String, String> values = new HashMap<>(), sources = new HashMap<>();
    for (var f : def.fields()) {
      var row = rows.stream().filter(r -> r.getKey().equals(f.key())).findFirst();
      if (row.isPresent()) {
        values.put(f.key(), crypto.decrypt(row.get().getValueEncrypted()));
        sources.put(f.key(), "admin");
      } else if (envValue(f.env()) != null) {
        values.put(f.key(), envValue(f.env()));
        sources.put(f.key(), "env");
      } else if (f.defaultValue() != null) {
        values.put(f.key(), f.defaultValue());
        sources.put(f.key(), "default");
      } else {
        sources.put(f.key(), "unset");
      }
    }
    Resolved r = new Resolved(Collections.unmodifiableMap(values), sources, System.currentTimeMillis());
    cache.put(provider, r);
    return r;
  }

  /** Effective values for an adapter (missing keys are absent). */
  public Map<String, String> get(String provider) {
    return resolve(provider).values();
  }

  public double businessNumber(String key, double fallback) {
    String v = get("business").get(key);
    try {
      double n = v == null ? Double.NaN : Double.parseDouble(v);
      return Double.isFinite(n) ? n : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  public boolean businessFlag(String key, boolean fallback) {
    String v = get("business").get(key);
    return v == null ? fallback : (v.equals("true") || v.equals("1"));
  }

  public void invalidate(String provider) {
    if (provider == null) cache.clear();
    else cache.remove(provider);
  }

  /** configured = required keys from Admin; env = satisfied via env; otherwise fake (dev) / disabled (prod). */
  public String mode(String provider) {
    var def = catalog.get(provider);
    var r = resolve(provider);
    if (def.requiredKeys().isEmpty()) return "configured";
    if ("fake".equals(r.values().get("gateway")) || "fake".equals(r.values().get("provider"))) return props.isProd() ? "disabled" : "fake";
    boolean ok = def.requiredKeys().stream().allMatch(k -> r.values().get(k) != null && !r.values().get(k).isEmpty());
    if (!ok) return props.isProd() ? "disabled" : "fake";
    return def.requiredKeys().stream().allMatch(k -> "admin".equals(r.sources().get(k))) ? "configured" : "env";
  }

  public State state(String provider) {
    var def = catalog.get(provider);
    var rows = repo.findByProvider(provider);
    var r = resolve(provider);
    var t = tests.findById(provider);
    List<FieldState> fields = def.fields().stream().map(f -> {
      String v = r.values().get(f.key());
      String updated = rows.stream().filter(x -> x.getKey().equals(f.key())).findFirst().map(x -> JsonConfig.iso(x.getUpdatedAt())).orElse(null);
      return new FieldState(f.key(), v == null ? null : f.secret() ? Crypto.mask(v) : v, r.sources().getOrDefault(f.key(), "unset"), f.secret(), updated);
    }).toList();
    return new State(provider, mode(provider), fields, t.map(x -> new LastTest(x.getOk(), x.getMessage(), JsonConfig.iso(x.getUpdatedAt()))).orElse(null));
  }

  public List<State> all() {
    return catalog.all().stream().map(d -> state(d.provider())).toList();
  }

  @Transactional(transactionManager = "coreTransactionManager")
  public State update(String provider, Map<String, String> values, UUID actorId, String ip) {
    var def = catalog.get(provider);
    State before = state(provider);
    for (var e : values.entrySet()) {
      var field = def.fields().stream().filter(f -> f.key().equals(e.getKey())).findFirst().orElseThrow(() -> ApiException.badRequest("Unknown field " + e.getKey()));
      String value = e.getValue();
      if (value == null) {
        repo.deleteByProviderAndKey(provider, e.getKey());
        continue;
      }
      if (field.secret() && value.isEmpty()) continue; // write-only: empty = keep existing
      if ("number".equals(field.type()) && !value.isEmpty()) {
        try {
          Double.parseDouble(value);
        } catch (NumberFormatException ex) {
          throw ApiException.badRequest(field.label() + ": সংখ্যা দিন");
        }
      }
      if ("select".equals(field.type()) && field.options() != null && !field.options().contains(value)) throw ApiException.badRequest(field.label() + ": অবৈধ মান");
      if ("json".equals(field.type()) && !value.isEmpty()) {
        try {
          json.readTree(value);
        } catch (RuntimeException ex) {
          throw ApiException.badRequest(field.label() + ": বৈধ JSON দিন");
        }
      }
      var row = repo.findByProviderAndKey(provider, e.getKey()).orElseGet(() -> new IntegrationSetting().setProvider(provider).setKey(e.getKey()));
      row.setValueEncrypted(crypto.encrypt(value)).setIsSecret(field.secret()).setUpdatedBy(actorId).setUpdatedAt(Instant.now());
      repo.save(row);
    }
    repo.flush();
    invalidate(provider);
    State after = state(provider);
    // audit stores masked values only
    audit.log(AuditService.Entry.of(actorId, "integration.update", "integration_settings", provider).before(before.fields()).after(after.fields()).ip(ip));
    return after;
  }

  public void registerTester(String provider, Tester t) {
    testers.put(provider, t);
  }

  @Transactional(transactionManager = "coreTransactionManager")
  public TestResult test(String provider, String to, UUID actorId) {
    Tester t = testers.get(provider);
    TestResult result;
    if (t == null) result = new TestResult(false, "এই ইন্টিগ্রেশনের জন্য টেস্ট নেই");
    else {
      try {
        result = t.apply(get(provider), new TestOptions(to, actorId));
      } catch (RuntimeException ex) {
        String m = ex.getMessage() == null ? "অজানা সমস্যা" : ex.getMessage();
        result = new TestResult(false, m.length() > 300 ? m.substring(0, 300) : m);
      }
    }
    var row = tests.findById(provider).orElseGet(() -> new IntegrationTest().setProvider(provider));
    row.setOk(result.ok()).setMessage(result.message()).setTestedBy(actorId).setUpdatedAt(Instant.now());
    tests.save(row);
    audit.log(AuditService.Entry.of(actorId, "integration.test", "integration_settings", provider).after(result));
    return result;
  }
}
