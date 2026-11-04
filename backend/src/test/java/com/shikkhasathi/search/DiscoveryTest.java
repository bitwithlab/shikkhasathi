package com.shikkhasathi.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.course.event.CoursePublishedEvent;
import com.shikkhasathi.search.service.SearchIndexJob;
import com.shikkhasathi.student.service.DashboardWidgetProvider;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.TestUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;

/** Port of discovery.test.ts: search & ranking, details, storefront, recommendations, wishlist, request board. */
class DiscoveryTest extends IntegrationTest {
  @Autowired List<DashboardWidgetProvider> widgets;
  @Autowired ApplicationEventPublisher events;
  @Autowired SearchIndexJob indexJob;

  private UUID courseId(String slug) {
    return contentJdbc.queryForObject("select id from courses where slug = :s", Map.of("s", slug), UUID.class);
  }

  /** MockMvc encodes the URI template itself, so query values are passed raw. */
  private static String enc(String s) {
    return s;
  }

  private static Map<String, Object> anyInstitution(String subject, String examType) {
    var m = new HashMap<String, Object>();
    m.put("institutionId", null);
    m.put("subjectName", subject);
    m.put("examType", examType);
    return m;
  }

  private static List<String> ids(JsonNode items) {
    List<String> out = new ArrayList<>();
    for (JsonNode n : items) out.add(n.get("id").asString());
    return out;
  }

  // ---------------------------------------------------------------- search & ranking

  @Test
  void dsRanksOwnSyllabusFirstWithMatchBadgeOthersShowMismatch() {
    seed();
    TestUser s = loginAs("admin@shikkhasathi.com"); // seeded as MU CSE sem 5
    coreJdbc.update("update student_profiles set semester = 4 where user_id = :u", Map.of("u", s.id()));
    var r = contract("discovery.search", get("/search?q=DS", s)).json();
    assertThat(r.get("engine").asString()).isEqualTo("postgres");
    assertThat(r.at("/interpreted/0/term").asString()).isEqualTo("ds");
    assertThat(r.at("/interpreted/0/meaning").asString()).isEqualTo("Data Structures");
    assertThat(r.at("/items/0/courseCode").asString()).isEqualTo("CSE-2203");
    assertThat(r.at("/items/0/compatibility").asString()).isEqualTo("match");
    boolean mismatch = false;
    for (JsonNode c : r.get("items")) if ("CSE225".equals(c.path("courseCode").asString()) && "mismatch".equals(c.get("compatibility").asString())) mismatch = true;
    assertThat(mismatch).isTrue();
    assertThat(r.at("/items/0/score").isNumber()).isTrue();
    var bn = contract("discovery.search", get("/search?q=" + enc("ডাটা স্ট্রাকচার"), s)).json();
    assertThat(ids(bn.get("items"))).isEqualTo(ids(r.get("items")));
  }

  @Test
  void courseCodeInAnyFormatFiltersSortPaginationZeroResults() {
    seed();
    for (String q : List.of("cse2203", "CSE 2203", "cse-2203")) assertThat(contract("discovery.search", get("/search?q=" + enc(q))).at("/items/0/courseCode").asString()).isEqualTo("CSE-2203");
    var free = contract("discovery.search", get("/search?price=free")).json();
    for (JsonNode c : free.get("items")) assertThat(c.get("price").asLong()).isEqualTo(0);
    assertThat(free.get("total").asInt()).isEqualTo(1);
    var finals = contract("discovery.search", get("/search?examType=final&sort=price_asc")).json();
    List<Long> prices = new ArrayList<>();
    for (JsonNode c : finals.get("items")) {
      assertThat(c.get("examType").asString()).isEqualTo("final");
      prices.add(c.get("price").asLong());
    }
    assertThat(prices).isSorted();
    var p1 = contract("discovery.search", get("/search?limit=5")).json();
    assertThat(p1.get("items").size()).isEqualTo(5);
    var p2 = contract("discovery.search", get("/search?limit=5&cursor=" + p1.get("nextCursor").asString())).json();
    assertThat(p2.at("/items/0/id").asString()).isNotEqualTo(p1.at("/items/0/id").asString());
    assertThat(p1.get("total").asInt()).isEqualTo(12); // 13 seeded − 1 draft of the pending teacher
    assertThat(contract("discovery.search", get("/search?q=quantum chromodynamics")).json().get("items").size()).isEqualTo(0);
    assertThat(contract("discovery.search", get("/search?q=nsu")).json().get("items").size()).isGreaterThan(0);
  }

  @Test
  void searchValidatesQuery() {
    assertThat(get("/search?limit=61").status()).isEqualTo(422);
    assertThat(get("/search?examType=bogus").status()).isEqualTo(422);
    assertThat(get("/search/suggest").status()).isEqualTo(422);
  }

