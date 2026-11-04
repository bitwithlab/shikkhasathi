package com.shikkhasathi.user.dto;

import java.util.List;
import java.util.UUID;

/** Contract {@code Me} (GET /me and every endpoint that returns the current user). */
public record MeDto(
    UUID id,
    String name,
    String phone,
    String email,
    boolean emailVerified,
    String avatarUrl,
    List<String> roles,
    String locale,
    String theme,
    boolean lowDataMode,
    boolean onboardingCompleted,
    boolean hasPassword,
    Academic academic,
    Seller seller,
    String createdAt) {

  public record Academic(
      UUID institutionId,
      String institutionName,
      String institutionShort,
      UUID departmentId,
      String departmentName,
      String departmentShort,
      UUID programId,
      String programName,
      Integer semester,
      String batch,
      String section) {}

  /** verificationStatus: none | pending | verified | rejected */
  public record Seller(String slug, String verificationStatus) {}
}
