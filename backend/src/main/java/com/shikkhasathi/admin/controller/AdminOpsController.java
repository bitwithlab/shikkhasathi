package com.shikkhasathi.admin.controller;

import com.shikkhasathi.admin.dto.OpsDtos;
import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.service.*;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Admin operations (contract group {@code adminOps}): payouts, users, moderation, broadcasts, logs, analytics. */
@RestController
@RequestMapping("/api/v1/admin")
@Auth(Auth.Level.ADMIN)
public class AdminOpsController {
  private final PayoutService payouts;
  private final AdminUserService users;
  private final ModerationService moderation;
  private final BroadcastService broadcasts;
  private final LogService logs;
  private final InsightsService insights;

  public AdminOpsController(PayoutService payouts, AdminUserService users, ModerationService moderation, BroadcastService broadcasts, LogService logs, InsightsService insights) {
    this.payouts = payouts;
    this.users = users;
    this.moderation = moderation;
    this.broadcasts = broadcasts;
    this.logs = logs;
    this.insights = insights;
  }

  @GetMapping("/payouts")
  public Page<AdminPayout> payouts(@RequestParam(defaultValue = "requested") @Pattern(regexp = "requested|approved|paid|rejected") String status,
                                   @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return payouts.adminList(status, cursor, limit);
  }

  @PatchMapping("/payouts/{id}")
  public AdminPayout decidePayout(AuthUser user, @PathVariable UUID id, @Valid @RequestBody DecidePayoutBody body) {
    return payouts.decide(user.id(), id, body);
  }

  @GetMapping("/users")
  public Page<AdminUser> users(@RequestParam(required = false) @Size(max = 80) String q, @RequestParam(required = false) @Pattern(regexp = OpsDtos.ROLE_RE) String role,
                               @RequestParam(required = false) @Pattern(regexp = "active|suspended") String status, @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return users.list(q, role, status, cursor, limit);
  }

  @PatchMapping("/users/{id}")
  public AdminUser updateUser(AuthUser user, @PathVariable UUID id, @Valid @RequestBody UpdateUserBody body) {
    return users.update(user.id(), id, body.status(), body.roles(), body.reason());
  }

  @GetMapping("/reports")
  public Page<Report> reports(@RequestParam(defaultValue = "open") @Pattern(regexp = "open|reviewing|resolved|dismissed") String status, @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return moderation.adminList(status, cursor, limit);
  }

  @PatchMapping("/reports/{id}")
  public Report resolveReport(AuthUser user, @PathVariable UUID id, @Valid @RequestBody ResolveReportBody body) {
    return moderation.resolve(user.id(), id, body);
  }

  @GetMapping("/broadcasts")
  public Items<Broadcast> broadcasts() {
    return broadcasts.list();
  }

  @PostMapping("/broadcasts")
  @RateLimit(max = 10, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public Broadcast broadcast(AuthUser user, @Valid @RequestBody BroadcastBody body) {
    return broadcasts.send(user.id(), body);
  }

  @PostMapping("/broadcasts/preview")
  public Recipients broadcastPreview(@Valid @RequestBody AudienceBody body) {
    return broadcasts.preview(body);
  }

  @GetMapping("/email-logs")
  public Page<EmailLog> emailLogs(@RequestParam(required = false) @Size(max = 20) String status, @RequestParam(required = false) @Size(max = 120) String q,
                                  @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return logs.emailLogs(status, q, cursor, limit);
  }

  @GetMapping("/audit")
  public Page<AuditEntry> audit(@RequestParam(required = false) @Size(max = 40) String entity, @RequestParam(required = false) @Size(max = 60) String action,
                                @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return logs.audit(entity, action, cursor, limit);
  }

  @GetMapping("/analytics")
  public AdminAnalytics analytics(@RequestParam(defaultValue = "30") @Min(7) @Max(365) int days) {
    return insights.admin(days);
  }
}
