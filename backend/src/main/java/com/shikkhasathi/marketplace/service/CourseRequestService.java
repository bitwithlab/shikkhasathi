package com.shikkhasathi.marketplace.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.course.event.CoursePublishedEvent;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.*;
import com.shikkhasathi.marketplace.repository.CourseRequestRepository;
import com.shikkhasathi.marketplace.repository.CourseRequestRepository.Row;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course request board (spec §6.11): ask, dedupe into upvotes, vote, teacher claim, notify on publish. */
@Service
public class CourseRequestService {
  private final CourseRequestRepository repo;
  private final AcademicLookup academic;
  private final IntegrationService integrations;
  private final NotificationService notifications;

  public CourseRequestService(CourseRequestRepository repo, AcademicLookup academic, IntegrationService integrations, NotificationService notifications) {
    this.repo = repo;
    this.academic = academic;
    this.integrations = integrations;
    this.notifications = notifications;
  }

  static String dedupeKey(UUID institutionId, String subject, String examType) {
    String s = subject.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0980-\\u09FF]+", "");
    return (institutionId == null ? "any" : institutionId.toString()) + "|" + s + "|" + examType;
  }

  private List<CourseRequestDto> hydrate(List<Row> rows, UUID userId) {
    if (rows.isEmpty()) return List.of();
    var ids = rows.stream().map(Row::id).toList();
    var names = academic.lookup(rows.stream().map(Row::institutionId).filter(Objects::nonNull).toList(), List.of(), List.of());
    var voted = userId == null ? new HashSet<UUID>() : new HashSet<>(repo.votedAmong(userId, ids));
    var claimers = repo.sellerNames(rows.stream().map(Row::claimedBy).filter(Objects::nonNull).toList());
    var linked = repo.courseSlugs(rows.stream().map(Row::linkedCourseId).filter(Objects::nonNull).toList());
    List<CourseRequestDto> out = new ArrayList<>();
    for (Row r : rows) {
      String instShort = r.institutionId() != null && names.institution().containsKey(r.institutionId()) ? names.institution().get(r.institutionId()).shortName() : null;
      out.add(new CourseRequestDto(r.id(), r.institutionId(), instShort, r.subjectMasterId(), r.subjectName(), r.courseCode(), r.examType(), r.note(), r.status(), r.voteCount(),
          voted.contains(r.id()), r.requesterId().equals(userId), r.claimedBy() == null ? null : claimers.get(r.claimedBy()),
          r.linkedCourseId() == null ? null : linked.get(r.linkedCourseId()), JsonConfig.iso(r.createdAt())));
    }
    return out;
  }

  private void ensureEnabled() {
    if (!integrations.businessFlag("feature_course_requests", true)) throw new ApiException(ErrorCode.INTEGRATION_DISABLED, "কোর্স অনুরোধ এখন বন্ধ আছে।");
  }

  private static int decodeOffset(String cursor) {
    if (cursor == null || cursor.isEmpty()) return 0;
    try {
      double n = Double.parseDouble(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).trim());
      return Double.isFinite(n) && n > 0 ? (int) Math.floor(n) : 0;
    } catch (RuntimeException e) {
      return 0;
    }
  }

  @Transactional(readOnly = true)
  public RequestPage list(ListRequestsQuery q, UUID userId) {
    int offset = decodeOffset(q.cursor());
    var rows = repo.list(q.status(), q.institutionId(), q.q(), q.sort(), q.limit() + 1, offset);
    boolean more = rows.size() > q.limit();
    var page = more ? rows.subList(0, q.limit()) : rows;
    return new RequestPage(hydrate(page, userId), more ? Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(offset + q.limit()).getBytes(StandardCharsets.UTF_8)) : null);
  }

  @Transactional
  public CreateResult create(UUID userId, CreateCourseRequest b) {
    ensureEnabled();
    String subjectName = b.subjectName();
    if (b.subjectId() != null) {
      String s = repo.subjectName(b.subjectId());
      if (s == null) throw ApiException.badRequest("সাবজেক্টটি পাওয়া যায়নি।");
      subjectName = s;
    }
    if (b.institutionId() != null && !repo.institutionExists(b.institutionId())) throw ApiException.badRequest("প্রতিষ্ঠানটি পাওয়া যায়নি।");
    String key = dedupeKey(b.institutionId(), subjectName, b.examType());
    Row existing = repo.findByDedupeKey(key);
    if (existing != null) {
      // Duplicate → count it as an upvote (spec §6.11 duplicate detection).
      vote(userId, existing.id(), true);
      return new CreateResult(hydrate(List.of(repo.find(existing.id())), userId).get(0), true);
    }
    UUID id = repo.insert(userId, b.institutionId(), b.subjectId(), subjectName, b.courseCode(), b.examType(), b.note(), key);
    repo.addVote(id, userId);
    return new CreateResult(hydrate(List.of(repo.find(id)), userId).get(0), false);
  }

  @Transactional
  public CourseRequestDto vote(UUID userId, UUID id) {
    return vote(userId, id, false);
  }

  CourseRequestDto vote(UUID userId, UUID id, boolean forceOn) {
    if (repo.find(id) == null) throw ApiException.notFound();
    UUID mine = repo.myVote(id, userId);
    if (mine != null && !forceOn) repo.deleteVote(mine);
    else if (mine == null) repo.addVote(id, userId);
    repo.recountVotes(id);
    return hydrate(List.of(repo.find(id)), userId).get(0);
  }

  @Transactional
  public CourseRequestDto claim(UUID sellerId, UUID id, UUID courseId) {
    Row r = repo.find(id);
    if (r == null) throw ApiException.notFound();
    if (r.claimedBy() != null && !r.claimedBy().equals(sellerId)) throw ApiException.forbidden("অন্য একজন শিক্ষক এটি বানাচ্ছেন।");
    if (courseId != null) {
      var c = repo.course(courseId);
      if (c == null || !c.sellerId().equals(sellerId)) throw ApiException.forbidden();
    }
    repo.claim(id, sellerId, "published".equals(r.status()) ? "published" : "teacher_working", courseId);
    if (courseId != null) onCoursePublished(courseId);
    return hydrate(List.of(repo.find(id)), sellerId).get(0);
  }

  /** A course was published (course review) — previously {@code onCoursePublished}. */
  @EventListener
  public void coursePublished(CoursePublishedEvent e) {
    onCoursePublished(e.courseId());
  }

  /** When a linked course is published, mark requests Published and notify everyone who asked/voted. */
  @Transactional
  public void onCoursePublished(UUID courseId) {
    var c = repo.course(courseId);
    if (c == null || !"published".equals(c.status())) return;
    for (Row r : repo.unpublishedLinkedTo(courseId)) {
      repo.markPublished(r.id());
      for (UUID v : repo.voters(r.id()))
        notifications.notify(NotifyInput.of(v, NotificationTypes.REQUEST_PUBLISHED, r.id().toString(), "আপনার অনুরোধ করা কোর্স এসেছে", c.title()).withLink("/c/" + c.slug()));
    }
  }
}
