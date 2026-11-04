package com.shikkhasathi.learning.controller;

import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.service.NotesService;
import com.shikkhasathi.learning.service.PlayerService;
import com.shikkhasathi.learning.service.QaService;
import com.shikkhasathi.learning.service.ReviewService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.CacheControl;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** My Courses, player, progress, notes/bookmarks, Q&A and reviews (contract group {@code learning}). */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.USER)
public class LearningController {
  private final PlayerService player;
  private final NotesService notes;
  private final QaService qa;
  private final ReviewService reviews;

  public LearningController(PlayerService player, NotesService notes, QaService qa, ReviewService reviews) {
    this.player = player;
    this.notes = notes;
    this.qa = qa;
    this.reviews = reviews;
  }

  @GetMapping("/me/courses")
  public MyCourses myCourses(AuthUser user, @RequestParam(defaultValue = "all") @Pattern(regexp = "all|in_progress|completed|expiring") String filter) {
    return player.myCourses(user.id(), filter);
  }

  @GetMapping("/learn/{courseId}")
  public PlayerOutline outline(AuthUser user, @PathVariable UUID courseId) {
    return player.outline(user.id(), courseId, user.roles());
  }

  @GetMapping("/lessons/{id}")
  public LessonDetail lesson(AuthUser user, @PathVariable UUID id) {
    return player.lessonDetail(user.id(), id, user.roles());
  }

  @PostMapping("/lessons/{id}/playback")
  @RateLimit(max = 120, window = 3600)
  public Playback playback(AuthUser user, @PathVariable UUID id, @Valid @RequestBody PlaybackBody body) {
    return player.playback(user.id(), id, body, user.roles());
  }

  @PostMapping("/lessons/{id}/progress")
  @RateLimit(max = 20, window = 60)
  public ProgressResponse progress(AuthUser user, @PathVariable UUID id, @Valid @RequestBody ProgressBody body) {
    return player.heartbeat(user.id(), id, body);
  }

  @PostMapping("/lessons/{id}/complete")
  public ProgressResponse complete(AuthUser user, @PathVariable UUID id, @Valid @RequestBody CompleteBody body) {
    return player.setComplete(user.id(), id, body.completed());
  }

  @GetMapping("/documents/{id}/url")
  public DocumentUrl document(AuthUser user, @PathVariable UUID id) {
    return player.documentUrl(user.id(), id, user.roles());
  }

  // ---------------------------------------------------------------- notes & bookmarks

  @GetMapping("/notes")
  public Page<Note> notes(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                          @RequestParam(required = false) UUID courseId, @RequestParam(required = false) UUID lessonId,
                          @RequestParam(required = false) @Size(max = 80) String q) {
    return notes.listNotes(user.id(), courseId, lessonId, q, cursor, limit);
  }

  @PostMapping("/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 120, window = 3600)
  public Note createNote(AuthUser user, @Valid @RequestBody CreateNote body) {
    return notes.createNote(user.id(), body);
  }

  @PatchMapping("/notes/{id}")
  public Note updateNote(AuthUser user, @PathVariable UUID id, @Valid @RequestBody UpdateNote body) {
    return notes.updateNote(user.id(), id, body.body());
  }

  @DeleteMapping("/notes/{id}")
  public Ok deleteNote(AuthUser user, @PathVariable UUID id) {
    notes.deleteNote(user.id(), id);
    return Ok.OK;
  }

  @GetMapping("/bookmarks")
  public Page<Bookmark> bookmarks(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                  @RequestParam(required = false) UUID courseId, @RequestParam(required = false) UUID lessonId) {
    return notes.listBookmarks(user.id(), courseId, lessonId, cursor, limit);
  }

  @PostMapping("/bookmarks")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 120, window = 3600)
  public Bookmark createBookmark(AuthUser user, @Valid @RequestBody CreateBookmark body) {
    return notes.createBookmark(user.id(), body);
  }

  @DeleteMapping("/bookmarks/{id}")
  public Ok deleteBookmark(AuthUser user, @PathVariable UUID id) {
    notes.deleteBookmark(user.id(), id);
    return Ok.OK;
  }

  // ---------------------------------------------------------------- Q&A

  @GetMapping("/lessons/{id}/questions")
  public Page<Question> questions(AuthUser user, @PathVariable UUID id, @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return qa.listQuestions(user.id(), id, cursor, limit, user.roles());
  }

  @PostMapping("/lessons/{id}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 20, window = 3600)
  public Question ask(AuthUser user, @PathVariable UUID id, @Valid @RequestBody AskBody body) {
    return qa.ask(user.id(), id, body);
  }

  @PostMapping("/questions/{id}/answers")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = 60, window = 3600)
  public Question answer(AuthUser user, @PathVariable UUID id, @Valid @RequestBody AnswerBody body) {
    return qa.answer(user.id(), id, body.body());
  }

  // ---------------------------------------------------------------- reviews

  @GetMapping("/courses/{courseId}/reviews")
  @Auth(Auth.Level.OPTIONAL)
  @CacheControl("public, max-age=60")
  public Page<Review> reviews(@PathVariable UUID courseId, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                              @RequestParam(required = false) @Min(1) @Max(5) Integer rating) {
    return reviews.listReviews(courseId, rating, cursor, limit);
  }

  @PutMapping("/courses/{courseId}/review")
  @RateLimit(max = 10, window = 3600)
  public Review review(AuthUser user, @PathVariable UUID courseId, @Valid @RequestBody ReviewBody body) {
    return reviews.upsertReview(user.id(), courseId, body);
  }
}
