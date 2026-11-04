package com.shikkhasathi.exam.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.exam.dto.ExamDtos.*;
import com.shikkhasathi.exam.repository.ExamContentRepository;
import com.shikkhasathi.exam.repository.ExamContentRepository.CurriculumLesson;
import com.shikkhasathi.exam.repository.ExamCoreRepository;
import com.shikkhasathi.exam.repository.ExamCoreRepository.AttemptRow;
import com.shikkhasathi.exam.repository.ExamCoreRepository.PlanRow;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.student.service.DashboardCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exam Mode (spec §6.9): exam plans with countdown, readiness and a generated daily plan, syllabus checklist,
 * flashcards with known / not-yet reviews, and timed mock-test attempts (start/resume, autosave, submit, result).
 */
@Service
public class ExamService {
  private static final Logger log = LoggerFactory.getLogger(ExamService.class);
  static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
  static final long GRACE_MS = 30_000;

  private final ExamCoreRepository core;
  private final ExamContentRepository content;
  private final DashboardCache dashboard;
  private final NotificationService notifications;
  private final JsonMapper json;

  public ExamService(ExamCoreRepository core, ExamContentRepository content, DashboardCache dashboard, NotificationService notifications, JsonMapper json) {
    this.core = core;
    this.content = content;
    this.dashboard = dashboard;
    this.notifications = notifications;
    this.json = json;
  }

  public static String dhakaDay(Instant i) {
    return LocalDate.ofInstant(i, DHAKA).toString();
  }

  static Instant dhakaStart(String ymd) {
    return LocalDate.parse(ymd).atStartOfDay(DHAKA).toInstant();
  }

  /** ISO date-time with offset (validated by the DTO pattern) → instant. */
  static Instant parseDate(String s) {
    String n = s.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2").replaceAll("([+-]\\d{2})$", "$1:00");
    try {
      return OffsetDateTime.parse(n).toInstant();
    } catch (DateTimeParseException e) {
      throw ApiException.validation(null, List.of(Map.of("path", "body.examDate", "message", "Invalid ISO datetime")));
    }
  }

  static PlanSummary summary(PlanRow p, Instant now) {
    return new PlanSummary(p.id(), p.subjectName(), p.courseCode(), p.examType(), p.examDate(),
        Math.max(0, Planner.daysBetween(dhakaDay(now), dhakaDay(p.examDate()))), p.readiness(), p.courseIds(), p.active(),
        p.examDate().toEpochMilli() < now.toEpochMilli() - 12 * 3_600_000L);
  }

  List<UUID> activeCourseIds(UUID userId, Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    Instant now = Instant.now();
    return core.activeEnrollments(userId, ids).stream().filter(r -> r.accessUntil() == null || r.accessUntil().isAfter(now)).map(ExamCoreRepository.EnrollmentRow::courseId).toList();
  }

  private PlanRow ownPlan(UUID userId, UUID id) {
    PlanRow p = core.findPlan(id).orElseThrow(() -> ApiException.notFound("পরীক্ষার প্ল্যানটি পাওয়া যায়নি।"));
    if (!p.studentId().equals(userId)) throw ApiException.forbidden();
    return p;
  }

  // ---------------------------------------------------------------- CRUD

  public PlanList listPlans(UUID userId) {
    Instant now = Instant.now();
    List<PlanSummary> items = new ArrayList<>(core.plansOf(userId).stream().map(r -> summary(r, now)).toList());
    // upcoming active first (nearest), then past/archived (most recent)
    Comparator<PlanSummary> cmp = (a, b) -> {
      int g = Integer.compare((a.past() || !a.active()) ? 1 : 0, (b.past() || !b.active()) ? 1 : 0);
      if (g != 0) return g;
      return a.past() ? b.examDate().compareTo(a.examDate()) : a.examDate().compareTo(b.examDate());
    };
    // stable insertion sort: the comparator is not a strict total order (same as before), so avoid TimSort's contract check
    for (int i = 1; i < items.size(); i++) {
      PlanSummary x = items.get(i);
      int j = i - 1;
      while (j >= 0 && cmp.compare(items.get(j), x) > 0) {
        items.set(j + 1, items.get(j));
        j--;
      }
      items.set(j + 1, x);
    }
    return new PlanList(items);
  }

