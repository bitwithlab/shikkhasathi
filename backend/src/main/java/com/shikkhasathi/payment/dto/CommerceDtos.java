package com.shikkhasathi.payment.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Request/response records of the {@code commerce}, {@code sellerMoney} and {@code adminCommerce} contract groups. */
public final class CommerceDtos {
  private CommerceDtos() {}

  public static final String ISO_DATETIME = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})$";
  public static final String COUPON_CODE_MSG = "ইংরেজি অক্ষর, সংখ্যা, - বা _ (৩–৩০)";

  static String upper(String s) {
    return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
  }

  static String trim(String s) {
    return s == null ? null : s.trim();
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  // ---------------------------------------------------------------- orders

  public record OrderItem(UUID id, UUID courseId, UUID bundleId, String title, long price, long discount, String coverColor, String slug) {}

  public record RefundInfo(String status, long amount, String reason) {}

  public record Order(UUID id, String status, List<OrderItem> items, long subtotal, long discount, long total, String couponCode, String paymentMethod, String createdAt,
                      String paidAt, String invoiceNumber, RefundInfo refund, boolean fulfilled, boolean refundEligible, String firstLessonPath) {}

  public record AdminOrder(@JsonUnwrapped Order order, String studentName, String studentPhone, String gateway, String gatewayTrxId) {}

  public record CreateOrderBody(UUID courseId, UUID bundleId, @Size(max = 40) String couponCode, @NotNull @Size(min = 8, max = 64) String idempotencyKey) {
    public CreateOrderBody {
      couponCode = upper(couponCode);
    }
  }

  public record PayBody(@NotNull @Pattern(regexp = "bkash|nagad|rocket|card") String method, @Size(max = 20) String phone) {}

  public record PayResponse(String redirectUrl, String gateway) {}

  public record InvoiceUrl(String url, String number) {}

  public record RefundRequestBody(@NotNull @Size(min = 10, max = 1000) String reason) {
    public RefundRequestBody {
      reason = trim(reason);
    }
  }

  public record CouponValidateBody(@NotNull @Size(min = 3, max = 40) String code, UUID courseId, UUID bundleId) {
    public CouponValidateBody {
      code = upper(code);
    }
  }

  public record CouponValidateResponse(boolean valid, long discount, long total, String message) {}

  public record BundleCourse(UUID id, String slug, String title, long price) {}

  public record Bundle(UUID id, String slug, String title, String description, long price, Long compareAtPrice, List<BundleCourse> courses) {}

  // ---------------------------------------------------------------- seller money

  public record Coupon(UUID id, String code, String scope, UUID courseId, String type, int value, Integer maxUses, int usedCount, int perUserLimit, long minAmount,
                       String startsAt, String expiresAt, boolean active) {}

  public record Coupons(List<Coupon> items) {}

  public record CouponCreate(@NotNull @Pattern(regexp = "^[A-Z0-9_-]{3,30}$", message = COUPON_CODE_MSG) String code, UUID courseId,
                             @NotNull @Pattern(regexp = "percent|fixed") String type, @NotNull @Positive Integer value, @Positive Integer maxUses, @Positive Integer perUserLimit,
                             @PositiveOrZero Long minAmount, @Pattern(regexp = ISO_DATETIME) String startsAt, @Pattern(regexp = ISO_DATETIME) String expiresAt, Boolean active) {
    public CouponCreate {
      code = upper(code);
    }
  }

  /** PATCH body: every field optional; presence is tracked separately by the controller. */
  public record CouponPatch(@Pattern(regexp = "^[A-Z0-9_-]{3,30}$", message = COUPON_CODE_MSG) String code, UUID courseId, @Pattern(regexp = "percent|fixed") String type,
                            @Positive Integer value, @Positive Integer maxUses, @Positive Integer perUserLimit, @PositiveOrZero Long minAmount,
                            @Pattern(regexp = ISO_DATETIME) String startsAt, @Pattern(regexp = ISO_DATETIME) String expiresAt, Boolean active) {
    public CouponPatch {
      code = upper(code);
    }
  }

  public record LedgerEntry(UUID id, String type, long amount, String description, String availableAt, long balanceAfter, String createdAt) {}

  public record Balance(long available, long held, long lifetime, long paidOut, long pendingPayouts, long minPayout) {}

  public record LedgerPage(List<LedgerEntry> items, String nextCursor, Balance balance) {}

  public record SellerBundle(UUID id, String slug, String title, long price, Long compareAtPrice, int courseCount) {}

  public record SellerBundles(List<SellerBundle> items) {}

  public record CreateBundleBody(@NotNull @Size(min = 5, max = 160) String title, @Size(max = 1000) String description,
                                 @NotNull @Size(min = 2, max = 10) List<@NotNull UUID> courseIds, @NotNull @PositiveOrZero Long price) {
    public CreateBundleBody {
      title = trim(title);
    }
  }

  public record Created(UUID id, String slug) {}

  // ---------------------------------------------------------------- admin

  public record AdminRefund(UUID id, UUID orderId, String studentName, long amount, String reason, String status, String createdAt, int watchedPercent,
                            long hoursSincePurchase, List<String> items, String note) {}

  public record DecideRefundBody(@NotNull @Pattern(regexp = "approve|reject") String decision, @Size(max = 500) String note, @Size(max = 100) String manualReference) {}
}
