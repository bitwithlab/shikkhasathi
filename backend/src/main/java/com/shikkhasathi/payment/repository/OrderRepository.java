package com.shikkhasathi.payment.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import com.shikkhasathi.common.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE: orders, order items, payments, invoices, refunds (+ the user/enrollment reads checkout needs). */
@Repository
public class OrderRepository {
  public record OrderRow(UUID id, UUID studentId, String status, long subtotal, long discount, long total, UUID couponId, String paymentMethod,
                         Instant paidAt, Instant fulfilledAt, String idempotencyKey, Instant createdAt) {}

  public record ItemRow(UUID id, UUID orderId, UUID courseId, UUID bundleId, UUID sellerId, String title, long price, long discount) {}

  /** {@code gatewayRef} = raw_payload->>'gatewayRef' */
  public record PaymentRow(UUID id, UUID orderId, String gateway, String gatewayTrxId, String gatewaySessionId, String method, long amount, String status,
                           String gatewayRef, Instant createdAt) {}

  public record InvoiceRow(UUID id, UUID orderId, String number, String billingName, String billingPhone, String billingEmail, String storageKey) {}

  public record RefundRow(UUID id, UUID orderId, UUID requestedBy, String reason, String status, long amount, String note, Instant createdAt) {}

  public record UserContact(UUID id, String name, String email, String phone) {}

  private final NamedParameterJdbcTemplate db;

  public OrderRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  // ---------------------------------------------------------------- mappers

  static final RowMapper<OrderRow> ORDER = (rs, i) -> new OrderRow(uuid(rs, "id"), uuid(rs, "student_id"), rs.getString("status"), rs.getLong("subtotal"), rs.getLong("discount"),
      rs.getLong("total"), uuid(rs, "coupon_id"), rs.getString("payment_method"), ts(rs, "paid_at"), ts(rs, "fulfilled_at"), rs.getString("idempotency_key"), ts(rs, "created_at"));
  static final RowMapper<ItemRow> ITEM = (rs, i) -> new ItemRow(uuid(rs, "id"), uuid(rs, "order_id"), uuid(rs, "course_id"), uuid(rs, "bundle_id"), uuid(rs, "seller_id"),
      rs.getString("title"), rs.getLong("price"), rs.getLong("discount"));
  static final RowMapper<PaymentRow> PAYMENT = (rs, i) -> new PaymentRow(uuid(rs, "id"), uuid(rs, "order_id"), rs.getString("gateway"), rs.getString("gateway_trx_id"),
      rs.getString("gateway_session_id"), rs.getString("method"), rs.getLong("amount"), rs.getString("status"), rs.getString("gateway_ref"), ts(rs, "created_at"));
  static final RowMapper<InvoiceRow> INVOICE = (rs, i) -> new InvoiceRow(uuid(rs, "id"), uuid(rs, "order_id"), rs.getString("number"), rs.getString("billing_name"),
      rs.getString("billing_phone"), rs.getString("billing_email"), rs.getString("storage_key"));
  static final RowMapper<RefundRow> REFUND = (rs, i) -> new RefundRow(uuid(rs, "id"), uuid(rs, "order_id"), uuid(rs, "requested_by"), rs.getString("reason"), rs.getString("status"),
      rs.getLong("amount"), rs.getString("note"), ts(rs, "created_at"));
  private static final String PAYMENT_COLS = "id, order_id, gateway, gateway_trx_id, gateway_session_id, method, amount, status, raw_payload->>'gatewayRef' as gateway_ref, created_at";

