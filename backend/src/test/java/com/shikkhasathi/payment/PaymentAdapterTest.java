package com.shikkhasathi.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.payment.adapter.*;
import com.shikkhasathi.payment.adapter.PaymentGateway.Customer;
import com.shikkhasathi.payment.adapter.PaymentGateway.InitInput;
import com.shikkhasathi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentAdapterTest extends IntegrationTest {
  @Autowired PaymentGateways gateways;
  @Autowired FakeGateway fakeGateway;

  private static final InitInput INIT = new InitInput("order-1", "trx-001", 129900, "bkash", new Customer("রাকিব", null, "+8801712345678"), "ডাটা স্ট্রাকচার");

  @Test
  void fakeGatewaySignatureIsCompatibleWithThePreviousApi() {
    // sha256("<JWT_ACCESS_SECRET>:trx-001:success")[:24] computed independently
    assertThat(fakeGateway.sign("trx-001", "success")).isEqualTo("fb28a7c787cba137fbc14b92");
    var init = fakeGateway.init(INIT);
    assertThat(init.redirectUrl()).isEqualTo("http://localhost:5173/api/v1/payments/fake/trx-001?amount=129900&method=bkash");
    assertThat(init.sessionId()).isEqualTo("fake-session-trx-001");

    assertThat(fakeGateway.query("trx-001", null).status()).isEqualTo("pending");
    var bad = fakeGateway.validate(Map.of("tran_id", "trx-001", "outcome", "success", "sig", "nope", "amount", "129900"));
    assertThat(bad.ok()).isFalse();
    assertThat(FakeGateway.RESULTS).isEmpty();
    var ok = fakeGateway.validate(Map.of("tran_id", "trx-001", "outcome", "success", "sig", fakeGateway.sign("trx-001", "success"), "amount", "129900"));
    assertThat(ok.ok()).isTrue();
    assertThat(ok.status()).isEqualTo("succeeded");
    assertThat(ok.amount()).isEqualTo(129900);
    assertThat(ok.gatewayRef()).isEqualTo("FAKE-trx-001");
    assertThat(((Map<?, ?>) ok.raw()).containsKey("sig")).isFalse();
    assertThat(fakeGateway.query("trx-001", null).ok()).isTrue();
    var fail = fakeGateway.validate(Map.of("tran_id", "trx-002", "outcome", "fail", "sig", fakeGateway.sign("trx-002", "fail")));
    assertThat(fail.status()).isEqualTo("failed");
    assertThat(fakeGateway.query("trx-002", null).status()).isEqualTo("failed");
    assertThat(fakeGateway.refund(new PaymentGateway.RefundRequest("trx-001", null, 100, "x")).refundId()).isEqualTo("FAKE-REFUND-rx-001");
  }

  @Test
  void gatewayLookupFallsBackToFakeOutsideProduction() {
    assertThat(gateways.activeGateway().name()).isEqualTo("fake");
    assertThatThrownBy(() -> gateways.gatewayByName("sslcommerz")).isInstanceOf(PaymentGateways.GatewayNotConfiguredException.class).hasMessage("payment gateway \"sslcommerz\" not configured");
    assertThat(gateways.callbackUrl("bkash", "success")).isEqualTo("http://localhost:5173/api/v1/payments/callback/bkash?status=success");
    assertThat(gateways.ipnUrl("sslcommerz")).isEqualTo("http://localhost:5173/api/v1/webhooks/payments/sslcommerz");

    var admin = createUser("Admin", "admin");
    Map<String, Object> v = new HashMap<>(Map.of("gateway", "sslcommerz", "sslcommerz_store_id", "store1", "sslcommerz_store_password", "pw"));
    put("/admin/integrations/payment", Map.of("values", v), admin).expect(200);
    assertThat(gateways.activeGateway().name()).isEqualTo("sslcommerz");
    put("/admin/integrations/payment", Map.of("values", Map.of("gateway", "bkash")), admin).expect(200);
    assertThat(gateways.activeGateway().name()).isEqualTo("fake"); // bKash credentials missing → fake in dev
  }

  @Test
  void sslcommerzInitValidateQuery() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var g = new SslcommerzGateway(new SslcommerzGateway.Config("store1", "pw", true), new PaymentUrls("https://shikkhasathi.com"), r -> {
      calls.add(r);
      if (r.url().contains("gwprocess")) return HttpTransport.Response.of(200, "{\"status\":\"SUCCESS\",\"GatewayPageURL\":\"https://sandbox.sslcommerz.com/pay/abc\",\"sessionkey\":\"S1\"}");
      if (r.url().contains("validationserverAPI")) return HttpTransport.Response.of(200, "{\"status\":\"VALID\",\"tran_id\":\"trx-001\",\"amount\":\"1299.00\",\"bank_tran_id\":\"BT1\"}");
      return HttpTransport.Response.of(200, "{\"APIConnect\":\"DONE\",\"element\":[{\"status\":\"FAILED\",\"tran_id\":\"trx-001\",\"amount\":\"1299.00\"},{\"status\":\"VALIDATED\",\"tran_id\":\"trx-001\",\"amount\":\"1299.00\",\"bank_tran_id\":\"BT1\"}]}");
    }, json);
    var init = g.init(INIT);
    assertThat(init.redirectUrl()).isEqualTo("https://sandbox.sslcommerz.com/pay/abc");
    assertThat(init.sessionId()).isEqualTo("S1");
    String form = calls.get(0).bodyText();
    assertThat(calls.get(0).url()).isEqualTo("https://sandbox.sslcommerz.com/gwprocess/v4/api.php");
    assertThat(form).contains("total_amount=1299.00").contains("tran_id=trx-001").contains("multi_card_name=bkash").contains("cus_email=no-reply%40shikkhasathi.com")
        .contains("success_url=https%3A%2F%2Fshikkhasathi.com%2Fapi%2Fv1%2Fpayments%2Fcallback%2Fsslcommerz%3Fstatus%3Dsuccess").contains("value_a=order-1");
    var v = g.validate(Map.of("val_id", "V1", "tran_id", "trx-001"));
    assertThat(v.ok()).isTrue();
    assertThat(v.amount()).isEqualTo(129900);
    assertThat(v.gatewayRef()).isEqualTo("BT1");
    assertThat(calls.get(1).url()).startsWith("https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php?val_id=V1&store_id=store1");
    assertThat(g.validate(Map.of("tran_id", "trx-001")).status()).isEqualTo("failed");
    assertThat(g.query("trx-001", null).status()).isEqualTo("succeeded");
  }

  @Test
  void bkashCachesTheGrantTokenAndMapsStatuses() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var g = new BkashGateway(new BkashGateway.Config("ak", "as", "user", "pass", true), new PaymentUrls("https://shikkhasathi.com"), r -> {
      calls.add(r);
      if (r.url().endsWith("/token/grant")) return HttpTransport.Response.of(200, "{\"id_token\":\"IDT\",\"expires_in\":3600}");
      if (r.url().endsWith("/create")) return HttpTransport.Response.of(200, "{\"bkashURL\":\"https://sandbox.bka.sh/x\",\"paymentID\":\"P1\"}");
      if (r.url().endsWith("/execute")) return HttpTransport.Response.of(200, "{\"transactionStatus\":\"Completed\",\"merchantInvoiceNumber\":\"trx-001\",\"amount\":\"1299\",\"trxID\":\"BK1\"}");
      return HttpTransport.Response.of(200, "{\"transactionStatus\":\"Initiated\"}");
    }, json);
    var init = g.init(INIT);
    assertThat(init.sessionId()).isEqualTo("P1");
    assertThat(json.readTree(calls.get(1).bodyText()).get("amount").asString()).isEqualTo("1299.00");
    assertThat(calls.get(1).headerValue("authorization")).isEqualTo("IDT");
    var v = g.validate(Map.of("status", "success", "paymentID", "P1"));
    assertThat(v.ok()).isTrue();
    assertThat(v.trxId()).isEqualTo("trx-001");
    assertThat(v.gatewayRef()).isEqualTo("BK1");
    assertThat(g.query("trx-001", "P1").status()).isEqualTo("pending");
    assertThat(g.validate(Map.of("status", "cancel", "paymentID", "P1")).status()).isEqualTo("failed");
    assertThat(calls.stream().filter(c -> c.url().endsWith("/token/grant")).count()).isEqualTo(1);
  }

  @Test
  void testerReportsTheFakeGateway() {
    var r = contract("settings.test", post("/admin/integrations/payment/test", Map.of(), createUser("Admin", "admin")));
    assertThat(r.at("/ok").asBoolean()).isTrue();
    assertThat(r.at("/message").asString()).isEqualTo("Fake গেটওয়ে (ডেভ মোড) — আসল টাকা কাটা হবে না");
  }
}
