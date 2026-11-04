package com.shikkhasathi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.email.FakeEmailProvider;
import com.shikkhasathi.notification.push.FakePushProvider;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.TestUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Notification center, preferences, push fan-out (port of the notification parts of classroom.test.ts). */
class NotificationTest extends IntegrationTest {
  @Autowired NotificationService notifications;
  @Autowired FakePushProvider fakePush;
  @Autowired FakeEmailProvider fakeEmail;
  @Autowired JobQueue jobs;

  /** 12:00 Asia/Dhaka — outside quiet hours */
  static final Instant NOON_DHAKA = Instant.parse("2026-11-04T06:00:00Z");

  @BeforeEach
  void fixClock() {
    notifications.useClock(Clock.fixed(NOON_DHAKA, ZoneOffset.UTC));
  }

  @AfterEach
  void restoreClock() {
    notifications.useClock(null);
  }

  private void addToken(TestUser u, String token) {
    contract("notifications.registerPush", post("/me/push-tokens", Map.of("platform", "android", "token", token), u));
  }

  private boolean notifyAnn(TestUser u, String ref) {
    return notifications.notify(NotifyInput.of(u.id(), NotificationTypes.CLASSROOM_ANNOUNCEMENT, ref, "ঘোষণা · CSE-2203", "Quiz on Sunday").withLink("/classroom/c1"));
  }

  @Test
  void notifyDedupesStoresAndPushes() {
    var s = createUser("Student");
    addToken(s, "fcm-token-123456");
    assertThat(notifyAnn(s, "a1")).isTrue();
    assertThat(notifyAnn(s, "a1")).isFalse();
    assertThat(notifications.notify(NotifyInput.of(s.id(), NotificationTypes.CLASSROOM_ASSIGNMENT_NEW, "w1", "নতুন অ্যাসাইনমেন্ট", "Lab 3"))).isTrue();
    assertThat(coreJdbc.queryForObject("select count(*) from notifications where user_id = :u", Map.of("u", s.id()), Long.class)).isEqualTo(2);

    assertThat(fakePush.sent()).hasSize(2);
    assertThat(fakePush.sent().get(0).tokens()).containsExactly("fcm-token-123456");
    assertThat(fakePush.sent().get(0).msg().link()).isEqualTo("http://localhost:5173/classroom/c1");
    assertThat(coreJdbc.queryForObject("select count(*) from notifications where user_id = :u and pushed_at is not null", Map.of("u", s.id()), Long.class)).isEqualTo(2);

    var ur = contract("notifications.unreadCount", get("/notifications/unread-count", s));
    assertThat(ur.at("/count").asLong()).isEqualTo(2);
    contract("notifications.read", post("/notifications/read", Map.of("all", true), s));
    assertThat(contract("notifications.unreadCount", get("/notifications/unread-count", s)).at("/count").asLong()).isZero();
  }

  @Test
  void listPaginatesNewestFirstAndFiltersUnread() {
    var s = createUser("Student");
    for (int i = 0; i < 5; i++) notifyAnn(s, "r" + i);
    var p1 = contract("notifications.list", get("/notifications?limit=2", s));
    assertThat(p1.at("/items").size()).isEqualTo(2);
    assertThat(p1.at("/items/0/body").asString()).isEqualTo("Quiz on Sunday");
    assertThat(p1.at("/items/0/read").asBoolean()).isFalse();
    String c = p1.at("/nextCursor").asString();
    var p2 = contract("notifications.list", get("/notifications?limit=2&cursor=" + c, s));
    var p3 = contract("notifications.list", get("/notifications?limit=2&cursor=" + p2.at("/nextCursor").asString(), s));
    assertThat(p3.at("/items").size()).isEqualTo(1);
    assertThat(p3.at("/nextCursor").isNull()).isTrue();
    var ids = new java.util.HashSet<String>();
    for (var p : List.of(p1, p2, p3)) p.at("/items").forEach(n -> ids.add(n.get("id").asString()));
    assertThat(ids).hasSize(5);

    String first = p1.at("/items/0/id").asString();
    contract("notifications.read", post("/notifications/read", Map.of("ids", List.of(first)), s));
    var unread = contract("notifications.list", get("/notifications?unread=true", s));
    assertThat(unread.at("/items").size()).isEqualTo(4);
    // someone else's notifications are invisible
    var other = createUser("Other");
    assertThat(contract("notifications.list", get("/notifications", other)).at("/items").size()).isZero();
  }

