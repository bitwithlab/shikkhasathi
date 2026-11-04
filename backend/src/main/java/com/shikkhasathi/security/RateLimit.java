package com.shikkhasathi.security;

import java.lang.annotation.*;

/** Per-route, per-client fixed-window limit (Redis). The global limit is 300/min per IP on /api. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
  int max();

  /** window in seconds */
  int window();
}
