package com.shikkhasathi.common.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal outbound HTTP used by every external adapter (Brevo, FCM, SMS, Bunny, Meilisearch, payment gateways,
 * Supabase Storage). The production bean is {@link JdkHttpTransport}; tests pass a lambda that records requests
 * and returns canned responses, so adapters are tested without network access.
 */
@FunctionalInterface
public interface HttpTransport {
  /** Send a request; network failures surface as {@link HttpException}. Non-2xx responses are returned, not thrown. */
  Response send(Request request);

  /** An outbound request. Header names are kept as given; {@code body} may be null. */
  record Request(String method, String url, Map<String, String> headers, byte[] body, Duration timeout) {
    public static Request get(String url) {
      return new Request("GET", url, new LinkedHashMap<>(), null, Duration.ofSeconds(15));
    }

    public static Request post(String url, byte[] body) {
      return new Request("POST", url, new LinkedHashMap<>(), body, Duration.ofSeconds(15));
    }

    public static Request post(String url, String body) {
      return post(url, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public static Request of(String method, String url, byte[] body) {
      return new Request(method, url, new LinkedHashMap<>(), body, Duration.ofSeconds(15));
    }

    /** Copy with one more header. */
    public Request header(String name, String value) {
      var h = new LinkedHashMap<>(headers);
      h.put(name, value);
      return new Request(method, url, h, body, timeout);
    }

    /** Copy with a different timeout. */
    public Request timeout(Duration d) {
      return new Request(method, url, headers, body, d);
    }

    /** Body as UTF-8 text (tests). */
    public String bodyText() {
      return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    /** Header lookup ignoring case. */
    public String headerValue(String name) {
      return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null);
    }
  }

  /** A response: status + raw body. */
  record Response(int status, byte[] body) {
    public static Response of(int status, String body) {
      return new Response(status, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
    }

    public boolean ok() {
      return status >= 200 && status < 300;
    }

    public String text() {
      return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
  }

  /** Transport-level failure (DNS, connect, timeout). */
  class HttpException extends RuntimeException {
    public HttpException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** application/x-www-form-urlencoded / query-string encoding, keeping the map order. */
  static String form(Map<String, String> params) {
    return params.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  /** {@code base?form(params)}. */
  static String withQuery(String base, Map<String, String> params) {
    String q = form(params);
    return q.isEmpty() ? base : base + (base.contains("?") ? "&" : "?") + q;
  }
}
