package com.shikkhasathi.auth.service;

import com.shikkhasathi.auth.adapter.GoogleIdentityVerifier;
import com.shikkhasathi.auth.dto.AuthDtos.OtpRequestResponse;
import com.shikkhasathi.auth.repository.AuthRepository;
import com.shikkhasathi.auth.service.SessionService.DeviceInfo;
import com.shikkhasathi.auth.service.SessionService.Tokens;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.notification.email.EmailService;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.sms.SmsService;
import com.shikkhasathi.security.RateLimiter;
import com.shikkhasathi.user.entity.User;
import com.shikkhasathi.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Sign-in flows: phone OTP (SMS), e-mail + password (argon2id), Google ID token, e-mail verification and password
 * reset codes. One-time codes are stored only as {@code HMAC(target:code)} and are never logged.
 *
 * <p>No method wraps a transaction on purpose: a wrong code must persist its attempt counter even though the request
 * fails, and code consumption is a compare-and-set update (same statement-by-statement behaviour as before).
 */
@Service
public class AuthService {
  static final int OTP_TTL_SEC = 120;
  static final int OTP_RESEND_SEC = 60;
  static final int OTP_MAX_PER_HOUR = 5;
  static final int OTP_MAX_ATTEMPTS = 5;
  static final int EMAIL_CODE_TTL_SEC = 600;

  public record AuthResult(User user, boolean isNewUser, Tokens tokens) {}

  private final AuthRepository repo;
  private final UserRepository users;
  private final SessionService sessions;
  private final PasswordEncoder passwords;
  private final RateLimiter limiter;
  private final SmsService sms;
  private final EmailService email;
  private final GoogleIdentityVerifier google;
  private final Crypto crypto;
  private final AppProperties props;
  private volatile String dummyHash;

  public AuthService(AuthRepository repo, UserRepository users, SessionService sessions, PasswordEncoder passwords, RateLimiter limiter,
                     SmsService sms, EmailService email, GoogleIdentityVerifier google, Crypto crypto, AppProperties props) {
    this.repo = repo;
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.limiter = limiter;
    this.sms = sms;
    this.email = email;
    this.google = google;
    this.crypto = crypto;
    this.props = props;
  }

  private String otpHash(String target, String code) {
    return crypto.hmac(target + ":" + code);
  }

  private String dummyHash() {
    String h = dummyHash;
    if (h == null) dummyHash = h = passwords.encode("not-a-real-password");
    return h;
  }

