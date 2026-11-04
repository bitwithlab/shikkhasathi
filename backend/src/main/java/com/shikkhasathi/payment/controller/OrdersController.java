package com.shikkhasathi.payment.controller;

import com.shikkhasathi.payment.dto.CommerceDtos.*;
import com.shikkhasathi.payment.service.BundleService;
import com.shikkhasathi.payment.service.CouponService;
import com.shikkhasathi.payment.service.OrderService;
import com.shikkhasathi.payment.service.RefundService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.CacheControl;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Checkout (contract group {@code commerce}) and the admin order/refund screens ({@code adminCommerce}). */
@RestController
@RequestMapping("/api/v1")
public class OrdersController {
  static final String ORDER_STATUS_RE = "pending|paid|failed|cancelled|refunded|partially_refunded";
  static final String REFUND_STATUS_RE = "requested|approved|rejected|processed";

  private final OrderService orders;
  private final RefundService refunds;
  private final CouponService coupons;
  private final BundleService bundles;

  public OrdersController(OrderService orders, RefundService refunds, CouponService coupons, BundleService bundles) {
    this.orders = orders;
    this.refunds = refunds;
    this.coupons = coupons;
    this.bundles = bundles;
  }

  @PostMapping("/orders")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 30, window = 3600)
  @ResponseStatus(HttpStatus.CREATED)
  public Order create(AuthUser user, @Valid @RequestBody CreateOrderBody body) {
    return orders.create(user.id(), body);
  }

  @GetMapping("/orders/{id}")
  @Auth(Auth.Level.USER)
  public Order get(AuthUser user, @PathVariable UUID id) {
    return orders.get(user.id(), id);
  }

  @PostMapping("/orders/{id}/pay")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 10, window = 3600)
  public PayResponse pay(AuthUser user, @PathVariable UUID id, @Valid @RequestBody PayBody body) {
    return orders.pay(user.id(), id, body.method());
  }

  @GetMapping("/me/orders")
  @Auth(Auth.Level.USER)
  public Page<Order> myOrders(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return orders.myOrders(user.id(), cursor, limit);
  }

  @GetMapping("/orders/{id}/invoice")
  @Auth(Auth.Level.USER)
  public InvoiceUrl invoice(AuthUser user, @PathVariable UUID id) {
    return orders.invoiceUrl(user.id(), id, user.isAdmin());
  }

  @PostMapping("/orders/{id}/refund-request")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 5, window = 3600)
  public Order refundRequest(AuthUser user, @PathVariable UUID id, @Valid @RequestBody RefundRequestBody body) {
    return refunds.request(user.id(), id, body.reason());
  }

  @PostMapping("/coupons/validate")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 30, window = 600)
  public CouponValidateResponse validateCoupon(AuthUser user, @Valid @RequestBody CouponValidateBody body) {
    return coupons.validate(user.id(), body.code(), body.courseId(), body.bundleId());
  }

  @GetMapping("/bundles/{slug}")
  @Auth(Auth.Level.OPTIONAL)
  @CacheControl("public, max-age=120")
  public Bundle bundle(@PathVariable String slug) {
    return bundles.get(slug);
  }

  // ---------------------------------------------------------------- admin

  @GetMapping("/admin/orders")
  @Auth(Auth.Level.ADMIN)
  public Page<AdminOrder> adminOrders(@RequestParam(required = false) @Pattern(regexp = ORDER_STATUS_RE) String status, @RequestParam(required = false) @Size(max = 80) String q,
                                      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return orders.adminOrders(status, q, cursor, limit);
  }

  @GetMapping("/admin/refunds")
  @Auth(Auth.Level.ADMIN)
  public Page<AdminRefund> adminRefunds(@RequestParam(defaultValue = "requested") @Pattern(regexp = REFUND_STATUS_RE) String status, @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return refunds.adminList(status, cursor, limit);
  }

  @PatchMapping("/admin/refunds/{id}")
  @Auth(Auth.Level.ADMIN)
  public AdminRefund decideRefund(AuthUser user, @PathVariable UUID id, @Valid @RequestBody DecideRefundBody body) {
    return refunds.decide(user.id(), id, body);
  }
}
