package com.shikkhasathi.health.controller;

import com.shikkhasathi.health.service.HealthService;
import com.shikkhasathi.security.Auth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract group {@code health}. */
@RestController
@RequestMapping("/api/v1")
public class HealthController {
  private final HealthService health;

  public HealthController(HealthService health) {
    this.health = health;
  }

  @GetMapping("/health")
  @Auth(Auth.Level.PUBLIC)
  public HealthService.Health health() {
    return health.health();
  }

  @GetMapping("/admin/system-health")
  @Auth(Auth.Level.ADMIN)
  public HealthService.SystemHealth system() {
    return health.system();
  }
}