  @Test
  void autocompleteGroupsInstitutionsSubjectsCodesTeachersAndCourses() {
    seed();
    var r = contract("discovery.suggest", get("/search/suggest?q=data"));
    assertThat(r.header("cache-control")).isEqualTo("public, max-age=60");
    Set<String> types = new HashSet<>();
    for (JsonNode i : r.json().get("items")) types.add(i.get("type").asString());
    assertThat(types).contains("subject", "course");
    var code = contract("discovery.suggest", get("/search/suggest?q=CSE22")).json();
    boolean found = false;
    for (JsonNode i : code.get("items")) if ("code".equals(i.get("type").asString()) && "CSE-2203".equals(i.get("label").asString())) found = true;
    assertThat(found).isTrue();
    var mu = contract("discovery.suggest", get("/search/suggest?q=metro")).json();
    assertThat(mu.at("/items/0/type").asString()).isEqualTo("institution");
    assertThat(mu.at("/items/0/href").asString()).isEqualTo("/u/metropolitan-university");
  }

  // ---------------------------------------------------------------- details, storefront, recommendations, wishlist

  @Test
  void detailBreadcrumbSellerCardCurriculumWithoutMediaResourcesViewerFlagsDraftsHidden() {
    seed();
    var resp = contract("discovery.course", get("/courses/data-structures-midterm-crash-cse-2203"));
    var d = resp.json();
    List<String> crumbs = new ArrayList<>();
    for (JsonNode b : d.get("breadcrumb")) crumbs.add(b.get("label").asString());
    assertThat(crumbs).containsExactly("Metropolitan University", "CSE", "৪র্থ সেমিস্টার", "Data Structures");
    assertThat(d.at("/seller/slug").asString()).isEqualTo("rakibul-hasan");
    assertThat(d.at("/seller/verified").asBoolean()).isTrue();
    assertThat(d.at("/sections/0/chapters/0/lessons/0/isPreview").asBoolean()).isTrue();
    assertThat(resp.body()).doesNotContain("videoAssetId").doesNotContain("providerVideoId").doesNotContain("storageKey");
    assertThat(d.has("score")).isFalse();
    List<Integer> years = new ArrayList<>();
    for (JsonNode y : d.at("/resources/previousQuestionYears")) years.add(y.asInt());
    assertThat(years).containsExactly(2024, 2023, 2022);
    assertThat(d.at("/viewer/enrolled").asBoolean()).isFalse();
    assertThat(get("/courses/dld-aiub-eee-2103-midterm").status()).isEqualTo(404);
    assertThat(get("/courses/dld-aiub-eee-2103-midterm").errorMessage()).isEqualTo("কোর্সটি পাওয়া যায়নি।");
    // by id (checkout links) works too
    UUID id = courseId("data-structures-midterm-crash-cse-2203");
    assertThat(contract("discovery.course", get("/courses/" + id)).at("/slug").asString()).isEqualTo("data-structures-midterm-crash-cse-2203");
  }

  @Test
  void storefrontAndFeatured() {
    seed();
    var t = contract("discovery.storefront", get("/teachers/rakibul-hasan")).json();
    assertThat(t.get("courses").size()).isEqualTo(3);
    assertThat(t.get("verified").asBoolean()).isTrue();
    assertThat(get("/teachers/nobody-here").status()).isEqualTo(404);
    var fr = contract("discovery.featured", get("/featured"));
    assertThat(fr.header("cache-control")).isEqualTo("public, max-age=300");
    var f = fr.json();
    assertThat(f.get("courses").size()).isEqualTo(8);
    assertThat(f.get("teachers").size()).isGreaterThan(0);
    assertThat(f.at("/stats/courses").asInt()).isEqualTo(12);
  }

