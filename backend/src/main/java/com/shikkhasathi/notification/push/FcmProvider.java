package com.shikkhasathi.notification.push;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * FCM HTTP v1 (Android + web) with the service-account JSON from Admin → Integrations → Push: an RS256 JWT
 * assertion is exchanged for an OAuth access token, cached until a minute before it expires.
 */
public class FcmProvider implements PushProvider {
  /** The fields of a Google service-account JSON we use. */
  public record ServiceAccount(String projectId, String clientEmail, String privateKeyPem) {
    /** Parse {@code {"project_id","client_email","private_key",...}}; throws IllegalArgumentException when invalid. */
    public static ServiceAccount parse(String json, JsonMapper mapper) {
      JsonNode n;
      try {
        n = mapper.readTree(json);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("invalid FCM service account JSON");
      }
      String p = n.path("project_id").asString(""), e = n.path("client_email").asString(""), k = n.path("private_key").asString("");
      if (p.isEmpty() || e.isEmpty() || k.isEmpty()) throw new IllegalArgumentException("invalid FCM service account JSON");
      return new ServiceAccount(p, e, k);
    }
  }

  static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  private final ServiceAccount sa;
  private final HttpTransport http;
  private final JsonMapper json;
  private String cachedToken;
  private long cachedExp;

  public FcmProvider(ServiceAccount sa, HttpTransport http, JsonMapper json) {
    this.sa = sa;
    this.http = http;
    this.json = json;
  }

  @Override
  public String name() {
    return "fcm";
  }

  /** OAuth access token for the FCM scope (cached). Also the "test connection" check. */
  public synchronized String accessToken() {
    if (cachedToken != null && cachedExp > System.currentTimeMillis() + 60_000) return cachedToken;
    String assertion;
    try {
      long now = System.currentTimeMillis() / 1000;
      var claims = new JWTClaimsSet.Builder()
          .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
          .issuer(sa.clientEmail())
          .audience(TOKEN_URL)
          .issueTime(new Date(now * 1000))
          .expirationTime(new Date((now + 3600) * 1000))
          .build();
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
      jwt.sign(new RSASSASigner(privateKey(sa.privateKeyPem())));
      assertion = jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("FCM: invalid private key");
    }
    var res = http.send(Request.post(TOKEN_URL, HttpTransport.form(new LinkedHashMap<>(Map.of("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion", assertion))))
        .header("content-type", "application/x-www-form-urlencoded"));
    if (!res.ok()) throw new IllegalStateException("FCM auth " + res.status());
    JsonNode d = json.readTree(res.text());
    cachedToken = d.path("access_token").asString();
    cachedExp = System.currentTimeMillis() + d.path("expires_in").asLong(3600) * 1000;
    return cachedToken;
  }

  static PrivateKey privateKey(String pem) throws Exception {
    String b64 = pem.replace("\\n", "\n").replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
  }

  @Override
  public List<String> send(List<Target> targets, PushMessage msg) {
    String at = accessToken();
    List<String> invalid = new ArrayList<>();
    for (Target t : targets) {
      Map<String, Object> data = new LinkedHashMap<>(msg.data() == null ? Map.of() : msg.data());
      if (msg.link() != null) data.put("link", msg.link());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("token", t.token());
      m.put("notification", Map.of("title", msg.title(), "body", msg.body()));
      m.put("data", data);
      m.put("android", Map.of("priority", "high", "notification", Map.of("channel_id", "classroom", "click_action", "OPEN_LINK")));
      if (msg.link() != null) m.put("webpush", Map.of("fcm_options", Map.of("link", msg.link())));
      var res = http.send(Request.post("https://fcm.googleapis.com/v1/projects/" + sa.projectId() + "/messages:send", json.writeValueAsString(Map.of("message", m)))
          .header("authorization", "Bearer " + at).header("content-type", "application/json").timeout(Duration.ofSeconds(10)));
      if (res.status() == 404 || res.status() == 400) invalid.add(t.token());
    }
    return invalid;
  }
}
