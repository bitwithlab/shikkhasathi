package com.shikkhasathi.marketplace.controller;

import com.shikkhasathi.marketplace.service.SeoService;
import com.shikkhasathi.marketplace.service.WebsiteShell;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public SEO routes outside {@code /api} (the reverse proxy forwards them here): the live {@code /sitemap.xml} and
 * {@code /c|u|s|t/<slug>} — the website shell with the server-rendered head. Unknown slugs get the plain shell;
 * without a reachable shell the answer is 404 so the proxy falls back to the website.
 */
@RestController
public class SeoController {
  private static final MediaType HTML = MediaType.parseMediaType("text/html; charset=utf-8");
  private final SeoService seo;
  private final WebsiteShell shell;

  public SeoController(SeoService seo, WebsiteShell shell) {
    this.seo = seo;
    this.shell = shell;
  }

  @GetMapping(value = "/sitemap.xml")
  public ResponseEntity<String> sitemap() {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600").contentType(MediaType.parseMediaType("application/xml; charset=utf-8")).body(seo.sitemapXml());
  }

  @GetMapping({"/{kind:c|u|s|t}/{slug:[a-z0-9-]{1,120}}", "/{kind:c|u|s|t}/{slug:[a-z0-9-]{1,120}}/"})
  public ResponseEntity<String> page(HttpServletRequest req) {
    var html = shell.get();
    if (html.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).header(HttpHeaders.CACHE_CONTROL, "no-cache").contentType(HTML).body("");
    var head = seo.dynamicHead(req.getRequestURI());
    if (head != null) return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=60").contentType(HTML).body(seo.renderHead(html.get(), head));
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache").contentType(HTML).body(html.get());
  }
}
