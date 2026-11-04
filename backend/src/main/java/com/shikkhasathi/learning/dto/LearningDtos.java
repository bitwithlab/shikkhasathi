package com.shikkhasathi.learning.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response records of the {@code learning} and {@code sellerLearning} contract groups. */
public final class LearningDtos {
  private LearningDtos() {}

  private static String trim(String s) {
    return s == null ? null : s.strip();
  }

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  // ---------------------------------------------------------------- My Courses

  public record MyCourse(UUID courseId, String slug, String title, String courseCode, String institutionShort, String examType, String sellerName,
                         String coverColor, int progressPercent, int completedLessons, int totalLessons, UUID resumeLessonId, String resumeLessonTitle,
                         Instant lastAccessedAt, Instant completedAt, Instant accessUntil, boolean expiringSoon, boolean expired) {}

  public record MyCourses(List<MyCourse> items) {}

  /** Dashboard "continue learning" widget data. */
  public record ContinueWidget(UUID courseId, String courseSlug, String title, String courseCode, String sellerName, int progressPercent,
                               int completedLessons, int totalLessons, UUID lastLessonId, String lastLessonTitle, String coverColor) {}

  // ---------------------------------------------------------------- player

  public record OutlineCourse(UUID id, String slug, String title, String courseCode, UUID sellerId, String sellerName, String sellerSlug) {}

  public record PlayerLesson(UUID id, String title, String type, int durationSec, boolean isPreview, boolean isImportant, boolean completed,
                             int lastPositionSec, boolean locked) {}

  public record PlayerChapter(UUID id, String title, List<PlayerLesson> lessons) {}

  public record PlayerSection(UUID id, String title, List<PlayerChapter> chapters) {}

  public record Resource(UUID id, String kind, String title, Integer year) {}

  public record MyReview(UUID id, int rating, String body) {}

  public record PlayerOutline(OutlineCourse course, boolean enrolled, boolean isOwner, Instant accessUntil, int progressPercent, int completedLessons,
                              int totalLessons, UUID resumeLessonId, List<PlayerSection> sections, List<Resource> resources, MyReview myReview,
                              boolean canReview) {}

  public record LessonDocument(UUID id, String title, String url, Integer pages) {}

  public record LessonVideo(String kind, String status) {}

  public record LessonDetail(UUID id, UUID courseId, String title, String type, int durationSec, boolean isImportant, String body, LessonDocument document,
                             LessonVideo video, boolean completed, int lastPositionSec, UUID prevLessonId, UUID nextLessonId) {}

  public record PlaybackBody(@NotNull @Size(min = 8, max = 80) String deviceId, @Size(max = 80) String deviceName) {}

  public record Playback(String kind, String url, Instant expiresAt, String streamToken, String watermark, int startAtSec) {}

  public record ProgressBody(@NotNull @Min(0) @Max(86_400) Integer positionSec, @NotNull @Min(0) @Max(60) Integer deltaSec, @Size(max = 80) String streamToken) {}

  public record CompleteBody(@NotNull Boolean completed) {}

  public record ProgressResponse(boolean completed, int progressPercent, boolean streamActive) {}

  public record DocumentUrl(String url, String title) {}

  // ---------------------------------------------------------------- notes & bookmarks

  public record Note(UUID id, UUID courseId, UUID lessonId, String lessonTitle, String courseTitle, Integer timestampSec, String body, Instant createdAt, Instant updatedAt) {}

  public record Bookmark(UUID id, UUID courseId, UUID lessonId, String lessonTitle, String courseTitle, Integer timestampSec, String body, Instant createdAt) {}

  public record CreateNote(@NotNull UUID lessonId, @Min(0) Integer timestampSec, @NotNull @Size(min = 1, max = 5000) String body) {
    public CreateNote {
      body = trim(body);
    }
  }

  public record UpdateNote(@NotNull @Size(min = 1, max = 5000) String body) {
    public UpdateNote {
      body = trim(body);
    }
  }

  public record CreateBookmark(@NotNull UUID lessonId, @Min(0) Integer timestampSec, @Size(max = 200) String body) {
    public CreateBookmark {
      body = trim(body);
    }
  }

  // ---------------------------------------------------------------- Q&A

  public record Answer(UUID id, String body, String authorName, boolean isSeller, Instant createdAt) {}

  public record Question(UUID id, UUID lessonId, String lessonTitle, String body, Integer timestampSec, String authorName, boolean mine, String status,
                         Instant createdAt, List<Answer> answers) {}

  public record SellerQuestion(UUID id, UUID lessonId, String lessonTitle, String body, Integer timestampSec, String authorName, boolean mine, String status,
                               Instant createdAt, List<Answer> answers, UUID courseId, String courseTitle) {
    public static SellerQuestion of(Question q, UUID courseId, String courseTitle) {
      return new SellerQuestion(q.id(), q.lessonId(), q.lessonTitle(), q.body(), q.timestampSec(), q.authorName(), q.mine(), q.status(), q.createdAt(), q.answers(), courseId, courseTitle);
    }
  }

  public record AskBody(@NotNull @Size(min = 5, max = 3000) String body, @Min(0) Integer timestampSec) {
    public AskBody {
      body = trim(body);
    }
  }

  public record AnswerBody(@NotNull @Size(min = 2, max = 5000) String body) {
    public AnswerBody {
      body = trim(body);
    }
  }

  // ---------------------------------------------------------------- reviews

  public record Review(UUID id, int rating, String body, String authorName, Instant createdAt, String sellerReply, Instant sellerRepliedAt, boolean verified) {}

  public record SellerReview(UUID id, int rating, String body, String authorName, Instant createdAt, String sellerReply, Instant sellerRepliedAt, boolean verified,
                             UUID courseId, String courseTitle) {
    public static SellerReview of(Review r, UUID courseId, String courseTitle) {
      return new SellerReview(r.id(), r.rating(), r.body(), r.authorName(), r.createdAt(), r.sellerReply(), r.sellerRepliedAt(), r.verified(), courseId, courseTitle);
    }
  }

  public record ReviewBody(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String body) {
    public ReviewBody {
      body = trim(body);
    }
  }

  public record ReplyBody(@NotNull @Size(min = 2, max = 2000) String reply) {
    public ReplyBody {
      reply = trim(reply);
    }
  }
}
