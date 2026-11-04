package com.shikkhasathi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of the previous API's settings.test.ts + the public config endpoint. */
class SettingsTest extends IntegrationTest {
  private static Map<String, Object> values(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return Map.of("values", m);
  }

  @Test
  void requiresAdmin() {
    var student = createUser("Student");
    get("/admin/integrations", student).expect(403);
    get("/admin/integrations").expect(401);
  }

  @Test
  void storesSecretsEncryptedNeverReturnsThemAndAuditLogs() {
    var admin = createUser("Admin", "student", "admin");
    var put = contract("settings.update", put("/admin/integrations/brevo", values("api_key", "xkeysib-very-secret-9876", "sender_email", "no-reply@shikkhasathi.com"), admin));
    assertThat(put.body()).doesNotContain("very-secret");
    var apiKey = java.util.stream.StreamSupport.stream(put.at("/fields").spliterator(), false).filter(f -> f.get("key").asString().equals("api_key")).findFirst().orElseThrow();
    assertThat(apiKey.get("value").asString()).isEqualTo("••••9876");
    assertThat(put.at("/mode").asString()).isEqualTo("configured");

    var row = coreJdbc.queryForMap("select value_encrypted, is_secret from integration_settings where key = 'api_key'", Map.of());
    assertThat((String) row.get("value_encrypted")).doesNotContain("very-secret");
    assertThat(row.get("is_secret")).isEqualTo(true);
    assertThat(integrations.get("brevo").get("api_key")).isEqualTo("xkeysib-very-secret-9876");

    // empty secret = unchanged (write-only)
    put("/admin/integrations/brevo", values("api_key", ""), admin).expect(200);
    assertThat(integrations.get("brevo").get("api_key")).isEqualTo("xkeysib-very-secret-9876");

    var logs = coreJdbc.queryForList("select coalesce(before_json::text, '') || coalesce(after_json::text, '') as j from audit_logs where action = 'integration.update'", Map.of(), String.class);
    assertThat(logs).hasSize(2);
    assertThat(String.join("", logs)).doesNotContain("very-secret");

    var list = contract("settings.list", get("/admin/integrations", admin));
    assertThat(list.body()).doesNotContain("very-secret");
  }

  @Test
  void fallsBackToEnvThenDefaultAndNullClearsAdminValue() {
    var admin = createUser("Admin", "admin");
    try {
      integrations.setEnvOverride("BREVO_SENDER_NAME", "From Env");
      assertThat(integrations.get("brevo").get("sender_name")).isEqualTo("From Env");
      put("/admin/integrations/brevo", values("sender_name", "Admin Name"), admin).expect(200);
      assertThat(integrations.get("brevo").get("sender_name")).isEqualTo("Admin Name");
      put("/admin/integrations/brevo", values("sender_name", null), admin).expect(200);
      assertThat(integrations.get("brevo").get("sender_name")).isEqualTo("From Env");
    } finally {
      integrations.setEnvOverride("BREVO_SENDER_NAME", null);
    }
    var list = contract("settings.list", get("/admin/integrations", admin));
    var brevo = java.util.stream.StreamSupport.stream(list.at("/items").spliterator(), false).filter(i -> i.get("provider").asString().equals("brevo")).findFirst().orElseThrow();
    var name = java.util.stream.StreamSupport.stream(brevo.get("fields").spliterator(), false).filter(f -> f.get("key").asString().equals("sender_name")).findFirst().orElseThrow();
    assertThat(name.get("source").asString()).isEqualTo("default");
    assertThat(brevo.get("mode").asString()).isEqualTo("fake");
  }

  @Test
  void validatesFieldTypes() {
    var admin = createUser("Admin", "admin");
    put("/admin/integrations/business", values("commission_percent", "abc"), admin).expect(400);
    put("/admin/integrations/payment", values("gateway", "paypal"), admin).expect(400);
    put("/admin/integrations/nope", values("x", "y"), admin).expect(400);
    put("/admin/integrations/brevo", Map.of(), admin).expect(422);
  }

  @Test
  void testEndpointRecordsResultAndRejectsStudents() {
    var admin = createUser("Admin", "admin");
    var r = contract("settings.test", post("/admin/integrations/payment/test", Map.of(), admin));
    assertThat(r.at("/ok").asBoolean()).isTrue();
    var sms = contract("settings.test", post("/admin/integrations/sms/test", Map.of("to", "+8801700000000"), admin));
    assertThat(sms.at("/ok").asBoolean()).isFalse();
    assertThat(sms.at("/message").asString()).isEqualTo("SMS API URL ও key দিন");
    post("/admin/integrations/payment/test", Map.of(), createUser("S")).expect(403);
  }

  @Test
  void publicConfig() {
    var r = contract("settings.publicConfig", get("/config"));
    assertThat(r.at("/googleClientId").isNull()).isTrue();
    assertThat(r.at("/paymentGateway").asString()).isEqualTo("fake");
    assertThat(r.at("/features/classroom").asBoolean()).isTrue();
    var admin = createUser("Admin", "admin");
    put("/admin/integrations/business", values("feature_bundles", "false"), admin).expect(200);
    put("/admin/integrations/google", values("client_id", "abc.apps.googleusercontent.com"), admin).expect(200);
    r = contract("settings.publicConfig", get("/config"));
    assertThat(r.at("/features/bundles").asBoolean()).isFalse();
    assertThat(r.at("/googleClientId").asString()).isEqualTo("abc.apps.googleusercontent.com");
  }
}
