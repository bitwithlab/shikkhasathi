package com.shikkhasathi.config.seed;

import com.shikkhasathi.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Demo data (3+ universities, subjects, 13 Bangla courses with lessons, teachers, students, mock tests, flashcards,
 * reviews, a demo exam plan …) from {@code db/seed/{core,content}.sql}. Every timestamp is shifted by
 * {@code now − seeded_at}, so relative dates (an exam in 6 days, 30-day-old enrollments) stay correct.
 *
 * <p>Dev: runs automatically on empty databases ({@code ss.seed-on-empty=true}, profile {@code dev}).
 * Tests: call {@link #seed()} after truncating. Never runs in production unless {@code ALLOW_PROD_SEED=true}.
 */
@Component
public class DevSeeder {
  private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);
  private static final Pattern SEEDED_AT = Pattern.compile("-- seeded_at: (\\S+)");
  public static final String DEMO_PASSWORD = "shikkhasathi123";

  private final DataSource core;
  private final DataSource content;
  private final NamedParameterJdbcTemplate coreJdbc;
  private final NamedParameterJdbcTemplate contentJdbc;
  private final AppProperties props;
  private final Environment env;

  public DevSeeder(@Qualifier("coreDataSource") DataSource core, @Qualifier("contentDataSource") DataSource content, @Qualifier("coreJdbc") NamedParameterJdbcTemplate coreJdbc, @Qualifier("contentJdbc") NamedParameterJdbcTemplate contentJdbc, AppProperties props, Environment env) {
    this.core = core;
    this.content = content;
    this.coreJdbc = coreJdbc;
    this.contentJdbc = contentJdbc;
    this.props = props;
    this.env = env;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedIfEmpty() {
    if (!env.getProperty("ss.seed-on-empty", Boolean.class, false)) return;
    if (props.isProd()) {
      log.warn("ss.seed-on-empty ignored in production");
      return;
    }
    Integer users = coreJdbc.queryForObject("select count(*) from users", Map.of(), Integer.class);
    if (users != null && users > 0) return;
    seed();
    log.info("seeded demo data (password: {})", DEMO_PASSWORD);
  }

  /** Load the demo dataset into both databases (expects empty tables). */
  public void seed() {
    if (props.isProd() && !"true".equals(env.getProperty("ALLOW_PROD_SEED"))) throw new IllegalStateException("Refusing to seed production");
    load("core", core, coreJdbc);
    load("content", content, contentJdbc);
  }

  private void load(String name, DataSource ds, NamedParameterJdbcTemplate jdbc) {
    var res = new ClassPathResource("db/seed/" + name + ".sql");
    Instant seededAt;
    try {
      String head = new String(res.getInputStream().readNBytes(400), StandardCharsets.UTF_8);
      Matcher m = SEEDED_AT.matcher(head);
      seededAt = m.find() ? Instant.parse(m.group(1)) : Instant.now();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    var pop = new ResourceDatabasePopulator(res);
    pop.setSqlScriptEncoding("UTF-8");
    pop.execute(ds);
    long shift = Duration.between(seededAt, Instant.now()).getSeconds();
    List<Map<String, Object>> cols = jdbc.queryForList(
        "select table_name, column_name from information_schema.columns where table_schema = 'public' and data_type = 'timestamp with time zone' and table_name <> 'flyway_schema_history'",
        Map.of());
    for (var c : cols) {
      jdbc.update("update \"" + c.get("table_name") + "\" set \"" + c.get("column_name") + "\" = \"" + c.get("column_name") + "\" + make_interval(secs => :s) where \"" + c.get("column_name") + "\" is not null", Map.of("s", shift));
    }
  }
}
