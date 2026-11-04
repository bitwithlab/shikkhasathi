package com.shikkhasathi.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikkhasathi.assignment.service.AssignmentService;
import com.shikkhasathi.classroom.adapter.ClassroomClients;
import com.shikkhasathi.classroom.adapter.ClassroomError;
import com.shikkhasathi.classroom.adapter.ClassroomMapping;
import com.shikkhasathi.classroom.adapter.ClassroomModels.GcCourse;
import com.shikkhasathi.classroom.adapter.FakeClassroomClient;
import com.shikkhasathi.classroom.adapter.RealClassroomClient;
import com.shikkhasathi.classroom.service.ClassroomReminderService;
import com.shikkhasathi.classroom.service.ClassroomSyncService;
import com.shikkhasathi.classroom.service.ClassroomSyncService.RenewJob;
import com.shikkhasathi.classroom.service.ClassroomSyncService.SyncJob;
import com.shikkhasathi.classroom.service.ClassroomText;
import com.shikkhasathi.common.Ids;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.notification.push.FakePushProvider;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.TestUser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/** Port of classroom.test.ts: connect + full sync, incremental sync/notifications/reminders, errors/push/disconnect, edge cases, dashboard, REST mapping. */
class ClassroomTest extends IntegrationTest {
  static final long HOUR = 3_600_000L;
  static final long DAY = 24 * HOUR;

  @Autowired ClassroomClients clients;
  @Autowired ClassroomSyncService sync;
  @Autowired ClassroomReminderService reminders;
  @Autowired NotificationService notifications;
  @Autowired FakePushProvider fakePush;

  /** the fake client's "now" */
  volatile long clock = System.currentTimeMillis();

  void useClock() {
    clock = System.currentTimeMillis();
    clients.setFactory(created -> new FakeClassroomClient(created != null ? created : Instant.now(), () -> Instant.ofEpochMilli(clock)));
  }

  @AfterEach
  void restore() {
    clients.setFactory(null);
    notifications.useClock(null);
  }

  String connect(TestUser u) {
    var r = contract("classroom.connectUrl", get("/classroom/connect-url?returnTo=/home", u));
    assertThat(r.at("/fake").asBoolean()).isTrue();
    URI url = URI.create(r.at("/url").asString());
    assertThat(url.getPath()).isEqualTo("/api/v1/classroom/oauth/callback");
    var cb = call(HttpMethod.GET, url.getRawPath() + "?" + url.getRawQuery(), null, null, Map.of());
    assertThat(cb.status()).isEqualTo(302);
    return cb.header("Location");
  }

