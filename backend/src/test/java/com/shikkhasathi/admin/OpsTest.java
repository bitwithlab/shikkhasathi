package com.shikkhasathi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.notification.email.FakeEmailProvider;
import com.shikkhasathi.payment.repository.LedgerRepository.NewEntry;
import com.shikkhasathi.payment.service.LedgerService;
import com.shikkhasathi.support.IntegrationTest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Port of Web/server/test/ops.test.ts: payouts, admin users + moderation, broadcasts, logs, analytics, seller insights. */
class OpsTest extends IntegrationTest {
  @Autowired LedgerService ledger;
  @Autowired FakeEmailProvider fakeEmail;

  private long count(String sql, Map<String, ?> p) {
    return coreJdbc.queryForObject(sql, p, Long.class);
  }

  @Test
  void payoutRequestApprovePaidLedgerDebitNotificationEmailOneOpenAtATime() {
    seed();
    var rakib = loginAs("rakib@demo.shikkhasathi.com");
    var admin = loginAs("admin@shikkhasathi.com");
    // released earnings: ৳1,000
    ledger.append(rakib.id(), new NewEntry("sale", 100_000, null, null, null, null, "seed sale", Instant.now().minusSeconds(1), "test:sale:1"));
    var bal = contract("sellerMoney.ledger", get("/seller/ledger", rakib)).at("/balance");
    long available = bal.at("/available").asLong();
    assertThat(available).isGreaterThanOrEqualTo(100_000);
    assertThat(post("/seller/payouts", Map.of("amount", 100), rakib).status()).isEqualTo(400); // below minimum
    assertThat(post("/seller/payouts", Map.of("amount", available + 100), rakib).status()).isEqualTo(400);
    var p = contract("sellerOps.requestPayout", post("/seller/payouts", Map.of("amount", 60_000), rakib));
    String pid = p.at("/id").asString();
    assertThat(post("/seller/payouts", Map.of("amount", 60_000), rakib).status()).isEqualTo(409);
    // a pending payout is held out of available
    assertThat(get("/seller/ledger", rakib).at("/balance/available").asLong()).isEqualTo(available - 60_000);
    assertThat(get("/admin/payouts", rakib).status()).isEqualTo(403);
    var q = contract("adminOps.payouts", get("/admin/payouts", admin));
    assertThat(q.at("/items/0/id").asString()).isEqualTo(pid);
    assertThat(q.at("/items/0/sellerName").asString()).isEqualTo("মো. রাকিবুল হাসান");
    assertThat(q.at("/items/0/status").asString()).isEqualTo("requested");
    assertThat(contract("adminOps.decidePayout", patch("/admin/payouts/" + pid, Map.of("decision", "approve"), admin)).at("/status").asString()).isEqualTo("approved");
    assertThat(patch("/admin/payouts/" + pid, Map.of("decision", "paid"), admin).status()).isEqualTo(400); // reference required
    var paid = contract("adminOps.decidePayout", patch("/admin/payouts/" + pid, Map.of("decision", "paid", "reference", "BKX9AB12CD"), admin));
    assertThat(paid.at("/status").asString()).isEqualTo("paid");
    var debit = coreJdbc.queryForList("select amount from ledger_entries where seller_id = :s and type = 'payout'", Map.of("s", rakib.id()), Long.class);
    assertThat(debit).containsExactly(-60_000L);
    var after = get("/seller/ledger", rakib).at("/balance");
    assertThat(after.at("/available").asLong()).isEqualTo(available - 60_000);
    assertThat(after.at("/pendingPayouts").asLong()).isZero();
    assertThat(after.at("/paidOut").asLong()).isEqualTo(60_000);
    assertThat(count("select count(*) from notifications where user_id = :u and type = 'seller.payout_paid'", Map.of("u", rakib.id()))).isEqualTo(1);
    assertThat(fakeEmail.outbox()).anyMatch(m -> m.subject().contains("পেআউট"));
    assertThat(patch("/admin/payouts/" + pid, Map.of("decision", "reject"), admin).status()).isEqualTo(409);
    assertThat(contract("sellerOps.payouts", get("/seller/payouts", rakib)).at("/items/0/status").asString()).isEqualTo("paid");
  }

