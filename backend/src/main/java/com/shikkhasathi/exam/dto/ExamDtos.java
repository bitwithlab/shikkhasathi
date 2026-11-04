package com.shikkhasathi.exam.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request/response records of the {@code exam} and {@code sellerExam} contract groups. */
public final class ExamDtos {
  private ExamDtos() {}

  public static final String EXAM_TYPE_RE = "midterm|final|quiz|lab|viva|admission|general";
  /** ISO-8601 date-time with an offset (Z or ±hh[:mm]), like z.iso.datetime({ offset: true }) */
  public static final String ISO_OFFSET_RE = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)$";

  static String trim(String s) {
    return s == null ? null : s.strip();
  }

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }

  // ---------------------------------------------------------------- requests

  public record CreatePlan(
      @NotNull @Size(min = 2, max = 160) String subjectName,
      UUID subjectMasterId,
      @Size(max = 30) String courseCode,
      @NotNull @Pattern(regexp = EXAM_TYPE_RE) String examType,
      @NotNull @Pattern(regexp = ISO_OFFSET_RE) String examDate,
      @Size(max = 5) List<@NotNull UUID> courseIds) {
    public CreatePlan {
      subjectName = trim(subjectName);
      courseCode = trim(courseCode);
      if (courseIds == null) courseIds = List.of();
    }
  }

  /** PATCH body: every field optional; {@link #has} tells "absent" from "null". */
  public static final class UpdatePlan {
    private final Set<String> present = new HashSet<>();

    @Size(min = 2, max = 160) private String subjectName;
    private UUID subjectMasterId;
    @Size(max = 30) private String courseCode;
    @Pattern(regexp = EXAM_TYPE_RE) private String examType;
    @Pattern(regexp = ISO_OFFSET_RE) private String examDate;
    @Size(max = 5) private List<@NotNull UUID> courseIds;
    private Boolean active;

    public boolean has(String field) {
      return present.contains(field);
    }

    // Zod's .optional() rejects explicit null for non-nullable fields
    @JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Invalid input")
    public boolean isNonNullableFieldsNotNull() {
      for (String f : List.of("subjectName", "examType", "examDate", "courseIds", "active")) {
        if (!present.contains(f)) continue;
        Object v = switch (f) {
          case "subjectName" -> subjectName;
          case "examType" -> examType;
          case "examDate" -> examDate;
          case "courseIds" -> courseIds;
          default -> active;
        };
        if (v == null) return false;
      }
      return true;
    }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { present.add("subjectName"); subjectName = trim(v); }
    public UUID getSubjectMasterId() { return subjectMasterId; }
    public void setSubjectMasterId(UUID v) { present.add("subjectMasterId"); subjectMasterId = v; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String v) { present.add("courseCode"); courseCode = trim(v); }
    public String getExamType() { return examType; }
    public void setExamType(String v) { present.add("examType"); examType = v; }
    public String getExamDate() { return examDate; }
    public void setExamDate(String v) { present.add("examDate"); examDate = v; }
    public List<UUID> getCourseIds() { return courseIds; }
    public void setCourseIds(List<UUID> v) { present.add("courseIds"); courseIds = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { present.add("active"); active = v; }
  }

  public record ReviewCard(@NotNull Boolean known) {}

  public record SaveAnswer(@NotNull UUID questionId, UUID optionId, Boolean flagged) {}

  public record OptionInput(@NotNull @Size(min = 1, max = 1000) String body, @NotNull Boolean isCorrect) {
    public OptionInput {
      body = trim(body);
    }
  }

  public record QuestionInput(
      @NotNull @Size(min = 3, max = 3000) String body,
      @Size(max = 3000) String explanation,
      @Size(max = 80) String chapterTag,
      @NotNull @Size(min = 2, max = 6) List<@Valid @NotNull OptionInput> options) {
    public QuestionInput {
      body = trim(body);
      explanation = trim(explanation);
      chapterTag = trim(chapterTag);
    }
  }

  public record MockTestInput(
      @NotNull @Size(min = 3, max = 160) String title,
      @Size(max = 1000) String description,
      @Min(300) @Max(4 * 3600) Integer durationSec,
      @NotNull @Size(min = 1, max = 150) List<@Valid @NotNull QuestionInput> questions) {
    public MockTestInput {
      title = trim(title);
      description = trim(description);
      if (durationSec == null) durationSec = 1800;
    }
  }

  public record FlashcardInput(@NotNull @Size(min = 1, max = 500) String front, @NotNull @Size(min = 1, max = 2000) String back) {
    public FlashcardInput {
      front = trim(front);
      back = trim(back);
    }
  }

  public record SetFlashcards(@NotNull @Size(max = 300) List<@Valid @NotNull FlashcardInput> cards) {}

  // ---------------------------------------------------------------- responses

  public record PlanSummary(UUID id, String subjectName, String courseCode, String examType, Instant examDate, int daysLeft, int readiness, List<UUID> courseIds, boolean active, boolean past) {}

  public record PlanList(List<PlanSummary> items) {}

  /** One plan item; {@code kind} is lesson | mock | flashcards | revision. */
  public sealed interface PlanItem permits LessonPlanItem, MockPlanItem, FlashcardsPlanItem, RevisionPlanItem {
    String kind();

    boolean done();
  }

  public record LessonPlanItem(String kind, UUID lessonId, UUID courseId, String title, String chapterTitle, int durationSec, String lessonType, boolean isImportant, boolean done) implements PlanItem {}

  public record MockPlanItem(String kind, UUID testId, String title, int questionCount, boolean done) implements PlanItem {}

  public record FlashcardsPlanItem(String kind, int count, boolean done) implements PlanItem {}

  public record RevisionPlanItem(String kind, String title, boolean done) implements PlanItem {}

  public record PlanDay(String date, List<PlanItem> items, int totalMinutes, boolean isRevision) {}

  public record SyllabusChapter(UUID courseId, UUID chapterId, String title, String sectionTitle, int done, int total, int important, String status) {}

  public record MockSummary(UUID id, UUID courseId, String courseTitle, String title, int questionCount, int durationSec, int attempts, Integer bestScore, Integer lastScore, UUID inProgressAttemptId) {}

  public record MockList(List<MockSummary> items) {}

  public record ReadinessBreakdown(int lessons, Integer mock, Integer flashcards) {}

  public record PlanCourse(UUID id, String slug, String title, boolean enrolled) {}

  public record ImportantLesson(UUID lessonId, UUID courseId, String title, boolean done, int durationSec) {}

  public record ShortLecture(UUID lessonId, UUID courseId, String title, int durationSec, boolean done) {}

  public record PreviousQuestion(UUID id, String title, Integer year, UUID courseId) {}

  public record FlashStats(int total, int known) {}

  public record RevisionPack(int importantLessons, int notes, int bookmarks, int wrongAnswers) {}

  public record PlanDetail(
      UUID id, String subjectName, String courseCode, String examType, Instant examDate, int daysLeft, int readiness, List<UUID> courseIds, boolean active, boolean past,
      ReadinessBreakdown readinessBreakdown, List<PlanCourse> courses, PlanDay today, List<PlanDay> days, List<SyllabusChapter> syllabus,
      List<ImportantLesson> important, List<ShortLecture> shortLectures, List<PreviousQuestion> previousQuestions, List<MockSummary> mockTests,
      FlashStats flashcards, boolean revisionUnlocked, RevisionPack revisionPack) {}

  public record FlashcardItem(UUID id, UUID courseId, String front, String back, Boolean known) {}

  public record FlashcardList(List<FlashcardItem> items) {}

  public record AttemptOption(UUID id, String body) {}

  public record AttemptQuestion(UUID id, String body, String chapterTag, List<AttemptOption> options) {}

  public record AttemptAnswer(UUID questionId, UUID optionId, boolean flagged) {}

  public record Attempt(UUID id, UUID testId, UUID courseId, String title, int durationSec, Instant startedAt, Instant deadlineAt, Instant serverNow, boolean submitted,
                        List<AttemptQuestion> questions, List<AttemptAnswer> answers) {}

  public record ChapterScore(String tag, int correct, int total) {}

  public record ReviewItem(UUID questionId, String body, String chapterTag, String explanation, UUID chosenOptionId, UUID correctOptionId, List<AttemptOption> options) {}

  public record AttemptResult(UUID attemptId, UUID testId, UUID courseId, String title, int score, int correct, int wrong, int skipped, int total, int timeUsedSec,
                              boolean autoSubmitted, int bestScore, int attempts, List<ChapterScore> chapters, List<ReviewItem> review) {}

  public record SellerOption(String body, boolean isCorrect) {}

  public record SellerQuestion(UUID id, String body, String explanation, String chapterTag, List<SellerOption> options) {}

  public record SellerMockTest(UUID id, String title, String description, int durationSec, List<SellerQuestion> questions, int attempts) {}

  public record SellerMockList(List<SellerMockTest> items) {}

  public record SellerFlashcard(UUID id, String front, String back) {}

  public record SellerFlashcardList(List<SellerFlashcard> items) {}
}
