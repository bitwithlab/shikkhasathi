package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Ids;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.payment.adapter.PaymentGateway;
import com.shikkhasathi.payment.adapter.PaymentGateways;
import com.shikkhasathi.payment.dto.CommerceDtos.*;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.repository.OrderRepository.ItemRow;
import com.shikkhasathi.payment.repository.OrderRepository.OrderRow;
import com.shikkhasathi.payment.repository.OrderRepository.PaymentRow;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Server-priced orders, starting payment, order history, invoices and the admin order list. */
@Service
public class OrderService {
  private final OrderRepository orders;
  private final CouponService coupons;
  private final OrderViews views;
  private final FulfilmentService fulfilment;
  private final PaymentGateways gateways;
  private final StorageService storage;

  public OrderService(OrderRepository orders, CouponService coupons, OrderViews views, FulfilmentService fulfilment, PaymentGateways gateways, StorageService storage) {
    this.orders = orders;
    this.coupons = coupons;
    this.views = views;
    this.fulfilment = fulfilment;
    this.gateways = gateways;
    this.storage = storage;
  }

  /** The caller's own order (404 / 403). */
  public OrderRow ownOrder(UUID userId, UUID id) {
    var o = orders.order(id).orElseThrow(ApiException::notFound);
    if (!o.studentId().equals(userId)) throw ApiException.forbidden();
    return o;
  }

  public Order get(UUID userId, UUID id) {
    return views.toOrder(ownOrder(userId, id));
  }

  public Page<Order> myOrders(UUID userId, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = orders.myOrders(userId, cur == null ? null : Instant.parse(cur.sortString()), cur == null ? null : cur.id(), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(views.toOrders(pg.items()), pg.nextCursor());
  }

  public Order create(UUID userId, CreateOrderBody b) {
    var existing = orders.orderByIdempotencyKey(userId, b.idempotencyKey());
    if (existing.isPresent()) return views.toOrder(existing.get());
    var lines = coupons.linesFor(b.courseId(), b.bundleId());
    List<UUID> allCourses = lines.stream().flatMap(l -> l.courseIds().stream()).toList();
    var owned = orders.activeEnrolledCourses(userId, allCourses);
    if (b.courseId() != null && !owned.isEmpty()) throw ApiException.conflict("এই কোর্সটি আপনার কাছে আগেই আছে।");
    if (b.bundleId() != null && owned.size() == allCourses.size()) throw ApiException.conflict("বান্ডেলের সব কোর্স আপনার কাছে আগেই আছে।");
    boolean ownCourse = lines.stream().anyMatch(l -> !l.courseIds().isEmpty() && l.sellerId().equals(userId));
    if (ownCourse) throw ApiException.badRequest("নিজের কোর্স কেনা যায় না।");
    long subtotal = lines.stream().mapToLong(CouponService.Line::price).sum();
    var applied = b.couponCode() != null && !b.couponCode().isEmpty() ?coupons.apply(b.couponCode(), userId, lines) : null;
    long discount = applied != null ? applied.discount() : 0;
    long total = Math.max(0, subtotal - discount);
    var id = orders.insertOrder(userId, subtotal, discount, total, applied != null ? applied.coupon().id() : null, b.idempotencyKey()).orElse(null);
    if (id == null) return views.toOrder(orders.orderByIdempotencyKey(userId, b.idempotencyKey()).orElseThrow());
    // allocate the discount proportionally per line (for the seller ledger)
    long left = discount;
    for (int i = 0; i < lines.size(); i++) {
      var l = lines.get(i);
      long share = i == lines.size() - 1 ? left : Math.round((discount * (double) l.price()) / Math.max(1, subtotal));
      left -= share;
      orders.insertItem(id, l.courseId(), l.bundleId(), l.sellerId(), l.title(), l.price(), share);
    }
    if (total == 0) {
      orders.markFreePaid(id);
      fulfilment.fulfil(id);
    }
    return views.toOrder(orders.order(id).orElseThrow());
  }

  public PayResponse pay(UUID userId, UUID orderId, String method) {
    var o = ownOrder(userId, orderId);
    if (!"pending".equals(o.status()) && !"failed".equals(o.status())) throw ApiException.conflict("এই অর্ডারের পেমেন্ট আগেই হয়েছে।");
    if (o.total() == 0) throw ApiException.badRequest("এই অর্ডারে পেমেন্ট লাগবে না।");
    var u = orders.user(userId).orElse(null);
    var items = orders.items(orderId);
    var gw = gateways.activeGateway();
    String uuid = Ids.uuidv7().toString();
    String trxId = "SS" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT) + uuid.substring(uuid.length() - 6).toUpperCase(Locale.ROOT);
    var r = gw.init(new PaymentGateway.InitInput(orderId.toString(), trxId, o.total(), method,
        new PaymentGateway.Customer(u != null ? u.name() : "Student", u != null ? u.email() : null, u != null ? u.phone() : null),
        items.stream().map(ItemRow::title).collect(Collectors.joining(", "))));
    orders.insertPayment(orderId, gw.name(), trxId, r.sessionId(), method, o.total());
    orders.setPending(orderId, method);
    return new PayResponse(r.redirectUrl(), gw.name());
  }

  public InvoiceUrl invoiceUrl(UUID userId, UUID orderId, boolean isAdmin) {
    var o = orders.order(orderId).orElseThrow(ApiException::notFound);
    if (!o.studentId().equals(userId) && !isAdmin) throw ApiException.forbidden();
    var inv = orders.invoice(orderId).orElse(null);
    if (inv == null || inv.storageKey() == null) throw ApiException.notFound("ইনভয়েস এখনো তৈরি হয়নি।");
    return new InvoiceUrl(storage.signedDownloadUrl(Bucket.INVOICES, inv.storageKey(), 600, inv.number() + ".pdf"), inv.number());
  }

  public Page<AdminOrder> adminOrders(String status, String q, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    boolean search = q != null && !q.isEmpty();
    List<UUID> studentIds = search ? orders.usersMatching(q, 50) : List.of();
    List<UUID> byTrx = search ? orders.ordersByTrxLike(q, 50) : List.of();
    var rows = orders.adminOrders(status, studentIds, byTrx, search, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    var people = orders.users(pg.items().stream().map(OrderRow::studentId).toList());
    Map<UUID, PaymentRow> latest = new HashMap<>();
    for (var p : orders.paymentsFor(pg.items().stream().map(OrderRow::id).toList())) latest.putIfAbsent(p.orderId(), p);
    var views = this.views.toOrders(pg.items());
    List<AdminOrder> items = new ArrayList<>();
    for (int i = 0; i < pg.items().size(); i++) {
      var o = pg.items().get(i);
      var u = people.get(o.studentId());
      var pay = latest.get(o.id());
      items.add(new AdminOrder(views.get(i), u != null ? u.name() : "", u != null ? u.phone() : null, pay != null ? pay.gateway() : null, pay != null ? pay.gatewayTrxId() : null));
    }
    return new Page<>(items, pg.nextCursor());
  }
}
