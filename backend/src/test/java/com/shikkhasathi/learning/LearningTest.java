package com.shikkhasathi.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.learning.dto.LearningDtos.ContinueWidget;
import com.shikkhasathi.student.service.DashboardWidgetProvider;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.TestUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Port of the previous Node API test suite: test/learning.test.ts. */
class LearningTest extends IntegrationTest {
  @Autowired List<DashboardWidgetProvider> widgets;

  record Course(UUID id, UUID sellerId, String title) {}

  record Setup(Course course, TestUser student) {}

  private Setup setup(boolean enroll) {
    seed();
    var row = contentJdbc.queryForMap("select id, seller_id, title from courses where status = 'published' and price > 0 and lesson_count > 3 limit 1", Map.of());
    var course = new Course((UUID) row.get("id"), (UUID) row.get("seller_id"), (String) row.get("title"));
    var student = createUser("নুসরাত জাহান", List.of("student"), null, "+8801712345678");
    if (enroll) enroll(student, course);
    return new Setup(course, student);
  }

  private void enroll(TestUser u, Course c) {
    coreJdbc.update("insert into enrollments (id, student_id, course_id, status) values (:id, :s, :c, 'active')", Map.of("id", Ids.uuidv7(), "s", u.id(), "c", c.id()));
  }

  private TestUser seller(UUID sellerId) {
    var row = coreJdbc.queryForMap("select id, name, email, phone, array_to_string(roles, ',') as roles from users where id = :id", Map.of("id", sellerId));
    return login(sellerId, (String) row.get("name"), (String) row.get("email"), (String) row.get("phone"), List.of(((String) row.get("roles")).split(",")));
  }

  private static List<JsonNode> lessons(JsonNode outline) {
    var out = new ArrayList<JsonNode>();
    for (var s : outline.get("sections")) for (var c : s.get("chapters")) for (var l : c.get("lessons")) out.add(l);
    return out;
  }

  private List<Map<String, Object>> courseLessons(UUID courseId, String extra) {
    return contentJdbc.queryForList("select id, title, type, is_preview, duration_sec from lessons where course_id = :c " + extra, Map.of("c", courseId));
  }

