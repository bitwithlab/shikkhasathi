package com.shikkhasathi.catalog.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CONTENT-database SQL of the catalog: master-data reads, published-course aggregates and bulk merge updates. */
@Repository
public class CatalogRepository {
  private final NamedParameterJdbcTemplate db;

  public CatalogRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record InstitutionRow(UUID id, String name, String nameBn, String shortName, String slug, String type, String city, String logoUrl, String website,
                               List<String> aliases, String status, UUID mergedIntoId, Instant createdAt) {}

  public record DepartmentRow(UUID id, UUID institutionId, String name, String nameBn, String shortName, String icon) {}

  public record ProgramRow(UUID id, UUID departmentId, String name, String level, int semesters) {}

  public record SubjectRow(UUID id, String name, String nameBn, String slug, List<String> aliases, String category, String status) {}

  public record InstitutionSubjectRow(UUID id, UUID departmentId, Integer semester, String courseCode, SubjectRow subject) {}

  public record OfferingRow(UUID institutionId, String institutionName, String institutionShort, String institutionSlug, String courseCode, Integer semester) {}

  public record RequestRow(UUID id, UUID requestedBy, String name, String type, String city, String note, String status, Instant createdAt) {}

  public record Counts(int courses, int teachers) {}

  // ---------------------------------------------------------------- mappers

  static List<String> strings(ResultSet rs, String col) throws SQLException {
    Array a = rs.getArray(col);
    return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
  }

