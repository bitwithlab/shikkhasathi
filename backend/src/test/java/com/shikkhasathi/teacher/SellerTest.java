package com.shikkhasathi.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.course.event.CoursePublishedEvent;
import com.shikkhasathi.notification.email.FakeEmailProvider;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.Resp;
import com.shikkhasathi.support.TestUser;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Port of Web/server/test/seller.test.ts: application & verification, course wizard → review → publish. */
@RecordApplicationEvents
class SellerTest extends IntegrationTest {
  @Autowired FakeEmailProvider fakeEmail;
  @Autowired JobQueue jobs;
  /** records course-published events (the course-requests listener is another module's) */
  @Autowired ApplicationEvents events;

  record Ids(UUID mu, UUID muCse, UUID ds) {}

  record Seller(TestUser auth, UUID userId) {}

  Ids ids;

  @BeforeEach
  void catalog() {
    seed();
    UUID mu = contentJdbc.queryForObject("select id from institutions where slug = 'metropolitan-university'", Map.of(), UUID.class);
    UUID cse = contentJdbc.queryForObject("select id from departments where institution_id = :i and short_name = 'CSE'", Map.of("i", mu), UUID.class);
    UUID ds = contentJdbc.queryForObject("select id from subject_masters where slug = 'data-structures'", Map.of(), UUID.class);
    ids = new Ids(mu, cse, ds);
  }

  private static String path(String url) {
    return url.replaceFirst("^https?://[^/]+", "");
  }

  private TestUser admin() {
    return createUser("Admin", "admin");
  }

