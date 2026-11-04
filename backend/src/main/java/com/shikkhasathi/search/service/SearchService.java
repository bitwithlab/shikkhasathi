package com.shikkhasathi.search.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.search.adapter.MeiliClient;
import com.shikkhasathi.search.adapter.SearchEngine;
import com.shikkhasathi.search.dto.SearchDtos.CourseCard;
import com.shikkhasathi.search.dto.SearchDtos.SearchQuery;
import com.shikkhasathi.search.dto.SearchDtos.SearchResponse;
import com.shikkhasathi.search.dto.SearchDtos.SuggestResponse;
import com.shikkhasathi.search.dto.SearchDtos.Suggestion;
import com.shikkhasathi.search.repository.CourseRows.CourseRow;
import com.shikkhasathi.search.repository.CourseRows.TargetRow;
import com.shikkhasathi.search.repository.SearchRepository;
import com.shikkhasathi.search.repository.SearchRepository.TextMatch;
import com.shikkhasathi.search.service.Ranking.RankCourse;
import com.shikkhasathi.search.service.Ranking.RankStudent;
import com.shikkhasathi.search.service.Ranking.RankTarget;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Smart search (spec §6): Meilisearch or Postgres candidates, re-ranked with the §6.5 score; autocomplete; course cards. */
@Service
public class SearchService {
  private final SearchRepository repo;
  private final SearchEngine engine;
  private final AcademicLookup academic;

  public SearchService(SearchRepository repo, SearchEngine engine, AcademicLookup academic) {
    this.repo = repo;
    this.engine = engine;
    this.academic = academic;
  }

  // ---------------------------------------------------------------- student context

  public RankStudent studentContext(UUID userId) {
    if (userId == null) return null;
    var p = repo.studentProfile(userId);
    var exams = repo.activeExams(userId);
    List<UUID> subjectIds = new ArrayList<>();
    if (p != null && p.institutionId() != null) subjectIds.addAll(repo.institutionSubjectIds(p.institutionId(), p.departmentId(), p.semester()));
    var all = new LinkedHashSet<>(subjectIds);
    exams.stream().map(Ranking.Exam::subjectMasterId).filter(Objects::nonNull).forEach(all::add);
    return new RankStudent(p == null ? null : p.institutionId(), p == null ? null : p.departmentId(), p == null ? null : p.semester(), p == null ? null : p.batch(), List.copyOf(all), exams);
  }

  // ---------------------------------------------------------------- cards

  public static List<RankTarget> rankTargets(List<TargetRow> ts) {
    return ts.stream().map(t -> new RankTarget(t.isGeneral(), t.institutionId(), t.departmentId(), t.semester(), t.subjectMasterId(), t.batches())).toList();
  }

  /** Prefer the student's own institution, then the first specific target, then any. */
  public static TargetRow primaryTarget(List<TargetRow> ts, RankStudent student) {
    if (student != null && student.institutionId() != null)
      for (TargetRow t : ts) if (student.institutionId().equals(t.institutionId())) return t;
    for (TargetRow t : ts) if (!t.isGeneral()) return t;
    return ts.isEmpty() ? null : ts.get(0);
  }

  public List<CourseCard> toCards(List<CourseRow> rows, RankStudent student) {
    return toCards(rows, student, null);
  }

  /** Course card DTOs for rows (batched cross-DB lookups). */
  public List<CourseCard> toCards(List<CourseRow> rows, RankStudent student, Map<UUID, Double> scores) {
    if (rows.isEmpty()) return List.of();
    var targets = repo.targets(rows.stream().map(CourseRow::id).toList());
    var slugs = repo.sellerSlugs(rows.stream().map(CourseRow::sellerId).collect(Collectors.toSet()));
    var names = academic.lookup(targets.stream().map(TargetRow::institutionId).filter(Objects::nonNull).toList(), List.of(), List.of());
    Map<UUID, List<TargetRow>> byCourse = group(targets);
    List<CourseCard> out = new ArrayList<>();
    for (CourseRow c : rows) {
      var ts = byCourse.getOrDefault(c.id(), List.of());
      TargetRow primary = primaryTarget(ts, student);
      String instShort = primary != null && primary.institutionId() != null && names.institution().containsKey(primary.institutionId()) ? names.institution().get(primary.institutionId()).shortName() : null;
      Double score = scores != null && scores.containsKey(c.id()) ? Math.round(scores.get(c.id()) * 10) / 10.0 : null;
      out.add(new CourseCard(c.id(), c.slug(), c.title(), c.shortDescription(), primary == null ? null : primary.courseCode(), instShort, c.sellerName(), slugs.get(c.sellerId()),
          c.sellerVerified(), c.examType(), c.courseType(), c.price(), c.compareAtPrice(), c.ratingAvg(), c.ratingCount(), c.coverUrl(), c.coverColor(), c.lessonCount(),
          c.totalDurationSec(), c.enrollmentCount(), Ranking.compatibility(rankTargets(ts), student), score));
    }
    return out;
  }