  @Test
  void validation() {
    var s = createUser("Student");
    get("/notifications?limit=0", s).expect(422);
    get("/notifications?limit=101", s).expect(422);
    get("/notifications?cursor=@@@", s).expect(400);
    post("/me/push-tokens", Map.of("platform", "blackberry", "token", "0123456789"), s).expect(422);
    post("/me/push-tokens", Map.of("platform", "web", "token", "short"), s).expect(422);
    put("/me/notification-preferences", Map.of("items", List.of(Map.of("type", "nope", "channel", "push", "enabled", true))), s).expect(422);
    post("/notifications/read", Map.of("ids", List.of("not-a-uuid")), s).expect(422);
    get("/notifications").expect(401);
  }

  @Test
  void pushAndInAppCanBeSwitchedOffPerType() {
    var s = createUser("Student");
    addToken(s, "fcm-token-123456");
    var prefs = contract("notifications.updatePreferences", put("/me/notification-preferences", Map.of("items", List.of(
        Map.of("type", "classroom.announcement", "channel", "push", "enabled", false),
        Map.of("type", "classroom.announcement", "channel", "in_app", "enabled", false))), s));
    assertThat(prefs.at("/items").size()).isEqualTo(NotificationTypes.ALL.size() * 4);
    notifyAnn(s, "a1");
    assertThat(fakePush.sent()).isEmpty();
    var readAt = coreJdbc.queryForObject("select read_at from notifications where user_id = :u", Map.of("u", s.id()), java.sql.Timestamp.class);
    assertThat(readAt).isNotNull();

    var got = contract("notifications.preferences", get("/me/notification-preferences", s));
    boolean push = false, overdueInApp = false, overduePush = true;
    for (var p : got.at("/items")) {
      String t = p.get("type").asString(), ch = p.get("channel").asString();
      if (t.equals("classroom.announcement") && ch.equals("push")) push = p.get("enabled").asBoolean();
      if (t.equals("classroom.overdue") && ch.equals("in_app")) overdueInApp = p.get("enabled").asBoolean();
      if (t.equals("classroom.overdue") && ch.equals("push")) overduePush = p.get("enabled").asBoolean();
    }
    assertThat(push).isFalse();
    assertThat(overdueInApp).isTrue();
    assertThat(overduePush).isFalse();
  }

  @Test
  void quietHoursHoldNonUrgentPushUntil0700Dhaka() {
    var s = createUser("Student");
    addToken(s, "fcm-token-123456");
    notifications.useClock(Clock.fixed(Instant.parse("2026-11-04T18:30:00Z"), ZoneOffset.UTC)); // 00:30 Dhaka
    int before = jobs.inlineLog().size();
    notifyAnn(s, "late");
    assertThat(fakePush.sent()).isEmpty();
    assertThat(jobs.inlineLog().subList(before, jobs.inlineLog().size()).stream().filter(e -> e.getKey().equals(JobNames.NOTIFICATIONS_DISPATCH)).count()).isEqualTo(2); // initial + delayed retry
    // urgent types go out immediately
    notifications.notify(NotifyInput.of(s.id(), NotificationTypes.CLASSROOM_DUE_2H, "w1", "২ ঘণ্টা বাকি", "Lab 3"));
    assertThat(fakePush.sent()).hasSize(1);
    assertThat(NotificationService.inQuietHours(Instant.parse("2026-11-04T17:00:00Z"))).isTrue(); // 23:00
    assertThat(NotificationService.inQuietHours(Instant.parse("2026-11-04T01:00:00Z"))).isFalse(); // 07:00
  }

