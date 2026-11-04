package com.shikkhasathi.marketplace.service;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The website's built {@code index.html}, fetched from {@code ss.website-index-url} (env {@code WEBSITE_INDEX_URL})
 * and cached for a minute. Empty when the URL is unset or the website is unreachable — the SEO routes then answer
 * 404 so the reverse proxy serves the website directly.
 */
@Component
public class WebsiteShell implements ResettableFake {
  static final long TTL_MS = 60_000;
  private static final Logger log = LoggerFactory.getLogger(WebsiteShell.class);

  private final AppProperties props;
  private final HttpTransport defaultHttp;
  private volatile String cached;
  private volatile long cachedAt;
  private volatile String urlOverride;
  private volatile HttpTransport httpOverride;

  public WebsiteShell(AppProperties props, HttpTransport http) {
    this.props = props;
    this.defaultHttp = http;
  }

  /** Test hook: fetch the shell from {@code url} through {@code http}. */
  public void override(String url, HttpTransport http) {
    this.urlOverride = url;
    this.httpOverride = http;
    this.cached = null;
  }

  @Override
  public void reset() {
    urlOverride = null;
    httpOverride = null;
    cached = null;
  }

  public Optional<String> get() {
    String url = urlOverride != null ? urlOverride : props.websiteIndexUrl();
    if (url == null || url.isBlank()) return Optional.empty();
    String c = cached;
    if (c != null && System.currentTimeMillis() - cachedAt < TTL_MS) return Optional.of(c);
    HttpTransport http = httpOverride != null ? httpOverride : defaultHttp;
    try {
      var res = http.send(HttpTransport.Request.get(url).timeout(Duration.ofSeconds(3)));
      if (!res.ok()) {
        log.warn("website shell fetch failed: HTTP {}", res.status());
        return Optional.empty();
      }
      cached = res.text();
      cachedAt = System.currentTimeMillis();
      return Optional.of(cached);
    } catch (RuntimeException e) {
      log.warn("website shell unreachable: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
