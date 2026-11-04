package com.shikkhasathi.learning.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CONTENT reads used by the learning module (courses, curriculum, documents, video assets, targets). */
@Repository
public class LearningContentRepository {
  /** Lesson types that count toward course progress. */
  public static final List<String> COUNTABLE = List.of("video", "pdf", "text");

  public record CourseRow(UUID id, UUID sellerId, String title, String slug, String status, String examType, String coverColor) {}

  public record LessonRow(UUID id, UUID chapterId, UUID courseId, String title, String type, int position, int durationSec, UUID videoAssetId,
                          UUID documentId, String body, boolean isPreview, boolean isImportant) {}

  public record SectionRow(UUID id, String title) {}

  public record ChapterRow(UUID id, UUID sectionId, String title) {}

  public record DocumentRow(UUID id, UUID courseId, String kind, String title, Integer year, String storageBucket, String storageKey, String externalUrl, Integer pages) {}

  public record VideoRow(UUID id, String provider, String providerVideoId, String externalUrl, String status) {}

  public record TargetRow(UUID courseId, UUID institutionId, String code) {}

  public record LessonTitle(UUID id, String title, UUID courseId) {}

  public record FirstLesson(UUID id, String title) {}

  private static final String COURSE_COLS = "id, seller_id, title, slug, status, exam_type, cover_color";
  private static final String LESSON_COLS = "id, chapter_id, course_id, title, type, position, duration_sec, video_asset_id, document_id, body, is_preview, is_important";

  private final NamedParameterJdbcTemplate db;

  public LearningContentRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  private static UUID uuid(ResultSet rs, String col) throws SQLException {
    return rs.getObject(col, UUID.class);
  }

  private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  private static CourseRow course(ResultSet rs, int i) throws SQLException {
    return new CourseRow(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("title"), rs.getString("slug"), rs.getString("status"), rs.getString("exam_type"), rs.getString("cover_color"));
  }

  private static LessonRow lesson(ResultSet rs, int i) throws SQLException {
    return new LessonRow(uuid(rs, "id"), uuid(rs, "chapter_id"), uuid(rs, "course_id"), rs.getString("title"), rs.getString("type"), rs.getInt("position"),
        rs.getInt("duration_sec"), uuid(rs, "video_asset_id"), uuid(rs, "document_id"), rs.getString("body"), rs.getBoolean("is_preview"), rs.getBoolean("is_important"));
  }

  private static DocumentRow document(ResultSet rs, int i) throws SQLException {
    return new DocumentRow(uuid(rs, "id"), uuid(rs, "course_id"), rs.getString("kind"), rs.getString("title"), intOrNull(rs, "year"), rs.getString("storage_bucket"),
        rs.getString("storage_key"), rs.getString("external_url"), intOrNull(rs, "pages"));
  }

  /** A course that is not soft-deleted. */
  public Optional<CourseRow> liveCourse(UUID id) {
    return db.query("select " + COURSE_COLS + " from courses where id = :id and deleted_at is null", Map.of("id", id), LearningContentRepository::course).stream().findFirst();
  }

  public List<CourseRow> courses(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select " + COURSE_COLS + " from courses where id in (:ids)", Map.of("ids", ids), LearningContentRepository::course);
  }

  public Map<UUID, String> courseTitles(Collection<UUID> ids) {
    var out = new HashMap<UUID, String>();
    if (ids.isEmpty()) return out;
    db.query("select id, title from courses where id in (:ids)", Map.of("ids", ids), rs -> {
      out.put(uuid(rs, "id"), rs.getString("title"));
    });
    return out;
  }

  public Optional<LessonRow> lesson(UUID id) {
    return db.query("select " + LESSON_COLS + " from lessons where id = :id", Map.of("id", id), LearningContentRepository::lesson).stream().findFirst();
  }

  public List<LessonRow> lessonsOf(UUID courseId) {
    return db.query("select " + LESSON_COLS + " from lessons where course_id = :c order by position", Map.of("c", courseId), LearningContentRepository::lesson);
  }

