package com.shikkhasathi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** An HTTP response in tests: status, parsed JSON body, raw body, headers. */
public record Resp(int status, String body, Map<String, String> headers) {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  public JsonNode json() {
    return body == null || body.isEmpty() ? JSON.nullNode() : JSON.readTree(body);
  }

  public JsonNode at(String pointer) {
    return json().at(pointer);
  }

  public String errorCode() {
    return json().at("/error/code").asString();
  }

  public String errorMessage() {
    return json().at("/error/message").asString();
  }

  public Resp expect(int s) {
    assertThat(status).as("HTTP status for body %s", body).isEqualTo(s);
    return this;
  }

  public String header(String name) {
    return headers.get(name.toLowerCase());
  }
}