  private boolean matches(String raw, String hash) {
    try {
      return passwords.matches(raw, hash);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private User reload(UUID id) {
    return users.findById(id).orElseThrow(ApiException::unauthorized);
  }

  // ---------------------------------------------------------------- phone OTP

  public OtpRequestResponse requestPhoneOtp(String phone, String ip) {
    var perPhone = limiter.hit("otp:phone:" + phone, OTP_MAX_PER_HOUR, 3600);
    if (!perPhone.allowed()) throw ApiException.rateLimited("এক ঘণ্টায় সর্বোচ্চ " + OTP_MAX_PER_HOUR + " বার কোড পাঠানো যায়।", Map.of("retryAfterSec", perPhone.retryAfterSec()));
    if (ip != null) {
      var perIp = limiter.hit("otp:ip:" + ip, 20, 3600);
      if (!perIp.allowed()) throw ApiException.rateLimited(null, Map.of("retryAfterSec", perIp.retryAfterSec()));
    }
    var last = repo.latestOtp(phone, "login").orElse(null);
    long now = System.currentTimeMillis();
    if (last != null && last.consumedAt() == null && now - last.createdAt().toEpochMilli() < OTP_RESEND_SEC * 1000L) {
      long wait = (long) Math.ceil((OTP_RESEND_SEC * 1000.0 - (now - last.createdAt().toEpochMilli())) / 1000.0);
      throw ApiException.rateLimited(wait + " সেকেন্ড পর আবার কোড চাইতে পারবেন।", Map.of("retryAfterSec", wait));
    }
    var provider = sms.provider().orElseThrow(() -> new ApiException(ErrorCode.INTEGRATION_DISABLED, "SMS সার্ভিস এখন চালু নেই। ইমেইল দিয়ে লগইন করুন।"));
    String code = Crypto.randomDigits(6);
    repo.createOtp(phone, otpHash(phone, code), "login", Instant.now().plusSeconds(OTP_TTL_SEC));
    provider.send(phone, "ShikkhaSathi কোড: " + code + " (২ মিনিট বৈধ)। কাউকে জানাবেন না।");
    boolean dev = "fake".equals(provider.name()) && !props.isProd();
    return new OtpRequestResponse(true, OTP_TTL_SEC, OTP_RESEND_SEC, dev ? code : null);
  }

  private void checkCode(String target, String purpose, String code) {
    var otp = repo.latestOtp(target, purpose).orElse(null);
    if (otp == null || otp.consumedAt() != null || otp.expiresAt().isBefore(Instant.now())) throw ApiException.badRequest("কোডের মেয়াদ শেষ। নতুন কোড নিন।");
    if (otp.attempts() >= OTP_MAX_ATTEMPTS) throw ApiException.rateLimited("অনেকবার ভুল কোড দেওয়া হয়েছে। নতুন কোড নিন।", null);
    if (!Crypto.safeEqual(otp.codeHash(), otpHash(target, code))) {
      repo.bumpOtpAttempts(otp.id());
      throw ApiException.badRequest("কোডটি সঠিক নয়। আবার চেষ্টা করুন।");
    }
    if (!repo.consumeOtp(otp.id())) throw ApiException.badRequest("কোডটি আগেই ব্যবহার হয়েছে।");
  }

  public AuthResult verifyPhoneOtp(String phone, String code, String name, DeviceInfo device) {
    checkCode(phone, "login", code);
    User user = users.findByPhone(phone).orElse(null);
    boolean isNew = user == null;
    if (user == null) {
      user = users.save(new User().setPhone(phone).setPhoneVerifiedAt(Instant.now()).setName(name != null ? name : "শিক্ষার্থী"));
    } else if (user.getPhoneVerifiedAt() == null) {
      repo.updateUser(user.getId(), Map.of("phone_verified_at", Instant.now()));
      user = reload(user.getId());
    }
    return new AuthResult(user, isNew, sessions.issue(user, device));
  }

  // ---------------------------------------------------------------- email + password

  public AuthResult register(String name, String emailAddr, String password, DeviceInfo device) {
    if (users.findByEmail(emailAddr).isPresent()) throw ApiException.conflict("এই ইমেইলে আগেই অ্যাকাউন্ট আছে। লগইন করুন।");
    User user = users.save(new User().setName(name).setEmail(emailAddr).setPasswordHash(passwords.encode(password)).setRoles(new java.util.ArrayList<>(List.of("student"))));
    String code = createEmailCode(user.getEmail(), "email_verify");
    email.send(user.getEmail(), user.getName(), EmailTemplate.WELCOME, Map.of("name", user.getName(), "code", code), null, "user:" + user.getId(), null);
    return new AuthResult(user, true, sessions.issue(user, device));
  }

  public AuthResult loginWithPassword(String emailAddr, String password, DeviceInfo device, String ip) {
    var lim = limiter.hit("login:" + (ip != null ? ip : "na") + ":" + emailAddr, 10, 15 * 60);
    if (!lim.allowed()) throw ApiException.rateLimited("অনেকবার চেষ্টা হয়েছে। ১৫ মিনিট পর আবার চেষ্টা করুন।", Map.of("retryAfterSec", lim.retryAfterSec()));
    User user = users.findByEmail(emailAddr).orElse(null);
    // Constant-ish work even when the user is missing, to avoid user enumeration by timing.
    String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash();
    boolean ok = matches(password, hash);
    if (user == null || user.getPasswordHash() == null || !ok) throw ApiException.unauthorized("ইমেইল বা পাসওয়ার্ড সঠিক নয়।");
    return new AuthResult(user, false, sessions.issue(user, device));
  }

  public void changePassword(UUID userId, String current, String next) {
    User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
    if (user.getPasswordHash() != null) {
      if (current == null || current.isEmpty() || !matches(current, user.getPasswordHash())) throw ApiException.badRequest("বর্তমান পাসওয়ার্ড সঠিক নয়।");
    }
    repo.updateUser(userId, Map.of("password_hash", passwords.encode(next)));
  }

  String createEmailCode(String emailAddr, String purpose) {
    String code = Crypto.randomDigits(6);
    repo.createOtp(emailAddr, otpHash(emailAddr, code), purpose, Instant.now().plusSeconds(EMAIL_CODE_TTL_SEC));
    return code;
  }

  public void requestEmailCode(String emailAddr, String purpose) {
    var lim = limiter.hit("emailcode:" + emailAddr, 5, 3600);
    if (!lim.allowed()) throw ApiException.rateLimited(null, Map.of("retryAfterSec", lim.retryAfterSec()));
    User user = users.findByEmail(emailAddr).orElse(null);
    // Do not reveal whether the account exists.
    if (user == null) return;
    String code = createEmailCode(emailAddr, purpose);
    email.send(emailAddr, user.getName(), "password_reset".equals(purpose) ? EmailTemplate.PASSWORD_RESET : EmailTemplate.EMAIL_OTP,
        Map.of("code", code), user.getLocale(), "user:" + user.getId(), null);
  }

  public void resetPassword(String emailAddr, String code, String password) {
    checkCode(emailAddr, "password_reset", code);
    User user = users.findByEmail(emailAddr).orElseThrow(() -> ApiException.badRequest("কোডটি সঠিক নয়।"));
    Map<String, Object> set = new HashMap<>();
    set.put("password_hash", passwords.encode(password));
    set.put("email_verified_at", user.getEmailVerifiedAt() != null ? user.getEmailVerifiedAt() : Instant.now());
    repo.updateUser(user.getId(), set);
  }

  public User verifyEmail(UUID userId, String code) {
    User user = users.findById(userId).orElse(null);
    if (user == null || user.getEmail() == null) throw ApiException.badRequest("কোনো ইমেইল যুক্ত নেই।");
    checkCode(user.getEmail(), "email_verify", code);
    repo.updateUser(userId, Map.of("email_verified_at", Instant.now()));
    return reload(userId);
  }

  // ---------------------------------------------------------------- Google sign-in

  public AuthResult loginWithGoogle(String idToken, DeviceInfo device) {
    var g = google.verify(idToken);
    User user = users.findByGoogleSub(g.sub()).orElse(null);
    boolean isNew = false;
    if (user == null && g.emailVerified()) {
      User byEmail = users.findByEmail(g.email()).orElse(null);
      if (byEmail != null) {
        Map<String, Object> set = new HashMap<>();
        set.put("google_sub", g.sub());
        set.put("email_verified_at", byEmail.getEmailVerifiedAt() != null ? byEmail.getEmailVerifiedAt() : Instant.now());
        set.put("avatar_url", byEmail.getAvatarUrl() != null ? byEmail.getAvatarUrl() : g.picture());
        repo.updateUser(byEmail.getId(), set);
        user = reload(byEmail.getId());
      }
    }
    if (user == null) {
      isNew = true;
      user = users.save(new User().setName(g.name()).setEmail(g.email()).setGoogleSub(g.sub()).setEmailVerifiedAt(g.emailVerified() ? Instant.now() : null).setAvatarUrl(g.picture()));
      email.send(g.email(), g.name(), EmailTemplate.WELCOME, Map.of("name", g.name()), null, "user:" + user.getId(), null);
    }
    return new AuthResult(user, isNew, sessions.issue(user, device));
  }
}