  private long count(String sql, Map<String, ?> p) {
    Long n = coreJdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  @Test
  void outlineLocksNonPreviewLessonsAndEnrolledSeesEverything() {
    var s = setup(false);
    var o = contract("learning.outline", get("/learn/" + s.course().id(), s.student())).json();
    var ls = lessons(o);
    assertThat(o.get("enrolled").asBoolean()).isFalse();
    assertThat(ls).anyMatch(l -> l.get("locked").asBoolean());
    assertThat(ls.stream().filter(l -> l.get("isPreview").asBoolean())).allMatch(l -> !l.get("locked").asBoolean());
    assertThat(o.get("resources").size()).isZero();
    var locked = ls.stream().filter(l -> l.get("locked").asBoolean()).findFirst().orElseThrow();
    String lockedId = locked.get("id").asString();
    get("/lessons/" + lockedId, s.student()).expect(403);
    post("/lessons/" + lockedId + "/playback", Map.of("deviceId", "device-aaaa"), s.student()).expect(403);

    enroll(s.student(), s.course());
    var o2 = contract("learning.outline", get("/learn/" + s.course().id(), s.student())).json();
    assertThat(o2.get("enrolled").asBoolean()).isTrue();
    assertThat(lessons(o2)).allMatch(l -> !l.get("locked").asBoolean());

    // expired access
    coreJdbc.update("update enrollments set access_until = :t where student_id = :s", Map.of("t", Timestamp.from(Instant.now().minusSeconds(1)), "s", s.student().id()));
    var r = get("/lessons/" + lockedId, s.student()).expect(403);
    assertThat(r.errorMessage()).contains("মেয়াদ");
  }

  @Test
  void playbackSignedUrlWatermarkDeviceLimitSingleStreamAndHeartbeatCompletes() {
    var s = setup(true);
    var v0 = courseLessons(s.course().id(), "and type = 'video' limit 1").get(0);
    UUID vid = (UUID) v0.get("id");
    contentJdbc.update("update lessons set duration_sec = 300 where id = :id", Map.of("id", vid)); // 9 heartbeats (rate limit 20/min)
    int duration = 300;
    var p1 = contract("learning.playback", post("/lessons/" + vid + "/playback", Map.of("deviceId", "device-1111"), s.student())).json();
    assertThat(p1.get("kind").asString()).isEqualTo("hls");
    assertThat(p1.get("url").asString()).contains("m3u8");
    assertThat(p1.get("watermark").asString()).contains("••5678");
    for (var d : List.of("device-2222", "device-3333")) post("/lessons/" + vid + "/playback", Map.of("deviceId", d), s.student()).expect(200);
    post("/lessons/" + vid + "/playback", Map.of("deviceId", "device-4444"), s.student()).expect(403);
    var p2 = contract("learning.playback", post("/lessons/" + vid + "/playback", Map.of("deviceId", "device-1111"), s.student())).json();

    // old stream was taken over → earns nothing
    var stale = contract("learning.progress", post("/lessons/" + vid + "/progress", Map.of("positionSec", 30, "deltaSec", 15, "streamToken", p1.get("streamToken").asString()), s.student())).json();
    assertThat(stale.get("streamActive").asBoolean()).isFalse();

    // watch ~90% via heartbeats (capped at 30 s each)
    int beats = (int) Math.ceil(duration * 0.9 / 30);
    JsonNode last = null;
    for (int i = 1; i <= beats; i++)
      last = contract("learning.progress", post("/lessons/" + vid + "/progress", Map.of("positionSec", i * 30, "deltaSec", 60, "streamToken", p2.get("streamToken").asString()), s.student())).json();
    assertThat(last.get("streamActive").asBoolean()).isTrue();
    assertThat(last.get("completed").asBoolean()).isTrue();
    assertThat(last.get("progressPercent").asInt()).isGreaterThan(0);
    var lesson = contract("learning.lesson", get("/lessons/" + vid, s.student())).json();
    assertThat(lesson.get("completed").asBoolean()).isTrue();
    assertThat(lesson.get("lastPositionSec").asInt()).isEqualTo(beats * 30);
  }

  @Test
  void manualCompleteMyCoursesFiltersAndContinueLearning() {
    var s = setup(true);
    var ls = courseLessons(s.course().id(), "");
    var target = ls.stream().filter(l -> !"video".equals(l.get("type"))).findFirst().orElse(ls.get(1));
    UUID tid = (UUID) target.get("id");
    var r = contract("learning.complete", post("/lessons/" + tid + "/complete", Map.of("completed", true), s.student())).json();
    assertThat(r.get("completed").asBoolean()).isTrue();
    var mc = contract("learning.myCourses", get("/me/courses", s.student())).json();
    assertThat(mc.get("items").size()).isEqualTo(1);
    var item = mc.get("items").get(0);
    assertThat(item.get("courseId").asString()).isEqualTo(s.course().id().toString());
    assertThat(item.get("completedLessons").asInt()).isEqualTo(1);
    assertThat(item.get("resumeLessonId").asString()).isEqualTo(tid.toString());
    assertThat(item.get("progressPercent").asInt()).isEqualTo((int) Math.round(100.0 / item.get("totalLessons").asInt()));
    assertThat(contract("learning.myCourses", get("/me/courses?filter=completed", s.student())).json().get("items").size()).isZero();
    assertThat(contract("learning.myCourses", get("/me/courses?filter=in_progress", s.student())).json().get("items").size()).isEqualTo(1);

    var provider = widgets.stream().filter(w -> DashboardWidgetProvider.CONTINUE_LEARNING.equals(w.widget())).findFirst().orElseThrow();
    var widget = (ContinueWidget) provider.load(s.student().id());
    assertThat(widget.courseId()).isEqualTo(s.course().id());
    assertThat(widget.lastLessonId()).isEqualTo(tid);

    var undo = contract("learning.complete", post("/lessons/" + tid + "/complete", Map.of("completed", false), s.student())).json();
    assertThat(undo.get("progressPercent").asInt()).isZero();
    // non-enrolled can't track progress
    var other = createUser("Other Student");
    post("/lessons/" + tid + "/complete", Map.of("completed", true), other).expect(403);
  }

  @Test
  void notesAndBookmarksCrudSearchOwnership() {
    var s = setup(true);
    var l = courseLessons(s.course().id(), "limit 1").get(0);
    UUID lid = (UUID) l.get("id");
    var n = contract("learning.createNote", post("/notes", Map.of("lessonId", lid, "timestampSec", 125, "body", "BST delete: inorder successor নিতে হবে"), s.student())).expect(201).json();
    assertThat(n.get("lessonTitle").asString()).isEqualTo(l.get("title"));
    assertThat(n.get("courseTitle").asString()).isEqualTo(s.course().title());
    assertThat(n.get("timestampSec").asInt()).isEqualTo(125);
    contract("learning.createNote", post("/notes", Map.of("lessonId", lid, "body", "Heap property মনে রাখো"), s.student()));
    assertThat(contract("learning.notes", get("/notes?q=successor", s.student())).json().get("items").size()).isEqualTo(1);
    assertThat(contract("learning.notes", get("/notes?courseId=" + s.course().id(), s.student())).json().get("items").size()).isEqualTo(2);
    String nid = n.get("id").asString();
    var other = createUser("Other Student");
    patch("/notes/" + nid, Map.of("body", "hack"), other).expect(403);
    UUID locked = (UUID) courseLessons(s.course().id(), "and is_preview = false limit 1").get(0).get("id");
    post("/notes", Map.of("lessonId", locked, "body", "x"), other).expect(403);
    assertThat(contract("learning.updateNote", patch("/notes/" + nid, Map.of("body", "updated"), s.student())).json().get("body").asString()).isEqualTo("updated");
    contract("learning.deleteNote", delete("/notes/" + nid, s.student())).expect(200);

    var b = contract("learning.createBookmark", post("/bookmarks", Map.of("lessonId", lid, "timestampSec", 60), s.student())).json();
    assertThat(contract("learning.bookmarks", get("/bookmarks", s.student())).json().get("items").get(0).get("id").asString()).isEqualTo(b.get("id").asString());
    delete("/bookmarks/" + b.get("id").asString(), other).expect(403);
  }

  @Test
  void qaStudentAsksTeacherNotifiedTeacherAnswersStudentNotifiedOutsidersBlocked() {
    var s = setup(true);
    UUID lid = (UUID) courseLessons(s.course().id(), "limit 1").get(0).get("id");
    var q = contract("learning.ask", post("/lessons/" + lid + "/questions", Map.of("body", "AVL rotation-এ double rotation কখন লাগে?", "timestampSec", 300), s.student())).expect(201).json();
    assertThat(q.get("authorName").asString()).isEqualTo("নুসরাত জা.");
    assertThat(count("select count(*) from notifications where user_id = :u and type = 'seller.qa_new'", Map.of("u", s.course().sellerId()))).isEqualTo(1);
    var t = seller(s.course().sellerId());
    var inbox = contract("sellerLearning.questions", get("/seller/questions", t)).json();
    assertThat(inbox.get("items").get(0).get("id").asString()).isEqualTo(q.get("id").asString());
    assertThat(inbox.get("items").get(0).get("courseTitle").asString()).isEqualTo(s.course().title());
    var a = contract("learning.answer", post("/questions/" + q.get("id").asString() + "/answers", Map.of("body", "LR বা RL কেস হলে — ছবিসহ পরের লেসনে আছে।"), t)).json();
    assertThat(a.get("status").asString()).isEqualTo("answered");
    assertThat(a.get("answers").get(0).get("isSeller").asBoolean()).isTrue();
    assertThat(count("select count(*) from notifications where user_id = :u and type = 'course.qa_answered'", Map.of("u", s.student().id()))).isEqualTo(1);
    assertThat(contract("sellerLearning.questions", get("/seller/questions?status=open", t)).json().get("items").size()).isZero();
    var outsider = createUser("Outsider");
    get("/lessons/" + lid + "/questions", outsider).expect(403);
    assertThat(contract("learning.questions", get("/lessons/" + lid + "/questions", s.student())).json().get("items").get(0).get("mine").asBoolean()).isTrue();
  }

  /** Not in the Node suite: resources (signed / external URL), external YouTube links and a still-processing video. */
  @Test
  void documentsExternalVideoLinksAndProcessingVideo() {
    var s = setup(true);
    UUID docId = Ids.uuidv7();
    contentJdbc.update("insert into documents (id, course_id, owner_id, kind, title, storage_bucket, storage_key, mime) values (:id, :c, :o, 'notes', 'হ্যান্ডনোট', 'course-docs', 'docs/abc.pdf', 'application/pdf')",
        Map.of("id", docId, "c", s.course().id(), "o", s.course().sellerId()));
    var d = contract("learning.document", get("/documents/" + docId + "/url", s.student())).json();
    assertThat(d.get("url").asString()).isNotEmpty();
    assertThat(d.get("title").asString()).isEqualTo("হ্যান্ডনোট");
    var outsider = createUser("Outsider");
    get("/documents/" + docId + "/url", outsider).expect(403);
    get("/documents/" + Ids.uuidv7() + "/url", s.student()).expect(404);
    var o = contract("learning.outline", get("/learn/" + s.course().id(), s.student())).json();
    assertThat(o.get("resources")).anyMatch(x -> x.get("id").asString().equals(docId.toString()));

    var v = courseLessons(s.course().id(), "and type = 'video' limit 1").get(0);
    UUID vid = (UUID) v.get("id");
    UUID assetId = contentJdbc.queryForObject("select video_asset_id from lessons where id = :id", Map.of("id", vid), UUID.class);
    contentJdbc.update("update video_assets set provider = 'external_link', external_url = 'https://youtu.be/dQw4w9WgXcQ', status = 'ready' where id = :id", Map.of("id", assetId));
    var p = contract("learning.playback", post("/lessons/" + vid + "/playback", Map.of("deviceId", "device-1111"), s.student())).json();
    assertThat(p.get("kind").asString()).isEqualTo("youtube");
    assertThat(p.get("url").asString()).isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0&modestbranding=1");
    assertThat(contract("learning.lesson", get("/lessons/" + vid, s.student())).json().get("video").get("kind").asString()).isEqualTo("youtube");

    contentJdbc.update("update video_assets set provider = 'fake', status = 'processing' where id = :id", Map.of("id", assetId));
    post("/lessons/" + vid + "/playback", Map.of("deviceId", "device-1111"), s.student()).expect(409);
  }

  @Test
  void reviewsNeedProgressOnePerStudentAggregateSellerRepliesOnce() {
    var s = setup(true);
    String path = "/courses/" + s.course().id() + "/review";
    put(path, Map.of("rating", 5, "body", "দারুণ"), s.student()).expect(403);
    coreJdbc.update("update enrollments set progress_percent = 40 where student_id = :s", Map.of("s", s.student().id()));
    var r = contract("learning.review", put(path, Map.of("rating", 4, "body", "Midterm-এর আগে খুব কাজে লেগেছে।"), s.student())).json();
    assertThat(r.get("rating").asInt()).isEqualTo(4);
    assertThat(r.get("verified").asBoolean()).isTrue();
    var again = contract("learning.review", put(path, Map.of("rating", 5), s.student())).json();
    assertThat(again.get("id").asString()).isEqualTo(r.get("id").asString());
    var listResp = contract("learning.reviews", get("/courses/" + s.course().id() + "/reviews"));
    assertThat(listResp.header("Cache-Control")).isEqualTo("public, max-age=60");
    var list = listResp.json().get("items");
    JsonNode mine = null;
    for (var x : list) if (x.get("id").asString().equals(r.get("id").asString())) mine = x;
    assertThat(mine).isNotNull();
    assertThat(mine.get("rating").asInt()).isEqualTo(5);
    Integer ratingCount = contentJdbc.queryForObject("select rating_count from courses where id = :id", Map.of("id", s.course().id()), Integer.class);
    assertThat(ratingCount).isEqualTo(list.size());
    var t = seller(s.course().sellerId());
    put("/seller/reviews/" + r.get("id").asString() + "/reply", Map.of("reply", "হ্যাক"), s.student()).expect(403);
    contract("sellerLearning.replyReview", put("/seller/reviews/" + r.get("id").asString() + "/reply", Map.of("reply", "ধন্যবাদ, Final-এর জন্যও আসছে।"), t)).expect(200);
    put("/seller/reviews/" + r.get("id").asString() + "/reply", Map.of("reply", "আবার"), t).expect(409);
    contract("sellerLearning.reviews", get("/seller/reviews", t));
    var o = contract("learning.outline", get("/learn/" + s.course().id(), s.student())).json();
    assertThat(o.get("myReview").get("rating").asInt()).isEqualTo(5);
    assertThat(o.get("canReview").asBoolean()).isTrue();
  }
}
