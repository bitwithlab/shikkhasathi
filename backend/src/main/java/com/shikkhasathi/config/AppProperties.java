package com.shikkhasathi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap configuration — env only (CLAUDE.md §0B). Integration credentials are NOT here: they come from the
 * integration settings (Admin panel → env fallback → disabled / Fake adapter).
 *
 * @param env        development | test | production
 * @param appUrl     public site URL (links in emails, OAuth/payment callbacks)
 * @param role       api | worker | all — which parts this process runs (jobs + schedules only in worker/all)
 * @param inlineJobs run queued jobs synchronously in-process (tests)
 */
@ConfigurationProperties(prefix = "ss")
public record AppProperties(
    String env,
    String appUrl,
    String role,
    String encryptionKey,
    String jwtAccessSecret,
    String jwtRefreshSecret,
    String localStorageDir,
    boolean inlineJobs,
    String websiteIndexUrl,
    String supabaseCoreUrl,
    String supabaseCoreServiceRoleKey,
    String supabaseContentUrl,
    String supabaseContentServiceRoleKey) {

  public boolean isProd() {
    return "production".equals(env);
  }

  public boolean isTest() {
    return "test".equals(env);
  }

  public boolean runsWorker() {
    return !"api".equals(role);
  }

  public boolean jobsInline() {
    return inlineJobs || isTest();
  }
}
