package com.shikkhasathi.learning.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.repository.LearningContentRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository.Keyset;
import com.shikkhasathi.learning.repository.LearningCoreRepository.NoteRow;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Timestamped notes and bookmarks on lessons (owner-only). */
@Service
public class NotesService {
  private final LearningAccess acc;
  private final LearningCoreRepository core;
  private final LearningContentRepository content;

  public NotesService(LearningAccess acc, LearningCoreRepository core, LearningContentRepository content) {
    this.acc = acc;
    this.core = core;
    this.content = content;
  }

  /** Lesson titles + course titles (of those lessons' courses) for a set of rows. */
  record Titles(Map<UUID, String> lessons, Map<UUID, String> courses) {
    String lesson(UUID id) {
      return lessons.getOrDefault(id, "");
    }

    String course(UUID id) {
      return courses.getOrDefault(id, "");
    }
  }

  Titles titles(Collection<UUID> lessonIds) {
    var ls = content.lessonTitles(new LinkedHashSet<>(lessonIds));
    var lt = ls.stream().collect(Collectors.toMap(LearningContentRepository.LessonTitle::id, LearningContentRepository.LessonTitle::title, (a, b) -> a));
    var ct = content.courseTitles(ls.stream().map(LearningContentRepository.LessonTitle::courseId).collect(Collectors.toSet()));
    return new Titles(lt, ct);
  }

  private List<Note> toNotes(List<NoteRow> rows) {
    var t = titles(rows.stream().map(NoteRow::lessonId).toList());
    return rows.stream().map(n -> new Note(n.id(), n.courseId(), n.lessonId(), t.lesson(n.lessonId()), t.course(n.courseId()), n.timestampSec(), n.body(), n.createdAt(), n.updatedAt())).toList();
  }

  private List<Bookmark> toBookmarks(List<NoteRow> rows) {
    var t = titles(rows.stream().map(NoteRow::lessonId).toList());
    return rows.stream().map(n -> new Bookmark(n.id(), n.courseId(), n.lessonId(), t.lesson(n.lessonId()), t.course(n.courseId()), n.timestampSec(), n.body(), n.createdAt())).toList();
  }

  static Keyset keyset(String cursor) {
    var c = Cursor.decode(cursor);
    if (c == null) return null;
    try {
      return new Keyset(Instant.parse(c.sortString()), UUID.fromString(c.id()));
    } catch (RuntimeException e) {
      throw ApiException.badRequest("অবৈধ cursor");
    }
  }

  static Cursor.Value cursorOf(Instant createdAt, UUID id) {
    return new Cursor.Value(JsonConfig.iso(createdAt), id.toString());
  }

  public Page<Note> listNotes(UUID userId, UUID courseId, UUID lessonId, String q, String cursor, int limit) {
    String term = q == null || q.isEmpty() ? null : q.replaceAll("[%_]", "");
    var rows = core.listNotes(userId, courseId, lessonId, term, keyset(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> cursorOf(r.createdAt(), r.id()));
    return new Page<>(toNotes(pg.items()), pg.nextCursor());
  }

  @Transactional
  public Note createNote(UUID userId, CreateNote b) {
    var l = acc.lessonForWrite(userId, b.lessonId());
    var n = core.insertNote(userId, l.courseId(), l.id(), b.timestampSec(), b.body());
    return toNotes(List.of(n)).get(0);
  }

  private NoteRow ownNote(UUID userId, UUID id) {
    var n = core.note(id).orElseThrow(ApiException::notFound);
    if (!n.studentId().equals(userId)) throw ApiException.forbidden();
    return n;
  }

  @Transactional
  public Note updateNote(UUID userId, UUID id, String body) {
    ownNote(userId, id);
    return toNotes(List.of(core.updateNote(id, body))).get(0);
  }

  @Transactional
  public void deleteNote(UUID userId, UUID id) {
    ownNote(userId, id);
    core.deleteNote(id);
  }

  public Page<Bookmark> listBookmarks(UUID userId, UUID courseId, UUID lessonId, String cursor, int limit) {
    var rows = core.listBookmarks(userId, courseId, lessonId, keyset(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> cursorOf(r.createdAt(), r.id()));
    return new Page<>(toBookmarks(pg.items()), pg.nextCursor());
  }

  @Transactional
  public Bookmark createBookmark(UUID userId, CreateBookmark b) {
    var l = acc.lessonForWrite(userId, b.lessonId());
    var n = core.insertBookmark(userId, l.courseId(), l.id(), b.timestampSec(), b.body());
    return toBookmarks(List.of(n)).get(0);
  }

  @Transactional
  public void deleteBookmark(UUID userId, UUID id) {
    var n = core.bookmark(id).orElseThrow(ApiException::notFound);
    if (!n.studentId().equals(userId)) throw ApiException.forbidden();
    core.deleteBookmark(id);
  }
}
