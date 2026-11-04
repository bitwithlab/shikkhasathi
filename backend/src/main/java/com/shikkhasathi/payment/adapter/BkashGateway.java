package com.shikkhasathi.payment.adapter;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** bKash tokenized checkout (v1.2.0-beta): grant token (cached), create, execute, status, refund. */
public class BkashGateway implements PaymentGateway {
  public record Config(String appKey, String appSecret, String username, String password, boolean sandbox) {}

  private final Config cfg;
  private final PaymentUrls urls;
  private final HttpTransport http;
  private final JsonMapper json;
  private final String base;
  private String token;
  private long tokenExp;

  public BkashGateway(Config cfg, PaymentUrls urls, HttpTransport http, JsonMapper json) {
    this.cfg = cfg;
    this.urls = urls;
    this.http = http;
    this.json = json;
    this.base = cfg.sandbox() ? "https://tokenized.sandbox.bka.sh/v1.2.0-beta/tokenized/checkout" : "https://tokenized.pay.bka.sh/v1.2.0-beta/tokenized/checkout";
  }

  @Override
  public String name() {
    return "bkash";
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> read(HttpTransport.Response res) {
    Map<String, Object> m;
    try {
      m = json.readValue(res.text(), LinkedHashMap.class);
    } catch (RuntimeException e) {
      m = Map.of();
    }
    Map<String, String> out = new LinkedHashMap<>();
    m.forEach((k, v) -> out.put(k, v == null ? null : String.valueOf(v)));
    return out;
  }

  /** id_token for API calls (cached until a minute before expiry). Also the "test connection" check. */
  public synchronized String idToken() {
    if (token != null && tokenExp > System.currentTimeMillis() + 60_000) return token;
    var res = http.send(Request.post(base + "/token/grant", json.writeValueAsString(Map.of("app_key", cfg.appKey(), "app_secret", cfg.appSecret())))
        .header("content-type", "application/json").header("accept", "application/json").header("username", cfg.username()).header("password", cfg.password()));
    var d = read(res);
    if (d.get("id_token") == null) throw new IllegalStateException("bKash token: " + (d.get("statusMessage") != null ? d.get("statusMessage") : res.status()));
    token = d.get("id_token");
    long exp = 3600;
    try {
      if (d.get("expires_in") != null) exp = (long) Double.parseDouble(d.get("expires_in"));
    } catch (NumberFormatException ignored) {
      // default
    }
    tokenExp = System.currentTimeMillis() + exp * 1000;
    return token;
  }

  private Map<String, String> call(String path, Map<String, Object> body) {
    return read(http.send(Request.post(base + path, json.writeValueAsString(body))
        .header("content-type", "application/json").header("accept", "application/json").header("authorization", idToken()).header("x-app-key", cfg.appKey()).timeout(Duration.ofSeconds(20))));
  }

  @Override
  public InitResult init(InitInput i) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("mode", "0011");
    b.put("payerReference", i.customer().phone() != null ? i.customer().phone() : i.orderId());
    b.put("callbackURL", urls.callbackUrl("bkash", "success"));
    b.put("amount", PaymentGateway.taka(i.amount()));
    b.put("currency", "BDT");
    b.put("intent", "sale");
    b.put("merchantInvoiceNumber", i.trxId());
    var d = call("/create", b);
    if (d.get("bkashURL") == null) throw new IllegalStateException("bKash create: " + (d.get("statusMessage") != null ? d.get("statusMessage") : "failed"));
    return new InitResult(d.get("bkashURL"), d.get("paymentID"));
  }

  private ValidateResult map(Map<String, String> d, String trxId) {
    boolean ok = "Completed".equals(d.get("transactionStatus"));
    return new ValidateResult(ok, ok ? "succeeded" : "Initiated".equals(d.get("transactionStatus")) ? "pending" : "failed",
        d.get("merchantInvoiceNumber") != null ? d.get("merchantInvoiceNumber") : trxId, PaymentGateway.toPaisa(d.get("amount")), d.get("trxID"), d);
  }

  @Override
  public ValidateResult validate(Map<String, String> params) {
    if (!"success".equals(params.get("status")) || params.get("paymentID") == null || params.get("paymentID").isEmpty()) return new ValidateResult(false, "failed", "", 0, null, params);
    var d = call("/execute", Map.of("paymentID", params.get("paymentID")));
    if (d.get("transactionStatus") != null) return map(d, "");
    return map(call("/payment/status", Map.of("paymentID", params.get("paymentID"))), "");
  }

  @Override
  public ValidateResult query(String trxId, String sessionId) {
    if (sessionId == null || sessionId.isEmpty()) return new ValidateResult(false, "pending", trxId, 0, null, null);
    return map(call("/payment/status", Map.of("paymentID", sessionId)), trxId);
  }

  @Override
  public RefundResult refund(RefundRequest p) {
    if (p.gatewayRef() == null) return new RefundResult(false, null);
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("paymentID", p.trxId());
    b.put("trxID", p.gatewayRef());
    b.put("amount", PaymentGateway.taka(p.amount()));
    b.put("reason", p.reason().length() > 200 ? p.reason().substring(0, 200) : p.reason());
    b.put("sku", "course");
    var d = call("/payment/refund", b);
    if (d.get("refundTrxID") == null) throw new IllegalStateException("bKash refund: " + (d.get("statusMessage") != null ? d.get("statusMessage") : "failed"));
    return new RefundResult(true, d.get("refundTrxID"));
  }
}
