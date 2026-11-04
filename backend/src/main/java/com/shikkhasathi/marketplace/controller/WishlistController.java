package com.shikkhasathi.marketplace.controller;

import com.shikkhasathi.marketplace.dto.MarketplaceDtos.Items;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.Ok;
import com.shikkhasathi.marketplace.service.CourseService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract group {@code wishlist}. */
@RestController
@RequestMapping("/api/v1")
public class WishlistController {
  private final CourseService courses;

  public WishlistController(CourseService courses) {
    this.courses = courses;
  }

  @PostMapping("/wishlist/{courseId}")
  @Auth(Auth.Level.USER)
  public Ok add(@PathVariable UUID courseId, AuthUser user) {
    courses.addWishlist(user.id(), courseId);
    return Ok.TRUE;
  }

  @DeleteMapping("/wishlist/{courseId}")
  @Auth(Auth.Level.USER)
  public Ok remove(@PathVariable UUID courseId, AuthUser user) {
    courses.removeWishlist(user.id(), courseId);
    return Ok.TRUE;
  }

  @GetMapping("/me/wishlist")
  @Auth(Auth.Level.USER)
  public Items list(AuthUser user) {
    return courses.listWishlist(user.id());
  }
}
