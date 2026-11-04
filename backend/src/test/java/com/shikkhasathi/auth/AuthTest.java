package com.shikkhasathi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.auth.adapter.GoogleIdentityVerifier;
import com.shikkhasathi.auth.service.SessionService;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.notification.email.FakeEmailProvider;
import com.shikkhasathi.notification.sms.FakeSmsProvider;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.Resp;
import com.shikkhasathi.support.TestUser;
import com.shikkhasathi.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Port of the previous API's auth.test.ts (plus the account endpoints and real Google ID-token verification). */
class AuthTest extends IntegrationTest {
  @Autowired FakeSmsProvider fakeSms;
  @Autowired FakeEmailProvider fakeEmail;
  @Autowired SessionService sessionService;
  @Autowired UserRepository users;
  @Autowired GoogleIdentityVerifier googleVerifier;
  @Autowired HttpTransport http;

  @AfterEach
  void restoreGoogle() {
    integrations.setEnvOverride("GOOGLE_CLIENT_ID", null);
    googleVerifier.useTransport(http);
  }

  private static String sixDigits(String text) {
    Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
    assertThat(m.find()).as("6-digit code in %s", text).isTrue();
    return m.group(1);
  }

  /** Same as the previous test helper: a user plus a real session with a known refresh token. */
  private record WithRefresh(TestUser user, String refreshToken) {}

  private WithRefresh createUserWithRefresh(String name) {
    var entity = users.save(new com.shikkhasathi.user.entity.User().setName(name));
    var t = sessionService.issue(entity, new SessionService.DeviceInfo(null, null, "android", null, null));
    return new WithRefresh(new TestUser(entity.getId(), name, null, null, entity.getRoles(), t.sessionId(), t.accessToken()), t.refreshToken());
  }

