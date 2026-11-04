package com.shikkhasathi.classroom.service;

import static com.shikkhasathi.classroom.service.ClassroomText.*;

import com.shikkhasathi.classroom.adapter.ClassroomClients;
import com.shikkhasathi.classroom.adapter.ClassroomError;
import com.shikkhasathi.classroom.adapter.ClassroomModels.*;
import com.shikkhasathi.classroom.adapter.GoogleClassroomClient;
import com.shikkhasathi.classroom.dto.ClassroomDtos.PubSubMessage;
import com.shikkhasathi.classroom.repository.ClassroomRepository;
import com.shikkhasathi.classroom.repository.ClassroomRepository.Conn;
import com.shikkhasathi.classroom.repository.ClassroomRepository.CourseworkRow;
import com.shikkhasathi.classroom.repository.ClassroomRepository.SubmissionRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.student.service.DashboardCache;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Classroom sync: full + incremental pulls into the classroom_* cache with diffing (feed items + notifications),
 * error classification with quota backoff, Pub/Sub push registrations and consumption, and fallback polling.
 * Each step writes as it goes (no wrapping transaction), so a failure keeps everything synced before it and the
 * error state on the connection.
 */
@Service
public class ClassroomSyncService {
  /** Payload of {@code classroom.fullSync} / {@code classroom.incrementalSync}. trigger = connect | reconnect | push | poll | manual */
  public record SyncJob(UUID userId, String trigger, String courseId) {}

  /** Payload of {@code classroom.renewRegistrations} (userId null = every active connection). */
  public record RenewJob(UUID userId) {}

  /** @param error null on success, else the error kind (reauth | workspace | quota | not_found | other) */
  public record SyncResult(int changes, String error) {}

  private final ClassroomRepository repo;
  private final ClassroomClients clients;
  private final Crypto crypto;
  private final JobQueue jobs;
  private final NotificationService notifications;
  private final DashboardCache dashboard;
  private final IntegrationService integrations;
  private final AppProperties props;
  private final JsonMapper json;

  public ClassroomSyncService(ClassroomRepository repo, ClassroomClients clients, Crypto crypto, JobQueue jobs, NotificationService notifications, DashboardCache dashboard,
                              IntegrationService integrations, AppProperties props, JsonMapper json) {
    this.repo = repo;
    this.clients = clients;
    this.crypto = crypto;
    this.jobs = jobs;
    this.notifications = notifications;
    this.dashboard = dashboard;
    this.integrations = integrations;
    this.props = props;
    this.json = json;
  }

  static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  GoogleClassroomClient clientFor(Conn conn) {
    return conn == null ? clients.forNewConnection() : clients.clientFor(true, conn.isFake(), conn.createdAt());
  }

  /** A valid access token: the cached one (if it lives &gt; 30 s more) or a refreshed one (stored encrypted). */
  String accessToken(Conn conn, GoogleClassroomClient client) {
    if (conn.accessTokenEnc() != null && conn.accessTokenExpiresAt() != null && conn.accessTokenExpiresAt().toEpochMilli() > System.currentTimeMillis() + 30_000) {
      return crypto.decrypt(conn.accessTokenEnc());
    }
    AccessToken r = client.refresh(crypto.decrypt(conn.refreshTokenEnc()));
    repo.updateAccessToken(conn.id(), crypto.encrypt(r.accessToken()), r.expiresAt());
    return r.accessToken();
  }

  private static String cut(String s) {
    return s.length() > 300 ? s.substring(0, 300) : s;
  }

  /** Record a sync failure on the connection: needs_reauth / workspace_blocked / quota backoff / error. */
  ClassroomError markError(Conn conn, Throwable err) {
    ClassroomError e = err instanceof ClassroomError ce ? ce : new ClassroomError(ClassroomError.Kind.OTHER, err.getMessage() == null ? "unknown" : err.getMessage());
    String msg = e.getMessage() == null ? "" : e.getMessage();
    switch (e.kind()) {
      case REAUTH -> repo.markConnection(conn.id(), "needs_reauth", "invalid_grant", cut(msg), false, null, false);
      case WORKSPACE -> repo.markConnection(conn.id(), "workspace_blocked", "workspace_blocked", cut(msg), false, null, false);
      case QUOTA -> {
        // exponential-ish backoff: 5 min, doubling up to 2 h via next_sync_allowed_at
        long nowMs = System.currentTimeMillis();
        double prev = conn.nextSyncAllowedAt() != null && conn.nextSyncAllowedAt().toEpochMilli() > nowMs ? conn.nextSyncAllowedAt().toEpochMilli() - nowMs : 2.5 * 60_000;
        long wait = (long) Math.min(prev * 2, 2 * 3_600_000);
        repo.markConnection(conn.id(), "active", "quota", null, true, Instant.ofEpochMilli(nowMs + wait), true);
      }
      default -> repo.markConnection(conn.id(), conn.lastSyncAt() != null ? "active" : "error", "error", cut(msg), false, null, false);
    }
    return e;
  }

