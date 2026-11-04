package com.shikkhasathi.classroom.adapter;

import java.time.Instant;
import java.util.List;

/** Normalized Google Classroom shapes (same fields as the previous API's GcCourse, GcCourseWork, …). */
public final class ClassroomModels {
  private ClassroomModels() {}

  /** Contract {@code Material}: kind = drive | link | youtube | form. */
  public record Material(String kind, String title, String url, String thumbnailUrl, String mime) {}

  public record GcCourse(String id, String name, String section, String room, String descriptionHeading, String alternateLink,
                         List<String> teacherNames, String state, Instant updateTime) {}

  public record GcCourseWork(String id, String courseId, String title, String description, String workType, Instant dueAt, Double maxPoints,
                             List<Material> materials, String alternateLink, String state, Instant creationTime, Instant updateTime) {}

  /** state: NEW | CREATED | TURNED_IN | RETURNED | RECLAIMED_BY_STUDENT */
  public record GcSubmission(String id, String courseId, String courseWorkId, String state, boolean late, Double assignedGrade,
                             String alternateLink, Instant updateTime) {}

  public record GcMaterialPost(String id, String courseId, String title, String description, List<Material> materials, String alternateLink,
                               Instant creationTime, Instant updateTime) {}

  public record GcAnnouncement(String id, String courseId, String text, String authorName, List<Material> materials, String alternateLink,
                               Instant creationTime, Instant updateTime) {}

  public record TokenSet(String refreshToken, String accessToken, Instant expiresAt, List<String> scopes, String googleUserId, String email) {}

  public record AccessToken(String accessToken, Instant expiresAt) {}

  public record Registration(String registrationId, Instant expiresAt) {}
}
