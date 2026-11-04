package com.shikkhasathi.catalog.controller;

import com.shikkhasathi.common.web.RequestBodies;
import com.shikkhasathi.common.web.RequestBodies.Spec;
import com.shikkhasathi.catalog.dto.CatalogDtos;
import com.shikkhasathi.catalog.dto.CatalogDtos.*;
import com.shikkhasathi.catalog.service.AdminCatalogService;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Admin master data (contract group {@code adminCatalog}). */
@RestController
@RequestMapping("/api/v1/admin")
@Auth(Auth.Level.ADMIN)
public class AdminCatalogController {
  // create schemas: required keys + optional-but-not-nullable keys (Zod .optional() / .default())
  static final Spec INSTITUTION = Spec.of().trim("name", "nameBn", "shortName", "city").required("name", "shortName", "slug", "type")
      .notNull("name", "shortName", "slug", "type", "aliases", "status");
  /** patchOf(...): every key optional, the same keys stay non-nullable */
  static final Spec INSTITUTION_PATCH = Spec.of().trim("name", "nameBn", "shortName", "city").notNull("name", "shortName", "slug", "type", "aliases", "status");
  static final Spec DEPARTMENT = Spec.of().trim("name", "nameBn", "shortName").required("institutionId", "name", "shortName").notNull("institutionId", "name", "shortName");
  static final Spec DEPARTMENT_PATCH = Spec.of().trim("name", "nameBn", "shortName").notNull("institutionId", "name", "shortName");
  static final Spec PROGRAM = Spec.of().trim("name").required("departmentId", "name").notNull("departmentId", "name", "level", "semesters");
  static final Spec SUBJECT = Spec.of().trim("name", "nameBn").required("name", "slug").notNull("name", "slug", "aliases", "category");
  static final Spec SUBJECT_PATCH = Spec.of().trim("name", "nameBn").notNull("name", "slug", "aliases", "category", "status");
  static final Spec INSTITUTION_SUBJECT = Spec.of().trim("courseCode").required("institutionId", "subjectMasterId").notNull("institutionId", "subjectMasterId");
  static final Spec MERGE = Spec.of().required("intoId").notNull("intoId");
  static final Spec APPROVE = Spec.of().trim("name", "shortName").required("shortName").notNull("name", "shortName", "slug", "city");
  static final Spec MERGE_REQUEST = Spec.of().required("institutionId").notNull("institutionId");
  static final Spec REJECT = Spec.of().notNull("note");

  private final AdminCatalogService svc;
  private final RequestBodies bodies;

  public AdminCatalogController(AdminCatalogService svc, RequestBodies bodies) {
    this.svc = svc;
    this.bodies = bodies;
  }

  // ---------------------------------------------------------------- institution requests

  @GetMapping("/institution-requests")
  public Page<InstitutionRequestItem> institutionRequests(@RequestParam(defaultValue = "pending") @Pattern(regexp = CatalogDtos.INSTITUTION_STATUS_RE) String status,
                                                          @RequestParam(required = false) String cursor,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return svc.listRequests(status, cursor, limit);
  }

  @PatchMapping("/institution-requests/{id}")
  public Ok decideInstitutionRequest(@PathVariable UUID id, @RequestBody(required = false) JsonNode body, AuthUser user) {
    String action = body != null && body.has("action") && body.get("action").isString() ? body.get("action").asString() : null;
    Object decision = switch (action == null ? "" : action) {
      case "approve" -> bodies.parse(body, ApproveDecision.class, APPROVE).value();
      case "merge" -> bodies.parse(body, MergeDecision.class, MERGE_REQUEST).value();
      case "reject" -> bodies.parse(body, RejectDecision.class, REJECT).value();
      default -> throw ApiException.validation("Invalid input", List.of(Map.of("path", "body.action", "message", "Invalid input")));
    };
    return svc.decideRequest(id, decision, user.id());
  }

  // ---------------------------------------------------------------- institutions