  private Seller asSeller(boolean verified) {
    var u = createUser("রাকিবুল হাসান", List.of("student"), "t" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@x.com", null);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("displayName", "মো. রাকিবুল হাসান");
    body.put("slug", "rakib-" + ThreadLocalRandom.current().nextInt(1_000_000));
    body.put("institutionId", ids.mu());
    body.put("method", "linkedin");
    body.put("evidence", Map.of("linkedinUrl", "https://linkedin.com/in/rakib"));
    body.put("payoutMethod", "bkash");
    body.put("payoutAccount", "01711223344");
    var r = contract("sellerApply.apply", post("/seller/apply", body, u));
    assertThat(r.at("/payoutAccountMasked").asString()).isEqualTo("••••••3344");
    // roles now include seller → re-issue token
    String roles = coreJdbc.queryForObject("select array_to_string(roles, ',') from users where id = :id", Map.of("id", u.id()), String.class);
    assertThat(roles).contains("seller");
    var auth = login(u.id(), u.name(), u.email(), null, List.of(roles.split(",")));
    if (verified) contract("adminSellers.decide", patch("/admin/sellers/" + u.id() + "/verification", Map.of("decision", "verified"), admin()));
    return new Seller(auth, u.id());
  }

  private JsonNode buildCourse(TestUser auth) {
    var c = contract("sellerCourses.create", post("/seller/courses", Map.of("title", "Data Structures — Midterm Crash Course"), auth)).json();
    String id = c.get("id").asString();
    contract("sellerCourses.update", patch("/seller/courses/" + id, Map.of(
        "shortDescription", "BST, Heap, Graph — Midterm-এর সম্ভাব্য গুরুত্বপূর্ণ টপিক, আগের বছরের প্রশ্নসহ।",
        "learnings", List.of("BST insert/delete", "Heap sort", "BFS/DFS"), "price", 29900, "compareAtPrice", 49900), auth));
    contract("sellerCourses.targets", put("/seller/courses/" + id + "/targets", Map.of("targets", List.of(Map.of(
        "institutionId", ids.mu(), "departmentId", ids.muCse(), "semester", 4, "subjectMasterId", ids.ds(), "courseCode", "cse 2203",
        "batches", List.of("56"), "sections", List.of("A")))), auth));
    var v = contract("sellerCourses.upload", post("/seller/uploads", Map.of("kind", "video", "title", "BST deletion"), auth)).json();
    var link = contract("sellerCourses.upload", post("/seller/uploads", Map.of("kind", "video_link", "title", "Heap intro", "url", "https://youtu.be/abc", "durationSec", 480), auth)).json();
    var cur = put("/seller/courses/" + id + "/curriculum", Map.of("sections", List.of(
        Map.of("title", "Trees", "chapters", List.of(Map.of("title", "BST", "lessons", List.of(
            Map.of("title", "BST deletion (৩টি কেস)", "type", "video", "videoAssetId", v.get("id").asString(), "isPreview", true, "isImportant", true),
            Map.of("title", "Notes", "type", "text", "body", "Inorder successor…"))))),
        Map.of("title", "Heaps", "chapters", List.of(Map.of("title", "Heap", "lessons", List.of(
            Map.of("title", "Heap intro", "type", "video", "videoAssetId", link.get("id").asString()))))))), auth);
    return contract("sellerCourses.curriculum", cur).json();
  }

  private static final Map<String, Boolean> ALL_CHECKED = Map.of("quality", true, "noGuaranteeClaims", true, "targeting", true, "copyright", true);

  // ---------------------------------------------------------------- seller application & verification

  @Test
  void applyAddsSellerRoleAdminVerifiesSellerNotifiedEmailedAuditLogged() {
    var s = asSeller(true);
    var p = coreJdbc.queryForMap("select verification_status, payout_account_enc from seller_profiles where user_id = :u", Map.of("u", s.userId()));
    assertThat(p.get("verification_status")).isEqualTo("verified");
    assertThat((String) p.get("payout_account_enc")).doesNotContain("01711223344");
    assertThat(coreJdbc.queryForList("select type from notifications where user_id = :u", Map.of("u", s.userId()), String.class)).contains("seller.application_result");
    assertThat(coreJdbc.queryForList("select action from audit_logs", Map.of(), String.class)).contains("seller.verified");
    assertThat(fakeEmail.outbox()).anyMatch(m -> m.to().equals(s.auth().email()));
    var prof = contract("sellerApply.profile", get("/seller/profile", s.auth()));
    assertThat(prof.at("/profile/verificationStatus").asString()).isEqualTo("verified");
    assertThat(prof.at("/profile/institutionName").asString()).isEqualTo("Metropolitan University");
  }

  @Test
  void universityEmailVerificationByCodePersonalGmailRejected() {
    var s = asSeller(false);
    assertThat(post("/seller/verify-email/request", Map.of("email", "me@gmail.com"), s.auth()).status()).isEqualTo(400);
    contract("sellerApply.emailCode", post("/seller/verify-email/request", Map.of("email", "rakib@metrouni.edu.bd"), s.auth()));
    Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(fakeEmail.outbox().get(fakeEmail.outbox().size() - 1).text());
    assertThat(m.find()).isTrue();
    var r = contract("sellerApply.emailConfirm", post("/seller/verify-email/confirm", Map.of("email", "rakib@metrouni.edu.bd", "code", m.group(1)), s.auth()));
    assertThat(r.at("/universityEmailVerified").asBoolean()).isTrue();
    // the code is single-use
    assertThat(post("/seller/verify-email/confirm", Map.of("email", "rakib@metrouni.edu.bd", "code", m.group(1)), s.auth()).status()).isEqualTo(400);
  }

  @Test
  void partialPatchPricingOnlyKeepsBasicsNoDefaultInjection() {
    var s = asSeller(true);
    var c = contract("sellerCourses.create", post("/seller/courses", Map.of("title", "Operating Systems — Final Prep"), s.auth())).json();
    String id = c.get("id").asString();
    patch("/seller/courses/" + id, Map.of("shortDescription", "Process, thread, deadlock — সম্ভাব্য গুরুত্বপূর্ণ টপিক।", "learnings", List.of("Scheduling"), "examType", "final", "language", "mixed"), s.auth());
    var r = contract("sellerCourses.update", patch("/seller/courses/" + id, Map.of("price", 19900), s.auth())).json();
    assertThat(r.get("price").asLong()).isEqualTo(19900);
    assertThat(r.get("shortDescription").asString()).isEqualTo("Process, thread, deadlock — সম্ভাব্য গুরুত্বপূর্ণ টপিক।");
    assertThat(r.get("learnings").get(0).asString()).isEqualTo("Scheduling");
    assertThat(r.get("learnings").size()).isEqualTo(1);
    assertThat(r.get("examType").asString()).isEqualTo("final");
    assertThat(r.get("language").asString()).isEqualTo("mixed");
  }

  @Test
  void nonSellersCannotUseTeacherStudioStudentsCannotSeeTheAdminQueue() {
    var s = createUser("Student");
    assertThat(get("/seller/courses", s).status()).isEqualTo(403);
    assertThat(get("/admin/sellers", s).status()).isEqualTo(403);
  }

  // ---------------------------------------------------------------- course wizard → review → publish

  @Test
  void buildsCourseUnverifiedCannotSubmitVerifiedCanAdminPublishes() {
    var unverified = asSeller(false);
    var c0 = buildCourse(unverified.auth());
    assertThat(post("/seller/courses/" + c0.get("id").asString() + "/submit", Map.of("declaration", true), unverified.auth()).status()).isEqualTo(403);

    var s = asSeller(true);
    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    assertThat(c.get("lessonCount").asInt()).isEqualTo(3);
    assertThat(c.get("totalDurationSec").asInt()).isEqualTo(600 + 480);
    var t = c.get("targets").get(0);
    assertThat(t.get("courseCode").asString()).isEqualTo("CSE-2203");
    assertThat(t.get("institutionShort").asString()).isEqualTo("MU");
    assertThat(t.get("departmentShort").asString()).isEqualTo("CSE");
    assertThat(t.get("subjectName").asString()).isEqualTo("Data Structures");
    var cl = contract("sellerCourses.checklist", get("/seller/courses/" + id + "/checklist", s.auth()));
    assertThat(cl.at("/canSubmit").asBoolean()).as(cl.body()).isTrue();
    var sub = contract("sellerCourses.submit", post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()));
    assertThat(sub.at("/status").asString()).isEqualTo("in_review");

    var admin = admin();
    var q = contract("adminReview.queue", get("/admin/courses/review", admin));
    assertThat(q.at("/items/0/id").asString()).isEqualTo(id);
    assertThat(q.at("/items/0/kind").asString()).isEqualTo("new");
    assertThat(q.at("/items/0/targets/0").asString()).isEqualTo("MU · CSE · CSE-2203");
    assertThat(post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", Map.of("quality", true, "noGuaranteeClaims", false, "targeting", true, "copyright", true)), admin).status()).isEqualTo(400);
    var pub = contract("adminReview.decide", post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin));
    assertThat(pub.at("/status").asString()).isEqualTo("published");
    var row = contentJdbc.queryForMap("select published_at, seller_verified from courses where id = :id", Map.of("id", UUID.fromString(id)));
    assertThat(row.get("published_at")).isNotNull();
    assertThat(row.get("seller_verified")).isEqualTo(true);
    // search indexing enqueued + course-published event (course requests listen to it)
    assertThat(jobs.inlineLog()).anyMatch(e -> e.getKey().equals(JobNames.SEARCH_INDEX_COURSE) && String.valueOf(((Map<?, ?>) e.getValue()).get("courseId")).equals(id));
    assertThat(events.stream(CoursePublishedEvent.class).map(CoursePublishedEvent::courseId)).containsExactly(UUID.fromString(id));
    assertThat(coreJdbc.queryForList("select type from notifications where user_id = :u", Map.of("u", s.userId()), String.class)).contains("seller.course_decision");
  }

