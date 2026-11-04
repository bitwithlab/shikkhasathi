package com.shikkhasathi.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.user.service.MeService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Port of the previous Node API test suite: test/catalog.test.ts (public catalog, academic profile + onboarding, institution requests, admin catalog). */
class CatalogTest extends IntegrationTest {
  @Autowired MeService meService;

  // ---------------------------------------------------------------- seed id lookups (seedCatalog() ids in the Node test)

  UUID inst(String slug) {
    return contentJdbc.queryForObject("select id from institutions where slug = :s", Map.of("s", slug), UUID.class);
  }

  UUID dept(String instSlug, String shortName) {
    return contentJdbc.queryForObject("select d.id from departments d join institutions i on i.id = d.institution_id where i.slug = :s and d.short_name = :d",
        Map.of("s", instSlug, "d", shortName), UUID.class);
  }

  UUID prog(String instSlug, String deptShort, String name) {
    return contentJdbc.queryForObject("""
        select p.id from programs p join departments d on d.id = p.department_id join institutions i on i.id = d.institution_id
        where i.slug = :s and d.short_name = :d and p.name = :n""", Map.of("s", instSlug, "d", deptShort, "n", name), UUID.class);
  }

  UUID subj(String slug) {
    return contentJdbc.queryForObject("select id from subject_masters where slug = :s", Map.of("s", slug), UUID.class);
  }

  static List<String> texts(JsonNode arr, String field) {
    return StreamSupport.stream(arr.spliterator(), false).map(n -> n.get(field).asString()).toList();
  }

  static JsonNode find(JsonNode arr, String field, String value) {
    for (JsonNode n : arr) if (value.equals(n.path(field).asString(null))) return n;
    return null;
  }

  // ---------------------------------------------------------------- catalog (public)

  @Test
  void listsSearchesFiltersAndPaginatesInstitutions() {
    seed();
    var all = contract("catalog.institutions", get("/institutions?limit=5"));
    assertThat(all.at("/items").size()).isEqualTo(5);
    String next = all.at("/nextCursor").asString();
    assertThat(next).isNotBlank();
    var p2 = contract("catalog.institutions", get("/institutions?limit=5&cursor=" + next));
    assertThat(p2.at("/items/0/id").asString()).isNotEqualTo(all.at("/items/0/id").asString());
    assertThat(texts(p2.at("/items"), "id")).doesNotContainAnyElementsOf(texts(all.at("/items"), "id"));
    assertThat(contract("catalog.institutions", get("/institutions?q=MU Sylhet")).at("/items/0/slug").asString()).isEqualTo("metropolitan-university");
    assertThat(contract("catalog.institutions", get("/institutions?q=বুয়েট")).at("/items/0/shortName").asString()).isEqualTo("BUET");
    var pub = contract("catalog.institutions", get("/institutions?type=public"));
    assertThat(pub.at("/items").size()).isPositive();
    assertThat(texts(pub.at("/items"), "type")).allMatch("public"::equals);
    // counts come from published course targets
    var mu = find(contract("catalog.institutions", get("/institutions?q=Metropolitan")).at("/items"), "slug", "metropolitan-university");
    assertThat(mu.get("courseCount").asInt()).isPositive();
    assertThat(mu.get("teacherCount").asInt()).isPositive();
    assertThat(get("/institutions?type=nope").status()).isEqualTo(422);
    assertThat(get("/institutions?limit=0").status()).isEqualTo(422);
  }

  @Test
  void returnsInstitutionDetailWithDepartmentsProgramsAndCourseCodes() {
    seed();
    var r = contract("catalog.institution", get("/institutions/metropolitan-university"));
    assertThat(r.header("cache-control")).contains("max-age=300");
    var cse = find(r.at("/departments"), "shortName", "CSE");
    assertThat(texts(cse.get("programs"), "name")).contains("B.Sc. in CSE (Day)");
    assertThat(cse.get("subjectCount").asInt()).isPositive();
    assertThat(find(r.at("/subjects"), "courseCode", "CSE-2203").get("subjectSlug").asString()).isEqualTo("data-structures");
    assertThat(get("/institutions/nope").status()).isEqualTo(404);
    assertThat(get("/institutions/nope").errorMessage()).isEqualTo("এই প্রতিষ্ঠানটি পাওয়া যায়নি।");

    var depts = contract("catalog.departments", get("/departments?institutionId=" + inst("metropolitan-university")));
    assertThat(texts(depts.at("/items"), "shortName")).contains("CSE");
    var progs = contract("catalog.programs", get("/programs?departmentId=" + dept("metropolitan-university", "CSE")));
    assertThat(texts(progs.at("/items"), "name")).contains("B.Sc. in CSE (Day)");
    assertThat(get("/departments").status()).isEqualTo(422);
  }

