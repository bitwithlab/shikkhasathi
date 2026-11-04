package com.shikkhasathi.auth.adapter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens (web GIS / Android Credential Manager / iOS GoogleSignIn) against Google's JWKS.
 * Accepted audiences are the configured web, Android and iOS client ids ({@code google} integration).
 * Without any client id (dev/test only) it accepts {@code fake:<email>:<name>} tokens, as before.
 */
@Component
public class GoogleIdentityVerifier {
  public static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
  private static final long JWKS_TTL_MS = 3_600_000;
  /** Refetch on an unknown key id at most this often (key rotation). */
  private static final long JWKS_MIN_REFRESH_MS = 60_000;
  private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

  public record GoogleIdentity(String sub, String email, boolean emailVerified, String name, String picture) {}

  private final IntegrationService integrations;
  private final AppProperties props;
  private volatile HttpTransport http;
  private volatile JWKSet jwks;
  private volatile long jwksAt;

  public GoogleIdentityVerifier(IntegrationService integrations, AppProperties props, HttpTransport http) {
    this.integrations = integrations;
    this.props = props;
    this.http = http;
  }

  /** Tests: swap the transport used to fetch Google's keys (and drop the cached key set). */
  public void useTransport(HttpTransport t) {
    this.http = t;
    this.jwks = null;
    this.jwksAt = 0;
  }

  public GoogleIdentity verify(String idToken) {
    var cfg = integrations.get("google");
    List<String> audiences = Stream.of(cfg.get("client_id"), cfg.get("android_client_id"), cfg.get("ios_client_id"))
        .filter(v -> v != null && !v.isEmpty()).toList();
    if (audiences.isEmpty()) {
      if (props.isProd()) throw ApiException.unauthorized("Google সাইন-ইন এখন চালু নেই।");
      String[] p = idToken.split(":", -1);
      if (!"fake".equals(p[0]) || p.length < 2 || p[1].isEmpty()) throw ApiException.unauthorized("Google টোকেন যাচাই করা যায়নি।");
      String email = p[1];
      String name = p.length > 2 && !p[2].isEmpty() ? p[2] : email.split("@", -1)[0];
      return new GoogleIdentity("fake-" + email, email.toLowerCase(Locale.ROOT), true, name, null);
    }
    try {
      SignedJWT jwt = SignedJWT.parse(idToken);
      if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) throw new IllegalArgumentException("alg");
      RSAKey key = key(jwt.getHeader().getKeyID());
      if (key == null || !jwt.verify(new RSASSAVerifier(key))) throw new IllegalArgumentException("signature");
      JWTClaimsSet c = jwt.getJWTClaimsSet();
      Date now = new Date();
      if (c.getExpirationTime() == null || !c.getExpirationTime().after(now)) throw new IllegalArgumentException("expired");
      if (c.getNotBeforeTime() != null && c.getNotBeforeTime().after(now)) throw new IllegalArgumentException("nbf");
      if (c.getIssuer() == null || !ISSUERS.contains(c.getIssuer())) throw new IllegalArgumentException("iss");
      if (c.getAudience() == null || c.getAudience().stream().noneMatch(audiences::contains)) throw new IllegalArgumentException("aud");
      Object email = c.getClaim("email");
      if (c.getSubject() == null || !(email instanceof String e)) throw new IllegalArgumentException("missing claims");
      Object name = c.getClaim("name");
      Object picture = c.getClaim("picture");
      return new GoogleIdentity(
          c.getSubject(),
          e.toLowerCase(Locale.ROOT),
          Boolean.TRUE.equals(c.getClaim("email_verified")),
          name instanceof String n ? n : e.split("@", -1)[0],
          picture instanceof String pic ? pic : null);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.unauthorized("Google টোকেন যাচাই করা যায়নি।");
    }
  }

  private RSAKey key(String kid) throws Exception {
    long now = System.currentTimeMillis();
    JWKSet set = jwks;
    if (set == null || now - jwksAt > JWKS_TTL_MS) set = fetch(now);
    JWK k = kid == null ? null : set.getKeyByKeyId(kid);
    if (k == null && now - jwksAt > JWKS_MIN_REFRESH_MS) {
      set = fetch(now);
      k = kid == null ? null : set.getKeyByKeyId(kid);
    }
    return k instanceof RSAKey r ? r : null;
  }

  private synchronized JWKSet fetch(long now) throws Exception {
    var res = http.send(HttpTransport.Request.get(JWKS_URL));
    if (!res.ok()) throw new IllegalStateException("jwks " + res.status());
    JWKSet set = JWKSet.parse(res.text());
    jwks = set;
    jwksAt = now;
    return set;
  }
}
