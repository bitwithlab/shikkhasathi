package com.shikkhasathi.course.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.course.dto.CourseDtos.*;
import com.shikkhasathi.course.event.CoursePublishedEvent;
import com.shikkhasathi.course.repository.CourseAuthoringRepository;
import com.shikkhasathi.course.repository.CourseAuthoringRepository.*;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.teacher.repository.SellerProfileRepository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Admin course review: queue (new, revisions, spot checks), detail drawer and the publish / changes / reject decision. */
@Service
public class CourseReviewService {
  private final CourseAuthoringRepository repo;
  private final CourseReadModel read;
  private final CourseAuthoringService authoring;
  private final SellerProfileRepository profiles;
  private final AcademicLookup academic;
  private final AuditService audit;
  private final NotificationService notifications;
  private final JobQueue jobs;
  private final ApplicationEventPublisher events;

  public CourseReviewService(CourseAuthoringRepository repo, CourseReadModel read, CourseAuthoringService authoring, SellerProfileRepository profiles, AcademicLookup academic,
                             AuditService audit, NotificationService notifications, JobQueue jobs, ApplicationEventPublisher events) {
    this.repo = repo;
    this.read = read;
    this.authoring = authoring;
    this.profiles = profiles;
    this.academic = academic;
    this.audit = audit;
    this.notifications = notifications;
    this.jobs = jobs;
    this.events = events;
  }

  /** Batched: one targets query and one academic lookup for the whole page. */
  private List<ReviewItem> reviewItems(List<CourseRow> cs, Map<UUID, String> kinds) {
    var targets = repo.targetsOf(cs.stream().map(CourseRow::id).toList());
    var names = academic.lookup(targets.stream().map(TargetRow::institutionId).filter(Objects::nonNull).toList(),
        targets.stream().map(TargetRow::departmentId).filter(Objects::nonNull).toList(), List.of());
    Map<UUID, List<TargetRow>> byCourse = targets.stream().collect(Collectors.groupingBy(TargetRow::courseId, LinkedHashMap::new, Collectors.toList()));
    return cs.stream().map(c -> new ReviewItem(c.id(), c.title(), c.sellerId(), c.sellerName(), c.sellerVerified(), c.status(), JsonConfig.iso(c.submittedAt()), c.price(),
        c.lessonCount(), byCourse.getOrDefault(c.id(), List.of()).stream().map(t -> {
          if (t.isGeneral()) return "General";
          List<String> parts = new ArrayList<>();
          var inst = t.institutionId() == null ? null : names.institution().get(t.institutionId());
          var dept = t.departmentId() == null ? null : names.department().get(t.departmentId());
          if (inst != null && inst.shortName() != null && !inst.shortName().isEmpty()) parts.add(inst.shortName());
          if (dept != null && dept.shortName() != null && !dept.shortName().isEmpty()) parts.add(dept.shortName());
          if (t.courseCode() != null && !t.courseCode().isEmpty()) parts.add(t.courseCode());
          return String.join(" · ", parts);
        }).toList(), kinds.get(c.id()))).toList();
  }

