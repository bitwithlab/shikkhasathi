package com.shikkhasathi.support;

import java.sql.DriverManager;

/** Creates the two test databases once (Flyway migrates them on context start). */
final class TestDatabases {
  private static boolean done;

  static synchronized void ensure() {
    if (done) return;
    String host = System.getenv().getOrDefault("TEST_PG_HOST", "localhost");
    try (var c = DriverManager.getConnection("jdbc:postgresql://" + host + ":5432/postgres", "postgres", System.getenv().getOrDefault("TEST_PG_PASSWORD", "postgres"))) {
      // TEST_DB_PREFIX lets several checkouts run their suites at the same time (default ss_test).
      String prefix = System.getenv().getOrDefault("TEST_DB_PREFIX", "ss_test");
      if (!prefix.matches("[a-z0-9_]+")) throw new IllegalArgumentException("TEST_DB_PREFIX must match [a-z0-9_]+");
      for (String db : new String[] {prefix + "_core", prefix + "_content"}) {
        var rs = c.createStatement().executeQuery("select 1 from pg_database where datname = '" + db + "'");
        if (!rs.next()) c.createStatement().execute("create database " + db);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Postgres for tests not reachable (start it: pg_ctlcluster 16 main start)", e);
    }
    done = true;
  }

  private TestDatabases() {}
}
