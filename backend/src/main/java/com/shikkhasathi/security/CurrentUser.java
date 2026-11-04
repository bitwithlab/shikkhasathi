package com.shikkhasathi.security;

import com.shikkhasathi.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Access to the caller resolved by {@link AuthInterceptor}. Controllers can also take {@code AuthUser} as a parameter. */
public final class CurrentUser {
  public static final String ATTR = "ss.authUser";

  private CurrentUser() {}

  public static AuthUser orNull() {
    var a = RequestContextHolder.getRequestAttributes();
    if (!(a instanceof ServletRequestAttributes s)) return null;
    return (AuthUser) s.getRequest().getAttribute(ATTR);
  }

  public static AuthUser require() {
    AuthUser u = orNull();
    if (u == null) throw ApiException.unauthorized();
    return u;
  }

  public static String ip(HttpServletRequest req) {
    return req.getRemoteAddr();
  }
}
