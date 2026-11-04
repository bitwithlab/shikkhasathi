package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import com.shikkhasathi.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE reports (moderation queue) + the CORE report targets (reviews, questions, answers, users). */
@Repository
public class ReportRepository {
  public record ReportRow(UUID id, UUID reporterId, String targetType, UUID targetId, String reason, String details, String status, String resolution, Instant createdAt) {}

  public record ReviewTarget(UUID id, UUID courseId, int rating, String body, String status) {}

  public record QuestionTarget(UUID id, UUID courseId, UUID lessonId, String body, String status) {}

  public record SimpleTarget(UUID id, String text, String status) {}

  private final NamedParameterJdbcTemplate db;

  public ReportRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<ReportRow> REPORT = (rs, i) -> new ReportRow(uuid(rs, "id"), uuid(rs, "reporter_id"), rs.getString("target_type"), uuid(rs, "target_id"),
      rs.getString("reason"), rs.getString("details"), rs.getString("status"), rs.getString("resolution"), ts(rs, "created_at"));

  public Optional<UUID> openDuplicate(UUID reporterId, String type, UUID targetId) {
    var l = db.queryForList("select id from reports where reporter_id = :r and target_type = :t and target_id = :id and status in ('open', 'reviewing')",
        Map.of("r", reporterId, "t", type, "id", targetId), UUID.class);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public UUID insert(UUID reporterId, String type, UUID targetId, String reason, String details) {
    UUID id = Ids.uuidv7();
    db.update("insert into reports (id, reporter_id, target_type, target_id, reason, details) values (:id, :r, :t, :tid, :reason, :d)",
        new MapSqlParameterSource().addValue("id", id).addValue("r", reporterId).addValue("t", type).addValue("tid", targetId).addValue("reason", reason).addValue("d", details));
    return id;
  }

  public Optional<ReportRow> byId(UUID id) {
    var l = db.query("select * from reports where id = :id", Map.of("id", id), REPORT);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<ReportRow> byStatus(String status, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("st", status).addValue("lim", limit);
    String cur = "";
    if (curAt != null) {
      cur = " and created_at < :ca";
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from reports where status = :st" + cur + " order by created_at desc limit :lim", p, REPORT);
  }

  /** Reports per target ("type:id" → count) over all statuses. */
  public Map<String, Long> sameTargetCounts(Collection<ReportRow> rows) {
    Map<String, Long> out = new HashMap<>();
    var ids = uniq(rows.stream().map(ReportRow::targetId).toList());
    if (ids.isEmpty()) return out;
    db.query("select target_type, target_id, count(*) as n from reports where target_id in (:ids) group by target_type, target_id", Map.of("ids", ids), rs -> {
      out.put(rs.getString("target_type") + ":" + uuid(rs, "target_id"), rs.getLong("n"));
    });
    return out;
  }

  /** Resolve the report itself, or (wide) every open/reviewing report on the same target plus this one. */
  public void resolve(ReportRow r, String status, UUID actorId, String resolution, boolean wide) {
    var p = new MapSqlParameterSource().addValue("id", r.id()).addValue("st", status).addValue("a", actorId).addValue("res", resolution).addValue("t", r.targetType())
        .addValue("tid", r.targetId());
    String where = wide ? "((target_type = :t and target_id = :tid and status in ('open', 'reviewing')) or id = :id)" : "id = :id";
    db.update("update reports set status = :st, resolved_by = :a, resolution = :res, updated_at = now() where " + where, p);
  }

  // ---------------------------------------------------------------- targets

  public Map<UUID, ReviewTarget> reviews(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, ReviewTarget> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select id, course_id, rating, body, status from reviews where id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "id"), new ReviewTarget(uuid(rs, "id"), uuid(rs, "course_id"), rs.getInt("rating"), rs.getString("body"), rs.getString("status")));
    });
    return out;
  }

  public Map<UUID, QuestionTarget> questions(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, QuestionTarget> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select id, course_id, lesson_id, body, status from questions where id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "id"), new QuestionTarget(uuid(rs, "id"), uuid(rs, "course_id"), uuid(rs, "lesson_id"), rs.getString("body"), rs.getString("status")));
    });
    return out;
  }

  public Map<UUID, SimpleTarget> answers(Collection<UUID> ids) {
    return simple("select id, body as text, status from answers where id in (:ids)", ids);
  }

  public Map<UUID, SimpleTarget> users(Collection<UUID> ids) {
    return simple("select id, name as text, status from users where id in (:ids)", ids);
  }

  private Map<UUID, SimpleTarget> simple(String sql, Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, SimpleTarget> out = new HashMap<>();
    if (!l.isEmpty()) db.query(sql, Map.of("ids", l), rs -> {
      out.put(uuid(rs, "id"), new SimpleTarget(uuid(rs, "id"), rs.getString("text"), rs.getString("status")));
    });
    return out;
  }

  // ---------------------------------------------------------------- hide / restore

  /** @return the review's course id (empty when the review doesn't exist) */
  public Optional<UUID> setReviewStatus(UUID id, String status) {
    var l = db.queryForList("update reviews set status = :st, updated_at = now() where id = :id returning course_id", Map.of("id", id, "st", status), UUID.class);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public record Rating(String avg, int count) {}

  /** Visible-review aggregate, avg rounded to 2 decimals (text, like numeric(3,2)). */
  public Rating visibleRating(UUID courseId) {
    return db.queryForObject("select coalesce(round(avg(rating)::numeric, 2), 0)::text as avg, count(*)::int as n from reviews where course_id = :c and status = 'visible'",
        Map.of("c", courseId), (rs, i) -> new Rating(rs.getString("avg"), rs.getInt("n")));
  }

  public void setQuestionStatus(UUID id, String status) {
    db.update("update questions set status = :st, updated_at = now() where id = :id", Map.of("id", id, "st", status));
  }

  public void setAnswerStatus(UUID id, String status) {
    db.update("update answers set status = :st, updated_at = now() where id = :id", Map.of("id", id, "st", status));
  }
}
