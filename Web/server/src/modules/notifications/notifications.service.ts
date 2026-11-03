import { and, count, desc, eq, gt, inArray, isNull, lt, or, sql, type SQL } from 'drizzle-orm';
import { NOTIFICATION_TYPES, type NotificationChannel, type NotificationType } from '@shikkhasathi/shared';
import { coreDb } from '../../database/client';
import { notificationPreferences, notifications, pushTokens, users } from '../../database/core/schema';
import { defineJob, enqueue } from '../../common/jobs';
import { decodeCursor, toPage } from '../../common/pagination';
import { pushProvider } from '../../services/push';
import { sendEmail } from '../../services/email';
import { env } from '../../config/env';

/** Default channels per type (spec §10). Users override per type × channel. */
export const DEFAULT_CHANNELS: Record<NotificationType, NotificationChannel[]> = {
  'classroom.assignment_new': ['in_app', 'push'],
  'classroom.material_new': ['in_app', 'push'],
  'classroom.announcement': ['in_app', 'push'],
  'classroom.graded': ['in_app', 'push'],
  'classroom.due_24h': ['in_app', 'push'],
  'classroom.due_2h': ['in_app', 'push'],
  'classroom.overdue': ['in_app'],
  'exam.daily_plan': ['push'],
  'course.qa_answered': ['in_app', 'push'],
  'seller.qa_new': ['in_app', 'push'],
  'order.paid': ['in_app', 'push', 'email'],
  'order.refunded': ['in_app', 'push', 'email'],
  'request.published': ['in_app', 'push'],
  'seller.course_decision': ['in_app', 'push', 'email'],
  'seller.payout_paid': ['in_app', 'push', 'email'],
  'seller.new_enrollment': ['in_app', 'push'],
  'seller.application_result': ['in_app', 'push', 'email'],
  'admin.broadcast': ['in_app', 'push'],
};
/** Never batched / silenced by quiet hours. */
const URGENT = new Set<NotificationType>(['classroom.due_2h', 'order.paid', 'order.refunded']);

async function channelsFor(userId: string, type: NotificationType, extraDefaults: NotificationChannel[] = []) {
  const rows = await coreDb.select().from(notificationPreferences).where(and(eq(notificationPreferences.userId, userId), eq(notificationPreferences.type, type)));
  const set = new Set([...DEFAULT_CHANNELS[type], ...extraDefaults]);
  for (const r of rows) {
    if (r.enabled) set.add(r.channel as NotificationChannel);
    else set.delete(r.channel as NotificationChannel);
  }
  return set;
}

export interface NotifyInput {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  link?: string | null;
  data?: Record<string, unknown>;
  /** Unique per (user, type, ref) — duplicates are ignored. */
  ref: string;
  email?: { template: Parameters<typeof sendEmail>[0]['template']; data: Record<string, string | number | null | undefined> };
  /** turn email on for this notification unless the user explicitly switched it off (admin broadcasts) */
  emailByDefault?: boolean;
}

/** Create an in-app notification (deduped) and fan out to push/email per preferences. Returns true if new. */
export async function notify(n: NotifyInput): Promise<boolean> {
  const channels = await channelsFor(n.userId, n.type, n.emailByDefault ? ['email'] : []);
  const dedupeKey = `${n.userId}:${n.type}:${n.ref}`;
  const [row] = await coreDb
    .insert(notifications)
    .values({ userId: n.userId, type: n.type, title: n.title, body: n.body, link: n.link ?? null, dataJson: n.data ?? null, dedupeKey, readAt: channels.has('in_app') ? null : new Date() })
    .onConflictDoNothing({ target: notifications.dedupeKey })
    .returning({ id: notifications.id });
  if (!row) return false;
  if (channels.has('push')) await enqueue('notifications.dispatch', { notificationId: row.id });
  if (channels.has('email') && n.email) {
    const [u] = await coreDb.select({ email: users.email, name: users.name, locale: users.locale }).from(users).where(eq(users.id, n.userId));
    if (u?.email) await sendEmail({ to: u.email, toName: u.name, template: n.email.template, data: n.email.data, locale: u.locale as 'bn' | 'en', relatedEntity: `notification:${row.id}` });
  }
  return true;
}

/** Asia/Dhaka quiet hours 23:00–07:00 for non-urgent push. */
export function inQuietHours(now = new Date()) {
  const h = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Dhaka', hour: '2-digit', hour12: false }).format(now));
  return h >= 23 || h < 7;
}

const BURST_WINDOW_MS = 10 * 60_000;
const BURST_MAX = 3;