  private String materialsJson(List<Material> m) {
    return json.writeValueAsString(m == null ? List.of() : m);
  }

  private static boolean sameInstant(Instant a, Instant b) {
    return Objects.equals(a == null ? null : a.toEpochMilli(), b == null ? null : b.toEpochMilli());
  }

  private static boolean sameNumber(java.math.BigDecimal a, Double b) {
    if (a == null || b == null) return a == null && b == null;
    return a.compareTo(java.math.BigDecimal.valueOf(b)) == 0;
  }

  private void notify(NotifyInput n, boolean silent) {
    if (!silent) notifications.notify(n);
  }

  /**
   * Pull everything (or one course), upsert the cache, diff against what we had, and emit feed items + notifications.
   * The first full sync builds the feed silently (no notification burst); later syncs notify on changes.
   * Transient ("other") failures are rethrown for job retry unless the sync was manual.
   */
  public SyncResult runSync(SyncJob job) {
    Conn conn = repo.connection(job.userId()).orElse(null);
    if (conn == null || "needs_reauth".equals(conn.status()) || "workspace_blocked".equals(conn.status())) return new SyncResult(0, null);
    String trigger = job.trigger();
    boolean full = "connect".equals(trigger) || "reconnect".equals(trigger) || conn.lastFullSyncAt() == null;
    boolean silent = conn.lastFullSyncAt() == null;
    UUID runId = repo.startSyncRun(job.userId(), full ? "full" : "incremental", trigger);
    int changes = 0;
    UUID uid = job.userId();
    try {
      GoogleClassroomClient client = clientFor(conn);
      String at = accessToken(conn, client);
      List<GcCourse> courses = client.listCourses(at);
      List<GcCourse> target = job.courseId() != null ? courses.stream().filter(c -> c.id().equals(job.courseId())).toList() : courses;

      // courses (colors stable by position in the full list)
      var existingCourses = repo.courses(uid);
      for (int i = 0; i < courses.size(); i++) {
        GcCourse c = courses.get(i);
        String color = existingCourses.stream().filter(x -> x.gcCourseId().equals(c.id())).map(x -> x.color()).findFirst().orElse(COLORS.get(i % COLORS.size()));
        repo.upsertCourse(uid, c.id(), c.name(), c.section(), c.room(), c.descriptionHeading(), c.teacherNames(), c.alternateLink(), color, c.state(), c.updateTime());
      }
      if (job.courseId() == null) {
        Set<String> live = new HashSet<>();
        courses.forEach(c -> live.add(c.id()));
        var gone = existingCourses.stream().map(x -> x.gcCourseId()).filter(id -> !live.contains(id)).toList();
        if (!gone.isEmpty()) repo.purgeCourses(uid, gone);
      }
      Map<String, String> courseName = new HashMap<>();
      courses.forEach(c -> courseName.put(c.id(), c.name()));

      for (GcCourse c : target) {
        var work = client.listCourseWork(at, c.id());
        var subs = client.listSubmissions(at, c.id());
        var mats = client.listMaterials(at, c.id());
        var anns = client.listAnnouncements(at, c.id());
        Map<String, CourseworkRow> prevWork = new HashMap<>();
        repo.coursework(uid, c.id()).forEach(p -> prevWork.put(p.gcCourseworkId(), p));
        Map<String, SubmissionRow> prevSubs = new HashMap<>();
        repo.submissions(uid, c.id()).forEach(p -> prevSubs.put(p.gcCourseworkId(), p));
        Set<String> prevMats = new HashSet<>(repo.materialIds(uid, c.id()));
        Set<String> prevAnns = new HashSet<>(repo.announcementIds(uid, c.id()));
        String name = courseName.getOrDefault(c.id(), c.name());

        for (GcCourseWork w : work) {
          CourseworkRow prev = prevWork.get(w.id());
          repo.upsertCoursework(uid, c.id(), w.id(), w.title(), w.description(), w.workType(), w.dueAt(), dec(w.maxPoints()), materialsJson(w.materials()), w.alternateLink(),
              w.state(), w.creationTime(), w.updateTime());
          if (prev == null) {
            changes++;
            repo.addFeed(uid, "assignment", c.id(), w.id(), w.title(), w.creationTime() != null ? w.creationTime() : now());
            notify(NotifyInput.of(uid, NotificationTypes.CLASSROOM_ASSIGNMENT_NEW, w.id(), "নতুন অ্যাসাইনমেন্ট · " + shortCourse(name),
                w.title() + (w.dueAt() != null ? " — জমা " + dhaka(w.dueAt()) : "")).withLink("/classroom/assignments?id=" + uri(w.id())), silent);
          } else if (!sameInstant(prev.dueAt(), w.dueAt()) || !prev.title().equals(w.title())) changes++;
        }
        Set<String> liveWork = new HashSet<>();
        work.forEach(w -> liveWork.add(w.id()));
        var removedWork = prevWork.keySet().stream().filter(id -> !liveWork.contains(id)).toList();
        if (!removedWork.isEmpty()) repo.deleteCoursework(uid, removedWork);

        for (GcSubmission s : subs) {
          SubmissionRow prev = prevSubs.get(s.courseWorkId());
          repo.upsertSubmission(uid, c.id(), s.courseWorkId(), s.id(), s.state(), s.late(), dec(s.assignedGrade()), s.alternateLink(), s.updateTime());
          boolean becameGraded = "RETURNED".equals(s.state()) && s.assignedGrade() != null
              && (prev == null || !"RETURNED".equals(prev.state()) || !sameNumber(prev.assignedGrade(), s.assignedGrade()));
          if (prev == null || !Objects.equals(prev.state(), s.state())) changes++;
          if (becameGraded) {
            GcCourseWork w = work.stream().filter(x -> x.id().equals(s.courseWorkId())).findFirst().orElse(null);
            String title = w == null ? "" : w.title();
            repo.addFeed(uid, "graded", c.id(), s.courseWorkId(), title, s.updateTime() != null ? s.updateTime() : now());
            boolean hasMax = w != null && w.maxPoints() != null && w.maxPoints() != 0;
            notify(NotifyInput.of(uid, NotificationTypes.CLASSROOM_GRADED, s.courseWorkId() + ":" + js(s.assignedGrade()), "গ্রেড এসেছে · " + shortCourse(name),
                title + " — " + js(s.assignedGrade()) + (hasMax ? "/" + js(w.maxPoints()) : "")).withLink("/classroom/assignments?id=" + uri(s.courseWorkId())), silent);
          }
        }

        for (GcMaterialPost m : mats) {
          repo.upsertMaterial(uid, c.id(), m.id(), m.title(), m.description(), materialsJson(m.materials()), m.alternateLink(), m.creationTime(), m.updateTime());
          if (!prevMats.contains(m.id())) {
            changes++;
            repo.addFeed(uid, "material", c.id(), m.id(), m.title(), m.creationTime() != null ? m.creationTime() : now());
            notify(NotifyInput.of(uid, NotificationTypes.CLASSROOM_MATERIAL_NEW, m.id(), "নতুন ম্যাটেরিয়াল · " + shortCourse(name), m.title())
                .withLink("/classroom/" + uri(c.id()) + "?tab=materials"), silent);
          }
        }
        Set<String> liveMats = new HashSet<>();
        mats.forEach(m -> liveMats.add(m.id()));
        var removedMats = prevMats.stream().filter(id -> !liveMats.contains(id)).toList();
        if (!removedMats.isEmpty()) repo.deleteMaterials(uid, removedMats);

        for (GcAnnouncement a : anns) {
          repo.upsertAnnouncement(uid, c.id(), a.id(), a.text(), a.authorName(), materialsJson(a.materials()), a.alternateLink(), a.creationTime(), a.updateTime());
          if (!prevAnns.contains(a.id())) {
            changes++;
            String title = a.text().length() > 90 ? a.text().substring(0, 88) + "…" : a.text();
            repo.addFeed(uid, "announcement", c.id(), a.id(), title, a.creationTime() != null ? a.creationTime() : now());
            notify(NotifyInput.of(uid, NotificationTypes.CLASSROOM_ANNOUNCEMENT, a.id(), "ঘোষণা · " + shortCourse(name), title).withLink("/classroom/" + uri(c.id())), silent);
          }
        }
      }

      Instant now = now();
      repo.markSynced(conn.id(), now, full);
      repo.finishSyncRun(runId, now, true, changes, null);
      dashboard.invalidate(uid);
      if (full) jobs.enqueue(JobNames.CLASSROOM_RENEW_REGISTRATIONS, new RenewJob(uid));
      return new SyncResult(changes, null);
    } catch (RuntimeException err) {
      ClassroomError e = markError(conn, err);
      repo.finishSyncRun(runId, now(), false, changes, cut(e.kind().key() + ": " + e.getMessage()));
      if (e.kind() == ClassroomError.Kind.OTHER && !"manual".equals(trigger)) throw e; // let the queue retry transient failures
      return new SyncResult(changes, e.kind().key());
    }
  }

