package com.shikkhasathi.admin.controller;

import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.service.InsightsService;
import com.shikkhasathi.admin.service.ModerationService;
import com.shikkhasathi.admin.service.PayoutService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Teacher Studio payouts + insights ({@code sellerOps}) and content reports by any user ({@code reports}). */
@RestController
@RequestMapping("/api/v1")
public class SellerOpsController {
  private final PayoutService payouts;
  private final InsightsService insights;
  private final ModerationService moderation;

  public SellerOpsController(PayoutService payouts, InsightsService insights, ModerationService moderation) {
    this.payouts = payouts;
    this.insights = insights;
    this.moderation = moderation;
  }

  @GetMapping("/seller/payouts")
  @Auth(Auth.Level.SELLER)
  public Items<Payout> payouts(AuthUser user) {
    return payouts.mine(user.id());
  }

  @PostMapping("/seller/payouts")
  @Auth(Auth.Level.VERIFIED_SELLER)
  @RateLimit(max = 10, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public Payout requestPayout(AuthUser user, @Valid @RequestBody RequestPayoutBody body) {
    return payouts.request(user.id(), body.amount());
  }

  @GetMapping("/seller/students")
  @Auth(Auth.Level.SELLER)
  public SellerStudents students(AuthUser user, @RequestParam(required = false) UUID courseId, @RequestParam(required = false) @Size(max = 80) String q,
                                 @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return insights.students(user.id(), courseId, q, cursor, limit);
  }

  @GetMapping("/seller/analytics")
  @Auth(Auth.Level.SELLER)
  public SellerAnalytics analytics(AuthUser user, @RequestParam(defaultValue = "30") @Min(7) @Max(365) int days) {
    return insights.seller(user.id(), days);
  }

  @PostMapping("/reports")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 20, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public ReportCreated report(AuthUser user, @Valid @RequestBody CreateReportBody body) {
    return moderation.create(user.id(), body);
  }
}
