package com.shikkhasathi.marketplace.controller;

import com.shikkhasathi.marketplace.dto.MarketplaceDtos.*;
import com.shikkhasathi.marketplace.service.CourseRequestService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Contract group {@code requests}: the course request board. */
@RestController
@RequestMapping("/api/v1")
public class CourseRequestController {
  private final CourseRequestService requests;

  public CourseRequestController(CourseRequestService requests) {
    this.requests = requests;
  }

  @GetMapping("/course-requests")
  @Auth(Auth.Level.OPTIONAL)
  public RequestPage list(@Valid @ModelAttribute ListRequestsQuery q, Optional<AuthUser> user) {
    return requests.list(q, user.map(AuthUser::id).orElse(null));
  }

  @PostMapping("/course-requests")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 10, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResult create(@Valid @RequestBody CreateCourseRequest body, AuthUser user) {
    return requests.create(user.id(), body);
  }

  @PostMapping("/course-requests/{id}/vote")
  @Auth(Auth.Level.USER)
  public CourseRequestDto vote(@PathVariable UUID id, AuthUser user) {
    return requests.vote(user.id(), id);
  }

  @PostMapping("/course-requests/{id}/claim")
  @Auth(Auth.Level.SELLER)
  public CourseRequestDto claim(@PathVariable UUID id, @Valid @RequestBody ClaimBody body, AuthUser user) {
    return requests.claim(user.id(), id, body.courseId());
  }
}
