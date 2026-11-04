package com.shikkhasathi.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.notification.email.FakeEmailProvider;
import com.shikkhasathi.payment.adapter.FakeGateway;
import com.shikkhasathi.payment.service.PaymentService;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.Resp;
import com.shikkhasathi.support.TestUser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** Port of the previous Node API test suite: test/orders.test.ts: orders, checkout, fulfilment, coupons, refunds, reconcile, seller money. */
class OrdersTest extends IntegrationTest {
  @Autowired FakeGateway fakeGateway;
  @Autowired FakeEmailProvider fakeEmail;
  @Autowired StorageService storage;
  @Autowired PaymentService payments;

  record Course(UUID id, UUID sellerId, long price, int enrollmentCount, int totalDurationSec) {}

  record Paid(Resp cb, Map<String, Object> body, String trxId) {}

  private static final Map<String, String> FORM = Map.of("Content-Type", "application/x-www-form-urlencoded");

  private static String form(Map<String, Object> o) {
    return o.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private Course course(String where) {
    return contentJdbc.queryForObject("select id, seller_id, price, enrollment_count, total_duration_sec from courses where " + where + " limit 1", Map.of(),
        (rs, i) -> new Course(rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class), rs.getLong("price"), rs.getInt("enrollment_count"), rs.getInt("total_duration_sec")));
  }

  private Course paidCourse() {
    return course("status = 'published' and price > 0");
  }

  private TestUser nadia() {
    return createUser("Nadia Rahman", List.of("student"), "nadia" + System.nanoTime() + "@example.com", null);
  }

  private TestUser loginById(UUID id) {
    var row = coreJdbc.queryForMap("select email from users where id = :id", Map.of("id", id));
    return loginAs((String) row.get("email"));
  }

  private Resp createOrder(TestUser u, Map<String, Object> body) {
    return post("/orders", body, u);
  }

  private String orderId(Resp r) {
    return r.at("/id").asString();
  }

  private long count(String sql, Map<String, ?> p) {
    return coreJdbc.queryForObject(sql, p, Long.class);
  }

  private Paid payFake(TestUser u, String orderId, String outcome, Long amountOverride) {
    var p = contract("commerce.pay", post("/orders/" + orderId + "/pay", Map.of("method", "bkash"), u));
    assertThat(p.at("/gateway").asString()).isEqualTo("fake");
    var page = get(URI.create(p.at("/redirectUrl").asString()).getPath());
    assertThat(page.status()).isEqualTo(200);
    assertThat(page.body()).contains("ডেভ পেমেন্ট গেটওয়ে");
    var row = coreJdbc.queryForList("select gateway_trx_id, amount from payments where order_id = cast(:o as uuid) order by created_at", Map.of("o", orderId)).get(0);
    String trx = (String) row.get("gateway_trx_id");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tran_id", trx);
    body.put("amount", amountOverride != null ? amountOverride : row.get("amount"));
    body.put("outcome", outcome);
    body.put("sig", fakeGateway.sign(trx, outcome));
    var cb = call(HttpMethod.POST, "/payments/callback/fake?status=" + outcome, form(body), null, FORM);
    return new Paid(cb, body, trx);
  }

  private Paid payFake(TestUser u, String orderId) {
    return payFake(u, orderId, "success", null);
  }

