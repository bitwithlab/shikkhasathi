package com.shikkhasathi.search.repository;

import com.shikkhasathi.search.dto.SearchDtos.SearchQuery;
import com.shikkhasathi.search.repository.CourseRows.CourseRow;
import com.shikkhasathi.search.repository.CourseRows.TargetRow;
import com.shikkhasathi.search.service.Ranking.Exam;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Discovery reads: CONTENT courses/targets/catalog and CORE profiles (never joined across databases). */
@Repository
public class SearchRepository {
  private static final Map<String, long[]> DURATION = Map.of("short", new long[] {0, 2 * 3600}, "medium", new long[] {2 * 3600, 6 * 3600}, "long", new long[] {6 * 3600, 1_000_000_000L});
  private static final String NORM_CODE = "regexp_replace(upper(%s), '[^A-Z0-9]', '', 'g')";

  private final NamedParameterJdbcTemplate content;
  private final NamedParameterJdbcTemplate core;

  public SearchRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate content, @Qualifier("coreJdbc") NamedParameterJdbcTemplate core) {
    this.content = content;
    this.core = core;
  }

  /** ILIKE pattern {@code %s%} with % _ \ escaped. */
  public static String like(String s) {
    return "%" + s.replaceAll("([%_\\\\])", "\\\\$1") + "%";
  }

  private static List<UUID> uniq(Collection<UUID> ids) {
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  // ---------------------------------------------------------------- student context (CORE + CONTENT)

  public record Profile(UUID institutionId, UUID departmentId, Integer semester, String batch) {}

  public Profile studentProfile(UUID userId) {
    var rows = core.query("select institution_id, department_id, semester, batch from student_profiles where user_id = :u", Map.of("u", userId),
        (rs, i) -> new Profile(rs.getObject("institution_id", UUID.class), rs.getObject("department_id", UUID.class), (Integer) rs.getObject("semester", Integer.class), rs.getString("batch")));
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<Exam> activeExams(UUID userId) {
    return core.query("select subject_master_id, exam_type, exam_date from exam_plans where student_id = :u and active = true", Map.of("u", userId),
        (rs, i) -> new Exam(rs.getObject("subject_master_id", UUID.class), rs.getString("exam_type"), rs.getTimestamp("exam_date").toInstant()));
  }

  public List<UUID> institutionSubjectIds(UUID institutionId, UUID departmentId, Integer semester) {
    var p = new MapSqlParameterSource("i", institutionId);
    StringBuilder sql = new StringBuilder("select subject_master_id from institution_subjects where institution_id = :i");
    if (departmentId != null) {
      sql.append(" and department_id = :d");
      p.addValue("d", departmentId);
    }
    if (semester != null && semester != 0) {
      sql.append(" and semester = :s");
      p.addValue("s", semester);
    }
    return content.queryForList(sql.toString(), p, UUID.class);
  }

  // ---------------------------------------------------------------- courses + targets

  public List<CourseRow> coursesByIds(Collection<UUID> ids, boolean excludeDeleted) {
    var u = uniq(ids);
    if (u.isEmpty()) return List.of();
    return content.query("select " + CourseRows.COURSE_COLS + " from courses c where c.id in (:ids)" + (excludeDeleted ? " and c.deleted_at is null" : ""), Map.of("ids", u), CourseRows.COURSE);
  }

  public List<TargetRow> targets(Collection<UUID> courseIds) {
    var u = uniq(courseIds);
    if (u.isEmpty()) return List.of();
    return content.query("select * from course_targets where course_id in (:ids)", Map.of("ids", u), CourseRows.TARGET);
  }

  public Map<UUID, String> sellerSlugs(Collection<UUID> sellerIds) {
    var u = uniq(sellerIds);
    Map<UUID, String> out = new HashMap<>();
    if (u.isEmpty()) return out;
    core.query("select user_id, slug from seller_profiles where user_id in (:ids)", Map.of("ids", u), rs -> {
      out.put(rs.getObject("user_id", UUID.class), rs.getString("slug"));
    });
    return out;
  }

  public List<UUID> subjectHits(List<String> terms) {
    if (terms.isEmpty()) return List.of();
    var p = new MapSqlParameterSource();
    List<String> or = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      p.addValue("l" + i, like(terms.get(i))).addValue("t" + i, terms.get(i));
      or.add("name ilike :l" + i + " or name_bn ilike :l" + i + " or exists (select 1 from unnest(aliases) a where lower(a) = :t" + i + ")");
    }
    return content.queryForList("select id from subject_masters where " + String.join(" or ", or), p, UUID.class);
  }

  public List<UUID> institutionHits(List<String> terms) {
    if (terms.isEmpty()) return List.of();
    var p = new MapSqlParameterSource();
    List<String> or = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      p.addValue("l" + i, like(terms.get(i))).addValue("t" + i, terms.get(i));
      or.add("name ilike :l" + i + " or name_bn ilike :l" + i + " or lower(short_name) = :t" + i + " or exists (select 1 from unnest(aliases) a where lower(a) = :t" + i + ")");
    }
    return content.queryForList("select id from institutions where " + String.join(" or ", or), p, UUID.class);
  }

  /** Text-match inputs for the Postgres engine; {@code meiliIds} non-null replaces the text match. */
  public record TextMatch(List<String> terms, String code, List<UUID> subjectHits, List<UUID> institutionHits) {}

  /**
   * Candidate rows (≤ 500) for a search: published, not deleted, course + target filters, then either the
   * Meilisearch id list or the Postgres text match.
   */
  public List<CourseRow> searchCandidates(SearchQuery q, String examType, List<String> meiliIds, TextMatch text) {
    var p = new MapSqlParameterSource();
    List<String> conds = new ArrayList<>(List.of("c.status = 'published'", "c.deleted_at is null"));
    if (examType != null) {
      conds.add("c.exam_type = :examType");
      p.addValue("examType", examType);
    }
    if (q.courseType() != null) {
      conds.add("c.course_type = :courseType");
      p.addValue("courseType", q.courseType());
    }
    if (q.language() != null) {
      conds.add("c.language = :language");
      p.addValue("language", q.language());
    }
    if ("free".equals(q.price())) conds.add("c.price = 0");
    if ("paid".equals(q.price())) conds.add("c.price > 0");
    if (q.minPrice() != null) {
      conds.add("c.price >= :minPrice");
      p.addValue("minPrice", q.minPrice());
    }
    if (q.maxPrice() != null) {
      conds.add("c.price <= :maxPrice");
      p.addValue("maxPrice", q.maxPrice());
    }
    if (q.rating() != null) {
      conds.add("c.rating_avg >= :rating");
      p.addValue("rating", BigDecimal.valueOf(q.rating()));
    }
    if (q.duration() != null) {
      long[] d = DURATION.get(q.duration());
      conds.add("c.total_duration_sec >= :dMin and c.total_duration_sec <= :dMax");
      p.addValue("dMin", d[0]).addValue("dMax", d[1]);
    }
    if (q.sellerId() != null) {
      conds.add("c.seller_id = :sellerId");
      p.addValue("sellerId", q.sellerId());
    }

    // target filters (any matching target)
    List<String> t = new ArrayList<>();
    if (q.institutionId() != null) {
      t.add("(t.institution_id = :fInst or t.is_general = true)");
      p.addValue("fInst", q.institutionId());
    }
    if (q.departmentId() != null) {
      t.add("(t.department_id = :fDept or t.department_id is null)");
      p.addValue("fDept", q.departmentId());
    }
    if (q.semester() != null && q.semester() != 0) {
      t.add("(t.semester = :fSem or t.semester is null)");
      p.addValue("fSem", q.semester());
    }
    if (q.subjectId() != null) {
      t.add("t.subject_master_id = :fSubj");
      p.addValue("fSubj", q.subjectId());
    }
    String filterCode = q.courseCode() != null && !q.courseCode().isEmpty() ? com.shikkhasathi.search.service.QueryExpansion.normalizeCode(q.courseCode()) : null;
    if (filterCode != null && !filterCode.isEmpty()) {
      t.add(NORM_CODE.formatted("t.course_code") + " = :fCode");
      p.addValue("fCode", filterCode);
    }
    if (q.batch() != null && !q.batch().isEmpty()) {
      t.add("(:fBatch = any(t.batches) or cardinality(t.batches) = 0)");
      p.addValue("fBatch", q.batch());
    }
    if (q.section() != null && !q.section().isEmpty()) {
      t.add("(:fSection = any(t.sections) or cardinality(t.sections) = 0)");
      p.addValue("fSection", q.section());
    }
    if (!t.isEmpty()) conds.add("exists (select 1 from course_targets t where t.course_id = c.id and " + String.join(" and ", t) + ")");

    if (meiliIds != null) {
      if (meiliIds.isEmpty()) conds.add("false");
      else {
        conds.add("c.id in (:meiliIds)");
        p.addValue("meiliIds", meiliIds.stream().map(SearchRepository::uuidOrNull).filter(Objects::nonNull).toList());
        if (((List<?>) p.getValue("meiliIds")).isEmpty()) conds.set(conds.size() - 1, "false");
      }
    } else if (text != null) {
      List<String> or = new ArrayList<>();
      for (int i = 0; i < text.terms().size(); i++) {
        p.addValue("tl" + i, like(text.terms().get(i)));
        or.add("c.title ilike :tl" + i);
        or.add("c.short_description ilike :tl" + i);
        or.add("c.seller_name ilike :tl" + i);
      }
      if (!text.subjectHits().isEmpty()) {
        or.add("exists (select 1 from course_targets t where t.course_id = c.id and t.subject_master_id in (:subjHits))");
        p.addValue("subjHits", text.subjectHits());
      }
      if (!text.institutionHits().isEmpty()) {
        or.add("exists (select 1 from course_targets t where t.course_id = c.id and t.institution_id in (:instHits))");
        p.addValue("instHits", text.institutionHits());
      }
      if (text.code() != null) {
        or.add("exists (select 1 from course_targets t where t.course_id = c.id and " + NORM_CODE.formatted("t.course_code") + " = :textCode)");
        p.addValue("textCode", text.code());
      }
      if (!or.isEmpty()) conds.add("(" + String.join(" or ", or) + ")");
    }
    return content.query("select " + CourseRows.COURSE_COLS + " from courses c where " + String.join(" and ", conds) + " limit 500", p, CourseRows.COURSE);
  }

  private static UUID uuidOrNull(String s) {
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  // ---------------------------------------------------------------- suggest

  public record InstSuggestion(UUID id, String name, String shortName, String slug, String city) {}

  public record SubjectSuggestion(UUID id, String name, String nameBn, String slug) {}

  public record CodeSuggestion(UUID id, String code, String inst, String subject) {}

  public record TeacherSuggestion(UUID sellerId, String name) {}

  public record CourseSuggestion(UUID id, String title, String slug, String seller) {}

  public List<InstSuggestion> suggestInstitutions(List<String> terms) {
    var p = new MapSqlParameterSource();
    List<String> or = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      p.addValue("l" + i, like(terms.get(i)));
      or.add("name ilike :l" + i + " or name_bn ilike :l" + i + " or short_name ilike :l" + i + " or exists (select 1 from unnest(aliases) a where a ilike :l" + i + ")");
    }
    return content.query("select id, name, short_name, slug, city from institutions where status = 'approved' and (" + String.join(" or ", or) + ") limit 3", p,
        (rs, i) -> new InstSuggestion(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("short_name"), rs.getString("slug"), rs.getString("city")));
  }

  public List<SubjectSuggestion> suggestSubjects(List<String> terms) {
    var p = new MapSqlParameterSource();
    List<String> or = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      p.addValue("l" + i, like(terms.get(i)));
      or.add("name ilike :l" + i + " or name_bn ilike :l" + i + " or exists (select 1 from unnest(aliases) a where a ilike :l" + i + ")");
    }
    return content.query("select id, name, name_bn, slug from subject_masters where status = 'approved' and (" + String.join(" or ", or) + ") limit 3", p,
        (rs, i) -> new SubjectSuggestion(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("slug")));
  }

  public List<CodeSuggestion> suggestCodes(String normalizedCode, int limit) {
    return content.query("select s.id, s.course_code, i.short_name, m.name from institution_subjects s join institutions i on i.id = s.institution_id join subject_masters m on m.id = s.subject_master_id where "
        + NORM_CODE.formatted("s.course_code") + " like :c limit :n", new MapSqlParameterSource("c", "%" + normalizedCode + "%").addValue("n", limit),
        (rs, i) -> new CodeSuggestion(rs.getObject("id", UUID.class), rs.getString("course_code"), rs.getString("short_name"), rs.getString("name")));
  }

  public List<TeacherSuggestion> suggestTeachers(String t) {
    return content.query("select distinct seller_id, seller_name from courses where status = 'published' and seller_name ilike :l limit 3", Map.of("l", like(t)),
        (rs, i) -> new TeacherSuggestion(rs.getObject("seller_id", UUID.class), rs.getString("seller_name")));
  }

  public List<CourseSuggestion> suggestCourses(List<String> terms) {
    var p = new MapSqlParameterSource();
    List<String> or = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      p.addValue("l" + i, like(terms.get(i)));
      or.add("title ilike :l" + i);
    }
    return content.query("select id, title, slug, seller_name from courses where status = 'published' and deleted_at is null and (" + String.join(" or ", or) + ") order by enrollments_30d desc limit 4", p,
        (rs, i) -> new CourseSuggestion(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("slug"), rs.getString("seller_name")));
  }

  // ---------------------------------------------------------------- search index documents

  public record SubjectNames(UUID id, String name, String nameBn, List<String> aliases) {}

  public record InstitutionNames(UUID id, String name, String nameBn, String shortName, List<String> aliases) {}

  public List<SubjectNames> subjectNames(Collection<UUID> ids) {
    var u = uniq(ids);
    if (u.isEmpty()) return List.of();
    return content.query("select id, name, name_bn, aliases from subject_masters where id in (:ids)", Map.of("ids", u),
        (rs, i) -> new SubjectNames(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), CourseRows.strings(rs, "aliases")));
  }

  public List<InstitutionNames> institutionNames(Collection<UUID> ids) {
    var u = uniq(ids);
    if (u.isEmpty()) return List.of();
    return content.query("select id, name, name_bn, short_name, aliases from institutions where id in (:ids)", Map.of("ids", u),
        (rs, i) -> new InstitutionNames(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("short_name"), CourseRows.strings(rs, "aliases")));
  }

  public List<UUID> publishedCourseIds() {
    return content.queryForList("select id from courses where status = 'published'", Map.of(), UUID.class);
  }
}
