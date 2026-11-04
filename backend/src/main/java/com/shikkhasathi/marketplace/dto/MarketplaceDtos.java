package com.shikkhasathi.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shikkhasathi.search.dto.SearchDtos.CourseCard;
import com.shikkhasathi.search.dto.SearchDtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Contract groups {@code discovery} (details, storefront, featured, recommendations), {@code wishlist} and {@code requests}. */
public final class MarketplaceDtos {
  private MarketplaceDtos() {}

  public record Ok(boolean ok) {
    public static final Ok TRUE = new Ok(true);
  }

  public record Items(List<CourseCard> items) {}

  // ---------------------------------------------------------------- course details

  public record Crumb(String label, String href) {}

  public record TargetLabel(String label, String institutionSlug, String courseCode, Integer semester, List<String> batches) {}

  public record SellerCard(UUID id, String slug, String name, String headline, boolean verified, double ratingAvg, int studentCount, int courseCount) {}

  public record PublicLesson(UUID id, String title, String type, int durationSec, boolean isPreview, boolean isImportant) {}

  public record ChapterDto(UUID id, String title, List<PublicLesson> lessons) {}

  public record SectionDto(UUID id, String title, List<ChapterDto> chapters) {}

  public record Resources(int videos, int notes, List<Integer> previousQuestionYears, int mockTests, int flashcards) {}

  public record Viewer(boolean enrolled, boolean wishlisted, UUID firstLessonId, UUID resumeLessonId, boolean isOwner) {}

  // ---------------------------------------------------------------- storefront + landing

  public record StorefrontStats(int students, int courses, double ratingAvg, int ratingCount) {}

  public record Storefront(UUID id, String slug, String name, String headline, String bio, String coverUrl, boolean verified, String institutionName, List<String> institutionsTaught,
                           StorefrontStats stats, List<CourseCard> courses) {}

  public record FeaturedTeacher(String slug, String name, String headline, String institutionName, double ratingAvg, int studentCount, int courseCount) {}

  public record FeaturedStats(int students, int courses, int institutions) {}

  public record Featured(List<CourseCard> courses, List<FeaturedTeacher> teachers, FeaturedStats stats) {}

  public record FeaturedQuery(UUID institutionId) {}

  // ---------------------------------------------------------------- course requests

  public record CourseRequestDto(UUID id, UUID institutionId, String institutionShort, UUID subjectId, String subjectName, String courseCode, String examType, String note, String status,
                                 int voteCount, boolean voted, boolean mine, String claimedByName, String linkedCourseSlug, String createdAt) {}

  public record RequestPage(List<CourseRequestDto> items, String nextCursor) {}

  public record CreateResult(CourseRequestDto request, boolean duplicate) {}

  public record ListRequestsQuery(
      String cursor,
      @Min(1) @Max(100) Integer limit,
      @Pattern(regexp = "open|teacher_working|published|closed|all") String status,
      UUID institutionId,
      @Size(max = 80) String q,
      @Pattern(regexp = "votes|newest") String sort) {
    public ListRequestsQuery {
      if (limit == null) limit = 20;
      if (status == null) status = "all";
      if (sort == null) sort = "votes";
    }
  }

  /** {@code institutionId} is required but nullable (present as null for "any institution"). */
  public record CreateCourseRequest(
      @JsonProperty(required = true) UUID institutionId,
      UUID subjectId,
      @NotNull @Size(min = 2, max = 120) String subjectName,
      @Size(max = 30) String courseCode,
      @NotNull @Pattern(regexp = SearchDtos.EXAM_TYPES) String examType,
      @Size(max = 500) String note) {
    public CreateCourseRequest {
      subjectName = subjectName == null ? null : subjectName.trim();
      courseCode = courseCode == null ? null : courseCode.trim();
      note = note == null ? null : note.trim();
    }
  }

  public record ClaimBody(UUID courseId) {}
}
