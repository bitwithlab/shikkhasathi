package com.shikkhasathi.student.controller;

import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.student.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Home dashboard aggregate (contract group {@code dashboard}). {@code ?fresh=1} bypasses the 60 s cache. */
@RestController
@RequestMapping("/api/v1")
@Auth(Auth.Level.USER)
public class DashboardController {
  private final DashboardService svc;

  public DashboardController(DashboardService svc) {
    this.svc = svc;
  }

  @GetMapping("/dashboard")
  public Object dashboard(AuthUser user, @RequestParam(required = false) String fresh) {
    return svc.getDashboard(user.id(), "1".equals(fresh));
  }
}
