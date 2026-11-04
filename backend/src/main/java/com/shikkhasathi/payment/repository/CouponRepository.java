package com.shikkhasathi.payment.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import com.shikkhasathi.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE coupons + coupon redemptions (the entity lives in {@code marketplace.entity}). */
@Repository
public class CouponRepository {
  public record CouponRow(UUID id, String code, String scope, UUID sellerId, UUID courseId, String type, int value, Integer maxUses, int usedCount, int perUserLimit,
                          long minAmount, Instant startsAt, Instant expiresAt, int active, Instant createdAt) {}

  /** Column values for insert/update (all present). */
  public record CouponValues(String code, String scope, UUID courseId, String type, int value, Integer maxUses, int perUserLimit, long minAmount, Instant startsAt,
                             Instant expiresAt, int active) {}

  private final NamedParameterJdbcTemplate db;

  public CouponRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<CouponRow> COUPON = (rs, i) -> new CouponRow(uuid(rs, "id"), rs.getString("code"), rs.getString("scope"), uuid(rs, "seller_id"), uuid(rs, "course_id"),
      rs.getString("type"), rs.getInt("value"), Rows.intOrNull(rs, "max_uses"), rs.getInt("used_count"), rs.getInt("per_user_limit"), rs.getLong("min_amount"),
      ts(rs, "starts_at"), ts(rs, "expires_at"), rs.getInt("active"), ts(rs, "created_at"));

  public Optional<CouponRow> byCode(String code) {
    var l = db.query("select * from coupons where code = :c", Map.of("c", code), COUPON);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public Optional<CouponRow> byId(UUID id) {
    var l = db.query("select * from coupons where id = :id", Map.of("id", id), COUPON);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<CouponRow> bySeller(UUID sellerId) {
    return db.query("select * from coupons where seller_id = :s order by created_at desc", Map.of("s", sellerId), COUPON);
  }

  public long redemptions(UUID couponId, UUID userId) {
    Long n = db.queryForObject("select count(*) from coupon_redemptions where coupon_id = :c and user_id = :u", Map.of("c", couponId, "u", userId), Long.class);
    return n == null ? 0 : n;
  }

  /** Counts a redemption once per (coupon, order). */
  public void redeem(UUID couponId, UUID userId, UUID orderId) {
    int n = db.update("insert into coupon_redemptions (id, coupon_id, user_id, order_id) values (:id, :c, :u, :o) on conflict do nothing",
        Map.of("id", Ids.uuidv7(), "c", couponId, "u", userId, "o", orderId));
    if (n == 1) db.update("update coupons set used_count = used_count + 1, updated_at = now() where id = :c", Map.of("c", couponId));
  }

  private static MapSqlParameterSource params(CouponValues v) {
    return new MapSqlParameterSource().addValue("code", v.code()).addValue("scope", v.scope()).addValue("course", v.courseId()).addValue("type", v.type())
        .addValue("value", v.value()).addValue("max", v.maxUses()).addValue("per", v.perUserLimit()).addValue("min", v.minAmount()).addValue("sa", t(v.startsAt()))
        .addValue("ea", t(v.expiresAt())).addValue("active", v.active());
  }

  /** Empty when the code already exists. */
  public Optional<CouponRow> insert(UUID sellerId, CouponValues v) {
    var p = params(v).addValue("id", Ids.uuidv7()).addValue("seller", sellerId);
    var l = db.query("insert into coupons (id, code, scope, seller_id, course_id, type, value, max_uses, per_user_limit, min_amount, starts_at, expires_at, active, created_by) "
        + "values (:id, :code, :scope, :seller, :course, :type, :value, :max, :per, :min, :sa, :ea, :active, :seller) on conflict do nothing returning *", p, COUPON);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public CouponRow update(UUID id, CouponValues v) {
    return db.query("update coupons set code = :code, scope = :scope, course_id = :course, type = :type, value = :value, max_uses = :max, per_user_limit = :per, "
        + "min_amount = :min, starts_at = :sa, expires_at = :ea, active = :active, updated_at = now() where id = :id returning *", params(v).addValue("id", id), COUPON).get(0);
  }
}