  @Test
  void searchSuspendReactivateCannotSuspendSelfRolesChangeAudited() {
    seed();
    var admin = loginAs("admin@shikkhasathi.com");
    var s = createUser("Rafiq Islam", List.of("student"), "rafiq@example.com", null);
    var found = contract("adminOps.users", get("/admin/users?q=rafiq", admin));
    assertThat(found.at("/items").size()).isEqualTo(1);
    var sellers = contract("adminOps.users", get("/admin/users?role=seller", admin)).at("/items");
    assertThat(sellers.size()).isGreaterThan(0);
    for (var u : sellers) assertThat(u.at("/roles").toString()).contains("\"seller\"");
    var sus = contract("adminOps.updateUser", patch("/admin/users/" + s.id(), Map.of("status", "suspended", "reason", "spam"), admin));
    assertThat(sus.at("/status").asString()).isEqualTo("suspended");
    // the access token is rejected right away (AuthInterceptor reads susp:<id>); sessions are revoked
    assertThat(get("/me/orders", s).status()).isEqualTo(403);
    assertThat(count("select count(*) from sessions where user_id = :u and revoked_at is null", Map.of("u", s.id()))).isZero();
    assertThat(patch("/admin/users/" + admin.id(), Map.of("status", "suspended"), admin).status()).isEqualTo(403);
    patch("/admin/users/" + s.id(), Map.of("status", "active"), admin).expect(200);
    assertThat(get("/me/orders", s).status()).isNotEqualTo(403);
    var r = contract("adminOps.updateUser", patch("/admin/users/" + s.id(), Map.of("roles", List.of("seller")), admin));
    var roles = new ArrayList<String>();
    r.at("/roles").forEach(x -> roles.add(x.asString()));
    Collections.sort(roles);
    assertThat(roles).containsExactly("seller", "student");
    assertThat(count("select count(*) from audit_logs where action = 'user.suspend'", Map.of())).isEqualTo(1);
  }

  @Test
  void reportReviewAdminHidesItRatingRecomputedAllReportsResolveRestoreWorks() {
    seed();
    var admin = loginAs("admin@shikkhasathi.com");
    UUID ds = contentJdbc.queryForObject("select id from courses where slug = 'data-structures-midterm-crash-cse-2203'", Map.of(), UUID.class);
    UUID sellerId = contentJdbc.queryForObject("select seller_id from courses where id = :id", Map.of("id", ds), UUID.class);
    var writer = createUser("Troll");
    coreJdbc.update("insert into enrollments (id, student_id, course_id, progress_percent) values (:id, :s, :c, 50)", Map.of("id", Ids.uuidv7(), "s", writer.id(), "c", ds));
    // the review itself is written by the learning module (PUT /courses/:id/review); insert it and recompute aggregates the same way
    UUID rv = Ids.uuidv7();
    coreJdbc.update("insert into reviews (id, course_id, seller_id, student_id, rating, body) values (:id, :c, :sel, :s, 1, :b)",
        Map.of("id", rv, "c", ds, "sel", sellerId, "s", writer.id(), "b", "এই কোর্স ১০০% পরীক্ষায় আসবে, আমার টেলিগ্রামে যোগ দিন"));
    var agg = coreJdbc.queryForMap("select coalesce(round(avg(rating)::numeric, 2), 0)::text as a, count(*)::int as n from reviews where course_id = :c and status = 'visible'", Map.of("c", ds));
    contentJdbc.update("update courses set rating_avg = cast(:a as numeric), rating_count = :n where id = :id", Map.of("a", agg.get("a"), "n", agg.get("n"), "id", ds));
    int c1 = contentJdbc.queryForObject("select rating_count from courses where id = :id", Map.of("id", ds), Integer.class);

    var a = createUser("Reporter A");
    var b = createUser("Reporter B");
    var r1 = contract("reports.create", post("/reports", Map.of("targetType", "review", "targetId", rv, "reason", "spam"), a));
    assertThat(r1.at("/duplicate").asBoolean()).isFalse();
    assertThat(contract("reports.create", post("/reports", Map.of("targetType", "review", "targetId", rv, "reason", "spam"), a)).at("/duplicate").asBoolean()).isTrue();
    post("/reports", Map.of("targetType", "review", "targetId", rv, "reason", "guarantee_claim"), b).expect(201);
    assertThat(post("/reports", Map.of("targetType", "review", "targetId", a.id(), "reason", "spam"), a).status()).isEqualTo(404);
    var q = contract("adminOps.reports", get("/admin/reports", admin));
    assertThat(q.at("/items").size()).isEqualTo(2);
    assertThat(q.at("/items/0/targetType").asString()).isEqualTo("review");
    assertThat(q.at("/items/0/sameTargetCount").asLong()).isEqualTo(2);
    assertThat(q.at("/items/0/targetHidden").asBoolean()).isFalse();
    assertThat(q.at("/items/0/targetPreview").asString()).startsWith("★ ");
    assertThat(q.at("/items/0/targetLink").asString()).endsWith("#reviews");
    assertThat(q.at("/items/0/reporterName").asString()).isEqualTo("Reporter B.");
    var h = contract("adminOps.resolveReport", patch("/admin/reports/" + r1.at("/id").asString(), Map.of("action", "hide", "resolution", "guarantee claim + spam link"), admin));
    assertThat(h.at("/status").asString()).isEqualTo("resolved");
    assertThat(h.at("/targetHidden").asBoolean()).isTrue();
    assertThat(get("/admin/reports", admin).at("/items").size()).isZero();
    assertThat(coreJdbc.queryForObject("select status from reviews where id = :id", Map.of("id", rv), String.class)).isEqualTo("hidden");
    int c2 = contentJdbc.queryForObject("select rating_count from courses where id = :id", Map.of("id", ds), Integer.class);
    assertThat(c2).isEqualTo(c1 - 1);
    var res = contract("adminOps.resolveReport", patch("/admin/reports/" + r1.at("/id").asString(), Map.of("action", "restore"), admin));
    assertThat(res.at("/targetHidden").asBoolean()).isFalse();
    assertThat(contentJdbc.queryForObject("select rating_count from courses where id = :id", Map.of("id", ds), Integer.class)).isEqualTo(c1);
    assertThat(count("select count(*) from audit_logs where action = 'report.hide'", Map.of())).isEqualTo(1);
  }

