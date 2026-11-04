package com.shikkhasathi.payment.repository;

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

/** CORE seller ledger (append-only) and the balance aggregates. */
@Repository
public class LedgerRepository {
  public record EntryRow(UUID id, UUID sellerId, String type, long amount, UUID orderId, UUID orderItemId, UUID payoutId, UUID refundId, String description,
                         Instant availableAt, long balanceAfter, Instant createdAt) {}

  public record NewEntry(String type, long amount, UUID orderId, UUID orderItemId, UUID payoutId, UUID refundId, String description, Instant availableAt, String dedupeKey) {}

  /** Raw sums per seller (before subtracting pending payouts). */
  public record Sums(long available, long held, long lifetime, long paidOut) {
    static final Sums ZERO = new Sums(0, 0, 0, 0);
  }

  private final NamedParameterJdbcTemplate db;

  public LedgerRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<EntryRow> ENTRY = (rs, i) -> new EntryRow(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("type"), rs.getLong("amount"), uuid(rs, "order_id"),
      uuid(rs, "order_item_id"), uuid(rs, "payout_id"), uuid(rs, "refund_id"), rs.getString("description"), ts(rs, "available_at"), rs.getLong("balance_after"), ts(rs, "created_at"));

  /** Serializes appends per seller for the current transaction (balance_after stays consistent). */
  public void lockSeller(UUID sellerId) {
    db.query("select pg_advisory_xact_lock(hashtext(:s))", Map.of("s", sellerId.toString()), rs -> {});
  }

  public boolean exists(String dedupeKey) {
    return !db.queryForList("select id from ledger_entries where dedupe_key = :k", Map.of("k", dedupeKey), UUID.class).isEmpty();
  }

  public long lastBalance(UUID sellerId) {
    var l = db.queryForList("select balance_after from ledger_entries where seller_id = :s order by created_at desc, id desc limit 1", Map.of("s", sellerId), Long.class);
    return l.isEmpty() ? 0 : l.get(0);
  }

  public void insert(UUID sellerId, NewEntry e, long balanceAfter) {
    db.update("insert into ledger_entries (id, seller_id, type, amount, order_id, order_item_id, payout_id, refund_id, description, available_at, balance_after, dedupe_key) "
            + "values (:id, :s, :type, :amount, :o, :oi, :p, :r, :d, :at, :b, :k)",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("s", sellerId).addValue("type", e.type()).addValue("amount", e.amount()).addValue("o", e.orderId())
            .addValue("oi", e.orderItemId()).addValue("p", e.payoutId()).addValue("r", e.refundId()).addValue("d", e.description()).addValue("at", t(e.availableAt()))
            .addValue("b", balanceAfter).addValue("k", e.dedupeKey()));
  }

  /** sale + commission entries of an order (for refund reversal). */
  public List<EntryRow> saleEntries(UUID orderId) {
    return db.query("select * from ledger_entries where order_id = :o and type in ('sale', 'commission')", Map.of("o", orderId), ENTRY);
  }

  public List<EntryRow> page(UUID sellerId, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("s", sellerId).addValue("lim", limit);
    String cur = "";
    if (curAt != null) {
      cur = " and created_at < :ca";
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from ledger_entries where seller_id = :s" + cur + " order by created_at desc, id desc limit :lim", p, ENTRY);
  }

  /** Ledger sums per seller, as of {@code now}. */
  public Map<UUID, Sums> sums(Collection<UUID> sellerIds, Instant now) {
    var l = uniq(sellerIds);
    Map<UUID, Sums> out = new HashMap<>();
    if (l.isEmpty()) return out;
    db.query("select seller_id, "
            + "coalesce(sum(amount) filter (where available_at <= :now), 0) as available, "
            + "coalesce(sum(amount) filter (where available_at > :now), 0) as held, "
            + "coalesce(sum(amount) filter (where type in ('sale','commission','refund','adjustment')), 0) as lifetime, "
            + "coalesce(-sum(amount) filter (where type = 'payout'), 0) as paid_out "
            + "from ledger_entries where seller_id in (:ids) group by seller_id",
        new MapSqlParameterSource().addValue("ids", l).addValue("now", t(now)), rs -> {
          out.put(uuid(rs, "seller_id"), new Sums(rs.getLong("available"), rs.getLong("held"), rs.getLong("lifetime"), rs.getLong("paid_out")));
        });
    for (UUID id : l) out.putIfAbsent(id, Sums.ZERO);
    return out;
  }

  /** Sum of open (requested/approved, not deleted) payouts per seller. */
  public Map<UUID, Long> pendingPayouts(Collection<UUID> sellerIds) {
    var l = uniq(sellerIds);
    Map<UUID, Long> out = new HashMap<>();
    if (l.isEmpty()) return out;
    db.query("select seller_id, coalesce(sum(amount), 0) as s from payouts where seller_id in (:ids) and status in ('requested', 'approved') and deleted_at is null group by seller_id",
        Map.of("ids", l), rs -> {
          out.put(uuid(rs, "seller_id"), rs.getLong("s"));
        });
    for (UUID id : l) out.putIfAbsent(id, 0L);
    return out;
  }
}
