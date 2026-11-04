package com.shikkhasathi.payment.adapter;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Gateway lookup from the {@code payment} integration + callback URLs + the "সংযোগ পরীক্ষা করুন" tester. */
@Service
public class PaymentGateways {
  /** Thrown when the named gateway has no credentials (or the fake is requested in production). */
  public static class GatewayNotConfiguredException extends IllegalStateException {
    public GatewayNotConfiguredException(String name) {
      super("payment gateway \"" + name + "\" not configured");
    }
  }

  private final IntegrationService integrations;
  private final FakeGateway fake;
  private final HttpTransport http;
  private final JsonMapper json;
  private final AppProperties props;
  private final PaymentUrls urls;
  /** bKash instances per credential set, so the grant token is reused */
  private final Map<BkashGateway.Config, BkashGateway> bkash = new ConcurrentHashMap<>();

  public PaymentGateways(IntegrationService integrations, FakeGateway fake, HttpTransport http, JsonMapper json, AppProperties props) {
    this.integrations = integrations;
    this.fake = fake;
    this.http = http;
    this.json = json;
    this.props = props;
    this.urls = new PaymentUrls(props.appUrl());
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("payment", (c, opts) -> {
      if ("sslcommerz".equals(c.get("gateway"))) {
        if (blank(c.get("sslcommerz_store_id")) || blank(c.get("sslcommerz_store_password"))) return new IntegrationService.TestResult(false, "SSLCommerz store id ও password দিন");
        var g = new SslcommerzGateway(ssl(c), urls, http, json);
        var r = g.query("connectivity-test", null);
        Object apiConnect = r.raw() instanceof Map<?, ?> m ? m.get("APIConnect") : null;
        return new IntegrationService.TestResult(true, "SSLCommerz API পৌঁছানো গেছে (" + (!"false".equals(c.get("sslcommerz_sandbox")) ? "sandbox" : "live") + ") — " + (apiConnect != null ? apiConnect : "OK"));
      }
      if ("bkash".equals(c.get("gateway"))) {
        new BkashGateway(bk(c), urls, http, json).idToken();
        return new IntegrationService.TestResult(true, "bKash টোকেন পাওয়া গেছে — সংযোগ ঠিক আছে");
      }
      return new IntegrationService.TestResult(true, "Fake গেটওয়ে (ডেভ মোড) — আসল টাকা কাটা হবে না");
    });
  }

  private static SslcommerzGateway.Config ssl(Map<String, String> c) {
    return new SslcommerzGateway.Config(c.get("sslcommerz_store_id"), c.get("sslcommerz_store_password"), !"false".equals(c.get("sslcommerz_sandbox")));
  }

  private static BkashGateway.Config bk(Map<String, String> c) {
    return new BkashGateway.Config(nz(c.get("bkash_app_key")), nz(c.get("bkash_app_secret")), nz(c.get("bkash_username")), nz(c.get("bkash_password")), !"false".equals(c.get("bkash_sandbox")));
  }

  /** Callback / IPN URL builder. */
  public PaymentUrls urls() {
    return urls;
  }

  public String callbackUrl(String gateway, String kind) {
    return urls.callbackUrl(gateway, kind);
  }

  public String ipnUrl(String gateway) {
    return urls.ipnUrl(gateway);
  }

  /** The fake gateway bean (dev/test page + signatures). */
  public FakeGateway fake() {
    return fake;
  }

  /** Gateway by name (callbacks/webhooks name it in the path); throws {@link GatewayNotConfiguredException}. */
  public PaymentGateway gatewayByName(String name) {
    var c = integrations.get("payment");
    if ("sslcommerz".equals(name) && !blank(c.get("sslcommerz_store_id")) && !blank(c.get("sslcommerz_store_password"))) return new SslcommerzGateway(ssl(c), urls, http, json);
    if ("bkash".equals(name) && !blank(c.get("bkash_app_key")) && !blank(c.get("bkash_app_secret")) && !blank(c.get("bkash_username")) && !blank(c.get("bkash_password")))
      return bkash.computeIfAbsent(bk(c), k -> new BkashGateway(k, urls, http, json));
    if ("fake".equals(name) && !props.isProd()) return fake;
    throw new GatewayNotConfiguredException(name);
  }

  /** The gateway selected in Admin (falls back to the fake outside production). */
  public PaymentGateway activeGateway() {
    String g = integrations.get("payment").getOrDefault("gateway", "fake");
    try {
      return gatewayByName(g);
    } catch (GatewayNotConfiguredException e) {
      if (!props.isProd()) return fake;
      throw e;
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isEmpty();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
