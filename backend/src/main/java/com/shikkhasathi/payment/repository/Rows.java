package com.shikkhasathi.payment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** JDBC helpers shared by the payment and admin repositories. */
public final class Rows {
  private Rows() {}

  public static Instant ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  public static UUID uuid(ResultSet rs, String col) throws SQLException {
    return rs.getObject(col, UUID.class);
  }

  /** Integer column that may be null. */
  public static Integer intOrNull(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  /** Instant → timestamptz parameter (null-safe). */
  public static OffsetDateTime t(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  /** Distinct, non-null ids for an {@code in (:ids)} parameter. */
  public static <T> List<T> uniq(Collection<T> ids) {
    return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
  }
}
