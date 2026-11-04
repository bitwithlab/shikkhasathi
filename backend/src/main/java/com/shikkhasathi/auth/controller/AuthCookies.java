package com.shikkhasathi.auth.controller;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Web refresh-token cookies, same names, paths and flags as before: {@code ss_rt} (httpOnly, path
 * {@code /api/v1/auth}) and the double-submit CSRF token {@code ss_csrf} (readable by JS, path {@code /}), both
 * SameSite=Lax, Secure in production, max-age = refresh TTL.
 */
@Component
public class AuthCookies {
  public static final String RT_COOKIE = "ss_rt";
  public static final String CSRF_COOKIE = "ss_csrf";
  public static final String COOKIE_PATH = "/api/v1/auth";

  /** Where the refresh token came from: the body (mobile) or the cookie (web). */
  public record Source(String token, boolean web) {}

  private final AppProperties props;

  public AuthCookies(AppProperties props) {
    this.props = props;
  }

  public void set(HttpServletResponse res, String refreshToken) {
    Duration maxAge = Duration.ofSeconds(JwtService.REFRESH_TTL_SEC);
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(RT_COOKIE, refreshToken).path(COOKIE_PATH).secure(props.isProd()).sameSite("Lax").maxAge(maxAge).httpOnly(true).build().toString());
    // Double-submit CSRF token: readable by JS, must be echoed in X-CSRF-Token on cookie-authenticated calls.
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, Crypto.randomToken(18)).path("/").secure(props.isProd()).sameSite("Lax").maxAge(maxAge).httpOnly(false).build().toString());
  }

  public void clear(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(RT_COOKIE, "").path(COOKIE_PATH).maxAge(0).build().toString());
    res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, "").path("/").maxAge(0).build().toString());
  }

  /** Web refresh/logout use the httpOnly cookie and must pass the CSRF double-submit check. */
  public Source refreshTokenFrom(HttpServletRequest req, String bodyToken) {
    if (bodyToken != null && !bodyToken.isEmpty()) return new Source(bodyToken, false);
    String cookie = cookie(req, RT_COOKIE);
    if (cookie == null || cookie.isEmpty()) return new Source(null, true);
    String header = req.getHeader("X-CSRF-Token");
    String csrf = cookie(req, CSRF_COOKIE);
    if (header == null || csrf == null || csrf.isEmpty() || !Crypto.safeEqual(header, csrf)) throw ApiException.badRequest("CSRF যাচাই ব্যর্থ হয়েছে।");
    return new Source(cookie, true);
  }

  private static String cookie(HttpServletRequest req, String name) {
    Cookie[] cs = req.getCookies();
    if (cs == null) return null;
    for (Cookie c : cs) if (name.equals(c.getName())) return c.getValue();
    return null;
  }
}
