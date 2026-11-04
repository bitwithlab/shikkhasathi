package com.shikkhasathi.learning.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.learning.repository.LearningContentRepository;
import com.shikkhasathi.learning.repository.LearningContentRepository.ChapterRow;
import com.shikkhasathi.learning.repository.LearningContentRepository.CourseRow;
import com.shikkhasathi.learning.repository.LearningContentRepository.LessonRow;
import com.shikkhasathi.learning.repository.LearningContentRepository.SectionRow;
import com.shikkhasathi.learning.repository.LearningCoreRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository.EnrollmentRow;
import com.shikkhasathi.student.service.DashboardCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Course access checks, curriculum order and progress recomputation shared by the learning services. */
@Component
public class LearningAccess {
  public record Access(CourseRow course, EnrollmentRow enrollment, boolean enrolled, boolean isOwner, boolean isAdmin) {
    /** enrolled, owner or admin */
    public boolean canSeeAll() {
      return enrolled || isOwner || isAdmin;
    }
  }

  public record Chapter(ChapterRow chapter, List<LessonRow> lessons) {}

  public record Section(SectionRow section, List<Chapter> chapters) {}

  public record Curriculum(List<Section> tree, List<LessonRow> flat) {}

  private final LearningContentRepository content;
  private final LearningCoreRepository core;
  private final DashboardCache dashboard;

  public LearningAccess(LearningContentRepository content, LearningCoreRepository core, DashboardCache dashboard) {
    this.content = content;
    this.core = core;
    this.dashboard = dashboard;
  }

  public Access access(UUID userId, UUID courseId, List<String> roles) {
    CourseRow c = content.liveCourse(courseId).orElseThrow(() -> ApiException.notFound("কোর্সটি পাওয়া যায়নি।"));
    EnrollmentRow e = core.enrollment(userId, courseId).orElse(null);
    return new Access(c, e, e != null && e.active(), c.sellerId().equals(userId), roles != null && roles.contains("admin"));
  }

  public Access access(UUID userId, UUID courseId) {
    return access(userId, courseId, List.of());
  }

  public void requireLessonAccess(Access a, LessonRow l) {
    if (a.canSeeAll() || l.isPreview()) return;
    if (a.enrollment() != null && !a.enrolled()) throw ApiException.forbidden("কোর্সের মেয়াদ শেষ হয়ে গেছে।");
    throw ApiException.forbidden("এই লেসন দেখতে কোর্সটি কিনুন।");
  }

  public LessonRow lessonOr404(UUID id) {
    return content.lesson(id).orElseThrow(() -> ApiException.notFound("লেসনটি পাওয়া যায়নি।"));
  }

  /** Lesson + access check (no roles: owners and enrolled students only, plus previews). */
  public LessonRow lessonForWrite(UUID userId, UUID lessonId) {
    LessonRow l = lessonOr404(lessonId);
    requireLessonAccess(access(userId, l.courseId()), l);
    return l;
  }

  /** Curriculum in display order (section → chapter → lesson position). */
  public Curriculum curriculum(UUID courseId) {
    var secs = content.sectionsOf(courseId);
    var chs = content.chaptersOf(courseId);
    var ls = content.lessonsOf(courseId);
    var tree = new ArrayList<Section>();
    var flat = new ArrayList<LessonRow>();
    for (var s : secs) {
      var chapters = new ArrayList<Chapter>();
      for (var c : chs) {
        if (!c.sectionId().equals(s.id())) continue;
        var lessons = ls.stream().filter(l -> l.chapterId().equals(c.id())).toList();
        chapters.add(new Chapter(c, lessons));
        flat.addAll(lessons);
      }
      tree.add(new Section(s, chapters));
    }
    return new Curriculum(tree, flat);
  }

  /** Recompute the enrollment's progress percent (and last lesson), then drop the cached dashboard. */
  public int recomputeProgress(UUID studentId, UUID courseId, UUID lastLessonId) {
    int tot = content.countableLessons(courseId);
    int done = core.completedLessons(studentId, courseId);
    int pct = tot > 0 ? Math.min(100, (int) Math.round(((double) done / tot) * 100)) : 0;
    core.updateEnrollmentProgress(studentId, courseId, pct, lastLessonId);
    dashboard.invalidate(studentId);
    return pct;
  }

  public static boolean countable(LessonRow l) {
    return LearningContentRepository.COUNTABLE.contains(l.type());
  }
}
