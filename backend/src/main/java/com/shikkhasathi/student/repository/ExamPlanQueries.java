package com.shikkhasathi.student.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only CORE queries on {@code exam_plans} (owned by the exam module) for the dashboard and calendar. */
@Repository
public class ExamPlanQueries {
  public record ExamRow(UUID id, String subjectName, String courseCode, String examType, Instant examDate, int readiness) {}

  private static final RowMapper<ExamRow> ROW = (rs, i) -> new ExamRow(rs.getObject("id", UUID.class), rs.getString("subject_name"), rs.getString("course_code"),
      rs.getString("exam_type"), rs.getTimestamp("exam_date").toInstant(), rs.getInt("readiness"));

  private final NamedParameterJdbcTemplate db;

  public ExamPlanQueries(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  private static MapSqlParameterSource range(UUID userId, Instant from, Instant to) {
    return new MapSqlParameterSource().addValue("u", userId).addValue("from", Timestamp.from(from), Types.TIMESTAMP).addValue("to", Timestamp.from(to), Types.TIMESTAMP);
  }

  /** Active plans with exam_date in [from, to] ({@code toInclusive}) or [from, to). */
  public List<ExamRow> activeBetween(UUID userId, Instant from, Instant to, boolean toInclusive) {
    return db.query("select id, subject_name, course_code, exam_type, exam_date, readiness from exam_plans where student_id = :u and active = true and exam_date >= :from and exam_date "
        + (toInclusive ? "<=" : "<") + " :to", range(userId, from, to), ROW);
  }

  /** The soonest active plan with exam_date &gt;= since. */
  public Optional<ExamRow> nearest(UUID userId, Instant since) {
    return db.query("select id, subject_name, course_code, exam_type, exam_date, readiness from exam_plans where student_id = :u and active = true and exam_date >= :s order by exam_date asc limit 1",
        new MapSqlParameterSource().addValue("u", userId).addValue("s", Timestamp.from(since), Types.TIMESTAMP), ROW).stream().findFirst();
  }
}
