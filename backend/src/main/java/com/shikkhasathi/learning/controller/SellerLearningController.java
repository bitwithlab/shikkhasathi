package com.shikkhasathi.learning.controller;

import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.service.QaService;
import com.shikkhasathi.learning.service.ReviewService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Teacher Studio Q&A inbox and reviews (contract group {@code sellerLearning}). */
@RestController
@RequestMapping("/api/v1/seller")
@Auth(Auth.Level.SELLER)
public class SellerLearningController {
  private final QaService qa;
  private final ReviewService reviews;

  public SellerLearningController(QaService qa, ReviewService reviews) {
    this.qa = qa;
    this.reviews = reviews;
  }

  @GetMapping("/questions")
  public Page<SellerQuestion> questions(AuthUser user, @RequestParam(defaultValue = "open") @Pattern(regexp = "open|answered|all") String status,
                                        @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return qa.sellerQuestions(user.id(), status, cursor, limit);
  }

  @GetMapping("/reviews")
  public Page<SellerReview> reviews(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return reviews.sellerReviews(user.id(), cursor, limit);
  }

  @PutMapping("/reviews/{id}/reply")
  public Review reply(AuthUser user, @PathVariable UUID id, @Valid @RequestBody ReplyBody body) {
    return reviews.replyReview(user.id(), id, body.reply());
  }
}
