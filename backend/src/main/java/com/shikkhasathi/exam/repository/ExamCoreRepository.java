package com.shikkhasathi.exam.repository;

import com.shikkhasathi.common.Ids;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * CORE-database queries of the exam module: exam_plans, mock_attempts, mock_answers, flashcard_reviews, plus
 * read-only lookups of enrollments, lesson_progress, notes and bookmarks (owned by other modules).
 */
@Repository
public class ExamCoreRepository {
  private final NamedParameterJdbcTemplate db;

  public ExamCoreRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record PlanRow(UUID id, UUID studentId, UUID subjectMasterId, String subjectName, String courseCode, String examType, Instant examDate, List<UUID> courseIds, int readiness, boolean active) {
    public PlanRow withReadiness(int r) {
      return new PlanRow(id, studentId, subjectMasterId, subjectName, courseCode, examType, examDate, courseIds, r, active);
    }
  }

  public record AttemptRow(UUID id, UUID testId, UUID courseId, UUID studentId, Instant startedAt, Instant deadlineAt, Instant submittedAt, List<UUID> questionOrder,
                           Integer score, Integer correctCount, Integer wrongCount, Integer skippedCount, String resultJson) {}

  public record AnswerRow(UUID questionId, UUID optionId, boolean flagged) {}

  public record EnrollmentRow(UUID courseId, Instant accessUntil) {}

  public record ReviewRow(UUID flashcardId, boolean known) {}

  static Instant ts(ResultSet rs, String c) throws SQLException {
    Timestamp t = rs.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }

  static Integer intOrNull(ResultSet rs, String c) throws SQLException {
    int v = rs.getInt(c);
    return rs.wasNull() ? null : v;
  }

  static List<UUID> uuids(ResultSet rs, String c) throws SQLException {
    Array a = rs.getArray(c);
    if (a == null) return new ArrayList<>();
    Object[] xs = (Object[]) a.getArray();
    List<UUID> out = new ArrayList<>(xs.length);
    for (Object x : xs) out.add(x instanceof UUID u ? u : UUID.fromString(String.valueOf(x)));
    return out;
  }

  /** {a,b,c} literal for {@code cast(:x as uuid[])} */
  public static String pgArray(Collection<UUID> ids) {
    return "{" + ids.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
  }

