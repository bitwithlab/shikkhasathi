package com.shikkhasathi.marketplace.controller;

import com.shikkhasathi.marketplace.dto.MarketplaceDtos.Featured;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.FeaturedQuery;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.Items;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.Storefront;
import com.shikkhasathi.marketplace.service.CourseService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.CacheControl;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract group {@code discovery}: course details, storefront, recommendations, landing. */
@RestController
@RequestMapping("/api/v1")
public class CourseController {
  private final CourseService courses;

  public CourseController(CourseService courses) {
    this.courses = courses;
  }

  @GetMapping("/courses/{slug}")
  @Auth(Auth.Level.OPTIONAL)
  public Map<String, Object> course(@PathVariable @Size(min = 1, max = 200) String slug, Optional<AuthUser> user) {
    return courses.courseDetail(slug, user.map(AuthUser::id).orElse(null), user.map(AuthUser::roles).orElse(List.of()));
  }

  @GetMapping("/teachers/{slug}")
  @Auth(Auth.Level.OPTIONAL)
  public Storefront storefront(@PathVariable @Size(min = 1, max = 200) String slug, Optional<AuthUser> user) {
    return courses.storefront(slug, user.map(AuthUser::id).orElse(null));
  }

  @GetMapping("/recommendations")
  @Auth(Auth.Level.USER)
  public Items recommendations(AuthUser user) {
    return courses.recommendations(user.id(), 8);
  }

  @GetMapping("/featured")
  @Auth(Auth.Level.PUBLIC)
  @CacheControl("public, max-age=300")
  public Featured featured(@ModelAttribute FeaturedQuery q) {
    return courses.featured(q.institutionId());
  }
}
