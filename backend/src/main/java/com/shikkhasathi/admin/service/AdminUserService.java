package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.AdminUser;
import com.shikkhasathi.admin.dto.OpsDtos.Page;
import com.shikkhasathi.admin.repository.AdminUserRepository;
import com.shikkhasathi.admin.repository.AdminUserRepository.UserRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Admin user management: search, suspend/reactivate (sessions end + Redis block), role changes — all audited. */
@Service
public class AdminUserService {
  /** Checked by {@code security.AuthInterceptor} on every request. */
  public static String suspendKey(UUID id) {
    return "susp:" + id;
  }

  /** Outlives any access token (15 min). */
  static final Duration SUSPEND_TTL = Duration.ofMinutes(20);

  private final AdminUserRepository users;
  private final AuditService audit;
  private final StringRedisTemplate redis;

  public AdminUserService(AdminUserRepository users, AuditService audit, StringRedisTemplate redis) {
    this.users = users;
    this.audit = audit;
    this.redis = redis;
  }

  List<AdminUser> toAdmin(List<UserRow> rows) {
    if (rows.isEmpty()) return List.of();
    var ids = rows.stream().map(UserRow::id).toList();
    var enr = users.enrollmentCounts(ids);
    var sellers = users.sellerStatuses(ids);
    return rows.stream().map(u -> new AdminUser(u.id(), u.name(), u.email(), u.phone(), u.roles(), "suspended".equals(u.status()) ? "suspended" : "active",
        JsonConfig.iso(u.createdAt()), JsonConfig.iso(u.lastActiveAt()), enr.getOrDefault(u.id(), 0L), sellers.get(u.id()))).toList();
  }

  public Page<AdminUser> list(String q, String role, String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    String like = q != null && !q.isEmpty() ? "%" + q.replaceAll("[%_]", "") + "%" : null;
    var rows = users.search(like, emptyToNull(role), emptyToNull(status), cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(toAdmin(pg.items()), pg.nextCursor());
  }

  static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  public AdminUser update(UUID actorId, UUID id, String status, List<String> roles, String reason) {
    var u = users.byId(id).orElseThrow(ApiException::notFound);
    if (id.equals(actorId) && ("suspended".equals(status) || (roles != null && !roles.contains("admin"))))
      throw ApiException.forbidden("নিজের অ্যাডমিন অ্যাক্সেস বা অ্যাকাউন্ট বন্ধ করা যায় না।");
    List<String> newRoles = null;
    if (roles != null) {
      var set = new LinkedHashSet<String>();
      if (!roles.contains("student")) set.add("student");
      set.addAll(roles);
      newRoles = List.copyOf(set);
    }
    var row = users.update(id, status, newRoles);
    if ("suspended".equals(status)) {
      users.revokeActiveSessions(id);
      redis.opsForValue().set(suspendKey(id), "1", SUSPEND_TTL);
    } else if ("active".equals(status)) redis.delete(suspendKey(id));
    // roles live in the access token — end sessions so the next login carries the new roles
    if (roles != null) users.revokeActiveSessions(id);
    String action = status != null ? "user." + ("suspended".equals(status) ? "suspend" : "reactivate") : "user.roles";
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("status", u.status());
    before.put("roles", u.roles());
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("status", row.status());
    after.put("roles", row.roles());
    if (reason != null) after.put("reason", reason);
    audit.log(AuditService.Entry.of(actorId, action, "users", id).before(before).after(after));
    return toAdmin(List.of(row)).get(0);
  }
}