  public static Map<UUID, List<TargetRow>> group(List<TargetRow> targets) {
    Map<UUID, List<TargetRow>> m = new HashMap<>();
    for (TargetRow t : targets) m.computeIfAbsent(t.courseId(), k -> new ArrayList<>()).add(t);
    return m;
  }

  // ---------------------------------------------------------------- search

  /** Text relevance for the Postgres engine (Meili provides its own ordering). */
  static double textScore(CourseRow c, List<TargetRow> ts, List<String> terms, String code, Set<UUID> subjectHits, Set<UUID> instHits) {
    double s = 0;
    String title = c.title().toLowerCase(Locale.ROOT);
    if (code != null && ts.stream().anyMatch(t -> t.courseCode() != null && !t.courseCode().isEmpty() && QueryExpansion.normalizeCode(t.courseCode()).equals(code))) s += 40;
    if (terms.stream().anyMatch(title::contains)) s += 30;
    if (ts.stream().anyMatch(t -> t.subjectMasterId() != null && subjectHits.contains(t.subjectMasterId()))) s += 20;
    if (ts.stream().anyMatch(t -> t.institutionId() != null && instHits.contains(t.institutionId()))) s += 15;
    String seller = c.sellerName().toLowerCase(Locale.ROOT);
    if (terms.stream().anyMatch(seller::contains)) s += 10;
    String sd = c.shortDescription().toLowerCase(Locale.ROOT);
    if (terms.stream().anyMatch(sd::contains)) s += 5;
    return s;
  }

