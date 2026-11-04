package com.shikkhasathi.notification.sms;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** SMS via the {@code sms} integration: HTTP gateway when configured, the fake outside production, else none. */
@Service
public class SmsService {
  private final IntegrationService integrations;
  private final FakeSmsProvider fake;
  private final HttpTransport http;
  private final AppProperties props;

  public SmsService(IntegrationService integrations, FakeSmsProvider fake, HttpTransport http, AppProperties props) {
    this.integrations = integrations;
    this.fake = fake;
    this.http = http;
    this.props = props;
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("sms", (c, opts) -> {
      if (!configured(c)) return new IntegrationService.TestResult(false, "SMS API URL ও key দিন");
      if (opts.to() == null || opts.to().isEmpty()) return new IntegrationService.TestResult(false, "টেস্ট SMS-এর জন্য নম্বর দিন");
      http(c).send(opts.to(), "ShikkhaSathi test SMS ✓");
      return new IntegrationService.TestResult(true, "টেস্ট SMS পাঠানো হয়েছে → " + opts.to());
    });
  }

  private static boolean configured(Map<String, String> c) {
    return "http".equals(c.get("provider")) && notBlank(c.get("api_url")) && notBlank(c.get("api_key"));
  }

  private HttpSmsProvider http(Map<String, String> c) {
    return new HttpSmsProvider(new HttpSmsProvider.Config(c.get("api_url"), c.get("api_key"), c.get("sender_id")), http);
  }

  /** The active provider; empty in production when SMS is not configured. */
  public Optional<SmsProvider> provider() {
    var c = integrations.get("sms");
    if (configured(c)) return Optional.of(http(c));
    return props.isProd() ? Optional.empty() : Optional.of(fake);
  }

  /** True when messages go to the fake outbox (dev/test: OTP may be shown as a dev hint). */
  public boolean isFake() {
    return provider().map(p -> "fake".equals(p.name())).orElse(false);
  }

  /** Send through the active provider; 503 INTEGRATION_DISABLED when none is configured. */
  public void send(String to, String message) {
    provider().orElseThrow(() -> new ApiException(ErrorCode.INTEGRATION_DISABLED)).send(to, message);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isEmpty();
  }
}
