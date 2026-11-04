package com.shikkhasathi.course.repository;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.course.dto.CourseDtos.TargetInput;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CONTENT-database queries for course authoring (courses, targets, curriculum, documents, video assets, revisions)
 * and the admin review queue. Every write also bumps {@code updated_at}, like the previous ORM did.
 */
@Repository
public class CourseAuthoringRepository {
  private final NamedParameterJdbcTemplate db;
  private final JsonMapper json;

  public CourseAuthoringRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  // ---------------------------------------------------------------- rows

  public record CourseRow(UUID id, UUID sellerId, String title, String slug, String shortDescription, String description, List<String> learnings, List<String> notFor,
                          JsonNode faq, String coverUrl, String coverColor, UUID introVideoId, String language, String examType, String courseType, long price,
                          Long compareAtPrice, String accessType, Integer accessDays, Instant accessUntil, String status, String reviewNote, Instant submittedAt,
                          Instant publishedAt, boolean needsSpotReview, BigDecimal ratingAvg, int ratingCount, int enrollmentCount, int totalDurationSec, int lessonCount,
                          boolean sellerVerified, String sellerName, Instant deletedAt, Instant updatedAt) {}

  public record TargetRow(UUID id, UUID courseId, boolean isGeneral, UUID institutionId, UUID departmentId, UUID programId, String academicLevel, Integer semester,
                          UUID subjectMasterId, String courseCode, List<String> batches, List<String> sections) {}

  public record SectionRow(UUID id, String title) {}

  public record ChapterRow(UUID id, UUID sectionId, String title) {}

  public record LessonRow(UUID id, UUID chapterId, String title, String type, int durationSec, UUID videoAssetId, UUID documentId, String body, boolean isPreview, boolean isImportant) {}

  public record DocumentRow(UUID id, UUID courseId, UUID ownerId, String kind, String title, Integer year, String storageKey, String externalUrl, String mime, Integer size) {}

  public record VideoRow(UUID id, UUID ownerId, String provider, String providerVideoId, String externalUrl, String title, String status, int durationSec) {}

  public record RevisionRow(UUID id, UUID courseId, JsonNode payload) {}

  static UUID uuid(ResultSet rs, String c) throws SQLException {
    return rs.getObject(c, UUID.class);
  }

  static Instant ts(ResultSet rs, String c) throws SQLException {
    Timestamp t = rs.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }

  static Integer intOrNull(ResultSet rs, String c) throws SQLException {
    int v = rs.getInt(c);
    return rs.wasNull() ? null : v;
  }

  static Long longOrNull(ResultSet rs, String c) throws SQLException {
    long v = rs.getLong(c);
    return rs.wasNull() ? null : v;
  }

  static List<String> strings(ResultSet rs, String c) throws SQLException {
    Array a = rs.getArray(c);
    if (a == null) return List.of();
    return List.of((String[]) a.getArray());
  }

  private final RowMapper<CourseRow> courseMapper = (rs, i) -> new CourseRow(
      uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("title"), rs.getString("slug"), rs.getString("short_description"), rs.getString("description"),
      strings(rs, "learnings"), strings(rs, "not_for"), readJson(rs.getString("faq")), rs.getString("cover_url"), rs.getString("cover_color"), uuid(rs, "intro_video_id"),
      rs.getString("language"), rs.getString("exam_type"), rs.getString("course_type"), rs.getLong("price"), longOrNull(rs, "compare_at_price"), rs.getString("access_type"),
      intOrNull(rs, "access_days"), ts(rs, "access_until"), rs.getString("status"), rs.getString("review_note"), ts(rs, "submitted_at"), ts(rs, "published_at"),
      rs.getBoolean("needs_spot_review"), rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"), rs.getInt("enrollment_count"), rs.getInt("total_duration_sec"),
      rs.getInt("lesson_count"), rs.getBoolean("seller_verified"), rs.getString("seller_name"), ts(rs, "deleted_at"), ts(rs, "updated_at"));

  private static final RowMapper<TargetRow> TARGET = (rs, i) -> new TargetRow(uuid(rs, "id"), uuid(rs, "course_id"), rs.getBoolean("is_general"), uuid(rs, "institution_id"),
      uuid(rs, "department_id"), uuid(rs, "program_id"), rs.getString("academic_level"), intOrNull(rs, "semester"), uuid(rs, "subject_master_id"), rs.getString("course_code"),
      strings(rs, "batches"), strings(rs, "sections"));

