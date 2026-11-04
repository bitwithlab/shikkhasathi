package com.shikkhasathi.security;

import java.lang.annotation.*;

/** Sets a Cache-Control header on successful responses (e.g. {@code public, max-age=60}). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheControl {
  String value();
}
