package com.shikkhasathi.integration.dto;

import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** Request/response records of the {@code settings} contract group. */
public final class IntegrationDtos {
  private IntegrationDtos() {}

  public record IntegrationList(List<IntegrationService.State> items) {}

  /** {@code values}: key → new value; null clears the admin value; "" leaves a secret unchanged. */
  public record UpdateIntegration(@NotNull Map<String, String> values) {}

  public record TestIntegration(String to) {}

  public record Features(boolean bundles, boolean courseRequests, boolean classroom) {}

  /** Public, non-secret client configuration ({@code GET /config}). */
  public record PublicConfig(String googleClientId, String webPushPublicKey, Features features, String paymentGateway) {}
}
