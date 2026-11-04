package com.shikkhasathi.course.controller;

import com.shikkhasathi.course.dto.CourseDtos.*;
import com.shikkhasathi.course.service.CourseReviewService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Admin course review queue + drawer + decision (contract group {@code adminReview}). */
@RestController
@RequestMapping("/api/v1/admin/courses")
@Auth(Auth.Level.ADMIN)
public class AdminReviewController {
  private final CourseReviewService svc;

  public AdminReviewController(CourseReviewService svc) {
    this.svc = svc;
  }

  @GetMapping("/review")
  public ReviewPage queue(@RequestParam(defaultValue = "in_review") @Pattern(regexp = "in_review|changes_requested|rejected|published") String status,
                          @RequestParam(required = false) String cursor,
                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return svc.queue(status, cursor, limit);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable UUID id) {
    return svc.detail(id);
  }

  @PostMapping("/{id}/decision")
  public ReviewItem decide(AuthUser user, @PathVariable UUID id, @Valid @RequestBody ReviewDecision body) {
    return svc.decide(user.id(), id, body);
  }
}
