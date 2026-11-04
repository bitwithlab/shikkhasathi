package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import com.shikkhasathi.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE payouts + the seller profile fields payouts need. */
@Repository
public class PayoutRepository {
  public record PayoutRow(UUID id, UUID sellerId, long amount, String method, String accountMasked, String status, String reference, String note, Instant createdAt,
                          Instant processedAt) {}

  public record SellerPayoutInfo(UUID userId, String displayName, String slug, String payoutMethod, String payoutAccountMasked) {}

  private final NamedParameterJdbcTemplate db;

  public PayoutRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<PayoutRow> PAYOUT = (rs, i) -> new PayoutRow(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getLong("amount"), rs.getString("method"),
      rs.getString("account_masked"), rs.getString("status"), rs.getString("reference"), rs.getString("note"), ts(rs, "created_at"), ts(rs, "processed_at"));
  static final RowMapper<SellerPayoutInfo> SELLER = (rs, i) -> new SellerPayoutInfo(uuid(rs, "user_id"), rs.getString("display_name"), rs.getString("slug"),
      rs.getString("payout_method"), rs.getString("payout_account_masked"));

  public List<PayoutRow> mine(UUID sellerId, int limit) {
    return db.query("select * from payouts where seller_id = :s and deleted_at is null order by created_at desc limit :lim", Map.of("s", sellerId, "lim", limit), PAYOUT);
  }

  public boolean hasOpen(UUID sellerId) {
    return !db.queryForList("select id from payouts where seller_id = :s and status in ('requested', 'approved') and deleted_at is null", Map.of("s", sellerId), UUID.class).isEmpty();
  }

  public PayoutRow insert(UUID sellerId, long amount, String method, String accountMasked) {
    return db.query("insert into payouts (id, seller_id, amount, method, account_masked, status) values (:id, :s, :a, :m, :acc, 'requested') returning *",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("s", sellerId).addValue("a", amount).addValue("m", method).addValue("acc", accountMasked), PAYOUT).get(0);
  }

  public Optional<PayoutRow> byId(UUID id) {
    var l = db.query("select * from payouts where id = :id", Map.of("id", id), PAYOUT);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<PayoutRow> byStatus(String status, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("st", status).addValue("lim", limit);
    String cur = "";
    if (curAt != null) {
      cur = " and created_at < :ca";
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from payouts where status = :st and deleted_at is null" + cur + " order by created_at desc limit :lim", p, PAYOUT);
  }

  /** Optimistic transition: only when the status is still {@code from}. */
  public Optional<PayoutRow> transition(UUID id, String from, String to, String reference, String note, UUID actorId) {
    var l = db.query("update payouts set status = :to, reference = :ref, note = :note, processed_by = :a, processed_at = now(), updated_at = now() where id = :id and status = :from returning *",
        new MapSqlParameterSource().addValue("id", id).addValue("from", from).addValue("to", to).addValue("ref", reference).addValue("note", note).addValue("a", actorId), PAYOUT);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public Optional<SellerPayoutInfo> seller(UUID userId) {
    var l = db.query("select user_id, display_name, slug, payout_method, payout_account_masked from seller_profiles where user_id = :id", Map.of("id", userId), SELLER);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public Map<UUID, SellerPayoutInfo> sellers(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, SellerPayoutInfo> out = new HashMap<>();
    if (!l.isEmpty())
      for (var s : db.query("select user_id, display_name, slug, payout_method, payout_account_masked from seller_profiles where user_id in (:ids)", Map.of("ids", l), SELLER))
        out.put(s.userId(), s);
    return out;
  }
}
