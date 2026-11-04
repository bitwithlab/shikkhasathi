package com.shikkhasathi.search.controller;

import com.shikkhasathi.search.dto.SearchDtos.SearchQuery;
import com.shikkhasathi.search.dto.SearchDtos.SearchResponse;
import com.shikkhasathi.search.dto.SearchDtos.SuggestQuery;
import com.shikkhasathi.search.dto.SearchDtos.SuggestResponse;
import com.shikkhasathi.search.service.SearchService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.CacheControl;
import com.shikkhasathi.security.RateLimit;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract group {@code discovery}: search + autocomplete. */
@RestController
@RequestMapping("/api/v1")
public class SearchController {
  private final SearchService search;

  public SearchController(SearchService search) {
    this.search = search;
  }

  @GetMapping("/search")
  @Auth(Auth.Level.OPTIONAL)
  @RateLimit(max = 60, window = 60)
  public SearchResponse search(@Valid @ModelAttribute SearchQuery q, Optional<AuthUser> user) {
    return search.search(q, user.map(AuthUser::id).orElse(null));
  }

  @GetMapping("/search/suggest")
  @Auth(Auth.Level.OPTIONAL)
  @RateLimit(max = 120, window = 60)
  @CacheControl("public, max-age=60")
  public SuggestResponse suggest(@Valid @ModelAttribute SuggestQuery q) {
    return search.suggest(q.q());
  }
}