  /** Raw MockMvc call for cookie flows (Resp keeps a single value per header). */
  private MockHttpServletResponse raw(String path, Object body, Map<String, String> headers, Cookie... cookies) {
    try {
      MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
      headers.forEach(b::header);
      if (cookies.length > 0) b.cookie(cookies);
      var res = mvc.perform(b).andReturn().getResponse();
      res.setCharacterEncoding("UTF-8");
      return res;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String setCookie(MockHttpServletResponse res, String name) {
    return res.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
  }

  private static String cookieValue(String header) {
    return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
  }

  // ---------------------------------------------------------------- phone OTP

  @Nested
  class PhoneOtp {
    @Test
    void normalisesThePhoneSendsSmsSignsUpANewUser() {
      var req = contract("auth.otpRequest", post("/auth/otp/request", Map.of("phone", "01712-345678")));
      String code = req.at("/devCode").asString();
      assertThat(code).matches("^\\d{6}$");
      assertThat(fakeSms.last().to()).isEqualTo("+8801712345678");
      var hash = coreJdbc.queryForObject("select code_hash from auth_otps", Map.of(), String.class);
      assertThat(hash).doesNotContain(code);

      var v = contract("auth.otpVerify", post("/auth/otp/verify", Map.of("phone", "+8801712345678", "code", code, "platform", "android")));
      assertThat(v.at("/isNewUser").asBoolean()).isTrue();
      assertThat(v.at("/user/phone").asString()).isEqualTo("+8801712345678");
      assertThat(v.at("/refreshToken").asString()).isNotEmpty();

      var me = call(org.springframework.http.HttpMethod.GET, "/me", null, null, Map.of("Authorization", "Bearer " + v.at("/accessToken").asString()));
      contract("auth.me", me);
      assertThat(me.at("/roles/0").asString()).isEqualTo("student");
      assertThat(me.at("/roles").size()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidNumbersWrongCodesReusedCodes() {
      post("/auth/otp/request", Map.of("phone", "12345")).expect(422);
      var req = post("/auth/otp/request", Map.of("phone", "01812345678")).expect(200);
      String code = req.at("/devCode").asString();
      String wrong = code.equals("000000") ? "111111" : "000000";
      var w = post("/auth/otp/verify", Map.of("phone", "01812345678", "code", wrong)).expect(400);
      assertThat(w.errorMessage()).isEqualTo("কোডটি সঠিক নয়। আবার চেষ্টা করুন।");
      contract("auth.otpVerify", post("/auth/otp/verify", Map.of("phone", "01812345678", "code", code)));
      post("/auth/otp/verify", Map.of("phone", "01812345678", "code", code)).expect(400);
    }

    @Test
    void enforcesResendCooldown() {
      post("/auth/otp/request", Map.of("phone", "01912345678")).expect(200);
      var second = post("/auth/otp/request", Map.of("phone", "01912345678")).expect(429);
      assertThat(second.errorCode()).isEqualTo("RATE_LIMITED");
      assertThat(second.at("/error/details/retryAfterSec").asLong()).isBetween(1L, 60L);
    }

    @Test
    void locksACodeAfterFiveWrongAttempts() {
      var req = post("/auth/otp/request", Map.of("phone", "01512345678"));
      String code = req.at("/devCode").asString();
      String wrong = code.equals("999999") ? "888888" : "999999";
      for (int i = 0; i < 5; i++) post("/auth/otp/verify", Map.of("phone", "01512345678", "code", wrong));
      post("/auth/otp/verify", Map.of("phone", "01512345678", "code", code)).expect(429);
    }

    @Test
    void webSignInSetsCookiesAndOmitsTheRefreshToken() {
      String code = post("/auth/otp/request", Map.of("phone", "01612345678")).at("/devCode").asString();
      var res = raw("/auth/otp/verify", Map.of("phone", "01612345678", "code", code, "name", "  রহিম  "), Map.of());
      assertThat(res.getStatus()).isEqualTo(200);
      var r = contract("auth.otpVerify", new Resp(200, contentOf(res), Map.of()));
      assertThat(r.json().has("refreshToken")).isFalse();
      assertThat(r.at("/user/name").asString()).isEqualTo("রহিম");
      assertThat(setCookie(res, "ss_rt")).contains("Path=/api/v1/auth").contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=2592000");
      assertThat(setCookie(res, "ss_csrf")).contains("Path=/").doesNotContain("HttpOnly");
    }
  }

  private static String contentOf(MockHttpServletResponse res) {
    try {
      return res.getContentAsString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------- email + password

  @Nested
  class EmailPassword {
    @Test
    void registersWithArgon2idSendsWelcomeEmailLogsInRejectsBadPassword() {
      var reg = post("/auth/register", Map.of("name", "রহিম", "email", "Rahim@Example.com", "password", "correct horse 1", "platform", "android"));
      contract("auth.register", reg);
      String hash = coreJdbc.queryForObject("select password_hash from users where email = 'rahim@example.com'", Map.of(), String.class);
      assertThat(hash).startsWith("$argon2id$");
      assertThat(fakeEmail.last().to()).isEqualTo("rahim@example.com");

      var dup = post("/auth/register", Map.of("name", "XY", "email", "rahim@example.com", "password", "another pass 2")).expect(409);
      assertThat(dup.errorMessage()).isEqualTo("এই ইমেইলে আগেই অ্যাকাউন্ট আছে। লগইন করুন।");

      var bad = post("/auth/login", Map.of("email", "rahim@example.com", "password", "nope-nope")).expect(401);
      assertThat(bad.errorMessage()).isEqualTo("ইমেইল বা পাসওয়ার্ড সঠিক নয়।");
      var good = contract("auth.login", post("/auth/login", Map.of("email", "rahim@example.com", "password", "correct horse 1", "platform", "ios")));
      assertThat(good.at("/user/hasPassword").asBoolean()).isTrue();
      assertThat(good.at("/isNewUser").asBoolean()).isFalse();
    }

    @Test
    void verifiesEmailWithTheWelcomeCodeAndResetsPassword() {
      var reg = post("/auth/register", Map.of("name", "Karim", "email", "karim@example.com", "password", "password-123", "platform", "android")).expect(201);
      String code = sixDigits(fakeEmail.last().text());
      var v = call(org.springframework.http.HttpMethod.POST, "/me/verify-email", Map.of("code", code), null, Map.of("Authorization", "Bearer " + reg.at("/accessToken").asString()));
      contract("auth.verifyEmail", v);
      assertThat(v.at("/emailVerified").asBoolean()).isTrue();

      contract("auth.emailOtp", post("/auth/email-otp", Map.of("email", "karim@example.com", "purpose", "password_reset")));
      String reset = sixDigits(fakeEmail.last().text());
      contract("auth.passwordReset", post("/auth/password-reset", Map.of("email", "karim@example.com", "code", reset, "password", "brand-new-pass")));
      post("/auth/login", Map.of("email", "karim@example.com", "password", "brand-new-pass")).expect(200);
      post("/auth/login", Map.of("email", "karim@example.com", "password", "password-123")).expect(401);
    }

    @Test
    void doesNotRevealUnknownEmailsOnResetRequest() {
      post("/auth/email-otp", Map.of("email", "ghost@example.com", "purpose", "password_reset")).expect(200);
      assertThat(fakeEmail.outbox()).isEmpty();
    }

    @Test
    void validatesEmailAndPasswordWithBanglaMessages() {
      var r = post("/auth/register", Map.of("name", "Rahim", "email", "not-an-email", "password", "short")).expect(422);
      assertThat(r.errorCode()).isEqualTo("VALIDATION_FAILED");
      assertThat(r.body()).contains("সঠিক ইমেইল দিন").contains("পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে");
    }
  }

  // ---------------------------------------------------------------- Google

  @Nested
  class Google {
    @Test
    void createsThenLinksByGoogleSub() {
      var a = contract("auth.google", post("/auth/google", Map.of("idToken", "fake:nadia@gmail.com:Nadia", "platform", "android")));
      assertThat(a.at("/isNewUser").asBoolean()).isTrue();
      var b = contract("auth.google", post("/auth/google", Map.of("idToken", "fake:nadia@gmail.com:Nadia", "platform", "android")));
      assertThat(b.at("/isNewUser").asBoolean()).isFalse();
      assertThat(b.at("/user/id").asString()).isEqualTo(a.at("/user/id").asString());
      post("/auth/google", Map.of("idToken", "not-a-google-token")).expect(401);
    }

    @Test
    void linksAnExistingAccountByVerifiedEmail() {
      var reg = post("/auth/register", Map.of("name", "Nadia", "email", "nadia@gmail.com", "password", "password-123", "platform", "android")).expect(201);
      var g = post("/auth/google", Map.of("idToken", "fake:Nadia@gmail.com:Nadia", "platform", "android")).expect(200);
      assertThat(g.at("/isNewUser").asBoolean()).isFalse();
      assertThat(g.at("/user/id").asString()).isEqualTo(reg.at("/user/id").asString());
      assertThat(g.at("/user/emailVerified").asBoolean()).isTrue();
    }

    @Test
    void verifiesRealIdTokensAgainstGoogleKeys() throws Exception {
      RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
      AtomicInteger fetches = new AtomicInteger();
      googleVerifier.useTransport(r -> {
        assertThat(r.url()).isEqualTo(GoogleIdentityVerifier.JWKS_URL);
        fetches.incrementAndGet();
        return HttpTransport.Response.of(200, new JWKSet(key.toPublicJWK()).toString());
      });
      integrations.setEnvOverride("GOOGLE_CLIENT_ID", "web-client.apps.googleusercontent.com");

      String good = idToken(key, "web-client.apps.googleusercontent.com", "https://accounts.google.com", Instant.now().plusSeconds(600));
      var r = contract("auth.google", post("/auth/google", Map.of("idToken", good, "platform", "android")));
      assertThat(r.at("/user/email").asString()).isEqualTo("mina@gmail.com");
      assertThat(r.at("/user/avatarUrl").asString()).isEqualTo("https://lh3.googleusercontent.com/a/x");
      // the fake format is not accepted once a client id is configured
      post("/auth/google", Map.of("idToken", "fake:nadia@gmail.com:Nadia")).expect(401);
      // wrong audience, wrong issuer, expired, other signing key
      post("/auth/google", Map.of("idToken", idToken(key, "someone-else", "https://accounts.google.com", Instant.now().plusSeconds(600)))).expect(401);
      post("/auth/google", Map.of("idToken", idToken(key, "web-client.apps.googleusercontent.com", "https://evil.example", Instant.now().plusSeconds(600)))).expect(401);
      post("/auth/google", Map.of("idToken", idToken(key, "web-client.apps.googleusercontent.com", "accounts.google.com", Instant.now().minusSeconds(5)))).expect(401);
      RSAKey other = new RSAKeyGenerator(2048).keyID("k1").generate();
      var forged = post("/auth/google", Map.of("idToken", idToken(other, "web-client.apps.googleusercontent.com", "accounts.google.com", Instant.now().plusSeconds(600)))).expect(401);
      assertThat(forged.errorMessage()).isEqualTo("Google টোকেন যাচাই করা যায়নি।");
      assertThat(fetches.get()).isEqualTo(1);
    }

    private String idToken(RSAKey key, String aud, String iss, Instant exp) throws Exception {
      var claims = new JWTClaimsSet.Builder().subject("1098765").issuer(iss).audience(aud).expirationTime(Date.from(exp)).issueTime(new Date())
          .claim("email", "Mina@gmail.com").claim("email_verified", true).claim("name", "Mina").claim("picture", "https://lh3.googleusercontent.com/a/x").build();
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    }
  }

  // ---------------------------------------------------------------- refresh rotation

  @Nested
  class Refresh {
    @Test
    void rotatesMobileBodyTokenAndDetectsReuseAfterGraceByRevokingTheFamily() {
      var a = createUserWithRefresh("Test User");
      var r1 = contract("auth.refresh", post("/auth/refresh", Map.of("refreshToken", a.refreshToken())));
      String rt2 = r1.at("/refreshToken").asString();
      assertThat(rt2).isNotEqualTo(a.refreshToken());

      // simulate the old token replayed long after rotation
      coreJdbc.update("update sessions set rotated_at = :t where user_id = :u", Map.of("t", Timestamp.from(Instant.now().minusSeconds(60)), "u", a.user().id()));
      post("/auth/refresh", Map.of("refreshToken", a.refreshToken())).expect(401);
      post("/auth/refresh", Map.of("refreshToken", rt2)).expect(401);
    }

    @Test
    void reuseWithinTheGraceWindowDoesNotRevokeTheFamily() {
      var a = createUserWithRefresh("Racer");
      String rt2 = post("/auth/refresh", Map.of("refreshToken", a.refreshToken())).expect(200).at("/refreshToken").asString();
      post("/auth/refresh", Map.of("refreshToken", a.refreshToken())).expect(401);
      post("/auth/refresh", Map.of("refreshToken", rt2)).expect(200);
    }

    @Test
    void webFlowHttpOnlyCookiePlusCsrfDoubleSubmit() {
      var reg = raw("/auth/register", Map.of("name", "Web", "email", "web@example.com", "password", "password-123"), Map.of());
      assertThat(reg.getStatus()).isEqualTo(201);
      assertThat(new Resp(201, contentOf(reg), Map.of()).json().has("refreshToken")).isFalse();
      String rt = setCookie(reg, "ss_rt"), csrf = setCookie(reg, "ss_csrf");
      assertThat(rt).contains("HttpOnly");
      // the stored hash is HMAC(refresh secret) of the cookie value
      assertThat(coreJdbc.queryForObject("select count(*) from sessions where refresh_token_hash = :h", Map.of("h", crypto.hmac(cookieValue(rt))), Integer.class)).isEqualTo(1);

      Cookie rtC = new Cookie("ss_rt", cookieValue(rt)), csrfC = new Cookie("ss_csrf", cookieValue(csrf));
      var noCsrf = raw("/auth/refresh", Map.of(), Map.of(), rtC, csrfC);
      assertThat(noCsrf.getStatus()).isEqualTo(400);
      var ok = raw("/auth/refresh", Map.of(), Map.of("X-CSRF-Token", cookieValue(csrf)), rtC, csrfC);
      assertThat(ok.getStatus()).isEqualTo(200);
      var body = contract("auth.refresh", new Resp(200, contentOf(ok), Map.of()));
      assertThat(body.at("/accessToken").asString()).isNotEmpty();
      assertThat(body.json().has("refreshToken")).isFalse();
      String rotated = cookieValue(setCookie(ok, "ss_rt"));
      assertThat(rotated).isNotEqualTo(cookieValue(rt));

      // the rotated-away cookie fails and the cookies are cleared
      var stale = raw("/auth/refresh", Map.of(), Map.of("X-CSRF-Token", cookieValue(csrf)), rtC, csrfC);
      assertThat(stale.getStatus()).isEqualTo(401);
      assertThat(setCookie(stale, "ss_rt")).contains("Max-Age=0");

      // logout with the current cookie revokes the family
      var out = raw("/auth/logout", Map.of(), Map.of("X-CSRF-Token", cookieValue(csrf)), new Cookie("ss_rt", rotated), csrfC);
      assertThat(out.getStatus()).isEqualTo(200);
      contract("auth.logout", new Resp(200, contentOf(out), Map.of()));
      assertThat(setCookie(out, "ss_rt")).contains("Max-Age=0");
      assertThat(raw("/auth/refresh", Map.of(), Map.of("X-CSRF-Token", cookieValue(csrf)), new Cookie("ss_rt", rotated), csrfC).getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithoutAnyTokenIs401() {
      post("/auth/refresh", Map.of()).expect(401);
      contract("auth.logout", post("/auth/logout", Map.of()));
    }

    @Test
    void listsAndRevokesSessions() {
      var a = createUserWithRefresh("Test User");
      var list = contract("auth.sessions", get("/me/sessions", a.user()));
      assertThat(list.at("/items").size()).isEqualTo(1);
      assertThat(list.at("/items/0/current").asBoolean()).isTrue();
      contract("auth.revokeSession", delete("/me/sessions/" + list.at("/items/0/id").asString(), a.user()));
      post("/auth/refresh", Map.of("refreshToken", a.refreshToken())).expect(401);
    }
  }

  // ---------------------------------------------------------------- account

  @Nested
  class Account {
    @Test
    void updatesProfileAndPreferences() {
      var a = createUserWithRefresh("Old Name");
      var r = contract("auth.updateMe", patch("/me", Map.of("name", "  নতুন নাম ", "locale", "en", "theme", "dark", "lowDataMode", true, "avatarUrl", "https://media.shikkhasathi.com/a.png"), a.user()));
      assertThat(r.at("/name").asString()).isEqualTo("নতুন নাম");
      assertThat(r.at("/locale").asString()).isEqualTo("en");
      assertThat(r.at("/theme").asString()).isEqualTo("dark");
      assertThat(r.at("/lowDataMode").asBoolean()).isTrue();
      assertThat(r.at("/avatarUrl").asString()).isEqualTo("https://media.shikkhasathi.com/a.png");

      var keep = contract("auth.updateMe", patch("/me", Map.of("theme", "light"), a.user()));
      assertThat(keep.at("/avatarUrl").asString()).isEqualTo("https://media.shikkhasathi.com/a.png");
      var body = new HashMap<String, Object>();
      body.put("avatarUrl", null);
      var cleared = contract("auth.updateMe", patch("/me", body, a.user()));
      assertThat(cleared.at("/avatarUrl").isNull()).isTrue();
      assertThat(cleared.at("/lowDataMode").asBoolean()).isTrue();
      patch("/me", Map.of("avatarUrl", "not a url"), a.user()).expect(422);
    }

    @Test
    void changingEmailUnverifiesItSendsACodeAndRejectsTakenEmails() {
      createUser("Other", List.of("student"), "taken@example.com", null);
      var a = createUserWithRefresh("Mitu");
      var conflict = patch("/me", Map.of("email", "taken@example.com"), a.user()).expect(409);
      assertThat(conflict.errorMessage()).isEqualTo("এই ইমেইল অন্য অ্যাকাউন্টে ব্যবহার হচ্ছে।");
      var r = contract("auth.updateMe", patch("/me", Map.of("email", " Mitu@Example.com "), a.user()));
      assertThat(r.at("/email").asString()).isEqualTo("mitu@example.com");
      assertThat(r.at("/emailVerified").asBoolean()).isFalse();
      assertThat(fakeEmail.last().to()).isEqualTo("mitu@example.com");
      String code = sixDigits(fakeEmail.last().text());
      var v = contract("auth.verifyEmail", post("/me/verify-email", Map.of("code", code), a.user()));
      assertThat(v.at("/emailVerified").asBoolean()).isTrue();
    }

    @Test
    void setsThenChangesPassword() {
      var a = createUserWithRefresh("Phone Only");
      assertThat(get("/me", a.user()).at("/hasPassword").asBoolean()).isFalse();
      contract("auth.changePassword", post("/me/password", Map.of("newPassword", "first-password"), a.user()));
      var wrong = post("/me/password", Map.of("currentPassword", "nope", "newPassword", "second-password"), a.user()).expect(400);
      assertThat(wrong.errorMessage()).isEqualTo("বর্তমান পাসওয়ার্ড সঠিক নয়।");
      post("/me/password", Map.of("currentPassword", "first-password", "newPassword", "second-password"), a.user()).expect(200);
      assertThat(get("/me", a.user()).at("/hasPassword").asBoolean()).isTrue();
    }

    @Test
    void deletionRequestSignsOutEverywhere() {
      var a = createUserWithRefresh("Leaving");
      contract("auth.deleteMe", delete("/me", a.user()));
      assertThat(coreJdbc.queryForObject("select status from users where id = :id", Map.of("id", a.user().id()), String.class)).isEqualTo("pending_deletion");
      post("/auth/refresh", Map.of("refreshToken", a.refreshToken())).expect(401);
    }
  }

  // ---------------------------------------------------------------- RBAC + envelope

  @Nested
  class RbacEnvelope {
    @Test
    void returnsTheErrorEnvelopeFor404AndBadTokens() {
      var nf = get("/does-not-exist").expect(404);
      assertThat(nf.errorCode()).isEqualTo("NOT_FOUND");
      call(org.springframework.http.HttpMethod.GET, "/me", null, null, Map.of("Authorization", "Bearer garbage")).expect(401);
    }

    @Test
    void suspendedUsersCannotSignIn() {
      post("/auth/register", Map.of("name", "Sumi", "email", "s@example.com", "password", "password-123")).expect(201);
      coreJdbc.update("update users set status = 'suspended' where email = 's@example.com'", Map.of());
      post("/auth/login", Map.of("email", "s@example.com", "password", "password-123")).expect(403);
    }

    @Test
    void responseContractStripsPrivateFields() {
      var reg = post("/auth/register", Map.of("name", "Polash", "email", "p@example.com", "password", "password-123")).expect(201);
      assertThat(reg.body()).doesNotContain("argon2");
    }

    @Test
    void everyAdminPathIsAdminOnlyAndEverySellerPathSellerOnlyByContract() {
      // becoming a teacher: any signed-in user may apply, verify a university email and upload an ID card
      Set<String> apply = Set.of("GET /seller/profile", "POST /seller/apply", "POST /seller/verify-email/request", "POST /seller/verify-email/confirm", "POST /seller/uploads");
      List<String> wrong = new ArrayList<>();
      for (var e : com.shikkhasathi.support.Contracts.endpoints().values()) {
        String path = (String) e.get("path"), auth = (String) e.get("auth"), m = (String) e.get("method");
        boolean bad = (path.startsWith("/admin/") && !auth.equals("admin"))
            || (path.startsWith("/seller/") && !List.of("seller", "verified_seller").contains(auth) && !(apply.contains(m + " " + path) && auth.equals("user")));
        if (bad) wrong.add(m + " " + path + " (" + auth + ")");
      }
      assertThat(wrong).isEmpty();
    }
  }
}
