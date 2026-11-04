package com.shikkhasathi.payment.controller;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.payment.dto.CommerceDtos.*;
import com.shikkhasathi.payment.service.BundleService;
import com.shikkhasathi.payment.service.CouponService;
import com.shikkhasathi.payment.service.LedgerService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Teacher Studio money: coupons, bundles, earnings ledger (contract group {@code sellerMoney}). */
@RestController
@RequestMapping("/api/v1/seller")
public class SellerMoneyController {
  /** keys of the coupon PATCH body that are not nullable */
  private static final Set<String> NOT_NULL = Set.of("code", "type", "value", "perUserLimit", "minAmount", "active");

  private final CouponService coupons;
  private final BundleService bundles;
  private final LedgerService ledger;
  private final JsonMapper json;
  private final Validator validator;

  public SellerMoneyController(CouponService coupons, BundleService bundles, LedgerService ledger, JsonMapper json, Validator validator) {
    this.coupons = coupons;
    this.bundles = bundles;
    this.ledger = ledger;
    this.json = json;
    this.validator = validator;
  }

  @GetMapping("/coupons")
  @Auth(Auth.Level.SELLER)
  public Coupons coupons(AuthUser user) {
    return coupons.list(user.id());
  }

  @PostMapping("/coupons")
  @Auth(Auth.Level.VERIFIED_SELLER)
  @ResponseStatus(HttpStatus.CREATED)
  public Coupon createCoupon(AuthUser user, @Valid @RequestBody CouponCreate body) {
    return coupons.create(user.id(), body);
  }

  /** PATCH: absent keys stay unchanged, explicit {@code null} clears a nullable field. */
  @PatchMapping("/coupons/{id}")
  @Auth(Auth.Level.SELLER)
  public Coupon updateCoupon(AuthUser user, @PathVariable UUID id, @RequestBody JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.validation(null, List.of(Map.of("path", "body", "message", "Invalid input: expected object")));
    Set<String> present = new HashSet<>(body.propertyNames());
    List<Map<String, String>> details = new ArrayList<>();
    for (String k : NOT_NULL) if (body.has(k) && body.get(k).isNull()) details.add(Map.of("path", "body." + k, "message", "Invalid input"));
    CouponPatch patch;
    try {
      patch = json.treeToValue(body, CouponPatch.class);
    } catch (RuntimeException e) {
      throw ApiException.validation(null, List.of(Map.of("path", "request", "message", String.valueOf(e.getMessage()))));
    }
    for (var v : validator.validate(patch)) details.add(Map.of("path", "body." + v.getPropertyPath(), "message", v.getMessage()));
    if (!details.isEmpty()) throw ApiException.validation(details.get(0).get("message"), details);
    return coupons.update(user.id(), id, patch, present);
  }

  @GetMapping("/ledger")
  @Auth(Auth.Level.SELLER)
  public LedgerPage ledger(AuthUser user, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return ledger.ledger(user.id(), cursor, limit);
  }

  @GetMapping("/bundles")
  @Auth(Auth.Level.SELLER)
  public SellerBundles bundles(AuthUser user) {
    return bundles.list(user.id());
  }

  @PostMapping("/bundles")
  @Auth(Auth.Level.VERIFIED_SELLER)
  @ResponseStatus(HttpStatus.CREATED)
  public Created createBundle(AuthUser user, @Valid @RequestBody CreateBundleBody body) {
    return bundles.create(user.id(), body);
  }
}