  long count(String sql, Map<String, ?> p) {
    Long n = coreJdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  List<Map<String, Object>> notificationsOf(TestUser u) {
    return coreJdbc.queryForList("select * from notifications where user_id = :u", Map.of("u", u.id()));
  }

  Map<String, Object> conn(TestUser u) {
    return coreJdbc.queryForMap("select * from google_connections where user_id = :u", Map.of("u", u.id()));
  }

  static Instant ts(Object o) {
    return o == null ? null : ((Timestamp) o).toInstant();
  }

  void runSync(TestUser u) {
    sync.runSync(new SyncJob(u.id(), "poll", null));
  }

  // ================================================================ connect + full sync

  @Test
  void oauthFakeEncryptedTokensFullSyncNoNotificationBurst() {
    useClock();
    var s = createUser("Student");
    assertThat(connect(s)).isEqualTo("/home?classroom=connected");

    var c = conn(s);
    assertThat((String) c.get("refresh_token_enc")).doesNotContain("fake-refresh");
    assertThat(c.get("last_full_sync_at")).isNotNull();

    var status = contract("classroom.status", get("/classroom/status", s));
    assertThat(status.at("/connected").asBoolean()).isTrue();
    assertThat(status.at("/status").asString()).isEqualTo("active");
    assertThat(status.at("/courseCount").asInt()).isEqualTo(5);
    assertThat(status.at("/isFake").asBoolean()).isTrue();

    var courses = contract("classroom.courses", get("/classroom/courses", s));
    Set<String> names = new HashSet<>(), colors = new HashSet<>();
    courses.at("/items").forEach(x -> {
      names.add(x.get("name").asString());
      colors.add(x.get("color").asString());
    });
    assertThat(names).contains("CSE-2203 Data Structures");
    assertThat(colors).hasSize(5);

    var all = contract("classroom.assignments", get("/classroom/assignments", s));
    Map<String, String> st = new HashMap<>();
    all.at("/items").forEach(a -> st.put(a.get("id").asString(), a.get("status").asString()));
    assertThat(st).containsEntry("cw-1", "missing").containsEntry("cw-2", "pending").containsEntry("cw-5", "turned_in").containsEntry("cw-6", "graded");
    assertThat(st).doesNotContainKey("cw-10"); // not posted yet

    var feed = contract("classroom.feed", get("/classroom/feed?limit=50", s));
    assertThat(feed.at("/items").size()).isGreaterThan(10);
    Set<String> kinds = new HashSet<>();
    feed.at("/items").forEach(f -> kinds.add(f.get("kind").asString()));
    assertThat(kinds).contains("assignment", "material", "announcement", "graded");
    assertThat(notificationsOf(s)).isEmpty();

    var stream = contract("classroom.stream", get("/classroom/courses/gc-cse2203/stream", s));
    Set<String> sk = new HashSet<>();
    stream.at("/items").forEach(i -> sk.add(i.get("kind").asString()));
    assertThat(sk).contains("assignment", "material", "announcement");
    get("/classroom/courses/nope/stream", s).expect(404);
  }

  @Test
  void rejectsTamperedOAuthState() {
    var r = get("/classroom/oauth/callback?code=fake-x&state=garbage");
    assertThat(r.status()).isEqualTo(302);
    assertThat(r.header("Location")).isEqualTo("/home?classroom=error");
  }

  @Test
  void deniedConsentAndKeptReturnPath() {
    useClock();
    var s = createUser("Student");
    var r = contract("classroom.connectUrl", get("/classroom/connect-url?returnTo=/classroom?tab=x", s));
    String state = URI.create(r.at("/url").asString()).getRawQuery().replaceAll(".*state=([^&]+).*", "$1");
    var cb = get("/classroom/oauth/callback?error=access_denied&state=" + state);
    assertThat(cb.header("Location")).isEqualTo("/classroom?tab=x&classroom=denied");
    // unsafe return paths fall back to /home
    var r2 = contract("classroom.connectUrl", get("/classroom/connect-url?returnTo=//evil.example", s));
    String st2 = URI.create(r2.at("/url").asString()).getRawQuery().replaceAll(".*state=([^&]+).*", "$1");
    assertThat(get("/classroom/oauth/callback?state=" + st2).header("Location")).isEqualTo("/home?classroom=error");
  }

  @Test
  void onlyRequestsMinimalReadOnlyScopes() {
    var c = new RealClassroomClient(new RealClassroomClient.Config("cid", "s", ClassroomMapping.CLASSROOM_SCOPES), r -> HttpTransport.Response.of(200, "{}"), json);
    URI url = URI.create(c.authUrl("s", "c", "https://shikkhasathi.com/cb", null));
    Map<String, String> q = query(url);
    for (String x : q.get("scope").split(" ")) {
      assertThat(x.equals("openid") || x.equals("email") || x.equals("profile") || x.endsWith(".readonly") || x.endsWith("classroom.push-notifications")).as(x).isTrue();
    }
    assertThat(q.get("access_type")).isEqualTo("offline");
    assertThat(q.get("code_challenge_method")).isEqualTo("S256");
  }

  static Map<String, String> query(URI url) {
    Map<String, String> q = new HashMap<>();
    for (String p : url.getRawQuery().split("&")) {
      int i = p.indexOf('=');
      q.put(p.substring(0, i), java.net.URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
    }
    return q;
  }

  @Test
  void mobileCodeExchangeConnects() {
    useClock();
    var s = createUser("Student");
    var r = contract("classroom.connect", post("/classroom/connect", Map.of("code", "fake-mobile-code", "platform", "android"), s));
    assertThat(r.at("/connected").asBoolean()).isTrue();
    assertThat(r.at("/status").asString()).isEqualTo("active");
    assertThat(r.at("/email").asString()).isEqualTo("student.demo@metrouni.edu.bd");
    post("/classroom/connect", Map.of("code", "abc"), s).expect(422);
  }

  // ================================================================ incremental sync, notifications, reminders

  @Test
  void newItemsAfterConnectGiveFeedDedupedNotificationsAndPush() {
    notifications.useClock(Clock.fixed(Instant.parse("2026-11-03T06:00:00Z"), ZoneOffset.UTC)); // 12:00 Dhaka — outside quiet hours
    useClock();
    var s = createUser("Student");
    coreJdbc.update("insert into push_tokens (id, user_id, platform, token) values (:id, :u, 'android', 'fcm-token-123456')", Map.of("id", Ids.uuidv7(), "u", s.id()));
    connect(s);
    clock += 7 * HOUR; // cw-10 (+20m), mt-5 (+2h), an-4 (+6h) become visible
    runSync(s);
    var types = notificationsOf(s).stream().map(n -> (String) n.get("type")).sorted().toList();
    assertThat(types).containsExactly("classroom.announcement", "classroom.assignment_new", "classroom.material_new");
    runSync(s);
    assertThat(notificationsOf(s)).hasSize(3);
    assertThat(fakePush.sent().size()).isGreaterThanOrEqualTo(1);

    var unread = contract("notifications.unreadCount", get("/notifications/unread-count", s));
    assertThat(unread.at("/count").asLong()).isEqualTo(3);
    post("/notifications/read", Map.of("all", true), s);
    assertThat(get("/notifications/unread-count", s).at("/count").asLong()).isZero();

    // notification copy
    var asg = notificationsOf(s).stream().filter(n -> n.get("type").equals("classroom.assignment_new")).findFirst().orElseThrow();
    assertThat(asg.get("title")).isEqualTo("নতুন অ্যাসাইনমেন্ট · CSE-2203");
    assertThat((String) asg.get("body")).startsWith("Midterm Practice: Graph Traversal — জমা ");
    assertThat(asg.get("link")).isEqualTo("/classroom/assignments?id=cw-10");
  }

  @Test
  void remindersSkipTurnedInWorkAndNudgeOverdueOnce() {
    useClock();
    var s = createUser("Student");
    connect(s);
    long anchor = ts(conn(s).get("created_at")).toEpochMilli();
    // cw-2 due anchor+9h → 2 h window at anchor+7h
    assertThat(reminders.runDeadlineReminders(Instant.ofEpochMilli(anchor + 7 * HOUR))).isGreaterThanOrEqualTo(1);
    assertThat(reminders.runDeadlineReminders(Instant.ofEpochMilli(anchor + 7 * HOUR))).isZero(); // deduped
    assertThat(notificationsOf(s).stream().anyMatch(r -> r.get("type").equals("classroom.due_2h") && ((String) r.get("body")).contains("BST"))).isTrue();
    reminders.runDeadlineReminders(Instant.ofEpochMilli(anchor + 10 * HOUR)); // cw-2 now 1 h overdue
    reminders.runDeadlineReminders(Instant.ofEpochMilli(anchor + 11 * HOUR));
    var nudges = notificationsOf(s).stream().filter(r -> r.get("type").equals("classroom.overdue") && ((String) r.get("body")).contains("BST")).toList();
    assertThat(nudges).hasSize(1);
    // cw-5 (turned in) due anchor+5d → no 24 h reminder
    reminders.runDeadlineReminders(Instant.ofEpochMilli(anchor + 4 * DAY));
    assertThat(notificationsOf(s).stream().anyMatch(r -> ((String) r.get("body")).contains("Essay"))).isFalse();
  }

  @Test
  void pushCanBeSwitchedOffPerType() {
    useClock();
    var s = createUser("Student");
    put("/me/notification-preferences", Map.of("items", List.of(Map.of("type", "classroom.announcement", "channel", "push", "enabled", false),
        Map.of("type", "classroom.announcement", "channel", "in_app", "enabled", false))), s);
    connect(s);
    clock += 7 * HOUR;
    runSync(s);
    var ann = notificationsOf(s).stream().filter(n -> n.get("type").equals("classroom.announcement")).findFirst().orElseThrow();
    assertThat(ann.get("read_at")).isNotNull(); // in-app off → stored as read (kept for dedupe), never shown as unread
    var prefs = get("/me/notification-preferences", s);
    boolean pushOn = true;
    for (JsonNode p : prefs.at("/items")) if (p.get("type").asString().equals("classroom.announcement") && p.get("channel").asString().equals("push")) pushOn = p.get("enabled").asBoolean();
    assertThat(pushOn).isFalse();
  }

  @Test
  void manualSyncIsRateLimitedToOncePerMinute() {
    useClock();
    var s = createUser("Student");
    connect(s);
    contract("classroom.sync", post("/classroom/sync", null, s));
    var second = post("/classroom/sync", null, s);
    assertThat(second.status()).isEqualTo(429);
    assertThat(second.at("/error/details/retryAfterSec").asLong()).isGreaterThan(0);
    assertThat(contract("classroom.status", get("/classroom/status", s)).at("/nextManualSyncAt").isNull()).isFalse();
  }

  @Test
  void pollsOnlyUsersActiveInTheLastSevenDays() {
    useClock();
    var a = createUser("Student");
    coreJdbc.update("update users set last_active_at = now() where id = :u", Map.of("u", a.id()));
    connect(a);
    coreJdbc.update("update google_connections set last_sync_at = now() - interval '1 hour'", Map.of());
    assertThat(sync.pollActive()).isEqualTo(1);
    coreJdbc.update("update users set last_active_at = now() - interval '8 days'", Map.of());
    assertThat(sync.pollActive()).isZero();
  }

  // ================================================================ errors, push, disconnect

  @Test
  void revokedRefreshTokenNeedsReauth() {
    useClock();
    var s = createUser("Student");
    connect(s);
    coreJdbc.update("update google_connections set refresh_token_enc = :r, access_token_expires_at = to_timestamp(0) where user_id = :u", Map.of("r", crypto.encrypt("fake-revoked"), "u", s.id()));
    runSync(s);
    assertThat(get("/classroom/status", s).at("/status").asString()).isEqualTo("needs_reauth");
    var r = post("/classroom/sync", null, s);
    assertThat(r.errorCode()).isEqualTo("CLASSROOM_REAUTH");
    assertThat(r.errorMessage()).contains("আবার সংযুক্ত");
  }

  @Test
  void pubsubPushVerifiesTokenAndTriggersIncrementalSync() {
    useClock();
    var admin = createUser("Admin", "admin");
    integrations.update("google", Map.of("pubsub_verification_token", "secret-push-token"), admin.id(), null);
    var s = createUser("Student");
    connect(s);
    var regs = coreJdbc.queryForList("select registration_id from classroom_push_registrations where gc_course_id = 'gc-cse2203'", Map.of(), String.class);
    assertThat(regs).hasSize(1);
    String data = Base64.getEncoder().encodeToString("{\"collection\":\"courses.courseWork\",\"eventType\":\"CREATED\",\"resourceId\":{\"courseId\":\"gc-cse2203\",\"id\":\"cw-10\"}}".getBytes(StandardCharsets.UTF_8));
    var body = Map.of("message", Map.of("data", data, "attributes", Map.of("registrationId", regs.get(0))));
    assertThat(post("/webhooks/google-pubsub?token=wrong", body).status()).isEqualTo(401);
    clock += HOUR;
    contract("webhooks.googlePubsub", post("/webhooks/google-pubsub?token=secret-push-token", body));
    assertThat(count("select count(*) from classroom_coursework where user_id = :u and gc_coursework_id = 'cw-10'", Map.of("u", s.id()))).isEqualTo(1);
    // bad payloads are dropped quietly
    contract("webhooks.googlePubsub", post("/webhooks/google-pubsub?token=secret-push-token", Map.of("message", Map.of("data", "not-json!"))));
    post("/webhooks/google-pubsub?token=secret-push-token", Map.of()).expect(422);
  }

  @Test
  void disconnectDeletesEveryCachedRowAndClassroomNotifications() {
    useClock();
    var s = createUser("Student");
    connect(s);
    clock += 7 * HOUR;
    runSync(s);
    assertThat(notificationsOf(s)).isNotEmpty();
    contract("classroom.disconnect", delete("/classroom/connection", s));
    for (String t : List.of("classroom_courses", "classroom_coursework", "classroom_feed_items", "classroom_push_registrations", "classroom_sync_runs", "google_connections")) {
      assertThat(count("select count(*) from " + t + " where user_id = :u", Map.of("u", s.id()))).as(t).isZero();
    }
    assertThat(notificationsOf(s)).isEmpty();
    assertThat(contract("classroom.status", get("/classroom/status", s)).at("/connected").asBoolean()).isFalse();
    contract("classroom.disconnect", delete("/classroom/connection", s)); // idempotent
  }

  // ================================================================ edge cases (Phase 9)

  /** Fake client whose list calls fail with a given Google error class. */
  void failing(ClassroomError.Kind kind) {
    clients.setFactory(created -> new FakeClassroomClient(created != null ? created : Instant.now(), () -> Instant.ofEpochMilli(clock)) {
      @Override
      public List<GcCourse> listCourses(String accessToken) {
        throw new ClassroomError(kind, kind == ClassroomError.Kind.QUOTA ? "Quota exceeded" : "The domain administrator has disabled Classroom API access");
      }
    });
  }

  @Test
  void workspaceBlockedIsFriendlyAndSkipped() {
    useClock();
    var s = createUser("Student");
    connect(s);
    failing(ClassroomError.Kind.WORKSPACE);
    runSync(s);
    assertThat(get("/classroom/status", s).at("/status").asString()).isEqualTo("workspace_blocked");
    assertThat(post("/classroom/sync", null, s).errorCode()).isEqualTo("CLASSROOM_WORKSPACE_BLOCKED");
    assertThat(sync.runSync(new SyncJob(s.id(), "poll", null))).isEqualTo(new ClassroomSyncService.SyncResult(0, null));
    // cached data from before stays readable
    assertThat(get("/classroom/courses", s).at("/items").size()).isGreaterThan(0);
  }

  @Test
  void quotaErrorsBackOffDoublingCappedAndKeepLastSync() {
    useClock();
    var s = createUser("Student");
    connect(s);
    var before = conn(s);
    failing(ClassroomError.Kind.QUOTA);
    runSync(s);
    var a = conn(s);
    assertThat(a.get("error_code")).isEqualTo("quota");
    assertThat(a.get("status")).isEqualTo("active");
    long w1 = ts(a.get("next_sync_allowed_at")).toEpochMilli() - System.currentTimeMillis();
    assertThat(w1).isGreaterThan(4 * 60_000L);
    assertThat(ts(a.get("last_sync_at"))).isEqualTo(ts(before.get("last_sync_at")));
    // quota backoff blocks manual sync
    assertThat(post("/classroom/sync", null, s).errorCode()).isEqualTo("CLASSROOM_QUOTA");
    coreJdbc.update("update google_connections set next_sync_allowed_at = now() - interval '1 millisecond' where user_id = :u", Map.of("u", s.id()));
    runSync(s);
    long w2 = ts(conn(s).get("next_sync_allowed_at")).toEpochMilli() - System.currentTimeMillis();
    assertThat(w2).isLessThanOrEqualTo(2 * HOUR);
  }

  @Test
  void pushRegistrationsRenewWithinTwoDaysOfExpiry() {
    useClock();
    var s = createUser("Student");
    connect(s);
    sync.renewRegistrations(new RenewJob(s.id()));
    var regs = coreJdbc.queryForList("select id, registration_id from classroom_push_registrations where user_id = :u", Map.of("u", s.id()));
    assertThat(regs).isNotEmpty();
    var fresh = regs.get(0);
    sync.renewRegistrations(new RenewJob(s.id()));
    var same = coreJdbc.queryForMap("select registration_id from classroom_push_registrations where id = :id", Map.of("id", fresh.get("id")));
    assertThat(same.get("registration_id")).isEqualTo(fresh.get("registration_id"));
    coreJdbc.update("update classroom_push_registrations set expires_at = now() + interval '1 hour' where id = :id", Map.of("id", fresh.get("id")));
    sync.renewRegistrations(new RenewJob(null));
    var renewed = coreJdbc.queryForMap("select expires_at from classroom_push_registrations where id = :id", Map.of("id", fresh.get("id")));
    assertThat(ts(renewed.get("expires_at")).toEpochMilli()).isGreaterThan(System.currentTimeMillis() + 2 * DAY);
  }

  // ================================================================ read models

  @Test
  void assignmentDetailFiltersCalendarAndFeedPaging() {
    useClock();
    var s = createUser("Student");
    connect(s);
    var a = contract("classroom.assignment", get("/classroom/assignments/cw-6", s));
    assertThat(a.at("/status").asString()).isEqualTo("graded");
    assertThat(a.at("/grade").asInt()).isEqualTo(22);
    assertThat(a.at("/maxPoints").toString()).isEqualTo("25");
    get("/classroom/assignments/nope", s).expect(404);

    var upcoming = contract("classroom.assignments", get("/classroom/assignments?status=upcoming&courseId=gc-mat2101", s));
    Set<String> ids = new HashSet<>();
    upcoming.at("/items").forEach(x -> ids.add(x.get("id").asString()));
    assertThat(ids).containsExactlyInAnyOrder("cw-1", "cw-9");
    get("/classroom/assignments?status=bogus", s).expect(422);

    // calendar: deadlines + an exam, ordered by time
    Instant examAt = Instant.now().plusMillis(2 * DAY);
    coreJdbc.update("insert into exam_plans (id, student_id, subject_name, course_code, exam_type, exam_date) values (:id, :u, 'Data Structures', 'CSE-2203', 'midterm', :d)",
        Map.of("id", Ids.uuidv7(), "u", s.id(), "d", Timestamp.from(examAt)));
    String from = Instant.now().minusMillis(DAY).toString(), to = Instant.now().plusMillis(10 * DAY).toString();
    var cal = contract("classroom.calendar", get("/classroom/calendar?from=" + from + "&to=" + to, s));
    List<String> at = new ArrayList<>();
    boolean exam = false;
    for (JsonNode e : cal.at("/items")) {
      at.add(e.get("at").asString());
      if (e.get("kind").asString().equals("exam")) {
        exam = true;
        assertThat(e.get("title").asString()).isEqualTo("Data Structures Midterm");
        assertThat(e.get("color").asString()).isEqualTo("#F59E0B");
      }
    }
    assertThat(exam).isTrue();
    assertThat(at).isSorted();
    get("/classroom/calendar?from=yesterday&to=" + to, s).expect(422);

    // feed: cursor pages, unread filter, mark read
    var p1 = contract("classroom.feed", get("/classroom/feed?limit=5", s));
    assertThat(p1.at("/items").size()).isEqualTo(5);
    String next = p1.at("/nextCursor").asString();
    var p2 = contract("classroom.feed", get("/classroom/feed?limit=5&cursor=" + next, s));
    Set<String> seen = new HashSet<>();
    p1.at("/items").forEach(x -> seen.add(x.get("id").asString()));
    p2.at("/items").forEach(x -> assertThat(seen.add(x.get("id").asString())).isTrue());
    var graded = new ArrayList<JsonNode>();
    contract("classroom.feed", get("/classroom/feed?limit=100", s)).at("/items").forEach(x -> { if (x.get("kind").asString().equals("graded")) graded.add(x); });
    assertThat(graded).isNotEmpty();
    assertThat(graded.get(0).at("/grade/max").isNumber()).isTrue();

    String firstId = p1.at("/items/0/id").asString();
    contract("classroom.feedRead", post("/classroom/feed/read", Map.of("ids", List.of(firstId)), s));
    var unread = contract("classroom.feed", get("/classroom/feed?limit=100&unread=true", s));
    unread.at("/items").forEach(x -> assertThat(x.get("id").asString()).isNotEqualTo(firstId));
    long unreadCount = get("/classroom/courses", s).at("/items").valueStream().mapToLong(c -> c.get("unreadCount").asLong()).sum();
    assertThat(unreadCount).isEqualTo(unread.at("/items").size());
    contract("classroom.feedRead", post("/classroom/feed/read", Map.of("all", true), s));
    assertThat(get("/classroom/feed?unread=1", s).at("/items").size()).isZero();
  }

  @Test
  void classroomFeatureFlagOff() {
    var admin = createUser("Admin", "admin");
    integrations.update("business", Map.of("feature_classroom", "false"), admin.id(), null);
    var s = createUser("Student");
    var r = get("/classroom/connect-url", s);
    assertThat(r.status()).isEqualTo(503);
    assertThat(r.errorMessage()).isEqualTo("Google Classroom ফিচার এখন বন্ধ আছে।");
  }

  // ================================================================ dashboard aggregate

  @Test
  void dashboardNotConnectedClassroomSectionsNullOtherWidgetsRender() {
      var s = createUser("সাদমান");
      var d = contract("dashboard.get", get("/dashboard", s));
      assertThat(d.at("/header/name").asString()).isEqualTo("সাদমান");
      assertThat(d.at("/header/classroom/connected").asBoolean()).isFalse();
      assertThat(d.at("/dueSoon/data").isNull()).isTrue();
      assertThat(d.at("/dueSoon/error").isNull()).isTrue();
      assertThat(d.at("/feed/data").isNull()).isTrue();
      assertThat(d.at("/week/data").size()).isEqualTo(7);
      assertThat(d.at("/continueLearning/data").isNull()).isTrue();
      assertThat(d.at("/recommendations/data").isArray()).isTrue();
      // cached for 60 s under dash:<id>; ?fresh=1 rebuilds
      var again = contract("dashboard.get", get("/dashboard", s));
      assertThat(again.at("/generatedAt").asString()).isEqualTo(d.at("/generatedAt").asString());
      assertThat(redisKeyExists("dash:" + s.id())).isTrue();
    }

  @Test
  void dashboardConnectedDueSoonGroupsFeedUnreadExamCountdown() {
      useClock();
      var s = createUser("Student");
      coreJdbc.update("insert into exam_plans (id, student_id, subject_name, course_code, exam_type, exam_date) values (:id, :u, 'Data Structures', 'CSE-2203', 'midterm', :d)",
          Map.of("id", Ids.uuidv7(), "u", s.id(), "d", Timestamp.from(Instant.now().plusMillis(5 * DAY))));
      connect(s);
      var d = contract("dashboard.get", get("/dashboard?fresh=1", s));
      assertThat(d.at("/dueSoon/data/groups/0/key").asString()).isEqualTo("overdue");
      assertThat(d.at("/dueSoon/data/total").asInt()).isGreaterThanOrEqualTo(4);
      assertThat(d.at("/feed/data/unread").asInt()).isGreaterThan(5);
      assertThat(d.at("/feed/data/items").size()).isEqualTo(8);
      assertThat(d.at("/exam/data/courseCode").asString()).isEqualTo("CSE-2203");
      assertThat(d.at("/exam/data/daysLeft").asInt()).isEqualTo(5);
      long deadlines = d.at("/week/data").valueStream().mapToLong(w -> w.get("deadlines").asLong()).sum();
      assertThat(deadlines).isGreaterThan(0);
      assertThat(d.at("/week/data").valueStream().mapToLong(w -> w.get("exams").asLong()).sum()).isEqualTo(1);
      // marking the feed read invalidates the cache
      post("/classroom/feed/read", Map.of("all", true), s);
      assertThat(redisKeyExists("dash:" + s.id())).isFalse();
      assertThat(contract("dashboard.get", get("/dashboard", s)).at("/feed/data/unread").asInt()).isZero();
    }

  boolean redisKeyExists(String key) {
    try (var c = redisFactory.getConnection()) {
      return Boolean.TRUE.equals(c.keyCommands().exists(key.getBytes(StandardCharsets.UTF_8)));
    }
  }

  // ================================================================ Google REST mapping

  @Test
  void mapsDueDatesAndMaterials() {
    assertThat(ClassroomMapping.mapDue(json.readTree("{\"year\":2026,\"month\":11,\"day\":5}"), json.readTree("{\"hours\":17,\"minutes\":59}"))).isEqualTo(Instant.parse("2026-11-05T17:59:00Z"));
    assertThat(ClassroomMapping.mapDue(json.readTree("{\"year\":2026,\"month\":11,\"day\":5}"), null)).isEqualTo(Instant.parse("2026-11-05T23:59:00Z"));
    assertThat(ClassroomMapping.mapDue(null, null)).isNull();
    var m = ClassroomMapping.mapMaterials(json.readTree("""
        [{"driveFile":{"driveFile":{"title":"a.pdf","alternateLink":"https://d"}}},{"link":{"url":"https://x","title":"X"}},
         {"youtubeVideo":{"title":"Y","alternateLink":"https://y"}},{"form":{"title":"F","formUrl":"https://f"}},{}]"""));
    assertThat(m.stream().map(x -> x.kind()).toList()).containsExactly("drive", "link", "youtube", "form");
  }

  @Test
  void classifiesApiErrorsAndPagesThroughResults() {
    Deque<HttpTransport.Response> responses = new ArrayDeque<>(List.of(
        HttpTransport.Response.of(200, "{\"courses\":[{\"id\":\"1\",\"name\":\"A\"}],\"nextPageToken\":\"p2\"}"),
        HttpTransport.Response.of(200, "{\"courses\":[{\"id\":\"2\",\"name\":\"B\"}]}"),
        HttpTransport.Response.of(429, "{\"error\":{\"message\":\"Quota exceeded\"}}"),
        HttpTransport.Response.of(403, "{\"error\":{\"message\":\"@ClassroomApiDisabled The user is not permitted to access the Classroom API.\"}}"),
        HttpTransport.Response.of(401, "{\"error\":{\"message\":\"Invalid Credentials\"}}")));
    List<HttpTransport.Request> calls = new ArrayList<>();
    var c = new RealClassroomClient(new RealClassroomClient.Config("a", "b", ClassroomMapping.CLASSROOM_SCOPES), r -> {
      calls.add(r);
      return responses.pop();
    }, json);
    assertThat(c.listCourses("tok").stream().map(GcCourse::id).toList()).containsExactly("1", "2");
    assertThat(calls.get(1).url()).contains("pageToken=p2");
    assertThat(calls.get(0).headerValue("authorization")).isEqualTo("Bearer tok");
    assertThatThrownBy(() -> c.listCourses("tok")).isInstanceOfSatisfying(ClassroomError.class, e -> assertThat(e.kind()).isEqualTo(ClassroomError.Kind.QUOTA));
    assertThatThrownBy(() -> c.listCourses("tok")).isInstanceOfSatisfying(ClassroomError.class, e -> assertThat(e.kind()).isEqualTo(ClassroomError.Kind.WORKSPACE));
    assertThatThrownBy(() -> c.listCourses("tok")).isInstanceOfSatisfying(ClassroomError.class, e -> assertThat(e.kind()).isEqualTo(ClassroomError.Kind.REAUTH));
  }

  @Test
  void tokenExchangeReadsIdTokenAndInvalidGrantMeansReauth() {
    String idToken = "x." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"g-1\",\"email\":\"a@b.com\"}".getBytes(StandardCharsets.UTF_8)) + ".y";
    List<HttpTransport.Request> calls = new ArrayList<>();
    var c = new RealClassroomClient(new RealClassroomClient.Config("cid", "sec", ClassroomMapping.CLASSROOM_SCOPES), r -> {
      calls.add(r);
      return calls.size() == 1 ? HttpTransport.Response.of(200, "{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600,\"scope\":\"openid email\",\"id_token\":\"" + idToken + "\"}")
          : HttpTransport.Response.of(400, "{\"error\":\"invalid_grant\"}");
    }, json);
    var t = c.exchangeCode("code-1", "https://shikkhasathi.com/cb", "verifier");
    assertThat(t.refreshToken()).isEqualTo("rt");
    assertThat(t.googleUserId()).isEqualTo("g-1");
    assertThat(t.email()).isEqualTo("a@b.com");
    assertThat(t.scopes()).containsExactly("openid", "email");
    assertThat(calls.get(0).bodyText()).contains("code_verifier=verifier").contains("grant_type=authorization_code");
    assertThatThrownBy(() -> c.refresh("rt")).isInstanceOfSatisfying(ClassroomError.class, e -> assertThat(e.kind()).isEqualTo(ClassroomError.Kind.REAUTH));
  }

  @Test
  void derivesStudentFacingStatus() {
    Instant now = Instant.parse("2026-11-03T12:00:00Z");
    assertThat(AssignmentService.assignmentStatus(null, Instant.parse("2026-11-02T00:00:00Z"), now)).isEqualTo("missing");
    assertThat(AssignmentService.assignmentStatus("NEW", Instant.parse("2026-11-04T00:00:00Z"), now)).isEqualTo("pending");
    assertThat(AssignmentService.assignmentStatus("TURNED_IN", Instant.parse("2026-11-02T00:00:00Z"), now)).isEqualTo("turned_in");
    assertThat(AssignmentService.assignmentStatus("RETURNED", null, now)).isEqualTo("graded");
  }

  @Test
  void banglaDateLikeIntl() {
    assertThat(ClassroomText.dhaka(Instant.parse("2026-11-05T11:59:00Z"))).isEqualTo("বৃহস্পতি ৫ নভে, ৫:৫৯ PM");
    assertThat(ClassroomText.dhaka(Instant.parse("2026-11-05T18:01:00Z"))).isEqualTo("শুক্র ৬ নভে, ১২:০১ AM");
    assertThat(ClassroomText.uri("a b/c(1)")).isEqualTo("a%20b%2Fc(1)");
  }

  @Test
  void googleIntegrationTester() {
    var admin = createUser("Admin", "admin");
    var r = integrations.test("google", null, admin.id());
    assertThat(r.ok()).isFalse();
    assertThat(r.message()).isEqualTo("Client id ও secret দিন");
  }
}