  @Test
  void broadcastToSellersPreviewInAppNotificationsOptionalEmailHistory() {
    seed();
    var admin = loginAs("admin@shikkhasathi.com");
    long recipients = contract("adminOps.broadcastPreview", post("/admin/broadcasts/preview", Map.of("audience", "sellers"), admin)).at("/recipients").asLong();
    assertThat(recipients).isGreaterThan(0);
    assertThat(post("/admin/broadcasts", Map.of("title", "নতুন ফিচার", "body", "মক টেস্ট এখন কোর্সে যোগ করা যায়।", "link", "https://evil.example", "audience", "sellers"), admin).status())
        .isEqualTo(422);
    assertThat(post("/admin/broadcasts/preview", Map.of("audience", "institution"), admin).status()).isEqualTo(400);
    var bc = contract("adminOps.broadcast",
        post("/admin/broadcasts", Map.of("title", "নতুন ফিচার", "body", "মক টেস্ট এখন কোর্সে যোগ করা যায়।", "link", "/teach", "audience", "sellers", "email", true), admin));
    assertThat(bc.at("/recipients").asLong()).isEqualTo(recipients);
    var sellerIds = coreJdbc.queryForList("select user_id from seller_profiles", Map.of(), UUID.class);
    var ns = coreJdbc.queryForList("select user_id from notifications where type = 'admin.broadcast'", Map.of(), UUID.class);
    assertThat((long) ns.size()).isEqualTo(recipients);
    assertThat(sellerIds).containsAll(ns);
    var list = contract("adminOps.broadcasts", get("/admin/broadcasts", admin));
    assertThat(list.at("/items/0/title").asString()).isEqualTo("নতুন ফিচার");
    assertThat(list.at("/items/0/recipients").asLong()).isEqualTo(recipients);
    var logs = contract("adminOps.emailLogs", get("/admin/email-logs?q=admin_broadcast", admin));
    assertThat(logs.at("/items").size()).isGreaterThan(0);
    var au = contract("adminOps.audit", get("/admin/audit?action=broadcast", admin));
    assertThat(au.at("/items/0/action").asString()).isEqualTo("broadcast.send");
  }

  @Test
  void auditRedactsSecretsAnalyticsAndSellerInsightsReturnRealNumbers() {
    seed();
    var admin = loginAs("admin@shikkhasathi.com");
    coreJdbc.update("insert into audit_logs (id, actor_id, action, entity, entity_id, after_json) values (:id, :a, 'settings.update', 'integration_settings', 'brevo', cast(:j as jsonb))",
        Map.of("id", Ids.uuidv7(), "a", admin.id(), "j", "{\"api_key\":\"xkeysib-123\",\"sender_name\":\"ShikkhaSathi\"}"));
    var au = contract("adminOps.audit", get("/admin/audit?entity=integration_settings", admin));
    assertThat(au.at("/items/0/after/api_key").asString()).isEqualTo("••••");
    assertThat(au.at("/items/0/after/sender_name").asString()).isEqualTo("ShikkhaSathi");
    assertThat(au.at("/items/0/before").isNull()).isTrue();
    var an = contract("adminOps.analytics", get("/admin/analytics?days=30", admin));
    assertThat(an.at("/series").size()).isEqualTo(30);
    assertThat(an.at("/queues/pendingSellers").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(an.at("/totals/enrollments").asLong()).isGreaterThan(0); // seeded demo enrollments
    var rakib = loginAs("rakib@demo.shikkhasathi.com");
    var st = contract("sellerOps.students", get("/seller/students", rakib));
    assertThat(st.at("/total").asLong()).isGreaterThan(0);
    assertThat(st.at("/items/0/studentName").asString()).endsWith("."); // short name only
    assertThat(st.body()).doesNotContain("@");
    var sa = contract("sellerOps.analytics", get("/seller/analytics?days=30", rakib));
    assertThat(sa.at("/series").size()).isEqualTo(30);
    assertThat(sa.at("/courses").size()).isGreaterThan(0);
    assertThat(sa.at("/totals/students").asLong()).isGreaterThan(0);
    assertThat(get("/admin/analytics", rakib).status()).isEqualTo(403);
    assertThat(get("/admin/analytics?days=3", admin).status()).isEqualTo(422);
  }
}
