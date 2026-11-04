package com.shikkhasathi.classroom.service;

import static com.shikkhasathi.classroom.service.ClassroomText.*;

import com.shikkhasathi.assignment.dto.AssignmentDtos.Assignment;
import com.shikkhasathi.assignment.service.AssignmentService;
import com.shikkhasathi.classroom.adapter.ClassroomClients;
import com.shikkhasathi.classroom.adapter.ClassroomError;
import com.shikkhasathi.classroom.adapter.ClassroomModels.TokenSet;
import com.shikkhasathi.classroom.adapter.FakeClassroomClient;
import com.shikkhasathi.classroom.adapter.GoogleClassroomClient;
import com.shikkhasathi.classroom.dto.ClassroomDtos.*;
import com.shikkhasathi.classroom.repository.ClassroomRepository;
import com.shikkhasathi.classroom.repository.ClassroomRepository.Conn;
import com.shikkhasathi.classroom.repository.ClassroomRepository.FeedRow;
import com.shikkhasathi.classroom.service.ClassroomSyncService.SyncJob;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.security.JwtService;
import com.shikkhasathi.student.service.DashboardCache;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Google Classroom connection (OAuth / code exchange / disconnect), manual sync and the class/feed read models. */
@Service
public class ClassroomService {
  private static final Logger log = LoggerFactory.getLogger(ClassroomService.class);
  static final long MANUAL_SYNC_COOLDOWN_MS = 60_000;

  private final ClassroomRepository repo;
  private final ClassroomClients clients;
  private final ClassroomSyncService sync;
  private final AssignmentService assignments;
  private final Crypto crypto;
  private final JwtService jwt;
  private final JobQueue jobs;
  private final IntegrationService integrations;
  private final DashboardCache dashboard;

  public ClassroomService(ClassroomRepository repo, ClassroomClients clients, ClassroomSyncService sync, AssignmentService assignments, Crypto crypto, JwtService jwt,
                          JobQueue jobs, IntegrationService integrations, DashboardCache dashboard) {
    this.repo = repo;
    this.clients = clients;
    this.sync = sync;
    this.assignments = assignments;
    this.crypto = crypto;
    this.jwt = jwt;
    this.jobs = jobs;
    this.integrations = integrations;
    this.dashboard = dashboard;
  }

  private void ensureEnabled() {
    if (!integrations.businessFlag("feature_classroom", true)) throw new ApiException(ErrorCode.INTEGRATION_DISABLED, "Google Classroom ফিচার এখন বন্ধ আছে।");
  }

  // ---------------------------------------------------------------- connect / OAuth

