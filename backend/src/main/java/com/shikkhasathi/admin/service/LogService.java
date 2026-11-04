package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.AuditEntry;
import com.shikkhasathi.admin.dto.OpsDtos.EmailLog;
import com.shikkhasathi.admin.dto.OpsDtos.Page;
import com.shikkhasathi.admin.repository.AdminUserRepository;
import com.shikkhasathi.admin.repository.OpsRepository;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.config.JsonConfig;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** Admin email delivery log and audit log (secret-looking keys are redacted). */
@Service
public class LogService {
  static final Pattern SECRETISH = Pattern.compile("secret|password|token|key|otp|code_hash|account_enc", Pattern.CASE_INSENSITIVE);

  private final OpsRepository ops;
  private final AdminUserRepository users;
  private final JsonMapper json;

  public LogService(OpsRepository ops, AdminUserRepository users, JsonMapper json) {
    this.ops = ops;
    this.users = users;
    this.json = json;
  }

  public Page<EmailLog> emailLogs(String status, String q, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = ops.emailLogs(AdminUserService.emptyToNull(status), AdminUserService.emptyToNull(q), cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(pg.items().stream().map(e -> new EmailLog(e.id(), e.toEmail(), e.template(), e.subject(), e.status(), e.provider(), e.error(), e.attempts(),
        JsonConfig.iso(e.createdAt()), JsonConfig.iso(e.sentAt()))).toList(), pg.nextCursor());
  }

  /** Replace values of secret-looking keys with "••••" (recursively). */
  static JsonNode redact(JsonNode v) {
    if (v == null) return null;
    if (v.isArray()) {
      ArrayNode out = ((ArrayNode) v).arrayNode();
      for (var x : v) out.add(redact(x));
      return out;
    }
    if (!v.isObject()) return v;
    ObjectNode out = ((ObjectNode) v).objectNode();
    for (var name : v.propertyNames()) out.set(name, SECRETISH.matcher(name).find() ? StringNode.valueOf("••••") : redact(v.get(name)));
    return out;
  }

  private JsonNode parse(String s) {
    return s == null ? null : json.readTree(s);
  }

  public Page<AuditEntry> audit(String entity, String action, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = ops.audit(AdminUserService.emptyToNull(entity), AdminUserService.emptyToNull(action), cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    var actors = users.names(pg.items().stream().map(OpsRepository.AuditRow::actorId).filter(Objects::nonNull).toList());
    return new Page<>(pg.items().stream().map(r -> new AuditEntry(r.id(), r.actorId() != null ? actors.get(r.actorId()) : null, r.action(), r.entity(), r.entityId(),
        redact(parse(r.beforeJson())), redact(parse(r.afterJson())), r.ip(), JsonConfig.iso(r.createdAt()))).toList(), pg.nextCursor());
  }
}
