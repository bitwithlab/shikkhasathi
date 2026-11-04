package com.shikkhasathi.config.db;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bootstrap DB settings — env only (CLAUDE.md §0B). Direct URLs are used for Flyway (never the pooler). */
@ConfigurationProperties(prefix = "ss.db")
public record DatabaseProperties(String coreUrl, String coreDirectUrl, String contentUrl, String contentDirectUrl, boolean pooler, int poolMax) {
  public DatabaseProperties {
    if (poolMax <= 0) poolMax = 10;
  }
}
