package com.shikkhasathi.learning.repository;

import com.shikkhasathi.common.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE SQL for the learning module: enrollments, lesson progress, notes, bookmarks, Q&A, reviews (+ user/seller names). */
@Repository
public class LearningCoreRepository {
  public record EnrollmentRow(UUID id, UUID studentId, UUID courseId, String status, Instant accessUntil, int progressPercent, UUID lastLessonId,
                              Instant lastAccessedAt, Instant completedAt, Instant createdAt) {
    /** active and not past {@code access_until} */
    public boolean active() {
      return "active".equals(status) && (accessUntil == null || accessUntil.isAfter(Instant.now()));
    }
  }

  public record ProgressRow(UUID id, UUID lessonId, int watchedSeconds, int lastPositionSec, Instant completedAt) {}

  public record NoteRow(UUID id, UUID studentId, UUID courseId, UUID lessonId, Integer timestampSec, String body, Instant createdAt, Instant updatedAt) {}

  public record QuestionRow(UUID id, UUID lessonId, UUID courseId, UUID sellerId, UUID authorId, String body, Integer timestampSec, String status, Instant createdAt) {}

  public record AnswerRow(UUID id, UUID questionId, UUID authorId, String body, boolean isSeller, Instant createdAt) {}

  public record ReviewRow(UUID id, UUID courseId, UUID sellerId, UUID studentId, int rating, String body, String sellerReply, Instant sellerRepliedAt, boolean verified, Instant createdAt) {}

  public record SellerName(String name, String slug) {}

  public record Contact(String phone, String email) {}

  /** Keyset cursor (created_at, id) — null when absent. */
  public record Keyset(Instant createdAt, UUID id) {}

  private final NamedParameterJdbcTemplate db;

  public LearningCoreRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static Instant ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  static UUID uuid(ResultSet rs, String col) throws SQLException {
    return rs.getObject(col, UUID.class);
  }

