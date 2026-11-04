package com.shikkhasathi.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.exam.service.ExamService;
import com.shikkhasathi.student.service.DashboardCache;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.Resp;
import com.shikkhasathi.support.TestUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

/** Port of the previous API's exam.test.ts (Exam Mode, mock tests, daily nudge, seller authoring). */
class ExamTest extends IntegrationTest {
  @Autowired ExamService exam;
  @Autowired StringRedisTemplate redis;

  static String inDays(int n) {
    return Instant.now().plusMillis(n * 86_400_000L).toString();
  }

  UUID courseId(String slug) {
    return contentJdbc.queryForObject("select id from courses where slug = :s", Map.of("s", slug), UUID.class);
  }

  void enroll(TestUser u, UUID courseId) {
    coreJdbc.update("insert into enrollments (id, student_id, course_id, status) values (:id, :s, :c, 'active')", Map.of("id", Ids.uuidv7(), "s", u.id(), "c", courseId));
  }

  /** stands in for POST /lessons/:id/complete (learning module) */
  void completeLesson(TestUser u, UUID courseId, String lessonId) {
    coreJdbc.update("insert into lesson_progress (id, student_id, course_id, lesson_id, completed_at) values (:id, :s, :c, :l, now()) on conflict (student_id, lesson_id) do update set completed_at = now()",
        Map.of("id", Ids.uuidv7(), "s", u.id(), "c", courseId, "l", UUID.fromString(lessonId)));
  }

  record Setup(UUID ds, TestUser student) {}

  Setup setup() {
    seed();
    UUID ds = courseId("data-structures-midterm-crash-cse-2203");
    TestUser student = createUser("তানভীর আহমেদ");
    enroll(student, ds);
    return new Setup(ds, student);
  }

