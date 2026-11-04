package com.shikkhasathi.notification.sms;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Generic HTTP SMS gateway (BulkSMSBD-style query API: api_key, number, message, senderid). */
public class HttpSmsProvider implements SmsProvider {
  /** Gateway settings; {@code senderId} nullable. */
  public record Config(String apiUrl, String apiKey, String senderId) {}

  private final Config cfg;
  private final HttpTransport http;

  public HttpSmsProvider(Config cfg, HttpTransport http) {
    this.cfg = cfg;
    this.http = http;
  }

  @Override
  public String name() {
    return "http";
  }

  @Override
  public void send(String to, String message) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("api_key", cfg.apiKey());
    q.put("number", to.replaceFirst("^\\+", ""));
    q.put("message", message);
    if (cfg.senderId() != null && !cfg.senderId().isEmpty()) q.put("senderid", cfg.senderId());
    var res = http.send(Request.get(HttpTransport.withQuery(cfg.apiUrl(), q)).timeout(Duration.ofSeconds(10)));
    if (!res.ok()) throw new IllegalStateException("SMS gateway " + res.status());
  }
}
