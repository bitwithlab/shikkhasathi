package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CONTENT reads/writes for admin + seller insights: courses, institutions, institution requests. */
@Repository
public class AdminContentRepository {
  public record CourseInfo(UUID id, UUID sellerId, String title, String slug, String status, BigDecimal ratingAvg, int ratingCount) {}

  private final NamedParameterJdbcTemplate db;

  public AdminContentRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<CourseInfo> COURSE = (rs, i) -> new CourseInfo(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("title"), rs.getString("slug"), rs.getString("status"),
      rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"));
  private static final String COLS = "id, seller_id, title, slug, status, rating_avg, rating_count";

  public Map<UUID, CourseInfo> courses(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, CourseInfo> out = new HashMap<>();
    if (!l.isEmpty()) for (var c : db.query("select " + COLS + " from courses where id in (:ids)", Map.of("ids", l), COURSE)) out.put(c.id(), c);
    return out;
  }

  /** A seller's live (not deleted) courses. */
  public List<CourseInfo> sellerCourses(UUID sellerId) {
    return db.query("select " + COLS + " from courses where seller_id = :s and deleted_at is null", Map.of("s", sellerId), COURSE);
  }

  public void setCourseStatus(UUID id, String status) {
    db.update("update courses set status = :st, updated_at = now() where id = :id", Map.of("id", id, "st", status));
  }

  public void setRating(UUID courseId, String avg, int count) {
    db.update("update courses set rating_avg = cast(:a as numeric), rating_count = :n, updated_at = now() where id = :id", Map.of("id", courseId, "a", avg, "n", count));
  }

  public Map<UUID, String> institutionShortNames(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, String> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select id, short_name from institutions where id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "id"), rs.getString("short_name"));
    });
    return out;
  }

  public long coursesInReview() {
    return count("select count(*) from courses where status = 'in_review'");
  }

  public long pendingInstitutionRequests() {
    return count("select count(*) from institution_requests where status = 'pending'");
  }

  private long count(String sql) {
    Long n = db.queryForObject(sql, Map.of(), Long.class);
    return n == null ? 0 : n;
  }
}