  static Integer intOrNull(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  static final RowMapper<InstitutionRow> INSTITUTION = (rs, i) -> new InstitutionRow(
      rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("short_name"), rs.getString("slug"), rs.getString("type"),
      rs.getString("city"), rs.getString("logo_url"), rs.getString("website"), strings(rs, "aliases"), rs.getString("status"),
      rs.getObject("merged_into_id", UUID.class), rs.getTimestamp("created_at").toInstant());

  static final RowMapper<DepartmentRow> DEPARTMENT = (rs, i) -> new DepartmentRow(
      rs.getObject("id", UUID.class), rs.getObject("institution_id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("short_name"), rs.getString("icon"));

  static final RowMapper<ProgramRow> PROGRAM = (rs, i) -> new ProgramRow(
      rs.getObject("id", UUID.class), rs.getObject("department_id", UUID.class), rs.getString("name"), rs.getString("level"), rs.getInt("semesters"));

  static SubjectRow subject(ResultSet rs, String p) throws SQLException {
    return new SubjectRow(rs.getObject(p + "id", UUID.class), rs.getString(p + "name"), rs.getString(p + "name_bn"), rs.getString(p + "slug"), strings(rs, p + "aliases"),
        rs.getString(p + "category"), rs.getString(p + "status"));
  }

  static final RowMapper<SubjectRow> SUBJECT = (rs, i) -> subject(rs, "");

  static final RowMapper<RequestRow> REQUEST = (rs, i) -> new RequestRow(
      rs.getObject("id", UUID.class), rs.getObject("requested_by", UUID.class), rs.getString("name"), rs.getString("type"), rs.getString("city"), rs.getString("note"),
      rs.getString("status"), rs.getTimestamp("created_at").toInstant());

  /** {@code col ilike %q%} with % and _ escaped (same as the previous textMatch). */
  public static String like(String q) {
    return "%" + q.replaceAll("[%_]", "\\\\$0") + "%";
  }

  // ---------------------------------------------------------------- institutions

  /** Approved institutions page ordered by (name, id); {@code q} matches name / short / Bangla / aliases. */
  public List<InstitutionRow> approvedInstitutions(String q, String type, String afterName, String afterId, int limit) {
    var p = new MapSqlParameterSource("limit", limit);
    var sql = new StringBuilder("select * from institutions where status = 'approved'");
    if (type != null) {
      sql.append(" and type = :type");
      p.addValue("type", type);
    }
    if (q != null) {
      sql.append(" and (name ilike :like or short_name ilike :like or name_bn ilike :like or exists (select 1 from unnest(aliases) a where a ilike :raw))");
      p.addValue("like", like(q)).addValue("raw", "%" + q + "%");
    }
    if (afterId != null) {
      sql.append(" and (name > :cn or (name = :cn and id > cast(:cid as uuid)))");
      p.addValue("cn", afterName).addValue("cid", afterId);
    }
    sql.append(" order by name asc, id asc limit :limit");
    return db.query(sql.toString(), p, INSTITUTION);
  }

  public Optional<InstitutionRow> institutionBySlug(String slug) {
    return db.query("select * from institutions where slug = :s", Map.of("s", slug), INSTITUTION).stream().findFirst();
  }

  public Optional<InstitutionRow> institution(UUID id) {
    return db.query("select * from institutions where id = :id", Map.of("id", id), INSTITUTION).stream().findFirst();
  }

  public Optional<UUID> institutionIdBySlug(String slug) {
    return db.queryForList("select id from institutions where slug = :s", Map.of("s", slug), UUID.class).stream().findFirst();
  }

  public List<InstitutionRow> adminInstitutions(String q, String status) {
    var p = new MapSqlParameterSource();
    var sql = new StringBuilder("select * from institutions where true");
    if (status != null) {
      sql.append(" and status = :status");
      p.addValue("status", status);
    }
    if (q != null && !q.isEmpty()) {
      sql.append(" and (name ilike :like or short_name ilike :like)");
      p.addValue("like", like(q));
    }
    sql.append(" order by name asc limit 500");
    return db.query(sql.toString(), p, INSTITUTION);
  }

  /** All non-merged institutions (for "similar" matching of requests), in insertion order. */
  public List<InstitutionRow> nonMergedInstitutions() {
    return db.query("select * from institutions where status <> 'merged' order by created_at asc, id asc", Map.of(), INSTITUTION);
  }

  /** courseCount / teacherCount per institution from published, non-deleted courses' targets (one grouped query). */
  public Map<UUID, Counts> institutionCounts(Collection<UUID> ids) {
    Map<UUID, Counts> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    db.query("""
        select t.institution_id as id, count(distinct c.id)::int as courses, count(distinct c.seller_id)::int as teachers
        from course_targets t join courses c on c.id = t.course_id
        where t.institution_id in (:ids) and c.status = 'published' and c.deleted_at is null
        group by t.institution_id""", Map.of("ids", ids), rs -> {
      out.put(rs.getObject("id", UUID.class), new Counts(rs.getInt("courses"), rs.getInt("teachers")));
    });
    return out;
  }

  // ---------------------------------------------------------------- departments / programs

  public List<DepartmentRow> departments(UUID institutionId) {
    return db.query("select * from departments where institution_id = :i order by position asc, name asc", Map.of("i", institutionId), DEPARTMENT);
  }

  public Optional<DepartmentRow> department(UUID id) {
    return db.query("select * from departments where id = :id", Map.of("id", id), DEPARTMENT).stream().findFirst();
  }

  public List<ProgramRow> programs(UUID departmentId) {
    return db.query("select * from programs where department_id = :d order by name asc", Map.of("d", departmentId), PROGRAM);
  }

  public List<ProgramRow> programsOf(Collection<UUID> departmentIds) {
    if (departmentIds.isEmpty()) return List.of();
    return db.query("select * from programs where department_id in (:d) order by name asc", Map.of("d", departmentIds), PROGRAM);
  }

  public Optional<ProgramRow> program(UUID id) {
    return db.query("select * from programs where id = :id", Map.of("id", id), PROGRAM).stream().findFirst();
  }

  public boolean departmentHasTargets(UUID departmentId) {
    return !db.queryForList("select id from course_targets where department_id = :d limit 1", Map.of("d", departmentId), UUID.class).isEmpty();
  }

  // ---------------------------------------------------------------- subjects

  private static final String IS_SUBJECT_COLS = "m.id as m_id, m.department_id as m_department_id, m.semester as m_semester, m.course_code as m_course_code, "
      + "s.id as s_id, s.name as s_name, s.name_bn as s_name_bn, s.slug as s_slug, s.aliases as s_aliases, s.category as s_category, s.status as s_status";

  private static final RowMapper<InstitutionSubjectRow> IS_SUBJECT = (rs, i) -> new InstitutionSubjectRow(
      rs.getObject("m_id", UUID.class), rs.getObject("m_department_id", UUID.class), intOrNull(rs, "m_semester"), rs.getString("m_course_code"), subject(rs, "s_"));

  /** institution_subjects ⋈ subject_masters of one institution, ordered by (semester, course code). */
  public List<InstitutionSubjectRow> institutionSubjects(UUID institutionId) {
    return db.query("select " + IS_SUBJECT_COLS + " from institution_subjects m join subject_masters s on s.id = m.subject_master_id"
        + " where m.institution_id = :i order by m.semester asc, m.course_code asc", Map.of("i", institutionId), IS_SUBJECT);
  }

  /** Subjects mapped to an institution (optionally department / semester), ordered by course code. */
  public List<InstitutionSubjectRow> scopedSubjects(UUID institutionId, UUID departmentId, Integer semester) {
    var p = new MapSqlParameterSource("i", institutionId);
    var sql = new StringBuilder("select " + IS_SUBJECT_COLS + " from institution_subjects m join subject_masters s on s.id = m.subject_master_id where m.institution_id = :i");
    if (departmentId != null) {
      sql.append(" and m.department_id = :d");
      p.addValue("d", departmentId);
    }
    if (semester != null) {
      sql.append(" and m.semester = :sem");
      p.addValue("sem", semester);
    }
    sql.append(" order by m.course_code asc");
    return db.query(sql.toString(), p, IS_SUBJECT);
  }

  /** Approved subject masters; {@code q} matches name / Bangla name / aliases. At most 200, by name. */
  public List<SubjectRow> approvedSubjects(String q) {
    var p = new MapSqlParameterSource();
    var sql = new StringBuilder("select * from subject_masters where status = 'approved'");
    if (q != null) {
      sql.append(" and (name ilike :like or name_bn ilike :like or exists (select 1 from unnest(aliases) a where a ilike :raw))");
      p.addValue("like", like(q)).addValue("raw", "%" + q + "%");
    }
    sql.append(" order by name asc limit 200");
    return db.query(sql.toString(), p, SUBJECT);
  }

  public List<SubjectRow> adminSubjects(String q, String status) {
    var p = new MapSqlParameterSource();
    var sql = new StringBuilder("select * from subject_masters where true");
    if (status != null) {
      sql.append(" and status = :status");
      p.addValue("status", status);
    }
    if (q != null && !q.isEmpty()) {
      sql.append(" and (name ilike :like or slug ilike :like)");
      p.addValue("like", like(q));
    }
    sql.append(" order by name asc limit 500");
    return db.query(sql.toString(), p, SUBJECT);
  }

  public Optional<SubjectRow> approvedSubjectBySlug(String slug) {
    return db.query("select * from subject_masters where slug = :s and status = 'approved'", Map.of("s", slug), SUBJECT).stream().findFirst();
  }

  public Optional<UUID> subjectIdBySlug(String slug) {
    return db.queryForList("select id from subject_masters where slug = :s", Map.of("s", slug), UUID.class).stream().findFirst();
  }

  public List<OfferingRow> offerings(UUID subjectId) {
    return db.query("""
        select i.id, i.name, i.short_name, i.slug, m.course_code, m.semester
        from institution_subjects m join institutions i on i.id = m.institution_id
        where m.subject_master_id = :s and i.status = 'approved'
        order by i.name asc""", Map.of("s", subjectId), (rs, n) -> new OfferingRow(
        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("short_name"), rs.getString("slug"), rs.getString("course_code"), intOrNull(rs, "semester")));
  }

  /** Published course count per subject master (one grouped query). */
  public Map<UUID, Integer> subjectCourseCounts(Collection<UUID> ids) {
    Map<UUID, Integer> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    db.query("""
        select t.subject_master_id as sid, count(distinct c.id)::int as n
        from course_targets t join courses c on c.id = t.course_id
        where t.subject_master_id in (:ids) and c.status = 'published'
        group by t.subject_master_id""", Map.of("ids", ids), rs -> {
      out.put(rs.getObject("sid", UUID.class), rs.getInt("n"));
    });
    return out;
  }

  /** Published course count per subject within one institution. */
  public Map<UUID, Integer> subjectCourseCountsIn(UUID institutionId) {
    Map<UUID, Integer> out = new HashMap<>();
    db.query("""
        select t.subject_master_id as sid, count(distinct c.id)::int as n
        from course_targets t join courses c on c.id = t.course_id
        where t.institution_id = :i and c.status = 'published'
        group by t.subject_master_id""", Map.of("i", institutionId), rs -> {
      UUID sid = rs.getObject("sid", UUID.class);
      if (sid != null) out.put(sid, rs.getInt("n"));
    });
    return out;
  }

  // ---------------------------------------------------------------- institution requests

  public boolean pendingRequestExists(UUID userId, String name) {
    return !db.queryForList("select id from institution_requests where requested_by = :u and status = 'pending' and name ilike :n",
        Map.of("u", userId, "n", name), UUID.class).isEmpty();
  }

  /**
   * Requests of one status ordered by (created_at, id). The cursor carries the millisecond ISO timestamp, so the
   * comparison uses created_at truncated to milliseconds.
   */
  public List<RequestRow> requests(String status, Instant afterCreated, String afterId, int limit) {
    var p = new MapSqlParameterSource("status", status).addValue("limit", limit);
    var sql = new StringBuilder("select * from institution_requests where status = :status");
    if (afterId != null) {
      sql.append(" and (date_trunc('milliseconds', created_at) > :ca or (date_trunc('milliseconds', created_at) = :ca and id > cast(:cid as uuid)))");
      p.addValue("ca", Timestamp.from(afterCreated)).addValue("cid", afterId);
    }
    sql.append(" order by date_trunc('milliseconds', created_at) asc, id asc limit :limit");
    return db.query(sql.toString(), p, REQUEST);
  }

  // ---------------------------------------------------------------- merge (inside a CONTENT transaction)

  public void moveDepartments(UUID from, UUID into) {
    db.update("update departments set institution_id = :into, updated_at = now() where institution_id = :from", Map.of("from", from, "into", into));
  }

  public void moveInstitutionSubjects(UUID from, UUID into) {
    db.update("update institution_subjects set institution_id = :into, updated_at = now() where institution_id = :from", Map.of("from", from, "into", into));
  }

  public void moveCourseTargets(UUID from, UUID into) {
    db.update("update course_targets set institution_id = :into, updated_at = now() where institution_id = :from", Map.of("from", from, "into", into));
  }
}