  private static <T> Optional<T> first(List<T> l) {
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  // ---------------------------------------------------------------- orders

  public Optional<OrderRow> order(UUID id) {
    return first(db.query("select * from orders where id = :id", Map.of("id", id), ORDER));
  }

  public List<OrderRow> orders(Collection<UUID> ids) {
    var l = uniq(ids);
    if (l.isEmpty()) return List.of();
    return db.query("select * from orders where id in (:ids)", Map.of("ids", l), ORDER);
  }

  public Optional<OrderRow> orderByIdempotencyKey(UUID studentId, String key) {
    return first(db.query("select * from orders where student_id = :s and idempotency_key = :k", Map.of("s", studentId, "k", key), ORDER));
  }

  /** Insert a pending order; empty when (student, idempotency key) already exists. */
  public Optional<UUID> insertOrder(UUID studentId, long subtotal, long discount, long total, UUID couponId, String idempotencyKey) {
    UUID id = Ids.uuidv7();
    var p = new MapSqlParameterSource().addValue("id", id).addValue("s", studentId).addValue("sub", subtotal).addValue("d", discount).addValue("t", total)
        .addValue("c", couponId).addValue("k", idempotencyKey);
    int n = db.update("insert into orders (id, student_id, status, subtotal, discount, total, coupon_id, idempotency_key) values (:id, :s, 'pending', :sub, :d, :t, :c, :k) "
        + "on conflict (student_id, idempotency_key) do nothing", p);
    return n == 1 ? Optional.of(id) : Optional.empty();
  }

  public void insertItem(UUID orderId, UUID courseId, UUID bundleId, UUID sellerId, String title, long price, long discount) {
    db.update("insert into order_items (id, order_id, course_id, bundle_id, seller_id, title, price, discount) values (:id, :o, :c, :b, :s, :t, :p, :d)",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("o", orderId).addValue("c", courseId).addValue("b", bundleId).addValue("s", sellerId)
            .addValue("t", title).addValue("p", price).addValue("d", discount));
  }

  public void markFreePaid(UUID orderId) {
    db.update("update orders set status = 'paid', paid_at = now(), payment_method = 'free', updated_at = now() where id = :id", Map.of("id", orderId));
  }

  public void setPending(UUID orderId, String method) {
    db.update("update orders set status = 'pending', payment_method = :m, updated_at = now() where id = :id", Map.of("id", orderId, "m", method));
  }

  /** pending → failed only */
  public void failIfPending(UUID orderId) {
    db.update("update orders set status = 'failed', updated_at = now() where id = :id and status = 'pending'", Map.of("id", orderId));
  }

  public void markPaid(UUID orderId) {
    db.update("update orders set status = 'paid', paid_at = now(), updated_at = now() where id = :id and status in ('pending', 'failed')", Map.of("id", orderId));
  }

  public void markFulfilled(UUID orderId) {
    db.update("update orders set fulfilled_at = now(), updated_at = now() where id = :id and fulfilled_at is null", Map.of("id", orderId));
  }

  public void setStatus(UUID orderId, String status) {
    db.update("update orders set status = :st, updated_at = now() where id = :id", Map.of("id", orderId, "st", status));
  }

  public String status(UUID orderId) {
    return first(db.queryForList("select status from orders where id = :id", Map.of("id", orderId), String.class)).orElse(null);
  }

  /** Student order history (pending orders older than a day are hidden), newest first. */
  public List<OrderRow> myOrders(UUID studentId, Instant curAt, String curId, int limit) {
    var p = new MapSqlParameterSource().addValue("s", studentId).addValue("lim", limit);
    String cur = "";
    if (curAt != null) {
      cur = " and (created_at < :ca or (created_at = :ca and id < cast(:ci as uuid)))";
      p.addValue("ca", t(curAt)).addValue("ci", curId);
    }
    return db.query("select * from orders where student_id = :s and (status <> 'pending' or created_at > now() - interval '1 day')" + cur
        + " order by created_at desc, id desc limit :lim", p, ORDER);
  }

  public List<OrderRow> paidUnfulfilled(int limit) {
    return db.query("select * from orders where status = 'paid' and fulfilled_at is null limit :lim", Map.of("lim", limit), ORDER);
  }

  /** Admin list: optional status, optional pre-resolved search (student ids / order ids), cursor on created_at. */
  public List<OrderRow> adminOrders(String status, List<UUID> studentIds, List<UUID> orderIds, boolean search, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("lim", limit);
    var w = new ArrayList<String>();
    if (status != null) {
      w.add("status = :st");
      p.addValue("st", status);
    }
    if (curAt != null) {
      w.add("created_at < :ca");
      p.addValue("ca", t(curAt));
    }
    if (search) {
      String a = studentIds.isEmpty() ? "false" : "student_id in (:sids)";
      String b = orderIds.isEmpty() ? "false" : "id in (:oids)";
      w.add("(" + a + " or " + b + ")");
      p.addValue("sids", studentIds).addValue("oids", orderIds);
    }
    return db.query("select * from orders" + (w.isEmpty() ? "" : " where " + String.join(" and ", w)) + " order by created_at desc limit :lim", p, ORDER);
  }

  public List<UUID> usersMatching(String q, int limit) {
    return db.queryForList("select id from users where name ilike :q or phone ilike :q or email ilike :q limit :lim", Map.of("q", "%" + q + "%", "lim", limit), UUID.class);
  }

  public List<UUID> ordersByTrxLike(String q, int limit) {
    return db.queryForList("select order_id from payments where gateway_trx_id ilike :q limit :lim", Map.of("q", "%" + q + "%", "lim", limit), UUID.class);
  }

  // ---------------------------------------------------------------- items

  public List<ItemRow> items(UUID orderId) {
    return db.query("select * from order_items where order_id = :o", Map.of("o", orderId), ITEM);
  }

  public List<ItemRow> itemsFor(Collection<UUID> orderIds) {
    var ids = uniq(orderIds);
    if (ids.isEmpty()) return List.of();
    return db.query("select * from order_items where order_id in (:ids)", Map.of("ids", ids), ITEM);
  }

  // ---------------------------------------------------------------- payments

  public void insertPayment(UUID orderId, String gateway, String trxId, String sessionId, String method, long amount) {
    db.update("insert into payments (id, order_id, gateway, gateway_trx_id, gateway_session_id, method, amount, status) values (:id, :o, :g, :t, :s, :m, :a, 'initiated')",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("o", orderId).addValue("g", gateway).addValue("t", trxId).addValue("s", sessionId)
            .addValue("m", method).addValue("a", amount));
  }

  public Optional<PaymentRow> paymentByTrx(String gateway, String trxId) {
    return first(db.query("select " + PAYMENT_COLS + " from payments where gateway = :g and gateway_trx_id = :t", Map.of("g", gateway, "t", trxId == null ? "" : trxId), PAYMENT));
  }

  public Optional<PaymentRow> succeededPayment(UUID orderId) {
    return first(db.query("select " + PAYMENT_COLS + " from payments where order_id = :o and status = 'succeeded'", Map.of("o", orderId), PAYMENT));
  }

  /** Newest first. */
  public List<PaymentRow> paymentsFor(Collection<UUID> orderIds) {
    var ids = uniq(orderIds);
    if (ids.isEmpty()) return List.of();
    return db.query("select " + PAYMENT_COLS + " from payments where order_id in (:ids) order by created_at desc", Map.of("ids", ids), PAYMENT);
  }

  /** initiated for more than 10 minutes but less than 3 days */
  public List<PaymentRow> stuckPayments(int limit) {
    return db.query("select " + PAYMENT_COLS + " from payments where status = 'initiated' and created_at < now() - interval '10 minutes' and created_at > now() - interval '3 days' limit :lim",
        Map.of("lim", limit), PAYMENT);
  }

  /** Amount mismatch: failed, keep the gateway payload. */
  public void failPayment(UUID id, String rawJson) {
    db.update("update payments set status = 'failed', raw_payload = cast(:r as jsonb), updated_at = now() where id = :id", new MapSqlParameterSource().addValue("id", id).addValue("r", rawJson));
  }

  public void failPaymentValidated(UUID id, String rawJson) {
    db.update("update payments set status = 'failed', raw_payload = cast(:r as jsonb), validated_at = now(), updated_at = now() where id = :id and status <> 'succeeded'",
        new MapSqlParameterSource().addValue("id", id).addValue("r", rawJson));
  }

  public void failPaymentPlain(UUID id) {
    db.update("update payments set status = 'failed', updated_at = now() where id = :id", Map.of("id", id));
  }

  /** @return true when this call flipped the payment to succeeded (exactly once) */
  public boolean succeedPayment(UUID id, String rawJson) {
    return db.update("update payments set status = 'succeeded', raw_payload = cast(:r as jsonb), validated_at = now(), updated_at = now() where id = :id and status <> 'succeeded'",
        new MapSqlParameterSource().addValue("id", id).addValue("r", rawJson)) == 1;
  }

  public void refundPayments(UUID orderId) {
    db.update("update payments set status = 'refunded', updated_at = now() where order_id = :o and status = 'succeeded'", Map.of("o", orderId));
  }

  // ---------------------------------------------------------------- invoices

  public Optional<InvoiceRow> invoice(UUID orderId) {
    return first(db.query("select * from invoices where order_id = :o", Map.of("o", orderId), INVOICE));
  }

  public Map<UUID, String> invoiceNumbers(Collection<UUID> orderIds) {
    var ids = uniq(orderIds);
    Map<UUID, String> out = new HashMap<>();
    if (!ids.isEmpty()) db.query("select order_id, number from invoices where order_id in (:ids)", Map.of("ids", ids), rs -> {
      out.put(uuid(rs, "order_id"), rs.getString("number"));
    });
    return out;
  }

  public long countInvoicesLike(String pattern) {
    Long n = db.queryForObject("select count(*) from invoices where number ilike :p", Map.of("p", pattern), Long.class);
    return n == null ? 0 : n;
  }

  public Optional<InvoiceRow> insertInvoice(UUID orderId, UUID userId, String number, String billingName, String billingPhone, String billingEmail, String itemsJson,
                                            long subtotal, long discount, long vat, long total) {
    var p = new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("o", orderId).addValue("u", userId).addValue("n", number).addValue("bn", billingName)
        .addValue("bp", billingPhone).addValue("be", billingEmail).addValue("items", itemsJson).addValue("sub", subtotal).addValue("d", discount).addValue("v", vat).addValue("t", total);
    return first(db.query("insert into invoices (id, order_id, user_id, number, billing_name, billing_phone, billing_email, items_json, subtotal, discount, vat, total) "
        + "values (:id, :o, :u, :n, :bn, :bp, :be, cast(:items as jsonb), :sub, :d, :v, :t) on conflict do nothing returning *", p, INVOICE));
  }

  public void setInvoiceFile(UUID invoiceId, String key) {
    db.update("update invoices set storage_key = :k, emailed_at = now(), updated_at = now() where id = :id", Map.of("id", invoiceId, "k", key));
  }

  // ---------------------------------------------------------------- refunds

  public Map<UUID, RefundRow> refundsByOrder(Collection<UUID> orderIds) {
    var ids = uniq(orderIds);
    Map<UUID, RefundRow> out = new HashMap<>();
    if (!ids.isEmpty()) for (var r : db.query("select * from refunds where order_id in (:ids)", Map.of("ids", ids), REFUND)) out.putIfAbsent(r.orderId(), r);
    return out;
  }

  public Optional<RefundRow> refund(UUID id) {
    return first(db.query("select * from refunds where id = :id", Map.of("id", id), REFUND));
  }

  public Optional<RefundRow> insertRefund(UUID orderId, UUID requestedBy, String reason, long amount) {
    var p = new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("o", orderId).addValue("u", requestedBy).addValue("r", reason).addValue("a", amount);
    return first(db.query("insert into refunds (id, order_id, requested_by, reason, amount) values (:id, :o, :u, :r, :a) on conflict do nothing returning *", p, REFUND));
  }

  public List<RefundRow> refundsByStatus(String status, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("st", status).addValue("lim", limit);
    String cur = "";
    if (curAt != null) {
      cur = " and created_at < :ca";
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from refunds where status = :st and deleted_at is null" + cur + " order by created_at desc limit :lim", p, REFUND);
  }

  public void decideRefund(UUID id, String status, UUID actorId, String note, String gatewayRefundId, boolean setGatewayRefundId) {
    var p = new MapSqlParameterSource().addValue("id", id).addValue("st", status).addValue("a", actorId).addValue("n", note).addValue("g", gatewayRefundId);
    db.update("update refunds set status = :st, processed_by = :a, processed_at = now(), note = :n" + (setGatewayRefundId ? ", gateway_refund_id = :g" : "") + ", updated_at = now() where id = :id", p);
  }

  // ---------------------------------------------------------------- coupon codes (read model)

  public Map<UUID, String> couponCodes(Collection<UUID> couponIds) {
    var ids = uniq(couponIds);
    Map<UUID, String> out = new HashMap<>();
    if (!ids.isEmpty()) db.query("select id, code from coupons where id in (:ids)", Map.of("ids", ids), rs -> {
      out.put(uuid(rs, "id"), rs.getString("code"));
    });
    return out;
  }

  // ---------------------------------------------------------------- enrollments & progress

  public List<UUID> activeEnrolledCourses(UUID studentId, Collection<UUID> courseIds) {
    var ids = uniq(courseIds);
    if (ids.isEmpty()) return List.of();
    return db.queryForList("select course_id from enrollments where student_id = :s and course_id in (:ids) and status = 'active'", Map.of("s", studentId, "ids", ids), UUID.class);
  }

  /** @return true when a new enrollment row was created */
  public boolean insertEnrollment(UUID studentId, UUID courseId, UUID orderId, UUID bundleId, Instant accessUntil) {
    var p = new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("s", studentId).addValue("c", courseId).addValue("o", orderId).addValue("b", bundleId)
        .addValue("au", t(accessUntil));
    return db.update("insert into enrollments (id, student_id, course_id, order_id, bundle_id, access_until, status) values (:id, :s, :c, :o, :b, :au, 'active') on conflict do nothing", p) == 1;
  }

  /** course ids per order (from enrollments) */
  public Map<UUID, List<UUID>> enrolledCoursesByOrder(Collection<UUID> orderIds) {
    var ids = uniq(orderIds);
    Map<UUID, List<UUID>> out = new HashMap<>();
    if (!ids.isEmpty()) db.query("select order_id, course_id from enrollments where order_id in (:ids)", Map.of("ids", ids), rs -> {
      out.computeIfAbsent(uuid(rs, "order_id"), k -> new ArrayList<>()).add(uuid(rs, "course_id"));
    });
    return out;
  }

  /** Revoke an order's enrollments; returns their course ids. */
  public List<UUID> refundEnrollments(UUID orderId) {
    return db.queryForList("update enrollments set status = 'refunded', updated_at = now() where order_id = :o returning course_id", Map.of("o", orderId), UUID.class);
  }

  /** sum(least(watched_seconds, 100000)) per (student, course) for the given students and courses. */
  public Map<String, Long> watchedSeconds(Collection<UUID> studentIds, Collection<UUID> courseIds) {
    var s = uniq(studentIds);
    var c = uniq(courseIds);
    Map<String, Long> out = new HashMap<>();
    if (s.isEmpty() || c.isEmpty()) return out;
    db.query("select student_id, course_id, coalesce(sum(least(watched_seconds, 100000)), 0) as w from lesson_progress where student_id in (:s) and course_id in (:c) group by student_id, course_id",
        Map.of("s", s, "c", c), rs -> {
          out.put(uuid(rs, "student_id") + ":" + uuid(rs, "course_id"), rs.getLong("w"));
        });
    return out;
  }

  // ---------------------------------------------------------------- people

  public Optional<UserContact> user(UUID id) {
    return first(db.query("select id, name, email, phone from users where id = :id", Map.of("id", id), USER));
  }

  public Map<UUID, UserContact> users(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, UserContact> out = new HashMap<>();
    if (!l.isEmpty()) for (var u : db.query("select id, name, email, phone from users where id in (:ids)", Map.of("ids", l), USER)) out.put(u.id(), u);
    return out;
  }

  private static final RowMapper<UserContact> USER = (rs, i) -> new UserContact(uuid(rs, "id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"));

  /** seller_profiles.commission_rate (null = platform default) */
  public Integer commissionRate(UUID sellerId) {
    var l = db.query("select commission_rate from seller_profiles where user_id = :id", Map.of("id", sellerId), (ResultSet rs, int i) -> Rows.intOrNull(rs, "commission_rate"));
    return l.isEmpty() ? null : l.get(0);
  }
}
