package com.shikkhasathi.student.service;

import com.shikkhasathi.assignment.service.AssignmentService;
import com.shikkhasathi.classroom.service.ClassroomService;
import com.shikkhasathi.classroom.service.ClassroomText;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.student.dto.DashboardDtos.*;
import com.shikkhasathi.student.repository.ExamPlanQueries;
import com.shikkhasathi.user.service.MeService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Home dashboard aggregate (CLAUDE.md §0A), cached 60 s in Redis under {@code dash:<userId>}. Every widget resolves
 * independently — one failing never blanks the page. Continue-learning and recommendations come from
 * {@link DashboardWidgetProvider} beans of other modules (null / [] when none is registered).
 */
@Service
public class DashboardService {
  private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

  private final MeService me;
  private final NotificationService notifications;
  private final ClassroomService classroom;
  private final AssignmentService assignments;
  private final ExamPlanQueries exams;
  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Map<String, DashboardWidgetProvider> providers = new HashMap<>();

  public DashboardService(MeService me, NotificationService notifications, ClassroomService classroom, AssignmentService assignments, ExamPlanQueries exams,
                          StringRedisTemplate redis, JsonMapper json, ObjectProvider<DashboardWidgetProvider> widgetProviders) {
    this.me = me;
    this.notifications = notifications;
    this.classroom = classroom;
    this.assignments = assignments;
    this.exams = exams;
    this.redis = redis;
    this.json = json;
    for (var p : widgetProviders.orderedStream().toList()) {
      var prev = providers.putIfAbsent(p.widget(), p);
      if (prev != null) log.warn("duplicate dashboard provider for {}: {} ignored", p.widget(), p.getClass().getName());
    }
  }

  private <T> Section<T> section(String name, Supplier<T> fn) {
    try {
      return Section.ok(fn.get());
    } catch (RuntimeException err) {
      log.warn("dashboard widget failed: widget={} err={}", name, err.getMessage());
      return Section.failed();
    }
  }

  /** Soonest active exam plan from 12 h ago on, with days left (Asia/Dhaka calendar days). */
  public ExamWidget nearestExam(UUID userId, Instant now) {
    return exams.nearest(userId, now.minus(12, ChronoUnit.HOURS)).map(p -> {
      long days = Math.max(0, ChronoUnit.DAYS.between(ClassroomText.dhakaDay(now), ClassroomText.dhakaDay(p.examDate())));
      return new ExamWidget(p.id(), p.subjectName(), p.courseCode(), p.examType(), JsonConfig.iso(p.examDate()), days, p.readiness());
    }).orElse(null);
  }

  /** 7-day strip from today (Asia/Dhaka): deadlines and exams per day. */
  List<WeekDay> week(UUID userId, Instant now) {
    LocalDate today = ClassroomText.dhakaDay(now);
    Instant start = today.atStartOfDay(ClassroomText.DHAKA).toInstant();
    Instant end = start.plus(Duration.ofDays(7));
    var deadlines = classroom.deadlineCountsByDay(userId, start, end);
    var examDays = exams.activeBetween(userId, start, end, false).stream().map(e -> ClassroomText.dhakaDay(e.examDate())).toList();
    List<WeekDay> out = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      LocalDate day = today.plusDays(i);
      String key = day.toString();
      out.add(new WeekDay(key, deadlines.getOrDefault(key, 0L), examDays.stream().filter(day::equals).count()));
    }
    return out;
  }

  private Object provided(String widget, UUID userId, Object fallback) {
    var p = providers.get(widget);
    return p == null ? fallback : p.load(userId);
  }

  /** @return the cached JSON (unless {@code fresh}) or a freshly built {@link Dashboard} */
  public Object getDashboard(UUID userId, boolean fresh) {
    String key = DashboardCache.key(userId);
    if (!fresh) {
      try {
        String hit = redis.opsForValue().get(key);
        if (hit != null) return json.readTree(hit);
      } catch (RuntimeException ignored) {
        // cache miss on Redis failure
      }
    }
    Instant now = Instant.now();
    var meDto = me.getMe(userId);
    long unread = notifications.unreadCount(userId);
    var status = classroom.getStatus(userId);
    boolean connected = status.connected() && !"needs_reauth".equals(status.status());
    var dueSoon = section("dueSoon", () -> connected ? assignments.dueSoonGroups(userId, now) : null);
    var feed = section("feed", () -> {
      if (!connected) return null;
      var page = classroom.feed(userId, null, 8, null, false);
      return new FeedWidget(page.items(), classroom.unreadFeedCount(userId));
    });
    var exam = section("exam", () -> nearestExam(userId, now));
    var continueLearning = section(DashboardWidgetProvider.CONTINUE_LEARNING, () -> provided(DashboardWidgetProvider.CONTINUE_LEARNING, userId, null));
    var week = section("week", () -> week(userId, now));
    var recommendations = section(DashboardWidgetProvider.RECOMMENDATIONS, () -> provided(DashboardWidgetProvider.RECOMMENDATIONS, userId, List.of()));
    var out = new Dashboard(
        new Header(meDto.name(), meDto.academic().institutionShort(), meDto.academic().departmentShort(), meDto.academic().semester(), unread, status),
        dueSoon, feed, exam, continueLearning, week, recommendations, JsonConfig.iso(Instant.now()));
    try {
      redis.opsForValue().set(key, json.writeValueAsString(out), Duration.ofSeconds(DashboardCache.TTL_SEC));
    } catch (RuntimeException ignored) {
      // best effort
    }
    return out;
  }
}
