package com.shikkhasathi.payment.service;

import java.math.BigDecimal;

/** Number formatting exactly like JS template literals, used in Bangla messages (e.g. {@code ৳299.5}). */
public final class Money {
  private Money() {}

  /** JS {@code String(n)}: 48 → "48", 299.5 → "299.5". */
  public static String num(double d) {
    if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
    return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
  }

  /** paisa → taka as JS would print {@code paisa / 100}. */
  public static String taka(long paisa) {
    return num(paisa / 100.0);
  }
}
