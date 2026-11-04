package com.shikkhasathi.security;

import java.lang.annotation.*;

/**
 * Required auth level for a controller method (same levels as the previous API contracts). Enforced by
 * {@link AuthInterceptor} BEFORE request validation, so a student calling an admin route gets 403, never 422.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auth {
  Level value();

  enum Level {
    /** anyone; a malformed bearer token is still rejected with 401 */
    PUBLIC,
    /** anyone; a bad token is ignored and the caller is treated as a guest */
    OPTIONAL,
    USER,
    SELLER,
    /** seller with verification_status = verified */
    VERIFIED_SELLER,
    ADMIN
  }
}
