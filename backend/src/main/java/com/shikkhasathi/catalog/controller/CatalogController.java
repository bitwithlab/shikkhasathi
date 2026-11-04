package com.shikkhasathi.catalog.controller;

import com.shikkhasathi.catalog.controller.RequestBodies.Spec;
import com.shikkhasathi.catalog.dto.CatalogDtos;
import com.shikkhasathi.catalog.dto.CatalogDtos.*;
import com.shikkhasathi.catalog.service.CatalogService;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.CacheControl;
import com.shikkhasathi.security.RateLimit;
import com.shikkhasathi.user.dto.MeDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Public master data + the student's academic profile and onboarding (contract group {@code catalog}). */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  static final String CACHE = "public, max-age=300, stale-while-revalidate=3600";

  static final Spec REQUEST = Spec.of().trim("name", "city", "note").required("name", "type").notNull("name", "type", "city", "note");
  static final Spec ACADEMIC = Spec.of().trim("batch", "section").required("institutionId");
  static final Spec ONBOARDING = Spec.of().notNull("academic");
  static final Spec NEXT_EXAM = Spec.of().trim("subjectName", "courseCode").required("subjectName", "examType", "examDate").notNull("subjectName", "examType", "examDate");

  private final CatalogService svc;
  private final RequestBodies bodies;

  public CatalogController(CatalogService svc, RequestBodies bodies) {
    this.svc = svc;
    this.bodies = bodies;
  }

  /** {@code q}: trimmed, then at most 100 characters. */
  private static void checkQ(String q) {
    if (q != null && q.strip().length() > 100)
      throw ApiException.validation("Too big: expected string to have <=100 characters", List.of(Map.of("path", "query.q", "message", "Too big: expected string to have <=100 characters")));
  }

  @GetMapping("/institutions")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public Page<InstitutionItem> institutions(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) @Pattern(regexp = CatalogDtos.INSTITUTION_TYPE_RE) String type,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    checkQ(q);
    return svc.listInstitutions(q, type, cursor, limit);
  }

  @GetMapping("/institutions/{slug}")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public InstitutionDetail institution(@PathVariable @Size(min = 1, max = 200) String slug) {
    return svc.institutionBySlug(slug);
  }

  @GetMapping("/departments")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public Items<DepartmentItem> departments(@RequestParam UUID institutionId) {
    return svc.listDepartments(institutionId);
  }

  @GetMapping("/programs")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public Items<ProgramItem> programs(@RequestParam UUID departmentId) {
    return svc.listPrograms(departmentId);
  }

  @GetMapping("/subjects")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public Items<?> subjects(@RequestParam(required = false) String q, @RequestParam(required = false) UUID institutionId,
                           @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) Integer semester) {
    checkQ(q);
    return svc.listSubjects(q, institutionId, departmentId, semester);
  }

  @GetMapping("/subjects/{slug}")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl(CACHE)
  public SubjectDetail subject(@PathVariable @Size(min = 1, max = 200) String slug) {
    return svc.subjectBySlug(slug);
  }

  @PostMapping("/institution-requests")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 5, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public IdStatus requestInstitution(AuthUser user, @RequestBody(required = false) JsonNode body) {
    return svc.requestInstitution(user.id(), bodies.parse(body, InstitutionRequestBody.class, REQUEST).value());
  }

  @PutMapping("/me/academic-profile")
  @Auth(Auth.Level.USER)
  public MeDto academicProfile(AuthUser user, @RequestBody(required = false) JsonNode body) {
    return svc.setAcademicProfile(user.id(), bodies.parse(body, AcademicProfileBody.class, ACADEMIC).value());
  }

  @PostMapping("/me/onboarding")
  @Auth(Auth.Level.USER)
  public MeDto completeOnboarding(AuthUser user, @RequestBody(required = false) JsonNode body) {
    bodies.parse(body, JsonNode.class, ONBOARDING); // shape check (object, academic not null)
    AcademicProfileBody academic = null;
    NextExamInput next = null;
    if (body != null && body.has("academic")) academic = bodies.parse(body.get("academic"), AcademicProfileBody.class, ACADEMIC, "body.academic").value();
    if (body != null && body.has("nextExam") && !body.get("nextExam").isNull()) {
      next = bodies.parse(body.get("nextExam"), NextExamInput.class, NEXT_EXAM, "body.nextExam").value();
      try {
        OffsetDateTime.parse(next.examDate());
      } catch (DateTimeParseException e) {
        throw ApiException.validation("Invalid ISO datetime", List.of(Map.of("path", "body.nextExam.examDate", "message", "Invalid ISO datetime")));
      }
    }
    return svc.completeOnboarding(user.id(), new OnboardingBody(academic, next));
  }
}
