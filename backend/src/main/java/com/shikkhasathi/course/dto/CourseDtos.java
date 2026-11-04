package com.shikkhasathi.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response records of the {@code sellerCourses} and {@code adminReview} contract groups. */
public final class CourseDtos {
  private CourseDtos() {}

  public static final String EXAM_TYPES = "midterm|final|quiz|lab|viva|admission|general";
  public static final String COURSE_TYPES = "crash|full|solve|suggestion|lab";
  public static final String ACCESS_TYPES = "lifetime|days|until_exam";
  public static final String LESSON_TYPES = "video|pdf|text|quiz|mock";
  public static final String DOCUMENT_KINDS = "notes|pdf|previous_questions|slides|link";
  public static final String LANGUAGES = "bn|en|mixed";
  /** any absolute URL (scheme + something), like z.url() */
  public static final String URL = "^[A-Za-z][A-Za-z0-9+.\\-]*:[^\\s]+$";

  static String trim(String s) {
    return s == null ? null : s.strip();
  }

  static List<String> trimAll(List<String> l) {
    return l == null ? null : l.stream().map(CourseDtos::trim).toList();
  }

  // ---------------------------------------------------------------- authoring inputs

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CreateCourse(@NotNull @Size(min = 5, max = 160) String title) {
    public CreateCourse {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FaqItem(@NotNull @Size(min = 1, max = 300) String q, @NotNull @Size(min = 1, max = 2000) String a) {
    public FaqItem {
      q = trim(q);
      a = trim(a);
    }
  }

  /** PATCH body: basics + pricing, every field optional, no defaults (patchOf). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CoursePatch(
      @Size(min = 5, message = "শিরোনাম কমপক্ষে ৫ অক্ষর") @Size(max = 160) String title,
      @Size(max = 300) String shortDescription,
      @Size(max = 10_000) String description,
      @Size(max = 15) List<@NotNull @Size(min = 1, max = 200) String> learnings,
      @Size(max = 10) List<@NotNull @Size(min = 1, max = 200) String> notFor,
      @Size(max = 15) List<@Valid @NotNull FaqItem> faq,
      @Size(max = 500) String coverUrl,
      @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String coverColor,
      UUID introVideoId,
      @Pattern(regexp = LANGUAGES) String language,
      @Pattern(regexp = EXAM_TYPES) String examType,
      @Pattern(regexp = COURSE_TYPES) String courseType,
      @Min(0) @Max(5_000_000) Long price,
      @Min(0) @Max(5_000_000) Long compareAtPrice,
      @Pattern(regexp = ACCESS_TYPES) String accessType,
      @Min(1) @Max(3650) Integer accessDays,
      String accessUntil) {
    public static final List<String> NULLABLE = List.of("coverUrl", "coverColor", "introVideoId", "compareAtPrice", "accessDays", "accessUntil");

    public CoursePatch {
      title = trim(title);
      shortDescription = trim(shortDescription);
      description = trim(description);
      learnings = trimAll(learnings);
      notFor = trimAll(notFor);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TargetInput(
      Boolean isGeneral,
      UUID institutionId,
      UUID departmentId,
      UUID programId,
      @Size(max = 40) String academicLevel,
      @Min(1) @Max(16) Integer semester,
      UUID subjectMasterId,
      @Size(max = 30) String courseCode,
      @Size(max = 20) List<@NotNull @Size(min = 1, max = 12) String> batches,
      @Size(max = 20) List<@NotNull @Size(min = 1, max = 12) String> sections) {
    public TargetInput {
      if (isGeneral == null) isGeneral = false;
      courseCode = trim(courseCode);
      batches = batches == null ? List.of() : trimAll(batches);
      sections = sections == null ? List.of() : trimAll(sections);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SetTargets(@NotNull @Size(min = 1, max = 20) List<@Valid @NotNull TargetInput> targets) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LessonInput(
      UUID id,
      @NotNull @Size(min = 1, max = 200) String title,
      @NotNull @Pattern(regexp = LESSON_TYPES) String type,
      @Min(0) @Max(36_000) Integer durationSec,
      UUID videoAssetId,
      UUID documentId,
      @Size(max = 50_000) String body,
      Boolean isPreview,
      Boolean isImportant) {
    public LessonInput {
      title = trim(title);
      if (durationSec == null) durationSec = 0;
      if (isPreview == null) isPreview = false;
      if (isImportant == null) isImportant = false;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ChapterInput(UUID id, @NotNull @Size(min = 1, max = 200) String title, @NotNull @Size(max = 100) List<@Valid @NotNull LessonInput> lessons) {
    public ChapterInput {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SectionInput(UUID id, @NotNull @Size(min = 1, max = 200) String title, @NotNull @Size(max = 50) List<@Valid @NotNull ChapterInput> chapters) {
    public SectionInput {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CurriculumBody(@NotNull @Size(max = 30) List<@Valid @NotNull SectionInput> sections) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubmitBody(@NotNull @AssertTrue Boolean declaration) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VisibilityBody(@NotNull @Pattern(regexp = "unlisted|archived|published") String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProposeSubject(@NotNull @Size(min = 2, max = 160) String name, @Size(max = 10) List<@NotNull @Size(max = 80) String> aliases) {
    public ProposeSubject {
      name = trim(name);
      aliases = aliases == null ? List.of() : trimAll(aliases);
    }
  }

  /** Discriminated union on {@code kind} (UploadBody). */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = VideoUpload.class, name = "video"),
      @JsonSubTypes.Type(value = VideoLinkUpload.class, name = "video_link"),
      @JsonSubTypes.Type(value = DocumentUpload.class, name = "document"),
      @JsonSubTypes.Type(value = DocumentLinkUpload.class, name = "document_link"),
      @JsonSubTypes.Type(value = CoverUpload.class, name = "cover"),
      @JsonSubTypes.Type(value = IdCardUpload.class, name = "id_card")})
  public sealed interface UploadBody permits VideoUpload, VideoLinkUpload, DocumentUpload, DocumentLinkUpload, CoverUpload, IdCardUpload {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VideoUpload(@NotNull @Size(min = 1, max = 200) String title, @Positive @Max(8L * 1024 * 1024 * 1024) Long size) implements UploadBody {
    public VideoUpload {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VideoLinkUpload(@NotNull @Size(min = 1, max = 200) String title, @NotNull @Pattern(regexp = URL) String url, @Min(0) @Max(36_000) Integer durationSec) implements UploadBody {
    public VideoLinkUpload {
      title = trim(title);
      if (durationSec == null) durationSec = 0;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentUpload(@NotNull UUID courseId, @NotNull @Pattern(regexp = DOCUMENT_KINDS) String docKind, @NotNull @Size(min = 1, max = 200) String title,
                               @Min(1990) @Max(2100) Integer year, @NotNull @Size(max = 100) String mime, @NotNull @Positive @Max(100L * 1024 * 1024) Long size) implements UploadBody {
    public DocumentUpload {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentLinkUpload(@NotNull UUID courseId, @NotNull @Pattern(regexp = DOCUMENT_KINDS) String docKind, @NotNull @Size(min = 1, max = 200) String title,
                                   @Min(1990) @Max(2100) Integer year, @NotNull @Pattern(regexp = URL) String url) implements UploadBody {
    public DocumentLinkUpload {
      title = trim(title);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CoverUpload(@NotNull @Pattern(regexp = "image/jpeg|image/png|image/webp") String mime, @NotNull @Positive @Max(5L * 1024 * 1024) Long size) implements UploadBody {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IdCardUpload(@NotNull @Pattern(regexp = "image/jpeg|image/png|image/webp|application/pdf") String mime, @NotNull @Positive @Max(10L * 1024 * 1024) Long size) implements UploadBody {}

  // ---------------------------------------------------------------- admin review inputs

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ReviewChecklist(@NotNull Boolean quality, @NotNull Boolean noGuaranteeClaims, @NotNull Boolean targeting, @NotNull Boolean copyright) {
    public boolean allChecked() {
      return quality && noGuaranteeClaims && targeting && copyright;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ReviewDecision(@NotNull @Pattern(regexp = "publish|changes_requested|reject") String decision, @Size(max = 2000) String note, @NotNull @Valid ReviewChecklist checklist) {}

  // ---------------------------------------------------------------- outputs

  public record VideoInfo(UUID id, String provider, String status, int durationSec, String title, String externalUrl) {}

  public record DocumentInfo(UUID id, String kind, String title, Integer year, String mime, Integer size, String externalUrl, boolean hasFile) {}

  public record LessonDto(UUID id, String title, String type, int durationSec, UUID videoAssetId, UUID documentId, String body, boolean isPreview, boolean isImportant,
                          VideoInfo video, DocumentInfo document) {}

  public record ChapterDto(UUID id, String title, List<LessonDto> lessons) {}

  public record SectionDto(UUID id, String title, List<ChapterDto> chapters) {}

  public record TargetDto(UUID id, boolean isGeneral, UUID institutionId, UUID departmentId, UUID programId, String academicLevel, Integer semester, UUID subjectMasterId,
                          String courseCode, List<String> batches, List<String> sections, String institutionShort, String departmentShort, String subjectName) {}

  public record SellerCourseListItem(UUID id, String slug, String title, String status, long price, int lessonCount, int enrollmentCount, double ratingAvg, int ratingCount,
                                     String updatedAt, String coverColor, String courseCode) {}

  public record CourseList(List<SellerCourseListItem> items) {}

  public record ChecklistItem(String key, boolean ok, String message) {}

  public record Checklist(List<ChecklistItem> items, boolean canSubmit) {}

  public record UploadInstructions(UUID id, String uploadUrl, String method, Map<String, String> headers, String key, String publicUrl) {}

  public record SubjectRef(UUID id, String status) {}

  public record ReviewItem(UUID id, String title, UUID sellerId, String sellerName, boolean sellerVerified, String status, String submittedAt, long price, int lessonCount,
                           List<String> targets, String kind) {}

  public record ReviewPage(List<ReviewItem> items, String nextCursor) {}

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }
}
