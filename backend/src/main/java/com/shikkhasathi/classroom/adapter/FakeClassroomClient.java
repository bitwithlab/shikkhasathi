package com.shikkhasathi.classroom.adapter;

import com.shikkhasathi.classroom.adapter.ClassroomModels.*;
import com.shikkhasathi.common.http.HttpTransport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Demo Classroom dataset for dev, demos and tests (same data as before). Offsets are relative to the anchor
 * (connection time) so the demo stays coherent and stable across syncs; items posted after "now - anchor" are not
 * visible yet, which simulates new work arriving through push / polling.
 */
public class FakeClassroomClient implements GoogleClassroomClient {
  static final long MIN = 60_000L;
  static final long HOUR = 3_600_000L;
  static final long DAY = 24 * HOUR;

  record FakeCourse(String id, String name, String section, String room, String teacher) {}

  /** due/posted are offsets from the anchor (ms); due null = no due date. */
  record FakeWork(String id, String course, String title, String description, String workType, Long due, long posted, Double points, String sub,
                  Double grade, boolean late, List<Material> materials) {}

  record FakeMaterial(String id, String course, String title, String description, long posted, List<Material> materials) {}

  record FakeAnnouncement(String id, String course, String text, String author, long posted) {}

  static final List<FakeCourse> COURSES = List.of(
      new FakeCourse("gc-cse2203", "CSE-2203 Data Structures", "Section A · Batch 56", "Room 402", "মো. রাকিবুল হাসান"),
      new FakeCourse("gc-cse2204", "CSE-2204 Data Structures Lab", "Section A1", "Lab 3", "নাফিসা তাবাসসুম"),
      new FakeCourse("gc-cse2205", "CSE-2205 OOP II (Java)", "Section A", "Room 305", "ফারহান আহমেদ"),
      new FakeCourse("gc-mat2101", "MAT-2101 Linear Algebra & Differential Equations", "Section A", "Room 210", "ড. মাহফুজুল আলম"),
      new FakeCourse("gc-eng2101", "ENG-2101 Technical Writing", "Section B", "Room 118", "Sharmin Akter"));

  private static Material pdf(String title) {
    return new Material("drive", title, "https://drive.google.com/", null, "application/pdf");
  }

  private static Material slides(String title) {
    return new Material("drive", title, "https://docs.google.com/presentation/", null, "application/vnd.google-apps.presentation");
  }

  private static Material yt(String title) {
    return new Material("youtube", title, "https://www.youtube.com/", null, null);
  }

  static final List<FakeWork> WORK = List.of(
      new FakeWork("cw-1", "gc-mat2101", "Assignment 3: Eigenvalues & Eigenvectors", "Chapter 4-এর ৫, ৯, ১২ নম্বর অঙ্ক। হাতে লিখে স্ক্যান করে জমা দিন।", "ASSIGNMENT", -20 * HOUR, -6 * DAY, 20.0, "NEW", null, false, List.of(pdf("Chapter 4 – Eigenvalues.pdf"))),
      new FakeWork("cw-2", "gc-cse2204", "Lab 2: BST Deletion Code", "Insert, delete (৩টি কেস) ও inorder traversal। GitHub লিংক জমা দিন।", "ASSIGNMENT", 9 * HOUR, -3 * DAY, 10.0, "NEW", null, false, List.of(pdf("Lab 2 – BST.pdf"))),
      new FakeWork("cw-3", "gc-cse2205", "Quiz 2: Java Multithreading", "Online quiz, ২০ মিনিট। Thread, Runnable, synchronized।", "MULTIPLE_CHOICE_QUESTION", 1 * DAY + 3 * HOUR, -2 * DAY, 15.0, "NEW", null, false, List.of(new Material("form", "Quiz 2 – Google Form", "https://forms.google.com/", null, null))),
      new FakeWork("cw-4", "gc-cse2203", "Problem Set 4: Heaps & Priority Queue", "Heapify, heap sort trace, k-th largest — ৬টি প্রশ্ন।", "ASSIGNMENT", 3 * DAY, -1 * DAY, 25.0, "NEW", null, false, List.of()),
      new FakeWork("cw-5", "gc-eng2101", "Essay Draft: Lab Report Structure", "৮০০ শব্দ। APA format।", "ASSIGNMENT", 5 * DAY, -4 * DAY, 30.0, "TURNED_IN", null, false, List.of()),
      new FakeWork("cw-6", "gc-cse2203", "Problem Set 3: AVL Rotations", "LL, RR, LR, RL rotation trace।", "ASSIGNMENT", -4 * DAY, -10 * DAY, 25.0, "RETURNED", 22.0, false, List.of()),
      new FakeWork("cw-7", "gc-cse2205", "Assignment 1: Inheritance & Interfaces", "UML + code।", "ASSIGNMENT", -7 * DAY, -14 * DAY, 20.0, "RETURNED", 17.0, true, List.of()),
      new FakeWork("cw-8", "gc-cse2204", "Lab 1: Linked List Operations", "", "ASSIGNMENT", -9 * DAY, -15 * DAY, 10.0, "TURNED_IN", null, false, List.of()),
      new FakeWork("cw-9", "gc-mat2101", "Class Test 2 Preparation Sheet", "CT-2: ২৪ নভেম্বর, Chapter 3–4।", "ASSIGNMENT", 9 * DAY, -1 * HOUR, null, "NEW", null, false, List.of()),
      // revealed after connect (simulates push / poll picking up new work)
      new FakeWork("cw-10", "gc-cse2203", "Midterm Practice: Graph Traversal", "BFS/DFS trace + shortest path। ঐচ্ছিক, তবে Midterm-এর জন্য ভালো প্রস্তুতি।", "ASSIGNMENT", 6 * DAY, 20 * MIN, 10.0, "NEW", null, false, List.of(slides("Graph Traversal – slides"))));

