package com.shikkhasathi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.notification.push.FcmProvider;
import com.shikkhasathi.notification.push.PushProvider;
import com.shikkhasathi.notification.push.PushService;
import com.shikkhasathi.notification.sms.FakeSmsProvider;
import com.shikkhasathi.notification.sms.HttpSmsProvider;
import com.shikkhasathi.notification.sms.SmsService;
import com.shikkhasathi.support.IntegrationTest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SMS + push adapters. */
class ProviderAdapterTest extends IntegrationTest {
  @Autowired SmsService sms;
  @Autowired FakeSmsProvider fakeSms;
  @Autowired PushService push;

  @Test
  void smsFakeOutsideProductionAndHttpGatewayQuery() {
    assertThat(sms.isFake()).isTrue();
    sms.send("+8801712345678", "কোড 123456");
    assertThat(fakeSms.last()).isEqualTo(new FakeSmsProvider.Sent("+8801712345678", "কোড 123456"));

    List<HttpTransport.Request> calls = new ArrayList<>();
    new HttpSmsProvider(new HttpSmsProvider.Config("https://bulksmsbd.net/api/smsapi", "KEY", "8809617"), r -> {
      calls.add(r);
      return HttpTransport.Response.of(200, "{}");
    }).send("+8801712345678", "hi there");
    assertThat(calls.get(0).url()).isEqualTo("https://bulksmsbd.net/api/smsapi?api_key=KEY&number=8801712345678&message=hi+there&senderid=8809617");

    var admin = createUser("Admin", "admin");
    put("/admin/integrations/sms", Map.of("values", Map.of("provider", "http", "api_url", "https://bulksmsbd.net/api/smsapi", "api_key", "KEY")), admin).expect(200);
    assertThat(sms.isFake()).isFalse();
    assertThat(sms.provider().orElseThrow().name()).isEqualTo("http");
  }

  @Test
  void fcmSignsAnRs256AssertionCachesTheTokenAndReportsInvalidTokens() throws Exception {
    var kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    var kp = kpg.generateKeyPair();
    String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
    String saJson = json.writeValueAsString(Map.of("type", "service_account", "project_id", "shikkhasathi-app", "client_email", "fcm@shikkhasathi-app.iam.gserviceaccount.com", "private_key", pem));
    List<HttpTransport.Request> calls = new ArrayList<>();
    var p = new FcmProvider(FcmProvider.ServiceAccount.parse(saJson, json), r -> {
      calls.add(r);
      if (r.url().equals("https://oauth2.googleapis.com/token")) return HttpTransport.Response.of(200, "{\"access_token\":\"ya29.x\",\"expires_in\":3599}");
      return r.bodyText().contains("dead-token") ? HttpTransport.Response.of(404, "{}") : HttpTransport.Response.of(200, "{}");
    }, json);
    var invalid = p.send(List.of(new PushProvider.Target("android", "good-token-1"), new PushProvider.Target("web", "dead-token-1")), new PushProvider.PushMessage("শিরোনাম", "বডি", "https://shikkhasathi.com/home"));
    assertThat(invalid).containsExactly("dead-token-1");
    p.send(List.of(new PushProvider.Target("android", "good-token-1")), new PushProvider.PushMessage("t", "b", null));
    assertThat(calls.stream().filter(c -> c.url().contains("oauth2")).count()).as("access token cached").isEqualTo(1);

    String form = calls.get(0).bodyText();
    String assertion = URLDecoder.decode(form.replaceAll(".*assertion=([^&]+).*", "$1"), StandardCharsets.UTF_8);
    var jwt = SignedJWT.parse(assertion);
    assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) kp.getPublic()))).isTrue();
    assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("fcm@shikkhasathi-app.iam.gserviceaccount.com");
    assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://oauth2.googleapis.com/token");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");

    var send = calls.get(1);
    assertThat(send.url()).isEqualTo("https://fcm.googleapis.com/v1/projects/shikkhasathi-app/messages:send");
    assertThat(send.headerValue("authorization")).isEqualTo("Bearer ya29.x");
    var msg = json.readTree(send.bodyText()).get("message");
    assertThat(msg.get("data").get("link").asString()).isEqualTo("https://shikkhasathi.com/home");
    assertThat(msg.get("webpush").get("fcm_options").get("link").asString()).isEqualTo("https://shikkhasathi.com/home");
    assertThat(msg.get("android").get("notification").get("channel_id").asString()).isEqualTo("classroom");
    assertThat(json.readTree(calls.get(3).bodyText()).get("message").has("webpush")).isFalse();

    // PushService picks FCM once the service account is saved in Admin
    assertThat(push.provider().orElseThrow().name()).isEqualTo("fake");
    put("/admin/integrations/push", Map.of("values", Map.of("fcm_service_account_json", saJson)), createUser("Admin", "admin")).expect(200);
    assertThat(push.provider().orElseThrow().name()).isEqualTo("fcm");
  }

  @Test
  void pushTesterRequiresTheServiceAccount() {
    var r = contract("settings.test", post("/admin/integrations/push/test", Map.of(), createUser("Admin", "admin")));
    assertThat(r.at("/ok").asBoolean()).isFalse();
    assertThat(r.at("/message").asString()).isEqualTo("FCM service account JSON দিন");
  }
}
