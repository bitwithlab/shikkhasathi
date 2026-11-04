package com.shikkhasathi.teacher.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Request/response records of the {@code sellerApply} and {@code adminSellers} contract groups. */
public final class SellerDtos {
  private SellerDtos() {}

  public static final String SLUG = "^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$";
  public static final String SLUG_MESSAGE = "ছোট হাতের ইংরেজি অক্ষর, সংখ্যা ও - দিয়ে ৩–৪০ অক্ষর";
  /** same rule as the previous contracts' z.email() */
  public static final String EMAIL = "^(?!\\.)(?!.*\\.\\.)([A-Za-z0-9_'+\\-\\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$";
  public static final String EMAIL_MESSAGE = "সঠিক ইমেইল দিন";
  public static final String URL = "^[A-Za-z][A-Za-z0-9+.\\-]*:[^\\s]+$";
  public static final String METHODS = "university_email|id_card|linkedin|reference";
  public static final String PAYOUT_METHODS = "bkash|nagad|bank";
  public static final String STATUSES = "none|pending|verified|rejected";

  static String trim(String s) {
    return s == null ? null : s.strip();
  }

  static String lower(String s) {
    return s == null ? null : s.strip().toLowerCase(Locale.ROOT);
  }

  // ---------------------------------------------------------------- inputs

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Evidence(
      @Pattern(regexp = EMAIL, message = EMAIL_MESSAGE) String universityEmail,
      @Size(max = 300) String idCardKey,
      @Pattern(regexp = URL) String linkedinUrl,
      @Size(max = 120) String referenceName,
      @Size(max = 120) String referenceContact) {
    public Evidence {
      universityEmail = lower(universityEmail);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ApplyBody(
      @NotNull @Size(min = 2, max = 80) String displayName,
      @NotNull @Pattern(regexp = SLUG, message = SLUG_MESSAGE) String slug,
      @Size(max = 120) String headline,
      @Size(max = 2000) String bio,
      UUID institutionId,
      @NotNull @Pattern(regexp = METHODS) String method,
      @NotNull @Valid Evidence evidence,
      @NotNull @Pattern(regexp = PAYOUT_METHODS) String payoutMethod,
      @NotNull @Size(min = 6, max = 60) String payoutAccount) {
    public static final List<String> NULLABLE = List.of("institutionId");
    public static final List<String> REQUIRED = List.of("institutionId");

    public ApplyBody {
      displayName = trim(displayName);
      slug = lower(slug);
      headline = trim(headline);
      bio = trim(bio);
      payoutAccount = trim(payoutAccount);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmailCodeBody(@NotNull @Pattern(regexp = EMAIL, message = EMAIL_MESSAGE) String email) {
    public EmailCodeBody {
      email = lower(email);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmailConfirmBody(@NotNull @Pattern(regexp = EMAIL, message = EMAIL_MESSAGE) String email, @NotNull @Pattern(regexp = "^\\d{6}$") String code) {
    public EmailConfirmBody {
      email = lower(email);
    }
  }

  /** PATCH body; presence matters (absent = unchanged, null = cleared for headline/bio/coverUrl). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StorefrontPatch(
      @Pattern(regexp = SLUG, message = SLUG_MESSAGE) String slug,
      @Size(min = 2, max = 80) String displayName,
      @Size(max = 120) String headline,
      @Size(max = 2000) String bio,
      @Pattern(regexp = URL) String coverUrl) {
    public static final List<String> NULLABLE = List.of("headline", "bio", "coverUrl");

    public StorefrontPatch {
      slug = lower(slug);
      displayName = trim(displayName);
      headline = trim(headline);
      bio = trim(bio);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SellerDecision(@NotNull @Pattern(regexp = "verified|rejected") String decision, @Size(max = 500) String note, @Min(0) @Max(50) Integer commissionRate) {}

  // ---------------------------------------------------------------- outputs

  public record SellerProfileDto(UUID userId, String slug, String displayName, String headline, String bio, String coverUrl, UUID institutionId, String institutionName,
                                 String verificationStatus, String verificationMethod, String verificationNote, boolean universityEmailVerified, String payoutMethod,
                                 String payoutAccountMasked, Integer commissionRate, String verifiedAt) {}

  public record ProfileEnvelope(SellerProfileDto profile) {}

  public record AdminSellerItem(UUID userId, String slug, String displayName, String headline, String bio, String coverUrl, UUID institutionId, String institutionName,
                                String verificationStatus, String verificationMethod, String verificationNote, boolean universityEmailVerified, String payoutMethod,
                                String payoutAccountMasked, Integer commissionRate, String verifiedAt,
                                String name, String email, String phone, JsonNode evidence, String idCardUrl, String appliedAt, int courseCount) {
    public static AdminSellerItem of(SellerProfileDto p, String name, String email, String phone, JsonNode evidence, String idCardUrl, String appliedAt, int courseCount) {
      return new AdminSellerItem(p.userId(), p.slug(), p.displayName(), p.headline(), p.bio(), p.coverUrl(), p.institutionId(), p.institutionName(), p.verificationStatus(),
          p.verificationMethod(), p.verificationNote(), p.universityEmailVerified(), p.payoutMethod(), p.payoutAccountMasked(), p.commissionRate(), p.verifiedAt(),
          name, email, phone, evidence, idCardUrl, appliedAt, courseCount);
    }
  }

  public record AdminSellerPage(List<AdminSellerItem> items, String nextCursor) {}

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }
}
