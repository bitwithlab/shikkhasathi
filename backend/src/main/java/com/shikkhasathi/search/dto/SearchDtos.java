package com.shikkhasathi.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shikkhasathi.search.service.QueryExpansion.Interpretation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Contract group {@code discovery} (search + suggest) and the shared course card. */
public final class SearchDtos {
  private SearchDtos() {}

  public static final String EXAM_TYPES = "midterm|final|quiz|lab|viva|admission|general";
  public static final String COURSE_TYPES = "crash|full|solve|suggestion|lab";

  /** GET /search query (strings are trimmed before validation, like the previous zod schema). */
  public record SearchQuery(
      @Size(max = 120) String q,
      UUID institutionId,
      UUID departmentId,
      @Min(1) @Max(16) Integer semester,
      UUID subjectId,
      @Size(max = 30) String courseCode,
      @Size(max = 12) String batch,
      @Size(max = 12) String section,
      @Pattern(regexp = EXAM_TYPES) String examType,
      @Pattern(regexp = COURSE_TYPES) String courseType,
      @Pattern(regexp = "free|paid") String price,
      @Min(0) Long minPrice,
      @Min(0) Long maxPrice,
      @Pattern(regexp = "bn|en|mixed") String language,
      @DecimalMin("0") @DecimalMax("5") Double rating,
      @Pattern(regexp = "short|medium|long") String duration,
      UUID sellerId,
      @Pattern(regexp = "best|newest|rating|price_asc|price_desc|popular") String sort,
      String cursor,
      @Min(1) @Max(60) Integer limit) {
    public SearchQuery {
      q = trim(q);
      courseCode = trim(courseCode);
      batch = trim(batch);
      section = trim(section);
      if (sort == null) sort = "best";
      if (limit == null) limit = 24;
    }

    /** Internal query (featured, recommendations): only sort/limit/institution. */
    public static SearchQuery of(String sort, int limit, UUID institutionId) {
      return new SearchQuery(null, institutionId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, sort, null, limit);
    }
  }

  /** GET /search/suggest query */
  public record SuggestQuery(@NotBlank @Size(min = 1, max = 80) String q) {
    public SuggestQuery {
      q = trim(q);
    }
  }

  static String trim(String s) {
    return s == null ? null : s.trim();
  }

  /** Course card everywhere (search, browse, recommendations, storefront, wishlist). {@code score} only on ranked lists. */
  public record CourseCard(
      UUID id, String slug, String title, String shortDescription, String courseCode, String institutionShort, String sellerName, String sellerSlug, boolean sellerVerified,
      String examType, String courseType, long price, Long compareAtPrice, double ratingAvg, int ratingCount, String coverUrl, String coverColor, int lessonCount,
      int totalDurationSec, int enrollmentCount, String compatibility, @JsonInclude(JsonInclude.Include.NON_NULL) Double score) {}

  public record SearchResponse(List<CourseCard> items, int total, String nextCursor, List<Interpretation> interpreted, String engine) {}

  public record Suggestion(String type, String id, String label, String sub, String href) {}

  public record SuggestResponse(List<Suggestion> items) {}
}