  @Test
  void recommendationsExcludeEnrolledCoursesAndFeedTheDashboardWishlistRoundTrip() {
    seed();
    TestUser s = loginAs("sadman@demo.shikkhasathi.com");
    UUID dsId = courseId("data-structures-midterm-crash-cse-2203");
    coreJdbc.update("delete from enrollments where student_id = :u", Map.of("u", s.id())); // seed enrolls the demo student
    var before = contract("discovery.recommendations", get("/recommendations", s)).json();
    assertThat(ids(before.get("items"))).contains(dsId.toString());
    coreJdbc.update("insert into enrollments (id, student_id, course_id) values (:id, :s, :c)", Map.of("id", Ids.uuidv7(), "s", s.id(), "c", dsId));
    var after = contract("discovery.recommendations", get("/recommendations", s)).json();
    assertThat(ids(after.get("items"))).doesNotContain(dsId.toString());
    var widget = widgets.stream().filter(w -> w.widget().equals(DashboardWidgetProvider.RECOMMENDATIONS)).findFirst().orElseThrow();
    assertThat((List<?>) widget.load(s.id())).isNotEmpty().hasSizeLessThanOrEqualTo(6);
    contract("wishlist.add", post("/wishlist/" + dsId, null, s));
    assertThat(contract("wishlist.list", get("/me/wishlist", s)).at("/items/0/id").asString()).isEqualTo(dsId.toString());
    var viewer = contract("discovery.course", get("/courses/data-structures-midterm-crash-cse-2203", s)).json().get("viewer");
    assertThat(viewer.get("enrolled").asBoolean()).isTrue();
    assertThat(viewer.get("wishlisted").asBoolean()).isTrue();
    contract("wishlist.remove", delete("/wishlist/" + dsId, s));
    assertThat(contract("wishlist.list", get("/me/wishlist", s)).json().get("items").size()).isEqualTo(0);
    assertThat(post("/wishlist/" + UUID.randomUUID(), null, s).status()).isEqualTo(404);
  }

  // ---------------------------------------------------------------- course request board

  @Test
  void createDedupesIntoAVoteTogglesVotesTeacherClaimsPublishNotifiesVoters() {
    seed();
    TestUser a = loginAs("sadman@demo.shikkhasathi.com");
    TestUser b = loginAs("nusrat@demo.shikkhasathi.com");
    UUID mu = contentJdbc.queryForObject("select id from institutions where slug = 'metropolitan-university'", Map.of(), UUID.class);
    UUID os = contentJdbc.queryForObject("select id from subject_masters where slug = 'operating-systems'", Map.of(), UUID.class);
    Map<String, Object> body = new HashMap<>(Map.of("institutionId", mu.toString(), "subjectId", os.toString(), "subjectName", "Operating Systems", "courseCode", "CSE-3201", "examType", "midterm"));
    var r1 = contract("requests.create", post("/course-requests", body, a)).json();
    assertThat(r1.get("duplicate").asBoolean()).isFalse();
    Map<String, Object> body2 = new HashMap<>(body);
    body2.put("note", "Final-এর আগেও লাগবে");
    var r2 = contract("requests.create", post("/course-requests", body2, b)).json();
    assertThat(r2.get("duplicate").asBoolean()).isTrue();
    assertThat(r2.at("/request/voteCount").asInt()).isEqualTo(2);
    String rid = r1.at("/request/id").asString();
    var un = contract("requests.vote", post("/course-requests/" + rid + "/vote", null, b)).json();
    assertThat(un.get("voteCount").asInt()).isEqualTo(1);
    assertThat(un.get("voted").asBoolean()).isFalse();
    post("/course-requests/" + rid + "/vote", null, b).expect(200);

    TestUser t = loginAs("rakib@demo.shikkhasathi.com");
    assertThat(post("/course-requests/" + rid + "/claim", Map.of(), a).status()).isEqualTo(403);
    var claimed = contract("requests.claim", post("/course-requests/" + rid + "/claim", Map.of("courseId", courseId("computer-networks-free-intro").toString()), t)).json();
    assertThat(claimed.get("status").asString()).isEqualTo("published"); // linked course is already published
    assertThat(claimed.get("claimedByName").asString()).isEqualTo("মো. রাকিবুল হাসান");
    var notified = coreJdbc.queryForList("select user_id from notifications where type = 'request.published'", Map.of(), UUID.class);
    assertThat(new HashSet<>(notified)).isEqualTo(Set.of(a.id(), b.id()));
    var list = contract("requests.list", get("/course-requests", a)).json();
    assertThat(list.at("/items/0/mine").asBoolean()).isTrue();
    assertThat(list.at("/items/0/voted").asBoolean()).isTrue();
    assertThat(list.at("/items/0/linkedCourseSlug").asString()).isEqualTo("computer-networks-free-intro");
  }

