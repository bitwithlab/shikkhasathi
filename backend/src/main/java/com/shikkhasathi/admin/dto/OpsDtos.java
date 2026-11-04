package com.shikkhasathi.admin.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Request/response records of the {@code sellerOps}, {@code adminOps} and {@code reports} contract groups. */
public final class OpsDtos {
  private OpsDtos() {}

  public static final String ROLE_RE = "student|seller|admin";

  static String trim(String s) {
    return s == null ? null : s.trim();
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  public record Items<T>(List<T> items) {}

  // ---------------------------------------------------------------- payouts

  public record Payout(UUID id, long amount, String method, String accountMasked, String status, String reference, String note, String createdAt, String processedAt) {}

  public record AdminPayout(@JsonUnwrapped Payout payout, UUID sellerId, String sellerName, String sellerSlug, long available) {}

  public record RequestPayoutBody(@NotNull @Positive Long amount) {}

  public record DecidePayoutBody(@NotNull @Pattern(regexp = "approve|reject|paid") String decision, @Size(max = 100) String reference, @Size(max = 500) String note) {
    public DecidePayoutBody {
      reference = trim(reference);
      note = trim(note);
    }
  }

  // ---------------------------------------------------------------- seller insights

  public record SellerStudent(UUID enrollmentId, String studentName, String institutionShort, UUID courseId, String courseTitle, int progressPercent, String enrolledAt,
                              String lastAccessedAt, boolean completed) {}

  public record SellerStudents(List<SellerStudent> items, String nextCursor, long total) {}

  public record SellerTotals(long revenue, long enrollments, long students, long avgCompletion, double ratingAvg, long ratingCount, long refunds, long openQuestions) {}

  public record SellerDay(String date, long revenue, long enrollments) {}

  public record SellerCourse(UUID id, String title, String status, long enrollments, long revenue, long avgCompletion, double ratingAvg, int ratingCount) {}

  public record SellerAnalytics(SellerTotals totals, List<SellerDay> series, List<SellerCourse> courses) {}

  // ---------------------------------------------------------------- users

  public record AdminUser(UUID id, String name, String email, String phone, List<String> roles, String status, String createdAt, String lastActiveAt, long enrollments,
                          String sellerStatus) {}

  public record UpdateUserBody(@Pattern(regexp = "active|suspended") String status, @Size(min = 1) List<@NotNull @Pattern(regexp = ROLE_RE) String> roles, @Size(max = 300) String reason) {
    public UpdateUserBody {
      reason = trim(reason);
    }
  }

  // ---------------------------------------------------------------- reports

  public record CreateReportBody(@NotNull @Pattern(regexp = "course|review|question|answer|user") String targetType, @NotNull UUID targetId,
                                 @NotNull @Pattern(regexp = "spam|abuse|copyright|misleading|guarantee_claim|other") String reason, @Size(max = 1000) String details) {
    public CreateReportBody {
      details = trim(details);
    }
  }

  public record ReportCreated(UUID id, boolean duplicate) {}

  public record Report(UUID id, String targetType, UUID targetId, String targetPreview, String targetLink, boolean targetHidden, String reason, String details,
                       String reporterName, String status, String resolution, String createdAt, long sameTargetCount) {}

  public record ResolveReportBody(@NotNull @Pattern(regexp = "hide|restore|dismiss|reviewing") String action, @Size(max = 500) String resolution) {
    public ResolveReportBody {
      resolution = trim(resolution);
    }
  }

  // ---------------------------------------------------------------- broadcasts

  public record BroadcastBody(@NotNull @Size(min = 3, max = 120) String title, @NotNull @Size(min = 3, max = 1000) String body,
                              @Size(max = 300) @Pattern(regexp = "^/[\\w\\-/?=&.%]*$", message = "সাইটের ভেতরের লিংক দিন, যেমন /explore") String link,
                              @NotNull @Pattern(regexp = "all|students|sellers|institution") String audience, UUID institutionId, Boolean email) {
    public BroadcastBody {
      title = trim(title);
      body = trim(body);
      link = trim(link);
    }
  }

  public record AudienceBody(@NotNull @Pattern(regexp = "all|students|sellers|institution") String audience, UUID institutionId) {}

  public record Recipients(long recipients) {}

  public record Broadcast(String id, String title, String body, String audience, long recipients, boolean email, String createdAt, String actorName) {}

  // ---------------------------------------------------------------- logs

  public record EmailLog(UUID id, String toEmail, String template, String subject, String status, String provider, String error, int attempts, String createdAt, String sentAt) {}

  public record AuditEntry(UUID id, String actorName, String action, String entity, String entityId, Object before, Object after, String ip, String createdAt) {}

  // ---------------------------------------------------------------- admin analytics

  public record Totals(long gmv, long platformRevenue, long orders, long refunds, long refundAmount, long newStudents, long newSellers, long enrollments, long classroomConnected,
                       long activeStudents7d) {}

  public record Queues(long pendingSellers, long pendingCourses, long pendingInstitutions, long openReports, long refundRequests, long payoutRequests) {}

  public record Day(String date, long gmv, long orders, long signups) {}

  public record TopCourse(UUID id, String title, String sellerName, long sales, long revenue) {}

  public record TopSeller(UUID id, String name, long revenue, long students) {}

  public record AdminAnalytics(Totals totals, Queues queues, List<Day> series, List<TopCourse> topCourses, List<TopSeller> topSellers) {}
}
