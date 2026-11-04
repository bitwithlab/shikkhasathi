package com.shikkhasathi.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

/** Request/response records of the {@code catalog} and {@code adminCatalog} contract groups. */
public final class CatalogDtos {
  private CatalogDtos() {}

  public static final String INSTITUTION_TYPE_RE = "public|private|college|polytechnic";
  public static final String INSTITUTION_STATUS_RE = "pending|approved|merged|rejected";
  public static final String SUBJECT_STATUS_RE = "approved|proposed|rejected";
  public static final String EXAM_TYPE_RE = "midterm|final|quiz|lab|viva|admission|general";
  public static final String SLUG_RE = "^[a-z0-9-]+$";

  // ---------------------------------------------------------------- responses

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }

  public record Items<T>(List<T> items) {}

  public record Page<T>(List<T> items, String nextCursor) {}

  public record InstitutionItem(UUID id, String name, String nameBn, String shortName, String slug, String type, String city, String logoUrl, int courseCount, int teacherCount) {}

  public record AdminInstitution(UUID id, String name, String nameBn, String shortName, String slug, String type, String city, String logoUrl, int courseCount, int teacherCount,
                                 String status, List<String> aliases, String website) {}

  public record DepartmentItem(UUID id, UUID institutionId, String name, String nameBn, String shortName, String icon) {}

  public record ProgramItem(UUID id, UUID departmentId, String name, String level, int semesters) {}

  public record DepartmentDetail(UUID id, UUID institutionId, String name, String nameBn, String shortName, String icon, List<ProgramItem> programs, int subjectCount) {}

  public record InstitutionSubjectItem(UUID id, UUID subjectId, String subjectName, String subjectSlug, UUID departmentId, Integer semester, String courseCode, int courseCount) {}

  public record InstitutionDetail(UUID id, String name, String nameBn, String shortName, String slug, String type, String city, String logoUrl, int courseCount, int teacherCount,
                                  String website, List<String> aliases, List<DepartmentDetail> departments, List<InstitutionSubjectItem> subjects) {}

  public record SubjectItem(UUID id, String name, String nameBn, String slug, List<String> aliases, String category, int courseCount) {}

  /** Subject list scoped to an institution: carries the institution's course code (may be null). */
  public record ScopedSubjectItem(UUID id, String name, String nameBn, String slug, List<String> aliases, String category, int courseCount, String courseCode) {}

  public record AdminSubject(UUID id, String name, String nameBn, String slug, List<String> aliases, String category, int courseCount, String status) {}

  public record Offering(UUID institutionId, String institutionName, String institutionShort, String institutionSlug, String courseCode, Integer semester) {}

  public record SubjectDetail(UUID id, String name, String nameBn, String slug, List<String> aliases, String category, int courseCount, List<Offering> offerings) {}

  public record IdStatus(UUID id, String status) {}

  public record IdOnly(UUID id) {}

  public record Requester(UUID id, String name) {}

  public record Similar(UUID id, String name, String shortName) {}

  public record InstitutionRequestItem(UUID id, String name, String type, String city, String note, String status, Requester requestedBy, List<Similar> similar, String createdAt) {}

  // ---------------------------------------------------------------- requests (validated by RequestBodies)

  public record InstitutionRequestBody(
      @NotNull @Size(min = 3, max = 160, message = "প্রতিষ্ঠানের পুরো নাম লিখুন") String name,
      @NotNull @Pattern(regexp = INSTITUTION_TYPE_RE) String type,
      @Size(max = 80) String city,
      @Size(max = 500) String note) {}

  public record AcademicProfileBody(
      UUID institutionId,
      UUID departmentId,
      UUID programId,
      @Min(1) @Max(12) Integer semester,
      @Size(max = 20) String batch,
      @Size(max = 20) String section) {}

  public record NextExamInput(
      UUID subjectId,
      @NotNull @Size(min = 2, max = 120) String subjectName,
      @Size(max = 30) String courseCode,
      @NotNull @Pattern(regexp = EXAM_TYPE_RE) String examType,
      @NotNull String examDate) {}

  public record OnboardingBody(@Valid AcademicProfileBody academic, @Valid NextExamInput nextExam) {}

  public record ApproveDecision(@Size(min = 3, max = 160) String name, @NotNull @Size(min = 2, max = 20) String shortName, @Pattern(regexp = SLUG_RE) String slug, @Size(max = 80) String city) {}

  public record MergeDecision(@NotNull UUID institutionId) {}

  public record RejectDecision(@Size(max = 300) String note) {}

  public record InstitutionUpsert(
      @NotNull @Size(min = 3, max = 160) String name,
      @Size(max = 160) String nameBn,
      @NotNull @Size(min = 2, max = 20) String shortName,
      @NotNull @Pattern(regexp = SLUG_RE, message = "ছোট হাতের ইংরেজি, সংখ্যা ও - ব্যবহার করুন") String slug,
      @NotNull @Pattern(regexp = INSTITUTION_TYPE_RE) String type,
      @Size(max = 80) String city,
      @URL String website,
      @URL String logoUrl,
      @Size(max = 20) List<@NotNull @Size(min = 1, max = 80) String> aliases,
      @Pattern(regexp = INSTITUTION_STATUS_RE) String status) {}

  public record MergeBody(@NotNull UUID intoId) {}

  public record DepartmentUpsert(
      @NotNull UUID institutionId,
      @NotNull @Size(min = 2, max = 160) String name,
      @Size(max = 160) String nameBn,
      @NotNull @Size(min = 2, max = 20) String shortName,
      @Size(max = 40) String icon) {}

  public record ProgramUpsert(@NotNull UUID departmentId, @NotNull @Size(min = 2, max = 160) String name, @Size(max = 40) String level, @Min(1) @Max(16) Integer semesters) {}

  public record SubjectUpsert(
      @NotNull @Size(min = 2, max = 160) String name,
      @Size(max = 160) String nameBn,
      @NotNull @Pattern(regexp = SLUG_RE) String slug,
      @Size(max = 30) List<@NotNull @Size(min = 1, max = 80) String> aliases,
      @Size(max = 40) String category,
      @Pattern(regexp = SUBJECT_STATUS_RE) String status) {}

  public record InstitutionSubjectUpsert(@NotNull UUID institutionId, UUID departmentId, UUID programId, @Min(1) @Max(16) Integer semester, @NotNull UUID subjectMasterId, @Size(max = 30) String courseCode) {}
}
