package com.shikkhasathi.security;

import com.shikkhasathi.common.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Inject the caller: {@code AuthUser user} (required — 401 if missing) or {@code @Nullable AuthUser user}
 * / {@code Optional<AuthUser>} on OPTIONAL routes.
 */
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter p) {
    return p.getParameterType() == AuthUser.class || (p.getParameterType() == java.util.Optional.class && p.getGenericParameterType().getTypeName().contains("AuthUser"));
  }

  @Override
  public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest req, WebDataBinderFactory f) {
    AuthUser u = (AuthUser) req.getAttribute(CurrentUser.ATTR, NativeWebRequest.SCOPE_REQUEST);
    if (p.getParameterType() == java.util.Optional.class) return java.util.Optional.ofNullable(u);
    if (u == null && !p.isOptional() && !p.hasParameterAnnotation(org.springframework.lang.Nullable.class)) throw ApiException.unauthorized();
    return u;
  }
}
