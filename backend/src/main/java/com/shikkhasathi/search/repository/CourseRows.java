package com.shikkhasathi.search.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** Row shapes of CONTENT {@code courses} / {@code course_targets} used by discovery (read-only, JDBC). */
public final class CourseRows {
  private CourseRows() {}

  public record CourseRow(
      UUID id, UUID sellerId, String title, String slug, String shortDescription, String description, List<String> learnings, List<String> notFor, String faqJson,
      String coverUrl, String coverColor, String language, String examType, String courseType, long price, Long compareAtPrice, String accessType, Integer accessDays,
      String status, Instant publishedAt, Instant contentUpdatedAt, double ratingAvg, int ratingCount, int enrollmentCount, int enrollments30d, int totalDurationSec,
      int lessonCount, boolean sellerVerified, String sellerName, Instant deletedAt, Instant createdAt, Instant updatedAt) {
    /** content_updated_at ?? updated_at */
    public Instant effectiveUpdatedAt() {
      return contentUpdatedAt != null ? contentUpdatedAt : updatedAt;
    }
  }

  public record TargetRow(UUID id, UUID courseId, boolean isGeneral, UUID institutionId, UUID departmentId, Integer semester, UUID subjectMasterId, String courseCode, List<String> batches, List<String> sections) {}

  public static final String COURSE_COLS = "c.id, c.seller_id, c.title, c.slug, c.short_description, c.description, c.learnings, c.not_for, c.faq::text as faq, c.cover_url, c.cover_color, c.language, c.exam_type, c.course_type, c.price, c.compare_at_price, c.access_type, c.access_days, c.status, c.published_at, c.content_updated_at, c.rating_avg, c.rating_count, c.enrollment_count, c.enrollments_30d, c.total_duration_sec, c.lesson_count, c.seller_verified, c.seller_name, c.deleted_at, c.created_at, c.updated_at";

  public static final RowMapper<CourseRow> COURSE = (rs, i) -> new CourseRow(
      rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class), rs.getString("title"), rs.getString("slug"), rs.getString("short_description"), rs.getString("description"),
      strings(rs, "learnings"), strings(rs, "not_for"), rs.getString("faq"), rs.getString("cover_url"), rs.getString("cover_color"), rs.getString("language"), rs.getString("exam_type"),
      rs.getString("course_type"), rs.getLong("price"), (Long) rs.getObject("compare_at_price", Long.class), rs.getString("access_type"), (Integer) rs.getObject("access_days", Integer.class),
      rs.getString("status"), instant(rs, "published_at"), instant(rs, "content_updated_at"), dec(rs.getBigDecimal("rating_avg")), rs.getInt("rating_count"), rs.getInt("enrollment_count"),
      rs.getInt("enrollments_30d"), rs.getInt("total_duration_sec"), rs.getInt("lesson_count"), rs.getBoolean("seller_verified"), rs.getString("seller_name"), instant(rs, "deleted_at"),
      instant(rs, "created_at"), instant(rs, "updated_at"));

  public static final RowMapper<TargetRow> TARGET = (rs, i) -> new TargetRow(
      rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getBoolean("is_general"), rs.getObject("institution_id", UUID.class), rs.getObject("department_id", UUID.class),
      (Integer) rs.getObject("semester", Integer.class), rs.getObject("subject_master_id", UUID.class), rs.getString("course_code"), strings(rs, "batches"), strings(rs, "sections"));

  public static List<String> strings(ResultSet rs, String col) throws SQLException {
    Array a = rs.getArray(col);
    if (a == null) return List.of();
    Object[] arr = (Object[]) a.getArray();
    return Arrays.stream(arr).map(String::valueOf).toList();
  }

  public static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }

  private static double dec(BigDecimal b) {
    return b == null ? 0 : b.doubleValue();
  }
}
