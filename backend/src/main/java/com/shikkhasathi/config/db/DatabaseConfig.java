package com.shikkhasathi.config.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Two Supabase Postgres databases (CLAUDE.md §0B): CORE (people, money) and CONTENT (catalog, material).
 * Each has its own pool, Hibernate unit, transaction manager, JDBC template and Flyway history.
 * No foreign keys across databases — services join with batched lookups.
 */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

  private static final String BASE = "com.shikkhasathi";

  private static HikariDataSource pool(String name, String url, DatabaseProperties p) {
    PgUrl u = PgUrl.parse(url, p.pooler());
    HikariConfig c = new HikariConfig();
    c.setPoolName(name);
    c.setJdbcUrl(u.jdbcUrl());
    if (u.username() != null) c.setUsername(u.username());
    if (u.password() != null) c.setPassword(u.password());
    c.setMaximumPoolSize(p.poolMax());
    c.setMinimumIdle(1);
    c.setConnectionTimeout(10_000);
    return new HikariDataSource(c);
  }

  static PersistenceManagedTypes managedTypes(Class<? extends java.lang.annotation.Annotation> marker) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    scanner.addIncludeFilter(new AnnotationTypeFilter(marker));
    List<String> names =
        scanner.findCandidateComponents(BASE).stream()
            .map(bd -> bd.getBeanClassName())
            .filter(n -> {
              try {
                Class<?> k = Class.forName(n);
                return k.isAnnotationPresent(Entity.class) && k.isAnnotationPresent(marker);
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
              }
            })
            .toList();
    return PersistenceManagedTypes.of(names, List.of());
  }

  private static LocalContainerEntityManagerFactoryBean emf(String unit, DataSource ds, PersistenceManagedTypes types) {
    var f = new LocalContainerEntityManagerFactoryBean();
    f.setPersistenceUnitName(unit);
    f.setDataSource(ds);
    f.setManagedTypes(types);
    f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    f.setJpaPropertyMap(Map.of(
        "hibernate.hbm2ddl.auto", "validate",
        "hibernate.jdbc.time_zone", "UTC",
        "hibernate.type.preferred_instant_jdbc_type", "TIMESTAMP_UTC",
        "hibernate.jdbc.batch_size", "50",
        "hibernate.order_inserts", "true",
        "hibernate.open-in-view", "false"));
    return f;
  }

  // ---------------------------------------------------------------- CORE

  @Bean
  @Primary
  public DataSource coreDataSource(DatabaseProperties p) {
    return pool("core", p.coreUrl(), p);
  }

  @Bean
  @Primary
  @DependsOn("flywayMigrations")
  public LocalContainerEntityManagerFactoryBean coreEntityManagerFactory(@Qualifier("coreDataSource") DataSource ds) {
    return emf("core", ds, managedTypes(CoreEntity.class));
  }

  @Bean
  @Primary
  public PlatformTransactionManager coreTransactionManager(@Qualifier("coreEntityManagerFactory") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }

  @Bean
  @Primary
  public NamedParameterJdbcTemplate coreJdbc(@Qualifier("coreDataSource") DataSource ds) {
    return new NamedParameterJdbcTemplate(ds);
  }

  // ---------------------------------------------------------------- CONTENT

  @Bean
  public DataSource contentDataSource(DatabaseProperties p) {
    return pool("content", p.contentUrl(), p);
  }

  @Bean
  @DependsOn("flywayMigrations")
  public LocalContainerEntityManagerFactoryBean contentEntityManagerFactory(@Qualifier("contentDataSource") DataSource ds) {
    return emf("content", ds, managedTypes(ContentEntity.class));
  }

  @Bean
  public PlatformTransactionManager contentTransactionManager(@Qualifier("contentEntityManagerFactory") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }

  @Bean
  public NamedParameterJdbcTemplate contentJdbc(@Qualifier("contentDataSource") DataSource ds) {
    return new NamedParameterJdbcTemplate(ds);
  }

  // ---------------------------------------------------------------- repositories

  @Configuration
  @EnableJpaRepositories(
      basePackages = BASE,
      entityManagerFactoryRef = "coreEntityManagerFactory",
      transactionManagerRef = "coreTransactionManager",
      includeFilters = @ComponentScan.Filter(CoreRepository.class))
  static class CoreRepos {}

  @Configuration
  @EnableJpaRepositories(
      basePackages = BASE,
      entityManagerFactoryRef = "contentEntityManagerFactory",
      transactionManagerRef = "contentTransactionManager",
      includeFilters = @ComponentScan.Filter(ContentRepository.class))
  static class ContentRepos {}

  // ---------------------------------------------------------------- Flyway (direct connection, both DBs)

  /**
   * Migrations run on startup against the DIRECT URLs (port 5432). A database that already has tables but no
   * Flyway history (created by the previous Drizzle migrator) is baselined at V1 — no data is touched.
   */
  @Bean
  public static FlywayMigrations flywayMigrations(DatabaseProperties p, org.springframework.core.env.Environment env) {
    boolean enabled = env.getProperty("ss.db.migrate-on-start", Boolean.class, true);
    var m = new FlywayMigrations(flyway("core", blank(p.coreDirectUrl()) ? p.coreUrl() : p.coreDirectUrl()), flyway("content", blank(p.contentDirectUrl()) ? p.contentUrl() : p.contentDirectUrl()));
    if (enabled) m.migrate();
    return m;
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  static Flyway flyway(String name, String url) {
    PgUrl u = PgUrl.parse(url, false);
    return Flyway.configure()
        .dataSource(u.jdbcUrl(), u.username(), u.password())
        .locations("classpath:db/migration/" + name)
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .baselineDescription("existing schema (Drizzle 0000)")
        .validateMigrationNaming(true)
        .load();
  }

  public record FlywayMigrations(Flyway core, Flyway content) {
    public void migrate() {
      core.migrate();
      content.migrate();
    }
  }
}
