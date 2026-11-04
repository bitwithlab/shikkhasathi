package com.shikkhasathi.notification.controller;

import com.shikkhasathi.notification.dto.NotificationDtos.*;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

/** Notification center, preferences and push-token registration (contract group {@code notifications}). */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.USER)
public class NotificationController {
  private final NotificationService svc;

  public NotificationController(NotificationService svc) {
    this.svc = svc;
  }

  @GetMapping("/notifications")
  public NotificationPage list(AuthUser user, @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                               @RequestParam(required = false) Boolean unread) {
    var page = svc.list(user.id(), cursor, limit, Boolean.TRUE.equals(unread));
    return new NotificationPage(page.items(), page.nextCursor());
  }

  @PostMapping("/notifications/read")
  public Ok read(AuthUser user, @Valid @RequestBody MarkRead body) {
    svc.markRead(user.id(), body.ids(), Boolean.TRUE.equals(body.all()));
    return Ok.OK;
  }

  @GetMapping("/notifications/unread-count")
  public UnreadCount unreadCount(AuthUser user) {
    return new UnreadCount(svc.unreadCount(user.id()));
  }

  @GetMapping("/me/notification-preferences")
  public Preferences preferences(AuthUser user) {
    return new Preferences(svc.preferences(user.id()));
  }

  @PutMapping("/me/notification-preferences")
  public Preferences updatePreferences(AuthUser user, @Valid @RequestBody UpdatePreferences body) {
    return new Preferences(svc.setPreferences(user.id(), body.items()));
  }

  @PostMapping("/me/push-tokens")
  public Ok registerPush(AuthUser user, @Valid @RequestBody RegisterPush body) {
    svc.registerPushToken(user.id(), body.platform(), body.token());
    return Ok.OK;
  }

  @DeleteMapping("/me/push-tokens")
  public Ok unregisterPush(AuthUser user, @Valid @RequestBody UnregisterPush body) {
    svc.unregisterPushToken(user.id(), body.token());
    return Ok.OK;
  }
}