  public List<SectionRow> sectionsOf(UUID courseId) {
    return db.query("select id, title from course_sections where course_id = :c order by position", Map.of("c", courseId), (rs, i) -> new SectionRow(uuid(rs, "id"), rs.getString("title")));
  }

  public List<ChapterRow> chaptersOf(UUID courseId) {
    return db.query("select id, section_id, title from course_chapters where course_id = :c order by position", Map.of("c", courseId),
        (rs, i) -> new ChapterRow(uuid(rs, "id"), uuid(rs, "section_id"), rs.getString("title")));
  }

  public List<LessonTitle> lessonTitles(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select id, title, course_id from lessons where id in (:ids)", Map.of("ids", ids), (rs, i) -> new LessonTitle(uuid(rs, "id"), rs.getString("title"), uuid(rs, "course_id")));
  }

  public int countableLessons(UUID courseId) {
    Integer n = db.queryForObject("select count(*)::int from lessons where course_id = :c and type in (:t)", new MapSqlParameterSource("c", courseId).addValue("t", COUNTABLE), Integer.class);
    return n == null ? 0 : n;
  }

  public Map<UUID, Integer> countableLessons(Collection<UUID> courseIds) {
    var out = new HashMap<UUID, Integer>();
    if (courseIds.isEmpty()) return out;
    db.query("select course_id, count(*)::int as n from lessons where course_id in (:ids) and type in (:t) group by course_id",
        new MapSqlParameterSource("ids", courseIds).addValue("t", COUNTABLE), rs -> {
          out.put(uuid(rs, "course_id"), rs.getInt("n"));
        });
    return out;
  }

  /** First lesson of each course in curriculum order (section → chapter → lesson position). */
  public Map<UUID, FirstLesson> firstLessons(Collection<UUID> courseIds) {
    var out = new HashMap<UUID, FirstLesson>();
    if (courseIds.isEmpty()) return out;
    db.query("""
        select l.course_id, l.id, l.title from lessons l
        join course_chapters ch on ch.id = l.chapter_id
        join course_sections s on s.id = ch.section_id
        where l.course_id in (:ids)
        order by s.position, ch.position, l.position""", Map.of("ids", courseIds), rs -> {
      out.putIfAbsent(uuid(rs, "course_id"), new FirstLesson(uuid(rs, "id"), rs.getString("title")));
    });
    return out;
  }

  public List<TargetRow> targets(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select course_id, institution_id, course_code from course_targets where course_id in (:ids)", Map.of("ids", courseIds),
        (rs, i) -> new TargetRow(uuid(rs, "course_id"), uuid(rs, "institution_id"), rs.getString("course_code")));
  }

  public Optional<String> firstTargetCode(UUID courseId) {
    var rows = db.query("select course_code from course_targets where course_id = :c limit 1", Map.of("c", courseId), (rs, i) -> Optional.ofNullable(rs.getString(1)));
    return rows.isEmpty() ? Optional.empty() : rows.get(0);
  }

  /** Course resources for the player (year desc, title). */
  public List<DocumentRow> documentsOf(UUID courseId) {
    return db.query("select * from documents where course_id = :c order by year desc, title", Map.of("c", courseId), LearningContentRepository::document);
  }

  public Optional<DocumentRow> document(UUID id) {
    return db.query("select * from documents where id = :id", Map.of("id", id), LearningContentRepository::document).stream().findFirst();
  }

  public Optional<VideoRow> video(UUID id) {
    return db.query("select id, provider, provider_video_id, external_url, status from video_assets where id = :id", Map.of("id", id),
        (rs, i) -> new VideoRow(uuid(rs, "id"), rs.getString("provider"), rs.getString("provider_video_id"), rs.getString("external_url"), rs.getString("status"))).stream().findFirst();
  }

  public void updateRating(UUID courseId, String avg, int count) {
    db.update("update courses set rating_avg = cast(:a as numeric), rating_count = :n where id = :id", new MapSqlParameterSource("a", avg).addValue("n", count).addValue("id", courseId));
  }
}
