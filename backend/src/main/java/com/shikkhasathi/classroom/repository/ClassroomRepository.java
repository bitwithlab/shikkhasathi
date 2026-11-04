package com.shikkhasathi.classroom.repository;

import com.shikkhasathi.common.Ids;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE SQL for google_connections and the classroom_* cache tables (external source, spec §10). */
@Repository
public class ClassroomRepository {
  public record Conn(UUID id, UUID userId, String googleUserId, String email, String refreshTokenEnc, String accessTokenEnc, Instant accessTokenExpiresAt,
                     String status, Instant lastFullSyncAt, Instant lastSyncAt, Instant lastManualSyncAt, Instant nextSyncAllowedAt, String errorCode,
                     boolean isFake, Instant createdAt) {}

  public record CourseRow(String gcCourseId, String name, String section, String room, List<String> teacherNames, String alternateLink, String color) {}

  public record CourseworkRow(String gcCourseId, String gcCourseworkId, String title, String description, String workType, Instant dueAt, BigDecimal maxPoints,
                              String materialsJson, String alternateLink, Instant createdAtGc) {}

  public record SubmissionRow(String gcCourseworkId, String state, boolean late, BigDecimal assignedGrade, String alternateLink) {}

  /** coursework left-joined with the user's submission (sub may be null) */
  public record AssignmentRow(CourseworkRow cw, SubmissionRow sub) {}

  public record MaterialRow(String gcCourseId, String gcMaterialId, String title, String description, String materialsJson, String alternateLink, Instant createdAtGc) {}

  public record AnnouncementRow(String gcCourseId, String gcAnnouncementId, String text, String authorName, String materialsJson, String alternateLink, Instant createdAtGc) {}

  public record FeedRow(UUID id, String kind, String gcCourseId, String refId, String title, Instant occurredAt, Instant readAt) {}

  public record RegRow(UUID id, UUID userId, String gcCourseId, String registrationId, Instant expiresAt) {}

  /** A reminder candidate: coursework + submission state + course name. */
  public record ReminderRow(UUID userId, String gcCourseworkId, String title, Instant dueAt, String alternateLink, String subState, String courseName) {}

  /** Classroom-owned tables, deleted on disconnect. */
  static final List<String> USER_TABLES = List.of("classroom_coursework", "classroom_submissions", "classroom_materials", "classroom_announcements", "classroom_feed_items",
      "classroom_push_registrations", "classroom_courses");

  private final NamedParameterJdbcTemplate db;

  public ClassroomRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  // ---------------------------------------------------------------- helpers

