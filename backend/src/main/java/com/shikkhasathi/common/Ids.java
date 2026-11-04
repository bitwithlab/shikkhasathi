package com.shikkhasathi.common;

import java.security.SecureRandom;
import java.util.UUID;

/** UUIDv7 (time-ordered) — same id scheme as the previous API, so cursor ordering by id stays meaningful. */
public final class Ids {
  private static final SecureRandom RNG = new SecureRandom();

  private Ids() {}

  public static UUID uuidv7() {
    long ms = System.currentTimeMillis();
    long randA = RNG.nextInt(1 << 12);
    long msb = (ms << 16) | (0x7L << 12) | randA;
    long lsb = (RNG.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }
}
