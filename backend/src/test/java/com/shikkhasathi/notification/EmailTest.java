package com.shikkhasathi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.notification.email.*;
import com.shikkhasathi.notification.email.EmailMessage.EmailSender;
import com.shikkhasathi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Port of the previous API's email.test.ts. */
class EmailTest extends IntegrationTest {
  @Autowired EmailService email;
  @Autowired EmailTemplates templates;
  @Autowired FakeEmailProvider fakeEmail;

  @Test
  void brevoApiPostsToSmtpEmailWithApiKeyHeader() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var p = new BrevoApiProvider("xkeysib-1", r -> {
      calls.add(r);
      return HttpTransport.Response.of(201, "{\"messageId\":\"<abc@brevo>\"}");
    }, json);
    String id = p.send(new EmailMessage("a@b.com", null, "S", "<p>h</p>", "t", null, null), new EmailSender("ShikkhaSathi", "no-reply@shikkhasathi.com", "support@shikkhasathi.com"));
    assertThat(id).isEqualTo("<abc@brevo>");
    var req = calls.get(0);
    assertThat(req.url()).isEqualTo("https://api.brevo.com/v3/smtp/email");
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.headerValue("api-key")).isEqualTo("xkeysib-1");
    var payload = json.readTree(req.bodyText());
    assertThat(payload.get("sender").get("name").asString()).isEqualTo("ShikkhaSathi");
    assertThat(payload.get("sender").get("email").asString()).isEqualTo("no-reply@shikkhasathi.com");
    assertThat(payload.get("replyTo").get("email").asString()).isEqualTo("support@shikkhasathi.com");
    assertThat(payload.get("to").get(0).get("email").asString()).isEqualTo("a@b.com");
    assertThat(payload.get("to").get(0).has("name")).isFalse();
    assertThat(payload.get("textContent").asString()).isEqualTo("t");
    assertThat(payload.has("tags")).isFalse();
  }

  @Test
  void brevoApiSurfacesErrors() {
    var p = new BrevoApiProvider("bad", r -> HttpTransport.Response.of(401, "{\"code\":\"unauthorized\"}"), json);
    assertThatThrownBy(() -> p.send(new EmailMessage("a@b.com", null, "S", "h", "t", null, null), new EmailSender("n", "e@x.com", null))).hasMessageContaining("401");
  }

  @Test
  void everyTemplateRendersBnAndEnWithPlainTextAndBranding() {
    Map<String, Object> d = Map.of("name", "Rahim", "code", "123456", "total", 29900, "items", "DS", "invoiceNumber", "SS-2026-000001", "subject", "Hi", "body", "Hello");
    for (EmailTemplate t : EmailTemplate.values()) {
      for (String l : List.of("bn", "en")) {
        var r = templates.render(t, d, l);
        assertThat(r.subject()).as(t + " " + l).isNotEmpty();
        assertThat(r.text().length()).as(t + " " + l).isGreaterThan(10);
        assertThat(r.html()).as(t + " " + l).containsAnyOf("শিক্ষাসাথী", "ShikkhaSathi");
      }
    }
  }

  @Test
  void rendersExactlyLikeThePreviousApi() {
    var r = templates.render(EmailTemplate.ORDER_PAID, Map.of("items", "DS & Algo", "total", 12345678, "invoiceNumber", "SS-2026-000001", "invoiceUrl", "https://x/y.pdf"), "bn");
    assertThat(r.subject()).isEqualTo("পেমেন্ট সফল — ইনভয়েস SS-2026-000001");
    assertThat(r.html()).contains("<strong>DS &amp; Algo</strong> — মোট ৳1,23,456.78 পরিশোধ হয়েছে।");
    assertThat(r.html()).contains("<a href=\"http://localhost:5173/my-courses\"");
    assertThat(r.text()).isEqualTo(String.join("\n",
        "শিক্ষাসাথী", "", "কোর্স আনলক হয়েছে", "",
        "DS &amp; Algo — মোট ৳1,23,456.78 পরিশোধ হয়েছে।", "ইনভয়েস নম্বর: SS-2026-000001",
        "", "শেখা শুরু করুন: http://localhost:5173/my-courses",
        "", "ইনভয়েস ডাউনলোড: PDF",
        "", "—", "আপনার ক্লাস, আপনার কোর্স, আপনার পরীক্ষার প্রস্তুতি — এক জায়গায়।", "কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।"));
    assertThat(templates.render(EmailTemplate.PAYOUT_PAID, Map.of("amount", 50000), "en").subject()).isEqualTo("Payout sent — ৳500");
    assertThat(templates.render(EmailTemplate.REFUND, Map.of("status", "approved", "amount", 29950.0), "en").html()).contains("৳299.5 will be returned");
    var otp = templates.render(EmailTemplate.EMAIL_OTP, Map.of("code", "654321"), "en");
    assertThat(otp.html()).contains("letter-spacing:8px;font-weight:700;color:#4F46E5\">654321</p>");
    assertThat(otp.html()).startsWith("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>Verification code</title></head>\n<body ");
    var b = templates.render(EmailTemplate.ADMIN_BROADCAST, Map.of("subject", "Notice", "body", "One\n\n\nTwo <b>"), "bn");
    assertThat(b.subject()).isEqualTo("Notice");
    assertThat(b.html()).contains(">One</p><p style=\"margin:0 0 12px;font-size:15px;color:#334155\">Two &lt;b&gt;</p>");
  }

  @Test
  void escapesUserDataInTemplates() {
    var r = templates.render(EmailTemplate.WELCOME, Map.of("name", "<script>x</script>"), "bn");
    assertThat(r.html()).doesNotContain("<script>x");
  }

  @Test
  void queuesThroughEmailSendLogsStatusAndNeverStoresTheOtp() {
    email.send("student@example.com", EmailTemplate.EMAIL_OTP, Map.of("code", "654321"));
    var rows = coreJdbc.queryForList("select * from email_logs where to_email = 'student@example.com'", Map.of());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("status")).isEqualTo("sent");
    assertThat(rows.get(0).get("provider")).isEqualTo("fake");
    assertThat(rows.get(0).get("template")).isEqualTo("email_otp");
    assertThat(rows.toString()).doesNotContain("654321");
    assertThat(fakeEmail.last().text()).contains("654321");
    assertThat(fakeEmail.last().from().name()).isEqualTo("ShikkhaSathi");
  }

  @Test
  void failedDeliveryIsLoggedWithErrorAndAttempts() {
    var admin = createUser("Admin", "admin");
    // SMTP fallback only, pointed at a closed local port: the chain has no fake, so delivery fails
    put("/admin/integrations/brevo", Map.of("values", Map.of("smtp_host", "127.0.0.1", "smtp_port", "1", "smtp_login", "u", "smtp_key", "k")), admin).expect(200);
    var id = email.send("x@example.com", EmailTemplate.REFUND, Map.of("status", "approved", "amount", 100));
    var row = coreJdbc.queryForMap("select status, provider, error, attempts from email_logs where id = :id", Map.of("id", id));
    assertThat(row.get("status")).isEqualTo("failed");
    assertThat((String) row.get("error")).startsWith("SMTP:");
    assertThat(row.get("attempts")).isEqualTo(1);
    assertThat(fakeEmail.outbox()).isEmpty();
  }

  @Test
  void adminTestMailWorksWithFakeProviderAndRecordsLastTest() {
    var admin = createUser("Admin", List.of("admin"), "admin@shikkhasathi.com", null);
    var r = contract("settings.test", post("/admin/integrations/brevo/test", Map.of("to", "admin@shikkhasathi.com"), admin));
    assertThat(r.at("/ok").asBoolean()).isTrue();
    assertThat(r.at("/message").asString()).isEqualTo("টেস্ট মেইল পাঠানো হয়েছে (fake) → admin@shikkhasathi.com");
    assertThat(fakeEmail.last().subject()).isEqualTo("শিক্ষাসাথী টেস্ট মেইল");
    var list = contract("settings.list", get("/admin/integrations", admin));
    var brevo = java.util.stream.StreamSupport.stream(list.at("/items").spliterator(), false).filter(i -> i.get("provider").asString().equals("brevo")).findFirst().orElseThrow();
    assertThat(brevo.get("lastTest").get("ok").asBoolean()).isTrue();
    var noTo = contract("settings.test", post("/admin/integrations/brevo/test", Map.of(), admin));
    assertThat(noTo.at("/ok").asBoolean()).isFalse();
  }
}