  static final List<FakeMaterial> MATERIALS = List.of(
      new FakeMaterial("mt-1", "gc-cse2203", "Lecture 9 slides: Heaps", "আজকের ক্লাসের স্লাইড।", -1 * DAY, List.of(slides("Lecture 9 – Heaps"))),
      new FakeMaterial("mt-2", "gc-cse2203", "Previous year midterm questions (2023, 2024)", "রিভিশনের জন্য।", -2 * DAY, List.of(pdf("DS Midterm 2023.pdf"), pdf("DS Midterm 2024.pdf"))),
      new FakeMaterial("mt-3", "gc-mat2101", "Chapter 4 handnote", "", -5 * DAY, List.of(pdf("Linear Algebra Ch4 handnote.pdf"))),
      new FakeMaterial("mt-4", "gc-cse2205", "Thread lifecycle explained (video)", "", -3 * DAY, List.of(yt("Java Thread Lifecycle"))),
      new FakeMaterial("mt-5", "gc-cse2204", "Lab 3 starter code", "Lab 3-এর জন্য starter repo।", 2 * HOUR, List.of(new Material("link", "github.com/mu-cse/ds-lab3", "https://github.com/", null, null))));

  static final List<FakeAnnouncement> ANNOUNCEMENTS = List.of(
      new FakeAnnouncement("an-1", "gc-cse2203", "আগামী রবিবারের ক্লাস Room 402-এর বদলে Room 501-এ হবে। Midterm সিলেবাস: Lecture 1–10।", "মো. রাকিবুল হাসান", -5 * HOUR),
      new FakeAnnouncement("an-2", "gc-cse2205", "Quiz 2 অনলাইনে হবে, সময় ২০ মিনিট। ল্যাপটপ বা মোবাইল চার্জ দিয়ে রাখবেন।", "ফারহান আহমেদ", -1 * DAY),
      new FakeAnnouncement("an-3", "gc-mat2101", "Makeup class: বৃহস্পতিবার বিকেল ৩টা, Room 210।", "ড. মাহফুজুল আলম", -2 * DAY),
      new FakeAnnouncement("an-4", "gc-eng2101", "Essay draft-এ feedback দেওয়া শুরু হয়েছে, শুক্রবারের মধ্যে সবাই পেয়ে যাবেন।", "Sharmin Akter", 6 * HOUR));

  static final List<String> FAKE_SCOPES = List.of("openid", "email", "profile", "classroom.courses.readonly", "classroom.coursework.me.readonly",
      "classroom.courseworkmaterials.readonly", "classroom.announcements.readonly", "classroom.push-notifications");

  private final Instant anchor;
  private final Supplier<Instant> now;

