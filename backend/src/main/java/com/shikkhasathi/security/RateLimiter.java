package com.shikkhasathi.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Fixed-window counter in Redis (same semantics as the previous hitLimit). */
@Component
public class RateLimiter {
  private final StringRedisTemplate redis;

  public RateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public record Result(boolean allowed, long count, long retryAfterSec) {}

  public Result hit(String key, int max, int windowSec) {
    Long n = redis.opsForValue().increment(key);
    long count = n == null ? 1 : n;
    if (count == 1) redis.expire(key, Duration.ofSeconds(windowSec));
    Long ttl = redis.getExpire(key);
    return new Result(count <= max, count, ttl != null && ttl > 0 ? ttl : windowSec);
  }
}
