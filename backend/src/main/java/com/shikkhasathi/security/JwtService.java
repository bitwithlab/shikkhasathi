package com.shikkhasathi.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * HS256 access tokens with the same claims as the previous API ({@code sub, roles, sid, iss=shikkhasathi,
 * aud=shikkhasathi-api}), so tokens and sessions keep working across the migration. Also short-lived
 * "purpose" tokens (OAuth state, playback, uploads).
 */
@Service
public class JwtService {
  public static final long ACCESS_TTL_SEC = 15 * 60;
  public static final long REFRESH_TTL_SEC = 30L * 24 * 3600;
  private final byte[] key;

  public JwtService(AppProperties props) {
    byte[] k = props.jwtAccessSecret().getBytes(StandardCharsets.UTF_8);
    // nimbus requires ≥ 256-bit keys; HMAC zero-pads short keys internally, so zero-padding yields the same
    // signatures as the previous API for secrets shorter than 32 bytes
    this.key = k.length >= 32 ? k : Arrays.copyOf(k, 32);
  }

  public record Signed(String token, Instant expiresAt) {}

  public Signed signAccess(AuthUser u) {
    Instant exp = Instant.now().plusSeconds(ACCESS_TTL_SEC);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(u.id().toString())
        .claim("roles", u.roles())
        .claim("sid", u.sessionId().toString())
        .issuer("shikkhasathi")
        .audience("shikkhasathi-api")
        .issueTime(new Date())
        .expirationTime(Date.from(exp))
        .build();
    return new Signed(sign(claims), exp);
  }

  public AuthUser verifyAccess(String token) {
    try {
      JWTClaimsSet c = verify(token);
      if (!"shikkhasathi".equals(c.getIssuer()) || c.getAudience() == null || !c.getAudience().contains("shikkhasathi-api")) throw new IllegalArgumentException("iss/aud");
      Object sid = c.getClaim("sid");
      if (c.getSubject() == null || !(sid instanceof String s)) throw new IllegalArgumentException("payload");
      @SuppressWarnings("unchecked")
      List<String> roles = c.getClaim("roles") instanceof List<?> l ? (List<String>) l : List.of("student");
      return new AuthUser(UUID.fromString(c.getSubject()), roles, UUID.fromString(s));
    } catch (Exception e) {
      throw ApiException.unauthorized();
    }
  }

  /** Signed short-lived token for a single purpose (claim {@code p}). */
  public String signPurpose(String purpose, Map<String, Object> data, long ttlSec) {
    var b = new JWTClaimsSet.Builder().issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(ttlSec))).claim("p", purpose);
    data.forEach(b::claim);
    return sign(b.build());
  }

  public Map<String, Object> verifyPurpose(String purpose, String token) {
    try {
      JWTClaimsSet c = verify(token);
      if (!purpose.equals(c.getClaim("p"))) throw new IllegalArgumentException("purpose");
      return c.getClaims();
    } catch (Exception e) {
      throw ApiException.unauthorized("লিংকটির মেয়াদ শেষ বা অবৈধ।");
    }
  }

  private String sign(JWTClaimsSet claims) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private JWTClaimsSet verify(String token) throws Exception {
    SignedJWT jwt = SignedJWT.parse(token);
    if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) throw new IllegalArgumentException("alg");
    if (!jwt.verify(new MACVerifier(key))) throw new IllegalArgumentException("signature");
    JWTClaimsSet c = jwt.getJWTClaimsSet();
    if (c.getExpirationTime() == null || c.getExpirationTime().before(new Date())) throw new IllegalArgumentException("expired");
    return c;
  }

}
