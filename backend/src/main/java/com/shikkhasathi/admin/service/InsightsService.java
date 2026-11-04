package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.repository.AdminContentRepository;
import com.shikkhasathi.admin.repository.AdminContentRepository.CourseInfo;
import com.shikkhasathi.admin.repository.InsightsRepository;
import com.shikkhasathi.admin.repository.InsightsRepository.DaySum;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Text;
import com.shikkhasathi.config.JsonConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

/** Platform KPIs for admins; students, revenue and completion insights for sellers. */
@Service
public class InsightsService {
  private static final long DAY_MS = 86_400_000L;
  private static final DateTimeFormatter DHAKA_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Dhaka"));

  private final InsightsRepository repo;
  private final AdminContentRepository content;

  public InsightsService(InsightsRepository repo, AdminContentRepository content) {
    this.repo = repo;
    this.content = content;
  }

  static Instant since(int days) {
    return Instant.now().minusMillis(days * DAY_MS);
  }

  /** Last {@code days} Asia/Dhaka dates, oldest first. */
  static List<String> daySeries(int days) {
    long now = System.currentTimeMillis();
    List<String> out = new ArrayList<>();
    for (int i = 0; i < days; i++) out.add(DHAKA_DAY.format(Instant.ofEpochMilli(now - (long) (days - 1 - i) * DAY_MS)));
    return out;
  }

  private static Map<String, DaySum> byDay(List<DaySum> l) {
    Map<String, DaySum> m = new HashMap<>();
    for (var d : l) m.putIfAbsent(d.day(), d);
    return m;
  }

  public AdminAnalytics admin(int days) {
    Instant from = since(days);
    var paid = repo.paidByDay(from);
    var ref = repo.processedRefunds(from);
    var signups = repo.signupsByDay(from);
    var top = repo.topCourses(from, 8);
    var sellersTop = repo.topSellers(from, 5);
    var titles = content.courses(top.stream().map(InsightsRepository.TopCourseRow::courseId).filter(Objects::nonNull).toList());
    Set<UUID> sIds = new LinkedHashSet<>();
    top.forEach(x -> sIds.add(x.sellerId()));
    sellersTop.forEach(x -> sIds.add(x.sellerId()));
    var names = repo.sellerNames(sIds);
    var totals = new Totals(paid.stream().mapToLong(DaySum::sum).sum(), repo.commissionSince(from), paid.stream().mapToLong(DaySum::n).sum(), ref[0], ref[1],
        signups.stream().mapToLong(DaySum::n).sum(), repo.newSellers(from), repo.enrollmentsSince(from), repo.activeClassroomConnections(), repo.activeUsersSince(since(7)));
    var queues = new Queues(repo.pendingSellers(), content.coursesInReview(), content.pendingInstitutionRequests(), repo.openReports(), repo.refundRequests(), repo.payoutRequests());
    var paidDays = byDay(paid);
    var signupDays = byDay(signups);
    var series = daySeries(days).stream().map(date -> {
      var p = paidDays.get(date);
      var s = signupDays.get(date);
      return new Day(date, p != null ? p.sum() : 0, p != null ? p.n() : 0, s != null ? s.n() : 0);
    }).toList();
    var topCourses = top.stream().map(x -> {
      var c = titles.get(x.courseId());
      return new TopCourse(x.courseId(), c != null ? c.title() : "", names.getOrDefault(x.sellerId(), ""), x.sales(), x.revenue());
    }).toList();
    var topSellers = sellersTop.stream().map(x -> new TopSeller(x.sellerId(), names.getOrDefault(x.sellerId(), ""), x.revenue(), x.students())).toList();
    return new AdminAnalytics(totals, queues, series, topCourses, topSellers);
  }

  // ---------------------------------------------------------------- seller