  private static final RowMapper<PlanRow> PLAN = (rs, i) -> new PlanRow(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class), rs.getObject("subject_master_id", UUID.class),
      rs.getString("subject_name"), rs.getString("course_code"), rs.getString("exam_type"), ts(rs, "exam_date"), uuids(rs, "course_ids"), rs.getInt("readiness"), rs.getBoolean("active"));

  private static final RowMapper<AttemptRow> ATTEMPT = (rs, i) -> new AttemptRow(rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class), rs.getObject("course_id", UUID.class),
      rs.getObject("student_id", UUID.class), ts(rs, "started_at"), ts(rs, "deadline_at"), ts(rs, "submitted_at"), uuids(rs, "question_order"), intOrNull(rs, "score"),
      intOrNull(rs, "correct_count"), intOrNull(rs, "wrong_count"), intOrNull(rs, "skipped_count"), rs.getString("result_json"));

  private static final String PLAN_COLS = "id, student_id, subject_master_id, subject_name, course_code, exam_type, exam_date, course_ids, readiness, active";
  private static final String ATTEMPT_COLS = "id, test_id, course_id, student_id, started_at, deadline_at, submitted_at, question_order, score, correct_count, wrong_count, skipped_count, result_json::text as result_json";

  // ---------------------------------------------------------------- enrollments / progress (read-only)

  public List<EnrollmentRow> activeEnrollments(UUID studentId, Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select course_id, access_until from enrollments where student_id = :s and course_id in (:ids) and status = 'active'",
        new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), (rs, i) -> new EnrollmentRow(rs.getObject("course_id", UUID.class), ts(rs, "access_until")));
  }

  public List<UUID> activeEnrollmentCourseIds(UUID studentId) {
    return db.query("select course_id from enrollments where student_id = :s and status = 'active'", Map.of("s", studentId), (rs, i) -> rs.getObject(1, UUID.class));
  }

  public List<UUID> completedLessons(UUID studentId, Collection<UUID> lessonIds) {
    if (lessonIds.isEmpty()) return List.of();
    return db.query("select lesson_id from lesson_progress where student_id = :s and lesson_id in (:ids) and completed_at is not null",
        new MapSqlParameterSource("s", studentId).addValue("ids", lessonIds), (rs, i) -> rs.getObject(1, UUID.class));
  }

  public List<UUID> completedLessonsSince(UUID studentId, Collection<UUID> lessonIds, Instant since) {
    if (lessonIds.isEmpty()) return List.of();
    return db.query("select lesson_id from lesson_progress where student_id = :s and lesson_id in (:ids) and completed_at >= :t",
        new MapSqlParameterSource("s", studentId).addValue("ids", lessonIds).addValue("t", Timestamp.from(since)), (rs, i) -> rs.getObject(1, UUID.class));
  }

  public int countNotes(UUID studentId, Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return 0;
    return db.queryForObject("select count(*)::int from notes where student_id = :s and course_id in (:ids)", new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), Integer.class);
  }

  public int countBookmarks(UUID studentId, Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return 0;
    return db.queryForObject("select count(*)::int from bookmarks where student_id = :s and course_id in (:ids)", new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), Integer.class);
  }

  // ---------------------------------------------------------------- exam plans

  public Optional<PlanRow> findPlan(UUID id) {
    return db.query("select " + PLAN_COLS + " from exam_plans where id = :id", Map.of("id", id), PLAN).stream().findFirst();
  }

  public List<PlanRow> plansOf(UUID studentId) {
    return db.query("select " + PLAN_COLS + " from exam_plans where student_id = :s order by exam_date asc", Map.of("s", studentId), PLAN);
  }

  public int countActiveUpcoming(UUID studentId) {
    return db.queryForObject("select count(*)::int from exam_plans where student_id = :s and active = true and exam_date >= now()", Map.of("s", studentId), Integer.class);
  }

  public UUID insertPlan(UUID studentId, String subjectName, UUID subjectMasterId, String courseCode, String examType, Instant examDate, List<UUID> courseIds) {
    UUID id = Ids.uuidv7();
    db.update("insert into exam_plans (id, student_id, subject_name, subject_master_id, course_code, exam_type, exam_date, course_ids) values (:id, :s, :n, :m, :c, :t, :d, cast(:ids as uuid[]))",
        new MapSqlParameterSource("id", id).addValue("s", studentId).addValue("n", subjectName).addValue("m", subjectMasterId).addValue("c", courseCode).addValue("t", examType)
            .addValue("d", Timestamp.from(examDate)).addValue("ids", pgArray(courseIds)));
    return id;
  }

  /** Partial update: only the keys present in {@code set} (column → value) change; updated_at always. */
  public void updatePlan(UUID id, Map<String, Object> set) {
    var p = new MapSqlParameterSource("id", id);
    StringBuilder sql = new StringBuilder("update exam_plans set updated_at = now()");
    for (var e : set.entrySet()) {
      String col = e.getKey();
      Object v = e.getValue();
      if (col.equals("course_ids")) {
        sql.append(", course_ids = cast(:course_ids as uuid[])");
        @SuppressWarnings("unchecked") List<UUID> ids = (List<UUID>) v;
        p.addValue("course_ids", pgArray(ids));
      } else {
        sql.append(", ").append(col).append(" = :").append(col);
        p.addValue(col, v instanceof Instant i ? Timestamp.from(i) : v);
      }
    }
    db.update(sql.append(" where id = :id").toString(), p);
  }

  public void deletePlan(UUID id) {
    db.update("delete from exam_plans where id = :id", Map.of("id", id));
  }

  public void setReadiness(UUID id, int readiness) {
    db.update("update exam_plans set readiness = :r where id = :id", Map.of("id", id, "r", readiness));
  }

  public List<PlanRow> activePlansWithCourse(UUID studentId, UUID courseId) {
    return db.query("select " + PLAN_COLS + " from exam_plans where student_id = :s and active = true and :c = any(course_ids)", Map.of("s", studentId, "c", courseId), PLAN);
  }

  public List<PlanRow> activePlansFrom(Instant from, int limit) {
    return db.query("select " + PLAN_COLS + " from exam_plans where active = true and exam_date >= :t limit :l", Map.of("t", Timestamp.from(from), "l", limit), PLAN);
  }

  public int archivePast() {
    return db.update("update exam_plans set active = false where active = true and exam_date < now() - interval '2 days'", Map.of());
  }

  // ---------------------------------------------------------------- flashcard reviews

  public int countKnown(UUID studentId, Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return 0;
    return db.queryForObject("select count(*)::int from flashcard_reviews where student_id = :s and course_id in (:ids) and known = true",
        new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), Integer.class);
  }

  public List<ReviewRow> reviews(UUID studentId, Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select flashcard_id, known from flashcard_reviews where student_id = :s and course_id in (:ids)",
        new MapSqlParameterSource("s", studentId).addValue("ids", courseIds), (rs, i) -> new ReviewRow(rs.getObject(1, UUID.class), rs.getBoolean(2)));
  }

  public void upsertReview(UUID studentId, UUID flashcardId, UUID courseId, boolean known) {
    db.update("insert into flashcard_reviews (id, student_id, flashcard_id, course_id, known) values (:id, :s, :f, :c, :k) "
            + "on conflict (student_id, flashcard_id) do update set known = excluded.known, updated_at = now()",
        new MapSqlParameterSource("id", Ids.uuidv7()).addValue("s", studentId).addValue("f", flashcardId).addValue("c", courseId).addValue("k", known));
  }

  // ---------------------------------------------------------------- attempts

  public List<AttemptRow> attemptsOf(UUID studentId, Collection<UUID> testIds) {
    if (testIds.isEmpty()) return List.of();
    return db.query("select " + ATTEMPT_COLS + " from mock_attempts where student_id = :s and test_id in (:ids) order by started_at desc",
        new MapSqlParameterSource("s", studentId).addValue("ids", testIds), ATTEMPT);
  }

  public Optional<AttemptRow> findAttempt(UUID id) {
    return db.query("select " + ATTEMPT_COLS + " from mock_attempts where id = :id", Map.of("id", id), ATTEMPT).stream().findFirst();
  }

  public Optional<AttemptRow> latestOpenAttempt(UUID studentId, UUID testId) {
    return db.query("select " + ATTEMPT_COLS + " from mock_attempts where student_id = :s and test_id = :t and submitted_at is null order by started_at desc limit 1",
        Map.of("s", studentId, "t", testId), ATTEMPT).stream().findFirst();
  }

  public UUID insertAttempt(UUID testId, UUID courseId, UUID studentId, Instant startedAt, Instant deadlineAt, List<UUID> order) {
    UUID id = Ids.uuidv7();
    db.update("insert into mock_attempts (id, test_id, course_id, student_id, started_at, deadline_at, question_order) values (:id, :t, :c, :s, :st, :dl, cast(:o as uuid[]))",
        new MapSqlParameterSource("id", id).addValue("t", testId).addValue("c", courseId).addValue("s", studentId).addValue("st", Timestamp.from(startedAt))
            .addValue("dl", Timestamp.from(deadlineAt)).addValue("o", pgArray(order)));
    return id;
  }

  /** Scores an open attempt; returns false when it was already submitted (concurrent finalize). */
  public boolean finalizeAttempt(UUID id, Instant submittedAt, int score, int correct, int wrong, int skipped, String resultJson) {
    return db.update("update mock_attempts set submitted_at = :at, score = :sc, correct_count = :c, wrong_count = :w, skipped_count = :sk, result_json = cast(:j as jsonb), updated_at = now() "
            + "where id = :id and submitted_at is null",
        new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(submittedAt)).addValue("sc", score).addValue("c", correct).addValue("w", wrong).addValue("sk", skipped).addValue("j", resultJson)) > 0;
  }

  public List<Integer> submittedScores(UUID studentId, UUID testId) {
    return db.query("select score from mock_attempts where student_id = :s and test_id = :t and submitted_at is not null", Map.of("s", studentId, "t", testId), (rs, i) -> intOrNull(rs, "score"));
  }

  public int sumWrong(UUID studentId, Collection<UUID> testIds) {
    if (testIds.isEmpty()) return 0;
    return db.queryForObject("select coalesce(sum(wrong_count),0)::int from mock_attempts where student_id = :s and test_id in (:ids) and submitted_at is not null",
        new MapSqlParameterSource("s", studentId).addValue("ids", testIds), Integer.class);
  }

  /** test id → number of attempts (all students) */
  public Map<UUID, Integer> attemptCounts(Collection<UUID> testIds) {
    Map<UUID, Integer> out = new HashMap<>();
    if (testIds.isEmpty()) return out;
    db.query("select test_id, count(*)::int as n from mock_attempts where test_id in (:ids) group by test_id", new MapSqlParameterSource("ids", testIds),
        rs -> { out.put(rs.getObject(1, UUID.class), rs.getInt(2)); });
    return out;
  }

  // ---------------------------------------------------------------- answers

  public List<AnswerRow> answers(UUID attemptId) {
    return db.query("select question_id, option_id, flagged from mock_answers where attempt_id = :a", Map.of("a", attemptId),
        (rs, i) -> new AnswerRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBoolean(3)));
  }

  public void upsertAnswer(UUID attemptId, UUID questionId, UUID optionId, Boolean flagged) {
    String onFlag = flagged != null ? ", flagged = excluded.flagged" : "";
    db.update("insert into mock_answers (id, attempt_id, question_id, option_id, flagged) values (:id, :a, :q, :o, :f) "
            + "on conflict (attempt_id, question_id) do update set option_id = excluded.option_id" + onFlag + ", updated_at = now()",
        new MapSqlParameterSource("id", Ids.uuidv7()).addValue("a", attemptId).addValue("q", questionId).addValue("o", optionId).addValue("f", flagged != null && flagged));
  }
}