  public PlanSummary createPlan(UUID userId, CreatePlan b) {
    Instant date = parseDate(b.examDate());
    long nowMs = System.currentTimeMillis();
    if (date.toEpochMilli() < nowMs - 3_600_000L) throw ApiException.badRequest("পরীক্ষার তারিখ আজ বা সামনের কোনো দিন দিন।");
    if (date.toEpochMilli() > nowMs + 365L * 86_400_000L) throw ApiException.badRequest("এক বছরের মধ্যের তারিখ দিন।");
    List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(b.courseIds()));
    if (activeCourseIds(userId, ids).size() != ids.size()) throw ApiException.forbidden("শুধু আপনার কেনা কোর্স যুক্ত করা যায়।");
    if (core.countActiveUpcoming(userId) >= 12) throw ApiException.conflict("একসঙ্গে সর্বোচ্চ ১২টি পরীক্ষার প্ল্যান রাখা যায়।");
    UUID id = core.insertPlan(userId, b.subjectName(), b.subjectMasterId(), b.courseCode(), b.examType(), date, ids);
    refreshReadiness(core.findPlan(id).orElseThrow());
    dashboard.invalidate(userId);
    return summary(core.findPlan(id).orElseThrow(), Instant.now());
  }

  public PlanSummary updatePlan(UUID userId, UUID id, UpdatePlan b) {
    ownPlan(userId, id);
    Map<String, Object> set = new LinkedHashMap<>();
    if (b.has("subjectName")) set.put("subject_name", b.getSubjectName());
    if (b.has("courseCode")) set.put("course_code", b.getCourseCode());
    if (b.has("subjectMasterId")) set.put("subject_master_id", b.getSubjectMasterId());
    if (b.has("examType")) set.put("exam_type", b.getExamType());
    if (b.has("active")) set.put("active", b.getActive());
    if (b.has("examDate")) set.put("exam_date", parseDate(b.getExamDate()));
    if (b.has("courseIds")) {
      List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(b.getCourseIds()));
      if (activeCourseIds(userId, ids).size() != ids.size()) throw ApiException.forbidden("শুধু আপনার কেনা কোর্স যুক্ত করা যায়।");
      set.put("course_ids", ids);
    }
    core.updatePlan(id, set);
    refreshReadiness(core.findPlan(id).orElseThrow());
    dashboard.invalidate(userId);
    return summary(core.findPlan(id).orElseThrow(), Instant.now());
  }

  public void deletePlan(UUID userId, UUID id) {
    ownPlan(userId, id);
    core.deletePlan(id);
    dashboard.invalidate(userId);
  }

  // ---------------------------------------------------------------- plan detail

  List<MockSummary> mockSummaries(UUID userId, List<UUID> courseIds) {
    if (courseIds.isEmpty()) return List.of();
    var tests = content.testsOfCourses(courseIds);
    if (tests.isEmpty()) return List.of();
    List<UUID> testIds = tests.stream().map(ExamContentRepository.TestRow::id).toList();
    Map<UUID, Integer> qc = content.questionCounts(testIds);
    Map<UUID, String> titles = content.courses(courseIds).stream().collect(Collectors.toMap(ExamContentRepository.CourseRow::id, ExamContentRepository.CourseRow::title, (x, y) -> x));
    List<AttemptRow> atts = core.attemptsOf(userId, testIds);
    long now = System.currentTimeMillis();
    List<MockSummary> out = new ArrayList<>();
    for (var t : tests) {
      List<AttemptRow> mine = atts.stream().filter(a -> a.testId().equals(t.id())).toList();
      List<AttemptRow> scored = mine.stream().filter(a -> a.submittedAt() != null && a.score() != null).toList();
      AttemptRow open = mine.stream().filter(a -> a.submittedAt() == null && a.deadlineAt().toEpochMilli() + GRACE_MS > now).findFirst().orElse(null);
      out.add(new MockSummary(t.id(), t.courseId(), titles.getOrDefault(t.courseId(), ""), t.title(), qc.getOrDefault(t.id(), 0), t.durationSec(), scored.size(),
          scored.isEmpty() ? null : scored.stream().mapToInt(AttemptRow::score).max().getAsInt(), scored.isEmpty() ? null : scored.get(0).score(), open == null ? null : open.id()));
    }
    return out;
  }

  FlashStats flashStats(UUID userId, List<UUID> courseIds) {
    if (courseIds.isEmpty()) return new FlashStats(0, 0);
    int t = content.countFlashcards(courseIds);
    int k = core.countKnown(userId, courseIds);
    return new FlashStats(t, Math.min(k, t));
  }

  record Refreshed(Planner.Readiness r, List<UUID> ids, List<CurriculumLesson> ls, List<MockSummary> mocks, FlashStats fl, Set<UUID> doneIds) {}

  /** Recompute + store readiness (dashboard widget reads the column). */
  Refreshed refreshReadiness(PlanRow p) {
    List<UUID> ids = activeCourseIds(p.studentId(), p.courseIds());
    var ls = content.curriculum(ids);
    var mocks = mockSummaries(p.studentId(), ids);
    var fl = flashStats(p.studentId(), ids);
    List<UUID> done = ls.isEmpty() ? List.of() : core.completedLessons(p.studentId(), ls.stream().map(CurriculumLesson::id).toList());
    var scored = mocks.stream().filter(m -> m.bestScore() != null).toList();
    Integer mockScore = mocks.isEmpty() ? null : scored.isEmpty() ? 0 : (int) Math.round((double) scored.stream().mapToInt(MockSummary::bestScore).sum() / mocks.size());
    var r = Planner.readiness(done.size(), ls.size(), mockScore, fl.known(), fl.total());
    if (r.total() != p.readiness()) core.setReadiness(p.id(), r.total());
    return new Refreshed(r, ids, ls, mocks, fl, new HashSet<>(done));
  }

  static PlanDay toPlanDay(Planner.GenDay d) {
    List<PlanItem> items = new ArrayList<>();
    for (var i : d.items) {
      items.add(switch (i) {
        case Planner.LessonItem l -> new LessonPlanItem("lesson", UUID.fromString(l.lesson().id()), UUID.fromString(l.lesson().courseId()), l.lesson().title(), l.lesson().chapterTitle(),
            l.lesson().durationSec(), l.lesson().type(), l.lesson().isImportant(), l.done());
        case Planner.MockItem m -> new MockPlanItem("mock", UUID.fromString(m.mock().id()), m.mock().title(), m.mock().questionCount(), m.done());
        case Planner.FlashcardsItem f -> new FlashcardsPlanItem("flashcards", f.count(), f.done());
        case Planner.RevisionItem r -> new RevisionPlanItem("revision", "পরীক্ষার আগের রাতের রিভিশন", false);
      });
    }
    return new PlanDay(d.date, items, d.totalMinutes, d.isRevision);
  }

  public PlanDetail planDetail(UUID userId, UUID id) {
    return planDetail(userId, id, Instant.now());
  }

  public PlanDetail planDetail(UUID userId, UUID id, Instant now) {
    PlanRow p = ownPlan(userId, id);
    Refreshed rf = refreshReadiness(p);
    var ls = rf.ls();
    var ids = rf.ids();
    var doneIds = rf.doneIds();
    String today = dhakaDay(now);
    Instant todayStart = dhakaStart(today);
    Set<UUID> todaySet = new HashSet<>(ls.isEmpty() ? List.of() : core.completedLessonsSince(userId, ls.stream().map(CurriculumLesson::id).toList(), todayStart));
    List<Planner.PlanLesson> planLessons = ls.stream().map(l -> new Planner.PlanLesson(l.id().toString(), l.courseId().toString(), l.title(), l.chapterTitle(), l.durationSec(), l.type(),
        l.isImportant(), doneIds.contains(l.id()) && !todaySet.contains(l.id()), todaySet.contains(l.id()))).toList();
    List<Planner.PlanMock> planMocks = rf.mocks().stream().map(m -> new Planner.PlanMock(m.id().toString(), m.title(), m.questionCount(), m.attempts() > 0)).toList();
    List<PlanDay> days = Planner.generatePlan(new Planner.Input(today, dhakaDay(p.examDate()), planLessons, planMocks, new Planner.Flash(rf.fl().total(), rf.fl().known())))
        .stream().map(ExamService::toPlanDay).toList();

    var cs = content.courses(p.courseIds());
    var docs = content.previousQuestions(ids);
    List<UUID> enr = activeCourseIds(userId, p.courseIds());
    int nCount = core.countNotes(userId, ids);
    int bCount = core.countBookmarks(userId, ids);
    int wrong = rf.mocks().isEmpty() ? 0 : core.sumWrong(userId, rf.mocks().stream().map(MockSummary::id).toList());

    // syllabus checklist by chapter
    record Ch(UUID courseId, UUID chapterId, String title, String sectionTitle, int[] n) {}
    Map<UUID, Ch> chapters = new LinkedHashMap<>();
    for (var l : ls) {
      Ch c = chapters.computeIfAbsent(l.chapterId(), k -> new Ch(l.courseId(), l.chapterId(), l.chapterTitle(), l.sectionTitle(), new int[3]));
      c.n()[1]++;
      if (doneIds.contains(l.id())) c.n()[0]++;
      if (l.isImportant()) c.n()[2]++;
    }
    int daysLeft = Planner.daysBetween(today, dhakaDay(p.examDate()));
    PlanSummary s = summary(p.withReadiness(rf.r().total()), now);
    return new PlanDetail(s.id(), s.subjectName(), s.courseCode(), s.examType(), s.examDate(), s.daysLeft(), s.readiness(), s.courseIds(), s.active(), s.past(),
        new ReadinessBreakdown(rf.r().lessons(), rf.r().mock(), rf.r().flashcards()),
        cs.stream().map(c -> new PlanCourse(c.id(), c.slug(), c.title(), enr.contains(c.id()))).toList(),
        days.stream().filter(d -> d.date().equals(today)).findFirst().orElse(null),
        days,
        chapters.values().stream().map(c -> new SyllabusChapter(c.courseId(), c.chapterId(), c.title(), c.sectionTitle(), c.n()[0], c.n()[1], c.n()[2],
            c.n()[0] == 0 ? "todo" : c.n()[0] >= c.n()[1] ? "done" : "doing")).toList(),
        ls.stream().filter(CurriculumLesson::isImportant).map(l -> new ImportantLesson(l.id(), l.courseId(), l.title(), doneIds.contains(l.id()), l.durationSec())).toList(),
        ls.stream().filter(l -> l.type().equals("video") && l.durationSec() > 0 && l.durationSec() <= 10 * 60)
            .map(l -> new ShortLecture(l.id(), l.courseId(), l.title(), l.durationSec(), doneIds.contains(l.id()))).toList(),
        docs.stream().filter(d -> d.courseId() != null).map(d -> new PreviousQuestion(d.id(), d.title(), d.year(), d.courseId())).toList(),
        rf.mocks(),
        rf.fl(),
        daysLeft <= 1,
        new RevisionPack((int) ls.stream().filter(CurriculumLesson::isImportant).count(), nCount, bCount, wrong));
  }

  // ---------------------------------------------------------------- flashcards

  public FlashcardList planFlashcards(UUID userId, UUID id) {
    PlanRow p = ownPlan(userId, id);
    List<UUID> ids = activeCourseIds(userId, p.courseIds());
    if (ids.isEmpty()) return new FlashcardList(List.of());
    var cards = content.flashcards(ids);
    Map<UUID, Boolean> revs = new HashMap<>();
    for (var r : core.reviews(userId, ids)) revs.putIfAbsent(r.flashcardId(), r.known());
    List<FlashcardItem> items = new ArrayList<>(cards.stream().map(c -> new FlashcardItem(c.id(), c.courseId(), c.front(), c.back(), revs.get(c.id()))).toList());
    // not-yet-known first, then unseen, then known (stable)
    Function<Boolean, Integer> rank = k -> Boolean.FALSE.equals(k) ? 0 : k == null ? 1 : 2;
    items.sort(Comparator.comparing(i -> rank.apply(i.known())));
    return new FlashcardList(items);
  }

  public void reviewFlashcard(UUID userId, UUID cardId, boolean known) {
    var c = content.flashcard(cardId).orElseThrow(ApiException::notFound);
    if (activeCourseIds(userId, List.of(c.courseId())).isEmpty()) throw ApiException.forbidden("এই ফ্ল্যাশকার্ড দেখতে কোর্সটি কিনুন।");
    core.upsertReview(userId, cardId, c.courseId(), known);
  }

  // ---------------------------------------------------------------- mock attempts

  static String sha1Hex(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Deterministic shuffle: order by sha1(seed + id), identical to the previous API. */
  static <T> List<T> shuffleBy(List<T> xs, Function<T, UUID> id, String seed) {
    Map<T, String> keys = new IdentityHashMap<>();
    for (T x : xs) keys.put(x, sha1Hex(seed + id.apply(x)));
    List<T> out = new ArrayList<>(xs);
    out.sort(Comparator.comparing(keys::get));
    return out;
  }

  private ExamContentRepository.TestRow testAccess(UUID userId, UUID testId) {
    var t = content.findTest(testId).orElseThrow(() -> ApiException.notFound("মক টেস্টটি পাওয়া যায়নি।"));
    var c = content.course(t.courseId());
    boolean owner = c.isPresent() && userId.equals(c.get().sellerId());
    if (!owner && activeCourseIds(userId, List.of(t.courseId())).isEmpty()) throw ApiException.forbidden("মক টেস্ট দিতে কোর্সটি কিনুন।");
    return t;
  }

  public MockList studentMockTests(UUID userId) {
    return new MockList(mockSummaries(userId, activeCourseIds(userId, core.activeEnrollmentCourseIds(userId))));
  }

  private Attempt toAttempt(AttemptRow a) {
    var t = content.findTest(a.testId());
    var qs = content.questionsByIds(a.questionOrder());
    var ans = core.answers(a.id());
    var opts = content.optionsOf(qs.stream().map(ExamContentRepository.QuestionRow::id).toList());
    Map<UUID, ExamContentRepository.QuestionRow> byId = qs.stream().collect(Collectors.toMap(ExamContentRepository.QuestionRow::id, q -> q));
    List<AttemptQuestion> questions = new ArrayList<>();
    for (UUID qid : a.questionOrder()) {
      var q = byId.get(qid);
      if (q == null) continue;
      var os = shuffleBy(opts.stream().filter(o -> o.questionId().equals(q.id())).toList(), ExamContentRepository.OptionRow::id, a.id().toString());
      questions.add(new AttemptQuestion(q.id(), q.body(), q.chapterTag(), os.stream().map(o -> new AttemptOption(o.id(), o.body())).toList()));
    }
    return new Attempt(a.id(), a.testId(), a.courseId(), t.map(ExamContentRepository.TestRow::title).orElse(""), t.map(ExamContentRepository.TestRow::durationSec).orElse(0),
        a.startedAt(), a.deadlineAt(), Instant.now(), a.submittedAt() != null, questions,
        ans.stream().map(x -> new AttemptAnswer(x.questionId(), x.optionId(), x.flagged())).toList());
  }

  private AttemptRow ownAttempt(UUID userId, UUID id) {
    AttemptRow a = core.findAttempt(id).orElseThrow(ApiException::notFound);
    if (!a.studentId().equals(userId)) throw ApiException.forbidden();
    return a;
  }

  public Attempt startAttempt(UUID userId, UUID testId) {
    var t = testAccess(userId, testId);
    var open = core.latestOpenAttempt(userId, testId);
    if (open.isPresent()) {
      if (open.get().deadlineAt().toEpochMilli() + GRACE_MS > System.currentTimeMillis()) return toAttempt(open.get());
      finalizeAttempt(open.get(), true);
    }
    var qs = content.questionsOfTests(List.of(testId));
    if (qs.isEmpty()) throw ApiException.badRequest("এই মক টেস্টে এখনো প্রশ্ন নেই।");
    String seed = userId + ":" + System.currentTimeMillis();
    List<UUID> order = (t.shuffle() ? shuffleBy(qs, ExamContentRepository.QuestionRow::id, seed) : qs).stream().map(ExamContentRepository.QuestionRow::id).toList();
    Instant now = Instant.now();
    UUID id = core.insertAttempt(testId, t.courseId(), userId, now, now.plusMillis(t.durationSec() * 1000L), order);
    return toAttempt(core.findAttempt(id).orElseThrow());
  }

  public Attempt getAttempt(UUID userId, UUID id) {
    AttemptRow a = ownAttempt(userId, id);
    if (a.submittedAt() == null && a.deadlineAt().toEpochMilli() + GRACE_MS < System.currentTimeMillis()) {
      finalizeAttempt(a, true);
      return toAttempt(core.findAttempt(id).orElseThrow());
    }
    return toAttempt(a);
  }

  public void saveAnswer(UUID userId, UUID id, SaveAnswer b) {
    AttemptRow a = ownAttempt(userId, id);
    if (a.submittedAt() != null) throw ApiException.conflict("এই মক টেস্ট জমা হয়ে গেছে।");
    if (a.deadlineAt().toEpochMilli() + GRACE_MS < System.currentTimeMillis()) {
      finalizeAttempt(a, true);
      throw ApiException.conflict("সময় শেষ — উত্তর স্বয়ংক্রিয়ভাবে জমা হয়েছে।");
    }
    if (!a.questionOrder().contains(b.questionId())) throw ApiException.badRequest("প্রশ্নটি এই টেস্টের নয়।");
    if (b.optionId() != null) {
      var q = content.optionQuestion(b.optionId());
      if (q.isEmpty() || !q.get().equals(b.questionId())) throw ApiException.badRequest("উত্তরটি এই প্রশ্নের নয়।");
    }
    core.upsertAnswer(id, b.questionId(), b.optionId(), b.flagged());
  }

  void finalizeAttempt(AttemptRow a, boolean auto) {
    var qs = content.questionsByIds(a.questionOrder());
    var ans = core.answers(a.id());
    var correctOpts = content.optionsOf(qs.stream().map(ExamContentRepository.QuestionRow::id).toList()).stream().filter(ExamContentRepository.OptionRow::isCorrect).toList();
    int correct = 0;
    int wrong = 0;
    Map<String, int[]> chapters = new LinkedHashMap<>();
    for (var q : qs) {
      UUID chosen = ans.stream().filter(x -> x.questionId().equals(q.id())).map(ExamCoreRepository.AnswerRow::optionId).filter(Objects::nonNull).findFirst().orElse(null);
      boolean ok = chosen != null && correctOpts.stream().anyMatch(o -> o.questionId().equals(q.id()) && o.id().equals(chosen));
      if (chosen != null && ok) correct++;
      else if (chosen != null) wrong++;
      String tag = q.chapterTag() == null || q.chapterTag().isEmpty() ? "অন্যান্য" : q.chapterTag();
      int[] c = chapters.computeIfAbsent(tag, k -> new int[2]);
      c[1]++;
      if (ok) c[0]++;
    }
    int total = qs.size();
    long nowMs = System.currentTimeMillis();
    Instant submittedAt = auto ? Instant.ofEpochMilli(Math.min(nowMs, a.deadlineAt().toEpochMilli())) : Instant.ofEpochMilli(nowMs);
    Map<String, Object> rj = new LinkedHashMap<>();
    rj.put("auto", auto);
    rj.put("chapters", chapters.entrySet().stream().map(e -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("tag", e.getKey());
      m.put("correct", e.getValue()[0]);
      m.put("total", e.getValue()[1]);
      return m;
    }).toList());
    int score = total != 0 ? (int) Math.round((double) correct / total * 100) : 0;
    boolean updated = core.finalizeAttempt(a.id(), submittedAt, score, correct, wrong, total - correct - wrong, json.writeValueAsString(rj));
    if (updated) {
      for (var p : core.activePlansWithCourse(a.studentId(), a.courseId())) refreshReadiness(p);
      dashboard.invalidate(a.studentId());
    }
  }

  public AttemptResult submitAttempt(UUID userId, UUID id) {
    AttemptRow a = ownAttempt(userId, id);
    if (a.submittedAt() == null) finalizeAttempt(a, a.deadlineAt().toEpochMilli() + GRACE_MS < System.currentTimeMillis());
    return attemptResult(userId, id);
  }

  public AttemptResult attemptResult(UUID userId, UUID id) {
    AttemptRow a = ownAttempt(userId, id);
    if (a.submittedAt() == null) {
      if (a.deadlineAt().toEpochMilli() + GRACE_MS > System.currentTimeMillis()) throw ApiException.conflict("টেস্টটি এখনো চলছে।");
      finalizeAttempt(a, true);
      a = ownAttempt(userId, id);
    }
    var t = content.findTest(a.testId());
    var qs = content.questionsByIds(a.questionOrder());
    var ans = core.answers(a.id());
    var all = core.submittedScores(userId, a.testId());
    var opts = content.optionsOf(qs.stream().map(ExamContentRepository.QuestionRow::id).toList());
    boolean auto = false;
    List<ChapterScore> chapters = new ArrayList<>();
    if (a.resultJson() != null) {
      JsonNode rj = json.readTree(a.resultJson());
      auto = rj.path("auto").asBoolean(false);
      for (JsonNode c : rj.path("chapters")) chapters.add(new ChapterScore(c.path("tag").asString(), c.path("correct").asInt(), c.path("total").asInt()));
    }
    Map<UUID, ExamContentRepository.QuestionRow> byId = qs.stream().collect(Collectors.toMap(ExamContentRepository.QuestionRow::id, q -> q));
    List<ReviewItem> review = new ArrayList<>();
    for (UUID qid : a.questionOrder()) {
      var q = byId.get(qid);
      if (q == null) continue;
      var os = shuffleBy(opts.stream().filter(o -> o.questionId().equals(q.id())).toList(), ExamContentRepository.OptionRow::id, a.id().toString());
      UUID chosen = ans.stream().filter(x -> x.questionId().equals(q.id())).findFirst().map(ExamCoreRepository.AnswerRow::optionId).orElse(null);
      UUID right = os.stream().filter(ExamContentRepository.OptionRow::isCorrect).findFirst().map(ExamContentRepository.OptionRow::id).orElse(null);
      review.add(new ReviewItem(q.id(), q.body(), q.chapterTag(), q.explanation(), chosen, right, os.stream().map(o -> new AttemptOption(o.id(), o.body())).toList()));
    }
    Instant end = a.submittedAt() != null ? a.submittedAt() : Instant.now();
    int best = 0;
    for (Integer s : all) best = Math.max(best, s == null ? 0 : s);
    return new AttemptResult(a.id(), a.testId(), a.courseId(), t.map(ExamContentRepository.TestRow::title).orElse(""),
        a.score() == null ? 0 : a.score(), a.correctCount() == null ? 0 : a.correctCount(), a.wrongCount() == null ? 0 : a.wrongCount(),
        a.skippedCount() == null ? 0 : a.skippedCount(), a.questionOrder().size(),
        (int) Math.max(0, Math.round(Duration.between(a.startedAt(), end).toMillis() / 1000.0)), auto, best, all.size(), chapters, review);
  }

  // ---------------------------------------------------------------- jobs

  /** exam.replan (01:00): refresh readiness for active upcoming plans; archive plans 2 days after the exam. */
  public int replanAll() {
    var plans = core.activePlansFrom(Instant.now().minus(Duration.ofDays(2)), 5000);
    for (var p : plans) refreshReadiness(p);
    core.archivePast();
    return plans.size();
  }

  /** exam.dailyPlan (08:00 Dhaka): "আজকের প্ল্যান" push for each upcoming plan with work today. */
  public int dailyPlanNudges(Instant now) {
    var plans = core.activePlansFrom(now, 5000);
    int sent = 0;
    for (var p : plans) {
      PlanDetail d;
      try {
        d = planDetail(p.studentId(), p.id(), now);
      } catch (RuntimeException e) {
        log.debug("daily plan skipped for {}: {}", p.id(), e.getMessage());
        continue;
      }
      if (d.today() == null) continue;
      var items = d.today().items().stream().filter(i -> !i.done()).toList();
      if (items.isEmpty()) continue;
      long lessonsN = items.stream().filter(i -> i instanceof LessonPlanItem).count();
      int mins = 0;
      for (var i : d.today().items()) if (i instanceof LessonPlanItem l) mins += Planner.effectiveMinutes(l.durationSec(), l.lessonType());
      String body = d.today().isRevision()
          ? "আজ রিভিশনের দিন — মক টেস্ট আর ফ্ল্যাশকার্ড দিয়ে শেষবার ঝালিয়ে নিন।"
          : lessonsN + "টি লেসন · প্রায় " + mins + " মিনিট। পরীক্ষার আর " + d.daysLeft() + " দিন বাকি।";
      if (notifications.notify(NotifyInput.of(p.studentId(), NotificationTypes.EXAM_DAILY_PLAN, p.id() + ":" + dhakaDay(now), "আজকের প্ল্যান: " + p.subjectName(), body)
          .withLink("/exam-mode/" + p.id()))) sent++;
    }
    return sent;
  }
}
