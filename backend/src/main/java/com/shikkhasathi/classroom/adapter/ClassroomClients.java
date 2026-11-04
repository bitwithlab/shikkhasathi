package com.shikkhasathi.classroom.adapter;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Picks the Classroom client: a fake connection keeps the fake; otherwise the real Google client when the Google
 * OAuth client id + secret are configured; otherwise the fake outside production (503 in production).
 * Also registers the "সংযোগ পরীক্ষা করুন" tester for the {@code google} integration.
 */
@Component
public class ClassroomClients implements ResettableFake {
  public static final String REDIRECT_PATH = "/api/v1/classroom/oauth/callback";

  private final IntegrationService integrations;
  private final HttpTransport http;
  private final JsonMapper json;
  private final AppProperties props;
  /** Test hook: given the connection's created_at (null = no connection), return a client. */
  private volatile Function<Instant, GoogleClassroomClient> override;

  public ClassroomClients(IntegrationService integrations, HttpTransport http, JsonMapper json, AppProperties props) {
    this.integrations = integrations;
    this.http = http;
    this.json = json;
    this.props = props;
  }

  /** Tests only: inject a fake with a controllable clock (null restores the default selection). */
  public void setFactory(Function<Instant, GoogleClassroomClient> f) {
    this.override = f;
  }

  @Override
  public void reset() {
    override = null;
  }

  public String redirectUri() {
    return props.appUrl() + REDIRECT_PATH;
  }

  public boolean isProd() {
    return props.isProd();
  }

  /** The configured real client, or null when the Google OAuth client is not set. */
  private RealClassroomClient real() {
    var g = integrations.get("google");
    String id = g.get("client_id");
    String secret = g.get("client_secret");
    if (id == null || id.isEmpty() || secret == null || secret.isEmpty()) return null;
    return new RealClassroomClient(new RealClassroomClient.Config(id, secret, ClassroomMapping.CLASSROOM_SCOPES), http, json);
  }

  /**
   * @param hasConn      a connection row exists
   * @param connIsFake   that connection was made with the fake client
   * @param connCreated  its created_at (anchor of the fake dataset)
   */
  public GoogleClassroomClient clientFor(boolean hasConn, boolean connIsFake, Instant connCreated) {
    var o = override;
    if (o != null) return o.apply(hasConn ? connCreated : null);
    if (hasConn && connIsFake) return new FakeClassroomClient(connCreated);
    var r = real();
    if (r != null) return r;
    if (props.isProd()) throw new ApiException(ErrorCode.INTEGRATION_DISABLED, "Google Classroom সংযোগ এখন চালু নেই।");
    return new FakeClassroomClient(hasConn ? connCreated : Instant.now());
  }

  /** No connection yet (connect URL, code exchange). */
  public GoogleClassroomClient forNewConnection() {
    return clientFor(false, false, null);
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("google", (c, opts) -> {
      String id = c.get("client_id");
      String secret = c.get("client_secret");
      if (id == null || id.isEmpty() || secret == null || secret.isEmpty()) return new IntegrationService.TestResult(false, "Client id ও secret দিন");
      // An invalid code returns invalid_grant when the client credentials are valid, invalid_client otherwise.
      Map<String, String> form = new LinkedHashMap<>();
      form.put("client_id", id);
      form.put("client_secret", secret);
      form.put("grant_type", "authorization_code");
      form.put("code", "test");
      form.put("redirect_uri", redirectUri());
      var res = http.send(Request.post("https://oauth2.googleapis.com/token", HttpTransport.form(form)).header("content-type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(15)));
      String error = null;
      try {
        JsonNode data = json.readTree(res.text());
        error = ClassroomMapping.str(data, "error");
      } catch (RuntimeException ignored) {
        // not JSON
      }
      if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) return new IntegrationService.TestResult(false, "Google: " + error + " — client id/secret ঠিক নেই");
      return new IntegrationService.TestResult(true, "Google OAuth client ঠিক আছে। Redirect URI: " + redirectUri());
    });
  }
}