  @Test
  void createsServerPricedOrderIdempotentlyAndBlocksOwnCourses() {
    seed();
    var course = paidCourse();
    var student = nadia();
    String key = "idem-key-0001";
    var a = contract("commerce.createOrder", createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", key)));
    var b = contract("commerce.createOrder", createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", key)));
    assertThat(orderId(b)).isEqualTo(orderId(a));
    assertThat(a.at("/total").asLong()).isEqualTo(course.price());
    assertThat(a.at("/status").asString()).isEqualTo("pending");
    assertThat(count("select count(*) from orders where student_id = :s", Map.of("s", student.id()))).isEqualTo(1);
    // seller can't buy own course
    var seller = loginById(course.sellerId());
    assertThat(createOrder(seller, Map.of("courseId", course.id(), "idempotencyKey", "idem-own-01")).status()).isEqualTo(400);
    // guests can't
    assertThat(post("/orders", Map.of("courseId", course.id(), "idempotencyKey", "idem-guest")).status()).isEqualTo(401);
    // other students can't read it
    var other = createUser("Other Student");
    assertThat(get("/orders/" + orderId(a), other).status()).isEqualTo(403);
    contract("commerce.getOrder", get("/orders/" + orderId(a), student));
    contract("commerce.myOrders", get("/me/orders", student));
  }

  @Test
  void fakeBkashSuccessFulfilsOnceDespiteDuplicateCallbacksAndIpn() {
    seed();
    var course = paidCourse();
    var student = nadia();
    var o = createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-pay-0001"));
    String id = orderId(o);
    var paid = payFake(student, id);
    assertThat(paid.cb().status()).isEqualTo(303);
    assertThat(paid.cb().header("location")).isEqualTo("/checkout/result?order=" + id + "&status=paid");
    // replay browser callback and IPN
    call(HttpMethod.POST, "/payments/callback/fake?status=success", form(paid.body()), null, FORM);
    var ipn = call(HttpMethod.POST, "/webhooks/payments/fake", form(paid.body()), null, FORM);
    assertThat(ipn.status()).isEqualTo(200);
    assertThat(ipn.at("/ok").asBoolean()).isTrue();

    var order = contract("commerce.getOrder", get("/orders/" + id, student));
    assertThat(order.at("/status").asString()).isEqualTo("paid");
    assertThat(order.at("/invoiceNumber").asString()).matches("^SS-\\d{4}-\\d{6}$");
    assertThat(order.at("/refundEligible").asBoolean()).isTrue();
    assertThat(order.at("/fulfilled").asBoolean()).isTrue();
    assertThat(order.at("/firstLessonPath").asString()).startsWith("/learn/");
    assertThat(count("select count(*) from enrollments where student_id = :s and course_id = :c", Map.of("s", student.id(), "c", course.id()))).isEqualTo(1);
    var led = coreJdbc.queryForList("select type, amount, available_at from ledger_entries where order_id = cast(:o as uuid) order by type", Map.of("o", id));
    assertThat(led.stream().map(l -> (String) l.get("type")).toList()).containsExactly("commission", "sale");
    assertThat((Long) led.get(1).get("amount")).isEqualTo(course.price());
    assertThat((Long) led.get(0).get("amount")).isLessThan(0);
    assertThat(led).allMatch(l -> ((Timestamp) l.get("available_at")).toInstant().isAfter(Instant.now()));
    var inv = coreJdbc.queryForMap("select number, storage_key from invoices where order_id = cast(:o as uuid)", Map.of("o", id));
    byte[] pdf = storage.get(Bucket.INVOICES, (String) inv.get("storage_key"));
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    var url = contract("commerce.invoice", get("/orders/" + id + "/invoice", student));
    assertThat(url.at("/number").asString()).isEqualTo(inv.get("number"));
    assertThat(fakeEmail.outbox().stream().filter(m -> m.to().equals(student.email()))).hasSize(1);
    assertThat(count("select count(*) from notifications where user_id = :u and type = 'order.paid'", Map.of("u", student.id()))).isEqualTo(1);
    assertThat(count("select count(*) from notifications where user_id = :u and type = 'seller.new_enrollment'", Map.of("u", course.sellerId()))).isEqualTo(1);
    int after = contentJdbc.queryForObject("select enrollment_count from courses where id = :id", Map.of("id", course.id()), Integer.class);
    assertThat(after).isEqualTo(course.enrollmentCount() + 1);
    // can't buy again; can't pay again
    assertThat(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-pay-0002")).status()).isEqualTo(409);
    assertThat(post("/orders/" + id + "/pay", Map.of("method", "bkash"), student).status()).isEqualTo(409);
    assertThat(count("select count(*) from audit_logs where action = 'payment.succeeded'", Map.of())).isEqualTo(1);
  }

  @Test
  void rejectsTamperedSignatureAndAmountMismatchFailurePathMarksOrderFailed() {
    seed();
    var course = paidCourse();
    var student = nadia();
    String id = orderId(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-bad-0001")));
    // forged success without a valid sig
    post("/orders/" + id + "/pay", Map.of("method", "nagad"), student);
    var p = coreJdbc.queryForMap("select gateway_trx_id, amount from payments where order_id = cast(:o as uuid)", Map.of("o", id));
    var forged = call(HttpMethod.POST, "/payments/callback/fake?status=success",
        form(Map.of("tran_id", p.get("gateway_trx_id"), "amount", p.get("amount"), "outcome", "success", "sig", "nope")), null, FORM);
    assertThat(forged.header("location")).contains("status=failed");
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isZero();
    // retry with the wrong amount (validly signed)
    var bad = payFake(student, id, "success", 100L);
    assertThat(bad.cb().header("location")).contains("status=failed");
    assertThat(get("/orders/" + id, student).at("/status").asString()).isEqualTo("failed");
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isZero();
    // explicit failure
    String id2 = orderId(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-bad-0002")));
    var f = payFake(student, id2, "fail", null);
    assertThat(f.cb().header("location")).isEqualTo("/checkout/result?order=" + id2 + "&status=failed");
    // retry after failure succeeds
    var ok = payFake(student, id2);
    assertThat(ok.cb().header("location")).contains("status=paid");
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isEqualTo(1);
  }

  private void insertCoupon(String code, String scope, UUID sellerId, String type, int value, Instant expiresAt, long minAmount) {
    var p = new HashMap<String, Object>();
    p.put("id", Ids.uuidv7());
    p.put("code", code);
    p.put("scope", scope);
    p.put("seller", sellerId);
    p.put("type", type);
    p.put("value", value);
    p.put("exp", expiresAt == null ? null : Timestamp.from(expiresAt));
    p.put("min", minAmount);
    coreJdbc.update("insert into coupons (id, code, scope, seller_id, type, value, expires_at, min_amount) values (:id, :code, :scope, :seller, :type, :value, :exp, :min)", p);
  }

  @Test
  void couponsMathScopePerUserLimitExpiryAndRedemptionCountedOnce() {
    seed();
    var course = paidCourse();
    var student = nadia();
    insertCoupon("EXAM20", "platform", null, "percent", 20, null, 0);
    insertCoupon("OLD10", "platform", null, "percent", 10, Instant.now().minusSeconds(1), 0);
    insertCoupon("BIG", "platform", null, "fixed", 100, null, 10_000_000);
    insertCoupon("OTHERSELLER", "seller", student.id(), "fixed", 5000, null, 0);
    long disc = Math.round(course.price() * 0.2);
    var v = contract("commerce.validateCoupon", post("/coupons/validate", Map.of("code", "exam20", "courseId", course.id()), student));
    assertThat(v.at("/valid").asBoolean()).isTrue();
    assertThat(v.at("/discount").asLong()).isEqualTo(disc);
    assertThat(v.at("/total").asLong()).isEqualTo(course.price() - disc);
    for (String code : List.of("OLD10", "BIG", "OTHERSELLER", "NOPE"))
      assertThat(contract("commerce.validateCoupon", post("/coupons/validate", Map.of("code", code, "courseId", course.id()), student)).at("/valid").asBoolean()).as(code).isFalse();
    assertThat(createOrder(student, Map.of("courseId", course.id(), "couponCode", "OLD10", "idempotencyKey", "idem-cpn-0000")).status()).isEqualTo(400);
    var o = contract("commerce.createOrder", createOrder(student, Map.of("courseId", course.id(), "couponCode", "EXAM20", "idempotencyKey", "idem-cpn-0001")));
    assertThat(o.at("/couponCode").asString()).isEqualTo("EXAM20");
    assertThat(o.at("/total").asLong()).isEqualTo(course.price() - disc);
    payFake(student, orderId(o));
    assertThat(coreJdbc.queryForObject("select used_count from coupons where code = 'EXAM20'", Map.of(), Integer.class)).isEqualTo(1);
    long sale = coreJdbc.queryForObject("select amount from ledger_entries where order_id = cast(:o as uuid) and type = 'sale'", Map.of("o", orderId(o)), Long.class);
    assertThat(sale).isEqualTo(o.at("/total").asLong()); // seller is credited the discounted amount
    assertThat(post("/coupons/validate", Map.of("code", "EXAM20", "courseId", course.id()), student).at("/message").asString()).contains("আগেই");
  }

  @Test
  void freeCourseEnrollsImmediatelyWithoutPayment() {
    seed();
    var student = nadia();
    var free = course("status = 'published' and price = 0");
    var o = contract("commerce.createOrder", createOrder(student, Map.of("courseId", free.id(), "idempotencyKey", "idem-free-001")));
    assertThat(o.at("/status").asString()).isEqualTo("paid");
    assertThat(o.at("/total").asLong()).isZero();
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isEqualTo(1);
    assertThat(count("select count(*) from invoices where order_id = cast(:o as uuid)", Map.of("o", orderId(o)))).isZero();
  }

  @Test
  void refundEligibilityAdminApprovalReversesLedgerAndRevokesEnrollment() {
    seed();
    var course = paidCourse();
    var student = nadia();
    String id = orderId(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-ref-0001")));
    payFake(student, id);
    assertThat(post("/orders/" + id + "/refund-request", Map.of("reason", "short"), student).status()).isEqualTo(422);
    var r = contract("commerce.refundRequest", post("/orders/" + id + "/refund-request", Map.of("reason", "ভুল কোর্স কিনে ফেলেছি, আমার সেকশনের সিলেবাস আলাদা।"), student));
    assertThat(r.at("/refund/status").asString()).isEqualTo("requested");
    assertThat(r.at("/refundEligible").asBoolean()).isFalse();
    assertThat(post("/orders/" + id + "/refund-request", Map.of("reason", "আবার অনুরোধ করছি দয়া করে"), student).status()).isEqualTo(409);
    // student can't see the admin queue
    assertThat(get("/admin/refunds", student).status()).isEqualTo(403);
    var admin = loginAs("admin@shikkhasathi.com");
    var q = contract("adminCommerce.refunds", get("/admin/refunds", admin));
    assertThat(q.at("/items").size()).isEqualTo(1);
    assertThat(q.at("/items/0/watchedPercent").asInt()).isZero();
    String refundId = q.at("/items/0/id").asString();
    var d = contract("adminCommerce.decideRefund", patch("/admin/refunds/" + refundId, Map.of("decision", "approve", "note", "নীতিমালা অনুযায়ী"), admin));
    assertThat(d.at("/status").asString()).isEqualTo("processed");
    assertThat(coreJdbc.queryForObject("select status from enrollments where student_id = :s", Map.of("s", student.id()), String.class)).isEqualTo("refunded");
    assertThat(coreJdbc.queryForObject("select coalesce(sum(amount), 0) from ledger_entries where order_id = cast(:o as uuid)", Map.of("o", id), Long.class)).isZero();
    assertThat(get("/orders/" + id, student).at("/status").asString()).isEqualTo("refunded");
    assertThat(patch("/admin/refunds/" + refundId, Map.of("decision", "reject"), admin).status()).isEqualTo(409);
    assertThat(count("select count(*) from audit_logs where action = 'refund.approve'", Map.of())).isEqualTo(1);
    // admin order list + search
    var ao = contract("adminCommerce.orders", get("/admin/orders?q=Nadia", admin));
    assertThat(ao.at("/items/0/studentName").asString()).isEqualTo("Nadia Rahman");
    assertThat(ao.at("/items/0/gateway").asString()).isEqualTo("fake");
    assertThat(ao.at("/items/0/status").asString()).isEqualTo("refunded");
    String trx = ao.at("/items/0/gatewayTrxId").asString();
    assertThat(contract("adminCommerce.orders", get("/admin/orders?q=" + trx, admin)).at("/items/0/id").asString()).isEqualTo(id);
    assertThat(contract("adminCommerce.orders", get("/admin/orders?status=pending", admin)).at("/items").size()).isZero();
  }

  @Test
  void refundBlockedAfterWatchingTwentyPercent() {
    seed();
    var course = paidCourse();
    var student = nadia();
    String id = orderId(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-ref-0002")));
    payFake(student, id);
    UUID lesson = contentJdbc.queryForObject("select id from lessons where course_id = :c limit 1", Map.of("c", course.id()), UUID.class);
    coreJdbc.update("insert into lesson_progress (id, student_id, course_id, lesson_id, watched_seconds) values (:id, :s, :c, :l, :w)",
        Map.of("id", Ids.uuidv7(), "s", student.id(), "c", course.id(), "l", lesson, "w", (int) Math.ceil(course.totalDurationSec() * 0.5) + 1));
    var r = post("/orders/" + id + "/refund-request", Map.of("reason", "কোর্সটা আমার প্রত্যাশামতো হয়নি।"), student);
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.errorMessage()).contains("20%");
    assertThat(get("/orders/" + id, student).at("/refundEligible").asBoolean()).isFalse();
  }

  @Test
  void reconcilePicksUpPaidAtGatewayPaymentWhoseCallbackNeverArrived() {
    seed();
    var course = paidCourse();
    var student = nadia();
    String id = orderId(createOrder(student, Map.of("courseId", course.id(), "idempotencyKey", "idem-rec-0001")));
    post("/orders/" + id + "/pay", Map.of("method", "bkash"), student).expect(200);
    var p = coreJdbc.queryForMap("select id, gateway_trx_id, amount from payments where order_id = cast(:o as uuid)", Map.of("o", id));
    FakeGateway.RESULTS.put((String) p.get("gateway_trx_id"), new FakeGateway.Result("success", (Long) p.get("amount")));
    coreJdbc.update("update payments set created_at = now() - interval '20 minutes' where id = :id", Map.of("id", p.get("id")));
    assertThat(payments.reconcile()).isEqualTo(1);
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isEqualTo(1);
  }

  @Test
  void sellerCouponsBundlesAndLedgerWithBalance() {
    seed();
    var student = nadia();
    var rakib = loginAs("rakib@demo.shikkhasathi.com");
    var mine = contentJdbc.queryForList("select id, price from courses where seller_id = :s and status = 'published'", Map.of("s", rakib.id()));
    assertThat(mine.size()).isGreaterThanOrEqualTo(2);
    UUID c0 = (UUID) mine.get(0).get("id");
    UUID c1 = (UUID) mine.get(1).get("id");
    var cp = contract("sellerMoney.createCoupon", post("/seller/coupons", Map.of("code", "rakib50", "type", "fixed", "value", 5000, "courseId", c0), rakib));
    assertThat(cp.at("/code").asString()).isEqualTo("RAKIB50");
    assertThat(cp.at("/scope").asString()).isEqualTo("course");
    assertThat(post("/seller/coupons", Map.of("code", "RAKIB50", "type", "fixed", "value", 10), rakib).status()).isEqualTo(409);
    assertThat(post("/seller/coupons", Map.of("code", "HACK", "type", "fixed", "value", 10), student).status()).isEqualTo(403);
    assertThat(post("/seller/coupons", Map.of("code", "x", "type", "fixed", "value", 10), rakib).status()).isEqualTo(422);
    assertThat(post("/seller/coupons", Map.of("code", "PCT95", "type", "percent", "value", 95), rakib).status()).isEqualTo(400);
    var off = contract("sellerMoney.updateCoupon", patch("/seller/coupons/" + cp.at("/id").asString(), Map.of("active", false), rakib));
    assertThat(off.at("/active").asBoolean()).isFalse();
    assertThat(off.at("/courseId").asString()).isEqualTo(c0.toString()); // absent keys stay unchanged
    var cleared = new HashMap<String, Object>();
    cleared.put("courseId", null);
    var seller = contract("sellerMoney.updateCoupon", patch("/seller/coupons/" + cp.at("/id").asString(), cleared, rakib));
    assertThat(seller.at("/scope").asString()).isEqualTo("seller");
    assertThat(seller.at("/courseId").isNull()).isTrue();
    var list = contract("sellerMoney.coupons", get("/seller/coupons", rakib));
    assertThat(list.at("/items").size()).isEqualTo(1);

    long sum = (Long) mine.get(0).get("price") + (Long) mine.get(1).get("price");
    assertThat(post("/seller/bundles", Map.of("title", "Midterm + Final combo", "courseIds", List.of(c0, c1), "price", sum), rakib).status()).isEqualTo(400);
    var b = contract("sellerMoney.createBundle", post("/seller/bundles", Map.of("title", "Midterm + Final combo", "courseIds", List.of(c0, c1), "price", sum - 10000), rakib));
    assertThat(b.at("/slug").asString()).startsWith("midterm-final-combo-");
    var bd = contract("commerce.bundle", get("/bundles/" + b.at("/slug").asString()));
    assertThat(bd.header("cache-control")).isEqualTo("public, max-age=120");
    assertThat(bd.at("/courses").size()).isEqualTo(2);
    assertThat(bd.at("/compareAtPrice").asLong()).isEqualTo(sum);
    var bundles = contract("sellerMoney.bundles", get("/seller/bundles", rakib));
    assertThat(bundles.at("/items/0/courseCount").asInt()).isEqualTo(2);
    // buy the bundle → both enrolled, ledger split per bundle line
    var o = contract("commerce.createOrder", createOrder(student, Map.of("bundleId", b.at("/id").asString(), "idempotencyKey", "idem-bdl-0001")));
    assertThat(o.at("/total").asLong()).isEqualTo(sum - 10000);
    payFake(student, orderId(o));
    assertThat(count("select count(*) from enrollments where student_id = :s", Map.of("s", student.id()))).isEqualTo(2);
    var l = contract("sellerMoney.ledger", get("/seller/ledger", rakib));
    assertThat(l.at("/items").size()).isGreaterThanOrEqualTo(2);
    assertThat(l.at("/balance/held").asLong()).isGreaterThan(0);
    assertThat(l.at("/items/0/balanceAfter").asLong()).isEqualTo(l.at("/balance/held").asLong() + l.at("/balance/available").asLong() + l.at("/balance/pendingPayouts").asLong());
  }

  @Test
  void callbackRejectsUnknownGatewayAndFakePageNeedsKnownTrx() {
    assertThat(get("/payments/callback/paypal").status()).isEqualTo(404);
    assertThat(call(HttpMethod.POST, "/webhooks/payments/paypal", "a=b", null, FORM).status()).isEqualTo(404);
    assertThat(get("/payments/fake/NOPE").status()).isEqualTo(404);
    var cb = get("/payments/callback/fake?tran_id=UNKNOWN&outcome=success&sig=x");
    assertThat(cb.status()).isEqualTo(303);
    assertThat(cb.header("location")).isEqualTo("/checkout/result?status=failed");
    assertThat(cb.header("cache-control")).isEqualTo("no-store");
  }
}
