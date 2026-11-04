package com.shikkhasathi.classroom.adapter;

import com.shikkhasathi.classroom.adapter.ClassroomModels.*;
import java.util.List;

/** Read-only Classroom access (+ OAuth plumbing). Real = Google REST, Fake = seeded demo dataset. */
public interface GoogleClassroomClient {
  boolean fake();

  /** @param loginHint nullable */
  String authUrl(String state, String codeChallenge, String redirectUri, String loginHint);

  /** @param redirectUri nullable; @param codeVerifier nullable */
  TokenSet exchangeCode(String code, String redirectUri, String codeVerifier);

  AccessToken refresh(String refreshToken);

  void revoke(String token);

  List<GcCourse> listCourses(String accessToken);

  List<GcCourseWork> listCourseWork(String accessToken, String courseId);

  List<GcSubmission> listSubmissions(String accessToken, String courseId);

  List<GcMaterialPost> listMaterials(String accessToken, String courseId);

  List<GcAnnouncement> listAnnouncements(String accessToken, String courseId);

  Registration registerCourseWorkPush(String accessToken, String courseId, String topicName);

  void deleteRegistration(String accessToken, String registrationId);
}
