package com.shikkhasathi.auth.repository;

import com.shikkhasathi.common.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * CORE queries of the auth module: one-time codes ({@code auth_otps}), refresh sessions ({@code sessions}) and the
 * two small writes to {@code users} / {@code student_profiles} the previous auth module did. Plain JDBC because the
 * rotation/consumption steps are conditional single-statement updates (compare-and-set), exactly as before.
 */
@Repository
public class AuthRepository {
  private final NamedParameterJdbcTemplate db;

  public AuthRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record OtpRow(UUID id, String codeHash, Instant expiresAt, int attempts, Instant consumedAt, Instant createdAt) {}

  public record SessionRow(
      UUID id, UUID userId, UUID familyId, String deviceId, String deviceName, String platform,
      Instant expiresAt, Instant lastSeenAt, Instant rotatedAt, Instant revokedAt, Instant createdAt) {}

  public record NewSession(UUID userId, UUID familyId, String refreshTokenHash, String deviceId, String deviceName, String platform, String ip, String userAgent, Instant expiresAt) {}

  private static Instant ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static OtpRow otp(ResultSet rs, int n) throws SQLException {
    return new OtpRow(rs.getObject("id", UUID.class), rs.getString("code_hash"), ts(rs, "expires_at"), rs.getInt("attempts"), ts(rs, "consumed_at"), ts(rs, "created_at"));
  }

  private static SessionRow session(ResultSet rs, int n) throws SQLException {
    return new SessionRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("family_id", UUID.class),
        rs.getString("device_id"), rs.getString("device_name"), rs.getString("platform"), ts(rs, "expires_at"), ts(rs, "last_seen_at"),
        ts(rs, "rotated_at"), ts(rs, "revoked_at"), ts(rs, "created_at"));
  }

  // ---------------------------------------------------------------- one-time codes

  public Optional<OtpRow> latestOtp(String target, String purpose) {
    return db.query("select * from auth_otps where target = :t and purpose = :p order by created_at desc limit 1",
        Map.of("t", target, "p", purpose), AuthRepository::otp).stream().findFirst();
  }

  public void createOtp(String target, String codeHash, String purpose, Instant expiresAt) {
    db.update("insert into auth_otps (id, target, code_hash, purpose, expires_at) values (:id, :t, :h, :p, :e)",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("t", target).addValue("h", codeHash).addValue("p", purpose).addValue("e", ts(expiresAt)));
  }

  public void bumpOtpAttempts(UUID id) {
    db.update("update auth_otps set attempts = attempts + 1, updated_at = now() where id = :id", Map.of("id", id));
  }

  /** True only for the caller that consumed it first. */
  public boolean consumeOtp(UUID id) {
    return db.update("update auth_otps set consumed_at = now(), updated_at = now() where id = :id and consumed_at is null", Map.of("id", id)) > 0;
  }

  // ---------------------------------------------------------------- sessions

  public UUID createSession(NewSession s) {
    UUID id = Ids.uuidv7();
    db.update("""
        insert into sessions (id, user_id, family_id, refresh_token_hash, device_id, device_name, platform, ip, user_agent, expires_at)
        values (:id, :u, :f, :h, :did, :dn, :p, :ip, :ua, :e)""",
        new MapSqlParameterSource().addValue("id", id).addValue("u", s.userId()).addValue("f", s.familyId()).addValue("h", s.refreshTokenHash())
            .addValue("did", s.deviceId()).addValue("dn", s.deviceName()).addValue("p", s.platform()).addValue("ip", s.ip())
            .addValue("ua", s.userAgent()).addValue("e", ts(s.expiresAt())));
    return id;
  }

  public Optional<SessionRow> sessionByHash(String hash) {
    return db.query("select * from sessions where refresh_token_hash = :h", Map.of("h", hash), AuthRepository::session).stream().findFirst();
  }

  /** Compare-and-set: true only for the first rotation of this session. */
  public boolean markRotated(UUID id) {
    return db.update("update sessions set rotated_at = now(), updated_at = now() where id = :id and rotated_at is null", Map.of("id", id)) > 0;
  }

  public void revokeFamily(UUID familyId) {
    db.update("update sessions set revoked_at = now(), updated_at = now() where family_id = :f and revoked_at is null", Map.of("f", familyId));
  }

  /** Revokes one of the user's sessions; returns its family (empty when it isn't the user's). */
  public Optional<UUID> revokeSession(UUID id, UUID userId) {
    return db.query("update sessions set revoked_at = now(), updated_at = now() where id = :id and user_id = :u returning family_id",
        Map.of("id", id, "u", userId), (rs, n) -> rs.getObject("family_id", UUID.class)).stream().findFirst();
  }

  public List<SessionRow> activeSessions(UUID userId) {
    return db.query("""
        select * from sessions
        where user_id = :u and revoked_at is null and rotated_at is null and expires_at > now()
        order by last_seen_at desc""", Map.of("u", userId), AuthRepository::session);
  }

  // ---------------------------------------------------------------- users / profiles

  private static final java.util.Set<String> USER_COLUMNS = java.util.Set.of(
      "name", "email", "email_verified_at", "phone_verified_at", "password_hash", "google_sub", "avatar_url", "locale", "theme", "status", "deletion_requested_at");

  /**
   * Partial update of {@code users} (only the given columns, like the previous API), so concurrent writes to other
   * columns by other modules are never overwritten with stale values. Keys are column names from a fixed allow-list.
   */
  public void updateUser(UUID id, Map<String, Object> set) {
    if (set.isEmpty()) return;
    var p = new MapSqlParameterSource().addValue("id", id);
    var sql = new StringBuilder("update users set updated_at = now()");
    for (var e : set.entrySet()) {
      if (!USER_COLUMNS.contains(e.getKey())) throw new IllegalArgumentException("column " + e.getKey());
      sql.append(", ").append(e.getKey()).append(" = :").append(e.getKey());
      p.addValue(e.getKey(), e.getValue() instanceof Instant i ? ts(i) : e.getValue());
    }
    db.update(sql.append(" where id = :id").toString(), p);
  }

  public void touch(UUID userId) {
    db.update("update users set last_active_at = now() where id = :id", Map.of("id", userId));
  }

  /** PATCH /me lowDataMode lives on the student profile (created on first use). */
  public void upsertLowDataMode(UUID userId, boolean lowDataMode) {
    db.update("""
        insert into student_profiles (user_id, low_data_mode) values (:u, :v)
        on conflict (user_id) do update set low_data_mode = excluded.low_data_mode, updated_at = now()""",
        Map.of("u", userId, "v", lowDataMode));
  }
}
