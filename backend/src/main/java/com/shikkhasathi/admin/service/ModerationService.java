package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.repository.AdminContentRepository;
import com.shikkhasathi.admin.repository.AdminUserRepository;
import com.shikkhasathi.admin.repository.ReportRepository;
import com.shikkhasathi.admin.repository.ReportRepository.ReportRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Text;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Reports (course, review, question, answer, user) and the moderation queue: hide / restore / dismiss / reviewing. */
@Service
public class ModerationService {
  static final String GONE = "রিপোর্ট করা বিষয়টি পাওয়া যায়নি।";

  /** Resolved target: preview text, link, hidden flag. */
  record Target(String preview, String link, boolean hidden) {}

  private final ReportRepository reports;
  private final AdminContentRepository content;
  private final AdminUserRepository users;
  private final AdminUserService userService;
  private final AuditService audit;

  public ModerationService(ReportRepository reports, AdminContentRepository content, AdminUserRepository users, AdminUserService userService, AuditService audit) {
    this.reports = reports;
    this.content = content;
    this.users = users;
    this.userService = userService;
    this.audit = audit;
  }

  private static String key(String type, UUID id) {
    return type + ":" + id;
  }

  /** Resolve targets across both databases in one batch per table; missing targets are absent from the map. */
  Map<String, Target> targets(Collection<Map.Entry<String, UUID>> refs) {
    Map<String, Set<UUID>> byType = new HashMap<>();
    for (var r : refs) byType.computeIfAbsent(r.getKey(), k -> new HashSet<>()).add(r.getValue());
    Map<String, Target> out = new HashMap<>();
    var courseIds = byType.getOrDefault("course", Set.of());
    if (!courseIds.isEmpty())
      content.courses(courseIds).forEach((id, c) -> out.put(key("course", id), new Target(c.title(), "/c/" + c.slug(), "unlisted".equals(c.status()))));
    var reviewIds = byType.getOrDefault("review", Set.of());
    if (!reviewIds.isEmpty()) {
      var rv = reports.reviews(reviewIds);
      var cs = content.courses(rv.values().stream().map(ReportRepository.ReviewTarget::courseId).toList());
      rv.forEach((id, r) -> {
        var c = cs.get(r.courseId());
        String preview = ("★".repeat(r.rating()) + " " + (r.body() != null ? r.body() : "")).trim();
        out.put(key("review", id), new Target(preview, c != null ? "/c/" + c.slug() + "#reviews" : null, "hidden".equals(r.status())));
      });
    }
    var qIds = byType.getOrDefault("question", Set.of());
    if (!qIds.isEmpty())
      reports.questions(qIds).forEach((id, q) -> out.put(key("question", id), new Target(q.body(), "/learn/" + q.courseId() + "/" + q.lessonId() + "?tab=qa", "hidden".equals(q.status()))));
    var aIds = byType.getOrDefault("answer", Set.of());
    if (!aIds.isEmpty()) reports.answers(aIds).forEach((id, a) -> out.put(key("answer", id), new Target(a.text(), null, "hidden".equals(a.status()))));
    Set<UUID> uIds = new HashSet<>();
    byType.forEach((type, ids) -> {
      if (!Set.of("course", "review", "question", "answer").contains(type)) uIds.addAll(ids);
    });
    if (!uIds.isEmpty()) {
      var us = reports.users(uIds);
      byType.forEach((type, ids) -> {
        if (Set.of("course", "review", "question", "answer").contains(type)) return;
        for (UUID id : ids) {
          var u = us.get(id);
          if (u != null) out.put(key(type, id), new Target(u.text(), null, "suspended".equals(u.status())));
        }
      });
    }
    return out;
  }

  private Target target(String type, UUID id) {
    var t = targets(List.of(Map.entry(type, id))).get(key(type, id));
    if (t == null) throw ApiException.notFound(GONE);
    return t;
  }

  public ReportCreated create(UUID userId, CreateReportBody b) {
    target(b.targetType(), b.targetId()); // 404 when the target doesn't exist
    var dup = reports.openDuplicate(userId, b.targetType(), b.targetId());
    if (dup.isPresent()) return new ReportCreated(dup.get(), true);
    return new ReportCreated(reports.insert(userId, b.targetType(), b.targetId(), b.reason(), b.details()), false);
  }

  List<Report> toReports(List<ReportRow> rows) {
    if (rows.isEmpty()) return List.of();
    var names = users.names(rows.stream().map(ReportRow::reporterId).toList());
    var same = reports.sameTargetCounts(rows);
    var tg = targets(rows.stream().map(r -> Map.entry(r.targetType(), r.targetId())).toList());
    return rows.stream().map(r -> {
      var t = tg.getOrDefault(key(r.targetType(), r.targetId()), new Target("(মুছে ফেলা হয়েছে)", null, true));
      String preview = t.preview() == null ? "" : t.preview();
      return new Report(r.id(), r.targetType(), r.targetId(), preview.length() > 280 ? preview.substring(0, 280) : preview, t.link(), t.hidden(), r.reason(), r.details(),
          Text.shortName(names.getOrDefault(r.reporterId(), "")), r.status(), r.resolution(), JsonConfig.iso(r.createdAt()), same.getOrDefault(key(r.targetType(), r.targetId()), 1L));
    }).toList();
  }

  public Page<Report> adminList(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = reports.byStatus(status, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(toReports(pg.items()), pg.nextCursor());
  }

  private void setHidden(String type, UUID id, boolean hidden, UUID actorId) {
    switch (type) {
      case "review" -> reports.setReviewStatus(id, hidden ? "hidden" : "visible").ifPresent(courseId -> {
        var x = reports.visibleRating(courseId);
        content.setRating(courseId, x.avg() != null ? x.avg() : "0", x.count());
      });
      case "question" -> reports.setQuestionStatus(id, hidden ? "hidden" : "open");
      case "answer" -> reports.setAnswerStatus(id, hidden ? "hidden" : "visible");
      case "course" -> {
        var c = content.courses(List.of(id)).get(id);
        if (c != null && (hidden ? "published".equals(c.status()) : "unlisted".equals(c.status()))) content.setCourseStatus(id, hidden ? "unlisted" : "published");
      }
      case "user" -> userService.update(actorId, id, hidden ? "suspended" : "active", null, "moderation");
      default -> {}
    }
  }

  public Report resolve(UUID actorId, UUID id, ResolveReportBody d) {
    var r = reports.byId(id).orElseThrow(ApiException::notFound);
    if ("hide".equals(d.action()) || "restore".equals(d.action())) setHidden(r.targetType(), r.targetId(), "hide".equals(d.action()), actorId);
    String status = "reviewing".equals(d.action()) ? "reviewing" : "dismiss".equals(d.action()) ? "dismissed" : "resolved";
    // resolving one report resolves every open report on the same target
    String resolution = d.resolution() != null ? d.resolution() : "hide".equals(d.action()) ? "hidden" : "restore".equals(d.action()) ? "restored" : null;
    reports.resolve(r, status, actorId, resolution, !"reviewing".equals(d.action()));
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("status", status);
    if (d.resolution() != null) after.put("resolution", d.resolution());
    audit.log(AuditService.Entry.of(actorId, "report." + d.action(), r.targetType(), r.targetId()).before(Map.of("status", r.status())).after(after));
    return toReports(List.of(reports.byId(id).orElseThrow())).get(0);
  }
}
