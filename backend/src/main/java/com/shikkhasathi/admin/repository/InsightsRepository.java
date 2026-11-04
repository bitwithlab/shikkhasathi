package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uniq;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE aggregate/reporting SQL for the admin dashboard and seller insights (days are Asia/Dhaka). */
@Repository
public class InsightsRepository {
  public record DaySum(String day, long sum, long n) {}

  public record TopCourseRow(UUID courseId, long sales, long revenue, UUID sellerId) {}

  public record TopSellerRow(UUID sellerId, long revenue, long students) {}

  public record CourseEnrollStats(UUID courseId, long n, long avg, long students) {}

  public record EnrollmentRow(UUID id, UUID studentId, UUID courseId, String name, int progressPercent, Instant createdAt, Instant lastAccessedAt, Instant completedAt) {}

  private final NamedParameterJdbcTemplate db;

  public InsightsRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = db.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private static MapSqlParameterSource from(Instant from) {
    return new MapSqlParameterSource().addValue("from", t(from));
  }

  private static final String DAY = "'Asia/Dhaka', 'YYYY-MM-DD')";

  // ---------------------------------------------------------------- admin

  /** GMV + order count per day (paid/refunded, total > 0). */
  public List<DaySum> paidByDay(Instant f) {
    return db.query("select to_char(paid_at at time zone " + DAY + " as day, coalesce(sum(total), 0) as s, count(*) as n from orders "
        + "where status in ('paid', 'refunded') and paid_at >= :from and total > 0 group by 1", from(f), (rs, i) -> new DaySum(rs.getString("day"), rs.getLong("s"), rs.getLong("n")));
  }

  public long[] processedRefunds(Instant f) {
    return db.queryForObject("select count(*) as n, coalesce(sum(amount), 0) as s from refunds where status = 'processed' and processed_at >= :from", from(f),
        (rs, i) -> new long[] {rs.getLong("n"), rs.getLong("s")});
  }

  public List<DaySum> signupsByDay(Instant f) {
    return db.query("select to_char(created_at at time zone " + DAY + " as day, count(*) as n from users where created_at >= :from group by 1", from(f),
        (rs, i) -> new DaySum(rs.getString("day"), 0, rs.getLong("n")));
  }

  public long newSellers(Instant f) {
    return count("select count(*) from seller_profiles where created_at >= :from", from(f));
  }

  public long enrollmentsSince(Instant f) {
    return count("select count(*) from enrollments where created_at >= :from", from(f));
  }

  public long activeClassroomConnections() {
    return count("select count(*) from google_connections where status = 'active'", new MapSqlParameterSource());
  }

  public long activeUsersSince(Instant f) {
    return count("select count(*) from users where last_active_at >= :from", from(f));
  }

  public long pendingSellers() {
    return count("select count(*) from seller_profiles where verification_status = 'pending'", new MapSqlParameterSource());
  }

  public long openReports() {
    return count("select count(*) from reports where status in ('open', 'reviewing')", new MapSqlParameterSource());
  }

  public long refundRequests() {
    return count("select count(*) from refunds where status = 'requested'", new MapSqlParameterSource());
  }

  public long payoutRequests() {
    return count("select count(*) from payouts where status in ('requested', 'approved')", new MapSqlParameterSource());
  }

  public long commissionSince(Instant f) {
    return count("select coalesce(-sum(amount), 0) from ledger_entries where type = 'commission' and created_at >= :from", from(f));
  }

  public List<TopCourseRow> topCourses(Instant f, int limit) {
    return db.query("select oi.course_id, count(*) as sales, coalesce(sum(oi.price - oi.discount), 0) as revenue, oi.seller_id from order_items oi "
            + "inner join orders o on o.id = oi.order_id where o.status = 'paid' and o.paid_at >= :from and oi.course_id is not null "
            + "group by oi.course_id, oi.seller_id order by 3 desc limit :lim",
        from(f).addValue("lim", limit), (rs, i) -> new TopCourseRow(uuid(rs, "course_id"), rs.getLong("sales"), rs.getLong("revenue"), uuid(rs, "seller_id")));
  }

  public List<TopSellerRow> topSellers(Instant f, int limit) {
    return db.query("select oi.seller_id, coalesce(sum(oi.price - oi.discount), 0) as revenue, count(distinct o.student_id) as students from order_items oi "
            + "inner join orders o on o.id = oi.order_id where o.status = 'paid' and o.paid_at >= :from group by oi.seller_id order by 2 desc limit :lim",
        from(f).addValue("lim", limit), (rs, i) -> new TopSellerRow(uuid(rs, "seller_id"), rs.getLong("revenue"), rs.getLong("students")));
  }

