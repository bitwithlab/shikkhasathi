package com.shikkhasathi.course.controller;

import com.shikkhasathi.course.dto.CourseDtos.*;
import com.shikkhasathi.course.service.CourseAuthoringService;
import com.shikkhasathi.common.web.RequestBodies;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Teacher Studio course wizard (contract group {@code sellerCourses}). */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.SELLER)
public class SellerCourseController {
  private final CourseAuthoringService svc;
  private final RequestBodies bodies;

  public SellerCourseController(CourseAuthoringService svc, RequestBodies bodies) {
    this.svc = svc;
    this.bodies = bodies;
  }

  @GetMapping("/seller/courses")
  public CourseList list(AuthUser user) {
    return svc.listMyCourses(user.id());
  }

  @PostMapping("/seller/courses")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(AuthUser user, @Valid @RequestBody CreateCourse body) {
    return svc.createCourse(user.id(), body.title());
  }

  @GetMapping("/seller/courses/{id}")
  public Map<String, Object> get(AuthUser user, @PathVariable UUID id) {
    return svc.getMyCourse(user.id(), id);
  }

  @PatchMapping("/seller/courses/{id}")
  public Map<String, Object> update(AuthUser user, @PathVariable UUID id, @RequestBody JsonNode raw) {
    return svc.updateCourse(user.id(), id, bodies.read(raw, CoursePatch.class, CoursePatch.NULLABLE), raw);
  }

  @PutMapping("/seller/courses/{id}/targets")
  public Map<String, Object> targets(AuthUser user, @PathVariable UUID id, @Valid @RequestBody SetTargets body) {
    return svc.setTargets(user.id(), id, body.targets());
  }

  @PutMapping("/seller/courses/{id}/curriculum")
  public Map<String, Object> curriculum(AuthUser user, @PathVariable UUID id, @Valid @RequestBody CurriculumBody body) {
    return svc.setCurriculum(user.id(), id, body);
  }

  @GetMapping("/seller/courses/{id}/checklist")
  public Checklist checklist(AuthUser user, @PathVariable UUID id) {
    return svc.checklist(user.id(), id);
  }

  @PostMapping("/seller/courses/{id}/submit")
  @Auth(Auth.Level.VERIFIED_SELLER)
  public Map<String, Object> submit(AuthUser user, @PathVariable UUID id, @Valid @RequestBody SubmitBody body) {
    return svc.submit(user.id(), id);
  }

  @PostMapping("/seller/courses/{id}/archive")
  public Map<String, Object> archive(AuthUser user, @PathVariable UUID id, @Valid @RequestBody VisibilityBody body) {
    return svc.setVisibility(user.id(), id, body.status());
  }

  @DeleteMapping("/seller/courses/{id}")
  public Ok remove(AuthUser user, @PathVariable UUID id) {
    svc.removeDraft(user.id(), id);
    return Ok.OK;
  }

  @PostMapping("/seller/uploads")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 60, window = 60)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadInstructions upload(AuthUser user, @Valid @RequestBody UploadBody body) {
    return svc.createUpload(user.id(), body);
  }

  @PostMapping("/seller/uploads/{id}/complete")
  public Ok uploadComplete(AuthUser user, @PathVariable UUID id) {
    svc.completeUpload(user.id(), id);
    return Ok.OK;
  }

  @PostMapping("/subjects/propose")
  @ResponseStatus(HttpStatus.CREATED)
  public SubjectRef proposeSubject(AuthUser user, @Valid @RequestBody ProposeSubject body) {
    return svc.proposeSubject(user.id(), body.name(), body.aliases());
  }
}