  @GetMapping("/catalog/institutions")
  public Items<AdminInstitution> institutions(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) @Pattern(regexp = CatalogDtos.INSTITUTION_STATUS_RE) String status) {
    return svc.listInstitutions(q, status);
  }

  @PostMapping("/catalog/institutions")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminInstitution createInstitution(@RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, InstitutionUpsert.class, INSTITUTION);
    return svc.upsertInstitution(user.id(), p.value(), p.present(), null);
  }

  @PatchMapping("/catalog/institutions/{id}")
  public AdminInstitution updateInstitution(@PathVariable UUID id, @RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, InstitutionUpsert.class, INSTITUTION_PATCH, true);
    return svc.upsertInstitution(user.id(), p.value(), p.present(), id);
  }

  @PostMapping("/catalog/institutions/{id}/merge")
  public Ok mergeInstitution(@PathVariable UUID id, @RequestBody(required = false) JsonNode body, AuthUser user) {
    return svc.mergeInstitution(user.id(), id, bodies.parse(body, MergeBody.class, MERGE).value().intoId());
  }

  // ---------------------------------------------------------------- departments / programs

  @PostMapping("/catalog/departments")
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentItem createDepartment(@RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, DepartmentUpsert.class, DEPARTMENT);
    return svc.upsertDepartment(user.id(), p.value(), p.present(), null);
  }

  @PatchMapping("/catalog/departments/{id}")
  public DepartmentItem updateDepartment(@PathVariable UUID id, @RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, DepartmentUpsert.class, DEPARTMENT_PATCH, true);
    return svc.upsertDepartment(user.id(), p.value(), p.present(), id);
  }

  @DeleteMapping("/catalog/departments/{id}")
  public Ok deleteDepartment(@PathVariable UUID id, AuthUser user) {
    return svc.deleteDepartment(user.id(), id);
  }

  @PostMapping("/catalog/programs")
  @ResponseStatus(HttpStatus.CREATED)
  public ProgramItem createProgram(@RequestBody(required = false) JsonNode body, AuthUser user) {
    return svc.createProgram(user.id(), bodies.parse(body, ProgramUpsert.class, PROGRAM).value());
  }

  @DeleteMapping("/catalog/programs/{id}")
  public Ok deleteProgram(@PathVariable UUID id, AuthUser user) {
    return svc.deleteProgram(user.id(), id);
  }

  // ---------------------------------------------------------------- subjects

  @GetMapping("/catalog/subjects")
  public Items<AdminSubject> subjects(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) @Pattern(regexp = CatalogDtos.SUBJECT_STATUS_RE) String status) {
    return svc.listSubjects(q, status);
  }

  @PostMapping("/catalog/subjects")
  @ResponseStatus(HttpStatus.CREATED)
  public SubjectItem createSubject(@RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, SubjectUpsert.class, SUBJECT);
    return svc.upsertSubject(user.id(), p.value(), p.present(), null);
  }

  @PatchMapping("/catalog/subjects/{id}")
  public SubjectItem updateSubject(@PathVariable UUID id, @RequestBody(required = false) JsonNode body, AuthUser user) {
    var p = bodies.parse(body, SubjectUpsert.class, SUBJECT_PATCH, true);
    return svc.upsertSubject(user.id(), p.value(), p.present(), id);
  }

  @PostMapping("/catalog/institution-subjects")
  @ResponseStatus(HttpStatus.CREATED)
  public IdOnly createInstitutionSubject(@RequestBody(required = false) JsonNode body, AuthUser user) {
    return svc.createInstitutionSubject(user.id(), bodies.parse(body, InstitutionSubjectUpsert.class, INSTITUTION_SUBJECT).value());
  }

  @DeleteMapping("/catalog/institution-subjects/{id}")
  public Ok deleteInstitutionSubject(@PathVariable UUID id, AuthUser user) {
    return svc.deleteInstitutionSubject(user.id(), id);
  }
}
