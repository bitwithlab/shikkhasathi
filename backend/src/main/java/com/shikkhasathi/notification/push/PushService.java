package com.shikkhasathi.notification.push;

import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Push via the {@code push} integration: FCM when the service-account JSON is set, the fake outside production. */
@Service
public class PushService {
  private static final Logger log = LoggerFactory.getLogger(PushService.class);
  private final IntegrationService integrations;
  private final FakePushProvider fake;
  private final HttpTransport http;
  private final JsonMapper json;
  private final AppProperties props;
  /** FCM provider reused while the service account is unchanged (keeps its cached access token) */
  private volatile String fcmKey;
  private volatile FcmProvider fcm;

  public PushService(IntegrationService integrations, FakePushProvider fake, HttpTransport http, JsonMapper json, AppProperties props) {
    this.integrations = integrations;
    this.fake = fake;
    this.http = http;
    this.json = json;
    this.props = props;
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("push", (c, opts) -> {
      String sa = c.get("fcm_service_account_json");
      if (sa == null || sa.isEmpty()) return new IntegrationService.TestResult(false, "FCM service account JSON দিন");
      var account = FcmProvider.ServiceAccount.parse(sa, json);
      new FcmProvider(account, http, json).accessToken();
      return new IntegrationService.TestResult(true, "FCM সংযোগ ঠিক আছে (" + account.projectId() + ")");
    });
  }

  /** The active provider; empty in production when push is not configured. */
  public Optional<PushProvider> provider() {
    String sa = integrations.get("push").get("fcm_service_account_json");
    if (sa != null && !sa.isEmpty()) {
      String key = Crypto.sha256(sa);
      if (key.equals(fcmKey) && fcm != null) return Optional.of(fcm);
      try {
        FcmProvider p = new FcmProvider(FcmProvider.ServiceAccount.parse(sa, json), http, json);
        fcm = p;
        fcmKey = key;
        return Optional.of(p);
      } catch (IllegalArgumentException e) {
        log.error("invalid FCM service account JSON");
      }
    }
    return props.isProd() ? Optional.empty() : Optional.of(fake);
  }
}
