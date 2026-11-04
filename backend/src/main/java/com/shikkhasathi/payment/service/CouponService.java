package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.payment.dto.CommerceDtos.Coupon;
import com.shikkhasathi.payment.dto.CommerceDtos.CouponCreate;
import com.shikkhasathi.payment.dto.CommerceDtos.CouponPatch;
import com.shikkhasathi.payment.dto.CommerceDtos.CouponValidateResponse;
import com.shikkhasathi.payment.dto.CommerceDtos.Coupons;
import com.shikkhasathi.payment.repository.CatalogRepository;
import com.shikkhasathi.payment.repository.CouponRepository;
import com.shikkhasathi.payment.repository.CouponRepository.CouponRow;
import com.shikkhasathi.payment.repository.CouponRepository.CouponValues;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/** Coupon math at checkout and the seller's coupon CRUD. */
@Service
public class CouponService {
  /** One priced line of an order (a course, or a bundle with its courses). */
  public record Line(UUID courseId, UUID bundleId, UUID sellerId, String title, long price, List<UUID> courseIds) {}

  public record Applied(CouponRow coupon, long discount) {}

  private final CouponRepository coupons;
  private final CatalogRepository catalog;
  private final AuditService audit;

  public CouponService(CouponRepository coupons, CatalogRepository catalog, AuditService audit) {
    this.coupons = coupons;
    this.catalog = catalog;
    this.audit = audit;
  }

  // ---------------------------------------------------------------- pricing

  /** Server-side pricing of what is being bought. */
  public List<Line> linesFor(UUID courseId, UUID bundleId) {
    if (courseId != null) {
      var c = catalog.liveCourse(courseId).orElse(null);
      if (c == null || !"published".equals(c.status())) throw ApiException.notFound("কোর্সটি এখন কেনা যাচ্ছে না।");
      return List.of(new Line(c.id(), null, c.sellerId(), c.title(), c.price(), List.of(c.id())));
    }
    if (bundleId != null) {
      var b = catalog.bundle(bundleId).orElse(null);
      if (b == null || !"published".equals(b.status())) throw ApiException.notFound("বান্ডেলটি পাওয়া যায়নি।");
      return List.of(new Line(null, b.id(), b.sellerId(), b.title(), b.price(), catalog.bundleCourseIds(b.id())));
    }
    throw ApiException.badRequest("কোর্স বা বান্ডেল দিন।");
  }

  /** Discount in paisa, or a friendly Bangla 400. */
  public Applied apply(String code, UUID userId, List<Line> lines) {
    var c = coupons.byCode(code.toUpperCase(Locale.ROOT)).orElse(null);
    Instant now = Instant.now();
    if (c == null || c.active() == 0) throw ApiException.badRequest("কুপনটি সঠিক নয়।");
    if (c.startsAt() != null && c.startsAt().isAfter(now)) throw ApiException.badRequest("কুপনটি এখনো চালু হয়নি।");
    if (c.expiresAt() != null && c.expiresAt().isBefore(now)) throw ApiException.badRequest("কুপনের মেয়াদ শেষ।");
    if (c.maxUses() != null && c.usedCount() >= c.maxUses()) throw ApiException.badRequest("কুপনটি সব ব্যবহার হয়ে গেছে।");
    if (coupons.redemptions(c.id(), userId) >= c.perUserLimit()) throw ApiException.badRequest("আপনি এই কুপন আগেই ব্যবহার করেছেন।");
    var eligible = lines.stream()
        .filter(l -> "platform".equals(c.scope()) || ("seller".equals(c.scope()) && Objects.equals(l.sellerId(), c.sellerId())) || ("course".equals(c.scope()) && l.courseId() != null && l.courseId().equals(c.courseId())))
        .toList();
    if (eligible.isEmpty()) throw ApiException.badRequest("এই কোর্সে কুপনটি প্রযোজ্য নয়।");
    long base = eligible.stream().mapToLong(Line::price).sum();
    if (base < c.minAmount()) throw ApiException.badRequest("কুপনটি কমপক্ষে ৳" + Money.taka(c.minAmount()) + "-এর কেনাকাটায় প্রযোজ্য।");
    long discount = "percent".equals(c.type()) ? Math.round((base * (double) Math.min(100, c.value())) / 100) : Math.min(base, c.value());
    return new Applied(c, discount);
  }

  public CouponValidateResponse validate(UUID userId, String code, UUID courseId, UUID bundleId) {
    var lines = linesFor(courseId, bundleId);
    long subtotal = lines.stream().mapToLong(Line::price).sum();
    try {
      long discount = apply(code, userId, lines).discount();
      return new CouponValidateResponse(true, discount, subtotal - discount, "কুপন প্রযোজ্য — ৳" + Money.taka(discount) + " ছাড়");
    } catch (ApiException e) {
      return new CouponValidateResponse(false, 0, subtotal, e.getMessage());
    }
  }

