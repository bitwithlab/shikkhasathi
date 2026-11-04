package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.payment.adapter.PaymentGateway;
import com.shikkhasathi.payment.adapter.PaymentGateways;
import com.shikkhasathi.payment.dto.CommerceDtos.AdminRefund;
import com.shikkhasathi.payment.dto.CommerceDtos.DecideRefundBody;
import com.shikkhasathi.payment.dto.CommerceDtos.Order;
import com.shikkhasathi.payment.dto.CommerceDtos.Page;
import com.shikkhasathi.payment.repository.CatalogRepository;
import com.shikkhasathi.payment.repository.LedgerRepository;
import com.shikkhasathi.payment.repository.LedgerRepository.NewEntry;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.repository.OrderRepository.OrderRow;
import com.shikkhasathi.payment.repository.OrderRepository.RefundRow;
import com.shikkhasathi.student.service.DashboardCache;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Refund requests (student), the admin queue and decisions (gateway or manual refund + ledger reversal). */
@Service
public class RefundService {
  private final OrderRepository orders;
  private final CatalogRepository catalog;
  private final LedgerRepository ledgerRepo;
  private final LedgerService ledger;
  private final OrderService orderService;
  private final OrderViews views;
  private final RefundPolicy policy;
  private final PaymentGateways gateways;
  private final AuditService audit;
  private final NotificationService notifications;
  private final DashboardCache dashboard;

  public RefundService(OrderRepository orders, CatalogRepository catalog, LedgerRepository ledgerRepo, LedgerService ledger, OrderService orderService, OrderViews views,
                       RefundPolicy policy, PaymentGateways gateways, AuditService audit, NotificationService notifications, DashboardCache dashboard) {
    this.orders = orders;
    this.catalog = catalog;
    this.ledgerRepo = ledgerRepo;
    this.ledger = ledger;
    this.orderService = orderService;
    this.views = views;
    this.policy = policy;
    this.gateways = gateways;
    this.audit = audit;
    this.notifications = notifications;
    this.dashboard = dashboard;
  }

  public Order request(UUID userId, UUID orderId, String reason) {
    var o = orderService.ownOrder(userId, orderId);
    if (!"paid".equals(o.status()) || o.total() == 0) throw ApiException.badRequest("এই অর্ডারে রিফান্ড প্রযোজ্য নয়।");
    var chk = policy.check(o);
    if (!chk.ok()) throw ApiException.badRequest("রিফান্ড দেওয়া যাচ্ছে না — " + chk.reason());
    var r = orders.insertRefund(orderId, userId, reason, o.total()).orElseThrow(() -> ApiException.conflict("এই অর্ডারের রিফান্ড অনুরোধ আগেই করা হয়েছে।"));
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("orderId", orderId);
    after.put("amount", o.total());
    audit.log(AuditService.Entry.of(userId, "refund.request", "refunds", r.id()).after(after));
    return views.toOrder(o);
  }

  private List<AdminRefund> toAdmin(List<RefundRow> rows) {
    if (rows.isEmpty()) return List.of();
    Map<UUID, OrderRow> byId = new HashMap<>();
    for (var o : orders.orders(rows.stream().map(RefundRow::orderId).toList())) byId.put(o.id(), o);
    var people = orders.users(rows.stream().map(RefundRow::requestedBy).toList());
    Map<UUID, List<String>> titles = new HashMap<>();
    for (var it : orders.itemsFor(rows.stream().map(RefundRow::orderId).toList())) titles.computeIfAbsent(it.orderId(), k -> new ArrayList<>()).add(it.title());
    var pcts = policy.watchedPercent(byId.values());
    return rows.stream().map(r -> {
      var o = byId.get(r.orderId());
      var u = people.get(r.requestedBy());
      long hours = o != null && o.paidAt() != null ? Math.round((r.createdAt().toEpochMilli() - o.paidAt().toEpochMilli()) / (double) RefundPolicy.HOUR_MS) : 0;
      return new AdminRefund(r.id(), r.orderId(), u != null ? u.name() : "", r.amount(), r.reason(), r.status(), JsonConfig.iso(r.createdAt()),
          o != null ? pcts.getOrDefault(o.id(), 0) : 0, hours, titles.getOrDefault(r.orderId(), List.of()), r.note());
    }).toList();
  }