  static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  static Instant inst(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  static List<String> strings(ResultSet rs, String col) throws SQLException {
    Array a = rs.getArray(col);
    if (a == null) return List.of();
    return Arrays.asList((String[]) a.getArray());
  }

  private static MapSqlParameterSource p() {
    return new MapSqlParameterSource();
  }

  private static MapSqlParameterSource t(MapSqlParameterSource p, String name, Instant v) {
    return p.addValue(name, ts(v), Types.TIMESTAMP);
  }

  private static MapSqlParameterSource n(MapSqlParameterSource p, String name, BigDecimal v) {
    return p.addValue(name, v, Types.NUMERIC);
  }

  private static final RowMapper<Conn> CONN = (rs, i) -> new Conn(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("google_user_id"),
      rs.getString("email"), rs.getString("refresh_token_enc"), rs.getString("access_token_enc"), inst(rs, "access_token_expires_at"), rs.getString("status"),
      inst(rs, "last_full_sync_at"), inst(rs, "last_sync_at"), inst(rs, "last_manual_sync_at"), inst(rs, "next_sync_allowed_at"), rs.getString("error_code"),
      rs.getBoolean("is_fake"), inst(rs, "created_at"));

  private static final RowMapper<CourseRow> COURSE = (rs, i) -> new CourseRow(rs.getString("gc_course_id"), rs.getString("name"), rs.getString("section"),
      rs.getString("room"), strings(rs, "teacher_names"), rs.getString("alternate_link"), rs.getString("color"));

  private static CourseworkRow cw(ResultSet rs) throws SQLException {
    return new CourseworkRow(rs.getString("gc_course_id"), rs.getString("gc_coursework_id"), rs.getString("title"), rs.getString("description"), rs.getString("work_type"),
        inst(rs, "due_at"), rs.getBigDecimal("max_points"), rs.getString("materials_json"), rs.getString("alternate_link"), inst(rs, "created_at_gc"));
  }

  private static final RowMapper<SubmissionRow> SUB = (rs, i) -> new SubmissionRow(rs.getString("gc_coursework_id"), rs.getString("state"), rs.getBoolean("late"),
      rs.getBigDecimal("assigned_grade"), rs.getString("alternate_link"));

  private static final RowMapper<MaterialRow> MAT = (rs, i) -> new MaterialRow(rs.getString("gc_course_id"), rs.getString("gc_material_id"), rs.getString("title"),
      rs.getString("description"), rs.getString("materials_json"), rs.getString("alternate_link"), inst(rs, "created_at_gc"));

  private static final RowMapper<AnnouncementRow> ANN = (rs, i) -> new AnnouncementRow(rs.getString("gc_course_id"), rs.getString("gc_announcement_id"), rs.getString("text"),
      rs.getString("author_name"), rs.getString("materials_json"), rs.getString("alternate_link"), inst(rs, "created_at_gc"));

  private static final RowMapper<FeedRow> FEED = (rs, i) -> new FeedRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("gc_course_id"),
      rs.getString("ref_id"), rs.getString("title"), inst(rs, "occurred_at"), inst(rs, "read_at"));