  @Test
  void classroomBurstsAreBatchedIntoOneSummary() {
    var s = createUser("Student");
    addToken(s, "fcm-token-123456");
    for (int i = 0; i < 6; i++) notifyAnn(s, "b" + i);
    var sent = fakePush.sent();
    assertThat(sent).hasSize(4); // 3 individual + 1 summary, then silence
    assertThat(sent.get(3).msg().title()).isEqualTo("Classroom-এ নতুন আপডেট");
    assertThat(sent.get(3).msg().body()).isEqualTo("আপনার ক্লাসগুলোতে 4টি নতুন আপডেট এসেছে");
  }

  @Test
  void emailFanOutFollowsPreferences() {
    var s = createUser("Rahim", List.of("student"), "rahim@example.com", null);
    notifications.notify(NotifyInput.of(s.id(), NotificationTypes.ORDER_PAID, "o1", "পেমেন্ট সফল", "DS")
        .withLink("/my-courses").withEmail(EmailTemplate.ORDER_PAID, Map.of("items", "DS", "total", 29900, "invoiceNumber", "SS-2026-000001")));
    assertThat(fakeEmail.last().to()).isEqualTo("rahim@example.com");
    var rel = coreJdbc.queryForObject("select related_entity from email_logs where to_email = 'rahim@example.com'", Map.of(), String.class);
    assertThat(rel).startsWith("notification:");

    // admin broadcast: email off by default, on with emailByDefault, off again if the user switched it off
    fakeEmail.reset();
    notifications.notify(NotifyInput.of(s.id(), NotificationTypes.ADMIN_BROADCAST, "b1", "Notice", "Hi").withEmail(EmailTemplate.ADMIN_BROADCAST, Map.of("subject", "Notice", "body", "Hi")));
    assertThat(fakeEmail.outbox()).isEmpty();
    notifications.notify(NotifyInput.of(s.id(), NotificationTypes.ADMIN_BROADCAST, "b2", "Notice", "Hi").withEmail(EmailTemplate.ADMIN_BROADCAST, Map.of("subject", "Notice", "body", "Hi")).withEmailByDefault());
    assertThat(fakeEmail.outbox()).hasSize(1);
    put("/me/notification-preferences", Map.of("items", List.of(Map.of("type", "admin.broadcast", "channel", "email", "enabled", false))), s).expect(200);
    notifications.notify(NotifyInput.of(s.id(), NotificationTypes.ADMIN_BROADCAST, "b3", "Notice", "Hi").withEmail(EmailTemplate.ADMIN_BROADCAST, Map.of("subject", "Notice", "body", "Hi")).withEmailByDefault());
    assertThat(fakeEmail.outbox()).hasSize(1);
  }

  @Test
  void pushTokensMoveBetweenUsersAndUnregister() {
    var a = createUser("A");
    var b = createUser("B");
    addToken(a, "shared-device-token-1");
    addToken(b, "shared-device-token-1");
    assertThat(coreJdbc.queryForObject("select user_id from push_tokens where token = 'shared-device-token-1'", Map.of(), UUID.class)).isEqualTo(b.id());
    contract("notifications.unregisterPush", call(org.springframework.http.HttpMethod.DELETE, "/me/push-tokens", Map.of("token", "shared-device-token-1"), a, Map.of()));
    assertThat(coreJdbc.queryForObject("select count(*) from push_tokens", Map.of(), Long.class)).isEqualTo(1);
    contract("notifications.unregisterPush", call(org.springframework.http.HttpMethod.DELETE, "/me/push-tokens", Map.of("token", "shared-device-token-1"), b, Map.of()));
    assertThat(coreJdbc.queryForObject("select count(*) from push_tokens", Map.of(), Long.class)).isZero();
  }
}
