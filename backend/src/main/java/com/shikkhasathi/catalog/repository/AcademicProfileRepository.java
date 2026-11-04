package com.shikkhasathi.catalog.repository;

import com.shikkhasathi.common.Ids;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE-database SQL used by the catalog: student academic profiles, onboarding, first exam plan, requester names. */
@Repository
public class AcademicProfileRepository {
  private final NamedParameterJdbcTemplate db;

  public AcademicProfileRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  public record Academic(UUID institutionId, UUID departmentId, UUID programId, Integer semester, String batch, String section) {}

  public record Person(UUID id, String name) {}

  /** Insert or update the student profile's academic fields (keyed by user id). */
  public void upsertAcademic(UUID userId, Academic a) {
    db.update("""
        insert into student_profiles (user_id, institution_id, department_id, program_id, semester, batch, section)
        values (:u, :i, :d, :p, :sem, :batch, :section)
        on conflict (user_id) do update set institution_id = excluded.institution_id, department_id = excluded.department_id,
          program_id = excluded.program_id, semester = excluded.semester, batch = excluded.batch, section = excluded.section, updated_at = now()""",
        new MapSqlParameterSource("u", userId).addValue("i", a.institutionId()).addValue("d", a.departmentId()).addValue("p", a.programId())
            .addValue("sem", a.semester()).addValue("batch", a.batch()).addValue("section", a.section()));
  }

  /** The student's first exam plan, created by onboarding. */
  public void insertExamPlan(UUID studentId, UUID subjectMasterId, String subjectName, String courseCode, String examType, Instant examDate) {
    db.update("""
        insert into exam_plans (id, student_id, subject_master_id, subject_name, course_code, exam_type, exam_date)
        values (:id, :s, :sm, :name, :code, :type, :date)""",
        new MapSqlParameterSource("id", Ids.uuidv7()).addValue("s", studentId).addValue("sm", subjectMasterId).addValue("name", subjectName)
            .addValue("code", courseCode).addValue("type", examType).addValue("date", Timestamp.from(examDate)));
  }

  public void markOnboardingCompleted(UUID userId) {
    db.update("update users set onboarding_completed_at = now(), updated_at = now() where id = :u", Map.of("u", userId));
  }

  /** Merge: move CORE references (no cross-DB FK) to the surviving institution. Idempotent. */
  public void moveInstitution(UUID from, UUID into) {
    db.update("update student_profiles set institution_id = :into, updated_at = now() where institution_id = :from", Map.of("from", from, "into", into));
  }

  /** Batched requester lookup. */
  public List<Person> people(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return db.query("select id, name from users where id in (:ids)", Map.of("ids", ids), (rs, i) -> new Person(rs.getObject("id", UUID.class), rs.getString("name")));
  }
}