  public Page<AdminRefund> adminList(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = orders.refundsByStatus(status, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(toAdmin(pg.items()), pg.nextCursor());
  }

  private AdminRefund reload(UUID id) {
    return toAdmin(List.of(orders.refund(id).orElseThrow())).get(0);
  }

  public AdminRefund decide(UUID actorId, UUID id, DecideRefundBody d) {
    var r = orders.refund(id).orElseThrow(ApiException::notFound);
    if (!"requested".equals(r.status())) throw ApiException.conflict("এই রিফান্ডের সিদ্ধান্ত আগেই হয়েছে।");
    var o = orders.order(r.orderId()).orElseThrow(ApiException::notFound);
    if ("reject".equals(d.decision())) {
      orders.decideRefund(id, "rejected", actorId, d.note(), null, false);
      Map<String, Object> after = new LinkedHashMap<>();
      if (d.note() != null) after.put("note", d.note());
      audit.log(AuditService.Entry.of(actorId, "refund.reject", "refunds", id).after(after));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("status", "rejected");
      data.put("orderId", o.id().toString());
      data.put("note", d.note() != null ? d.note() : "");
      notifications.notify(NotifyInput.of(o.studentId(), NotificationTypes.ORDER_REFUNDED, "rejected:" + id, "রিফান্ড অনুরোধ গ্রহণ হয়নি", d.note() != null ? d.note() : "")
          .withLink("/profile#orders").withEmail(EmailTemplate.REFUND, data));
      return reload(id);
    }
    // approve: gateway refund where supported, else manual with reference
    var p = orders.succeededPayment(o.id()).orElse(null);
    String gatewayRefundId = d.manualReference();
    if (p != null && d.manualReference() == null) {
      var gw = gateways.gatewayByName(p.gateway());
      var res = gw.refund(new PaymentGateway.RefundRequest(p.gatewayTrxId(), p.gatewayRef(), r.amount(), r.reason()));
      if (!res.supported()) throw ApiException.badRequest("এই গেটওয়েতে স্বয়ংক্রিয় রিফান্ড নেই — ম্যানুয়ালি টাকা ফেরত দিয়ে রেফারেন্স লিখুন।");
      gatewayRefundId = res.refundId();
    }
    orders.decideRefund(id, "processed", actorId, d.note(), gatewayRefundId, true);
    orders.setStatus(o.id(), "refunded");
    orders.refundPayments(o.id());
    var enr = orders.refundEnrollments(o.id());
    if (!enr.isEmpty()) catalog.decrementEnrollments(enr);
    // ledger reversal: undo sale and commission per item
    for (var e : ledgerRepo.saleEntries(o.id()))
      ledger.append(e.sellerId(), new NewEntry("refund", -e.amount(), o.id(), e.orderItemId(), null, id, "Refund reversal (" + e.type() + ")", Instant.now(), "refund:" + id + ":" + e.id()));
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("status", "processed");
    after.put("amount", r.amount());
    after.put("gatewayRefundId", gatewayRefundId);
    audit.log(AuditService.Entry.of(actorId, "refund.approve", "refunds", id).before(Map.of("status", "requested")).after(after));
    dashboard.invalidate(o.studentId());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", "approved");
    data.put("amount", r.amount());
    data.put("orderId", o.id().toString());
    notifications.notify(NotifyInput.of(o.studentId(), NotificationTypes.ORDER_REFUNDED, id.toString(), "রিফান্ড অনুমোদিত হয়েছে", "৳" + Money.taka(r.amount()) + " ফেরত যাচ্ছে (৩–৭ কার্যদিবস)")
        .withLink("/profile#orders").withEmail(EmailTemplate.REFUND, data));
    return reload(id);
  }
}
