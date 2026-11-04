package com.shikkhasathi.integration.controller;

import com.shikkhasathi.integration.dto.IntegrationDtos.*;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Admin → সেটিংস → Integrations, plus the public client config (contract group {@code settings}). */
@RestController
@RequestMapping("/api/v1")
public class SettingsController {
  private final IntegrationService integrations;

  public SettingsController(IntegrationService integrations) {
    this.integrations = integrations;
  }

  @GetMapping("/admin/integrations")
  @Auth(Auth.Level.ADMIN)
  public IntegrationList list() {
    return new IntegrationList(integrations.all());
  }

  @PutMapping("/admin/integrations/{provider}")
  @Auth(Auth.Level.ADMIN)
  public IntegrationService.State update(@PathVariable String provider, @Valid @RequestBody UpdateIntegration body, AuthUser user, HttpServletRequest req) {
    return integrations.update(provider, body.values(), user.id(), req.getRemoteAddr());
  }

  @PostMapping("/admin/integrations/{provider}/test")
  @Auth(Auth.Level.ADMIN)
  @RateLimit(max = 10, window = 60)
  public IntegrationService.TestResult test(@PathVariable String provider, @RequestBody(required = false) TestIntegration body, AuthUser user) {
    integrations.catalog().get(provider); // 400 for an unknown provider
    return integrations.test(provider, body == null ? null : body.to(), user.id());
  }

  @GetMapping("/config")
  @Auth(Auth.Level.PUBLIC)
  public PublicConfig publicConfig() {
    var google = integrations.get("google");
    var push = integrations.get("push");
    var payment = integrations.get("payment");
    return new PublicConfig(
        google.get("client_id"),
        push.get("web_push_public_key"),
        new Features(integrations.businessFlag("feature_bundles", true), integrations.businessFlag("feature_course_requests", true), integrations.businessFlag("feature_classroom", true)),
        payment.getOrDefault("gateway", "fake"));
  }
}
