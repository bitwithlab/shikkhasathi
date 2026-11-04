package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import java.sql.Array;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE users for the admin panel: search, status/roles, session revocation, per-user counts. */
@Repository
public class AdminUserRepository {
  public record UserRow(UUID id, String name, String email, String phone, List<String> roles, String status, Instant createdAt, Instant lastActiveAt) {}

  private final NamedParameterJdbcTemplate db;

  public AdminUserRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<UserRow> USER = (rs, i) -> {
    Array a = rs.getArray("roles");
    List<String> roles = a == null ? List.of() : List.of((String[]) a.getArray());
    return new UserRow(uuid(rs, "id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), roles, rs.getString("status"), ts(rs, "created_at"),
        ts(rs, "last_active_at"));
  };
  private static final String COLS = "id, name, email, phone, roles, status, created_at, last_active_at";

  public Optional<UserRow> byId(UUID id) {
    var l = db.query("select " + COLS + " from users where id = :id", Map.of("id", id), USER);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<UserRow> search(String like, String role, String status, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("lim", limit);
    var w = new ArrayList<String>(List.of("deleted_at is null"));
    if (like != null) {
      w.add("(name ilike :q or email ilike :q or phone ilike :q)");
      p.addValue("q", like);
    }
    if (role != null) {
      w.add(":role = any(roles)");
      p.addValue("role", role);
    }
    if (status != null) {
      w.add("status = :st");
      p.addValue("st", status);
    }
    if (curAt != null) {
      w.add("created_at < :ca");
      p.addValue("ca", t(curAt));
    }
    return db.query("select " + COLS + " from users where " + String.join(" and ", w) + " order by created_at desc limit :lim", p, USER);
  }

  /** Update status and/or roles (null = unchanged). */
  public UserRow update(UUID id, String status, List<String> roles) {
    var p = new MapSqlParameterSource().addValue("id", id).addValue("st", status).addValue("roles", roles == null ? null : "{" + String.join(",", roles) + "}");
    return db.query("update users set updated_at = now()" + (status != null ? ", status = :st" : "") + (roles != null ? ", roles = cast(:roles as text[])" : "")
        + " where id = :id returning " + COLS, p, USER).get(0);
  }

  /** Revoke every session family that still has an active session (sign out everywhere). */
  public void revokeActiveSessions(UUID userId) {
    db.update("update sessions set revoked_at = now(), updated_at = now() where revoked_at is null and family_id in "
        + "(select family_id from sessions where user_id = :u and revoked_at is null and rotated_at is null and expires_at > now())", Map.of("u", userId));
  }

  public Map<UUID, Long> enrollmentCounts(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, Long> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select student_id, count(*) as n from enrollments where student_id in (:ids) group by student_id", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "student_id"), rs.getLong("n"));
    });
    return out;
  }

  public Map<UUID, String> sellerStatuses(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, String> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select user_id, verification_status from seller_profiles where user_id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "user_id"), rs.getString("verification_status"));
    });
    return out;
  }

  public Map<UUID, String> names(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, String> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select id, name from users where id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "id"), rs.getString("name"));
    });
    return out;
  }
}
