package com.shikkhasathi.health.service;

import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.integration.service.IntegrationService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Liveness ({@code /health}) and Admin → System health: both databases, Redis, queues, integration modes. */
@Service
public class HealthService {
  public record Health(String status, String version, String time) {}

  /** {@code migration} = latest successful Flyway version ("V1 initial schema"); {@code rlsOk} = every public table has RLS on. */
  public record DbHealth(String name, boolean connected, Long latencyMs, String migration, Boolean rlsOk) {}

  public record RedisHealth(boolean connected, Long latencyMs) {}

  public record IntegrationMode(String provider, String mode) {}

  public record SystemHealth(List<DbHealth> databases, RedisHealth redis, List<JobQueue.QueueStat> queues, List<IntegrationMode> integrations) {}

  private final NamedParameterJdbcTemplate core;
  private final NamedParameterJdbcTemplate content;
  private final RedisConnectionFactory redis;
  private final JobQueue jobs;
  private final IntegrationService integrations;
  private final String version;

  public HealthService(@Qualifier("coreJdbc") NamedParameterJdbcTemplate core, @Qualifier("contentJdbc") NamedParameterJdbcTemplate content, RedisConnectionFactory redis, JobQueue jobs, IntegrationService integrations, Environment env) {
    this.core = core;
    this.content = content;
    this.redis = redis;
    this.jobs = jobs;
    this.integrations = integrations;
    this.version = env.getProperty("APP_VERSION", "0.1.0");
  }

  private static boolean ping(NamedParameterJdbcTemplate db) {
    try {
      db.getJdbcTemplate().queryForObject("select 1", Integer.class);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  public Health health() {
    boolean ok = ping(core) & ping(content);
    return new Health(ok ? "ok" : "degraded", version, com.shikkhasathi.config.JsonConfig.iso(java.time.Instant.now()));
  }

  /** Public tables without row-level security (must be empty). */
  public static List<String> tablesWithoutRls(NamedParameterJdbcTemplate db) {
    return db.queryForList("""
        select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity order by 1""", Map.of(), String.class);
  }

  DbHealth db(String name, NamedParameterJdbcTemplate db) {
    long t = System.nanoTime();
    if (!ping(db)) return new DbHealth(name, false, null, null, null);
    long latency = Math.round((System.nanoTime() - t) / 1e6);
    String migration;
    try {
      migration = db.queryForList("select 'V' || version || ' ' || description from flyway_schema_history where success and version is not null order by installed_rank desc limit 1", Map.of(), String.class).stream().findFirst().orElse(null);
    } catch (RuntimeException e) {
      migration = null;
    }
    Boolean rls;
    try {
      rls = tablesWithoutRls(db).isEmpty();
    } catch (RuntimeException e) {
      rls = null;
    }
    return new DbHealth(name, true, latency, migration, rls);
  }

  public SystemHealth system() {
    long t = System.nanoTime();
    boolean redisOk;
    try (var c = redis.getConnection()) {
      redisOk = c.ping() != null;
    } catch (RuntimeException e) {
      redisOk = false;
    }
    Long redisLatency = redisOk ? Math.round((System.nanoTime() - t) / 1e6) : null;
    List<JobQueue.QueueStat> queues;
    try {
      queues = jobs.stats();
    } catch (RuntimeException e) {
      queues = List.of();
    }
    return new SystemHealth(
        List.of(db("core", core), db("content", content)),
        new RedisHealth(redisOk, redisLatency),
        queues,
        integrations.catalog().all().stream().map(d -> new IntegrationMode(d.provider(), integrations.mode(d.provider()))).toList());
  }
}
