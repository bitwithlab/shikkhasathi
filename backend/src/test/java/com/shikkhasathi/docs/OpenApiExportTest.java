package com.shikkhasathi.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the backend's OpenAPI document to {@code docs/api/openapi.json} (keys sorted, stable output).
 * It is committed: the website generates its API types from it ({@code pnpm gen:api-types}), the mobile
 * app reads it, and CI fails when the committed copy is stale.
 */
class OpenApiExportTest extends IntegrationTest {
  @Test
  void exportOpenApi() throws Exception {
    var body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/openapi.json")).andReturn().getResponse().getContentAsString();
    var tree = json.readTree(body);
    assertThat(tree.path("paths").size()).isGreaterThan(0);
    var pretty = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).configure(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true).build();
    Object asMap = pretty.treeToValue(tree, java.util.TreeMap.class);
    Path out = Path.of("..", "docs", "api", "openapi.json");
    Files.createDirectories(out.getParent());
    Files.writeString(out, pretty.writeValueAsString(asMap) + "\n");
  }
}
