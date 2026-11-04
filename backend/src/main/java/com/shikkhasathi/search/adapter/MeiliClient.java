package com.shikkhasathi.search.adapter;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Meilisearch HTTP client for the {@code courses} index: one document per course with the target fields as
 * filterable arrays. Text relevance only — the search service re-ranks with the spec §6.5 score. Synonyms
 * (DS = Data Structures, DBMS = …) are configured on the index (ADR-017).
 */
public class MeiliClient {
  public static final String INDEX = "courses";

  /** Index settings uploaded by {@link #configure()} — identical to the previous API. */
  public static final Map<String, Object> INDEX_SETTINGS;

  static {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("searchableAttributes", List.of("title", "courseCodes", "subjectNames", "institutionNames", "sellerName", "shortDescription"));
    s.put("filterableAttributes", List.of("institutionIds", "departmentIds", "semesters", "subjectIds", "courseCodesNorm", "examType", "courseType", "language", "price", "sellerId"));
    Map<String, List<String>> syn = new LinkedHashMap<>();
    syn.put("ds", List.of("data structures"));
    syn.put("data structures", List.of("ds"));
    syn.put("dbms", List.of("database management systems"));
    syn.put("oop", List.of("object oriented programming"));
    syn.put("dld", List.of("digital logic design"));
    syn.put("ডাটা স্ট্রাকচার", List.of("data structures"));
    syn.put("অ্যালগরিদম", List.of("algorithms"));
    s.put("synonyms", syn);
    s.put("typoTolerance", Map.of("enabled", true, "minWordSizeForTypos", Map.of("oneTypo", 4, "twoTypos", 8)));
    INDEX_SETTINGS = java.util.Collections.unmodifiableMap(s);
  }

  private final String host;
  private final String key;
  private final HttpTransport http;
  private final JsonMapper json;

  /** @param key master/API key, nullable */
  public MeiliClient(String host, String key, HttpTransport http, JsonMapper json) {
    this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    this.key = key;
    this.http = http;
    this.json = json;
  }

  private JsonNode req(String method, String path, Object body) {
    var r = Request.of(method, host + path, body == null ? null : json.writeValueAsBytes(body)).header("content-type", "application/json").timeout(Duration.ofSeconds(5));
    if (key != null && !key.isEmpty()) r = r.header("authorization", "Bearer " + key);
    var res = http.send(r);
    if (!res.ok()) throw new IllegalStateException("meili " + res.status());
    String t = res.text();
    return t.isEmpty() ? json.createObjectNode() : json.readTree(t);
  }

  /** GET /health */
  public JsonNode health() {
    return req("GET", "/health", null);
  }

  /** PATCH the index settings (searchable/filterable attributes, synonyms, typo tolerance). */
  public void configure() {
    req("PATCH", "/indexes/" + INDEX + "/settings", INDEX_SETTINGS);
  }

  /** Add or replace documents (primary key {@code id}). */
  public void upsert(List<Map<String, Object>> docs) {
    if (!docs.isEmpty()) req("POST", "/indexes/" + INDEX + "/documents?primaryKey=id", docs);
  }

  public void remove(String id) {
    req("DELETE", "/indexes/" + INDEX + "/documents/" + id, null);
  }

  /** Matching course ids in relevance order; {@code filter} are Meilisearch filter expressions (ANDed), may be empty. */
  public List<String> searchIds(String q, List<String> filter, int limit) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("q", q);
    body.put("limit", limit);
    body.put("attributesToRetrieve", List.of("id"));
    if (filter != null && !filter.isEmpty()) body.put("filter", filter);
    List<String> ids = new ArrayList<>();
    for (JsonNode h : req("POST", "/indexes/" + INDEX + "/search", body).path("hits")) ids.add(h.path("id").asString());
    return ids;
  }

  /** {@code field = "value"} with quotes escaped (for {@link #searchIds} filters). */
  public static String eq(String field, String value) {
    return field + " = \"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
