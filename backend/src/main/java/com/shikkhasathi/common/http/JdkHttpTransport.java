package com.shikkhasathi.common.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** {@link HttpTransport} on the JDK HttpClient (HTTP/1.1 + HTTP/2, follows no redirects). */
@Component
public class JdkHttpTransport implements HttpTransport {
  private static final java.util.Set<String> RESTRICTED = java.util.Set.of("connection", "content-length", "host", "expect", "upgrade");
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

  @Override
  public Response send(Request r) {
    var b = HttpRequest.newBuilder(URI.create(r.url())).timeout(r.timeout() == null ? Duration.ofSeconds(15) : r.timeout());
    r.headers().forEach((k, v) -> {
      if (!RESTRICTED.contains(k.toLowerCase())) b.header(k, v);
    });
    b.method(r.method(), r.body() == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(r.body()));
    try {
      HttpResponse<byte[]> res = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Response(res.statusCode(), res.body());
    } catch (IOException e) {
      throw new HttpException(r.method() + " " + host(r.url()) + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpException("interrupted", e);
    }
  }

  /** host only — never log full URLs (they can carry keys in the query string) */
  private static String host(String url) {
    try {
      return URI.create(url).getHost();
    } catch (RuntimeException e) {
      return "?";
    }
  }
}