  static String encodeOffset(int n) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(n).getBytes(StandardCharsets.UTF_8));
  }

  static int decodeOffset(String c) {
    if (c == null || c.isEmpty()) return 0;
    try {
      String s = new String(Base64.getUrlDecoder().decode(c), StandardCharsets.UTF_8).trim();
      double n = s.isEmpty() ? 0 : Double.parseDouble(s);
      return Double.isFinite(n) && n >= 0 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(n)) : 0;
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static boolean has(String s) {
    return s != null && !s.isEmpty();
  }

  public SearchResponse search(SearchQuery q, UUID userId) {
    RankStudent student = studentContext(userId);
    var exp = QueryExpansion.expand(q.q() == null ? "" : q.q());
    String examType = q.examType() != null ? q.examType() : exp.examType();

    // ---- candidate ids: Meili when configured, else Postgres
    String engineName = "postgres";
    List<String> meiliIds = null;
    var m = engine.meili();
    if (m.isPresent() && has(q.q())) {
      try {
        List<String> filter = new ArrayList<>();
        if (q.institutionId() != null) filter.add(MeiliClient.eq("institutionIds", q.institutionId().toString()));
        if (q.subjectId() != null) filter.add(MeiliClient.eq("subjectIds", q.subjectId().toString()));
        if (q.examType() != null) filter.add(MeiliClient.eq("examType", q.examType()));
        meiliIds = m.get().searchIds(q.q(), filter, 300);
        engineName = "meilisearch";
      } catch (RuntimeException e) {
        meiliIds = null;
      }
    }

    Set<UUID> subjectHits = Set.of();
    Set<UUID> instHits = Set.of();
    TextMatch text = null;
    if (meiliIds == null && has(q.q()) && (!exp.terms().isEmpty() || exp.code() != null)) {
      subjectHits = new LinkedHashSet<>(repo.subjectHits(exp.terms()));
      instHits = new LinkedHashSet<>(repo.institutionHits(exp.terms()));
      text = new TextMatch(exp.terms(), exp.code(), List.copyOf(subjectHits), List.copyOf(instHits));
    }

    List<CourseRow> rows = repo.searchCandidates(q, examType, meiliIds, text);
    var byCourse = group(repo.targets(rows.stream().map(CourseRow::id).toList()));
    Instant now = Instant.now();
    Map<UUID, Double> scores = new HashMap<>();
    for (CourseRow c : rows) {
      var ts = byCourse.getOrDefault(c.id(), List.of());
      double base = Ranking.rankScore(new RankCourse(c.examType(), c.ratingAvg(), c.ratingCount(), c.enrollments30d(), c.sellerVerified(), c.effectiveUpdatedAt(), rankTargets(ts)), student, now);
      double t;
      if (has(q.q()) && meiliIds == null) t = textScore(c, ts, exp.terms(), exp.code(), subjectHits, instHits);
      else if (meiliIds != null) t = 60 - Math.min(60, meiliIds.indexOf(c.id().toString()));
      else t = 0;
      scores.put(c.id(), base + t);
    }
    Comparator<CourseRow> cmp = switch (q.sort()) {
      case "newest" -> Comparator.comparingLong((CourseRow c) -> c.publishedAt() == null ? 0 : c.publishedAt().toEpochMilli()).reversed();
      case "rating" -> Comparator.comparingDouble(CourseRow::ratingAvg).reversed().thenComparing(Comparator.comparingInt(CourseRow::ratingCount).reversed());
      case "price_asc" -> Comparator.comparingLong(CourseRow::price);
      case "price_desc" -> Comparator.comparingLong(CourseRow::price).reversed();
      case "popular" -> Comparator.comparingInt(CourseRow::enrollments30d).reversed().thenComparing(Comparator.comparingInt(CourseRow::enrollmentCount).reversed());
      default -> Comparator.comparingDouble((CourseRow c) -> scores.get(c.id())).reversed();
    };
    List<CourseRow> sorted = new ArrayList<>(rows);
    sorted.sort(cmp); // stable, like Array.prototype.sort
    int offset = decodeOffset(q.cursor());
    int limit = q.limit();
    List<CourseRow> page = offset >= sorted.size() ? List.of() : sorted.subList(offset, (int) Math.min(sorted.size(), (long) offset + limit));
    return new SearchResponse(toCards(page, student, scores), sorted.size(), (long) offset + limit < sorted.size() ? encodeOffset(offset + limit) : null, exp.interpreted(), engineName);
  }

  // ---------------------------------------------------------------- suggest

  public SuggestResponse suggest(String qs) {
    var exp = QueryExpansion.expand(qs);
    String t = qs.trim();
    var termSet = new LinkedHashSet<String>();
    termSet.add(t.toLowerCase(Locale.ROOT));
    termSet.addAll(exp.terms());
    List<String> terms = termSet.stream().limit(6).toList();
    String code = QueryExpansion.normalizeCode(t);
    var insts = repo.suggestInstitutions(terms);
    var subs = repo.suggestSubjects(terms);
    var codes = code.length() >= 3 ? repo.suggestCodes(code, 4) : List.<SearchRepository.CodeSuggestion>of();
    var teachers = repo.suggestTeachers(t);
    var crs = repo.suggestCourses(terms);
    var slugs = teachers.isEmpty() ? Map.<UUID, String>of() : repo.sellerSlugs(teachers.stream().map(SearchRepository.TeacherSuggestion::sellerId).toList());
    List<Suggestion> items = new ArrayList<>();
    for (var i : insts) {
      String sub = java.util.stream.Stream.of(i.shortName(), i.city()).filter(SearchService::has).collect(Collectors.joining(" · "));
      items.add(new Suggestion("institution", i.id().toString(), i.name(), sub, "/u/" + i.slug()));
    }
    for (var s : subs) items.add(new Suggestion("subject", s.id().toString(), s.name(), s.nameBn(), "/explore?subjectId=" + s.id()));
    for (var c : codes)
      if (has(c.code())) items.add(new Suggestion("code", c.id().toString(), c.code(), c.inst() + " · " + c.subject(), "/explore?courseCode=" + encodeURIComponent(c.code())));
    for (var x : teachers) items.add(new Suggestion("teacher", x.sellerId().toString(), x.name(), null, "/t/" + slugs.getOrDefault(x.sellerId(), "")));
    for (var c : crs) items.add(new Suggestion("course", c.id().toString(), c.title(), c.seller(), "/c/" + c.slug()));
    return new SuggestResponse(items);
  }

  /** JS encodeURIComponent */
  static String encodeURIComponent(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~").replace("%2A", "*");
  }
}