  /** @param anchor connection time; items with posted &gt; now - anchor are not visible yet */
  public FakeClassroomClient(Instant anchor, Supplier<Instant> now) {
    this.anchor = anchor;
    this.now = now;
  }

  public FakeClassroomClient(Instant anchor) {
    this(anchor, Instant::now);
  }

  @Override
  public boolean fake() {
    return true;
  }

  private boolean visible(long posted) {
    return anchor.toEpochMilli() + posted <= now.get().toEpochMilli();
  }

  private Instant at(long offset) {
    return Instant.ofEpochMilli(anchor.toEpochMilli() + offset);
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  @Override
  public String authUrl(String state, String codeChallenge, String redirectUri, String loginHint) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("code", "fake-auth-code");
    q.put("state", state);
    return HttpTransport.withQuery(redirectUri, q);
  }

  @Override
  public TokenSet exchangeCode(String code, String redirectUri, String codeVerifier) {
    if (!code.startsWith("fake")) throw new ClassroomError(ClassroomError.Kind.REAUTH, "invalid_grant");
    String sub;
    try {
      sub = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(code.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    return new TokenSet("fake-refresh-" + sub, "fake-access-" + sub, Instant.now().plusMillis(HOUR), FAKE_SCOPES, "fake-" + sub, "student.demo@metrouni.edu.bd");
  }

  @Override
  public AccessToken refresh(String refreshToken) {
    if ("fake-revoked".equals(refreshToken)) throw new ClassroomError(ClassroomError.Kind.REAUTH, "invalid_grant");
    return new AccessToken("fake-access-" + System.currentTimeMillis(), Instant.now().plusMillis(HOUR));
  }

  @Override
  public void revoke(String token) {}

  @Override
  public List<GcCourse> listCourses(String accessToken) {
    return COURSES.stream()
        .map(c -> new GcCourse(c.id(), c.name(), c.section(), c.room(), c.name(), "https://classroom.google.com/c/" + c.id(), List.of(c.teacher()), "ACTIVE", at(-30 * DAY)))
        .toList();
  }

  @Override
  public List<GcCourseWork> listCourseWork(String accessToken, String courseId) {
    return WORK.stream().filter(w -> w.course().equals(courseId) && visible(w.posted()))
        .map(w -> new GcCourseWork(w.id(), courseId, w.title(), emptyToNull(w.description()), w.workType(), w.due() == null ? null : at(w.due()), w.points(),
            w.materials(), "https://classroom.google.com/c/" + courseId + "/a/" + w.id() + "/details", "PUBLISHED", at(w.posted()), at(w.posted())))
        .toList();
  }

  @Override
  public List<GcSubmission> listSubmissions(String accessToken, String courseId) {
    return WORK.stream().filter(w -> w.course().equals(courseId) && visible(w.posted()))
        .map(w -> new GcSubmission("sub-" + w.id(), courseId, w.id(), w.sub(), w.late(), w.grade(),
            "https://classroom.google.com/c/" + courseId + "/a/" + w.id() + "/details",
            at("RETURNED".equals(w.sub()) && w.due() != null ? w.due() + 2 * DAY : w.posted())))
        .toList();
  }

  @Override
  public List<GcMaterialPost> listMaterials(String accessToken, String courseId) {
    return MATERIALS.stream().filter(m -> m.course().equals(courseId) && visible(m.posted()))
        .map(m -> new GcMaterialPost(m.id(), courseId, m.title(), emptyToNull(m.description()), m.materials(),
            "https://classroom.google.com/c/" + courseId + "/m/" + m.id() + "/details", at(m.posted()), at(m.posted())))
        .toList();
  }

  @Override
  public List<GcAnnouncement> listAnnouncements(String accessToken, String courseId) {
    return ANNOUNCEMENTS.stream().filter(a -> a.course().equals(courseId) && visible(a.posted()))
        .map(a -> new GcAnnouncement(a.id(), courseId, a.text(), a.author(), List.of(), "https://classroom.google.com/c/" + courseId + "/p/" + a.id(), at(a.posted()), at(a.posted())))
        .toList();
  }

  @Override
  public Registration registerCourseWorkPush(String accessToken, String courseId, String topicName) {
    return new Registration("fake-reg-" + courseId + "-" + anchor.toEpochMilli(), now.get().plusMillis(7 * DAY));
  }

  @Override
  public void deleteRegistration(String accessToken, String registrationId) {}
}
