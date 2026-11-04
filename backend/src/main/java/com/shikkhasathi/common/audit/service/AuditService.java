package com.shikkhasathi.common.audit.service;

import com.shikkhasathi.common.audit.entity.AuditLog;
import com.shikkhasathi.common.audit.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Audit log for payments, refunds, payouts, course status, settings and moderation changes (spec §10). */
@Service
public class AuditService {
  private final AuditLogRepository repo;
  private final JsonMapper json;

  public AuditService(AuditLogRepository repo, JsonMapper json) {
    this.repo = repo;
    this.json = json;
  }

  public record Entry(UUID actorId, String action, String entity, String entityId, Object before, Object after, String ip) {
    public static Entry of(UUID actorId, String action, String entity, Object entityId) {
      return new Entry(actorId, action, entity, entityId == null ? null : String.valueOf(entityId), null, null, null);
    }

    public Entry before(Object b) { return new Entry(actorId, action, entity, entityId, b, after, ip); }
    public Entry after(Object a) { return new Entry(actorId, action, entity, entityId, before, a, ip); }
    public Entry ip(String i) { return new Entry(actorId, action, entity, entityId, before, after, i); }
  }

  @Transactional(transactionManager = "coreTransactionManager", propagation = Propagation.REQUIRED)
  public void log(Entry e) {
    repo.save(new AuditLog()
        .setActorId(e.actorId())
        .setAction(e.action())
        .setEntity(e.entity())
        .setEntityId(e.entityId())
        .setBeforeJson(toJson(e.before()))
        .setAfterJson(toJson(e.after()))
        .setIp(e.ip()));
  }

  private JsonNode toJson(Object o) {
    return o == null ? null : json.valueToTree(o);
  }
}