  // ---------------------------------------------------------------- push (Pub/Sub)

  /** Registers COURSE_WORK_CHANGES per course; renews registrations expiring within 2 days. */
  public void renewRegistrations(RenewJob job) {
    String topic = integrations.get("google").get("pubsub_topic");
    boolean hasTopic = topic != null && !topic.isEmpty();
    for (Conn conn : repo.activeConnections(job == null ? null : job.userId())) {
      GoogleClassroomClient client = clientFor(conn);
      if (!client.fake() && !hasTopic) continue;
      try {
        String at = accessToken(conn, client);
        var regs = repo.registrations(conn.userId());
        for (String courseId : repo.courseIds(conn.userId())) {
          var r = regs.stream().filter(x -> courseId.equals(x.gcCourseId())).findFirst().orElse(null);
          if (r != null && r.expiresAt().toEpochMilli() > System.currentTimeMillis() + 2 * 86_400_000L) continue;
          Registration reg = client.registerCourseWorkPush(at, courseId, hasTopic ? topic : "fake-topic");
          if (r != null) repo.updateRegistration(r.id(), reg.registrationId(), reg.expiresAt());
          else repo.insertRegistration(conn.userId(), "COURSE_WORK_CHANGES", courseId, reg.registrationId(), reg.expiresAt());
        }
      } catch (RuntimeException err) {
        markError(conn, err);
      }
    }
  }

