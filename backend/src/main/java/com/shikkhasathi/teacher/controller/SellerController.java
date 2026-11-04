package com.shikkhasathi.teacher.controller;

import com.shikkhasathi.course.service.RequestBodies;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import com.shikkhasathi.teacher.dto.SellerDtos;
import com.shikkhasathi.teacher.dto.SellerDtos.*;
import com.shikkhasathi.teacher.service.SellerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Teacher application & verification (contract groups {@code sellerApply} and {@code adminSellers}). */
@RestController
@RequestMapping("/api/v1")
public class SellerController {
  private final SellerService svc;
  private final RequestBodies bodies;

  public SellerController(SellerService svc, RequestBodies bodies) {
    this.svc = svc;
    this.bodies = bodies;
  }

  @GetMapping("/seller/profile")
  @Auth(Auth.Level.USER)
  public ProfileEnvelope profile(AuthUser user) {
    return svc.getProfile(user.id());
  }

  @PostMapping("/seller/apply")
  @Auth(Auth.Level.USER)
  @RateLimit(max = 10, window = 3600)
  public SellerProfileDto apply(AuthUser user, @RequestBody JsonNode raw) {
    return svc.apply(user.id(), bodies.read(raw, ApplyBody.class, ApplyBody.NULLABLE, ApplyBody.REQUIRED));
  }

  @PostMapping("/seller/verify-email/request")
  @Auth(Auth.Level.USER)
  public Ok emailCode(AuthUser user, @Valid @RequestBody EmailCodeBody body) {
    svc.requestUniversityEmailCode(user.id(), body.email());
    return Ok.OK;
  }

  @PostMapping("/seller/verify-email/confirm")
  @Auth(Auth.Level.USER)
  public SellerProfileDto emailConfirm(AuthUser user, @Valid @RequestBody EmailConfirmBody body) {
    return svc.confirmUniversityEmail(user.id(), body.email(), body.code());
  }

  @PatchMapping("/seller/storefront")
  @Auth(Auth.Level.SELLER)
  public SellerProfileDto updateStorefront(AuthUser user, @RequestBody JsonNode raw) {
    return svc.updateStorefront(user.id(), bodies.read(raw, StorefrontPatch.class, StorefrontPatch.NULLABLE), raw);
  }

  // ---------------------------------------------------------------- admin

  @GetMapping("/admin/sellers")
  @Auth(Auth.Level.ADMIN)
  public AdminSellerPage adminList(@RequestParam(defaultValue = "pending") @Pattern(regexp = SellerDtos.STATUSES) String status,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return svc.adminListSellers(status, cursor, limit);
  }

  @PatchMapping("/admin/sellers/{id}/verification")
  @Auth(Auth.Level.ADMIN)
  public AdminSellerItem adminDecide(AuthUser user, @PathVariable UUID id, @Valid @RequestBody SellerDecision body) {
    return svc.adminDecideSeller(user.id(), id, body);
  }
}
