package com.shikkhasathi.config.db;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Accepts the same env format as before: postgres://user:pass@host:port/db?params (or a jdbc: URL). */
public record PgUrl(String jdbcUrl, String username, String password) {
  public static PgUrl parse(String raw, boolean pooler) {
    if (raw.startsWith("jdbc:")) return new PgUrl(withPooler(raw, pooler), null, null);
    URI u = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
    String user = null, pass = null;
    if (u.getRawUserInfo() != null) {
      String[] p = u.getRawUserInfo().split(":", 2);
      user = URLDecoder.decode(p[0], StandardCharsets.UTF_8);
      if (p.length > 1) pass = URLDecoder.decode(p[1], StandardCharsets.UTF_8);
    }
    String q = u.getRawQuery() == null ? "" : "?" + u.getRawQuery().replace("sslmode=require", "sslmode=require");
    String jdbc = "jdbc:postgresql://" + u.getHost() + ":" + (u.getPort() > 0 ? u.getPort() : 5432) + u.getRawPath() + q;
    return new PgUrl(withPooler(jdbc, pooler), user, pass);
  }

  /** Supabase transaction pooler (port 6543) can't hold prepared statements across transactions. */
  private static String withPooler(String jdbc, boolean pooler) {
    boolean is6543 = jdbc.contains(":6543/");
    if (!pooler && !is6543) return jdbc;
    return jdbc + (jdbc.contains("?") ? "&" : "?") + "prepareThreshold=0";
  }
}
