package com.shikkhasathi.payment.service;

import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.payment.invoice.InvoicePdfRenderer;
import com.shikkhasathi.payment.repository.CheckoutContentRepository;
import com.shikkhasathi.payment.repository.CheckoutContentRepository.CourseRow;
import com.shikkhasathi.payment.repository.CouponRepository;
import com.shikkhasathi.payment.repository.LedgerRepository.NewEntry;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.repository.OrderRepository.InvoiceRow;
import com.shikkhasathi.payment.repository.OrderRepository.ItemRow;
import com.shikkhasathi.payment.repository.OrderRepository.OrderRow;
import com.shikkhasathi.student.service.DashboardCache;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Paid order → enrollments → ledger → coupon redemption → invoice PDF → email. Every step is idempotent (unique
 * enrollment per student+course, ledger dedupe keys, one invoice per order, fulfilled_at as the outbox marker), so the
 * {@code payments.fulfil} job and the hourly reconcile can re-run it safely after a half-finished cross-DB attempt.
 */
@Service
public class FulfilmentService {
  private static final long DAY_MS = 86_400_000L;

  private final OrderRepository orders;
  private final CheckoutContentRepository catalog;
  private final CouponRepository coupons;
  private final LedgerService ledger;
  private final IntegrationService integrations;
  private final NotificationService notifications;
  private final StorageService storage;
  private final InvoicePdfRenderer pdf;
  private final DashboardCache dashboard;
  private final JsonMapper json;

  public FulfilmentService(OrderRepository orders, CheckoutContentRepository catalog, CouponRepository coupons, LedgerService ledger, IntegrationService integrations,
                           NotificationService notifications, StorageService storage, InvoicePdfRenderer pdf, DashboardCache dashboard, JsonMapper json) {
    this.orders = orders;
    this.catalog = catalog;
    this.coupons = coupons;
    this.ledger = ledger;
    this.integrations = integrations;
    this.notifications = notifications;
    this.storage = storage;
    this.pdf = pdf;
    this.dashboard = dashboard;
    this.json = json;
  }

  /** Idempotent: safe to run any number of times. */
  public void fulfil(UUID orderId) {
    var o = orders.order(orderId).orElse(null);
    if (o == null || !"paid".equals(o.status()) || o.fulfilledAt() != null) return;
    var items = orders.items(orderId);
    double holdDays = integrations.businessNumber("ledger_hold_days", 7);
    double defaultCommission = integrations.businessNumber("commission_percent", 20);
    for (var it : items) {
      List<UUID> courseIds = it.courseId() != null ? List.of(it.courseId()) : catalog.bundleCourseIds(it.bundleId());
      for (CourseRow c : catalog.courses(courseIds)) {
        Instant accessUntil = "days".equals(c.accessType()) && c.accessDays() != null && c.accessDays() != 0 ? Instant.now().plusMillis(c.accessDays() * DAY_MS)
            : "until_exam".equals(c.accessType()) ? c.accessUntil() : null;
        if (orders.insertEnrollment(o.studentId(), c.id(), orderId, it.bundleId(), accessUntil)) {
          catalog.incrementEnrollment(c.id());
          notifications.notify(NotifyInput.of(c.sellerId(), NotificationTypes.SELLER_NEW_ENROLLMENT, orderId + ":" + c.id(), "নতুন শিক্ষার্থী এনরোল করেছেন", c.title())
              .withLink("/teach/students"));
        }
      }
      long net = it.price() - it.discount();
      if (net > 0) {
        Integer sp = orders.commissionRate(it.sellerId());
        double rate = sp != null ? sp : defaultCommission;
        Instant availableAt = Instant.now().plusMillis((long) (holdDays * DAY_MS));
        ledger.append(it.sellerId(), new NewEntry("sale", net, orderId, it.id(), null, null, it.title(), availableAt, "sale:" + it.id()));
        ledger.append(it.sellerId(), new NewEntry("commission", -Math.round((net * rate) / 100), orderId, it.id(), null, null, Money.num(rate) + "% platform commission", availableAt,
            "commission:" + it.id()));
      }
    }
    if (o.couponId() != null) coupons.redeem(o.couponId(), o.studentId(), orderId);
    if (o.total() > 0) generateInvoice(o, items);
    orders.markFulfilled(orderId);
    dashboard.invalidate(o.studentId());
    if (o.total() > 0) {
      var inv = orders.invoice(orderId).orElse(null);
      String titles = items.stream().map(ItemRow::title).collect(Collectors.joining(", "));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("items", titles);
      data.put("total", o.total());
      data.put("invoiceNumber", inv != null ? inv.number() : "");
      data.put("invoiceUrl", inv != null && inv.storageKey() != null ? storage.signedDownloadUrl(Bucket.INVOICES, inv.storageKey(), 7 * 86_400, inv.number() + ".pdf") : "");
      notifications.notify(NotifyInput.of(o.studentId(), NotificationTypes.ORDER_PAID, orderId.toString(), "পেমেন্ট সফল — কোর্স আনলক হয়েছে", titles)
          .withLink("/my-courses")
          .withEmail(EmailTemplate.ORDER_PAID, data));
    }
  }

  private String nextInvoiceNumber() {
    int year = Year.now(ZoneId.systemDefault()).getValue();
    long n = orders.countInvoicesLike("SS-" + year + "-%");
    return "SS-" + year + "-" + String.format("%06d", n + 1);
  }

  InvoiceRow generateInvoice(OrderRow o, List<ItemRow> items) {
    var exists = orders.invoice(o.id()).orElse(null);
    if (exists != null && exists.storageKey() != null) return exists;
    var u = orders.user(o.studentId()).orElse(null);
    var p = orders.succeededPayment(o.id()).orElse(null);
    double vatPct = integrations.businessNumber("vat_percent", 0);
    long vat = vatPct != 0 ? Math.round((o.total() * vatPct) / (100 + vatPct)) : 0; // VAT-inclusive prices
    List<Map<String, Object>> itemsJson = items.stream().map(i -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("title", i.title());
      m.put("price", i.price());
      m.put("discount", i.discount());
      return m;
    }).toList();
    InvoiceRow inv = exists;
    for (int attempt = 0; inv == null && attempt < 5; attempt++) {
      inv = orders.insertInvoice(o.id(), o.studentId(), nextInvoiceNumber(), u != null ? u.name() : "Student", u != null ? u.phone() : null, u != null ? u.email() : null,
          json.writeValueAsString(itemsJson), o.subtotal(), o.discount(), vat, o.total()).orElse(null);
      if (inv == null) inv = orders.invoice(o.id()).orElse(null);
    }
    if (inv == null) throw new IllegalStateException("invoice number allocation failed");
    String gatewayRef = p == null ? null : p.gatewayRef() != null ? p.gatewayRef() : p.gatewayTrxId();
    byte[] file = pdf.render(new InvoicePdfRenderer.InvoiceData(inv.number(), o.paidAt() != null ? o.paidAt() : Instant.now(), inv.billingName(), inv.billingPhone(),
        inv.billingEmail(), o.id().toString(), items.stream().map(i -> new InvoicePdfRenderer.Item(i.title(), i.price(), i.discount())).toList(), o.subtotal(), o.discount(), vat,
        o.total(), o.paymentMethod(), gatewayRef));
    String key = o.studentId() + "/" + inv.number() + ".pdf";
    storage.put(Bucket.INVOICES, key, file, "application/pdf");
    orders.setInvoiceFile(inv.id(), key);
    return inv;
  }
}
