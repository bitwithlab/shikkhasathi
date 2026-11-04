package com.shikkhasathi.student.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cached home dashboard per user (Redis {@code dash:<userId>}, 60 s). Any module whose data appears on the
 * dashboard (orders, learning progress, exam plans, Classroom sync) calls {@link #invalidate} after writes.
 */
@Component
public class DashboardCache {
  public static final long TTL_SEC = 60;
  private static final Logger log = LoggerFactory.getLogger(DashboardCache.class);
  private final StringRedisTemplate redis;

  public DashboardCache(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public static String key(UUID userId) {
    return "dash:" + userId;
  }

  /** Best effort, like before: a Redis failure never fails the caller. */
  public void invalidate(UUID userId) {
    try {
      redis.delete(key(userId));
    } catch (RuntimeException e) {
      log.debug("dashboard invalidate failed: {}", e.getMessage());
    }
  }
}
