package com.shikkhasathi.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.search.adapter.MeiliClient;
import com.shikkhasathi.search.adapter.SearchEngine;
import com.shikkhasathi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SearchAdapterTest extends IntegrationTest {
  @Autowired SearchEngine search;

  @Test
  void postgresFallbackUntilAHostIsSet() {
    assertThat(search.usePostgresFallback()).isTrue();
    var r = contract("settings.test", post("/admin/integrations/search/test", Map.of(), createUser("Admin", "admin")));
    assertThat(r.at("/ok").asBoolean()).isFalse();
    put("/admin/integrations/search", Map.of("values", Map.of("host", "http://meili:7700")), createUser("Admin2", "admin")).expect(200);
    assertThat(search.usePostgresFallback()).isFalse();
  }

  @Test
  void meiliClientConfiguresTheIndexAndSearches() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var m = new MeiliClient("http://meili:7700/", "mk", r -> {
      calls.add(r);
      if (r.url().endsWith("/search")) return HttpTransport.Response.of(200, "{\"hits\":[{\"id\":\"c1\"},{\"id\":\"c2\"}]}");
      return HttpTransport.Response.of(202, "{\"taskUid\":1}");
    }, json);
    m.configure();
    assertThat(calls.get(0).method()).isEqualTo("PATCH");
    assertThat(calls.get(0).url()).isEqualTo("http://meili:7700/indexes/courses/settings");
    assertThat(calls.get(0).headerValue("authorization")).isEqualTo("Bearer mk");
    var settings = json.readTree(calls.get(0).bodyText());
    assertThat(settings.get("synonyms").get("ds").get(0).asString()).isEqualTo("data structures");
    assertThat(settings.get("synonyms").get("ডাটা স্ট্রাকচার").get(0).asString()).isEqualTo("data structures");
    assertThat(settings.get("typoTolerance").get("minWordSizeForTypos").get("oneTypo").asInt()).isEqualTo(4);
    m.upsert(List.of(Map.of("id", "c1", "title", "DS")));
    assertThat(calls.get(1).url()).isEqualTo("http://meili:7700/indexes/courses/documents?primaryKey=id");
    m.upsert(List.of());
    assertThat(calls).hasSize(2);
    assertThat(m.searchIds("ds", List.of(MeiliClient.eq("examType", "final")), 300)).containsExactly("c1", "c2");
    assertThat(json.readTree(calls.get(2).bodyText()).get("filter").get(0).asString()).isEqualTo("examType = \"final\"");
    m.remove("c1");
    assertThat(calls.get(3).method()).isEqualTo("DELETE");
  }
}
