package com.shikkhasathi.exam.controller;

import com.shikkhasathi.exam.dto.ExamDtos.*;
import com.shikkhasathi.exam.service.ExamService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Exam Mode: exam plans, flashcards and mock-test attempts (contract group {@code exam}). */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.USER)
public class ExamController {
  private final ExamService svc;

  public ExamController(ExamService svc) {
    this.svc = svc;
  }

  @GetMapping("/exam-plans")
  public PlanList plans(AuthUser user) {
    return svc.listPlans(user.id());
  }

  @PostMapping("/exam-plans")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 30, window = 3600)
  public PlanSummary create(AuthUser user, @Valid @RequestBody CreatePlan body) {
    return svc.createPlan(user.id(), body);
  }

  @GetMapping("/exam-plans/{id}")
  public PlanDetail get(AuthUser user, @PathVariable UUID id) {
    return svc.planDetail(user.id(), id);
  }

  @PatchMapping("/exam-plans/{id}")
  public PlanSummary update(AuthUser user, @PathVariable UUID id, @Valid @RequestBody UpdatePlan body) {
    return svc.updatePlan(user.id(), id, body);
  }

  @DeleteMapping("/exam-plans/{id}")
  public Ok remove(AuthUser user, @PathVariable UUID id) {
    svc.deletePlan(user.id(), id);
    return Ok.OK;
  }

  @GetMapping("/exam-plans/{id}/flashcards")
  public FlashcardList flashcards(AuthUser user, @PathVariable UUID id) {
    return svc.planFlashcards(user.id(), id);
  }

  @PostMapping("/flashcards/{id}/review")
  public Ok reviewCard(AuthUser user, @PathVariable UUID id, @Valid @RequestBody ReviewCard body) {
    svc.reviewFlashcard(user.id(), id, body.known());
    return Ok.OK;
  }

  @GetMapping("/mock-tests")
  public MockList mockTests(AuthUser user) {
    return svc.studentMockTests(user.id());
  }

  @PostMapping("/mock-tests/{id}/attempts")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 30, window = 3600)
  public Attempt start(AuthUser user, @PathVariable UUID id) {
    return svc.startAttempt(user.id(), id);
  }

  @GetMapping("/mock-attempts/{id}")
  public Attempt attempt(AuthUser user, @PathVariable UUID id) {
    return svc.getAttempt(user.id(), id);
  }

  @PutMapping("/mock-attempts/{id}/answers")
  @RateLimit(max = 600, window = 3600)
  public Ok answer(AuthUser user, @PathVariable UUID id, @Valid @RequestBody SaveAnswer body) {
    svc.saveAnswer(user.id(), id, body);
    return Ok.OK;
  }

  @PostMapping("/mock-attempts/{id}/submit")
  public AttemptResult submit(AuthUser user, @PathVariable UUID id) {
    return svc.submitAttempt(user.id(), id);
  }

  @GetMapping("/mock-attempts/{id}/result")
  public AttemptResult result(AuthUser user, @PathVariable UUID id) {
    return svc.attemptResult(user.id(), id);
  }
}
