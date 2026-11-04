package com.shikkhasathi.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API contract oracle: {@code contracts/endpoints.json} holds every endpoint (method, path, auth, status) with
 * JSON Schemas exported from the website's Zod contracts. Tests assert Java responses match them exactly,
 * so the website and mobile app keep working against the new backend.
 */
public final class Contracts {
  private static final ObjectMapper M = new ObjectMapper();
  private static final Map<String, JsonSchema> CACHE = new ConcurrentHashMap<>();
  private static Map<String, Map<String, Object>> endpoints;

  private Contracts() {}

  @SuppressWarnings("unchecked")
  public static synchronized Map<String, Map<String, Object>> endpoints() {
    if (endpoints == null) {
      try (InputStream in = Contracts.class.getResourceAsStream("/contracts/endpoints.json")) {
        endpoints = M.readValue(in, LinkedHashMap.class);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return endpoints;
  }

  public static Map<String, Object> endpoint(String name) {
    var e = endpoints().get(name);
    if (e == null) throw new IllegalArgumentException("unknown contract endpoint " + name);
    return e;
  }

  private static JsonSchema schema(String name) {
    return CACHE.computeIfAbsent(name, n -> {
      Object s = endpoint(n).get("response");
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(M.valueToTree(s));
    });
  }

  /** Asserts the declared success status and that the body validates against the contract's response schema. */
  public static Resp check(String name, Resp r) {
    int status = ((Number) endpoint(name).get("status")).intValue();
    assertThat(r.status()).as("%s status (body: %s)", name, r.body()).isEqualTo(status);
    try {
      var errors = schema(name).validate(M.readTree(r.body()));
      assertThat(errors).as("%s response must match the contract; body: %s", name, r.body()).isEmpty();
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError(name + " returned invalid JSON: " + r.body(), e);
    }
    return r;
  }
}