  @Test
  void checklistBlocksGuaranteeClaimsAndThinCourses() {
    var s = asSeller(true);
    var c = contract("sellerCourses.create", post("/seller/courses", Map.of("title", "DS ১০০% কমন সাজেশন"), s.auth())).json();
    String id = c.get("id").asString();
    var cl = contract("sellerCourses.checklist", get("/seller/courses/" + id + "/checklist", s.auth())).json();
    assertThat(cl.get("canSubmit").asBoolean()).isFalse();
    JsonNode noGuarantee = null;
    for (JsonNode i : cl.get("items")) if (i.get("key").asString().equals("noGuarantee")) noGuarantee = i;
    assertThat(noGuarantee).isNotNull();
    assertThat(noGuarantee.get("ok").asBoolean()).isFalse();
    var sub = post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth());
    assertThat(sub.status()).isEqualTo(422);
    assertThat(sub.errorMessage()).isEqualTo("রিভিউয়ের আগে চেকলিস্টের সবগুলো পূরণ করুন।");
  }

  @Test
  void changesRequestedBackToDraftPublishedTitlePriceEditCreatesRevisionContentEditsGoLive() {
    var s = asSeller(true);
    var admin = admin();
    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()).expect(200);
    var changes = Map.of("quality", false, "noGuaranteeClaims", true, "targeting", true, "copyright", true);
    assertThat(post("/admin/courses/" + id + "/decision", Map.of("decision", "changes_requested", "checklist", changes), admin).status()).isEqualTo(400); // note required
    var cr = contract("adminReview.decide", post("/admin/courses/" + id + "/decision", Map.of("decision", "changes_requested", "note", "প্রিভিউ ভিডিওর অডিও ঠিক করুন", "checklist", changes), admin));
    assertThat(cr.at("/status").asString()).isEqualTo("changes_requested");
    var ed = contract("sellerCourses.update", patch("/seller/courses/" + id, Map.of("description", "নতুন অডিও"), s.auth()));
    assertThat(ed.at("/status").asString()).isEqualTo("draft");
    post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()).expect(200);
    post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin).expect(200);

    var rev = contract("sellerCourses.update", patch("/seller/courses/" + id, Map.of("title", "Data Structures — Final Crash Course", "price", 39900, "description", "আপডেট"), s.auth()));
    assertThat(rev.at("/pendingRevision").asBoolean()).isTrue();
    assertThat(rev.at("/title").asString()).isEqualTo("Data Structures — Final Crash Course"); // seller sees the draft revision
    var live = contentJdbc.queryForMap("select title, description, needs_spot_review, price from courses where id = :id", Map.of("id", UUID.fromString(id)));
    assertThat(live.get("title")).isEqualTo("Data Structures — Midterm Crash Course"); // public sees live
    assertThat(live.get("description")).isEqualTo("আপডেট"); // content edits go live
    assertThat(live.get("needs_spot_review")).isEqualTo(true);
    var q = contract("adminReview.queue", get("/admin/courses/review", admin)).json();
    String kind = null;
    for (JsonNode i : q.get("items")) if (i.get("id").asString().equals(id)) kind = i.get("kind").asString();
    assertThat(kind).isEqualTo("revision");
    var detail = contract("adminReview.detail", get("/admin/courses/" + id, admin));
    assertThat(detail.at("/revision/title").asString()).isEqualTo("Data Structures — Final Crash Course");
    assertThat(detail.at("/revision/price").asLong()).isEqualTo(39900);
    assertThat(detail.at("/title").asString()).isEqualTo("Data Structures — Midterm Crash Course"); // admin reviews the live values
    assertThat(detail.at("/sellerName").asString()).isEqualTo("মো. রাকিবুল হাসান");
    var pub = contract("adminReview.decide", post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin));
    assertThat(pub.at("/kind").asString()).isEqualTo("revision");
    var after = contentJdbc.queryForMap("select title, price from courses where id = :id", Map.of("id", UUID.fromString(id)));
    assertThat(after.get("title")).isEqualTo("Data Structures — Final Crash Course");
    assertThat(((Number) after.get("price")).longValue()).isEqualTo(39900);
    assertThat(contentJdbc.queryForObject("select status from course_revisions where course_id = :id", Map.of("id", UUID.fromString(id)), String.class)).isEqualTo("approved");
  }

  @Test
  void curriculumReplaceKeepsLessonIdsDeletesRemovedOnesOtherSellersCannotTouchIt() {
    var s = asSeller(true);
    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    ObjectNode first = ((ObjectNode) c.at("/sections/0/chapters/0/lessons/0")).deepCopy();
    first.remove("video");
    first.remove("document");
    first.put("title", "BST deletion — updated");
    ObjectNode body = json.createObjectNode();
    ArrayNode secs = body.putArray("sections");
    ObjectNode sec = secs.addObject();
    sec.put("id", c.at("/sections/0/id").asString());
    sec.put("title", "Trees (updated)");
    ObjectNode ch = sec.putArray("chapters").addObject();
    ch.put("id", c.at("/sections/0/chapters/0/id").asString());
    ch.put("title", "BST");
    ch.putArray("lessons").add(first);
    var r = contract("sellerCourses.curriculum", put("/seller/courses/" + id + "/curriculum", json.writeValueAsString(body), s.auth())).json();
    assertThat(r.get("sections").size()).isEqualTo(1);
    assertThat(r.at("/sections/0/chapters/0/lessons/0/id").asString()).isEqualTo(first.get("id").asString());
    assertThat(contentJdbc.queryForObject("select count(*) from lessons where course_id = :c", Map.of("c", UUID.fromString(id)), Long.class)).isEqualTo(1);
    var other = asSeller(true);
    assertThat(get("/seller/courses/" + id, other.auth()).status()).isEqualTo(403);
  }

  @Test
  void documentUploadSignedUrlPutCompleteCoverUploadReturnsPublicUrl() {
    var s = asSeller(true);
    var c = buildCourse(s.auth());
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("kind", "document");
    doc.put("courseId", c.get("id").asString());
    doc.put("docKind", "previous_questions");
    doc.put("title", "DS Midterm 2024");
    doc.put("year", 2024);
    doc.put("mime", "application/pdf");
    doc.put("size", 12);
    var up = contract("sellerCourses.upload", post("/seller/uploads", doc, s.auth())).json();
    assertThat(up.get("method").asString()).isEqualTo("PUT");
    assertThat(post("/seller/uploads/" + up.get("id").asString() + "/complete", null, s.auth()).status()).isEqualTo(400); // not uploaded yet
    var put = call(HttpMethod.PUT, path(up.get("uploadUrl").asString()), "%PDF-1.4 test".getBytes(), null, Map.of("Content-Type", "application/pdf"));
    assertThat(put.status()).isEqualTo(200);
    contract("sellerCourses.uploadComplete", post("/seller/uploads/" + up.get("id").asString() + "/complete", null, s.auth()));
    var cv = contract("sellerCourses.upload", post("/seller/uploads", Map.of("kind", "cover", "mime", "image/png", "size", 100), s.auth())).json();
    assertThat(cv.get("publicUrl").asString()).contains("/files/public/course-covers/");
    var tampered = call(HttpMethod.PUT, "/files/upload?token=bad", "x".getBytes(), null, Map.of("Content-Type", "application/pdf"));
    assertThat(tampered.status()).isEqualTo(401);
  }

  // ---------------------------------------------------------------- remaining endpoints (contract coverage)

  @Test
  void storefrontListVisibilityDeleteProposeSubjectAndAdminSellerQueue() {
    var pending = asSeller(false);
    var admin = admin();
    var list = contract("adminSellers.list", get("/admin/sellers?limit=50", admin)).json();
    boolean found = false;
    for (JsonNode i : list.get("items")) if (i.get("userId").asString().equals(pending.userId().toString())) {
      found = true;
      assertThat(i.get("name").asString()).isEqualTo("রাকিবুল হাসান");
      assertThat(i.get("evidence").get("linkedinUrl").asString()).isEqualTo("https://linkedin.com/in/rakib");
    }
    assertThat(found).isTrue();
    var page1 = contract("adminSellers.list", get("/admin/sellers?status=verified&limit=1", admin)).json();
    if (!page1.get("nextCursor").isNull()) {
      var page2 = contract("adminSellers.list", get("/admin/sellers?status=verified&limit=1&cursor=" + page1.get("nextCursor").asString(), admin)).json();
      assertThat(page2.at("/items/0/userId").asString()).isNotEqualTo(page1.at("/items/0/userId").asString());
    }
    assertThat(get("/admin/sellers?status=bogus", admin).status()).isEqualTo(422);

    var s = asSeller(true);
    Map<String, Object> sf = new LinkedHashMap<>();
    sf.put("displayName", "রাকিবুল স্যার");
    sf.put("bio", null);
    sf.put("headline", "CSE লেকচারার");
    var p = contract("sellerApply.updateStorefront", patch("/seller/storefront", sf, s.auth()));
    assertThat(p.at("/displayName").asString()).isEqualTo("রাকিবুল স্যার");
    assertThat(p.at("/headline").asString()).isEqualTo("CSE লেকচারার");
    assertThat(p.at("/bio").isNull()).isTrue();
    var taken = coreJdbc.queryForObject("select slug from seller_profiles where user_id = :u", Map.of("u", pending.userId()), String.class);
    assertThat(patch("/seller/storefront", Map.of("slug", taken), s.auth()).status()).isEqualTo(409);
    assertThat(patch("/seller/storefront", Map.of("displayName", "ab", "headline", "x".repeat(121)), s.auth()).status()).isEqualTo(422);

    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    assertThat(contentJdbc.queryForObject("select seller_name from courses where id = :id", Map.of("id", UUID.fromString(id)), String.class)).isEqualTo("রাকিবুল স্যার");
    var mine = contract("sellerCourses.list", get("/seller/courses", s.auth())).json();
    assertThat(mine.at("/items/0/id").asString()).isEqualTo(id);
    assertThat(mine.at("/items/0/courseCode").asString()).isEqualTo("CSE-2203");
    contract("sellerCourses.get", get("/seller/courses/" + id, s.auth()));
    // unpublished courses cannot be unlisted; drafts can be deleted
    assertThat(post("/seller/courses/" + id + "/archive", Map.of("status", "unlisted"), s.auth()).status()).isEqualTo(400);
    post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()).expect(200);
    post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin).expect(200);
    var un = contract("sellerCourses.archive", post("/seller/courses/" + id + "/archive", Map.of("status", "unlisted"), s.auth()));
    assertThat(un.at("/status").asString()).isEqualTo("unlisted");
    assertThat(delete("/seller/courses/" + id, s.auth()).status()).isEqualTo(400);
    var draft = contract("sellerCourses.create", post("/seller/courses", Map.of("title", "Discrete Math — Quiz Prep"), s.auth())).json();
    contract("sellerCourses.remove", delete("/seller/courses/" + draft.get("id").asString(), s.auth()));
    assertThat(get("/seller/courses/" + draft.get("id").asString(), s.auth()).status()).isEqualTo(404);

    var subj = contract("sellerCourses.proposeSubject", post("/subjects/propose", Map.of("name", "Quantum Computing", "aliases", List.of("QC")), s.auth())).json();
    assertThat(subj.get("status").asString()).isEqualTo("proposed");
    var again = contract("sellerCourses.proposeSubject", post("/subjects/propose", Map.of("name", "Quantum Computing"), s.auth())).json();
    assertThat(again.get("id").asString()).isEqualTo(subj.get("id").asString());
    var existing = contract("sellerCourses.proposeSubject", post("/subjects/propose", Map.of("name", "Data Structures"), s.auth())).json();
    assertThat(existing.get("status").asString()).isEqualTo("approved");
  }

  @Autowired com.shikkhasathi.course.service.VideoProcessedJob videoJob;

  @Test
  void bunnyUploadCompleteQueuesProcessingPollAndTheJobMarksTheAssetReady() {
    var s = asSeller(true);
    UUID vid = UUID.randomUUID();
    contentJdbc.update("insert into video_assets (id, owner_id, provider, provider_video_id, title, status, duration_sec) values (:id, :o, 'bunny', 'b-123', 'Lecture', 'uploading', 0)",
        Map.of("id", vid, "o", s.userId()));
    contract("sellerCourses.uploadComplete", post("/seller/uploads/" + vid + "/complete", null, s.auth()));
    assertThat(contentJdbc.queryForObject("select status from video_assets where id = :id", Map.of("id", vid), String.class)).isEqualTo("processing");
    assertThat(jobs.inlineLog()).anyMatch(e -> e.getKey().equals(JobNames.VIDEO_PROCESSED));
    videoJob.handle(new com.shikkhasathi.course.service.VideoProcessedJob.Payload(vid, 0));
    var row = contentJdbc.queryForMap("select status, duration_sec from video_assets where id = :id", Map.of("id", vid));
    assertThat(row.get("status")).isEqualTo("ready");
    assertThat(row.get("duration_sec")).isEqualTo(600);
    // someone else's asset is not found
    assertThat(post("/seller/uploads/" + vid + "/complete", null, asSeller(true).auth()).status()).isEqualTo(404);
  }

  @Test
  void targetChangesOnAPublishedCourseWaitForReviewThenApply() {
    var s = asSeller(true);
    var admin = admin();
    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()).expect(200);
    post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin).expect(200);
    var r = contract("sellerCourses.targets", put("/seller/courses/" + id + "/targets", Map.of("targets", List.of(Map.of("isGeneral", true, "courseCode", "cse 2203", "institutionId", ids.mu()))), s.auth()));
    assertThat(r.at("/pendingRevision").asBoolean()).isTrue();
    assertThat(r.at("/targets/0/isGeneral").asBoolean()).isFalse(); // live targets until approved
    // invalid targets are rejected
    assertThat(put("/seller/courses/" + id + "/targets", Map.of("targets", List.of(Map.of("courseCode", "X-1"))), s.auth()).status()).isEqualTo(400);
    assertThat(put("/seller/courses/" + id + "/targets", Map.of("targets", List.of()), s.auth()).status()).isEqualTo(422);
    contract("adminReview.decide", post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin));
    var t = contentJdbc.queryForMap("select is_general, institution_id, course_code from course_targets where course_id = :c", Map.of("c", UUID.fromString(id)));
    assertThat(t.get("is_general")).isEqualTo(true);
    assertThat(t.get("institution_id")).isNull();
    assertThat(t.get("course_code")).isEqualTo("CSE-2203");
  }

  @Test
  void spotCheckFailureTakesPublishedCourseDownAndVideoLinksMustBeHttps() {
    var s = asSeller(true);
    var admin = admin();
    var c = buildCourse(s.auth());
    String id = c.get("id").asString();
    post("/seller/courses/" + id + "/submit", Map.of("declaration", true), s.auth()).expect(200);
    post("/admin/courses/" + id + "/decision", Map.of("decision", "publish", "checklist", ALL_CHECKED), admin).expect(200);
    // a content edit on a published course queues a spot check
    patch("/seller/courses/" + id, Map.of("description", "নতুন বিবরণ"), s.auth()).expect(200);
    var q = contract("adminReview.queue", get("/admin/courses/review", admin)).json();
    assertThat(q.at("/items/0/kind").asString()).isEqualTo("spot_check");
    var r = contract("adminReview.decide", post("/admin/courses/" + id + "/decision",
        Map.of("decision", "reject", "note", "কপিরাইট সমস্যা", "checklist", Map.of("quality", true, "noGuaranteeClaims", true, "targeting", true, "copyright", false)), admin));
    assertThat(r.at("/status").asString()).isEqualTo("rejected");
    assertThat(contentJdbc.queryForObject("select needs_spot_review from courses where id = :id", Map.of("id", UUID.fromString(id)), Boolean.class)).isFalse();
    var rejectedQ = contract("adminReview.queue", get("/admin/courses/review?status=rejected", admin));
    assertThat(rejectedQ.at("/items/0/id").asString()).isEqualTo(id);

    Resp bad = post("/seller/uploads", Map.of("kind", "video_link", "title", "x", "url", "http://example.com/v.mp4"), s.auth());
    assertThat(bad.status()).isEqualTo(400);
    assertThat(bad.errorMessage()).isEqualTo("https লিংক দিন।");
    assertThat(post("/seller/uploads", Map.of("kind", "nope"), s.auth()).status()).isEqualTo(422);
    // a student may upload an id card (for the application) but nothing else
    var student = createUser("Student");
    contract("sellerCourses.upload", post("/seller/uploads", Map.of("kind", "id_card", "mime", "image/png", "size", 100), student));
    assertThat(post("/seller/uploads", Map.of("kind", "cover", "mime", "image/png", "size", 100), student).status()).isEqualTo(403);
  }
}
