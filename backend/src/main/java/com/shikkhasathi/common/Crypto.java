package com.shikkhasathi.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM in the previous API's exact format {@code v1:<iv b64>:<tag b64>:<ciphertext b64>}, so integration
 * secrets and Google tokens already in the database keep decrypting after the migration.
 */
public final class Crypto {
  private static final SecureRandom RNG = new SecureRandom();
  private static final String VERSION = "v1";
  private final byte[] key;
  private final String refreshSecret;

  public Crypto(String rawKey, String refreshSecret) {
    this.key = deriveKey(rawKey);
    this.refreshSecret = refreshSecret;
  }

  static byte[] deriveKey(String raw) {
    if (raw.matches("(?i)^[0-9a-f]{64}$")) return HexFormat.of().parseHex(raw);
    if (raw.matches("^[A-Za-z0-9+/=]+$")) {
      try {
        byte[] b = Base64.getDecoder().decode(raw);
        if (b.length == 32) return b;
      } catch (IllegalArgumentException ignored) {
        // fallthrough
      }
    }
    // dev keys: stretch to 32 bytes (production must provide a real 32-byte key)
    return sha256Bytes(raw);
  }

  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[12];
      RNG.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      // Java appends the 16-byte tag to the ciphertext; the stored format keeps them separate
      byte[] ct = java.util.Arrays.copyOfRange(out, 0, out.length - 16);
      byte[] tag = java.util.Arrays.copyOfRange(out, out.length - 16, out.length);
      var b = Base64.getEncoder();
      return String.join(":", VERSION, b.encodeToString(iv), b.encodeToString(tag), b.encodeToString(ct));
    } catch (Exception e) {
      throw new IllegalStateException("encrypt failed", e);
    }
  }

  public String decrypt(String payload) {
    String[] p = payload.split(":");
    if (p.length != 4 || !VERSION.equals(p[0])) throw new IllegalArgumentException("Unsupported ciphertext");
    try {
      var d = Base64.getDecoder();
      byte[] iv = d.decode(p[1]), tag = d.decode(p[2]), ct = d.decode(p[3]);
      byte[] in = new byte[ct.length + tag.length];
      System.arraycopy(ct, 0, in, 0, ct.length);
      System.arraycopy(tag, 0, in, ct.length, tag.length);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      return new String(c.doFinal(in), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("decrypt failed", e);
    }
  }

  public static byte[] sha256Bytes(String v) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String sha256(String v) {
    return HexFormat.of().formatHex(sha256Bytes(v));
  }

  /** HMAC-SHA256 hex with the refresh secret (refresh tokens, OTP codes) — same as before. */
  public String hmac(String v) {
    return hmac(v, refreshSecret);
  }

  public static String hmac(String v, String secret) {
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(m.doFinal(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String randomToken(int bytes) {
    byte[] b = new byte[bytes];
    RNG.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String randomDigits(int n) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < n; i++) s.append(RNG.nextInt(10));
    return s.toString();
  }

  public static boolean safeEqual(String a, String b) {
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  /** {@code ••••1234} — last 4 visible, never more than a quarter of the secret. */
  public static String mask(String secret) {
    if (secret == null || secret.isEmpty()) return null;
    return "••••" + (secret.length() >= 12 ? secret.substring(secret.length() - 4) : "");
  }
}
