package com.shikkhasathi.exam.repository;

import static com.shikkhasathi.exam.repository.ExamCoreRepository.ts;

import com.shikkhasathi.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * CONTENT-database queries of the exam module: mock_tests, mock_questions, mock_options, flashcards, plus
 * read-only lookups of courses, the curriculum (sections → chapters → lessons) and documents.
 */
@Repository
public class ExamContentRepository {
  private final NamedParameterJdbcTemplate db;

  public ExamContentRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record CurriculumLesson(UUID id, UUID courseId, String title, String type, int durationSec, boolean isImportant, UUID chapterId, String chapterTitle, String sectionTitle) {}

  public record TestRow(UUID id, UUID courseId, String title, String description, int durationSec, boolean shuffle, Instant createdAt) {}

  public record QuestionRow(UUID id, UUID testId, String body, String explanation, String chapterTag, int position) {}

  public record OptionRow(UUID id, UUID questionId, String body, boolean isCorrect, int position) {}

  public record CourseRow(UUID id, String slug, String title, UUID sellerId) {}

  public record FlashcardRow(UUID id, UUID courseId, String front, String back) {}

  public record DocumentRow(UUID id, String title, Integer year, UUID courseId) {}

  public List<CurriculumLesson> curriculum(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("""
        select l.id, l.course_id, l.title, l.type, l.duration_sec, l.is_important, ch.id as chapter_id, ch.title as chapter_title, s.title as section_title
        from lessons l
        join course_chapters ch on ch.id = l.chapter_id
        join course_sections s on s.id = ch.section_id
        where l.course_id in (:ids) and l.type in ('video', 'pdf', 'text')
        order by s.position asc, ch.position asc, l.position asc""",
        new MapSqlParameterSource("ids", courseIds),
        (rs, i) -> new CurriculumLesson(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title"), rs.getString("type"), rs.getInt("duration_sec"),
            rs.getBoolean("is_important"), rs.getObject("chapter_id", UUID.class), rs.getString("chapter_title"), rs.getString("section_title")));
  }

  // ---------------------------------------------------------------- courses / documents

