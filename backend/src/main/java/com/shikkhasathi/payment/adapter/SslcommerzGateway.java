package com.shikkhasathi.payment.adapter;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** SSLCommerz v4 hosted page (bKash, Nagad, Rocket and cards) + validation / transaction-query / refund APIs. */
public class SslcommerzGateway implements PaymentGateway {
  public record Config(String storeId, String storePassword, boolean sandbox) {}

  private final Config cfg;
  private final PaymentUrls urls;
  private final HttpTransport http;
  private final JsonMapper json;
  private final String base;

  public SslcommerzGateway(Config cfg, PaymentUrls urls, HttpTransport http, JsonMapper json) {
    this.cfg = cfg;
    this.urls = urls;
    this.http = http;
    this.json = json;
    this.base = cfg.sandbox() ? "https://sandbox.sslcommerz.com" : "https://securepay.sslcommerz.com";
  }

  @Override
  public String name() {
    return "sslcommerz";
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMap(HttpTransport.Response res) {
    try {
      return json.readValue(res.text(), LinkedHashMap.class);
    } catch (RuntimeException e) {
      throw new IllegalStateException("SSLCommerz: invalid response " + res.status());
    }
  }

  @Override
  public InitResult init(InitInput i) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("store_id", cfg.storeId());
    f.put("store_passwd", cfg.storePassword());
    f.put("total_amount", PaymentGateway.taka(i.amount()));
    f.put("currency", "BDT");
    f.put("tran_id", i.trxId());
    f.put("success_url", urls.callbackUrl("sslcommerz", "success"));
    f.put("fail_url", urls.callbackUrl("sslcommerz", "fail"));
    f.put("cancel_url", urls.callbackUrl("sslcommerz", "cancel"));
    f.put("ipn_url", urls.ipnUrl("sslcommerz"));
    f.put("cus_name", i.customer().name());
    f.put("cus_email", i.customer().email() != null ? i.customer().email() : "no-reply@shikkhasathi.com");
    f.put("cus_phone", i.customer().phone() != null ? i.customer().phone() : "01700000000");
    f.put("cus_add1", "Bangladesh");
    f.put("cus_city", "Dhaka");
    f.put("cus_country", "Bangladesh");
    f.put("shipping_method", "NO");
    f.put("product_name", i.productName().length() > 250 ? i.productName().substring(0, 250) : i.productName());
    f.put("product_category", "Education");
    f.put("product_profile", "non-physical-goods");
    f.put("value_a", i.orderId());
    // pre-select the wallet the student chose
    f.put("multi_card_name", "card".equals(i.method()) ? "visacard,mastercard,amexcard" : "rocket".equals(i.method()) ? "dbbl_mobile" : i.method());
    var res = http.send(Request.post(base + "/gwprocess/v4/api.php", HttpTransport.form(f)).header("content-type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(20)));
    var d = readMap(res);
    Object url = d.get("GatewayPageURL");
    if (!"SUCCESS".equals(d.get("status")) || url == null || String.valueOf(url).isEmpty())
      throw new IllegalStateException("SSLCommerz init: " + (d.get("failedreason") != null ? d.get("failedreason") : d.get("status")));
    return new InitResult(String.valueOf(url), d.get("sessionkey") == null ? null : String.valueOf(d.get("sessionkey")));
  }

  private ValidateResult map(Map<String, Object> d) {
    String st = String.valueOf(d.getOrDefault("status", "")).toUpperCase();
    boolean ok = st.equals("VALID") || st.equals("VALIDATED");
    Object ref = d.get("bank_tran_id");
    return new ValidateResult(ok, ok ? "succeeded" : st.equals("PENDING") ? "pending" : "failed", String.valueOf(d.getOrDefault("tran_id", "")), PaymentGateway.toPaisa(d.get("amount")),
        ref == null || String.valueOf(ref).isEmpty() ? null : String.valueOf(ref), d);
  }

  @Override
  public ValidateResult validate(Map<String, String> params) {
    if (params.get("val_id") == null || params.get("val_id").isEmpty()) return new ValidateResult(false, "failed", params.getOrDefault("tran_id", ""), 0, null, params);
    Map<String, String> q = new LinkedHashMap<>();
    q.put("val_id", params.get("val_id"));
    q.put("store_id", cfg.storeId());
    q.put("store_passwd", cfg.storePassword());
    q.put("format", "json");
    return map(readMap(http.send(Request.get(HttpTransport.withQuery(base + "/validator/api/validationserverAPI.php", q)).timeout(Duration.ofSeconds(20)))));
  }

  @Override
  @SuppressWarnings("unchecked")
  public ValidateResult query(String trxId, String sessionId) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("tran_id", trxId);
    q.put("store_id", cfg.storeId());
    q.put("store_passwd", cfg.storePassword());
    q.put("format", "json");
    var data = readMap(http.send(Request.get(HttpTransport.withQuery(base + "/validator/api/merchantTransIDvalidationAPI.php", q)).timeout(Duration.ofSeconds(20))));
    List<Map<String, Object>> els = data.get("element") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    var el = els.stream().filter(e -> List.of("VALID", "VALIDATED").contains(String.valueOf(e.get("status")))).findFirst().orElse(els.isEmpty() ? null : els.get(0));
    return el != null ? map(el) : new ValidateResult(false, "pending", trxId, 0, null, data);
  }

  @Override
  public RefundResult refund(RefundRequest p) {
    if (p.gatewayRef() == null) return new RefundResult(false, null);
    Map<String, String> q = new LinkedHashMap<>();
    q.put("bank_tran_id", p.gatewayRef());
    q.put("refund_amount", PaymentGateway.taka(p.amount()));
    q.put("refund_remarks", p.reason().length() > 200 ? p.reason().substring(0, 200) : p.reason());
    q.put("store_id", cfg.storeId());
    q.put("store_passwd", cfg.storePassword());
    q.put("format", "json");
    var d = readMap(http.send(Request.get(HttpTransport.withQuery(base + "/validator/api/merchantTransIDvalidationAPI.php", q)).timeout(Duration.ofSeconds(20))));
    if (!"DONE".equals(d.get("APIConnect")) || !List.of("success", "processing").contains(String.valueOf(d.get("status"))))
      throw new IllegalStateException("SSLCommerz refund: " + (d.get("status") != null ? d.get("status") : d.get("APIConnect")));
    return new RefundResult(true, d.get("refund_ref_id") == null ? null : String.valueOf(d.get("refund_ref_id")));
  }
}