  static Map<String, Object> plan(String subject, String code, int days, List<UUID> courseIds) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("subjectName", subject);
    if (code != null) m.put("courseCode", code);
    m.put("examType", "midterm");
    m.put("examDate", inDays(days));
    m.put("courseIds", courseIds);
    return m;
  }

  TestUser sellerOf(UUID courseId) {
    UUID sellerId = contentJdbc.queryForObject("select seller_id from courses where id = :id", Map.of("id", courseId), UUID.class);
    var row = coreJdbc.queryForMap("select name, email, phone, array_to_string(roles, ',') as roles from users where id = :id", Map.of("id", sellerId));
    return login(sellerId, (String) row.get("name"), (String) row.get("email"), (String) row.get("phone"), List.of(((String) row.get("roles")).split(",")));
  }

  @Test
  void createsPlanOnlyWithOwnedCoursesDetailHasCountdownDailyPlanSyllabusResourcesReadiness() {
    var s = setup();
    UUID algo = courseId("algorithm-analysis-design-cse-2201");
    post("/exam-plans", plan("Algorithms", null, 5, List.of(algo)), s.student()).expect(403);
    post("/exam-plans", plan("Past", null, -3, List.of()), s.student()).expect(400);
    redis.opsForValue().set(DashboardCache.key(s.student().id()), "{}");
    var c = contract("exam.create", post("/exam-plans", plan("Data Structures", "CSE-2203", 6, List.of(s.ds())), s.student()));
    assertThat(c.status()).isEqualTo(201);
    JsonNode p = c.json();
    assertThat(p.get("daysLeft").asInt()).isGreaterThanOrEqualTo(5);
    JsonNode d = contract("exam.get", get("/exam-plans/" + p.get("id").asString(), s.student())).json();
    JsonNode todayItems = d.get("today").get("items");
    assertThat(todayItems.size()).isGreaterThan(0);
    assertThat(todayItems.get(0).get("kind").asString()).isEqualTo("lesson");
    assertThat(todayItems.get(0).get("isImportant").asBoolean()).isTrue(); // ⭐ first
    JsonNode last = d.get("days").get(d.get("days").size() - 1);
    assertThat(last.get("isRevision").asBoolean()).isTrue();
    assertThat(last.get("items").valueStream().anyMatch(i -> i.get("kind").asString().equals("mock"))).isTrue();
    assertThat(d.get("syllabus").size()).isGreaterThan(0);
    assertThat(d.get("syllabus").valueStream().allMatch(x -> x.get("status").asString().equals("todo"))).isTrue();
    assertThat(d.get("mockTests").size()).isEqualTo(2);
    assertThat(d.get("flashcards").get("total").asInt()).isEqualTo(12);
    assertThat(d.get("flashcards").get("known").asInt()).isEqualTo(0);
    assertThat(d.get("previousQuestions").size()).isGreaterThan(0);
    assertThat(d.get("revisionUnlocked").asBoolean()).isFalse();
    assertThat(d.get("readiness").asInt()).isEqualTo(0);
    // other students can't see it
    var other = createUser("Other");
    get("/exam-plans/" + p.get("id").asString(), other).expect(403);
    // the dashboard (countdown widget) is invalidated
    assertThat(redis.hasKey(DashboardCache.key(s.student().id()))).isFalse();
    // listed, upcoming first
    JsonNode list = contract("exam.plans", get("/exam-plans", s.student())).json();
    assertThat(list.get("items").get(0).get("id").asString()).isEqualTo(p.get("id").asString());
  }

  @Test
  void readinessRisesWithLessonsFlashcardsAndMockScoreSyllabusStatusMoves() {
    var s = setup();
    JsonNode p = post("/exam-plans", plan("DS", null, 4, List.of(s.ds())), s.student()).expect(201).json();
    String pid = p.get("id").asString();
    JsonNode d0 = get("/exam-plans/" + pid, s.student()).json();
    JsonNode first = d0.get("today").get("items").get(0);
    completeLesson(s.student(), s.ds(), first.get("lessonId").asString());
    JsonNode cards = contract("exam.flashcards", get("/exam-plans/" + pid + "/flashcards", s.student())).json().get("items");
    assertThat(cards.size()).isEqualTo(12);
    for (int i = 0; i < 6; i++) contract("exam.reviewCard", post("/flashcards/" + cards.get(i).get("id").asString() + "/review", Map.of("known", true), s.student()));
    post("/flashcards/" + cards.get(6).get("id").asString() + "/review", Map.of("known", false), s.student()).expect(200);
    JsonNode again = get("/exam-plans/" + pid + "/flashcards", s.student()).json().get("items");
    assertThat(again.get(0).get("known").asBoolean()).isFalse(); // not-yet-known first
    assertThat(again.get(0).get("known").isNull()).isFalse();
    JsonNode d1 = contract("exam.get", get("/exam-plans/" + pid, s.student())).json();
    assertThat(d1.get("today").get("items").get(0).get("lessonId").asString()).isEqualTo(first.get("lessonId").asString()); // stays in today's list, ticked
    assertThat(d1.get("today").get("items").get(0).get("done").asBoolean()).isTrue();
    assertThat(d1.get("flashcards").get("known").asInt()).isEqualTo(6);
    assertThat(d1.get("readinessBreakdown").get("flashcards").asInt()).isEqualTo(50);
    assertThat(d1.get("readiness").asInt()).isGreaterThan(0);
    assertThat(d1.get("syllabus").valueStream().anyMatch(x -> !x.get("status").asString().equals("todo"))).isTrue();
    Integer stored = coreJdbc.queryForObject("select readiness from exam_plans where id = :id", Map.of("id", UUID.fromString(pid)), Integer.class);
    assertThat(stored).isEqualTo(d1.get("readiness").asInt());
    // flashcards of courses not bought are refused
    var outsider = createUser("Outsider");
    post("/flashcards/" + cards.get(0).get("id").asString() + "/review", Map.of("known", true), outsider).expect(403);
  }

  @Test
  void mockTestShuffledAttemptResumeAutosaveSubmitScoreChapterBreakdownReviewRetryIsNewAttempt() {
    var s = setup();
    JsonNode list = contract("exam.mockTests", get("/mock-tests", s.student())).json().get("items");
    JsonNode t = list.valueStream().filter(x -> x.get("courseId").asString().equals(s.ds().toString()) && x.get("questionCount").asInt() == 12).findFirst().orElse(null);
    assertThat(t).isNotNull();
    String tid = t.get("id").asString();
    Resp a = contract("exam.start", post("/mock-tests/" + tid + "/attempts", null, s.student()));
    assertThat(a.status()).isEqualTo(201);
    JsonNode att = a.json();
    String aid = att.get("id").asString();
    assertThat(att.get("questions").size()).isEqualTo(12);
    assertThat(a.body()).doesNotContain("isCorrect");
    // resume returns the same attempt
    assertThat(post("/mock-tests/" + tid + "/attempts", null, s.student()).json().get("id").asString()).isEqualTo(aid);
    Map<UUID, UUID> correct = new HashMap<>();
    contentJdbc.query("select o.id, o.question_id from mock_options o join mock_questions q on q.id = o.question_id where q.test_id = :t and o.is_correct = true",
        Map.of("t", UUID.fromString(tid)), rs -> { correct.put(rs.getObject(2, UUID.class), rs.getObject(1, UUID.class)); });
    // answer 8 correctly, 2 wrong, 2 skipped; flag one
    JsonNode qs = att.get("questions");
    for (int i = 0; i < 10; i++) {
      JsonNode q = qs.get(i);
      String right = correct.get(UUID.fromString(q.get("id").asString())).toString();
      String wrong = q.get("options").valueStream().map(o -> o.get("id").asString()).filter(o -> !o.equals(right)).findFirst().orElseThrow();
      Map<String, Object> body = new HashMap<>();
      body.put("questionId", q.get("id").asString());
      body.put("optionId", i < 8 ? right : wrong);
      body.put("flagged", i == 3);
      contract("exam.answer", put("/mock-attempts/" + aid + "/answers", body, s.student())).expect(200);
    }
    put("/mock-attempts/" + aid + "/answers", Map.of("questionId", qs.get(0).get("id").asString(), "optionId", qs.get(1).get("options").get(0).get("id").asString()), s.student()).expect(400);
    JsonNode resumed = contract("exam.attempt", get("/mock-attempts/" + aid, s.student())).json();
    assertThat(resumed.get("answers").valueStream().filter(x -> x.get("flagged").asBoolean()).count()).isEqualTo(1);
    var other = createUser("Other");
    get("/mock-attempts/" + aid, other).expect(403);
    JsonNode res = contract("exam.submit", post("/mock-attempts/" + aid + "/submit", null, s.student())).json();
    assertThat(res.get("correct").asInt()).isEqualTo(8);
    assertThat(res.get("wrong").asInt()).isEqualTo(2);
    assertThat(res.get("skipped").asInt()).isEqualTo(2);
    assertThat(res.get("total").asInt()).isEqualTo(12);
    assertThat(res.get("score").asInt()).isEqualTo(67);
    assertThat(res.get("autoSubmitted").asBoolean()).isFalse();
    assertThat(res.get("attempts").asInt()).isEqualTo(1);
    assertThat(res.get("bestScore").asInt()).isEqualTo(67);
    assertThat(res.get("chapters").valueStream().mapToInt(c -> c.get("total").asInt()).sum()).isEqualTo(12);
    assertThat(res.get("review").get(0).get("correctOptionId").isNull()).isFalse();
    Map<String, Object> skip = new HashMap<>();
    skip.put("questionId", qs.get(11).get("id").asString());
    skip.put("optionId", null);
    put("/mock-attempts/" + aid + "/answers", skip, s.student()).expect(409);
    contract("exam.result", get("/mock-attempts/" + aid + "/result", s.student()));
    JsonNode again = post("/mock-tests/" + tid + "/attempts", null, s.student()).expect(201).json();
    assertThat(again.get("id").asString()).isNotEqualTo(aid);
    assertThat(again.get("answers").size()).isEqualTo(0);
  }

  @Test
  void expiredAttemptAutoSubmitsOnResumeNonBuyersCannotStart() {
    var s = setup();
    UUID tid = contentJdbc.queryForObject("select id from mock_tests where course_id = :c limit 1", Map.of("c", s.ds()), UUID.class);
    JsonNode att = post("/mock-tests/" + tid + "/attempts", null, s.student()).expect(201).json();
    coreJdbc.update("update mock_attempts set deadline_at = :d where id = :id", Map.of("d", Timestamp.from(Instant.now().minusSeconds(60)), "id", UUID.fromString(att.get("id").asString())));
    JsonNode r = get("/mock-attempts/" + att.get("id").asString(), s.student()).json();
    assertThat(r.get("submitted").asBoolean()).isTrue();
    JsonNode res = contract("exam.result", get("/mock-attempts/" + att.get("id").asString() + "/result", s.student())).json();
    assertThat(res.get("autoSubmitted").asBoolean()).isTrue();
    assertThat(res.get("skipped").asInt()).isEqualTo(res.get("total").asInt());
    assertThat(res.get("score").asInt()).isEqualTo(0);
    var outsider = createUser("Outsider");
    post("/mock-tests/" + tid + "/attempts", null, outsider).expect(403);
  }

  @Test
  void dailyPlanNudgeOneNotificationPerPlanPerDay() {
    var s = setup();
    post("/exam-plans", plan("DS", null, 3, List.of(s.ds())), s.student()).expect(201);
    exam.dailyPlanNudges(Instant.now());
    exam.dailyPlanNudges(Instant.now());
    var ns = coreJdbc.queryForList("select title from notifications where user_id = :u and type = 'exam.daily_plan'", Map.of("u", s.student().id()), String.class);
    assertThat(ns).hasSize(1);
    assertThat(ns.get(0)).contains("আজকের প্ল্যান");
  }

  @Test
  void sellerAuthoringExactlyOneCorrectOptionReplaceFlashcardsOwnership() {
    var s = setup();
    TestUser t = sellerOf(s.ds());
    String base = "/seller/courses/" + s.ds();
    var bad = post(base + "/mock-tests", Map.of("title", "Bad", "questions", List.of(Map.of("body", "Q1?", "options", List.of(Map.of("body", "a", "isCorrect", true), Map.of("body", "b", "isCorrect", true))))), t);
    assertThat(bad.status()).isEqualTo(400);
    var ok = contract("sellerExam.createMock", post(base + "/mock-tests", Map.of("title", "Quiz 2", "durationSec", 600, "questions",
        List.of(Map.of("body", "Stack কোন নীতিতে চলে?", "chapterTag", "Stack", "options", List.of(Map.of("body", "LIFO", "isCorrect", true), Map.of("body", "FIFO", "isCorrect", false))))), t));
    assertThat(ok.status()).isEqualTo(201);
    String mid = ok.json().get("id").asString();
    JsonNode up = contract("sellerExam.updateMock", put("/seller/mock-tests/" + mid, Map.of("title", "Quiz 2 (updated)", "durationSec", 900, "questions", List.of(
        Map.of("body", "Queue?", "options", List.of(Map.of("body", "FIFO", "isCorrect", true), Map.of("body", "LIFO", "isCorrect", false))),
        Map.of("body", "Heap?", "options", List.of(Map.of("body", "tree", "isCorrect", true), Map.of("body", "list", "isCorrect", false))))), t)).json();
    assertThat(up.get("questions").size()).isEqualTo(2);
    assertThat(contract("sellerExam.mockTests", get(base + "/mock-tests", t)).json().get("items").size()).isEqualTo(3);
    get(base + "/mock-tests", s.student()).expect(403);
    JsonNode fc = contract("sellerExam.setFlashcards", put(base + "/flashcards", Map.of("cards", List.of(Map.of("front", "LIFO?", "back", "Stack"))), t)).json();
    assertThat(fc.get("items").size()).isEqualTo(1);
    contract("sellerExam.flashcards", get(base + "/flashcards", t));
    contract("sellerExam.deleteMock", delete("/seller/mock-tests/" + mid, t)).expect(200);
    Integer audits = coreJdbc.queryForObject("select count(*)::int from audit_logs where entity = 'mock_tests' and entity_id = :id", Map.of("id", mid), Integer.class);
    assertThat(audits).isEqualTo(2);
  }

  @Test
  void planUpdateArchiveAndDelete() {
    var s = setup();
    JsonNode p = post("/exam-plans", plan("DS", "CSE-2203", 5, List.of(s.ds())), s.student()).expect(201).json();
    String pid = p.get("id").asString();
    Map<String, Object> patch = new HashMap<>();
    patch.put("courseCode", null);
    patch.put("active", false);
    JsonNode u = contract("exam.update", patch("/exam-plans/" + pid, patch, s.student())).json();
    assertThat(u.get("courseCode").isNull()).isTrue();
    assertThat(u.get("active").asBoolean()).isFalse();
    assertThat(u.get("subjectName").asString()).isEqualTo("DS");
    UUID algo = courseId("algorithm-analysis-design-cse-2201");
    patch("/exam-plans/" + pid, Map.of("courseIds", List.of(algo)), s.student()).expect(403);
    Map<String, Object> nullName = new HashMap<>();
    nullName.put("subjectName", null);
    patch("/exam-plans/" + pid, nullName, s.student()).expect(422);
    post("/exam-plans", Map.of(), s.student()).expect(422);
    post("/exam-plans", Map.of("subjectName", "  D  ", "examType", "midterm", "examDate", inDays(3)), s.student()).expect(422); // trimmed → too short
    contract("exam.remove", delete("/exam-plans/" + pid, s.student())).expect(200);
    get("/exam-plans/" + pid, s.student()).expect(404);
  }

  /** exam part of the previous "dashboard completion (Phase 9)" case: the demo student's seeded plan gains readiness. */
  @Test
  void demoStudentPlanReadinessRisesAfterALesson() {
    seed();
    TestUser u = loginAs("sadman@demo.shikkhasathi.com");
    JsonNode plan = get("/exam-plans", u).json().get("items").get(0);
    assertThat(plan.get("subjectName").asString()).isEqualTo("Data Structures");
    JsonNode first = get("/exam-plans/" + plan.get("id").asString(), u).json().get("today").get("items").get(0);
    completeLesson(u, UUID.fromString(first.get("courseId").asString()), first.get("lessonId").asString());
    get("/exam-plans/" + plan.get("id").asString(), u).expect(200); // refresh readiness
    Integer stored = coreJdbc.queryForObject("select readiness from exam_plans where id = :id", Map.of("id", UUID.fromString(plan.get("id").asString())), Integer.class);
    assertThat(stored).isGreaterThan(0);
  }
}
