package com.shikkhasathi.notification.repository;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.notification.dto.NotificationDtos.NotificationItem;
import com.shikkhasathi.notification.push.PushProvider;
import com.shikkhasathi.notification.service.NotifyInput;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** CORE SQL for notifications, notification_preferences and push_tokens. */
@Repository
public class NotificationRepository {
  public record PrefRow(String type, String channel, boolean enabled) {}

  public record DispatchRow(UUID id, UUID userId, String type, String title, String body, String link, Instant pushedAt) {}

  public record UserContact(String email, String name, String locale) {}

  private final NamedParameterJdbcTemplate db;
  private final JsonMapper json;

  public NotificationRepository(@Qualifier("coreJdbc") NamedParameterJdbcTemplate db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  public List<PrefRow> preferencesFor(UUID userId, String type) {
    return db.query("select type, channel, enabled from notification_preferences where user_id = :u and type = :t", Map.of("u", userId, "t", type),
        (rs, i) -> new PrefRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
  }

  public List<PrefRow> preferences(UUID userId) {
    return db.query("select type, channel, enabled from notification_preferences where user_id = :u", Map.of("u", userId),
        (rs, i) -> new PrefRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
  }

  /** Insert unless the dedupe key exists; returns the new id. created_at is millisecond-precise (stable cursors). */
  public Optional<UUID> insertIfNew(UUID id, NotifyInput n, String dedupeKey, boolean alreadyRead) {
    var p = new MapSqlParameterSource()
        .addValue("id", id).addValue("u", n.userId()).addValue("t", n.type()).addValue("ti", n.title()).addValue("b", n.body())
        .addValue("l", n.link()).addValue("d", n.data() == null ? null : json.writeValueAsString(n.data())).addValue("k", dedupeKey).addValue("r", alreadyRead);
    List<UUID> ids = db.queryForList("""
        insert into notifications (id, user_id, type, title, body, link, data_json, dedupe_key, read_at, created_at, updated_at)
        values (:id, :u, :t, :ti, :b, :l, cast(:d as jsonb), :k, case when :r then now() else null end, date_trunc('milliseconds', now()), now())
        on conflict (dedupe_key) do nothing returning id""", p, UUID.class);
    return ids.stream().findFirst();
  }

  public Optional<UserContact> userContact(UUID userId) {
    return db.query("select email, name, locale from users where id = :u", Map.of("u", userId), (rs, i) -> new UserContact(rs.getString(1), rs.getString(2), rs.getString(3))).stream().findFirst();
  }

  public Optional<DispatchRow> forDispatch(UUID id) {
    return db.query("select id, user_id, type, title, body, link, pushed_at from notifications where id = :id", Map.of("id", id), (rs, i) -> {
      Timestamp t = rs.getTimestamp(7);
      return new DispatchRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), t == null ? null : t.toInstant());
    }).stream().findFirst();
  }

  public List<PushProvider.Target> pushTargets(UUID userId) {
    return db.query("select platform, token from push_tokens where user_id = :u", Map.of("u", userId), (rs, i) -> new PushProvider.Target(rs.getString(1), rs.getString(2)));
  }

  public void markPushed(UUID id) {
    db.update("update notifications set pushed_at = now(), updated_at = now() where id = :id", Map.of("id", id));
  }

  /** Classroom notifications pushed to the user within the last {@code windowMs}. */
  public long recentClassroomPushes(UUID userId, long windowMs) {
    Long n = db.queryForObject("select count(*) from notifications where user_id = :u and type like 'classroom.%' and pushed_at > now() - make_interval(secs => :s)",
        new MapSqlParameterSource().addValue("u", userId).addValue("s", windowMs / 1000.0), Long.class);
    return n == null ? 0 : n;
  }

  public void deletePushTokens(Collection<String> tokens) {
    if (!tokens.isEmpty()) db.update("delete from push_tokens where token in (:t)", Map.of("t", tokens));
  }

  /** Page query: newest first; {@code before}/{@code beforeId} is the keyset cursor (nullable). */
  public List<NotificationItem> list(UUID userId, boolean unreadOnly, Instant before, UUID beforeId, int limit) {
    StringBuilder sql = new StringBuilder("select id, type, title, body, link, created_at, read_at is not null as read from notifications where user_id = :u");
    var p = new MapSqlParameterSource().addValue("u", userId).addValue("lim", limit);
    if (unreadOnly) sql.append(" and read_at is null");
    if (before != null) {
      sql.append(" and (created_at < :c or (created_at = :c and id < :cid))");
      p.addValue("c", Timestamp.from(before)).addValue("cid", beforeId);
    }
    sql.append(" order by created_at desc, id desc limit :lim");
    return db.query(sql.toString(), p, (rs, i) -> new NotificationItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
        JsonConfig.iso(rs.getTimestamp(6).toInstant()), rs.getBoolean(7)));
  }

  /** Mark unread rows read; {@code ids == null} = all. */
  public void markRead(UUID userId, List<UUID> ids) {
    if (ids == null) db.update("update notifications set read_at = now(), updated_at = now() where user_id = :u and read_at is null", Map.of("u", userId));
    else db.update("update notifications set read_at = now(), updated_at = now() where user_id = :u and read_at is null and id in (:ids)", Map.of("u", userId, "ids", ids));
  }

  public long unreadCount(UUID userId) {
    Long n = db.queryForObject("select count(*) from notifications where user_id = :u and read_at is null", Map.of("u", userId), Long.class);
    return n == null ? 0 : n;
  }

  public void upsertPreference(UUID userId, String type, String channel, boolean enabled) {
    db.update("""
        insert into notification_preferences (id, user_id, type, channel, enabled) values (:id, :u, :t, :c, :e)
        on conflict (user_id, type, channel) do update set enabled = excluded.enabled, updated_at = now()""",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("t", type).addValue("c", channel).addValue("e", enabled));
  }

  public void upsertPushToken(UUID userId, String platform, String token) {
    db.update("""
        insert into push_tokens (id, user_id, platform, token) values (:id, :u, :p, :t)
        on conflict (token) do update set user_id = excluded.user_id, platform = excluded.platform, last_seen_at = now(), updated_at = now()""",
        new MapSqlParameterSource().addValue("id", Ids.uuidv7()).addValue("u", userId).addValue("p", platform).addValue("t", token));
  }

  public void deletePushToken(UUID userId, String token) {
    db.update("delete from push_tokens where user_id = :u and token = :t", Map.of("u", userId, "t", token));
  }
}