  public SellerStudents students(UUID sellerId, UUID courseId, String q, String cursor, int limit) {
    var mine = content.sellerCourses(sellerId);
    var ids = mine.stream().map(CourseInfo::id).filter(id -> courseId == null || id.equals(courseId)).toList();
    if (ids.isEmpty()) return new SellerStudents(List.of(), null, 0);
    var cur = Cursor.decode(cursor);
    String like = q != null && !q.isEmpty() ? "%" + q.replaceAll("[%_]", "") + "%" : null;
    long total = repo.countStudents(ids, like);
    var rows = repo.students(ids, like, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    var profs = repo.studentInstitutions(pg.items().stream().map(InsightsRepository.EnrollmentRow::studentId).toList());
    var insts = content.institutionShortNames(profs.values().stream().filter(Objects::nonNull).toList());
    Map<UUID, String> titles = new HashMap<>();
    mine.forEach(c -> titles.put(c.id(), c.title()));
    // students appear by short name only — teachers never see contact details
    var items = pg.items().stream().map(r -> {
      UUID inst = profs.get(r.studentId());
      return new SellerStudent(r.id(), Text.shortName(r.name()), inst != null ? insts.get(inst) : null, r.courseId(), titles.getOrDefault(r.courseId(), ""), r.progressPercent(),
          JsonConfig.iso(r.createdAt()), JsonConfig.iso(r.lastAccessedAt()), r.completedAt() != null);
    }).toList();
    return new SellerStudents(items, pg.nextCursor(), total);
  }

  public SellerAnalytics seller(UUID sellerId, int days) {
    Instant from = since(days);
    var mine = content.sellerCourses(sellerId);
    var ids = mine.stream().map(CourseInfo::id).toList();
    var sales = repo.sellerEarningsByDay(sellerId, from);
    var enrDays = repo.enrollmentsByDay(ids, from);
    var perCourse = repo.activeEnrollmentStats(ids);
    long refunds = repo.sellerRefunds(sellerId, from);
    long openQ = repo.openQuestions(sellerId);
    Map<UUID, Long> revByCourse = ids.isEmpty() ? Map.of() : repo.revenueByCourse(sellerId, from);
    Map<UUID, InsightsRepository.CourseEnrollStats> stats = new HashMap<>();
    perCourse.forEach(x -> stats.put(x.courseId(), x));
    var rated = mine.stream().filter(c -> c.ratingCount() > 0).toList();
    long totalStudents = perCourse.stream().mapToLong(InsightsRepository.CourseEnrollStats::students).sum();
    long ratingCount = rated.stream().mapToLong(CourseInfo::ratingCount).sum();
    double ratingAvg = rated.isEmpty() ? 0
        : Math.round(rated.stream().mapToDouble(c -> c.ratingAvg().doubleValue() * c.ratingCount()).sum() / ratingCount * 10) / 10.0;
    var totals = new SellerTotals(sales.stream().mapToLong(DaySum::sum).sum(), enrDays.stream().mapToLong(DaySum::n).sum(), totalStudents,
        totalStudents != 0 ? Math.round(perCourse.stream().mapToDouble(x -> (double) x.avg() * x.students()).sum() / totalStudents) : 0, ratingAvg, ratingCount, refunds, openQ);
    var salesDays = byDay(sales);
    var enrollDays = byDay(enrDays);
    var series = daySeries(days).stream().map(date -> {
      var s = salesDays.get(date);
      var e = enrollDays.get(date);
      return new SellerDay(date, s != null ? s.sum() : 0, e != null ? e.n() : 0);
    }).toList();
    var courses = new ArrayList<>(mine.stream().map(c -> {
      var st = stats.get(c.id());
      return new SellerCourse(c.id(), c.title(), c.status(), st != null ? st.n() : 0, revByCourse.getOrDefault(c.id(), 0L), st != null ? st.avg() : 0, c.ratingAvg().doubleValue(),
          c.ratingCount());
    }).toList());
    courses.sort(Comparator.comparingLong(SellerCourse::revenue).reversed().thenComparing(Comparator.comparingLong(SellerCourse::enrollments).reversed()));
    return new SellerAnalytics(totals, series, courses);
  }
}
