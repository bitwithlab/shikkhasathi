package com.shikkhasathi.auth.controller;

import com.shikkhasathi.auth.dto.AuthDtos.*;
import com.shikkhasathi.auth.service.AuthService;
import com.shikkhasathi.auth.service.AuthService.AuthResult;
import com.shikkhasathi.auth.service.SessionService;
import com.shikkhasathi.auth.service.SessionService.DeviceInfo;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.RateLimit;
import com.shikkhasathi.user.service.MeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Sign-in, refresh-token rotation, logout and e-mail codes (contract group {@code auth}, paths {@code /auth/*}). */
@RestController
@RequestMapping("/api/v1/auth")
@Auth(Auth.Level.PUBLIC)
public class AuthController {
  /** Same as the previous API's "strict" limit on credential endpoints: 10 per 15 minutes per client. */
  static final int STRICT_MAX = 10;
  static final int STRICT_WINDOW = 15 * 60;

  private final AuthService auth;
  private final SessionService sessions;
  private final MeService me;
  private final AuthCookies cookies;

  public AuthController(AuthService auth, SessionService sessions, MeService me, AuthCookies cookies) {
    this.auth = auth;
    this.sessions = sessions;
    this.me = me;
    this.cookies = cookies;
  }

  static DeviceInfo device(HttpServletRequest req, String deviceId, String deviceName, String platform) {
    String ua = req.getHeader("User-Agent");
    String name = deviceName != null ? deviceName : ua == null ? null : ua.substring(0, Math.min(80, ua.length()));
    return new DeviceInfo(deviceId, name, platform != null ? platform : "web", req.getRemoteAddr(), ua);
  }

  static DeviceInfo device(HttpServletRequest req, DeviceFields b) {
    return device(req, b.deviceId(), b.deviceName(), b.platform());
  }

  private AuthTokens respond(HttpServletResponse res, boolean web, AuthResult r) {
    var user = me.buildMe(r.user());
    var t = r.tokens();
    if (web) {
      cookies.set(res, t.refreshToken());
      return new AuthTokens(t.accessToken(), t.accessTokenExpiresAt(), null, user, r.isNewUser());
    }
    return new AuthTokens(t.accessToken(), t.accessTokenExpiresAt(), t.refreshToken(), user, r.isNewUser());
  }

  @PostMapping("/otp/request")
  @RateLimit(max = 10, window = 3600)
  public OtpRequestResponse otpRequest(@Valid @RequestBody OtpRequestBody body, HttpServletRequest req) {
    return auth.requestPhoneOtp(body.phone(), req.getRemoteAddr());
  }

  @PostMapping("/otp/verify")
  @RateLimit(max = STRICT_MAX, window = STRICT_WINDOW)
  public AuthTokens otpVerify(@Valid @RequestBody OtpVerifyBody body, HttpServletRequest req, HttpServletResponse res) {
    return respond(res, "web".equals(body.platform()), auth.verifyPhoneOtp(body.phone(), body.code(), body.name(), device(req, body)));
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(max = STRICT_MAX, window = STRICT_WINDOW)
  public AuthTokens register(@Valid @RequestBody RegisterBody body, HttpServletRequest req, HttpServletResponse res) {
    return respond(res, "web".equals(body.platform()), auth.register(body.name(), body.email(), body.password(), device(req, body)));
  }

  @PostMapping("/login")
  @RateLimit(max = STRICT_MAX, window = STRICT_WINDOW)
  public AuthTokens login(@Valid @RequestBody LoginBody body, HttpServletRequest req, HttpServletResponse res) {
    return respond(res, "web".equals(body.platform()), auth.loginWithPassword(body.email(), body.password(), device(req, body), req.getRemoteAddr()));
  }

  @PostMapping("/google")
  @RateLimit(max = STRICT_MAX, window = STRICT_WINDOW)
  public AuthTokens google(@Valid @RequestBody GoogleLoginBody body, HttpServletRequest req, HttpServletResponse res) {
    return respond(res, "web".equals(body.platform()), auth.loginWithGoogle(body.idToken(), device(req, body)));
  }

  @PostMapping("/refresh")
  @RateLimit(max = 60, window = 60)
  public AuthTokens refresh(@Valid @RequestBody RefreshBody body, HttpServletRequest req, HttpServletResponse res) {
    var src = cookies.refreshTokenFrom(req, body.refreshToken());
    if (src.token() == null) throw ApiException.unauthorized();
    try {
      var r = sessions.refresh(src.token(), device(req, null, null, src.web() ? "web" : "android"));
      return respond(res, src.web(), new AuthResult(r.user(), false, r.tokens()));
    } catch (ApiException e) {
      if (src.web()) cookies.clear(res);
      throw e;
    }
  }

  @PostMapping("/logout")
  public Ok logout(@Valid @RequestBody RefreshBody body, HttpServletRequest req, HttpServletResponse res) {
    var src = cookies.refreshTokenFrom(req, body.refreshToken());
    sessions.logout(src.token());
    cookies.clear(res);
    return Ok.OK;
  }

  @PostMapping("/email-otp")
  @RateLimit(max = 5, window = 3600)
  public Ok emailOtp(@Valid @RequestBody EmailOtpRequestBody body) {
    auth.requestEmailCode(body.email(), body.purpose());
    return Ok.OK;
  }

  @PostMapping("/password-reset")
  @RateLimit(max = STRICT_MAX, window = STRICT_WINDOW)
  public Ok passwordReset(@Valid @RequestBody PasswordResetBody body) {
    auth.resetPassword(body.email(), body.code(), body.password());
    return Ok.OK;
  }
}
