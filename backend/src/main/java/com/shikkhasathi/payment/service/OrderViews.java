package com.shikkhasathi.payment.service;

import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.payment.dto.CommerceDtos.Order;
import com.shikkhasathi.payment.dto.CommerceDtos.OrderItem;
import com.shikkhasathi.payment.dto.CommerceDtos.RefundInfo;
import com.shikkhasathi.payment.repository.CatalogRepository;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.repository.OrderRepository.ItemRow;
import com.shikkhasathi.payment.repository.OrderRepository.OrderRow;
import java.util.*;
import org.springframework.stereotype.Component;

/** Order read model (CORE order rows + CONTENT course slugs/covers/first lessons), batched for lists. */
@Component
public class OrderViews {
  private final OrderRepository orders;
  private final CatalogRepository catalog;
  private final RefundPolicy refunds;

  public OrderViews(OrderRepository orders, CatalogRepository catalog, RefundPolicy refunds) {
    this.orders = orders;
    this.catalog = catalog;
    this.refunds = refunds;
  }

  public Order toOrder(OrderRow o) {
    return toOrders(List.of(o)).get(0);
  }

  public List<Order> toOrders(List<OrderRow> rows) {
    if (rows.isEmpty()) return List.of();
    var ids = rows.stream().map(OrderRow::id).toList();
    Map<UUID, List<ItemRow>> items = new HashMap<>();
    for (var it : orders.itemsFor(ids)) items.computeIfAbsent(it.orderId(), k -> new ArrayList<>()).add(it);
    var invoices = orders.invoiceNumbers(ids);
    var refundRows = orders.refundsByOrder(ids);
    var codes = orders.couponCodes(rows.stream().map(OrderRow::couponId).filter(Objects::nonNull).toList());
    Set<UUID> courseIds = new HashSet<>();
    Set<UUID> firstCourseIds = new HashSet<>();
    for (var l : items.values()) {
      for (var it : l) if (it.courseId() != null) courseIds.add(it.courseId());
      if (!l.isEmpty() && l.get(0).courseId() != null) firstCourseIds.add(l.get(0).courseId());
    }
    Map<UUID, CatalogRepository.CourseRow> courses = new HashMap<>();
    for (var c : catalog.courses(courseIds)) courses.put(c.id(), c);
    var firstLessons = catalog.firstLessons(firstCourseIds);
    var candidates = rows.stream().filter(o -> "paid".equals(o.status()) && o.total() > 0 && !refundRows.containsKey(o.id()) && o.paidAt() != null).toList();
    var checks = refunds.check(candidates);

    List<Order> out = new ArrayList<>();
    for (var o : rows) {
      var its = items.getOrDefault(o.id(), List.of());
      var ref = refundRows.get(o.id());
      var check = checks.get(o.id());
      UUID firstCourse = its.isEmpty() ? null : its.get(0).courseId();
      UUID firstLesson = firstCourse == null ? null : firstLessons.get(firstCourse);
      out.add(new Order(
          o.id(),
          o.status(),
          its.stream().map(i -> {
            var c = i.courseId() == null ? null : courses.get(i.courseId());
            return new OrderItem(i.id(), i.courseId(), i.bundleId(), i.title(), i.price(), i.discount(), c == null ? null : c.coverColor(), c == null ? null : c.slug());
          }).toList(),
          o.subtotal(),
          o.discount(),
          o.total(),
          o.couponId() == null ? null : codes.get(o.couponId()),
          o.paymentMethod(),
          JsonConfig.iso(o.createdAt()),
          JsonConfig.iso(o.paidAt()),
          invoices.get(o.id()),
          ref == null ? null : new RefundInfo(ref.status(), ref.amount(), ref.reason()),
          o.fulfilledAt() != null,
          check != null && check.ok(),
          firstCourse != null && firstLesson != null ? "/learn/" + firstCourse + "/" + firstLesson : null));
    }
    return out;
  }
}
