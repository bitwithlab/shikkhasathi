package com.shikkhasathi.assignment.controller;

import com.shikkhasathi.assignment.dto.AssignmentDtos.*;
import com.shikkhasathi.assignment.service.AssignmentService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.*;

/** Classroom assignments list/detail and the calendar (contract group {@code classroom}). */
@RestController
@RequestMapping("/api/v1/classroom")
@Auth(Auth.Level.USER)
public class AssignmentController {
  /** ISO-8601 date-time with seconds and an offset (z.iso.datetime({ offset: true })). */
  static final String DATETIME = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$";

  private final AssignmentService svc;

  public AssignmentController(AssignmentService svc) {
    this.svc = svc;
  }

  private static Instant at(String v) {
    return v == null ? null : OffsetDateTime.parse(v).toInstant();
  }

  @GetMapping("/assignments")
  public AssignmentList assignments(AuthUser user,
                                    @RequestParam(defaultValue = "all") @Pattern(regexp = "pending|turned_in|graded|missing|all|upcoming") String status,
                                    @RequestParam(required = false) String courseId,
                                    @RequestParam(required = false) @Pattern(regexp = DATETIME) String from,
                                    @RequestParam(required = false) @Pattern(regexp = DATETIME) String to) {
    return svc.list(user.id(), status, courseId, at(from), at(to));
  }

  @GetMapping("/assignments/{id}")
  public Assignment assignment(AuthUser user, @PathVariable @Size(min = 1, max = 64) String id) {
    return svc.get(user.id(), id);
  }

  @GetMapping("/calendar")
  public CalendarList calendar(AuthUser user, @RequestParam @NotNull @Pattern(regexp = DATETIME) String from, @RequestParam @NotNull @Pattern(regexp = DATETIME) String to) {
    return svc.calendar(user.id(), at(from), at(to));
  }
}