  public List<CourseRow> courses(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select id, slug, title, seller_id from courses where id in (:ids)", new MapSqlParameterSource("ids", ids),
        (rs, i) -> new CourseRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class)));
  }

  public Optional<CourseRow> course(UUID id) {
    return courses(List.of(id)).stream().findFirst();
  }

  public List<DocumentRow> previousQuestions(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select id, title, year, course_id from documents where course_id in (:ids) and kind = 'previous_questions' order by year desc",
        new MapSqlParameterSource("ids", courseIds), (rs, i) -> new DocumentRow(rs.getObject(1, UUID.class), rs.getString(2), ExamCoreRepository.intOrNull(rs, "year"), rs.getObject(4, UUID.class)));
  }

  // ---------------------------------------------------------------- mock tests

  private static final String TEST_COLS = "id, course_id, title, description, duration_sec, shuffle, created_at";

  private static TestRow test(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new TestRow(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title"), rs.getString("description"), rs.getInt("duration_sec"),
        rs.getBoolean("shuffle"), ts(rs, "created_at"));
  }

  public List<TestRow> testsOfCourses(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select " + TEST_COLS + " from mock_tests where course_id in (:ids) order by created_at asc", new MapSqlParameterSource("ids", courseIds), (rs, i) -> test(rs));
  }

  public Optional<TestRow> findTest(UUID id) {
    return db.query("select " + TEST_COLS + " from mock_tests where id = :id", Map.of("id", id), (rs, i) -> test(rs)).stream().findFirst();
  }

  public List<TestRow> findTests(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select " + TEST_COLS + " from mock_tests where id in (:ids)", new MapSqlParameterSource("ids", ids), (rs, i) -> test(rs));
  }

  /** test id → question count */
  public Map<UUID, Integer> questionCounts(Collection<UUID> testIds) {
    Map<UUID, Integer> out = new HashMap<>();
    if (testIds.isEmpty()) return out;
    db.query("select test_id, count(*)::int from mock_questions where test_id in (:ids) group by test_id", new MapSqlParameterSource("ids", testIds),
        rs -> { out.put(rs.getObject(1, UUID.class), rs.getInt(2)); });
    return out;
  }

  private static QuestionRow question(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new QuestionRow(rs.getObject("id", UUID.class), rs.getObject("test_id", UUID.class), rs.getString("body"), rs.getString("explanation"), rs.getString("chapter_tag"), rs.getInt("position"));
  }

  public List<QuestionRow> questionsByIds(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select id, test_id, body, explanation, chapter_tag, position from mock_questions where id in (:ids)", new MapSqlParameterSource("ids", ids), (rs, i) -> question(rs));
  }

  public List<QuestionRow> questionsOfTests(Collection<UUID> testIds) {
    if (testIds.isEmpty()) return List.of();
    return db.query("select id, test_id, body, explanation, chapter_tag, position from mock_questions where test_id in (:ids) order by position asc",
        new MapSqlParameterSource("ids", testIds), (rs, i) -> question(rs));
  }

  public List<OptionRow> optionsOf(Collection<UUID> questionIds) {
    if (questionIds.isEmpty()) return List.of();
    return db.query("select id, question_id, body, is_correct, position from mock_options where question_id in (:ids) order by position asc",
        new MapSqlParameterSource("ids", questionIds),
        (rs, i) -> new OptionRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4), rs.getInt(5)));
  }

  public Optional<UUID> optionQuestion(UUID optionId) {
    return db.query("select question_id from mock_options where id = :id", Map.of("id", optionId), (rs, i) -> rs.getObject(1, UUID.class)).stream().findFirst();
  }

  public UUID insertTest(UUID courseId, String title, String description, int durationSec) {
    UUID id = Ids.uuidv7();
    db.update("insert into mock_tests (id, course_id, title, description, duration_sec) values (:id, :c, :t, :d, :s)",
        new MapSqlParameterSource("id", id).addValue("c", courseId).addValue("t", title).addValue("d", description).addValue("s", durationSec));
    return id;
  }

  public void updateTest(UUID id, String title, String description, int durationSec) {
    db.update("update mock_tests set title = :t, description = :d, duration_sec = :s, updated_at = now() where id = :id",
        new MapSqlParameterSource("id", id).addValue("t", title).addValue("d", description).addValue("s", durationSec));
  }

  public void deleteQuestionsOf(UUID testId) {
    db.update("delete from mock_questions where test_id = :t", Map.of("t", testId));
  }

  public void deleteTest(UUID id) {
    db.update("delete from mock_tests where id = :id", Map.of("id", id));
  }

  public UUID insertQuestion(UUID testId, String body, String explanation, String chapterTag, int position) {
    UUID id = Ids.uuidv7();
    db.update("insert into mock_questions (id, test_id, body, explanation, chapter_tag, position) values (:id, :t, :b, :e, :c, :p)",
        new MapSqlParameterSource("id", id).addValue("t", testId).addValue("b", body).addValue("e", explanation).addValue("c", chapterTag).addValue("p", position));
    return id;
  }

  public void insertOption(UUID questionId, String body, boolean isCorrect, int position) {
    db.update("insert into mock_options (id, question_id, body, is_correct, position) values (:id, :q, :b, :c, :p)",
        new MapSqlParameterSource("id", Ids.uuidv7()).addValue("q", questionId).addValue("b", body).addValue("c", isCorrect).addValue("p", position));
  }

  // ---------------------------------------------------------------- flashcards

  public int countFlashcards(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return 0;
    return db.queryForObject("select count(*)::int from flashcards where course_id in (:ids)", new MapSqlParameterSource("ids", courseIds), Integer.class);
  }

  public List<FlashcardRow> flashcards(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select id, course_id, front, back from flashcards where course_id in (:ids) order by position asc", new MapSqlParameterSource("ids", courseIds),
        (rs, i) -> new FlashcardRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)));
  }

  public Optional<FlashcardRow> flashcard(UUID id) {
    return db.query("select id, course_id, front, back from flashcards where id = :id", Map.of("id", id),
        (rs, i) -> new FlashcardRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4))).stream().findFirst();
  }

  public void deleteFlashcards(UUID courseId) {
    db.update("delete from flashcards where course_id = :c", Map.of("c", courseId));
  }

  public void insertFlashcard(UUID courseId, String front, String back, int position) {
    db.update("insert into flashcards (id, course_id, front, back, position) values (:id, :c, :f, :b, :p)",
        new MapSqlParameterSource("id", Ids.uuidv7()).addValue("c", courseId).addValue("f", front).addValue("b", back).addValue("p", position));
  }
}