  private static final RowMapper<DocumentRow> DOC = (rs, i) -> new DocumentRow(uuid(rs, "id"), uuid(rs, "course_id"), uuid(rs, "owner_id"), rs.getString("kind"), rs.getString("title"),
      intOrNull(rs, "year"), rs.getString("storage_key"), rs.getString("external_url"), rs.getString("mime"), intOrNull(rs, "size"));

  private static final RowMapper<VideoRow> VIDEO = (rs, i) -> new VideoRow(uuid(rs, "id"), uuid(rs, "owner_id"), rs.getString("provider"), rs.getString("provider_video_id"),
      rs.getString("external_url"), rs.getString("title"), rs.getString("status"), rs.getInt("duration_sec"));

  private JsonNode readJson(String s) {
    return s == null ? null : json.readTree(s);
  }

  private static Map<String, Object> p(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  // ---------------------------------------------------------------- courses

  public Optional<CourseRow> course(UUID id) {
    return db.query("select * from courses where id = :id", p("id", id), courseMapper).stream().findFirst();
  }

  public Optional<CourseRow> liveCourse(UUID id) {
    return db.query("select * from courses where id = :id and deleted_at is null", p("id", id), courseMapper).stream().findFirst();
  }

  public List<CourseRow> coursesOfSeller(UUID sellerId) {
    return db.query("select * from courses where seller_id = :s and deleted_at is null order by updated_at desc", p("s", sellerId), courseMapper);
  }

  /** all courses of the seller, deleted included */
  public int countBySeller(UUID sellerId) {
    Integer n = db.queryForObject("select count(*) from courses where seller_id = :s", p("s", sellerId), Integer.class);
    return n == null ? 0 : n;
  }

  /** seller id → number of courses (deleted included) */
  public Map<UUID, Integer> countBySellers(Collection<UUID> sellerIds) {
    Map<UUID, Integer> out = new HashMap<>();
    if (sellerIds.isEmpty()) return out;
    db.query("select seller_id, count(*) as n from courses where seller_id in (:ids) group by seller_id", p("ids", sellerIds),
        rs -> {
          out.put(uuid(rs, "seller_id"), rs.getInt("n"));
        });
    return out;
  }

  public UUID insertCourse(UUID sellerId, String title, String slug, String sellerName, boolean sellerVerified, String coverColor) {
    UUID id = Ids.uuidv7();
    db.update("insert into courses (id, seller_id, title, slug, seller_name, seller_verified, cover_color) values (:id, :s, :t, :slug, :n, :v, :c)",
        p("id", id, "s", sellerId, "t", title, "slug", slug, "n", sellerName, "v", sellerVerified, "c", coverColor));
    return id;
  }

  /** Column kinds for dynamic course updates (camelCase API field → column). */
  public enum Kind { TEXT, TEXT_ARRAY, JSONB, UUID, LONG, INT, BOOL, TIMESTAMP }

  public static final Map<String, Map.Entry<String, Kind>> COURSE_COLUMNS = Map.ofEntries(
      Map.entry("title", Map.entry("title", Kind.TEXT)),
      Map.entry("shortDescription", Map.entry("short_description", Kind.TEXT)),
      Map.entry("description", Map.entry("description", Kind.TEXT)),
      Map.entry("learnings", Map.entry("learnings", Kind.TEXT_ARRAY)),
      Map.entry("notFor", Map.entry("not_for", Kind.TEXT_ARRAY)),
      Map.entry("faq", Map.entry("faq", Kind.JSONB)),
      Map.entry("coverUrl", Map.entry("cover_url", Kind.TEXT)),
      Map.entry("coverColor", Map.entry("cover_color", Kind.TEXT)),
      Map.entry("introVideoId", Map.entry("intro_video_id", Kind.UUID)),
      Map.entry("language", Map.entry("language", Kind.TEXT)),
      Map.entry("examType", Map.entry("exam_type", Kind.TEXT)),
      Map.entry("courseType", Map.entry("course_type", Kind.TEXT)),
      Map.entry("price", Map.entry("price", Kind.LONG)),
      Map.entry("compareAtPrice", Map.entry("compare_at_price", Kind.LONG)),
      Map.entry("accessType", Map.entry("access_type", Kind.TEXT)),
      Map.entry("accessDays", Map.entry("access_days", Kind.INT)),
      Map.entry("accessUntil", Map.entry("access_until", Kind.TIMESTAMP)),
      Map.entry("status", Map.entry("status", Kind.TEXT)),
      Map.entry("reviewNote", Map.entry("review_note", Kind.TEXT)),
      Map.entry("needsSpotReview", Map.entry("needs_spot_review", Kind.BOOL)),
      Map.entry("contentUpdatedAt", Map.entry("content_updated_at", Kind.TIMESTAMP)),
      Map.entry("submittedAt", Map.entry("submitted_at", Kind.TIMESTAMP)),
      Map.entry("publishedAt", Map.entry("published_at", Kind.TIMESTAMP)),
      Map.entry("sellerVerified", Map.entry("seller_verified", Kind.BOOL)),
      Map.entry("sellerName", Map.entry("seller_name", Kind.TEXT)),
      Map.entry("totalDurationSec", Map.entry("total_duration_sec", Kind.INT)),
      Map.entry("lessonCount", Map.entry("lesson_count", Kind.INT)),
      Map.entry("deletedAt", Map.entry("deleted_at", Kind.TIMESTAMP)));

  /**
   * {@code update courses set <fields>, updated_at = now() where id = :id}. Values by kind: TEXT String,
   * TEXT_ARRAY List&lt;String&gt;, JSONB JsonNode/Object, UUID, LONG/INT Number, BOOL, TIMESTAMP Instant (all nullable).
   */
  public void updateCourse(UUID id, Map<String, Object> fields) {
    if (fields.isEmpty()) return;
    var sets = new ArrayList<String>();
    var params = new MapSqlParameterSource().addValue("id", id);
    int n = 0;
    for (var e : fields.entrySet()) {
      var col = COURSE_COLUMNS.get(e.getKey());
      if (col == null) throw new IllegalArgumentException("unknown course field " + e.getKey());
      String pn = "v" + n++;
      Object v = e.getValue();
      switch (col.getValue()) {
        case TEXT_ARRAY -> {
          sets.add(col.getKey() + " = cast(:" + pn + " as text[])");
          params.addValue(pn, v == null ? null : ((List<?>) v).stream().map(String::valueOf).toArray(String[]::new));
        }
        case JSONB -> {
          sets.add(col.getKey() + " = cast(:" + pn + " as jsonb)");
          params.addValue(pn, v == null ? null : json.writeValueAsString(v));
        }
        case TIMESTAMP -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v == null ? null : ((Instant) v).atOffset(java.time.ZoneOffset.UTC), java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        }
        case LONG -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v == null ? null : ((Number) v).longValue(), java.sql.Types.BIGINT);
        }
        case INT -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v == null ? null : ((Number) v).intValue(), java.sql.Types.INTEGER);
        }
        case UUID -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v, java.sql.Types.OTHER);
        }
        case BOOL -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v, java.sql.Types.BOOLEAN);
        }
        default -> {
          sets.add(col.getKey() + " = :" + pn);
          params.addValue(pn, v, java.sql.Types.VARCHAR);
        }
      }
    }
    db.update("update courses set " + String.join(", ", sets) + ", updated_at = now() where id = :id", params);
  }

  public void updateSellerNameOnCourses(UUID sellerId, String name) {
    db.update("update courses set seller_name = :n, updated_at = now() where seller_id = :s", p("n", name, "s", sellerId));
  }

  public void updateSellerVerifiedOnCourses(UUID sellerId, boolean verified) {
    db.update("update courses set seller_verified = :v, updated_at = now() where seller_id = :s", p("v", verified, "s", sellerId));
  }

  // ---------------------------------------------------------------- related rows

  public List<TargetRow> targets(UUID courseId) {
    return db.query("select * from course_targets where course_id = :c", p("c", courseId), TARGET);
  }

  public List<TargetRow> targetsOf(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select * from course_targets where course_id in (:ids)", p("ids", courseIds), TARGET);
  }

  public List<SectionRow> sections(UUID courseId) {
    return db.query("select id, title from course_sections where course_id = :c order by position asc", p("c", courseId),
        (rs, i) -> new SectionRow(uuid(rs, "id"), rs.getString("title")));
  }

  public List<ChapterRow> chapters(UUID courseId) {
    return db.query("select id, section_id, title from course_chapters where course_id = :c order by position asc", p("c", courseId),
        (rs, i) -> new ChapterRow(uuid(rs, "id"), uuid(rs, "section_id"), rs.getString("title")));
  }

  public List<LessonRow> lessons(UUID courseId) {
    return db.query("select * from lessons where course_id = :c order by position asc", p("c", courseId),
        (rs, i) -> new LessonRow(uuid(rs, "id"), uuid(rs, "chapter_id"), rs.getString("title"), rs.getString("type"), rs.getInt("duration_sec"), uuid(rs, "video_asset_id"),
            uuid(rs, "document_id"), rs.getString("body"), rs.getBoolean("is_preview"), rs.getBoolean("is_important")));
  }

  public int countLessons(UUID courseId) {
    Integer n = db.queryForObject("select count(*) from lessons where course_id = :c", p("c", courseId), Integer.class);
    return n == null ? 0 : n;
  }

  public List<DocumentRow> documentsOfCourse(UUID courseId) {
    return db.query("select * from documents where course_id = :c order by created_at asc", p("c", courseId), DOC);
  }

  public List<DocumentRow> documents(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select * from documents where id in (:ids)", p("ids", ids), DOC);
  }

  public Optional<DocumentRow> document(UUID id) {
    return db.query("select * from documents where id = :id", p("id", id), DOC).stream().findFirst();
  }

  public List<VideoRow> videos(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select * from video_assets where id in (:ids)", p("ids", ids), VIDEO);
  }

  public Optional<VideoRow> video(UUID id) {
    return db.query("select * from video_assets where id = :id", p("id", id), VIDEO).stream().findFirst();
  }

  public Map<UUID, String> subjectNames(Collection<UUID> ids) {
    Map<UUID, String> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    db.query("select id, name from subject_masters where id in (:ids)", p("ids", ids), rs -> {
      out.put(uuid(rs, "id"), rs.getString("name"));
    });
    return out;
  }

  /** department id → institution id */
  public Map<UUID, UUID> departmentInstitutions(Collection<UUID> ids) {
    Map<UUID, UUID> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    db.query("select id, institution_id from departments where id in (:ids)", p("ids", ids), rs -> {
      out.put(uuid(rs, "id"), uuid(rs, "institution_id"));
    });
    return out;
  }

  public boolean institutionExists(UUID id) {
    Integer n = db.queryForObject("select count(*) from institutions where id = :id", p("id", id), Integer.class);
    return n != null && n > 0;
  }

  // ---------------------------------------------------------------- targets

  /** delete + insert, inside the caller's CONTENT transaction */
  public void replaceTargets(UUID courseId, List<TargetInput> ts) {
    db.update("delete from course_targets where course_id = :c", p("c", courseId));
    for (TargetInput t : ts) {
      boolean g = Boolean.TRUE.equals(t.isGeneral());
      String code = t.courseCode() == null || t.courseCode().isEmpty() ? null : t.courseCode().toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
      var ps = new MapSqlParameterSource()
          .addValue("id", Ids.uuidv7())
          .addValue("c", courseId)
          .addValue("g", g)
          .addValue("i", g ? null : t.institutionId(), java.sql.Types.OTHER)
          .addValue("d", g ? null : t.departmentId(), java.sql.Types.OTHER)
          .addValue("p", g ? null : t.programId(), java.sql.Types.OTHER)
          .addValue("al", t.academicLevel(), java.sql.Types.VARCHAR)
          .addValue("sem", t.semester(), java.sql.Types.INTEGER)
          .addValue("sm", t.subjectMasterId(), java.sql.Types.OTHER)
          .addValue("code", code, java.sql.Types.VARCHAR)
          .addValue("b", t.batches().toArray(String[]::new))
          .addValue("s", t.sections().toArray(String[]::new));
      db.update("insert into course_targets (id, course_id, is_general, institution_id, department_id, program_id, academic_level, semester, subject_master_id, course_code, batches, sections) "
          + "values (:id, :c, :g, :i, :d, :p, :al, :sem, :sm, :code, cast(:b as text[]), cast(:s as text[]))", ps);
    }
  }

  // ---------------------------------------------------------------- curriculum (inside the caller's transaction)

  public boolean updateSection(UUID id, UUID courseId, String title, int position) {
    return db.update("update course_sections set title = :t, position = :p, updated_at = now() where id = :id and course_id = :c",
        p("t", title, "p", position, "id", id, "c", courseId)) > 0;
  }

  public UUID insertSection(UUID courseId, String title, int position) {
    UUID id = Ids.uuidv7();
    db.update("insert into course_sections (id, course_id, title, position) values (:id, :c, :t, :p)", p("id", id, "c", courseId, "t", title, "p", position));
    return id;
  }

  public boolean updateChapter(UUID id, UUID courseId, UUID sectionId, String title, int position) {
    return db.update("update course_chapters set title = :t, position = :p, section_id = :s, updated_at = now() where id = :id and course_id = :c",
        p("t", title, "p", position, "s", sectionId, "id", id, "c", courseId)) > 0;
  }

  public UUID insertChapter(UUID courseId, UUID sectionId, String title, int position) {
    UUID id = Ids.uuidv7();
    db.update("insert into course_chapters (id, course_id, section_id, title, position) values (:id, :c, :s, :t, :p)",
        p("id", id, "c", courseId, "s", sectionId, "t", title, "p", position));
    return id;
  }

  public record LessonValues(String title, String type, int position, int durationSec, UUID videoAssetId, UUID documentId, String body, boolean isPreview, boolean isImportant, UUID chapterId) {}

  private static MapSqlParameterSource lessonParams(LessonValues v) {
    return new MapSqlParameterSource()
        .addValue("t", v.title()).addValue("ty", v.type()).addValue("pos", v.position()).addValue("dur", v.durationSec())
        .addValue("va", v.videoAssetId(), java.sql.Types.OTHER).addValue("doc", v.documentId(), java.sql.Types.OTHER)
        .addValue("body", v.body(), java.sql.Types.VARCHAR).addValue("pre", v.isPreview()).addValue("imp", v.isImportant()).addValue("ch", v.chapterId());
  }

  public boolean updateLesson(UUID id, UUID courseId, LessonValues v) {
    return db.update("update lessons set title = :t, type = :ty, position = :pos, duration_sec = :dur, video_asset_id = :va, document_id = :doc, body = :body, "
        + "is_preview = :pre, is_important = :imp, chapter_id = :ch, updated_at = now() where id = :id and course_id = :c", lessonParams(v).addValue("id", id).addValue("c", courseId)) > 0;
  }

  public UUID insertLesson(UUID courseId, LessonValues v) {
    UUID id = Ids.uuidv7();
    db.update("insert into lessons (id, course_id, title, type, position, duration_sec, video_asset_id, document_id, body, is_preview, is_important, chapter_id) "
        + "values (:id, :c, :t, :ty, :pos, :dur, :va, :doc, :body, :pre, :imp, :ch)", lessonParams(v).addValue("id", id).addValue("c", courseId));
    return id;
  }

  /** delete rows of {@code table} (lessons | course_chapters | course_sections) of the course whose id is not kept */
  public void deleteExcept(String table, UUID courseId, List<UUID> keep) {
    if (!Set.of("lessons", "course_chapters", "course_sections").contains(table)) throw new IllegalArgumentException(table);
    if (keep.isEmpty()) db.update("delete from " + table + " where course_id = :c", p("c", courseId));
    else db.update("delete from " + table + " where course_id = :c and id not in (:keep)", p("c", courseId, "keep", keep));
  }

  // ---------------------------------------------------------------- revisions

  public List<RevisionRow> pendingRevisions(UUID courseId) {
    return db.query("select id, course_id, payload_json from course_revisions where course_id = :c and status = 'pending'", p("c", courseId),
        (rs, i) -> new RevisionRow(uuid(rs, "id"), uuid(rs, "course_id"), readJson(rs.getString("payload_json"))));
  }

  public Set<UUID> coursesWithPendingRevision() {
    return new HashSet<>(db.queryForList("select course_id from course_revisions where status = 'pending'", Map.of(), UUID.class));
  }

  public void updateRevisionPayload(UUID id, JsonNode payload) {
    db.update("update course_revisions set payload_json = cast(:p as jsonb), updated_at = now() where id = :id", p("p", json.writeValueAsString(payload), "id", id));
  }

  public void insertRevision(UUID courseId, JsonNode payload) {
    db.update("insert into course_revisions (id, course_id, payload_json) values (:id, :c, cast(:p as jsonb))", p("id", Ids.uuidv7(), "c", courseId, "p", json.writeValueAsString(payload)));
  }

  public void closeRevision(UUID id, String status, UUID reviewerId, String notes) {
    db.update("update course_revisions set status = :s, reviewer_id = :r, notes = :n, updated_at = now() where id = :id",
        new MapSqlParameterSource().addValue("s", status).addValue("r", reviewerId).addValue("n", notes, java.sql.Types.VARCHAR).addValue("id", id));
  }

  // ---------------------------------------------------------------- review queue

  /** in_review, or published with a spot check / pending revision; oldest submission first */
  public List<CourseRow> reviewQueue(Collection<UUID> revisionCourseIds, Instant before, int limit) {
    var ps = new MapSqlParameterSource().addValue("lim", limit);
    String rev = "false";
    if (!revisionCourseIds.isEmpty()) {
      rev = "id in (:rev)";
      ps.addValue("rev", revisionCourseIds);
    }
    String cur = "";
    if (before != null) {
      cur = " and updated_at < :before";
      ps.addValue("before", before.atOffset(java.time.ZoneOffset.UTC));
    }
    return db.query("select * from courses where (status = 'in_review' or (status = 'published' and (needs_spot_review = true or " + rev + "))) and deleted_at is null" + cur
        + " order by submitted_at asc, updated_at desc limit :lim", ps, courseMapper);
  }

  public List<CourseRow> coursesByStatus(String status, Instant before, int limit) {
    var ps = new MapSqlParameterSource().addValue("s", status).addValue("lim", limit);
    String cur = "";
    if (before != null) {
      cur = " and updated_at < :before";
      ps.addValue("before", before.atOffset(java.time.ZoneOffset.UTC));
    }
    return db.query("select * from courses where status = :s and deleted_at is null" + cur + " order by updated_at desc limit :lim", ps, courseMapper);
  }

  // ---------------------------------------------------------------- uploads

  public UUID insertVideo(UUID ownerId, String provider, String providerVideoId, String externalUrl, String title, String status, int durationSec) {
    UUID id = Ids.uuidv7();
    db.update("insert into video_assets (id, owner_id, provider, provider_video_id, external_url, title, status, duration_sec) values (:id, :o, :p, :pv, :e, :t, :s, :d)",
        new MapSqlParameterSource().addValue("id", id).addValue("o", ownerId).addValue("p", provider).addValue("pv", providerVideoId, java.sql.Types.VARCHAR)
            .addValue("e", externalUrl, java.sql.Types.VARCHAR).addValue("t", title).addValue("s", status).addValue("d", durationSec));
    return id;
  }

  public void updateVideo(UUID id, String status, Integer durationSec) {
    if (durationSec == null) db.update("update video_assets set status = :s, updated_at = now() where id = :id", p("s", status, "id", id));
    else db.update("update video_assets set status = :s, duration_sec = :d, updated_at = now() where id = :id", p("s", status, "d", durationSec, "id", id));
  }

  public UUID insertDocument(UUID courseId, UUID ownerId, String kind, String title, Integer year, String bucket, String key, String externalUrl, String mime, Integer size) {
    UUID id = Ids.uuidv7();
    db.update("insert into documents (id, course_id, owner_id, kind, title, year, storage_bucket, storage_key, external_url, mime, size) values (:id, :c, :o, :k, :t, :y, :b, :key, :e, :m, :sz)",
        new MapSqlParameterSource().addValue("id", id).addValue("c", courseId).addValue("o", ownerId).addValue("k", kind).addValue("t", title)
            .addValue("y", year, java.sql.Types.INTEGER).addValue("b", bucket, java.sql.Types.VARCHAR).addValue("key", key, java.sql.Types.VARCHAR)
            .addValue("e", externalUrl, java.sql.Types.VARCHAR).addValue("m", mime, java.sql.Types.VARCHAR).addValue("sz", size, java.sql.Types.INTEGER));
    return id;
  }

  // ---------------------------------------------------------------- subjects

  public Optional<Map.Entry<UUID, String>> subjectBySlug(String slug) {
    return db.query("select id, status from subject_masters where slug = :s", p("s", slug), (rs, i) -> Map.entry(uuid(rs, "id"), rs.getString("status"))).stream().findFirst();
  }

  public UUID insertProposedSubject(String name, String slug, List<String> aliases, UUID proposedBy) {
    UUID id = Ids.uuidv7();
    db.update("insert into subject_masters (id, name, slug, aliases, status, proposed_by) values (:id, :n, :s, cast(:a as text[]), 'proposed', :p)",
        p("id", id, "n", name, "s", slug, "a", aliases.toArray(String[]::new), "p", proposedBy));
    return id;
  }
}
