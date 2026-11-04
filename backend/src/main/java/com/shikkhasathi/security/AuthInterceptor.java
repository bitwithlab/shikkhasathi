package com.shikkhasathi.security;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before argument resolution/validation for every /api/v1 handler:
 * authenticates the bearer token, blocks suspended accounts, enforces {@link Auth} levels (RBAC),
 * applies the global and per-route {@link RateLimit}, and sets {@link CacheControl}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
  private static final int GLOBAL_MAX = 300;
  private final JwtService jwt;
  private final StringRedisTemplate redis;
  private final RateLimiter limiter;
  private final NamedParameterJdbcTemplate core;

  public AuthInterceptor(JwtService jwt, StringRedisTemplate redis, RateLimiter limiter, @Qualifier("coreJdbc") NamedParameterJdbcTemplate core) {
    this.jwt = jwt;
    this.redis = redis;
    this.limiter = limiter;
    this.core = core;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    if (!(handler instanceof HandlerMethod hm)) return true;
    Auth a = hm.getMethodAnnotation(Auth.class);
    if (a == null) a = hm.getBeanType().getAnnotation(Auth.class);
    if (a == null) return true; // actuator, springdoc, static
    String ip = req.getRemoteAddr();

    var g = limiter.hit("rl:g:" + ip, GLOBAL_MAX, 60);
    if (!g.allowed()) throw ApiException.rateLimited(null, Map.of("retryAfterSec", g.retryAfterSec()));

    AuthUser user = null;
    String h = req.getHeader("Authorization");
    if (h != null) {
      try {
        String[] p = h.split(" ", 2);
        if (p.length != 2 || !p[0].equalsIgnoreCase("bearer") || p[1].isBlank()) throw ApiException.unauthorized();
        user = jwt.verifyAccess(p[1]);
        // suspended accounts lose access immediately (access tokens live 15 min)
        if (Boolean.TRUE.equals(redis.hasKey("susp:" + user.id())))
          throw new ApiException(ErrorCode.FORBIDDEN, "আপনার অ্যাকাউন্ট সাময়িকভাবে বন্ধ আছে। সাপোর্টে যোগাযোগ করুন।");
      } catch (ApiException e) {
        if (a.value() != Auth.Level.OPTIONAL) throw e;
        user = null;
      }
    }
    Auth.Level lvl = a.value();
    if (lvl != Auth.Level.PUBLIC && lvl != Auth.Level.OPTIONAL) {
      if (user == null) throw ApiException.unauthorized();
      if (lvl == Auth.Level.ADMIN && !user.isAdmin()) throw ApiException.forbidden();
      if ((lvl == Auth.Level.SELLER || lvl == Auth.Level.VERIFIED_SELLER) && !user.has("seller")) throw ApiException.forbidden();
      if (lvl == Auth.Level.VERIFIED_SELLER && !verifiedSeller(user.id())) throw ApiException.forbidden("ভেরিফিকেশন সম্পন্ন হলে এই কাজটি করতে পারবেন।");
    }
    if (user != null) {
      req.setAttribute(CurrentUser.ATTR, user);
      var auth = new UsernamePasswordAuthenticationToken(user, null, user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    RateLimit rl = hm.getMethodAnnotation(RateLimit.class);
    if (rl != null) {
      String who = user != null ? user.id().toString() : ip;
      var r = limiter.hit("rl:r:" + hm.getMethod().getDeclaringClass().getSimpleName() + "." + hm.getMethod().getName() + ":" + who, rl.max(), rl.window());
      if (!r.allowed()) throw ApiException.rateLimited(null, Map.of("retryAfterSec", r.retryAfterSec()));
    }
    CacheControl cc = hm.getMethodAnnotation(CacheControl.class);
    if (cc != null) res.setHeader("Cache-Control", cc.value());
    return true;
  }

  private boolean verifiedSeller(UUID id) {
    var rows = core.queryForList("select verification_status from seller_profiles where user_id = :id", Map.of("id", id), String.class);
    return !rows.isEmpty() && "verified".equals(rows.get(0));
  }
}