  /** Pub/Sub push endpoint: verify the token, then queue the message for consumption. */
  public void handlePubsub(String token, PubSubMessage message) {
    String expected = integrations.get("google").get("pubsub_verification_token");
    boolean reject = expected != null && !expected.isEmpty() ? !expected.equals(token) : props.isProd();
    if (reject) throw new ApiException(ErrorCode.UNAUTHORIZED);
    jobs.enqueue(JobNames.CLASSROOM_PUBSUB_CONSUME, message);
  }

  private static byte[] b64(String s) {
    try {
      return Base64.getDecoder().decode(s);
    } catch (IllegalArgumentException e) {
      return Base64.getMimeDecoder().decode(s.replace('-', '+').replace('_', '/'));
    }
  }

  /** Map registration → user/course and enqueue a targeted incremental sync (bucketed per 30 s). */
  public void consumePubsub(PubSubMessage message) {
    JsonNode payload;
    try {
      payload = message.data() != null && !message.data().isEmpty() ? json.readTree(new String(b64(message.data()), StandardCharsets.UTF_8)) : json.createObjectNode();
    } catch (RuntimeException e) {
      return;
    }
    String registrationId = message.attributes() != null && message.attributes().get("registrationId") != null ? message.attributes().get("registrationId")
        : payload.hasNonNull("registrationId") ? payload.get("registrationId").asString() : null;
    if (registrationId == null) return;
    var reg = repo.registration(registrationId).orElse(null);
    if (reg == null) return;
    JsonNode cid = payload.path("resourceId").path("courseId");
    String courseId = cid.isString() && !cid.asString().isEmpty() ? cid.asString() : reg.gcCourseId();
    jobs.enqueue(JobNames.CLASSROOM_INCREMENTAL_SYNC, new SyncJob(reg.userId(), "push", courseId),
        EnqueueOptions.jobId("push-" + reg.userId() + "-" + (System.currentTimeMillis() / 30_000)));
  }

  /** Fallback polling: users active in the last 7 days whose last sync is older than 15 min. */
  public int pollActive() {
    long nowMs = System.currentTimeMillis();
    var users = repo.usersToPoll(Instant.ofEpochMilli(nowMs - 15 * 60_000L), Instant.ofEpochMilli(nowMs - 7 * 86_400_000L), Instant.ofEpochMilli(nowMs));
    for (UUID u : users) jobs.enqueue(JobNames.CLASSROOM_INCREMENTAL_SYNC, new SyncJob(u, "poll", null), EnqueueOptions.jobId("poll-" + u + "-" + (nowMs / 900_000)));
    return users.size();
  }
}
