package com.shikkhasathi.payment.controller;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.payment.adapter.PaymentGateways;
import com.shikkhasathi.payment.repository.OrderRepository;
import com.shikkhasathi.payment.service.PaymentService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway browser returns (GET/POST, query and/or form-urlencoded), server-to-server IPN, and the dev-only fake hosted
 * payment page. Validation is always server-side; callback params are never trusted.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentCallbackController {
  private static final Set<String> GATEWAYS = Set.of("sslcommerz", "bkash", "fake");

  private final PaymentService payments;
  private final PaymentGateways gateways;
  private final OrderRepository orders;
  private final AppProperties props;
  private final JsonMapper json;

  public PaymentCallbackController(PaymentService payments, PaymentGateways gateways, OrderRepository orders, AppProperties props, JsonMapper json) {
    this.payments = payments;
    this.gateways = gateways;
    this.orders = orders;
    this.props = props;
    this.json = json;
  }

  /** Query params and form/JSON body merged (body wins); only string values are kept. */
  private Map<String, String> params(HttpServletRequest req) throws IOException {
    // query string: a key given once is a string; a repeated key is an array and is skipped (like before)
    Map<String, List<String>> query = new LinkedHashMap<>();
    if (req.getQueryString() != null)
      for (String kv : req.getQueryString().split("&")) {
        if (kv.isEmpty()) continue;
        int i = kv.indexOf('=');
        String k = java.net.URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
        String v = i < 0 ? "" : java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
        query.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
      }
    Map<String, String> out = new LinkedHashMap<>();
    query.forEach((k, v) -> {
      if (v.size() == 1) out.put(k, v.get(0));
    });
    String ct = req.getContentType();
    if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE)) {
      byte[] raw = req.getInputStream().readNBytes(64 * 1024);
      if (raw.length > 0) {
        JsonNode n;
        try {
          n = json.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
          throw ApiException.badRequest(null);
        }
        if (n != null && n.isObject()) for (var name : n.propertyNames()) if (n.get(name).isString()) out.put(name, n.get(name).asString());
      }
      return out;
    }
    // form body: the servlet merges query values first, then body values; the last body value wins
    for (var e : req.getParameterMap().entrySet()) {
      int fromQuery = query.getOrDefault(e.getKey(), List.of()).size();
      String[] v = e.getValue();
      if (v.length > fromQuery) out.put(e.getKey(), v[v.length - 1]);
    }
    return out;
  }

  @RequestMapping(value = "/payments/callback/{gateway}", method = {RequestMethod.GET, RequestMethod.POST})
  @Auth(Auth.Level.PUBLIC)
  @RateLimit(max = 60, window = 60)
  public void callback(@PathVariable String gateway, HttpServletRequest req, HttpServletResponse res) throws IOException {
    if (!GATEWAYS.contains(gateway)) throw ApiException.notFound();
    String to = payments.handleCallback(gateway, params(req));
    res.setHeader("Cache-Control", "no-store");
    res.setStatus(303);
    res.setHeader("Location", to);
  }

  @PostMapping("/webhooks/payments/{gateway}")
  @Auth(Auth.Level.PUBLIC)
  @RateLimit(max = 300, window = 60)
  public Map<String, Boolean> ipn(@PathVariable String gateway, HttpServletRequest req) throws IOException {
    if (!GATEWAYS.contains(gateway)) throw ApiException.notFound();
    payments.handleIpn(gateway, params(req));
    return Map.of("ok", true);
  }

  private static String esc(String s) {
    var sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') sb.append("&#").append((int) c).append(';');
      else sb.append(c);
    }
    return sb.toString();
  }

  /** Dev-only fake hosted payment page (404 in production). */
  @GetMapping(value = "/payments/fake/{trxId}", produces = "text/html;charset=utf-8")
  @Auth(Auth.Level.PUBLIC)
  public void fakePage(@PathVariable String trxId, HttpServletResponse res) throws IOException {
    if (props.isProd()) throw ApiException.notFound();
    var p = orders.paymentByTrx("fake", trxId).orElseThrow(ApiException::notFound);
    String trx = esc(p.gatewayTrxId());
    var fake = gateways.fake();
    java.util.function.BiFunction<String, String[], String> form = (outcome, labelColor) ->
        "<form method=\"post\" action=\"/api/v1/payments/callback/fake?status=" + ("success".equals(outcome) ? "success" : "fail") + "\"><input type=\"hidden\" name=\"tran_id\" value=\"" + trx
            + "\"><input type=\"hidden\" name=\"amount\" value=\"" + p.amount() + "\"><input type=\"hidden\" name=\"outcome\" value=\"" + outcome
            + "\"><input type=\"hidden\" name=\"sig\" value=\"" + fake.sign(p.gatewayTrxId(), outcome) + "\"><button style=\"background:" + labelColor[1] + "\" type=\"submit\">"
            + labelColor[0] + "</button></form>";
    String amount = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US)).format(p.amount() / 100.0);
    String html = "<!doctype html><html lang=\"bn\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Fake payment · ShikkhaSathi</title>"
        + "<style>body{font-family:system-ui,sans-serif;background:#F8FAFC;color:#0F172A;display:grid;place-items:center;min-height:100vh;margin:0;padding:16px}main{background:#fff;border:1px solid #E2E8F0;border-radius:16px;padding:28px;max-width:380px;width:100%}h1{font-size:18px;margin:0 0 4px}p{color:#475569;margin:0 0 20px;font-size:14px}.amt{font-size:32px;font-weight:700;margin:8px 0 20px;font-variant-numeric:tabular-nums}button{width:100%;border:0;color:#fff;border-radius:12px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;margin-bottom:10px}small{color:#94A3B8}</style>"
        + "</head><body><main><h1>ডেভ পেমেন্ট গেটওয়ে</h1><p>এটি শুধু ডেভেলপমেন্টের জন্য — কোনো আসল টাকা কাটা হবে না। মাধ্যম: " + esc(p.method() == null ? "" : p.method())
        + "</p><div class=\"amt\">৳" + amount + "</div>" + form.apply("success", new String[] {"পেমেন্ট সফল করুন", "#10B981"}) + form.apply("fail", new String[] {"ব্যর্থ করুন", "#EF4444"})
        + "<small>trx: " + trx + "</small></main></body></html>";
    res.setStatus(200);
    res.setContentType("text/html; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    res.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
  }
}
