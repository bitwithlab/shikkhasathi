package com.shikkhasathi.payment.adapter;

import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Dev/test gateway: a local hosted page ({@code GET /api/v1/payments/fake/:trxId}, owned by the payments module)
 * where you choose success or failure. The page posts back {@code tran_id, outcome, amount, sig} with
 * {@code sig = sign(trxId, outcome)} — same scheme as the previous API, so pending fake payments keep working.
 */
@Component
public class FakeGateway implements PaymentGateway, ResettableFake {
  /** Outcome recorded when the fake page is submitted. */
  public record Result(String outcome, long amount) {}

  /** results by trx id (set when the fake page is submitted; read by {@link #query}) */
  public static final Map<String, Result> RESULTS = new ConcurrentHashMap<>();

  private final String secret;
  private final String appUrl;

  @org.springframework.beans.factory.annotation.Autowired
  public FakeGateway(AppProperties props) {
    this(props.jwtAccessSecret(), props.appUrl());
  }

  public FakeGateway(String secret, String appUrl) {
    this.secret = secret;
    this.appUrl = appUrl;
  }

  /** {@code sha256("<JWT_ACCESS_SECRET>:<trxId>:<outcome>")} hex, first 24 chars. */
  public static String sign(String secret, String trxId, String outcome) {
    return Crypto.sha256(secret + ":" + trxId + ":" + outcome).substring(0, 24);
  }

  /** Signature for the fake page's form. */
  public String sign(String trxId, String outcome) {
    return sign(secret, trxId, outcome);
  }

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public InitResult init(InitInput i) {
    return new InitResult(appUrl + "/api/v1/payments/fake/" + i.trxId() + "?amount=" + i.amount() + "&method=" + i.method(), "fake-session-" + i.trxId());
  }

  private static long amount(String v) {
    if (v == null || v.isEmpty()) return 0;
    try {
      return Math.round(Double.parseDouble(v));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String ref(String trxId) {
    return "FAKE-" + trxId.substring(Math.max(0, trxId.length() - 8));
  }

  @Override
  public ValidateResult validate(Map<String, String> params) {
    String trxId = params.getOrDefault("tran_id", "");
    String outcome = "success".equals(params.get("outcome")) ? "success" : "fail";
    boolean sigOk = params.get("sig") != null && Crypto.safeEqual(params.get("sig"), sign(trxId, outcome));
    boolean ok = sigOk && outcome.equals("success");
    long amount = amount(params.get("amount"));
    if (sigOk) RESULTS.put(trxId, new Result(outcome, amount));
    Map<String, String> raw = new LinkedHashMap<>(params);
    raw.remove("sig");
    return new ValidateResult(ok, ok ? "succeeded" : "failed", trxId, amount, ok ? ref(trxId) : null, raw);
  }

  @Override
  public ValidateResult query(String trxId, String sessionId) {
    Result r = RESULTS.get(trxId);
    if (r == null) return new ValidateResult(false, "pending", trxId, 0, null, null);
    boolean ok = r.outcome().equals("success");
    return new ValidateResult(ok, ok ? "succeeded" : "failed", trxId, r.amount(), ref(trxId), r);
  }

  @Override
  public RefundResult refund(RefundRequest p) {
    return new RefundResult(true, "FAKE-REFUND-" + p.trxId().substring(Math.max(0, p.trxId().length() - 6)));
  }

  @Override
  public void reset() {
    RESULTS.clear();
  }
}