  public Map<UUID, String> sellerNames(Collection<UUID> ids) {
    var l = uniq(ids);
    Map<UUID, String> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select user_id, display_name from seller_profiles where user_id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "user_id"), rs.getString("display_name"));
    });
    return out;
  }

  // ---------------------------------------------------------------- seller

  /** Net earnings (sale + commission + refund) per Dhaka day. */
  public List<DaySum> sellerEarningsByDay(UUID sellerId, Instant f) {
    return db.query("select to_char(created_at at time zone " + DAY + " as day, coalesce(sum(amount), 0) as s from ledger_entries "
            + "where seller_id = :s and type in ('sale', 'commission', 'refund') and created_at >= :from group by 1",
        from(f).addValue("s", sellerId), (rs, i) -> new DaySum(rs.getString("day"), rs.getLong("s"), 0));
  }

  public List<DaySum> enrollmentsByDay(Collection<UUID> courseIds, Instant f) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select to_char(created_at at time zone " + DAY + " as day, count(*) as n from enrollments where course_id in (:ids) and created_at >= :from group by 1",
        from(f).addValue("ids", uniq(courseIds)), (rs, i) -> new DaySum(rs.getString("day"), 0, rs.getLong("n")));
  }

  public List<CourseEnrollStats> activeEnrollmentStats(Collection<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    return db.query("select course_id, count(*) as n, coalesce(round(avg(progress_percent)), 0)::int as avg, count(distinct student_id) as students from enrollments "
            + "where course_id in (:ids) and status = 'active' group by course_id",
        new MapSqlParameterSource().addValue("ids", uniq(courseIds)), (rs, i) -> new CourseEnrollStats(uuid(rs, "course_id"), rs.getLong("n"), rs.getLong("avg"), rs.getLong("students")));
  }

  public long sellerRefunds(UUID sellerId, Instant f) {
    return count("select count(*) from refunds r inner join order_items oi on oi.order_id = r.order_id where oi.seller_id = :s and r.status = 'processed' and r.processed_at >= :from",
        from(f).addValue("s", sellerId));
  }

  public long openQuestions(UUID sellerId) {
    return count("select count(*) from questions where seller_id = :s and status = 'open'", new MapSqlParameterSource().addValue("s", sellerId));
  }

  /** course id → revenue (paid orders since {@code from}); the null course (bundles) is keyed by null. */
  public Map<UUID, Long> revenueByCourse(UUID sellerId, Instant f) {
    Map<UUID, Long> out = new HashMap<>();
    db.query("select oi.course_id, coalesce(sum(oi.price - oi.discount), 0) as s, count(*) as n from order_items oi inner join orders o on o.id = oi.order_id "
        + "where oi.seller_id = :s and o.status = 'paid' and o.paid_at >= :from group by oi.course_id", from(f).addValue("s", sellerId), rs -> {
          out.put(uuid(rs, "course_id"), rs.getLong("s"));
        });
    return out;
  }

  // ---------------------------------------------------------------- seller students

  private static String studentWhere(MapSqlParameterSource p, Collection<UUID> courseIds, String like) {
    p.addValue("ids", uniq(courseIds));
    String w = "e.course_id in (:ids) and e.status = 'active'";
    if (like != null) {
      w += " and u.name ilike :q";
      p.addValue("q", like);
    }
    return w;
  }

  public long countStudents(Collection<UUID> courseIds, String like) {
    var p = new MapSqlParameterSource();
    return count("select count(*) from enrollments e inner join users u on u.id = e.student_id where " + studentWhere(p, courseIds, like), p);
  }

  public List<EnrollmentRow> students(Collection<UUID> courseIds, String like, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("lim", limit);
    String w = studentWhere(p, courseIds, like);
    if (curAt != null) {
      w += " and e.created_at < :ca";
      p.addValue("ca", t(curAt));
    }
    return db.query("select e.id, e.student_id, e.course_id, u.name, e.progress_percent, e.created_at, e.last_accessed_at, e.completed_at from enrollments e "
            + "inner join users u on u.id = e.student_id where " + w + " order by e.created_at desc limit :lim", p,
        (rs, i) -> new EnrollmentRow(uuid(rs, "id"), uuid(rs, "student_id"), uuid(rs, "course_id"), rs.getString("name"), rs.getInt("progress_percent"), ts(rs, "created_at"),
            ts(rs, "last_accessed_at"), ts(rs, "completed_at")));
  }

  public Map<UUID, UUID> studentInstitutions(Collection<UUID> studentIds) {
    var l = uniq(studentIds);
    Map<UUID, UUID> out = new HashMap<>();
    if (!l.isEmpty()) db.query("select user_id, institution_id from student_profiles where user_id in (:ids)", Map.of("ids", l), rs -> {
      out.put(uuid(rs, "user_id"), uuid(rs, "institution_id"));
    });
    return out;
  }
}
