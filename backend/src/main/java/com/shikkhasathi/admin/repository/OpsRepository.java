package com.shikkhasathi.admin.repository;

import static com.shikkhasathi.payment.repository.Rows.t;
import static com.shikkhasathi.payment.repository.Rows.ts;
import static com.shikkhasathi.payment.repository.Rows.uuid;

import com.shikkhasathi.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** CORE: broadcast audiences, audit log and email log reads. */
@Repository
public class OpsRepository {
  public record AuditRow(UUID id, UUID actorId, String action, String entity, String entityId, String beforeJson, String afterJson, String ip, Instant createdAt) {}

  public record EmailLogRow(UUID id, String toEmail, String template, String subject, String status, String provider, String error, int attempts, Instant createdAt,
                            Instant sentAt) {}

  private final NamedParameterJdbcTemplate db;

  public OpsRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db) {
    this.db = db;
  }

  static final RowMapper<AuditRow> AUDIT = (rs, i) -> new AuditRow(uuid(rs, "id"), uuid(rs, "actor_id"), rs.getString("action"), rs.getString("entity"), rs.getString("entity_id"),
      rs.getString("before_json"), rs.getString("after_json"), rs.getString("ip"), ts(rs, "created_at"));
  static final RowMapper<EmailLogRow> EMAIL = (rs, i) -> new EmailLogRow(uuid(rs, "id"), rs.getString("to_email"), rs.getString("template"), rs.getString("subject"),
      rs.getString("status"), rs.getString("provider"), rs.getString("error"), rs.getInt("attempts"), ts(rs, "created_at"), ts(rs, "sent_at"));

  // ---------------------------------------------------------------- audiences (active, not deleted)

  public List<UUID> sellers() {
    return db.queryForList("select id from users where status = 'active' and deleted_at is null and 'seller' = any(roles)", Map.of(), UUID.class);
  }

  public List<UUID> students() {
    return db.queryForList("select id from users where status = 'active' and deleted_at is null and not ('seller' = any(roles))", Map.of(), UUID.class);
  }

  public List<UUID> institutionStudents(UUID institutionId) {
    return db.queryForList("select u.id from users u inner join student_profiles sp on sp.user_id = u.id where u.status = 'active' and u.deleted_at is null and sp.institution_id = :i",
        Map.of("i", institutionId), UUID.class);
  }

  public List<UUID> everyone() {
    return db.queryForList("select id from users where status = 'active' and deleted_at is null", Map.of(), UUID.class);
  }

  // ---------------------------------------------------------------- audit log

  /** Insert an audit row and return its created_at (broadcast history lives in the audit log). */
  public Instant insertAudit(UUID actorId, String action, String entity, String entityId, String afterJson) {
    return db.queryForObject("insert into audit_logs (id, actor_id, action, entity, entity_id, after_json) values (:id, :a, :ac, :e, :eid, cast(:after as jsonb)) returning created_at",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("a", actorId).addValue("ac", action).addValue("e", entity).addValue("eid", entityId).addValue("after", afterJson),
        (rs, i) -> ts(rs, "created_at"));
  }

  public List<AuditRow> auditByAction(String action, int limit) {
    return db.query("select * from audit_logs where action = :a order by created_at desc limit :lim", Map.of("a", action, "lim", limit), AUDIT);
  }

  public List<AuditRow> audit(String entity, String actionPrefix, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("lim", limit);
    var w = new ArrayList<String>();
    if (entity != null) {
      w.add("entity = :e");
      p.addValue("e", entity);
    }
    if (actionPrefix != null) {
      w.add("action ilike :a");
      p.addValue("a", actionPrefix + "%");
    }
    if (curAt != null) {
      w.add("created_at < :ca");
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from audit_logs" + (w.isEmpty() ? "" : " where " + String.join(" and ", w)) + " order by created_at desc limit :lim", p, AUDIT);
  }

  // ---------------------------------------------------------------- email log

  public List<EmailLogRow> emailLogs(String status, String q, Instant curAt, int limit) {
    var p = new MapSqlParameterSource().addValue("lim", limit);
    var w = new ArrayList<String>();
    if (status != null) {
      w.add("status = :st");
      p.addValue("st", status);
    }
    if (q != null) {
      w.add("(to_email ilike :q or template ilike :q)");
      p.addValue("q", "%" + q + "%");
    }
    if (curAt != null) {
      w.add("created_at < :ca");
      p.addValue("ca", t(curAt));
    }
    return db.query("select * from email_logs" + (w.isEmpty() ? "" : " where " + String.join(" and ", w)) + " order by created_at desc limit :lim", p, EMAIL);
  }
}
