package com.shikkhasathi.classroom.controller;

import com.shikkhasathi.classroom.dto.ClassroomDtos.*;
import com.shikkhasathi.classroom.service.ClassroomService;
import com.shikkhasathi.classroom.service.ClassroomSyncService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Google Classroom connection, sync, classes, stream and feed (contract group {@code classroom}) + the Pub/Sub webhook. */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.USER)
public class ClassroomController {
  private final ClassroomService svc;
  private final ClassroomSyncService sync;

  public ClassroomController(ClassroomService svc, ClassroomSyncService sync) {
    this.svc = svc;
    this.sync = sync;
  }

  @GetMapping("/classroom/connect-url")
  public ConnectUrl connectUrl(AuthUser user, @RequestParam(required = false) @Size(max = 200) String returnTo) {
    return svc.connectUrl(user.id(), returnTo);
  }

  @PostMapping("/classroom/connect")
  public ClassroomStatus connect(AuthUser user, @Valid @RequestBody ConnectBody body) {
    return svc.connectWithCode(user.id(), body.code(), body.redirectUri());
  }

  @GetMapping("/classroom/status")
  public ClassroomStatus status(AuthUser user) {
    return svc.getStatus(user.id());
  }

  @DeleteMapping("/classroom/connection")
  public Ok disconnect(AuthUser user) {
    svc.disconnect(user.id());
    return Ok.OK;
  }

  @PostMapping("/classroom/sync")
  @RateLimit(max = 3, window = 60)
  public ClassroomStatus sync(AuthUser user) {
    return svc.manualSync(user.id());
  }

  @GetMapping("/classroom/courses")
  public CourseList courses(AuthUser user) {
    return svc.listCourses(user.id());
  }

  @GetMapping("/classroom/courses/{id}/stream")
  public Stream stream(AuthUser user, @PathVariable @Size(min = 1, max = 64) String id) {
    return svc.courseStream(user.id(), id);
  }

  @GetMapping("/classroom/feed")
  public FeedPage feed(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(required = false) String courseId, @RequestParam(required = false) String unread) {
    // z.coerce.boolean(): any non-empty value is true
    return svc.feed(user.id(), cursor, limit, courseId, unread != null && !unread.isEmpty());
  }

  @PostMapping("/classroom/feed/read")
  public Ok feedRead(AuthUser user, @Valid @RequestBody FeedReadBody body) {
    svc.markFeedRead(user.id(), body.ids(), Boolean.TRUE.equals(body.all()));
    return Ok.OK;
  }

  /** Browser redirect target of the Google consent screen (not JSON — redirects back into the SPA). */
  @GetMapping("/classroom/oauth/callback")
  @Auth(Auth.Level.PUBLIC)
  public ResponseEntity<Void> oauthCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                            @RequestParam(required = false) String error) {
    String to = svc.oauthCallback(code, state, error);
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, to).build();
  }

  /** Classroom push notifications via a Pub/Sub push subscription. */
  @PostMapping("/webhooks/google-pubsub")
  @Auth(Auth.Level.PUBLIC)
  public Ok googlePubsub(@RequestParam(required = false) String token, @Valid @RequestBody PubSubPush body) {
    sync.handlePubsub(token, body.message());
    return Ok.OK;
  }
}