  private static final RowMapper<RegRow> REG = (rs, i) -> new RegRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("gc_course_id"),
      rs.getString("registration_id"), inst(rs, "expires_at"));

  // ---------------------------------------------------------------- connections

  public Optional<Conn> connection(UUID userId) {
    return db.query("select * from google_connections where user_id = :u limit 1", Map.of("u", userId), CONN).stream().findFirst();
  }

  public List<Conn> activeConnections(UUID userId) {
    if (userId != null) return db.query("select * from google_connections where user_id = :u and status = 'active'", Map.of("u", userId), CONN);
    return db.query("select * from google_connections where status = 'active'", Map.of(), CONN);
  }

  public Optional<String> userEmail(UUID userId) {
    return db.query("select email from users where id = :u", Map.of("u", userId), (rs, i) -> rs.getString(1)).stream().filter(Objects::nonNull).findFirst();
  }

  /** Insert or update the connection (status syncing, errors cleared); {@code resetCreatedAt} re-anchors created_at. */
  public void saveConnection(UUID existingId, UUID userId, String googleUserId, String email, String refreshEnc, String accessEnc, Instant expiresAt, List<String> scopes,
                             boolean isFake, boolean resetCreatedAt, Instant now) {
    var p = p().addValue("u", userId).addValue("g", googleUserId).addValue("e", email).addValue("r", refreshEnc).addValue("a", accessEnc)
        .addValue("sc", scopes.toArray(String[]::new)).addValue("f", isFake).addValue("reset", resetCreatedAt);
    t(p, "exp", expiresAt);
    t(p, "now", now);
    if (existingId != null) {
      db.update("""
          update google_connections set google_user_id = :g, email = :e, refresh_token_enc = :r, access_token_enc = :a, access_token_expires_at = :exp,
            scopes = :sc, status = 'syncing', error_code = null, error_message = null, is_fake = :f,
            created_at = case when :reset then :now else created_at end, updated_at = now()
          where id = :id""", p.addValue("id", existingId));
    } else {
      db.update("""
          insert into google_connections (id, user_id, google_user_id, email, refresh_token_enc, access_token_enc, access_token_expires_at, scopes, status, is_fake, created_at, updated_at)
          values (:id, :u, :g, :e, :r, :a, :exp, :sc, 'syncing', :f, :now, now())""", p.addValue("id", Ids.uuidv7()));
    }
  }

  public void updateAccessToken(UUID connId, String accessEnc, Instant expiresAt) {
    db.update("update google_connections set access_token_enc = :a, access_token_expires_at = :exp, updated_at = now() where id = :id",
        t(p().addValue("a", accessEnc).addValue("id", connId), "exp", expiresAt));
  }

  /** status + error fields (+ optional next_sync_allowed_at when {@code setNextSync}). */
  public void markConnection(UUID connId, String status, String errorCode, String errorMessage, boolean keepMessage, Instant nextSyncAllowedAt, boolean setNextSync) {
    var p = p().addValue("id", connId).addValue("s", status).addValue("c", errorCode).addValue("m", errorMessage).addValue("km", keepMessage).addValue("sn", setNextSync);
    t(p, "next", nextSyncAllowedAt);
    db.update("""
        update google_connections set status = :s, error_code = :c,
          error_message = case when :km then error_message else :m end,
          next_sync_allowed_at = case when :sn then :next else next_sync_allowed_at end, updated_at = now()
        where id = :id""", p);
  }

  public void markSynced(UUID connId, Instant now, boolean full) {
    db.update("""
        update google_connections set status = 'active', last_sync_at = :now, last_full_sync_at = case when :full then :now else last_full_sync_at end,
          error_code = null, error_message = null, updated_at = now() where id = :id""", t(p().addValue("id", connId).addValue("full", full), "now", now));
  }

  public void setLastManualSync(UUID connId, Instant at) {
    db.update("update google_connections set last_manual_sync_at = :at, updated_at = now() where id = :id", t(p().addValue("id", connId), "at", at));
  }

  public void deleteConnection(UUID connId) {
    db.update("delete from google_connections where id = :id", Map.of("id", connId));
  }

  /** Users to poll: active, last sync older than {@code cutoff}, active since {@code activeSince}, not in quota backoff. */
  public List<UUID> usersToPoll(Instant cutoff, Instant activeSince, Instant now) {
    var p = p();
    t(p, "cutoff", cutoff);
    t(p, "active", activeSince);
    t(p, "now", now);
    return db.queryForList("""
        select g.user_id from google_connections g join users u on u.id = g.user_id
        where g.status = 'active' and (g.last_sync_at is null or g.last_sync_at < :cutoff) and u.last_active_at >= :active
          and (g.next_sync_allowed_at is null or g.next_sync_allowed_at < :now)
        limit 5000""", p, UUID.class);
  }

  // ---------------------------------------------------------------- sync runs

  public UUID startSyncRun(UUID userId, String kind, String trigger) {
    UUID id = Ids.uuidv7();
    db.update("insert into classroom_sync_runs (id, user_id, kind, trigger) values (:id, :u, :k, :t)", Map.of("id", id, "u", userId, "k", kind, "t", trigger));
    return id;
  }

  public void finishSyncRun(UUID runId, Instant at, boolean ok, int changes, String error) {
    if (ok) db.update("update classroom_sync_runs set finished_at = :at, ok = true, changes = :c, updated_at = now() where id = :id", t(p().addValue("id", runId).addValue("c", changes), "at", at));
    else db.update("update classroom_sync_runs set finished_at = :at, ok = false, error = :e, updated_at = now() where id = :id", t(p().addValue("id", runId).addValue("e", error), "at", at));
  }

  // ---------------------------------------------------------------- courses

  /** Ordered by name (like before). */
  public List<CourseRow> courses(UUID userId) {
    return db.query("select * from classroom_courses where user_id = :u order by name asc", Map.of("u", userId), COURSE);
  }

  public long courseCount(UUID userId) {
    Long n = db.queryForObject("select count(*) from classroom_courses where user_id = :u", Map.of("u", userId), Long.class);
    return n == null ? 0 : n;
  }

  public List<String> courseIds(UUID userId) {
    return db.queryForList("select gc_course_id from classroom_courses where user_id = :u", Map.of("u", userId), String.class);
  }

  public void upsertCourse(UUID userId, String gcId, String name, String section, String room, String descriptionHeading, List<String> teacherNames, String alternateLink,
                           String color, String state, Instant updateTime) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("g", gcId).addValue("n", name).addValue("s", section).addValue("r", room).addValue("d", descriptionHeading)
        .addValue("tn", teacherNames.toArray(String[]::new)).addValue("l", alternateLink).addValue("c", color).addValue("st", state);
    t(p, "ut", updateTime);
    db.update("""
        insert into classroom_courses (id, user_id, gc_course_id, name, section, room, description_heading, teacher_names, alternate_link, color, state, updated_at_gc)
        values (:id, :u, :g, :n, :s, :r, :d, :tn, :l, :c, :st, :ut)
        on conflict (user_id, gc_course_id) do update set name = excluded.name, section = excluded.section, room = excluded.room, teacher_names = excluded.teacher_names,
          alternate_link = excluded.alternate_link, state = excluded.state, updated_at_gc = excluded.updated_at_gc, updated_at = now()""", p);
  }

  /** Delete every cached row of the given courses (course gone from Classroom). */
  public void purgeCourses(UUID userId, List<String> courseIds) {
    if (courseIds.isEmpty()) return;
    for (String table : USER_TABLES) db.update("delete from " + table + " where user_id = :u and gc_course_id in (:ids)", p().addValue("u", userId).addValue("ids", courseIds));
  }

  // ---------------------------------------------------------------- coursework + submissions

  public List<CourseworkRow> coursework(UUID userId, String courseId) {
    return db.query("select * from classroom_coursework where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), (rs, i) -> cw(rs));
  }

  public List<CourseworkRow> courseworkByIds(UUID userId, Collection<String> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select * from classroom_coursework where user_id = :u and gc_coursework_id in (:ids)", p().addValue("u", userId).addValue("ids", ids), (rs, i) -> cw(rs));
  }

  public void upsertCoursework(UUID userId, String courseId, String id, String title, String description, String workType, Instant dueAt, BigDecimal maxPoints,
                               String materialsJson, String alternateLink, String state, Instant creationTime, Instant updateTime) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("c", courseId).addValue("w", id).addValue("ti", title).addValue("d", description)
        .addValue("wt", workType).addValue("m", materialsJson).addValue("l", alternateLink).addValue("st", state);
    t(p, "due", dueAt);
    n(p, "mp", maxPoints);
    t(p, "ct", creationTime);
    t(p, "ut", updateTime);
    db.update("""
        insert into classroom_coursework (id, user_id, gc_course_id, gc_coursework_id, title, description, work_type, due_at, max_points, materials_json, alternate_link, state, created_at_gc, updated_at_gc)
        values (:id, :u, :c, :w, :ti, :d, :wt, :due, :mp, cast(:m as jsonb), :l, :st, :ct, :ut)
        on conflict (user_id, gc_coursework_id) do update set title = excluded.title, description = excluded.description, due_at = excluded.due_at,
          max_points = excluded.max_points, materials_json = excluded.materials_json, alternate_link = excluded.alternate_link, state = excluded.state,
          updated_at_gc = excluded.updated_at_gc, updated_at = now()""", p);
  }

  /** Coursework removed in Classroom: drop it, its submission and its feed items. */
  public void deleteCoursework(UUID userId, List<String> ids) {
    if (ids.isEmpty()) return;
    var p = p().addValue("u", userId).addValue("ids", ids);
    db.update("delete from classroom_coursework where user_id = :u and gc_coursework_id in (:ids)", p);
    db.update("delete from classroom_submissions where user_id = :u and gc_coursework_id in (:ids)", p);
    db.update("delete from classroom_feed_items where user_id = :u and ref_id in (:ids)", p);
  }

  public List<SubmissionRow> submissions(UUID userId, String courseId) {
    return db.query("select * from classroom_submissions where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), SUB);
  }

  public List<SubmissionRow> submissionsByCoursework(UUID userId, Collection<String> courseworkIds) {
    if (courseworkIds.isEmpty()) return List.of();
    return db.query("select * from classroom_submissions where user_id = :u and gc_coursework_id in (:ids)", p().addValue("u", userId).addValue("ids", courseworkIds), SUB);
  }

  public void upsertSubmission(UUID userId, String courseId, String courseworkId, String submissionId, String state, boolean late, BigDecimal grade, String alternateLink, Instant updateTime) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("c", courseId).addValue("w", courseworkId).addValue("s", submissionId).addValue("st", state)
        .addValue("late", late).addValue("l", alternateLink);
    n(p, "g", grade);
    t(p, "ut", updateTime);
    db.update("""
        insert into classroom_submissions (id, user_id, gc_course_id, gc_coursework_id, gc_submission_id, state, late, assigned_grade, alternate_link, updated_at_gc)
        values (:id, :u, :c, :w, :s, :st, :late, :g, :l, :ut)
        on conflict (user_id, gc_coursework_id) do update set state = excluded.state, late = excluded.late, assigned_grade = excluded.assigned_grade,
          alternate_link = excluded.alternate_link, updated_at_gc = excluded.updated_at_gc, updated_at = now()""", p);
  }

  /**
   * Coursework left-joined with the user's submission, ordered by due date (nulls last) then newest posted.
   *
   * @param courseId   nullable filter
   * @param courseworkId nullable filter
   * @param dueFrom    nullable: due_at &gt;= dueFrom
   * @param dueTo      nullable: due_at &lt;= dueTo
   * @param dueNotNull only rows with a due date
   */
  public List<AssignmentRow> assignmentRows(UUID userId, String courseId, String courseworkId, Instant dueFrom, Instant dueTo, boolean dueNotNull) {
    StringBuilder sql = new StringBuilder("""
        select cw.*, s.gc_coursework_id as s_cw, s.state as s_state, s.late as s_late, s.assigned_grade as s_grade, s.alternate_link as s_link
        from classroom_coursework cw
        left join classroom_submissions s on s.user_id = cw.user_id and s.gc_coursework_id = cw.gc_coursework_id
        where cw.user_id = :u""");
    var p = p().addValue("u", userId);
    if (courseId != null) {
      sql.append(" and cw.gc_course_id = :c");
      p.addValue("c", courseId);
    }
    if (courseworkId != null) {
      sql.append(" and cw.gc_coursework_id = :w");
      p.addValue("w", courseworkId);
    }
    if (dueNotNull) sql.append(" and cw.due_at is not null");
    if (dueFrom != null) {
      sql.append(" and cw.due_at >= :from");
      t(p, "from", dueFrom);
    }
    if (dueTo != null) {
      sql.append(" and cw.due_at <= :to");
      t(p, "to", dueTo);
    }
    sql.append(" order by cw.due_at asc nulls last, cw.created_at_gc desc");
    return db.query(sql.toString(), p, (rs, i) -> new AssignmentRow(cw(rs), rs.getString("s_cw") == null ? null
        : new SubmissionRow(rs.getString("s_cw"), rs.getString("s_state"), rs.getBoolean("s_late"), rs.getBigDecimal("s_grade"), rs.getString("s_link"))));
  }

  /** Coursework due in [from, to] for active connections, with submission state and course name. */
  public List<ReminderRow> dueBetween(Instant from, Instant to, boolean exclusive) {
    String cond = exclusive ? "cw.due_at > :from and cw.due_at < :to" : "cw.due_at >= :from and cw.due_at <= :to";
    var p = p();
    t(p, "from", from);
    t(p, "to", to);
    return db.query("""
        select cw.user_id, cw.gc_coursework_id, cw.title, cw.due_at, cw.alternate_link, s.state as sub_state, c.name as course_name
        from classroom_coursework cw
        join google_connections g on g.user_id = cw.user_id and g.status = 'active'
        join classroom_courses c on c.user_id = cw.user_id and c.gc_course_id = cw.gc_course_id
        left join classroom_submissions s on s.user_id = cw.user_id and s.gc_coursework_id = cw.gc_coursework_id
        where\s""" + cond, p,
        (rs, i) -> new ReminderRow(rs.getObject("user_id", UUID.class), rs.getString("gc_coursework_id"), rs.getString("title"), inst(rs, "due_at"),
            rs.getString("alternate_link"), rs.getString("sub_state"), rs.getString("course_name")));
  }

  /** Deadlines per Asia/Dhaka day (YYYY-MM-DD) with due_at in [from, to). */
  public Map<String, Long> deadlineCountsByDay(UUID userId, Instant from, Instant to) {
    var p = p().addValue("u", userId);
    t(p, "from", from);
    t(p, "to", to);
    Map<String, Long> out = new HashMap<>();
    db.query("""
        select to_char(due_at at time zone 'Asia/Dhaka', 'YYYY-MM-DD') as day, count(*) as n from classroom_coursework
        where user_id = :u and due_at >= :from and due_at < :to group by 1""", p, rs -> {
      out.put(rs.getString("day"), rs.getLong("n"));
    });
    return out;
  }

  // ---------------------------------------------------------------- materials + announcements

  public List<String> materialIds(UUID userId, String courseId) {
    return db.queryForList("select gc_material_id from classroom_materials where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), String.class);
  }

  public List<MaterialRow> materials(UUID userId, String courseId) {
    return db.query("select * from classroom_materials where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), MAT);
  }

  public List<MaterialRow> materialsByIds(UUID userId, Collection<String> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select * from classroom_materials where user_id = :u and gc_material_id in (:ids)", p().addValue("u", userId).addValue("ids", ids), MAT);
  }

  public void upsertMaterial(UUID userId, String courseId, String id, String title, String description, String materialsJson, String alternateLink, Instant creationTime, Instant updateTime) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("c", courseId).addValue("m", id).addValue("ti", title).addValue("d", description)
        .addValue("mj", materialsJson).addValue("l", alternateLink);
    t(p, "ct", creationTime);
    t(p, "ut", updateTime);
    db.update("""
        insert into classroom_materials (id, user_id, gc_course_id, gc_material_id, title, description, materials_json, alternate_link, created_at_gc, updated_at_gc)
        values (:id, :u, :c, :m, :ti, :d, cast(:mj as jsonb), :l, :ct, :ut)
        on conflict (user_id, gc_material_id) do update set title = excluded.title, description = excluded.description, materials_json = excluded.materials_json,
          alternate_link = excluded.alternate_link, updated_at_gc = excluded.updated_at_gc, updated_at = now()""", p);
  }

  public void deleteMaterials(UUID userId, List<String> ids) {
    if (ids.isEmpty()) return;
    db.update("delete from classroom_materials where user_id = :u and gc_material_id in (:ids)", p().addValue("u", userId).addValue("ids", ids));
  }

  public List<String> announcementIds(UUID userId, String courseId) {
    return db.queryForList("select gc_announcement_id from classroom_announcements where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), String.class);
  }

  public List<AnnouncementRow> announcements(UUID userId, String courseId) {
    return db.query("select * from classroom_announcements where user_id = :u and gc_course_id = :c", Map.of("u", userId, "c", courseId), ANN);
  }

  public List<AnnouncementRow> announcementsByIds(UUID userId, Collection<String> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select * from classroom_announcements where user_id = :u and gc_announcement_id in (:ids)", p().addValue("u", userId).addValue("ids", ids), ANN);
  }

  public void upsertAnnouncement(UUID userId, String courseId, String id, String text, String authorName, String materialsJson, String alternateLink, Instant creationTime, Instant updateTime) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("c", courseId).addValue("a", id).addValue("tx", text).addValue("au", authorName)
        .addValue("mj", materialsJson).addValue("l", alternateLink);
    t(p, "ct", creationTime);
    t(p, "ut", updateTime);
    db.update("""
        insert into classroom_announcements (id, user_id, gc_course_id, gc_announcement_id, text, author_name, materials_json, alternate_link, created_at_gc, updated_at_gc)
        values (:id, :u, :c, :a, :tx, :au, cast(:mj as jsonb), :l, :ct, :ut)
        on conflict (user_id, gc_announcement_id) do update set text = excluded.text, materials_json = excluded.materials_json, alternate_link = excluded.alternate_link,
          updated_at_gc = excluded.updated_at_gc, updated_at = now()""", p);
  }

  // ---------------------------------------------------------------- feed

  public void addFeed(UUID userId, String kind, String courseId, String refId, String title, Instant occurredAt) {
    var p = p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("k", kind).addValue("c", courseId).addValue("r", refId).addValue("ti", title);
    t(p, "at", occurredAt);
    db.update("""
        insert into classroom_feed_items (id, user_id, kind, gc_course_id, ref_id, title, occurred_at) values (:id, :u, :k, :c, :r, :ti, :at)
        on conflict do nothing""", p);
  }

  /** Newest first; keyset on (occurred_at, id). */
  public List<FeedRow> feed(UUID userId, String courseId, boolean unreadOnly, Instant before, UUID beforeId, int limit) {
    StringBuilder sql = new StringBuilder("select * from classroom_feed_items where user_id = :u");
    var p = p().addValue("u", userId).addValue("lim", limit);
    if (courseId != null) {
      sql.append(" and gc_course_id = :c");
      p.addValue("c", courseId);
    }
    if (unreadOnly) sql.append(" and read_at is null");
    if (before != null) {
      sql.append(" and (occurred_at < :b or (occurred_at = :b and id < :bid))");
      t(p, "b", before);
      p.addValue("bid", beforeId);
    }
    sql.append(" order by occurred_at desc, id desc limit :lim");
    return db.query(sql.toString(), p, FEED);
  }

  public Map<String, Long> unreadByCourse(UUID userId) {
    Map<String, Long> out = new HashMap<>();
    db.query("select gc_course_id, count(*) as n from classroom_feed_items where user_id = :u and read_at is null group by gc_course_id", Map.of("u", userId),
        rs -> {
          out.put(rs.getString(1), rs.getLong(2));
        });
    return out;
  }

  public long unreadFeedCount(UUID userId) {
    Long n = db.queryForObject("select count(*) from classroom_feed_items where user_id = :u and read_at is null", Map.of("u", userId), Long.class);
    return n == null ? 0 : n;
  }

  /** @param ids null = all unread */
  public void markFeedRead(UUID userId, List<UUID> ids) {
    if (ids == null) db.update("update classroom_feed_items set read_at = now(), updated_at = now() where user_id = :u and read_at is null", Map.of("u", userId));
    else db.update("update classroom_feed_items set read_at = now(), updated_at = now() where user_id = :u and read_at is null and id in (:ids)", p().addValue("u", userId).addValue("ids", ids));
  }

  // ---------------------------------------------------------------- push registrations

  public List<RegRow> registrations(UUID userId) {
    return db.query("select * from classroom_push_registrations where user_id = :u", Map.of("u", userId), REG);
  }

  public Optional<RegRow> registration(String registrationId) {
    return db.query("select * from classroom_push_registrations where registration_id = :r", Map.of("r", registrationId), REG).stream().findFirst();
  }

  public void updateRegistration(UUID id, String registrationId, Instant expiresAt) {
    db.update("update classroom_push_registrations set registration_id = :r, expires_at = :e, updated_at = now() where id = :id",
        t(p().addValue("id", id).addValue("r", registrationId), "e", expiresAt));
  }

  public void insertRegistration(UUID userId, String feedType, String courseId, String registrationId, Instant expiresAt) {
    db.update("""
        insert into classroom_push_registrations (id, user_id, feed_type, gc_course_id, registration_id, expires_at) values (:id, :u, :f, :c, :r, :e)
        on conflict do nothing""", t(p().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("f", feedType).addValue("c", courseId).addValue("r", registrationId), "e", expiresAt));
  }

  // ---------------------------------------------------------------- disconnect

  /** Every cached Classroom row of the user, plus Classroom-derived notifications (they contain course content). */
  public void deleteAllFor(UUID userId) {
    var p = Map.of("u", userId);
    for (String table : USER_TABLES) db.update("delete from " + table + " where user_id = :u", p);
    db.update("delete from classroom_sync_runs where user_id = :u", p);
    db.update("delete from notifications where user_id = :u and type like 'classroom.%'", p);
  }
}
