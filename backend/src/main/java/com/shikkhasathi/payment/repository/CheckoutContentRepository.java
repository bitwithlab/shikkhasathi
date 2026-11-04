package com.shikkhasathi.payment.repository;

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

/** CONTENT reads/writes for checkout: courses, bundles + bundle items, lessons. */
@Repository
public class CheckoutContentRepository {
  public record CourseRow(UUID id, UUID sellerId, String title, String slug, String status, long price, String coverColor, String accessType, Integer accessDays,
                          Instant accessUntil, int totalDurationSec, Instant deletedAt) {}

  public record BundleRow(UUID id, UUID sellerId, String title, String slug, String description, long price, Long compareAtPrice, String status, Instant createdAt) {}

  public record BundleCourse(UUID id, String slug, String title, long price) {}

  private final NamedParameterJdbcTemplate db;

  public CheckoutContentRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<CourseRow> COURSE = (rs, i) -> new CourseRow(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("title"), rs.getString("slug"), rs.getString("status"),
      rs.getLong("price"), rs.getString("cover_color"), rs.getString("access_type"), Rows.intOrNull(rs, "access_days"), ts(rs, "access_until"), rs.getInt("total_duration_sec"),
      ts(rs, "deleted_at"));
  static final RowMapper<BundleRow> BUNDLE = (rs, i) -> {
    long cmp = rs.getLong("compare_at_price");
    Long compare = rs.wasNull() ? null : cmp;
    return new BundleRow(uuid(rs, "id"), uuid(rs, "seller_id"), rs.getString("title"), rs.getString("slug"), rs.getString("description"), rs.getLong("price"), compare,
        rs.getString("status"), ts(rs, "created_at"));
  };
  private static final String COURSE_COLS = "id, seller_id, title, slug, status, price, cover_color, access_type, access_days, access_until, total_duration_sec, deleted_at";

  public Optional<CourseRow> liveCourse(UUID id) {
    var l = db.query("select " + COURSE_COLS + " from courses where id = :id and deleted_at is null", Map.of("id", id), COURSE);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public Optional<CourseRow> course(UUID id) {
    var l = db.query("select " + COURSE_COLS + " from courses where id = :id", Map.of("id", id), COURSE);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<CourseRow> courses(Collection<UUID> ids) {
    var l = uniq(ids);
    if (l.isEmpty()) return List.of();
    return db.query("select " + COURSE_COLS + " from courses where id in (:ids)", Map.of("ids", l), COURSE);
  }

  public List<CourseRow> liveCourses(Collection<UUID> ids) {
    var l = uniq(ids);
    if (l.isEmpty()) return List.of();
    return db.query("select " + COURSE_COLS + " from courses where id in (:ids) and deleted_at is null", Map.of("ids", l), COURSE);
  }

  /** Total video seconds over the given courses. */
  public long totalDuration(Collection<UUID> ids) {
    var l = uniq(ids);
    if (l.isEmpty()) return 0;
    Long n = db.queryForObject("select coalesce(sum(total_duration_sec), 0) from courses where id in (:ids)", Map.of("ids", l), Long.class);
    return n == null ? 0 : n;
  }

  /** First lesson (by position) per course. */
  public Map<UUID, UUID> firstLessons(Collection<UUID> courseIds) {
    var l = uniq(courseIds);
    Map<UUID, UUID> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select distinct on (course_id) course_id, id from lessons where course_id in (:ids) order by course_id, position", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "course_id"), uuid(rs, "id"));
    });
    return out;
  }

  public void incrementEnrollment(UUID courseId) {
    db.update("update courses set enrollment_count = enrollment_count + 1, enrollments_30d = enrollments_30d + 1, updated_at = now() where id = :id", Map.of("id", courseId));
  }

  public void decrementEnrollments(Collection<UUID> courseIds) {
    var l = uniq(courseIds);
    if (l.isEmpty()) return;
    db.update("update courses set enrollment_count = greatest(enrollment_count - 1, 0), updated_at = now() where id in (:ids)", Map.of("ids", l));
  }

  // ---------------------------------------------------------------- bundles

  public Optional<BundleRow> bundle(UUID id) {
    var l = db.query("select * from bundles where id = :id", Map.of("id", id), BUNDLE);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public Optional<BundleRow> publishedBundleBySlug(String slug) {
    var l = db.query("select * from bundles where slug = :s and status = 'published'", Map.of("s", slug), BUNDLE);
    return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
  }

  public List<UUID> bundleCourseIds(UUID bundleId) {
    return db.queryForList("select course_id from bundle_items where bundle_id = :b", Map.of("b", bundleId), UUID.class);
  }

  public List<BundleCourse> bundleCourses(UUID bundleId) {
    return db.query("select c.id, c.slug, c.title, c.price from bundle_items bi inner join courses c on c.id = bi.course_id where bi.bundle_id = :b", Map.of("b", bundleId),
        (rs, i) -> new BundleCourse(uuid(rs, "id"), rs.getString("slug"), rs.getString("title"), rs.getLong("price")));
  }

  public List<BundleRow> sellerBundles(UUID sellerId) {
    return db.query("select * from bundles where seller_id = :s order by created_at desc", Map.of("s", sellerId), BUNDLE);
  }

  public Map<UUID, Integer> bundleItemCounts(Collection<UUID> bundleIds) {
    var l = uniq(bundleIds);
    Map<UUID, Integer> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select bundle_id, count(*)::int as n from bundle_items where bundle_id in (:ids) group by bundle_id", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "bundle_id"), rs.getInt("n"));
    });
    return out;
  }

  /** Inside the caller's CONTENT transaction. */
  public UUID insertBundle(UUID sellerId, String title, String slug, String description, long price, long compareAtPrice, List<UUID> courseIds) {
    UUID id = Ids.uuidv7();
    db.update("insert into bundles (id, seller_id, title, slug, description, price, compare_at_price, status) values (:id, :s, :t, :slug, :d, :p, :c, 'published')",
        new MapSqlParameterSource().addValue("id", id).addValue("s", sellerId).addValue("t", title).addValue("slug", slug).addValue("d", description).addValue("p", price)
            .addValue("c", compareAtPrice));
    for (UUID c : courseIds)
      db.update("insert into bundle_items (id, bundle_id, course_id) values (:id, :b, :c)", Map.of("id", Ids.uuidv7(), "b", id, "c", c));
    return id;
  }
}
