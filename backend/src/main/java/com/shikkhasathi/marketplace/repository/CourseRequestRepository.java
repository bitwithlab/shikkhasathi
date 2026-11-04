package com.shikkhasathi.marketplace.repository;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.search.repository.CourseRows;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE {@code course_requests} + {@code course_request_votes}, and the CONTENT lookups the board needs. */
@Repository
public class CourseRequestRepository {
  private final NamedParameterJdbcTemplate core;
  private final NamedParameterJdbcTemplate content;

  public CourseRequestRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate core, @Qualifier("contentJdbc") NamedParameterJdbcTemplate content) {
    this.core = core;
    this.content = content;
  }

  public record Row(UUID id, UUID requesterId, UUID institutionId, UUID subjectMasterId, String subjectName, String courseCode, String examType, String note, String status, int voteCount,
                    UUID claimedBy, UUID linkedCourseId, String dedupeKey, Instant createdAt) {}

  private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("requester_id", UUID.class), rs.getObject("institution_id", UUID.class),
      rs.getObject("subject_master_id", UUID.class), rs.getString("subject_name"), rs.getString("course_code"), rs.getString("exam_type"), rs.getString("note"), rs.getString("status"),
      rs.getInt("vote_count"), rs.getObject("claimed_by", UUID.class), rs.getObject("linked_course_id", UUID.class), rs.getString("dedupe_key"), CourseRows.instant(rs, "created_at"));

  private static final String COLS = "id, requester_id, institution_id, subject_master_id, subject_name, course_code, exam_type, note, status, vote_count, claimed_by, linked_course_id, dedupe_key, created_at";

  private static List<UUID> uniq(Collection<UUID> ids) {
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  public List<Row> list(String status, UUID institutionId, String q, String sort, int limit, int offset) {
    var p = new MapSqlParameterSource().addValue("lim", limit).addValue("off", offset);
    List<String> conds = new ArrayList<>();
    if (!"all".equals(status)) {
      conds.add("status = :status");
      p.addValue("status", status);
    }
    if (institutionId != null) {
      conds.add("institution_id = :inst");
      p.addValue("inst", institutionId);
    }
    if (q != null && !q.isEmpty()) {
      conds.add("(subject_name ilike :q or course_code ilike :q)");
      p.addValue("q", "%" + q + "%");
    }
    String order = "newest".equals(sort) ? "created_at desc" : "case status when 'open' then 0 when 'teacher_working' then 1 else 2 end, vote_count desc, created_at desc";
    return core.query("select " + COLS + " from course_requests" + (conds.isEmpty() ? "" : " where " + String.join(" and ", conds)) + " order by " + order + " limit :lim offset :off", p, ROW);
  }

  public Row find(UUID id) {
    var r = core.query("select " + COLS + " from course_requests where id = :id", Map.of("id", id), ROW);
    return r.isEmpty() ? null : r.get(0);
  }

  public Row findByDedupeKey(String key) {
    var r = core.query("select " + COLS + " from course_requests where dedupe_key = :k", Map.of("k", key), ROW);
    return r.isEmpty() ? null : r.get(0);
  }

  public UUID insert(UUID requesterId, UUID institutionId, UUID subjectMasterId, String subjectName, String courseCode, String examType, String note, String dedupeKey) {
    UUID id = Ids.uuidv7();
    core.update("insert into course_requests (id, requester_id, institution_id, subject_master_id, subject_name, course_code, exam_type, note, dedupe_key, vote_count) values (:id, :r, :i, :s, :n, :c, :e, :note, :k, 1)",
        new MapSqlParameterSource("id", id).addValue("r", requesterId).addValue("i", institutionId).addValue("s", subjectMasterId).addValue("n", subjectName).addValue("c", courseCode)
            .addValue("e", examType).addValue("note", note).addValue("k", dedupeKey));
    return id;
  }

  public UUID myVote(UUID requestId, UUID userId) {
    var r = core.queryForList("select id from course_request_votes where request_id = :r and user_id = :u", Map.of("r", requestId, "u", userId), UUID.class);
    return r.isEmpty() ? null : r.get(0);
  }

  public void addVote(UUID requestId, UUID userId) {
    core.update("insert into course_request_votes (id, request_id, user_id) values (:id, :r, :u) on conflict do nothing", Map.of("id", Ids.uuidv7(), "r", requestId, "u", userId));
  }

  public void deleteVote(UUID voteId) {
    core.update("delete from course_request_votes where id = :id", Map.of("id", voteId));
  }

  public void recountVotes(UUID requestId) {
    core.update("update course_requests set vote_count = (select count(*)::int from course_request_votes where request_id = :id), updated_at = now() where id = :id", Map.of("id", requestId));
  }

  public void claim(UUID id, UUID sellerId, String status, UUID linkedCourseId) {
    var p = new MapSqlParameterSource("id", id).addValue("s", sellerId).addValue("st", status);
    String link = "";
    if (linkedCourseId != null) {
      link = ", linked_course_id = :l";
      p.addValue("l", linkedCourseId);
    }
    core.update("update course_requests set claimed_by = :s, status = :st" + link + ", updated_at = now() where id = :id", p);
  }

  public List<Row> unpublishedLinkedTo(UUID courseId) {
    return core.query("select " + COLS + " from course_requests where linked_course_id = :c and status <> 'published'", Map.of("c", courseId), ROW);
  }

  public void markPublished(UUID id) {
    core.update("update course_requests set status = 'published', updated_at = now() where id = :id", Map.of("id", id));
  }

  public List<UUID> voters(UUID requestId) {
    return core.queryForList("select user_id from course_request_votes where request_id = :r", Map.of("r", requestId), UUID.class);
  }

  public List<UUID> votedAmong(UUID userId, Collection<UUID> requestIds) {
    var u = uniq(requestIds);
    if (u.isEmpty()) return List.of();
    return core.queryForList("select request_id from course_request_votes where user_id = :u and request_id in (:ids)", Map.of("u", userId, "ids", u), UUID.class);
  }

  public Map<UUID, String> sellerNames(Collection<UUID> userIds) {
    Map<UUID, String> out = new HashMap<>();
    var u = uniq(userIds);
    if (u.isEmpty()) return out;
    core.query("select user_id, display_name from seller_profiles where user_id in (:ids)", Map.of("ids", u), rs -> {
      out.put(rs.getObject("user_id", UUID.class), rs.getString("display_name"));
    });
    return out;
  }

  // ---------------------------------------------------------------- CONTENT lookups

  public Map<UUID, String> courseSlugs(Collection<UUID> courseIds) {
    Map<UUID, String> out = new HashMap<>();
    var u = uniq(courseIds);
    if (u.isEmpty()) return out;
    content.query("select id, slug from courses where id in (:ids)", Map.of("ids", u), rs -> {
      out.put(rs.getObject("id", UUID.class), rs.getString("slug"));
    });
    return out;
  }

  public String subjectName(UUID id) {
    var r = content.queryForList("select name from subject_masters where id = :id", Map.of("id", id), String.class);
    return r.isEmpty() ? null : r.get(0);
  }

  public boolean institutionExists(UUID id) {
    return !content.queryForList("select id from institutions where id = :id", Map.of("id", id), UUID.class).isEmpty();
  }

  public record CourseOwner(UUID sellerId, String status, String slug, String title) {}

  public CourseOwner course(UUID id) {
    var r = content.query("select seller_id, status, slug, title from courses where id = :id", Map.of("id", id),
        (rs, i) -> new CourseOwner(rs.getObject("seller_id", UUID.class), rs.getString("status"), rs.getString("slug"), rs.getString("title")));
    return r.isEmpty() ? null : r.get(0);
  }
}