  // ---------------------------------------------------------------- seller CRUD

  public static Coupon toDto(CouponRow c) {
    return new Coupon(c.id(), c.code(), c.scope(), c.courseId(), c.type(), c.value(), c.maxUses(), c.usedCount(), c.perUserLimit(), c.minAmount(),
        JsonConfig.iso(c.startsAt()), JsonConfig.iso(c.expiresAt()), c.active() != 0);
  }

  static Instant parse(String iso) {
    return iso == null ? null : OffsetDateTime.parse(iso).toInstant();
  }

  private void checkInput(UUID sellerId, String type, Integer value, UUID courseId, String startsAt, String expiresAt) {
    if ("percent".equals(type) && value != null && (value < 1 || value > 90)) throw ApiException.badRequest("শতাংশ ছাড় ১–৯০ এর মধ্যে দিন।");
    if (courseId != null) {
      var c = catalog.course(courseId).orElse(null);
      if (c == null || !c.sellerId().equals(sellerId)) throw ApiException.forbidden("শুধু নিজের কোর্সে কুপন দেওয়া যায়।");
    }
    if (startsAt != null && expiresAt != null && !parse(expiresAt).isAfter(parse(startsAt))) throw ApiException.badRequest("মেয়াদ শেষের তারিখ শুরুর পরে হতে হবে।");
  }

  public Coupons list(UUID sellerId) {
    return new Coupons(coupons.bySeller(sellerId).stream().map(CouponService::toDto).toList());
  }

  public Coupon create(UUID sellerId, CouponCreate d) {
    checkInput(sellerId, d.type(), d.value(), d.courseId(), d.startsAt(), d.expiresAt());
    var v = new CouponValues(d.code(), d.courseId() != null ? "course" : "seller", d.courseId(), d.type(), d.value(), d.maxUses(), d.perUserLimit() == null ? 1 : d.perUserLimit(),
        d.minAmount() == null ? 0 : d.minAmount(), parse(d.startsAt()), parse(d.expiresAt()), Boolean.FALSE.equals(d.active()) ? 0 : 1);
    var row = coupons.insert(sellerId, v).orElseThrow(() -> ApiException.conflict("এই কোডের কুপন আগে থেকেই আছে — অন্য কোড দিন।"));
    audit.log(AuditService.Entry.of(sellerId, "coupon.create", "coupons", row.id()).after(toDto(row)));
    return toDto(row);
  }

  /** {@code present} = body keys that were sent (absent keys are left unchanged; explicit null clears). */
  public Coupon update(UUID sellerId, UUID id, CouponPatch d, Set<String> present) {
    var c = coupons.byId(id).orElseThrow(ApiException::notFound);
    if (!Objects.equals(c.sellerId(), sellerId)) throw ApiException.forbidden();
    checkInput(sellerId, d.type() != null ? d.type() : c.type(), d.value() != null ? d.value() : Integer.valueOf(c.value()), d.courseId(), d.startsAt(), d.expiresAt());
    String code = present.contains("code") && d.code() != null ? d.code() : c.code();
    UUID courseId = present.contains("courseId") ? d.courseId() : c.courseId();
    String scope = present.contains("courseId") ? (d.courseId() != null ? "course" : "seller") : c.scope();
    String type = present.contains("type") && d.type() != null ? d.type() : c.type();
    int value = present.contains("value") && d.value() != null ? d.value() : c.value();
    Integer maxUses = present.contains("maxUses") ? d.maxUses() : c.maxUses();
    int perUser = present.contains("perUserLimit") && d.perUserLimit() != null ? d.perUserLimit() : c.perUserLimit();
    long minAmount = present.contains("minAmount") && d.minAmount() != null ? d.minAmount() : c.minAmount();
    Instant startsAt = present.contains("startsAt") ? parse(d.startsAt()) : c.startsAt();
    Instant expiresAt = present.contains("expiresAt") ? parse(d.expiresAt()) : c.expiresAt();
    int active = present.contains("active") && d.active() != null ? (d.active() ? 1 : 0) : c.active();
    var row = coupons.update(id, new CouponValues(code, scope, courseId, type, value, maxUses, perUser, minAmount, startsAt, expiresAt, active));
    audit.log(AuditService.Entry.of(sellerId, "coupon.update", "coupons", id).before(toDto(c)).after(toDto(row)));
    return toDto(row);
  }
}
