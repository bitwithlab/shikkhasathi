package com.shikkhasathi.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shikkhasathi.user.dto.MeDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

/**
 * Request/response records of the {@code auth} contract group. Normalisation that the previous Zod schemas did with
 * transforms (trim, lower-case e-mail, Bangladeshi phone → {@code +8801XXXXXXXXX}) happens in the compact
 * constructors, before Bean Validation runs, so the patterns check the normalised value exactly as before.
 */
public final class AuthDtos {
  private AuthDtos() {}

  /** Same regex as zod's {@code z.email()}. */
  public static final String EMAIL_RE = "^(?!\\.)(?!.*\\.\\.)([A-Za-z0-9_'+\\-.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$";
  public static final String PHONE_RE = "^\\+8801[3-9]\\d{8}$";
  public static final String PLATFORM_RE = "web|android|ios";
  static final String EMAIL_MSG = "সঠিক ইমেইল দিন";
  static final String PHONE_MSG = "সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)";
  static final String PASSWORD_MSG = "পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে";
  static final String CODE_RE = "^\\d{6}$";

  /** "01712-345678" / "8801712345678" / "+880 1712 345678" → "+8801712345678" (validated afterwards). */
  public static String normalizePhone(String v) {
    if (v == null) return null;
    String s = v.trim().replaceAll("[\\s-]", "");
    if (s.startsWith("+880")) return s;
    if (s.startsWith("880")) return "+" + s;
    if (s.startsWith("0")) return "+88" + s;
    return s;
  }

  public static String normalizeEmail(String v) {
    return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
  }

  static String trim(String v) {
    return v == null ? null : v.trim();
  }

  static String platformOrWeb(String p) {
    return p == null ? "web" : p;
  }

  /** Device fields shared by every sign-in body. */
  public interface DeviceFields {
    String deviceId();

    String deviceName();

    String platform();
  }

  // ---------------------------------------------------------------- requests

  public record OtpRequestBody(@NotNull @Pattern(regexp = PHONE_RE, message = PHONE_MSG) String phone) {
    public OtpRequestBody {
      phone = normalizePhone(phone);
    }
  }

  public record OtpVerifyBody(
      @Size(min = 4, max = 128) String deviceId,
      @Size(max = 120) String deviceName,
      @Pattern(regexp = PLATFORM_RE) String platform,
      @NotNull @Pattern(regexp = PHONE_RE, message = PHONE_MSG) String phone,
      @NotNull @Pattern(regexp = CODE_RE, message = "৬ সংখ্যার কোড দিন") String code,
      @Size(min = 2, max = 80) String name)
      implements DeviceFields {
    public OtpVerifyBody {
      platform = platformOrWeb(platform);
      phone = normalizePhone(phone);
      name = trim(name);
    }
  }

  public record LoginBody(
      @Size(min = 4, max = 128) String deviceId,
      @Size(max = 120) String deviceName,
      @Pattern(regexp = PLATFORM_RE) String platform,
      @NotNull @Pattern(regexp = EMAIL_RE, message = EMAIL_MSG) String email,
      @NotNull @Size(min = 1, max = 128) String password)
      implements DeviceFields {
    public LoginBody {
      platform = platformOrWeb(platform);
      email = normalizeEmail(email);
    }
  }

  public record RegisterBody(
      @Size(min = 4, max = 128) String deviceId,
      @Size(max = 120) String deviceName,
      @Pattern(regexp = PLATFORM_RE) String platform,
      @NotNull @Size(min = 2, message = "নাম লিখুন") @Size(max = 80) String name,
      @NotNull @Pattern(regexp = EMAIL_RE, message = EMAIL_MSG) String email,
      @NotNull @Size(min = 8, message = PASSWORD_MSG) @Size(max = 128) String password,
      Boolean asSeller)
      implements DeviceFields {
    public RegisterBody {
      platform = platformOrWeb(platform);
      name = trim(name);
      email = normalizeEmail(email);
      if (asSeller == null) asSeller = false;
    }
  }

  public record GoogleLoginBody(
      @Size(min = 4, max = 128) String deviceId,
      @Size(max = 120) String deviceName,
      @Pattern(regexp = PLATFORM_RE) String platform,
      @NotNull @Size(min = 10) String idToken)
      implements DeviceFields {
    public GoogleLoginBody {
      platform = platformOrWeb(platform);
    }
  }

  public record RefreshBody(String refreshToken) {}

  public record EmailOtpRequestBody(
      @NotNull @Pattern(regexp = EMAIL_RE, message = EMAIL_MSG) String email,
      @NotNull @Pattern(regexp = "email_verify|password_reset") String purpose) {
    public EmailOtpRequestBody {
      email = normalizeEmail(email);
    }
  }

  public record PasswordResetBody(
      @NotNull @Pattern(regexp = EMAIL_RE, message = EMAIL_MSG) String email,
      @NotNull @Pattern(regexp = CODE_RE) String code,
      @NotNull @Size(min = 8, message = PASSWORD_MSG) @Size(max = 128) String password) {
    public PasswordResetBody {
      email = normalizeEmail(email);
    }
  }

  public record EmailVerifyBody(@NotNull @Pattern(regexp = CODE_RE) String code) {}

  /**
   * PATCH /me. A class rather than a record so that {@code avatarUrl} can tell "absent" (keep) from an explicit JSON
   * {@code null} (clear): Jackson only calls a setter for properties present in the body.
   */
  public static final class UpdateMeBody {
    @Size(min = 2, max = 80) private String name;
    @Pattern(regexp = "bn|en") private String locale;
    @Pattern(regexp = "system|light|dark") private String theme;
    private Boolean lowDataMode;
    @Pattern(regexp = EMAIL_RE, message = EMAIL_MSG) private String email;
    @URL private String avatarUrl;
    private boolean avatarUrlPresent;

    public String getName() { return name; }
    public void setName(String v) { name = trim(v); }
    public String getLocale() { return locale; }
    public void setLocale(String v) { locale = v; }
    public String getTheme() { return theme; }
    public void setTheme(String v) { theme = v; }
    public Boolean getLowDataMode() { return lowDataMode; }
    public void setLowDataMode(Boolean v) { lowDataMode = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = normalizeEmail(v); }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { avatarUrl = v; avatarUrlPresent = true; }
    /** True when the body contained {@code avatarUrl} (a string or {@code null}). */
    public boolean avatarUrlPresent() { return avatarUrlPresent; }
  }

  public record ChangePasswordBody(
      @Size(max = 128) String currentPassword,
      @NotNull @Size(min = 8, message = PASSWORD_MSG) @Size(max = 128) String newPassword) {}

  // ---------------------------------------------------------------- responses

  public record OtpRequestResponse(
      boolean sent, long expiresInSec, long resendInSec, @JsonInclude(JsonInclude.Include.NON_NULL) String devCode) {}

  /** {@code refreshToken} is only in the body for mobile clients; web gets an httpOnly cookie (field absent). */
  public record AuthTokens(
      String accessToken,
      String accessTokenExpiresAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken,
      MeDto user,
      boolean isNewUser) {}

  public record SessionItem(UUID id, String deviceName, String platform, String lastSeenAt, String createdAt, boolean current) {}

  public record SessionList(List<SessionItem> items) {}

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }
}