  @Test
  void filtersSubjectsByInstitutionSemesterAndByAlias() {
    seed();
    var sem4 = contract("catalog.subjects", get("/subjects?institutionId=" + inst("metropolitan-university") + "&semester=4"));
    assertThat(texts(sem4.at("/items"), "courseCode")).containsExactlyInAnyOrder("CSE-2203", "CSE-2205");
    var dbms = contract("catalog.subjects", get("/subjects?q=DBMS"));
    assertThat(dbms.at("/items/0/slug").asString()).isEqualTo("database-management-systems");
    assertThat(dbms.at("/items/0").has("courseCode")).isFalse();
    var ds = contract("catalog.subject", get("/subjects/data-structures"));
    assertThat(ds.at("/offerings").size()).isGreaterThanOrEqualTo(8);
    assertThat(ds.header("cache-control")).contains("max-age=300");
    assertThat(get("/subjects/nope").status()).isEqualTo(404);
  }

  // ---------------------------------------------------------------- academic profile + onboarding

  @Test
  void validatesDepartmentBelongsToInstitutionAndCompletesOnboardingWithExamPlan() {
    seed();
    var s = createUser("Onboarding Student");
    var bad = put("/me/academic-profile", Map.of("institutionId", inst("metropolitan-university"), "departmentId", dept("brac-university", "CSE")), s);
    assertThat(bad.status()).isEqualTo(400);
    assertThat(bad.errorMessage()).isEqualTo("বিভাগটি এই ইউনিভার্সিটির নয়।");
    assertThat(put("/me/academic-profile", Map.of("departmentId", dept("brac-university", "CSE")), s).status()).isEqualTo(422);

    String examDate = Instant.now().plusSeconds(5 * 86_400).toString();
    Map<String, Object> academic = Map.of("institutionId", inst("metropolitan-university"), "departmentId", dept("metropolitan-university", "CSE"),
        "programId", prog("metropolitan-university", "CSE", "B.Sc. in CSE (Day)"), "semester", 5, "batch", " 56 ", "section", "A");
    Map<String, Object> nextExam = Map.of("subjectId", subj("data-structures"), "subjectName", "Data Structures", "courseCode", "CSE-2203", "examType", "midterm", "examDate", examDate);
    var ok = contract("catalog.completeOnboarding", post("/me/onboarding", Map.of("academic", academic, "nextExam", nextExam), s));
    assertThat(ok.at("/onboardingCompleted").asBoolean()).isTrue();
    assertThat(ok.at("/academic/institutionShort").asString()).isEqualTo("MU");
    assertThat(ok.at("/academic/departmentShort").asString()).isEqualTo("CSE");
    assertThat(ok.at("/academic/programName").asString()).isEqualTo("B.Sc. in CSE (Day)");
    assertThat(ok.at("/academic/semester").asInt()).isEqualTo(5);
    assertThat(ok.at("/academic/batch").asString()).isEqualTo("56");
    assertThat(ok.at("/academic/section").asString()).isEqualTo("A");
    assertThat(coreJdbc.queryForObject("select count(*) from exam_plans where student_id = :u", Map.of("u", s.id()), Long.class)).isEqualTo(1);

    // a past exam date is rejected
    var past = new HashMap<>(nextExam);
    past.put("examDate", Instant.now().minusSeconds(3 * 86_400).toString());
    var r = post("/me/onboarding", Map.of("nextExam", past), s);
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.errorMessage()).isEqualTo("পরীক্ষার তারিখ আজ বা পরের কোনো দিন দিন।");