  @Test
  void requestBoardListPaginatesAndCanBeSwitchedOff() {
    seed();
    TestUser a = loginAs("sadman@demo.shikkhasathi.com");
    for (String name : List.of("Compiler Design", "Numerical Methods", "Software Engineering"))
      contract("requests.create", post("/course-requests", anyInstitution(name, "final"), a)).expect(201);
    var unknownInst = post("/course-requests", Map.of("subjectName", "Compiler Design", "examType", "final", "institutionId", "00000000-0000-0000-0000-000000000000"), a);
    assertThat(unknownInst.status()).isEqualTo(400);
    assertThat(unknownInst.errorMessage()).isEqualTo("প্রতিষ্ঠানটি পাওয়া যায়নি।");
    assertThat(post("/course-requests", Map.of("subjectName", "Missing institution key", "examType", "final"), a).status()).isEqualTo(422);
    contract("requests.create", post("/course-requests", anyInstitution("Graph Theory", "quiz"), a));
    var p1 = contract("requests.list", get("/course-requests?limit=2&sort=newest")).json();
    assertThat(p1.get("items").size()).isEqualTo(2);
    assertThat(p1.at("/items/0/mine").asBoolean()).isFalse();
    var p2 = contract("requests.list", get("/course-requests?limit=2&sort=newest&cursor=" + p1.get("nextCursor").asString())).json();
    assertThat(p2.at("/items/0/id").asString()).isNotEqualTo(p1.at("/items/0/id").asString());
    put("/admin/integrations/business", Map.of("values", Map.of("feature_course_requests", "false")), createUser("Admin", "admin")).expect(200);
    var off = post("/course-requests", anyInstitution("Anything", "final"), a);
    assertThat(off.status()).isEqualTo(503);
    assertThat(off.errorMessage()).isEqualTo("কোর্স অনুরোধ এখন বন্ধ আছে।");
  }

  @Test
  void coursePublishedEventMarksLinkedRequestsAndNotifiesVoters() {
    seed();
    TestUser a = loginAs("sadman@demo.shikkhasathi.com");
    UUID cn = courseId("computer-networks-free-intro");
    var r = contract("requests.create", post("/course-requests", anyInstitution("Computer Networks", "final"), a)).json();
    UUID rid = UUID.fromString(r.at("/request/id").asString());
    coreJdbc.update("update course_requests set linked_course_id = :c, status = 'teacher_working' where id = :id", Map.of("c", cn, "id", rid));
    events.publishEvent(new CoursePublishedEvent(cn));
    assertThat(coreJdbc.queryForObject("select status from course_requests where id = :id", Map.of("id", rid), String.class)).isEqualTo("published");
    assertThat(coreJdbc.queryForList("select user_id from notifications where type = 'request.published'", Map.of(), UUID.class)).containsExactly(a.id());
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchIndexDocumentCarriesTargetFields() {
    seed();
    UUID id = courseId("data-structures-midterm-crash-cse-2203");
    var doc = indexJob.courseDocument(id);
    assertThat(doc.get("status")).isEqualTo("published");
    assertThat((List<Object>) doc.get("courseCodesNorm")).contains("CSE2203");
    assertThat((List<Object>) doc.get("institutionNames")).contains("Metropolitan University");
    assertThat((List<Object>) doc.get("subjectNames")).contains("Data Structures");
    assertThat(indexJob.courseDocument(UUID.randomUUID())).isNull();
    indexJob.handle(new SearchIndexJob.Payload(id.toString(), null)); // no Meilisearch configured → no-op
  }

  @Test
  void meilisearchEngineSuppliesCandidatesAndTheIndexJobUpserts() throws Exception {
    seed();
    UUID ds = courseId("data-structures-midterm-crash-cse-2203");
    List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> {
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      calls.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + body);
      byte[] out = (ex.getRequestURI().getPath().endsWith("/search") ? "{\"hits\":[{\"id\":\"" + ds + "\"}]}" : "{\"taskUid\":1}").getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
    try {
      put("/admin/integrations/search", Map.of("values", Map.of("host", "http://127.0.0.1:" + server.getAddress().getPort())), createUser("Admin", "admin")).expect(200);
      var r = contract("discovery.search", get("/search?q=anything at all&examType=midterm")).json();
      assertThat(r.get("engine").asString()).isEqualTo("meilisearch");
      assertThat(ids(r.get("items"))).containsExactly(ds.toString());
      assertThat(calls.get(0)).startsWith("POST /indexes/courses/search").contains("examType = \\\"midterm\\\"");
      indexJob.handle(new SearchIndexJob.Payload(ds.toString(), null));
      assertThat(calls.get(calls.size() - 1)).startsWith("POST /indexes/courses/documents?primaryKey=id").contains("CSE2203");
      UUID draft = courseId("dld-aiub-eee-2103-midterm");
      indexJob.handle(new SearchIndexJob.Payload(draft.toString(), null));
      assertThat(calls.get(calls.size() - 1)).startsWith("DELETE /indexes/courses/documents/" + draft);
      indexJob.handle(new SearchIndexJob.Payload(null, true));
      assertThat(calls.stream().anyMatch(c -> c.startsWith("PATCH /indexes/courses/settings"))).isTrue();
    } finally {
      server.stop(0);
    }
  }
}