  static Integer intOrNull(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  static Timestamp sqlTs(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  // ---------------------------------------------------------------- enrollments & progress

  private static EnrollmentRow enrollment(ResultSet rs, int i) throws SQLException {
    return new EnrollmentRow(uuid(rs, "id"), uuid(rs, "student_id"), uuid(rs, "course_id"), rs.getString("status"), ts(rs, "access_until"), rs.getInt("progress_percent"),
        uuid(rs, "last_lesson_id"), ts(rs, "last_accessed_at"), ts(rs, "completed_at"), ts(rs, "created_at"));
  }

  public Optional<EnrollmentRow> enrollment(UUID studentId, UUID courseId) {
    return db.query("select * from enrollments where student_id = :s and course_id = :c", Map.of("s", studentId, "c", courseId), LearningCoreRepository::enrollment).stream().findFirst();
  }

  /** My Courses: active + expired, most recently accessed first. */
  public List<EnrollmentRow> myEnrollments(UUID studentId) {
    return db.query("select * from enrollments where student_id = :s and status in ('active', 'expired') order by last_accessed_at desc nulls last, created_at desc",
        Map.of("s", studentId), LearningCoreRepository::enrollment);
  }

  public int completedLessons(UUID studentId, UUID courseId) {
    Integer n = db.queryForObject("select count(*)::int from lesson_progress where student_id = :s and course_id = :c and completed_at is not null", Map.of("s", studentId, "c", courseId), Integer.class);
    return n == null ? 0 : n;
  }

  public Map<UUID, Integer> completedLessons(UUID studentId, Collection<UUID> courseIds) {
    var out = new HashMap<UUID, Integer>();
    if (courseIds.isEmpty()) return out;
    db.query("select course_id, count(*)::int as n from lesson_progress where student_id = :s and course_id in (:ids) and completed_at is not null group by course_id",
        new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), rs -> {
          out.put(uuid(rs, "course_id"), rs.getInt("n"));
        });
    return out;
  }

  public void updateEnrollmentProgress(UUID studentId, UUID courseId, int pct, UUID lastLessonId) {
    var p = new MapSqlParameterSource("s", studentId).addValue("c", courseId).addValue("p", pct).addValue("l", lastLessonId).addValue("done", pct >= 100);
    db.update("""
        update enrollments set progress_percent = :p, last_accessed_at = now(),
          last_lesson_id = coalesce(cast(:l as uuid), last_lesson_id),
          completed_at = case when :done then coalesce(completed_at, now()) else null end
        where student_id = :s and course_id = :c""", p);
  }

  private static ProgressRow progress(ResultSet rs, int i) throws SQLException {
    return new ProgressRow(uuid(rs, "id"), uuid(rs, "lesson_id"), rs.getInt("watched_seconds"), rs.getInt("last_position_sec"), ts(rs, "completed_at"));
  }

  public List<ProgressRow> progressFor(UUID studentId, UUID courseId) {
    return db.query("select * from lesson_progress where student_id = :s and course_id = :c", Map.of("s", studentId, "c", courseId), LearningCoreRepository::progress);
  }

  public Optional<ProgressRow> progress(UUID studentId, UUID lessonId) {
    return db.query("select * from lesson_progress where student_id = :s and lesson_id = :l", Map.of("s", studentId, "l", lessonId), LearningCoreRepository::progress).stream().findFirst();
  }

  /** Heartbeat upsert: adds {@code delta} watched seconds (capped) and stores the position. */
  public ProgressRow upsertHeartbeat(UUID studentId, UUID courseId, UUID lessonId, int delta, int positionSec, int cap) {
    var p = new MapSqlParameterSource("id", Ids.uuidv7()).addValue("s", studentId).addValue("c", courseId).addValue("l", lessonId).addValue("d", delta).addValue("pos", positionSec).addValue("cap", cap);
    return db.queryForObject("""
        insert into lesson_progress (id, student_id, course_id, lesson_id, watched_seconds, last_position_sec)
        values (:id, :s, :c, :l, :d, :pos)
        on conflict (student_id, lesson_id) do update set watched_seconds = least(lesson_progress.watched_seconds + :d, :cap),
          last_position_sec = :pos, updated_at = now()
        returning *""", p, LearningCoreRepository::progress);
  }

  public void markProgressCompleted(UUID progressId) {
    db.update("update lesson_progress set completed_at = now() where id = :id", Map.of("id", progressId));
  }

  public void upsertCompleted(UUID studentId, UUID courseId, UUID lessonId, boolean completed) {
    var p = new MapSqlParameterSource("id", Ids.uuidv7()).addValue("s", studentId).addValue("c", courseId).addValue("l", lessonId).addValue("done", completed);
    db.update("""
        insert into lesson_progress (id, student_id, course_id, lesson_id, completed_at)
        values (:id, :s, :c, :l, case when :done then now() else null end)
        on conflict (student_id, lesson_id) do update set
          completed_at = case when :done then coalesce(lesson_progress.completed_at, now()) else null end, updated_at = now()""", p);
  }

  // ---------------------------------------------------------------- people

  public Map<UUID, SellerName> sellerNames(Collection<UUID> userIds) {
    var out = new HashMap<UUID, SellerName>();
    if (userIds.isEmpty()) return out;
    db.query("select user_id, display_name, slug from seller_profiles where user_id in (:ids)", Map.of("ids", userIds), rs -> {
      out.put(uuid(rs, "user_id"), new SellerName(rs.getString("display_name"), rs.getString("slug")));
    });
    return out;
  }

  public Map<UUID, String> userNames(Collection<UUID> userIds) {
    var out = new HashMap<UUID, String>();
    if (userIds.isEmpty()) return out;
    db.query("select id, name from users where id in (:ids)", Map.of("ids", userIds), rs -> {
      out.put(uuid(rs, "id"), rs.getString("name"));
    });
    return out;
  }

  public Optional<Contact> contact(UUID userId) {
    return db.query("select phone, email from users where id = :id", Map.of("id", userId), (rs, i) -> new Contact(rs.getString("phone"), rs.getString("email"))).stream().findFirst();
  }

  // ---------------------------------------------------------------- notes & bookmarks

  private static NoteRow note(ResultSet rs, int i) throws SQLException {
    return new NoteRow(uuid(rs, "id"), uuid(rs, "student_id"), uuid(rs, "course_id"), uuid(rs, "lesson_id"), intOrNull(rs, "timestamp_sec"), rs.getString("body"), ts(rs, "created_at"), ts(rs, "updated_at"));
  }

  /** Shared list query for notes and bookmarks (same columns). */
  private List<NoteRow> listOwn(String table, UUID studentId, UUID courseId, UUID lessonId, String q, Keyset cur, int limit) {
    var sql = new StringBuilder("select * from " + table + " where student_id = :s");
    var p = new MapSqlParameterSource("s", studentId).addValue("lim", limit + 1);
    if (courseId != null) {
      sql.append(" and course_id = :c");
      p.addValue("c", courseId);
    }
    if (lessonId != null) {
      sql.append(" and lesson_id = :l");
      p.addValue("l", lessonId);
    }
    if (q != null) {
      sql.append(" and body ilike :q");
      p.addValue("q", "%" + q + "%");
    }
    if (cur != null) {
      sql.append(" and (created_at < :ca or (created_at = :ca and id < :ci))");
      p.addValue("ca", sqlTs(cur.createdAt())).addValue("ci", cur.id());
    }
    sql.append(" order by created_at desc, id desc limit :lim");
    return db.query(sql.toString(), p, LearningCoreRepository::note);
  }

  public List<NoteRow> listNotes(UUID studentId, UUID courseId, UUID lessonId, String q, Keyset cur, int limit) {
    return listOwn("notes", studentId, courseId, lessonId, q, cur, limit);
  }

  public List<NoteRow> listBookmarks(UUID studentId, UUID courseId, UUID lessonId, Keyset cur, int limit) {
    return listOwn("bookmarks", studentId, courseId, lessonId, null, cur, limit);
  }

  public NoteRow insertNote(UUID studentId, UUID courseId, UUID lessonId, Integer timestampSec, String body) {
    return insertOwn("notes", studentId, courseId, lessonId, timestampSec, body);
  }

  public NoteRow insertBookmark(UUID studentId, UUID courseId, UUID lessonId, Integer timestampSec, String body) {
    return insertOwn("bookmarks", studentId, courseId, lessonId, timestampSec, body);
  }

  private NoteRow insertOwn(String table, UUID studentId, UUID courseId, UUID lessonId, Integer timestampSec, String body) {
    var p = new MapSqlParameterSource("id", Ids.uuidv7()).addValue("s", studentId).addValue("c", courseId).addValue("l", lessonId).addValue("t", timestampSec).addValue("b", body);
    return db.queryForObject("insert into " + table + " (id, student_id, course_id, lesson_id, timestamp_sec, body, created_at, updated_at) "
        + "values (:id, :s, :c, :l, cast(:t as integer), :b, date_trunc('milliseconds', now()), date_trunc('milliseconds', now())) returning *", p, LearningCoreRepository::note);
  }

  public Optional<NoteRow> note(UUID id) {
    return db.query("select * from notes where id = :id", Map.of("id", id), LearningCoreRepository::note).stream().findFirst();
  }

  public NoteRow updateNote(UUID id, String body) {
    return db.queryForObject("update notes set body = :b, updated_at = now() where id = :id returning *", new MapSqlParameterSource("id", id).addValue("b", body), LearningCoreRepository::note);
  }

  public void deleteNote(UUID id) {
    db.update("delete from notes where id = :id", Map.of("id", id));
  }

  public Optional<NoteRow> bookmark(UUID id) {
    return db.query("select * from bookmarks where id = :id", Map.of("id", id), LearningCoreRepository::note).stream().findFirst();
  }

  public void deleteBookmark(UUID id) {
    db.update("delete from bookmarks where id = :id", Map.of("id", id));
  }

  // ---------------------------------------------------------------- Q&A

  private static QuestionRow question(ResultSet rs, int i) throws SQLException {
    return new QuestionRow(uuid(rs, "id"), uuid(rs, "lesson_id"), uuid(rs, "course_id"), uuid(rs, "seller_id"), uuid(rs, "author_id"), rs.getString("body"),
        intOrNull(rs, "timestamp_sec"), rs.getString("status"), ts(rs, "created_at"));
  }

  public Optional<QuestionRow> question(UUID id) {
    return db.query("select * from questions where id = :id", Map.of("id", id), LearningCoreRepository::question).stream().findFirst();
  }

  /** Lesson Q&A: everything not hidden, plus the viewer's own hidden questions. */
  public List<QuestionRow> lessonQuestions(UUID lessonId, UUID viewerId, Instant before, int limit) {
    var p = new MapSqlParameterSource("l", lessonId).addValue("u", viewerId).addValue("lim", limit + 1).addValue("b", sqlTs(before));
    return db.query("select * from questions where lesson_id = :l and (status <> 'hidden' or author_id = :u)"
        + (before != null ? " and created_at < :b" : "") + " order by created_at desc limit :lim", p, LearningCoreRepository::question);
  }

  public List<QuestionRow> sellerQuestions(UUID sellerId, String status, Instant before, int limit) {
    var p = new MapSqlParameterSource("s", sellerId).addValue("st", status).addValue("lim", limit + 1).addValue("b", sqlTs(before));
    return db.query("select * from questions where seller_id = :s and status <> 'hidden'" + (status != null ? " and status = :st" : "")
        + (before != null ? " and created_at < :b" : "") + " order by created_at desc limit :lim", p, LearningCoreRepository::question);
  }

  public QuestionRow insertQuestion(UUID lessonId, UUID courseId, UUID sellerId, UUID authorId, String body, Integer timestampSec) {
    var p = new MapSqlParameterSource("id", Ids.uuidv7()).addValue("l", lessonId).addValue("c", courseId).addValue("s", sellerId).addValue("a", authorId).addValue("b", body).addValue("t", timestampSec);
    return db.queryForObject("insert into questions (id, lesson_id, course_id, seller_id, author_id, body, timestamp_sec, created_at, updated_at) "
        + "values (:id, :l, :c, :s, :a, :b, cast(:t as integer), date_trunc('milliseconds', now()), date_trunc('milliseconds', now())) returning *", p, LearningCoreRepository::question);
  }

  public void markAnswered(UUID questionId) {
    db.update("update questions set status = 'answered', updated_at = now() where id = :id", Map.of("id", questionId));
  }

  public List<AnswerRow> visibleAnswers(Collection<UUID> questionIds) {
    if (questionIds.isEmpty()) return List.of();
    return db.query("select * from answers where question_id in (:ids) and status = 'visible' order by created_at", Map.of("ids", questionIds),
        (rs, i) -> new AnswerRow(uuid(rs, "id"), uuid(rs, "question_id"), uuid(rs, "author_id"), rs.getString("body"), rs.getBoolean("is_seller"), ts(rs, "created_at")));
  }

  public UUID insertAnswer(UUID questionId, UUID authorId, String body, boolean isSeller) {
    UUID id = Ids.uuidv7();
    db.update("insert into answers (id, question_id, author_id, body, is_seller, created_at, updated_at) values (:id, :q, :a, :b, :s, date_trunc('milliseconds', now()), date_trunc('milliseconds', now()))",
        new MapSqlParameterSource("id", id).addValue("q", questionId).addValue("a", authorId).addValue("b", body).addValue("s", isSeller));
    return id;
  }

  // ---------------------------------------------------------------- reviews

  private static ReviewRow review(ResultSet rs, int i) throws SQLException {
    return new ReviewRow(uuid(rs, "id"), uuid(rs, "course_id"), uuid(rs, "seller_id"), uuid(rs, "student_id"), rs.getInt("rating"), rs.getString("body"),
        rs.getString("seller_reply"), ts(rs, "seller_replied_at"), rs.getBoolean("verified"), ts(rs, "created_at"));
  }

  public Optional<ReviewRow> review(UUID id) {
    return db.query("select * from reviews where id = :id", Map.of("id", id), LearningCoreRepository::review).stream().findFirst();
  }

  public Optional<ReviewRow> reviewBy(UUID courseId, UUID studentId) {
    return db.query("select * from reviews where course_id = :c and student_id = :s", Map.of("c", courseId, "s", studentId), LearningCoreRepository::review).stream().findFirst();
  }

  public List<ReviewRow> courseReviews(UUID courseId, Integer rating, Instant before, int limit) {
    var p = new MapSqlParameterSource("c", courseId).addValue("r", rating).addValue("lim", limit + 1).addValue("b", sqlTs(before));
    return db.query("select * from reviews where course_id = :c and status = 'visible'" + (rating != null ? " and rating = :r" : "")
        + (before != null ? " and created_at < :b" : "") + " order by created_at desc limit :lim", p, LearningCoreRepository::review);
  }

  public List<ReviewRow> sellerReviews(UUID sellerId, Instant before, int limit) {
    var p = new MapSqlParameterSource("s", sellerId).addValue("lim", limit + 1).addValue("b", sqlTs(before));
    return db.query("select * from reviews where seller_id = :s and status = 'visible'" + (before != null ? " and created_at < :b" : "")
        + " order by created_at desc limit :lim", p, LearningCoreRepository::review);
  }

  public ReviewRow upsertReview(UUID courseId, UUID sellerId, UUID studentId, int rating, String body) {
    var p = new MapSqlParameterSource("id", Ids.uuidv7()).addValue("c", courseId).addValue("s", sellerId).addValue("st", studentId).addValue("r", rating).addValue("b", body);
    return db.queryForObject("""
        insert into reviews (id, course_id, seller_id, student_id, rating, body, created_at, updated_at)
        values (:id, :c, :s, :st, :r, :b, date_trunc('milliseconds', now()), date_trunc('milliseconds', now()))
        on conflict (course_id, student_id) do update set rating = excluded.rating, body = excluded.body, updated_at = now()
        returning *""", p, LearningCoreRepository::review);
  }

  public record RatingAggregate(String avg, int count) {}

  public RatingAggregate ratingOf(UUID courseId) {
    return db.queryForObject("select coalesce(round(avg(rating)::numeric, 2), 0)::text as avg, count(*)::int as n from reviews where course_id = :c and status = 'visible'",
        Map.of("c", courseId), (rs, i) -> new RatingAggregate(rs.getString("avg"), rs.getInt("n")));
  }

  public ReviewRow setSellerReply(UUID id, String reply) {
    return db.queryForObject("update reviews set seller_reply = :r, seller_replied_at = now() where id = :id returning *", new MapSqlParameterSource("id", id).addValue("r", reply), LearningCoreRepository::review);
  }
}
