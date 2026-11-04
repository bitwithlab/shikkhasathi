package com.shikkhasathi.teacher.repository;

import com.shikkhasathi.common.Ids;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE-database queries of Teacher Studio outside {@code seller_profiles}: roles, university-email codes, user contact lookups. */
@Repository
public class SellerCoreQueries {
  private final NamedParameterJdbcTemplate db;

  public SellerCoreQueries(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record Otp(UUID id, String codeHash, Instant expiresAt, int attempts) {}

  public record Contact(String name, String email, String phone) {}

  /** roles ∪ {seller} */
  public void addSellerRole(UUID userId) {
    db.update("update users set roles = (select array_agg(distinct r) from unnest(roles || ARRAY['seller']::text[]) r), updated_at = now() where id = :id", Map.of("id", userId));
  }

  public void insertOtp(String target, String codeHash, String purpose, Instant expiresAt) {
    db.update("insert into auth_otps (id, target, code_hash, purpose, expires_at) values (:id, :t, :h, :p, :e)",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("t", target).addValue("h", codeHash).addValue("p", purpose).addValue("e", expiresAt.atOffset(ZoneOffset.UTC)));
  }

  public Optional<Otp> latestOpenOtp(String target, String purpose) {
    return db.query("select id, code_hash, expires_at, attempts from auth_otps where target = :t and purpose = :p and consumed_at is null order by created_at desc limit 1",
        Map.of("t", target, "p", purpose),
        (rs, i) -> new Otp(rs.getObject("id", UUID.class), rs.getString("code_hash"), rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts"))).stream().findFirst();
  }

  public void setOtpAttempts(UUID id, int attempts) {
    db.update("update auth_otps set attempts = :a, updated_at = now() where id = :id", Map.of("a", attempts, "id", id));
  }

  public void consumeOtp(UUID id) {
    db.update("update auth_otps set consumed_at = now(), updated_at = now() where id = :id", Map.of("id", id));
  }

  public Map<UUID, Contact> contacts(Collection<UUID> userIds) {
    Map<UUID, Contact> out = new HashMap<>();
    if (userIds.isEmpty()) return out;
    db.query("select id, name, email, phone from users where id in (:ids)", Map.of("ids", userIds), rs -> {
      out.put(rs.getObject("id", UUID.class), new Contact(rs.getString("name"), rs.getString("email"), rs.getString("phone")));
    });
    return out;
  }

  /** user ids of profiles with this status, newest update first, keyset after (updatedAt, userId) */
  public List<UUID> profileIdsByStatus(String status, Instant afterTs, UUID afterId, int limit) {
    var ps = new MapSqlParameterSource().addValue("s", status).addValue("lim", limit);
    String cur = "";
    if (afterTs != null) {
      cur = " and (updated_at < :t or (updated_at = :t and user_id < :u))";
      ps.addValue("t", Timestamp.from(afterTs)).addValue("u", afterId);
    }
    return db.queryForList("select user_id from seller_profiles where verification_status = :s" + cur + " order by updated_at desc, user_id desc limit :lim", ps, UUID.class);
  }
}