    // clearing the institution clears department/program too
    var clear = new HashMap<String, Object>();
    clear.put("institutionId", null);
    clear.put("departmentId", dept("metropolitan-university", "CSE"));
    clear.put("semester", 6);
    var cleared = contract("catalog.academicProfile", put("/me/academic-profile", clear, s));
    assertThat(cleared.at("/academic/institutionId").isNull()).isTrue();
    assertThat(cleared.at("/academic/departmentId").isNull()).isTrue();
    assertThat(cleared.at("/academic/semester").asInt()).isEqualTo(6);
  }

  @Test
  void onboardingIsSkippable() {
    var s = createUser("Skipper");
    var r = contract("catalog.completeOnboarding", post("/me/onboarding", Map.of(), s));
    assertThat(r.at("/onboardingCompleted").asBoolean()).isTrue();
  }

  // ---------------------------------------------------------------- institution requests → admin

  @Test
  void studentRequestsAdminSeesSimilarApprovesOrMerges() {
    seed();
    var s = createUser("Requesting Student");
    var admin = createUser("Admin", "admin");
    var r1 = contract("catalog.requestInstitution", post("/institution-requests", Map.of("name", "Leading University", "type", "private", "city", "সিলেট"), s));
    var r2 = contract("catalog.requestInstitution", post("/institution-requests", Map.of("name", "Metropolitan University Sylhet", "type", "private"), s));
    assertThat(r1.at("/status").asString()).isEqualTo("pending");
    var dup = post("/institution-requests", Map.of("name", "Leading University", "type", "private"), s);
    assertThat(dup.status()).isEqualTo(409);
    assertThat(dup.errorMessage()).isEqualTo("এই প্রতিষ্ঠানের জন্য আপনার অনুরোধ আগেই জমা আছে।");
    var short_ = post("/institution-requests", Map.of("name", " ab ", "type", "private"), s);
    assertThat(short_.status()).isEqualTo(422);
    assertThat(short_.errorMessage()).isEqualTo("প্রতিষ্ঠানের পুরো নাম লিখুন");

    assertThat(get("/admin/institution-requests", s).status()).isEqualTo(403);
    var q = contract("adminCatalog.institutionRequests", get("/admin/institution-requests", admin));
    assertThat(q.at("/items").size()).isEqualTo(2);
    var metroReq = find(q.at("/items"), "id", r2.at("/id").asString());
    assertThat(metroReq.at("/similar/0/shortName").asString()).isEqualTo("MU");
    assertThat(metroReq.at("/requestedBy/id").asString()).isEqualTo(s.id().toString());
    // cursor paging over the queue
    var page1 = contract("adminCatalog.institutionRequests", get("/admin/institution-requests?limit=1", admin));
    var page2 = contract("adminCatalog.institutionRequests", get("/admin/institution-requests?limit=1&cursor=" + page1.at("/nextCursor").asString(), admin));
    assertThat(page2.at("/items/0/id").asString()).isNotEqualTo(page1.at("/items/0/id").asString());
    assertThat(page2.at("/nextCursor").isNull()).isTrue();

    contract("adminCatalog.decideInstitutionRequest", patch("/admin/institution-requests/" + r1.at("/id").asString(), Map.of("action", "approve", "shortName", "LU"), admin));
    var lu = contentJdbc.queryForMap("select status, name, city from institutions where slug = 'lu'", Map.of());
    assertThat(lu.get("status")).isEqualTo("approved");
    assertThat(lu.get("city")).isEqualTo("সিলেট");

    contract("adminCatalog.decideInstitutionRequest", patch("/admin/institution-requests/" + r2.at("/id").asString(),
        Map.of("action", "merge", "institutionId", metroReq.at("/similar/0/id").asString()), admin));
    var aliases = contentJdbc.queryForObject("select array_to_string(aliases, '|') from institutions where slug = 'metropolitan-university'", Map.of(), String.class);
    assertThat(List.of(aliases.split("\\|"))).contains("Metropolitan University Sylhet");
    var again = patch("/admin/institution-requests/" + r2.at("/id").asString(), Map.of("action", "reject"), admin);
    assertThat(again.status()).isEqualTo(409);
    assertThat(patch("/admin/institution-requests/" + r2.at("/id").asString(), Map.of("action", "nope"), admin).status()).isEqualTo(422);
    var actions = coreJdbc.queryForList("select action from audit_logs", Map.of(), String.class);
    assertThat(actions).contains("institution_request.approve", "institution_request.merge");
    var approved = contract("adminCatalog.institutionRequests", get("/admin/institution-requests?status=approved", admin));
    assertThat(approved.at("/items").size()).isEqualTo(1);
  }

  @Test
  void institutionRequestsAreRateLimitedToFivePerHour() {
    var s = createUser("Eager Student");
    for (int i = 0; i < 5; i++) contract("catalog.requestInstitution", post("/institution-requests", Map.of("name", "College number " + i, "type", "college"), s));
    assertThat(post("/institution-requests", Map.of("name", "College number 6", "type", "college"), s).status()).isEqualTo(429);
  }

  @Test
  void adminCatalogCrudAndMergeDuplicatesMovesStudentProfiles() {
    seed();
    var admin = createUser("Admin", "admin");
    var created = contract("adminCatalog.createInstitution", post("/admin/catalog/institutions", Map.of("name", "Metro Uni (dup)", "shortName", "MUD", "slug", "metro-dup", "type", "private"), admin));
    assertThat(created.at("/status").asString()).isEqualTo("approved");
    assertThat(post("/admin/catalog/institutions", Map.of("name", "Another", "shortName", "AN", "slug", "metro-dup", "type", "private"), admin).status()).isEqualTo(409);
    var badSlug = post("/admin/catalog/institutions", Map.of("name", "Another", "shortName", "AN", "slug", "Bad Slug", "type", "private"), admin);
    assertThat(badSlug.status()).isEqualTo(422);
    assertThat(badSlug.errorMessage()).isEqualTo("ছোট হাতের ইংরেজি, সংখ্যা ও - ব্যবহার করুন");
    String createdId = created.at("/id").asString();

    var s = createUser("Dup Student");
    contract("catalog.academicProfile", put("/me/academic-profile", Map.of("institutionId", createdId), s));
    var m = contract("adminCatalog.mergeInstitution", post("/admin/catalog/institutions/" + createdId + "/merge", Map.of("intoId", inst("metropolitan-university")), admin));
    assertThat(m.at("/ok").asBoolean()).isTrue();
    assertThat(meService.getMe(s.id()).academic().institutionShort()).isEqualTo("MU");
    assertThat(post("/admin/catalog/institutions/" + createdId + "/merge", Map.of("intoId", createdId), admin).status()).isEqualTo(400);
    // the merged slug resolves to the surviving institution
    assertThat(contract("catalog.institution", get("/institutions/metro-dup")).at("/slug").asString()).isEqualTo("metropolitan-university");
    var muAliases = contentJdbc.queryForObject("select array_to_string(aliases, '|') from institutions where slug = 'metropolitan-university'", Map.of(), String.class);
    assertThat(List.of(muAliases.split("\\|"))).contains("Metro Uni (dup)", "MUD");

    var dept = post("/admin/catalog/departments", Map.of("institutionId", inst("metropolitan-university"), "name", "Computer Science again", "shortName", "CSE"), admin);
    assertThat(dept.status()).isEqualTo(409);
    assertThat(dept.errorMessage()).isEqualTo("এই সংক্ষিপ্ত নামে বিভাগ আগেই আছে।");
    var subj = contract("adminCatalog.createSubject", post("/admin/catalog/subjects", Map.of("name", "Compiler Design", "slug", "compiler-design", "aliases", List.of("Compiler")), admin));
    var map = contract("adminCatalog.createInstitutionSubject", post("/admin/catalog/institution-subjects", Map.of("institutionId", inst("metropolitan-university"),
        "departmentId", dept("metropolitan-university", "CSE"), "semester", 8, "subjectMasterId", subj.at("/id").asString(), "courseCode", "CSE-4201"), admin));
    assertThat(map.at("/id").asString()).isNotBlank();
  }

  @Test
  void adminCatalogListsPatchesAndDeletes() {
    seed();
    var admin = createUser("Admin", "admin");
    var list = contract("adminCatalog.institutions", get("/admin/catalog/institutions?q=Metropolitan", admin));
    var mu = find(list.at("/items"), "slug", "metropolitan-university");
    assertThat(mu.get("courseCount").asInt()).isPositive();
    assertThat(contract("adminCatalog.institutions", get("/admin/catalog/institutions?status=merged", admin)).at("/items").size()).isZero();

    var upd = contract("adminCatalog.updateInstitution", patch("/admin/catalog/institutions/" + mu.get("id").asString(), Map.of("city", "Sylhet", "aliases", List.of("MU", "Metro")), admin));
    assertThat(upd.at("/city").asString()).isEqualTo("Sylhet");
    assertThat(upd.at("/name").asString()).isEqualTo("Metropolitan University");
    assertThat(upd.at("/aliases").size()).isEqualTo(2);
    assertThat(patch("/admin/catalog/institutions/" + mu.get("id").asString(), Map.of("name", (Object) "Metropolitan University"), admin).status()).isEqualTo(200);
    var nullName = new HashMap<String, Object>();
    nullName.put("name", null);
    assertThat(patch("/admin/catalog/institutions/" + mu.get("id").asString(), nullName, admin).status()).isEqualTo(422);
    assertThat(patch("/admin/catalog/institutions/" + UUID.randomUUID(), Map.of("city", "x"), admin).status()).isEqualTo(404);

    var d = contract("adminCatalog.createDepartment", post("/admin/catalog/departments", Map.of("institutionId", mu.get("id").asString(), "name", "Architecture", "shortName", "ARCH"), admin));
    var d2 = contract("adminCatalog.updateDepartment", patch("/admin/catalog/departments/" + d.at("/id").asString(), Map.of("nameBn", "স্থাপত্য"), admin));
    assertThat(d2.at("/nameBn").asString()).isEqualTo("স্থাপত্য");
    assertThat(d2.at("/shortName").asString()).isEqualTo("ARCH");
    var p = contract("adminCatalog.createProgram", post("/admin/catalog/programs", Map.of("departmentId", d.at("/id").asString(), "name", "B.Arch"), admin));
    assertThat(p.at("/level").asString()).isEqualTo("undergraduate");
    assertThat(p.at("/semesters").asInt()).isEqualTo(8);
    contract("adminCatalog.deleteProgram", delete("/admin/catalog/programs/" + p.at("/id").asString(), admin));
    contract("adminCatalog.deleteDepartment", delete("/admin/catalog/departments/" + d.at("/id").asString(), admin));
    assertThat(contentJdbc.queryForObject("select count(*) from departments where id = :id", Map.of("id", UUID.fromString(d.at("/id").asString())), Long.class)).isZero();
    // a department with course targets cannot be deleted
    var used = delete("/admin/catalog/departments/" + dept("metropolitan-university", "CSE"), admin);
    assertThat(used.status()).isEqualTo(409);

    var subs = contract("adminCatalog.subjects", get("/admin/catalog/subjects?q=data", admin));
    var ds = find(subs.at("/items"), "slug", "data-structures");
    assertThat(ds.get("status").asString()).isEqualTo("approved");
    var us = contract("adminCatalog.updateSubject", patch("/admin/catalog/subjects/" + ds.get("id").asString(), Map.of("status", "proposed"), admin));
    assertThat(us.at("/slug").asString()).isEqualTo("data-structures");
    assertThat(get("/subjects/data-structures").status()).isEqualTo(404);
    assertThat(patch("/admin/catalog/subjects/" + ds.get("id").asString(), Map.of("slug", "database-management-systems"), admin).status()).isEqualTo(409);

    var is = contract("adminCatalog.createInstitutionSubject", post("/admin/catalog/institution-subjects", Map.of("institutionId", mu.get("id").asString(), "subjectMasterId", ds.get("id").asString()), admin));
    contract("adminCatalog.deleteInstitutionSubject", delete("/admin/catalog/institution-subjects/" + is.at("/id").asString(), admin));
    var actions = new ArrayList<>(coreJdbc.queryForList("select action from audit_logs", Map.of(), String.class));
    assertThat(actions).contains("institution.update", "department.create", "department.update", "program.create", "program.delete", "department.delete",
        "subject.update", "institution_subject.create", "institution_subject.delete");
  }
}
