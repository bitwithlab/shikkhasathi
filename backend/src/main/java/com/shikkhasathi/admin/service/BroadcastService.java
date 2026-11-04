package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.repository.AdminUserRepository;
import com.shikkhasathi.admin.repository.OpsRepository;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Admin announcements (in-app + push for everyone in the audience, email optional); history lives in the audit log. */
@Service
public class BroadcastService {
  private final OpsRepository ops;
  private final AdminUserRepository users;
  private final NotificationService notifications;
  private final JsonMapper json;

  public BroadcastService(OpsRepository ops, AdminUserRepository users, NotificationService notifications, JsonMapper json) {
    this.ops = ops;
    this.users = users;
    this.notifications = notifications;
    this.json = json;
  }

  List<UUID> audienceIds(String audience, UUID institutionId) {
    if ("sellers".equals(audience)) return ops.sellers();
    if ("students".equals(audience)) return ops.students();
    if ("institution".equals(audience)) {
      if (institutionId == null) throw ApiException.badRequest("প্রতিষ্ঠান বাছাই করুন।");
      return ops.institutionStudents(institutionId);
    }
    return ops.everyone();
  }

  public Recipients preview(AudienceBody a) {
    return new Recipients(audienceIds(a.audience(), a.institutionId()).size());
  }

  public Broadcast send(UUID actorId, BroadcastBody b) {
    var ids = audienceIds(b.audience(), b.institutionId());
    if (ids.isEmpty()) throw ApiException.badRequest("এই অডিয়েন্সে কেউ নেই।");
    boolean email = Boolean.TRUE.equals(b.email());
    String key = "bc-" + Long.toString(System.currentTimeMillis(), 36);
    // in-app + push for everyone, email only when chosen (each user's preferences still apply)
    for (UUID userId : ids) {
      var n = NotifyInput.of(userId, NotificationTypes.ADMIN_BROADCAST, key, b.title(), b.body()).withLink(b.link());
      if (email) n = n.withEmail(EmailTemplate.ADMIN_BROADCAST, Map.of("subject", b.title(), "body", b.body())).withEmailByDefault();
      notifications.notify(n);
    }
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("title", b.title());
    after.put("body", b.body());
    after.put("link", b.link());
    after.put("audience", b.audience());
    after.put("institutionId", b.institutionId());
    after.put("email", email);
    after.put("recipients", ids.size());
    var createdAt = ops.insertAudit(actorId, "broadcast.send", "broadcast", key, json.writeValueAsString(after));
    String actorName = users.names(List.of(actorId)).getOrDefault(actorId, "");
    return new Broadcast(key, b.title(), b.body(), b.audience(), ids.size(), email, JsonConfig.iso(createdAt), actorName);
  }

  public Items<Broadcast> list() {
    var rows = ops.auditByAction("broadcast.send", 50);
    var actors = users.names(rows.stream().map(OpsRepository.AuditRow::actorId).filter(Objects::nonNull).toList());
    return new Items<>(rows.stream().map(r -> {
      JsonNode a = r.afterJson() == null ? json.createObjectNode() : json.readTree(r.afterJson());
      return new Broadcast(r.entityId() != null ? r.entityId() : r.id().toString(), text(a, "title", ""), text(a, "body", ""), text(a, "audience", "all"),
          a.path("recipients").isNumber() ? a.path("recipients").asLong() : 0, a.path("email").asBoolean(false), JsonConfig.iso(r.createdAt()),
          r.actorId() != null ? actors.getOrDefault(r.actorId(), "") : "");
    }).toList());
  }

  private static String text(JsonNode n, String k, String fallback) {
    var v = n.get(k);
    return v == null || v.isNull() ? fallback : v.asString();
  }
}
