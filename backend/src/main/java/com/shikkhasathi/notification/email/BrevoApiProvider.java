package com.shikkhasathi.notification.email;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import com.shikkhasathi.notification.email.EmailMessage.EmailSender;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Brevo transactional API: {@code POST https://api.brevo.com/v3/smtp/email} with the {@code api-key} header. */
public class BrevoApiProvider implements EmailProvider {
  public static final String DEFAULT_BASE_URL = "https://api.brevo.com/v3";
  private final String apiKey;
  private final HttpTransport http;
  private final JsonMapper json;
  private final String baseUrl;

  public BrevoApiProvider(String apiKey, HttpTransport http, JsonMapper json) {
    this(apiKey, http, json, DEFAULT_BASE_URL);
  }

  public BrevoApiProvider(String apiKey, HttpTransport http, JsonMapper json, String baseUrl) {
    this.apiKey = apiKey;
    this.http = http;
    this.json = json;
    this.baseUrl = baseUrl;
  }

  @Override
  public String name() {
    return "brevo_api";
  }

  @Override
  public String send(EmailMessage msg, EmailSender from) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sender", Map.of("name", from.name(), "email", from.email()));
    Map<String, Object> to = new LinkedHashMap<>();
    to.put("email", msg.to());
    if (msg.toName() != null && !msg.toName().isEmpty()) to.put("name", msg.toName());
    body.put("to", List.of(to));
    if (from.replyTo() != null && !from.replyTo().isEmpty()) body.put("replyTo", Map.of("email", from.replyTo()));
    body.put("subject", msg.subject());
    body.put("htmlContent", msg.html());
    body.put("textContent", msg.text());
    if (!msg.tags().isEmpty()) body.put("tags", msg.tags());
    if (!msg.attachments().isEmpty()) body.put("attachment", msg.attachments().stream().map(a -> Map.of("name", a.name(), "content", a.contentBase64())).toList());
    var res = http.send(Request.post(baseUrl + "/smtp/email", json.writeValueAsString(body))
        .header("api-key", apiKey).header("content-type", "application/json").header("accept", "application/json").timeout(Duration.ofSeconds(15)));
    if (!res.ok()) {
      String t = res.text();
      throw new IllegalStateException("Brevo API " + res.status() + ": " + (t.length() > 200 ? t.substring(0, 200) : t));
    }
    try {
      JsonNode n = json.readTree(res.text());
      return n.hasNonNull("messageId") ? n.get("messageId").asString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** GET /account — used by the "test connection" button. Returns the account email (nullable). */
  public String ping() {
    var res = http.send(Request.get(baseUrl + "/account").header("api-key", apiKey).header("accept", "application/json").timeout(Duration.ofSeconds(10)));
    if (!res.ok()) throw new IllegalStateException("Brevo API " + res.status());
    JsonNode n = json.readTree(res.text());
    return n.hasNonNull("email") ? n.get("email").asString() : null;
  }
}