export async function dispatchPush(job: { notificationId: string }) {
  const [n] = await coreDb.select().from(notifications).where(eq(notifications.id, job.notificationId));
  if (!n || n.pushedAt) return;
  const type = n.type as NotificationType;
  const urgent = URGENT.has(type);
  if (!urgent && inQuietHours()) {
    // retry at 07:00 Dhaka; BullMQ delay computed from now
    const now = new Date();
    const dhakaHour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Dhaka', hour: '2-digit', hour12: false }).format(now));
    const hoursLeft = dhakaHour >= 23 ? 24 - dhakaHour + 7 : 7 - dhakaHour;
    await enqueue('notifications.dispatch', job, { delay: hoursLeft * 3_600_000, jobId: `push-${n.id}` });
    return;
  }
  const tokens = await coreDb.select({ platform: pushTokens.platform, token: pushTokens.token }).from(pushTokens).where(eq(pushTokens.userId, n.userId));
  await coreDb.update(notifications).set({ pushedAt: new Date() }).where(eq(notifications.id, n.id));
  if (!tokens.length) return;
  const provider = await pushProvider();
  if (!provider) return;
  let msg = { title: n.title, body: n.body, link: n.link ? `${env.APP_URL}${n.link}` : null };
  if (!urgent && type.startsWith('classroom.')) {
    // Burst batching: > 3 Classroom pushes in 10 min → one summary push for the window.
    const [{ value: recent } = { value: 0 }] = await coreDb
      .select({ value: count() })
      .from(notifications)
      .where(and(eq(notifications.userId, n.userId), sql`${notifications.type} like 'classroom.%'`, gt(notifications.pushedAt, new Date(Date.now() - BURST_WINDOW_MS))));
    if (recent > BURST_MAX + 1) return; // summary already sent for this window
    if (recent > BURST_MAX) msg = { title: 'Classroom-এ নতুন আপডেট', body: `আপনার ক্লাসগুলোতে ${recent}টি নতুন আপডেট এসেছে`, link: `${env.APP_URL}/home` };
  }
  const { invalid } = await provider.send(tokens, msg);
  if (invalid.length) await coreDb.delete(pushTokens).where(inArray(pushTokens.token, invalid));
}
defineJob('notifications.dispatch', dispatchPush);

// ---------------------------------------------------------------- queries

export async function listNotifications(userId: string, q: { cursor?: string; limit: number; unread?: boolean }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(notifications.userId, userId)];
  if (q.unread) conds.push(isNull(notifications.readAt));
  if (cur) conds.push(or(lt(notifications.createdAt, new Date(String(cur[0]))), and(eq(notifications.createdAt, new Date(String(cur[0]))), lt(notifications.id, cur[1])))!);
  const rows = await coreDb.select().from(notifications).where(and(...conds)).orderBy(desc(notifications.createdAt), desc(notifications.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: pg.items.map((r) => ({ id: r.id, type: r.type as NotificationType, title: r.title, body: r.body, link: r.link, createdAt: r.createdAt.toISOString(), read: !!r.readAt })), nextCursor: pg.nextCursor };
}

export async function markRead(userId: string, b: { ids?: string[]; all?: boolean }) {
  const conds: SQL[] = [eq(notifications.userId, userId), isNull(notifications.readAt)];
  if (!b.all) {
    if (!b.ids?.length) return;
    conds.push(inArray(notifications.id, b.ids));
  }
  await coreDb.update(notifications).set({ readAt: new Date() }).where(and(...conds));
}

export async function unreadCount(userId: string) {
  const [r] = await coreDb.select({ value: count() }).from(notifications).where(and(eq(notifications.userId, userId), isNull(notifications.readAt)));
  return r?.value ?? 0;
}

export async function getPreferences(userId: string) {
  const rows = await coreDb.select().from(notificationPreferences).where(eq(notificationPreferences.userId, userId));
  const items: { type: NotificationType; channel: NotificationChannel; enabled: boolean }[] = [];
  for (const type of NOTIFICATION_TYPES) {
    for (const channel of ['in_app', 'push', 'email', 'sms'] as const) {
      const r = rows.find((x) => x.type === type && x.channel === channel);
      items.push({ type, channel, enabled: r ? r.enabled : DEFAULT_CHANNELS[type].includes(channel) });
    }
  }
  return { items };
}

export async function setPreferences(userId: string, items: { type: NotificationType; channel: NotificationChannel; enabled: boolean }[]) {
  for (const i of items) {
    await coreDb
      .insert(notificationPreferences)
      .values({ userId, ...i })
      .onConflictDoUpdate({ target: [notificationPreferences.userId, notificationPreferences.type, notificationPreferences.channel], set: { enabled: i.enabled, updatedAt: new Date() } });
  }
  return getPreferences(userId);
}

export async function registerPushToken(userId: string, platform: string, token: string) {
  await coreDb
    .insert(pushTokens)
    .values({ userId, platform, token })
    .onConflictDoUpdate({ target: pushTokens.token, set: { userId, platform, lastSeenAt: new Date() } });
}

export async function unregisterPushToken(userId: string, token: string) {
  await coreDb.delete(pushTokens).where(and(eq(pushTokens.userId, userId), eq(pushTokens.token, token)));
}
