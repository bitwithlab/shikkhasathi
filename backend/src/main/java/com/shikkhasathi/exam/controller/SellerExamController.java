package com.shikkhasathi.exam.controller;

import com.shikkhasathi.exam.dto.ExamDtos.*;
import com.shikkhasathi.exam.service.SellerExamService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Teacher Studio: mock tests and flashcards of a course (contract group {@code sellerExam}). */
@RestController
@RequestMapping("/api/v1/seller")
@Auth(Auth.Level.SELLER)
public class SellerExamController {
  private final SellerExamService svc;

  public SellerExamController(SellerExamService svc) {
    this.svc = svc;
  }

  @GetMapping("/courses/{courseId}/mock-tests")
  public SellerMockList mockTests(AuthUser user, @PathVariable UUID courseId) {
    return svc.listSellerMocks(user.id(), courseId);
  }

  @PostMapping("/courses/{courseId}/mock-tests")
  @ResponseStatus(HttpStatus.CREATED)
  public SellerMockTest createMock(AuthUser user, @PathVariable UUID courseId, @Valid @RequestBody MockTestInput body) {
    return svc.createMock(user.id(), courseId, body);
  }

  @PutMapping("/mock-tests/{id}")
  public SellerMockTest updateMock(AuthUser user, @PathVariable UUID id, @Valid @RequestBody MockTestInput body) {
    return svc.updateMock(user.id(), id, body);
  }

  @DeleteMapping("/mock-tests/{id}")
  public Ok deleteMock(AuthUser user, @PathVariable UUID id) {
    svc.deleteMock(user.id(), id);
    return Ok.OK;
  }

  @GetMapping("/courses/{courseId}/flashcards")
  public SellerFlashcardList flashcards(AuthUser user, @PathVariable UUID courseId) {
    return svc.sellerFlashcards(user.id(), courseId);
  }

  @PutMapping("/courses/{courseId}/flashcards")
  public SellerFlashcardList setFlashcards(AuthUser user, @PathVariable UUID courseId, @Valid @RequestBody SetFlashcards body) {
    return svc.setFlashcards(user.id(), courseId, body.cards());
  }
}
