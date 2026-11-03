import { and, asc, eq, gte, lt } from 'drizzle-orm';
import type { Dashboard } from '@shikkhasathi/shared';
import { coreDb } from '../../database/client';
import { examPlans } from '../../database/core/schema';
import { redis } from '../../common/redis';
import { logger } from '../../common/logger';
import { getMe } from '../auth/auth.service';
import { unreadCount } from '../notifications/notifications.service';
import * as classroom from '../classroom/classroom.service';

const TTL_SEC = 60;
const key = (userId: string) => `dash:${userId}`;

/** Extension points filled by later phases (learning → continue, discovery → recommendations). */
type Provider<T> = (userId: string) => Promise<T | null>;
const providers: { continueLearning: Provider<NonNullable<Dashboard['continueLearning']['data']>>; recommendations: Provider<NonNullable<Dashboard['recommendations']['data']>> } = {
  continueLearning: async () => null,
  recommendations: async () => [],
};
export function setDashboardProvider<K extends keyof typeof providers>(k: K, fn: (typeof providers)[K]) {
  providers[k] = fn;
}

export async function invalidateDashboard(userId: string) {
  await redis().del(key(userId)).catch(() => undefined);
}

/** Each widget resolves independently — one failing never blanks the page (spec §6.2). */
async function section<T>(name: string, fn: () => Promise<T | null>): Promise<{ data: T | null; error: string | null }> {
  try {
    return { data: await fn(), error: null };
  } catch (err) {
    logger.warn({ widget: name, err: (err as Error).message }, 'dashboard widget failed');
    return { data: null, error: 'unavailable' };
  }
}

const dhakaDay = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);

export async function nearestExam(userId: string, now = new Date()) {
  const [p] = await coreDb
    .select()
    .from(examPlans)
    .where(and(eq(examPlans.studentId, userId), eq(examPlans.active, true), gte(examPlans.examDate, new Date(now.getTime() - 12 * 3_600_000))))
    .orderBy(asc(examPlans.examDate))
    .limit(1);
  if (!p) return null;
  const daysLeft = Math.max(0, Math.round((Date.parse(dhakaDay(p.examDate)) - Date.parse(dhakaDay(now))) / 86_400_000));
  return { planId: p.id, subjectName: p.subjectName, courseCode: p.courseCode, examType: p.examType, examDate: p.examDate.toISOString(), daysLeft, readiness: p.readiness };
}

async function week(userId: string, now = new Date()) {
  const start = new Date(`${dhakaDay(now)}T00:00:00+06:00`);
  const end = new Date(start.getTime() + 7 * 86_400_000);
  const [deadlines, exams] = await Promise.all([
    classroom.deadlineCountsByDay(userId, start, end),
    coreDb.select({ d: examPlans.examDate }).from(examPlans).where(and(eq(examPlans.studentId, userId), eq(examPlans.active, true), gte(examPlans.examDate, start), lt(examPlans.examDate, end))),
  ]);
  return Array.from({ length: 7 }, (_, i) => {
    const day = dhakaDay(new Date(start.getTime() + i * 86_400_000 + 3_600_000));
    return { date: day, deadlines: deadlines.get(day) ?? 0, exams: exams.filter((e) => dhakaDay(e.d) === day).length };
  });
}

export async function getDashboard(userId: string, opts: { fresh?: boolean } = {}): Promise<Dashboard> {
  if (!opts.fresh) {
    const hit = await redis().get(key(userId)).catch(() => null);
    if (hit) return JSON.parse(hit) as Dashboard;
  }
  const [me, unread, status] = await Promise.all([getMe(userId), unreadCount(userId), classroom.getStatus(userId)]);
  const connected = status.connected && status.status !== 'needs_reauth';
  const [dueSoon, feed, exam, continueLearning, wk, recommendations] = await Promise.all([
    section('dueSoon', async () => (connected ? classroom.dueSoonGroups(userId) : null)),
    section('feed', async () => {
      if (!connected) return null;
      const [page, unreadFeed] = await Promise.all([classroom.feed(userId, { limit: 8 }), classroom.unreadFeedCount(userId)]);
      return { items: page.items, unread: unreadFeed };
    }),
    section('exam', () => nearestExam(userId)),
    section('continueLearning', () => providers.continueLearning(userId)),
    section('week', () => week(userId)),
    section('recommendations', () => providers.recommendations(userId)),
  ]);
  const out: Dashboard = {
    header: { name: me.name, institutionShort: me.academic.institutionShort, departmentShort: me.academic.departmentShort, semester: me.academic.semester, unreadNotifications: unread, classroom: status },
    dueSoon,
    feed,
    exam,
    continueLearning,
    week: wk,
    recommendations,
    generatedAt: new Date().toISOString(),
  };
  await redis().set(key(userId), JSON.stringify(out), 'EX', TTL_SEC).catch(() => undefined);
  return out;
}
