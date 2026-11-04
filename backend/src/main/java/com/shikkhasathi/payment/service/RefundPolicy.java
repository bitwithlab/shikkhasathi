package com.shikkhasathi.payment.service;

import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.payment.repository.CatalogRepository;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.repository.OrderRepository.OrderRow;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Refund eligibility (≤ refund_window_hours since purchase, < refund_max_watch_percent watched). Batched across orders. */
@Component
public class RefundPolicy {
  static final long HOUR_MS = 3_600_000L;

  public record Check(boolean ok, String reason, int pct) {}

  private final OrderRepository orders;
  private final CatalogRepository catalog;
  private final IntegrationService integrations;

  public RefundPolicy(OrderRepository orders, CatalogRepository catalog, IntegrationService integrations) {
    this.orders = orders;
    this.catalog = catalog;
    this.integrations = integrations;
  }

  /** Watched percent of each order's courses by its student (order id → percent). */
  public Map<UUID, Integer> watchedPercent(Collection<OrderRow> rows) {
    Map<UUID, Integer> out = new HashMap<>();
    if (rows.isEmpty()) return out;
    var byOrder = orders.enrolledCoursesByOrder(rows.stream().map(OrderRow::id).toList());
    Set<UUID> allCourses = new HashSet<>();
    byOrder.values().forEach(allCourses::addAll);
    Map<UUID, Integer> duration = new HashMap<>();
    for (var c : catalog.courses(allCourses)) duration.put(c.id(), c.totalDurationSec());
    var watched = orders.watchedSeconds(rows.stream().map(OrderRow::studentId).toList(), allCourses);
    for (var o : rows) {
      var courseIds = new LinkedHashSet<>(byOrder.getOrDefault(o.id(), List.of()));
      long tot = 0;
      long w = 0;
      for (UUID c : courseIds) {
        tot += duration.getOrDefault(c, 0);
        w += watched.getOrDefault(o.studentId() + ":" + c, 0L);
      }
      out.put(o.id(), courseIds.isEmpty() || tot == 0 ? 0 : (int) Math.round((double) w / tot * 100));
    }
    return out;
  }

  public int watchedPercent(OrderRow o) {
    return watchedPercent(List.of(o)).get(o.id());
  }

  /** Eligibility for each order (watched percent is computed only for orders inside the window). */
  public Map<UUID, Check> check(Collection<OrderRow> rows) {
    double windowH = integrations.businessNumber("refund_window_hours", 48);
    double maxPct = integrations.businessNumber("refund_max_watch_percent", 20);
    Map<UUID, Check> out = new HashMap<>();
    List<OrderRow> inWindow = new ArrayList<>();
    long now = Instant.now().toEpochMilli();
    for (var o : rows) {
      double hours = o.paidAt() != null ? (now - o.paidAt().toEpochMilli()) / (double) HOUR_MS : Double.POSITIVE_INFINITY;
      if (hours > windowH) out.put(o.id(), new Check(false, "কেনার " + Money.num(windowH) + " ঘণ্টা পেরিয়ে গেছে।", 0));
      else inWindow.add(o);
    }
    var pcts = watchedPercent(inWindow);
    for (var o : inWindow) {
      int pct = pcts.get(o.id());
      if (pct >= maxPct) out.put(o.id(), new Check(false, "কোর্সের " + Money.num(maxPct) + "%-এর বেশি দেখা হয়ে গেছে।", pct));
      else out.put(o.id(), new Check(true, "", pct));
    }
    return out;
  }

  public Check check(OrderRow o) {
    return check(List.of(o)).get(o.id());
  }
}
