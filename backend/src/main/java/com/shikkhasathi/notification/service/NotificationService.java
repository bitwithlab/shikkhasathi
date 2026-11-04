package com.shikkhasathi.notification.service;

import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Ids;
import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.notification.dto.NotificationDtos.NotificationItem;
import com.shikkhasathi.notification.dto.NotificationDtos.Preference;
import com.shikkhasathi.notification.email.EmailService;
import com.shikkhasathi.notification.push.PushProvider;
import com.shikkhasathi.notification.push.PushService;
import com.shikkhasathi.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * In-app notification center + fan-out (spec §10): {@link #notify} stores a deduped notification and, per the
 * user's channel preferences, queues push ({@code notifications.dispatch}) and email. Push respects Asia/Dhaka quiet
 * hours (23:00–07:00, except urgent types) and batches Classroom bursts (&gt; 3 in 10 min → one summary).
 */
@Service
public class NotificationService {
  /** Payload of the {@code notifications.dispatch} job. */
  public record DispatchJob(UUID notificationId) {}

  public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
  private static final long BURST_WINDOW_MS = 10 * 60_000L;
  private static final int BURST_MAX = 3;

  private final NotificationRepository repo;
  private final JobQueue jobs;
  private final EmailService email;
  private final PushService push;
  private final AppProperties props;
  private volatile Clock clock;

  public NotificationService(NotificationRepository repo, JobQueue jobs, EmailService email, PushService push, AppProperties props, Clock clock) {
    this.repo = repo;
    this.jobs = jobs;
    this.email = email;
    this.push = push;
    this.props = props;
    this.clock = clock;
  }

  /** Tests only: fix "now" for quiet-hour decisions (null restores the system clock). */
  public void useClock(Clock c) {
    this.clock = c == null ? Clock.systemUTC() : c;
  }

  /** Effective channels for a user and type: defaults (+ extra defaults) overridden by saved preferences. */
  public Set<String> channelsFor(UUID userId, String type, List<String> extraDefaults) {
    Set<String> set = new LinkedHashSet<>(NotificationTypes.DEFAULT_CHANNELS.getOrDefault(type, List.of()));
    set.addAll(extraDefaults);
    for (var r : repo.preferencesFor(userId, type)) {
      if (r.enabled()) set.add(r.channel());
      else set.remove(r.channel());
    }
    return set;
  }

  /**
   * Create an in-app notification (deduped by {@code user:type:ref}) and fan out to push/email per preferences.
   * When the in-app channel is off the row is stored already read. Returns true if it was new.
   */
  public boolean notify(NotifyInput n) {
    if (!NotificationTypes.isType(n.type())) throw new IllegalArgumentException("unknown notification type " + n.type());
    Set<String> channels = channelsFor(n.userId(), n.type(), n.emailByDefault() ? List.of(NotificationTypes.EMAIL) : List.of());
    Optional<UUID> id = repo.insertIfNew(Ids.uuidv7(), n, n.dedupeKey(), !channels.contains(NotificationTypes.IN_APP));
    if (id.isEmpty()) return false;
    if (channels.contains(NotificationTypes.PUSH)) jobs.enqueue(JobNames.NOTIFICATIONS_DISPATCH, new DispatchJob(id.get()));
    if (channels.contains(NotificationTypes.EMAIL) && n.emailTemplate() != null) {
      repo.userContact(n.userId()).filter(u -> u.email() != null && !u.email().isEmpty()).ifPresent(u ->
          email.send(u.email(), u.name(), n.emailTemplate(), n.emailData() == null ? Map.of() : n.emailData(), u.locale(), "notification:" + id.get(), List.of()));
    }
    return true;
  }

  /** Asia/Dhaka quiet hours 23:00–07:00 for non-urgent push. */
  public static boolean inQuietHours(Instant now) {
    int h = now.atZone(DHAKA).getHour();
    return h >= 23 || h < 7;
  }

  /** {@code notifications.dispatch}: push one notification (quiet hours → re-queued for 07:00 Dhaka). */
  public void dispatchPush(UUID notificationId) {
    var n = repo.forDispatch(notificationId).orElse(null);
    if (n == null || n.pushedAt() != null) return;
    boolean urgent = NotificationTypes.URGENT.contains(n.type());
    Instant now = clock.instant();
    if (!urgent && inQuietHours(now)) {
      int dhakaHour = now.atZone(DHAKA).getHour();
      int hoursLeft = dhakaHour >= 23 ? 24 - dhakaHour + 7 : 7 - dhakaHour;
      jobs.enqueue(JobNames.NOTIFICATIONS_DISPATCH, new DispatchJob(n.id()), EnqueueOptions.delay(hoursLeft * 3_600_000L).withJobId("push-" + n.id()));
      return;
    }
    var tokens = repo.pushTargets(n.userId());
    repo.markPushed(n.id());
    if (tokens.isEmpty()) return;
    var provider = push.provider().orElse(null);
    if (provider == null) return;
    var msg = new PushProvider.PushMessage(n.title(), n.body(), n.link() != null ? props.appUrl() + n.link() : null);
    if (!urgent && n.type().startsWith("classroom.")) {
      // Burst batching: > 3 Classroom pushes in 10 min → one summary push for the window.
      long recent = repo.recentClassroomPushes(n.userId(), BURST_WINDOW_MS);
      if (recent > BURST_MAX + 1) return; // summary already sent for this window
      if (recent > BURST_MAX) msg = new PushProvider.PushMessage("Classroom-এ নতুন আপডেট", "আপনার ক্লাসগুলোতে " + recent + "টি নতুন আপডেট এসেছে", props.appUrl() + "/home");
    }
    List<String> invalid = provider.send(tokens, msg);
    if (!invalid.isEmpty()) repo.deletePushTokens(invalid);
  }

  // ---------------------------------------------------------------- queries

  /** Newest first, cursor-paginated. */
  public Cursor.Page<NotificationItem> list(UUID userId, String cursor, int limit, boolean unreadOnly) {
    var cur = Cursor.decode(cursor);
    Instant before = null;
    UUID beforeId = null;
    if (cur != null) {
      try {
        before = Instant.parse(cur.sortString());
        beforeId = UUID.fromString(cur.id());
      } catch (RuntimeException e) {
        throw com.shikkhasathi.common.ApiException.badRequest("অবৈধ cursor");
      }
    }
    var rows = repo.list(userId, unreadOnly, before, beforeId, limit + 1);
    return Cursor.page(rows, limit, r -> new Cursor.Value(r.createdAt(), r.id().toString()));
  }

  /** Mark the given ids (or everything with {@code all}) read. */
  public void markRead(UUID userId, List<UUID> ids, boolean all) {
    if (!all && (ids == null || ids.isEmpty())) return;
    repo.markRead(userId, all ? null : ids);
  }

  public long unreadCount(UUID userId) {
    return repo.unreadCount(userId);
  }

  /** Every type × channel with the effective enabled flag. */
  public List<Preference> preferences(UUID userId) {
    var rows = repo.preferences(userId);
    List<Preference> items = new ArrayList<>();
    for (String type : NotificationTypes.ALL) {
      for (String channel : NotificationTypes.CHANNELS) {
        var r = rows.stream().filter(x -> x.type().equals(type) && x.channel().equals(channel)).findFirst();
        items.add(new Preference(type, channel, r.map(NotificationRepository.PrefRow::enabled).orElse(NotificationTypes.DEFAULT_CHANNELS.get(type).contains(channel))));
      }
    }
    return items;
  }

  /** Upsert preferences, return the full effective list. */
  public List<Preference> setPreferences(UUID userId, List<Preference> items) {
    for (var i : items) repo.upsertPreference(userId, i.type(), i.channel(), i.enabled());
    return preferences(userId);
  }

  /** Register (or move to this user) a device push token. */
  public void registerPushToken(UUID userId, String platform, String token) {
    repo.upsertPushToken(userId, platform, token);
  }

  public void unregisterPushToken(UUID userId, String token) {
    repo.deletePushToken(userId, token);
  }
}
