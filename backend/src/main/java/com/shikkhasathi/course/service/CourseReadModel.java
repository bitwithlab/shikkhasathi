package com.shikkhasathi.course.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.course.dto.CourseDtos.*;
import com.shikkhasathi.course.repository.CourseAuthoringRepository;
import com.shikkhasathi.course.repository.CourseAuthoringRepository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * The editable course (SellerCourse contract): basics, pricing, targets, curriculum tree and documents. When a
 * published course has a pending revision, the seller sees the revision values (what they are editing); the public
 * and the admin see the live course.
 */
@Service
public class CourseReadModel {
  private final CourseAuthoringRepository repo;
  private final AcademicLookup academic;

  public CourseReadModel(CourseAuthoringRepository repo, AcademicLookup academic) {
    this.repo = repo;
    this.academic = academic;
  }

  static DocumentInfo toDoc(DocumentRow d) {
    return new DocumentInfo(d.id(), d.kind(), d.title(), d.year(), d.mime(), d.size(), d.externalUrl(), d.storageKey() != null && !d.storageKey().isEmpty());
  }

  static VideoInfo toVid(VideoRow v) {
    return new VideoInfo(v.id(), v.provider(), v.status(), v.durationSec(), v.title(), v.externalUrl());
  }

  /** SellerCourse as an ordered map (so the revision overlay and the admin extension can add/replace keys). */
  public Map<String, Object> load(CourseRow c) {
    List<TargetRow> targets = repo.targets(c.id());
    List<SectionRow> secs = repo.sections(c.id());
    List<ChapterRow> chs = repo.chapters(c.id());
    List<LessonRow> ls = repo.lessons(c.id());
    List<DocumentRow> docs = repo.documentsOfCourse(c.id());
    List<RevisionRow> revs = repo.pendingRevisions(c.id());

    Set<UUID> videoIds = ls.stream().map(LessonRow::videoAssetId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, VideoRow> vids = repo.videos(videoIds).stream().collect(Collectors.toMap(VideoRow::id, Function.identity()));
    Map<UUID, DocumentRow> docById = docs.stream().collect(Collectors.toMap(DocumentRow::id, Function.identity(), (a, b) -> a));
    var names = academic.lookup(targets.stream().map(TargetRow::institutionId).filter(Objects::nonNull).toList(),
        targets.stream().map(TargetRow::departmentId).filter(Objects::nonNull).toList(), List.of());
    Map<UUID, String> subjects = repo.subjectNames(targets.stream().map(TargetRow::subjectMasterId).filter(Objects::nonNull).distinct().toList());

    List<TargetDto> targetDtos = targets.stream().map(t -> new TargetDto(t.id(), t.isGeneral(), t.institutionId(), t.departmentId(), t.programId(), t.academicLevel(),
        t.semester(), t.subjectMasterId(), t.courseCode(), t.batches(), t.sections(),
        t.institutionId() != null && names.institution().get(t.institutionId()) != null ? names.institution().get(t.institutionId()).shortName() : null,
        t.departmentId() != null && names.department().get(t.departmentId()) != null ? names.department().get(t.departmentId()).shortName() : null,
        t.subjectMasterId() != null ? subjects.get(t.subjectMasterId()) : null)).toList();

    List<SectionDto> sections = secs.stream().map(s -> new SectionDto(s.id(), s.title(), chs.stream()
        .filter(ch -> ch.sectionId().equals(s.id()))
        .map(ch -> new ChapterDto(ch.id(), ch.title(), ls.stream()
            .filter(l -> l.chapterId().equals(ch.id()))
            .map(l -> {
              VideoRow v = l.videoAssetId() == null ? null : vids.get(l.videoAssetId());
              DocumentRow d = l.documentId() == null ? null : docById.get(l.documentId());
              return new LessonDto(l.id(), l.title(), l.type(), l.durationSec(), l.videoAssetId(), l.documentId(), l.body(), l.isPreview(), l.isImportant(),
                  v == null ? null : toVid(v), d == null ? null : toDoc(d));
            })
            .toList()))
        .toList())).toList();

    JsonNode rev = revs.isEmpty() ? null : revs.get(0).payload();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.id());
    m.put("slug", c.slug());
    m.put("status", c.status());
    m.put("reviewNote", c.reviewNote());
    m.put("title", c.title());
    m.put("shortDescription", c.shortDescription());
    m.put("description", c.description());
    m.put("learnings", c.learnings());
    m.put("notFor", c.notFor());
    m.put("faq", c.faq());
    m.put("coverUrl", c.coverUrl());
    m.put("coverColor", c.coverColor());
    m.put("introVideoId", c.introVideoId());
    m.put("language", c.language());
    m.put("examType", c.examType());
    m.put("courseType", c.courseType());
    m.put("price", c.price());
    m.put("compareAtPrice", c.compareAtPrice());
    m.put("accessType", c.accessType());
    m.put("accessDays", c.accessDays());
    m.put("accessUntil", JsonConfig.iso(c.accessUntil()));
    m.put("targets", targetDtos);
    m.put("sections", sections);
    m.put("documents", docs.stream().map(CourseReadModel::toDoc).toList());
    m.put("lessonCount", c.lessonCount());
    m.put("totalDurationSec", c.totalDurationSec());
    m.put("updatedAt", JsonConfig.iso(c.updatedAt()));
    m.put("publishedAt", JsonConfig.iso(c.publishedAt()));
    m.put("submittedAt", JsonConfig.iso(c.submittedAt()));
    m.put("pendingRevision", rev != null);
    if (rev != null && rev.isObject()) {
      for (var e : rev.properties()) if (!e.getKey().equals("targets")) m.put(e.getKey(), e.getValue());
    }
    return m;
  }

  /** All lessons of a loaded course, in tree order. */
  @SuppressWarnings("unchecked")
  public static List<LessonDto> lessonsOf(Map<String, Object> course) {
    return ((List<SectionDto>) course.get("sections")).stream().flatMap(s -> s.chapters().stream()).flatMap(ch -> ch.lessons().stream()).toList();
  }
}