  public ConnectUrl connectUrl(UUID userId, String returnTo) {
    ensureEnabled();
    GoogleClassroomClient client = clients.forNewConnection();
    String verifier = Crypto.randomToken(48);
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.sha256Bytes(verifier));
    String safeReturn = returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/home";
    Map<String, Object> st = new LinkedHashMap<>();
    st.put("uid", userId.toString());
    st.put("v", verifier);
    st.put("r", safeReturn);
    st.put("f", client.fake());
    String state = jwt.signPurpose("gc-oauth", st, 10 * 60);
    String email = repo.userEmail(userId).orElse(null);
    return new ConnectUrl(client.authUrl(state, challenge, clients.redirectUri(), email), client.fake());
  }

  /** Web redirect target. Always returns a path to redirect the browser to. */
  public String oauthCallback(String code, String state, String error) {
    String returnTo = "/home";
    try {
      if (state == null) throw ApiException.badRequest(null);
      Map<String, Object> st = jwt.verifyPurpose("gc-oauth", state);
      returnTo = String.valueOf(st.get("r"));
      if (error != null || code == null) return withQuery(returnTo, "classroom", "access_denied".equals(error) ? "denied" : "error");
      GoogleClassroomClient client = Boolean.TRUE.equals(st.get("f")) ? new FakeClassroomClient(Instant.now()) : clients.forNewConnection();
      TokenSet tokens = client.exchangeCode(code, clients.redirectUri(), String.valueOf(st.get("v")));
      saveConnection(UUID.fromString(String.valueOf(st.get("uid"))), tokens, client.fake());
      return withQuery(returnTo, "classroom", "connected");
    } catch (RuntimeException err) {
      log.warn("classroom oauth callback failed: {}", err.getMessage());
      String reason = err instanceof ClassroomError ce && ce.kind() == ClassroomError.Kind.WORKSPACE ? "workspace" : "error";
      return withQuery(returnTo, "classroom", reason);
    }
  }

  /** {@code path} with one query parameter set (others kept), like URL.searchParams.set. */
  static String withQuery(String path, String key, String value) {
    String hash = "";
    int h = path.indexOf('#');
    if (h >= 0) {
      hash = path.substring(h);
      path = path.substring(0, h);
    }
    String query = "";
    int q = path.indexOf('?');
    if (q >= 0) {
      query = path.substring(q + 1);
      path = path.substring(0, q);
    }
    List<String[]> params = new ArrayList<>();
    if (!query.isEmpty()) {
      for (String pair : query.split("&")) {
        if (pair.isEmpty()) continue;
        int eq = pair.indexOf('=');
        String k = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
        String v = eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8) : "";
        params.add(new String[] {k, v});
      }
    }
    boolean replaced = false;
    List<String[]> out = new ArrayList<>();
    for (String[] p : params) {
      if (p[0].equals(key)) {
        if (!replaced) out.add(new String[] {key, value});
        replaced = true;
      } else out.add(p);
    }
    if (!replaced) out.add(new String[] {key, value});
    String qs = out.stream().map(p -> URLEncoder.encode(p[0], StandardCharsets.UTF_8) + "=" + URLEncoder.encode(p[1], StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    return path + "?" + qs + hash;
  }

  /** Mobile: server auth code from Credential Manager / GoogleSignIn. */
  public ClassroomStatus connectWithCode(UUID userId, String code, String redirect) {
    ensureEnabled();
    GoogleClassroomClient client = code.startsWith("fake") && !clients.isProd() ? new FakeClassroomClient(Instant.now()) : clients.forNewConnection();
    TokenSet tokens = client.exchangeCode(code, redirect == null ? "" : redirect, null);
    saveConnection(userId, tokens, client.fake());
    return getStatus(userId);
  }

  private void saveConnection(UUID userId, TokenSet t, boolean isFake) {
    Conn existing = repo.connection(userId).orElse(null);
    String refresh = t.refreshToken() != null ? t.refreshToken() : existing != null ? crypto.decrypt(existing.refreshTokenEnc()) : null;
    if (refresh == null) throw new ClassroomError(ClassroomError.Kind.REAUTH, "no refresh token");
    repo.saveConnection(existing == null ? null : existing.id(), userId, t.googleUserId(), t.email(), crypto.encrypt(refresh), crypto.encrypt(t.accessToken()), t.expiresAt(), t.scopes(),
        isFake, existing != null && existing.isFake() != isFake, ClassroomSyncService.now());
    jobs.enqueue(JobNames.CLASSROOM_FULL_SYNC, new SyncJob(userId, existing != null ? "reconnect" : "connect", null));
  }

  /** Manual refresh — once a minute, respects quota backoff. Runs inline so the UI gets fresh data. */
  public ClassroomStatus manualSync(UUID userId) {
    Conn conn = repo.connection(userId).orElseThrow(() -> ApiException.notFound("Google Classroom সংযুক্ত নেই।"));
    if ("needs_reauth".equals(conn.status())) throw new ApiException(ErrorCode.CLASSROOM_REAUTH);
    if ("workspace_blocked".equals(conn.status())) throw new ApiException(ErrorCode.CLASSROOM_WORKSPACE_BLOCKED);
    long now = System.currentTimeMillis();
    if (conn.lastManualSyncAt() != null && now - conn.lastManualSyncAt().toEpochMilli() < MANUAL_SYNC_COOLDOWN_MS) {
      throw ApiException.rateLimited("এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন।",
          Map.of("retryAfterSec", (long) Math.ceil((MANUAL_SYNC_COOLDOWN_MS - (now - conn.lastManualSyncAt().toEpochMilli())) / 1000.0)));
    }
    if (conn.nextSyncAllowedAt() != null && conn.nextSyncAllowedAt().toEpochMilli() > now) {
      throw new ApiException(ErrorCode.CLASSROOM_QUOTA, null, Map.of("retryAfterSec", (long) Math.ceil((conn.nextSyncAllowedAt().toEpochMilli() - now) / 1000.0)));
    }
    repo.setLastManualSync(conn.id(), ClassroomSyncService.now());
    sync.runSync(new SyncJob(userId, "manual", null));
    return getStatus(userId);
  }

  // ---------------------------------------------------------------- disconnect

  /** Revoke at Google, stop push, delete every cached Classroom row and Classroom notifications. */
  public void disconnect(UUID userId) {
    Conn conn = repo.connection(userId).orElse(null);
    if (conn == null) return;
    try {
      GoogleClassroomClient client = sync.clientFor(conn);
      var regs = repo.registrations(userId);
      if (!regs.isEmpty()) {
        String at;
        try {
          at = sync.accessToken(conn, client);
        } catch (RuntimeException e) {
          at = null;
        }
        if (at != null) for (var r : regs) client.deleteRegistration(at, r.registrationId());
      }
      client.revoke(crypto.decrypt(conn.refreshTokenEnc()));
    } catch (RuntimeException err) {
      log.warn("classroom revoke failed (continuing with local delete): {}", err.getMessage());
    }
    repo.deleteAllFor(userId);
    repo.deleteConnection(conn.id());
    dashboard.invalidate(userId);
  }

  // ---------------------------------------------------------------- read models

  public ClassroomStatus getStatus(UUID userId) {
    Conn conn = repo.connection(userId).orElse(null);
    if (conn == null) return new ClassroomStatus(false, null, null, null, null, null, null, 0, false);
    long count = repo.courseCount(userId);
    Instant nextManual = conn.lastManualSyncAt() == null ? null : conn.lastManualSyncAt().plusMillis(MANUAL_SYNC_COOLDOWN_MS);
    return new ClassroomStatus(true, conn.status(), conn.email(), JsonConfig.iso(conn.lastSyncAt()), JsonConfig.iso(conn.lastFullSyncAt()),
        nextManual != null && nextManual.isAfter(Instant.now()) ? JsonConfig.iso(nextManual) : null, conn.errorCode(), count, conn.isFake());
  }

  public CourseList listCourses(UUID userId) {
    var courses = assignments.coursesMap(userId);
    var unread = repo.unreadByCourse(userId);
    var all = assignments.all(userId, null, Instant.now());
    List<ClassCourse> items = new ArrayList<>();
    for (var c : courses.values()) {
      var pending = all.stream().filter(a -> a.courseId().equals(c.gcCourseId()) && a.status().equals("pending")).toList();
      items.add(new ClassCourse(c.gcCourseId(), c.name(), c.section(), c.room(), c.teacherNames(), c.color(), c.alternateLink(), unread.getOrDefault(c.gcCourseId(), 0L),
          pending.size(), pending.stream().map(Assignment::dueAt).filter(Objects::nonNull).findFirst().orElse(null)));
    }
    return new CourseList(items);
  }

  public Stream courseStream(UUID userId, String courseId) {
    var course = listCourses(userId).items().stream().filter(c -> c.id().equals(courseId)).findFirst().orElseThrow(() -> ApiException.notFound("এই ক্লাসটি পাওয়া যায়নি।"));
    var work = assignments.all(userId, courseId, Instant.now());
    List<StreamItem> items = new ArrayList<>();
    for (var a : work) items.add(new StreamItem(a.id(), "assignment", a.title(), a.description(), null, a.materials(), a.postedAt(), a.alternateLink(), a));
    for (var m : repo.materials(userId, courseId)) {
      items.add(new StreamItem(m.gcMaterialId(), "material", m.title(), m.description(), null, assignments.materials(m.materialsJson()), JsonConfig.iso(m.createdAtGc()), m.alternateLink(), null));
    }
    for (var a : repo.announcements(userId, courseId)) {
      String first = a.text().split("\n", -1)[0];
      items.add(new StreamItem(a.gcAnnouncementId(), "announcement", first.length() > 120 ? first.substring(0, 120) : first, a.text(), a.authorName(),
          assignments.materials(a.materialsJson()), JsonConfig.iso(a.createdAtGc()), a.alternateLink(), null));
    }
    items.sort((x, y) -> (y.postedAt() == null ? "" : y.postedAt()).compareTo(x.postedAt() == null ? "" : x.postedAt()));
    return new Stream(course, items);
  }

  /** "নতুন আপডেট" — newest first, cursor-paginated. */
  public FeedPage feed(UUID userId, String cursor, int limit, String courseId, boolean unreadOnly) {
    var cur = Cursor.decode(cursor);
    Instant before = null;
    UUID beforeId = null;
    if (cur != null) {
      try {
        before = Instant.parse(cur.sortString());
        beforeId = UUID.fromString(cur.id());
      } catch (RuntimeException e) {
        throw ApiException.badRequest("অবৈধ cursor");
      }
    }
    var rows = repo.feed(userId, courseId, unreadOnly, before, beforeId, limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.occurredAt()), r.id().toString()));
    return new FeedPage(hydrateFeed(userId, pg.items()), pg.nextCursor());
  }

  /** Batch-load the referenced coursework/material/announcement rows for feed items (no N+1). */
  private List<FeedItem> hydrateFeed(UUID userId, List<FeedRow> items) {
    if (items.isEmpty()) return List.of();
    var courses = assignments.coursesMap(userId);
    java.util.function.Function<Set<String>, List<String>> ids = kinds -> items.stream().filter(i -> kinds.contains(i.kind())).map(FeedRow::refId).toList();
    var cws = repo.courseworkByIds(userId, ids.apply(Set.of("assignment", "graded"))).stream().collect(Collectors.toMap(ClassroomRepository.CourseworkRow::gcCourseworkId, x -> x, (a, b) -> a));
    var subs = repo.submissionsByCoursework(userId, ids.apply(Set.of("graded"))).stream().collect(Collectors.toMap(ClassroomRepository.SubmissionRow::gcCourseworkId, x -> x, (a, b) -> a));
    var mts = repo.materialsByIds(userId, ids.apply(Set.of("material"))).stream().collect(Collectors.toMap(ClassroomRepository.MaterialRow::gcMaterialId, x -> x, (a, b) -> a));
    var ans = repo.announcementsByIds(userId, ids.apply(Set.of("announcement"))).stream().collect(Collectors.toMap(ClassroomRepository.AnnouncementRow::gcAnnouncementId, x -> x, (a, b) -> a));
    List<FeedItem> out = new ArrayList<>();
    for (FeedRow i : items) {
      var c = courses.get(i.gcCourseId());
      String courseName = c == null ? "" : c.name();
      String color = c == null ? COLORS.get(0) : c.color();
      String occurred = JsonConfig.iso(i.occurredAt());
      boolean read = i.readAt() != null;
      if (i.kind().equals("assignment") || i.kind().equals("graded")) {
        var cw = cws.get(i.refId());
        var s = subs.get(i.refId());
        FeedGrade grade = i.kind().equals("graded") ? new FeedGrade(s == null ? null : num(s.assignedGrade()), cw == null ? null : num(cw.maxPoints())) : null;
        out.add(new FeedItem(i.id(), i.kind(), i.refId(), i.gcCourseId(), courseName, color, i.title(), cw == null ? null : cw.description(),
            cw == null ? List.of() : assignments.materials(cw.materialsJson()), grade, cw == null ? null : cw.alternateLink(), occurred, read));
      } else if (i.kind().equals("material")) {
        var m = mts.get(i.refId());
        out.add(new FeedItem(i.id(), i.kind(), i.refId(), i.gcCourseId(), courseName, color, i.title(), m == null ? null : m.description(),
            m == null ? List.of() : assignments.materials(m.materialsJson()), null, m == null ? null : m.alternateLink(), occurred, read));
      } else {
        var a = ans.get(i.refId());
        out.add(new FeedItem(i.id(), i.kind(), i.refId(), i.gcCourseId(), courseName, color, i.title(), a == null ? null : a.text(),
            a == null ? List.of() : assignments.materials(a.materialsJson()), null, a == null ? null : a.alternateLink(), occurred, read));
      }
    }
    return out;
  }

  public void markFeedRead(UUID userId, List<UUID> ids, boolean all) {
    if (!all && (ids == null || ids.isEmpty())) return;
    repo.markFeedRead(userId, all ? null : ids);
    dashboard.invalidate(userId);
  }

  public long unreadFeedCount(UUID userId) {
    return repo.unreadFeedCount(userId);
  }

  /** Deadlines per Asia/Dhaka day (YYYY-MM-DD) in [from, to) — the dashboard week strip. */
  public Map<String, Long> deadlineCountsByDay(UUID userId, Instant from, Instant to) {
    return repo.deadlineCountsByDay(userId, from, to);
  }
}