  public ReviewPage queue(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    Instant before = null;
    if (cur != null) {
      try {
        before = Instant.parse(cur.sortString());
      } catch (RuntimeException e) {
        throw ApiException.badRequest("অবৈধ cursor");
      }
    }
    Map<UUID, String> kinds = new HashMap<>();
    List<CourseRow> rows;
    if (status.equals("in_review")) {
      Set<UUID> revCourseIds = repo.coursesWithPendingRevision();
      rows = repo.reviewQueue(revCourseIds, before, limit + 1);
      for (CourseRow c : rows) kinds.put(c.id(), c.status().equals("in_review") ? "new" : revCourseIds.contains(c.id()) ? "revision" : "spot_check");
    } else {
      rows = repo.coursesByStatus(status, before, limit + 1);
      for (CourseRow c : rows) kinds.put(c.id(), "new");
    }
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.updatedAt()), r.id().toString()));
    return new ReviewPage(reviewItems(pg.items(), kinds), pg.nextCursor());
  }

  public Map<String, Object> detail(UUID id) {
    CourseRow c = repo.course(id).orElseThrow(ApiException::notFound);
    var revs = repo.pendingRevisions(id);
    // Admin reviews the live values; the revision is shown separately as a diff.
    Map<String, Object> live = read.load(c);
    if (!revs.isEmpty()) {
      live.put("title", c.title());
      live.put("price", c.price());
      live.put("compareAtPrice", c.compareAtPrice());
    }
    live.put("sellerName", c.sellerName());
    live.put("revision", revs.isEmpty() ? null : revs.get(0).payload());
    return live;
  }

  /** a revision field value → the Java value {@link CourseAuthoringRepository#updateCourse} expects */
  private static Object fieldValue(String k, JsonNode v) {
    if (v == null || v.isNull()) return null;
    return switch (k) {
      case "price", "compareAtPrice" -> v.asLong();
      default -> v.asString();
    };
  }

  public ReviewItem decide(UUID actorId, UUID id, ReviewDecision d) {
    CourseRow c = repo.course(id).orElseThrow(ApiException::notFound);
    var revs = repo.pendingRevisions(id);
    RevisionRow rev = revs.isEmpty() ? null : revs.get(0);
    if (d.decision().equals("publish") && !d.checklist().allChecked()) throw ApiException.badRequest("প্রকাশের আগে চেকলিস্টের সবগুলো নিশ্চিত করুন।");
    if (!d.decision().equals("publish") && (d.note() == null || d.note().isEmpty())) throw ApiException.badRequest("শিক্ষককে কী বদলাতে হবে, নোটে লিখুন।");
    boolean isRevision = c.status().equals("published") && rev != null;
    if (d.decision().equals("publish")) {
      var p = profiles.findById(c.sellerId()).orElse(null);
      if (rev != null) {
        JsonNode payload = rev.payload();
        if (payload != null && payload.has("targets")) authoring.replaceTargets(id, authoring.targetsFromJson(payload.get("targets")));
        Map<String, Object> fields = new LinkedHashMap<>();
        if (payload != null) for (var e : payload.properties()) if (!e.getKey().equals("targets")) fields.put(e.getKey(), fieldValue(e.getKey(), e.getValue()));
        if (!fields.isEmpty()) repo.updateCourse(id, fields);
        repo.closeRevision(rev.id(), "approved", actorId, d.note());
      }
      Map<String, Object> set = new LinkedHashMap<>();
      set.put("status", "published");
      set.put("publishedAt", c.publishedAt() != null ? c.publishedAt() : Instant.now());
      set.put("reviewNote", d.note());
      set.put("needsSpotReview", false);
      set.put("sellerVerified", p != null && "verified".equals(p.getVerificationStatus()));
      set.put("sellerName", p != null ? p.getDisplayName() : c.sellerName());
      repo.updateCourse(id, set);
      jobs.enqueue(JobNames.SEARCH_INDEX_COURSE, Map.of("courseId", id));
      events.publishEvent(new CoursePublishedEvent(id));
    } else if (isRevision) {
      repo.closeRevision(rev.id(), "rejected", actorId, d.note());
      Map<String, Object> set = new LinkedHashMap<>();
      set.put("reviewNote", d.note());
      repo.updateCourse(id, set);
    } else if (c.status().equals("published")) {
      // spot-check failure: take it down until fixed
      Map<String, Object> set = new LinkedHashMap<>();
      set.put("status", d.decision().equals("reject") ? "rejected" : "changes_requested");
      set.put("reviewNote", d.note());
      set.put("needsSpotReview", false);
      repo.updateCourse(id, set);
      jobs.enqueue(JobNames.SEARCH_INDEX_COURSE, Map.of("courseId", id));
    } else {
      Map<String, Object> set = new LinkedHashMap<>();
      set.put("status", d.decision().equals("reject") ? "rejected" : "changes_requested");
      set.put("reviewNote", d.note());
      repo.updateCourse(id, set);
    }
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("decision", d.decision());
    if (d.note() != null) after.put("note", d.note());
    after.put("checklist", d.checklist());
    after.put("revision", rev != null);
    audit.log(AuditService.Entry.of(actorId, "course.review." + d.decision(), "courses", id).before(Map.of("status", c.status())).after(after));
    String decision = d.decision().equals("publish") ? "published" : d.decision().equals("reject") ? "rejected" : "changes_requested";
    String title = switch (decision) {
      case "published" -> "কোর্স প্রকাশিত হয়েছে: " + c.title();
      case "changes_requested" -> "কিছু পরিবর্তন দরকার: " + c.title();
      default -> "কোর্সটি প্রকাশ করা যায়নি: " + c.title();
    };
    String body = d.note() != null ? d.note() : decision.equals("published") ? "শিক্ষার্থীরা এখন কোর্সটি খুঁজে পাবে।" : "";
    notifications.notify(NotifyInput.of(c.sellerId(), NotificationTypes.SELLER_COURSE_DECISION, id + ":" + System.currentTimeMillis(), title, body)
        .withLink("/teach/courses/" + id + "/edit")
        .withEmail(EmailTemplate.COURSE_REVIEW_DECISION, Map.of("courseTitle", c.title(), "decision", decision, "note", d.note() != null ? d.note() : "")));
    CourseRow fresh = repo.course(id).orElseThrow(ApiException::notFound);
    return reviewItems(List.of(fresh), Map.of(id, isRevision ? "revision" : "new")).get(0);
  }
}
