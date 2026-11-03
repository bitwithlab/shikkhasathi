import { createHash } from 'node:crypto';
import { and, asc, count, desc, eq, gt, gte, inArray, isNotNull, isNull, lt, lte, or, sql, type SQL } from 'drizzle-orm';
import { CLASSROOM_SCOPES, type AssignmentStatus, type ClassroomStatus, type Material } from '@shikkhasathi/shared';
import { coreDb } from '../../database/client';
import {
  classroomAnnouncements, classroomCourses, classroomCoursework, classroomFeedItems, classroomMaterials, classroomPushRegistrations, classroomSubmissions, classroomSyncRuns, examPlans, googleConnections, notifications, users,
} from '../../database/core/schema';
import { decrypt, encrypt, randomToken } from '../../common/crypto';
import { AppError, badRequest, notFound } from '../../common/errors';
import { signPurposeToken, verifyPurposeToken } from '../../common/jwt';
import { defineJob, enqueue } from '../../common/jobs';
import { decodeCursor, toPage } from '../../common/pagination';
import { logger } from '../../common/logger';
import { env, isProd } from '../../config/env';
import { getBusinessFlag, getIntegration, registerIntegrationTest } from '../settings/settings.service';
import { notify } from '../notifications/notifications.service';
import { redis } from '../../common/redis';

const dropDash = (userId: string) => redis().del(`dash:${userId}`).catch(() => undefined);
import { ClassroomError, FakeClassroomClient, RealClassroomClient, type GoogleClassroomClient } from '../../services/google/classroom';

type Conn = typeof googleConnections.$inferSelect;

/** Class colors (stable per course order) — distinct, AA on white text-free stripes. */
const COLORS = ['#4F46E5', '#0EA5E9', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#14B8A6', '#F97316', '#64748B'];
const MANUAL_SYNC_COOLDOWN_MS = 60_000;
export const REDIRECT_PATH = '/api/v1/classroom/oauth/callback';
const redirectUri = () => `${env.APP_URL}${REDIRECT_PATH}`;

// ---------------------------------------------------------------- client selection

async function realConfig() {
  const g = await getIntegration('google');
  return g.client_id && g.client_secret ? { clientId: g.client_id, clientSecret: g.client_secret, topic: g.pubsub_topic ?? null } : null;
}

/** Test hook: lets tests inject a fake with a controllable clock. */
let clientOverride: ((conn: Conn | null) => GoogleClassroomClient) | null = null;
export function __setClassroomClientFactory(f: typeof clientOverride) {
  clientOverride = f;
}

export async function clientFor(conn: Conn | null): Promise<GoogleClassroomClient> {
  if (clientOverride) return clientOverride(conn);
  if (conn?.isFake) return new FakeClassroomClient(conn.createdAt);
  const cfg = await realConfig();
  if (cfg) return new RealClassroomClient({ clientId: cfg.clientId, clientSecret: cfg.clientSecret, scopes: CLASSROOM_SCOPES });
  if (isProd) throw new AppError('INTEGRATION_DISABLED', 'Google Classroom সংযোগ এখন চালু নেই।');
  return new FakeClassroomClient(conn?.createdAt ?? new Date());
}

async function ensureEnabled() {
  if (!(await getBusinessFlag('feature_classroom'))) throw new AppError('INTEGRATION_DISABLED', 'Google Classroom ফিচার এখন বন্ধ আছে।');
}

const connFor = async (userId: string) => (await coreDb.select().from(googleConnections).where(eq(googleConnections.userId, userId)))[0] ?? null;

// ---------------------------------------------------------------- connect / OAuth

const b64url = (b: Buffer) => b.toString('base64url');

export async function connectUrl(userId: string, returnTo?: string) {
  await ensureEnabled();
  const client = await clientFor(null);
  const verifier = randomToken(48);
  const challenge = b64url(createHash('sha256').update(verifier).digest());
  const safeReturn = returnTo && returnTo.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/home';
  const state = await signPurposeToken('gc-oauth', { uid: userId, v: verifier, r: safeReturn, f: client.fake }, 10 * 60);
  const [u] = await coreDb.select({ email: users.email }).from(users).where(eq(users.id, userId));
  return { url: client.authUrl({ state, codeChallenge: challenge, redirectUri: redirectUri(), loginHint: u?.email ?? undefined }), fake: client.fake };
}

/** Web redirect target. Always returns a path to redirect the browser to. */
export async function oauthCallback(q: { code?: string; state?: string; error?: string }): Promise<string> {
  let returnTo = '/home';
  try {
    if (!q.state) throw badRequest();
    const st = await verifyPurposeToken<{ uid: string; v: string; r: string; f: boolean }>('gc-oauth', q.state);
    returnTo = st.r;
    if (q.error || !q.code) return withQuery(returnTo, { classroom: q.error === 'access_denied' ? 'denied' : 'error' });
    const client = st.f ? new FakeClassroomClient(new Date()) : await clientFor(null);
    const tokens = await client.exchangeCode({ code: q.code, redirectUri: redirectUri(), codeVerifier: st.v });
    await saveConnection(st.uid, tokens, client.fake);
    return withQuery(returnTo, { classroom: 'connected' });
  } catch (err) {
    logger.warn({ err: (err as Error).message }, 'classroom oauth callback failed');
    const reason = err instanceof ClassroomError && err.kind === 'workspace' ? 'workspace' : 'error';
    return withQuery(returnTo, { classroom: reason });
  }
}

function withQuery(path: string, q: Record<string, string>) {
  const u = new URL(path, 'http://x');
  for (const [k, v] of Object.entries(q)) u.searchParams.set(k, v);
  return `${u.pathname}${u.search}${u.hash}`;
}

/** Mobile: server auth code from Credential Manager / GoogleSignIn. */
export async function connectWithCode(userId: string, code: string, redirect?: string) {
  await ensureEnabled();
  const client = code.startsWith('fake') && !isProd ? new FakeClassroomClient(new Date()) : await clientFor(null);
  const tokens = await client.exchangeCode({ code, redirectUri: redirect ?? '' });
  await saveConnection(userId, tokens, client.fake);
  return getStatus(userId);
}

async function saveConnection(userId: string, t: Awaited<ReturnType<GoogleClassroomClient['exchangeCode']>>, isFake: boolean) {
  const existing = await connFor(userId);
  const refresh = t.refreshToken ?? (existing ? decrypt(existing.refreshTokenEnc) : null);
  if (!refresh) throw new ClassroomError('reauth', 'no refresh token');
  const values = {
    googleUserId: t.googleUserId,
    email: t.email,
    refreshTokenEnc: encrypt(refresh),
    accessTokenEnc: encrypt(t.accessToken),
    accessTokenExpiresAt: t.expiresAt,
    scopes: t.scopes,
    status: 'syncing',
    errorCode: null,
    errorMessage: null,
    isFake,
  };
  if (existing) await coreDb.update(googleConnections).set({ ...values, ...(existing.isFake !== isFake ? { createdAt: new Date() } : {}) }).where(eq(googleConnections.id, existing.id));
  else await coreDb.insert(googleConnections).values({ userId, ...values, createdAt: new Date() });
  await enqueue('classroom.fullSync', { userId, trigger: existing ? 'reconnect' : 'connect' });
}

async function accessToken(conn: Conn, client: GoogleClassroomClient) {
  if (conn.accessTokenEnc && conn.accessTokenExpiresAt && conn.accessTokenExpiresAt.getTime() > Date.now() + 30_000) return decrypt(conn.accessTokenEnc);
  const r = await client.refresh(decrypt(conn.refreshTokenEnc));
  await coreDb.update(googleConnections).set({ accessTokenEnc: encrypt(r.accessToken), accessTokenExpiresAt: r.expiresAt }).where(eq(googleConnections.id, conn.id));
  return r.accessToken;
}

// ---------------------------------------------------------------- status derivation

/** Pending · Turned in · Graded (returned) · Missing (past due, not turned in). */
export function assignmentStatus(sub: { state: string; assignedGrade: string | number | null } | undefined, dueAt: Date | null, now = new Date()): AssignmentStatus {
  if (sub?.state === 'RETURNED') return 'graded';
  if (sub?.state === 'TURNED_IN') return 'turned_in';
  if (dueAt && dueAt.getTime() < now.getTime()) return 'missing';
  return 'pending';
}

// ---------------------------------------------------------------- sync

interface SyncJob {
  userId: string;
  trigger: 'connect' | 'reconnect' | 'push' | 'poll' | 'manual';
  courseId?: string;
}

const num = (v: number | null) => (v === null ? null : String(v));

async function markError(conn: Conn, err: unknown) {
  const e = err instanceof ClassroomError ? err : new ClassroomError('other', (err as Error)?.message ?? 'unknown');
  if (e.kind === 'reauth') {
    await coreDb.update(googleConnections).set({ status: 'needs_reauth', errorCode: 'invalid_grant', errorMessage: e.message.slice(0, 300) }).where(eq(googleConnections.id, conn.id));
  } else if (e.kind === 'workspace') {
    await coreDb.update(googleConnections).set({ status: 'workspace_blocked', errorCode: 'workspace_blocked', errorMessage: e.message.slice(0, 300) }).where(eq(googleConnections.id, conn.id));
  } else if (e.kind === 'quota') {
    // exponential-ish backoff: 5 min, doubling up to 2 h via next_sync_allowed_at
    const prev = conn.nextSyncAllowedAt && conn.nextSyncAllowedAt > new Date() ? conn.nextSyncAllowedAt.getTime() - Date.now() : 2.5 * 60_000;
    const wait = Math.min(prev * 2, 2 * 3_600_000);
    await coreDb.update(googleConnections).set({ status: 'active', errorCode: 'quota', nextSyncAllowedAt: new Date(Date.now() + wait) }).where(eq(googleConnections.id, conn.id));
  } else {
    await coreDb.update(googleConnections).set({ status: conn.lastSyncAt ? 'active' : 'error', errorCode: 'error', errorMessage: e.message.slice(0, 300) }).where(eq(googleConnections.id, conn.id));
  }
  return e;
}

/**
 * Pull everything (or one course), upsert the cache, diff against what we had, and emit feed items + notifications.
 * The first full sync builds the feed silently (no notification burst); later syncs notify on changes.
 */
export async function runSync(job: SyncJob) {
  const conn = await connFor(job.userId);
  if (!conn || conn.status === 'needs_reauth' || conn.status === 'workspace_blocked') return { changes: 0 };
  const full = job.trigger === 'connect' || job.trigger === 'reconnect' || !conn.lastFullSyncAt;
  const silent = !conn.lastFullSyncAt;
  const [run] = await coreDb.insert(classroomSyncRuns).values({ userId: job.userId, kind: full ? 'full' : 'incremental', trigger: job.trigger }).returning({ id: classroomSyncRuns.id });
  let changes = 0;
  try {
    const client = await clientFor(conn);
    const at = await accessToken(conn, client);
    const courses = await client.listCourses(at);
    const target = job.courseId ? courses.filter((c) => c.id === job.courseId) : courses;

    // courses (colors stable by position in the full list)
    const existingCourses = await coreDb.select().from(classroomCourses).where(eq(classroomCourses.userId, job.userId));
    for (const [i, c] of courses.entries()) {
      const prev = existingCourses.find((x) => x.gcCourseId === c.id);
      const color = prev?.color ?? COLORS[i % COLORS.length]!;
      await coreDb
        .insert(classroomCourses)
        .values({ userId: job.userId, gcCourseId: c.id, name: c.name, section: c.section, room: c.room, descriptionHeading: c.descriptionHeading, teacherNames: c.teacherNames, alternateLink: c.alternateLink, color, state: c.state, updatedAtGc: c.updateTime })
        .onConflictDoUpdate({ target: [classroomCourses.userId, classroomCourses.gcCourseId], set: { name: c.name, section: c.section, room: c.room, teacherNames: c.teacherNames, alternateLink: c.alternateLink, state: c.state, updatedAtGc: c.updateTime, updatedAt: new Date() } });
    }
    if (!job.courseId) {
      const gone = existingCourses.filter((x) => !courses.some((c) => c.id === x.gcCourseId)).map((x) => x.gcCourseId);
      if (gone.length) await purgeCourses(job.userId, gone);
    }
    const courseName = new Map(courses.map((c) => [c.id, c.name]));

    for (const c of target) {
      const [work, subs, mats, anns] = await Promise.all([client.listCourseWork(at, c.id), client.listSubmissions(at, c.id), client.listMaterials(at, c.id), client.listAnnouncements(at, c.id)]);
      const [prevWork, prevSubs, prevMats, prevAnns] = await Promise.all([
        coreDb.select().from(classroomCoursework).where(and(eq(classroomCoursework.userId, job.userId), eq(classroomCoursework.gcCourseId, c.id))),
        coreDb.select().from(classroomSubmissions).where(and(eq(classroomSubmissions.userId, job.userId), eq(classroomSubmissions.gcCourseId, c.id))),
        coreDb.select({ id: classroomMaterials.gcMaterialId }).from(classroomMaterials).where(and(eq(classroomMaterials.userId, job.userId), eq(classroomMaterials.gcCourseId, c.id))),
        coreDb.select({ id: classroomAnnouncements.gcAnnouncementId }).from(classroomAnnouncements).where(and(eq(classroomAnnouncements.userId, job.userId), eq(classroomAnnouncements.gcCourseId, c.id))),
      ]);
      const name = courseName.get(c.id) ?? c.name;

      for (const w of work) {
        const prev = prevWork.find((p) => p.gcCourseworkId === w.id);
        await coreDb
          .insert(classroomCoursework)
          .values({ userId: job.userId, gcCourseId: c.id, gcCourseworkId: w.id, title: w.title, description: w.description, workType: w.workType, dueAt: w.dueAt, maxPoints: num(w.maxPoints), materialsJson: w.materials, alternateLink: w.alternateLink, state: w.state, createdAtGc: w.creationTime, updatedAtGc: w.updateTime })
          .onConflictDoUpdate({ target: [classroomCoursework.userId, classroomCoursework.gcCourseworkId], set: { title: w.title, description: w.description, dueAt: w.dueAt, maxPoints: num(w.maxPoints), materialsJson: w.materials, alternateLink: w.alternateLink, state: w.state, updatedAtGc: w.updateTime, updatedAt: new Date() } });
        if (!prev) {
          changes++;
          await addFeed(job.userId, 'assignment', c.id, w.id, w.title, w.creationTime ?? new Date());
          if (!silent) await notify({ userId: job.userId, type: 'classroom.assignment_new', ref: w.id, title: `নতুন অ্যাসাইনমেন্ট · ${shortCourse(name)}`, body: w.title + (w.dueAt ? ` — জমা ${dhaka(w.dueAt)}` : ''), link: `/classroom/assignments?id=${encodeURIComponent(w.id)}` });
        } else if (prev.dueAt?.getTime() !== w.dueAt?.getTime() || prev.title !== w.title) changes++;
      }
      const removedWork = prevWork.filter((p) => !work.some((w) => w.id === p.gcCourseworkId)).map((p) => p.gcCourseworkId);
      if (removedWork.length) {
        await coreDb.delete(classroomCoursework).where(and(eq(classroomCoursework.userId, job.userId), inArray(classroomCoursework.gcCourseworkId, removedWork)));
        await coreDb.delete(classroomSubmissions).where(and(eq(classroomSubmissions.userId, job.userId), inArray(classroomSubmissions.gcCourseworkId, removedWork)));
        await coreDb.delete(classroomFeedItems).where(and(eq(classroomFeedItems.userId, job.userId), inArray(classroomFeedItems.refId, removedWork)));
      }

      for (const s of subs) {
        const prev = prevSubs.find((p) => p.gcCourseworkId === s.courseWorkId);
        await coreDb
          .insert(classroomSubmissions)
          .values({ userId: job.userId, gcCourseId: c.id, gcCourseworkId: s.courseWorkId, gcSubmissionId: s.id, state: s.state, late: s.late, assignedGrade: num(s.assignedGrade), alternateLink: s.alternateLink, updatedAtGc: s.updateTime })
          .onConflictDoUpdate({ target: [classroomSubmissions.userId, classroomSubmissions.gcCourseworkId], set: { state: s.state, late: s.late, assignedGrade: num(s.assignedGrade), alternateLink: s.alternateLink, updatedAtGc: s.updateTime, updatedAt: new Date() } });
        const becameGraded = s.state === 'RETURNED' && s.assignedGrade !== null && (prev?.state !== 'RETURNED' || prev?.assignedGrade !== num(s.assignedGrade));
        if (prev?.state !== s.state) changes++;
        if (becameGraded) {
          const w = work.find((x) => x.id === s.courseWorkId);
          await addFeed(job.userId, 'graded', c.id, s.courseWorkId, w?.title ?? '', s.updateTime ?? new Date());
          if (!silent) await notify({ userId: job.userId, type: 'classroom.graded', ref: `${s.courseWorkId}:${s.assignedGrade}`, title: `গ্রেড এসেছে · ${shortCourse(name)}`, body: `${w?.title ?? ''} — ${s.assignedGrade}${w?.maxPoints ? `/${w.maxPoints}` : ''}`, link: `/classroom/assignments?id=${encodeURIComponent(s.courseWorkId)}` });
        }
      }

      for (const m of mats) {
        await coreDb
          .insert(classroomMaterials)
          .values({ userId: job.userId, gcCourseId: c.id, gcMaterialId: m.id, title: m.title, description: m.description, materialsJson: m.materials, alternateLink: m.alternateLink, createdAtGc: m.creationTime, updatedAtGc: m.updateTime })
          .onConflictDoUpdate({ target: [classroomMaterials.userId, classroomMaterials.gcMaterialId], set: { title: m.title, description: m.description, materialsJson: m.materials, alternateLink: m.alternateLink, updatedAtGc: m.updateTime, updatedAt: new Date() } });
        if (!prevMats.some((p) => p.id === m.id)) {
          changes++;
          await addFeed(job.userId, 'material', c.id, m.id, m.title, m.creationTime ?? new Date());
          if (!silent) await notify({ userId: job.userId, type: 'classroom.material_new', ref: m.id, title: `নতুন ম্যাটেরিয়াল · ${shortCourse(name)}`, body: m.title, link: `/classroom/${encodeURIComponent(c.id)}?tab=materials` });
        }
      }
      const removedMats = prevMats.filter((p) => !mats.some((m) => m.id === p.id)).map((p) => p.id);
      if (removedMats.length) await coreDb.delete(classroomMaterials).where(and(eq(classroomMaterials.userId, job.userId), inArray(classroomMaterials.gcMaterialId, removedMats)));

      for (const a of anns) {
        await coreDb
          .insert(classroomAnnouncements)
          .values({ userId: job.userId, gcCourseId: c.id, gcAnnouncementId: a.id, text: a.text, authorName: a.authorName, materialsJson: a.materials, alternateLink: a.alternateLink, createdAtGc: a.creationTime, updatedAtGc: a.updateTime })
          .onConflictDoUpdate({ target: [classroomAnnouncements.userId, classroomAnnouncements.gcAnnouncementId], set: { text: a.text, materialsJson: a.materials, alternateLink: a.alternateLink, updatedAtGc: a.updateTime, updatedAt: new Date() } });
        if (!prevAnns.some((p) => p.id === a.id)) {
          changes++;
          const title = a.text.length > 90 ? `${a.text.slice(0, 88)}…` : a.text;
          await addFeed(job.userId, 'announcement', c.id, a.id, title, a.creationTime ?? new Date());
          if (!silent) await notify({ userId: job.userId, type: 'classroom.announcement', ref: a.id, title: `ঘোষণা · ${shortCourse(name)}`, body: title, link: `/classroom/${encodeURIComponent(c.id)}` });
        }
      }
    }

    const now = new Date();
    await coreDb.update(googleConnections).set({ status: 'active', lastSyncAt: now, ...(full ? { lastFullSyncAt: now } : {}), errorCode: null, errorMessage: null }).where(eq(googleConnections.id, conn.id));
    await coreDb.update(classroomSyncRuns).set({ finishedAt: now, ok: true, changes }).where(eq(classroomSyncRuns.id, run!.id));
    await dropDash(job.userId);
    if (full) await enqueue('classroom.renewRegistrations', { userId: job.userId });
    return { changes };
  } catch (err) {
    const e = await markError(conn, err);
    await coreDb.update(classroomSyncRuns).set({ finishedAt: new Date(), ok: false, error: `${e.kind}: ${e.message}`.slice(0, 300) }).where(eq(classroomSyncRuns.id, run!.id));
    if (e.kind === 'other' && job.trigger !== 'manual') throw e; // let BullMQ retry transient failures
    return { changes, error: e.kind };
  }
}

const shortCourse = (name: string) => name.split(' ').slice(0, 1).join(' ');
const dhaka = (d: Date) => new Intl.DateTimeFormat('bn-BD', { timeZone: 'Asia/Dhaka', weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(d);

async function addFeed(userId: string, kind: string, courseId: string, refId: string, title: string, occurredAt: Date) {
  await coreDb.insert(classroomFeedItems).values({ userId, kind, gcCourseId: courseId, refId, title, occurredAt }).onConflictDoNothing();
}

async function purgeCourses(userId: string, courseIds: string[]) {
  for (const t of [classroomCoursework, classroomSubmissions, classroomMaterials, classroomAnnouncements, classroomFeedItems, classroomPushRegistrations, classroomCourses] as const) {
    await coreDb.delete(t).where(and(eq(t.userId, userId), inArray(t.gcCourseId, courseIds)));
  }
}

defineJob<SyncJob>('classroom.fullSync', (j) => runSync({ ...j, trigger: j.trigger ?? 'connect' }));
defineJob<SyncJob>('classroom.incrementalSync', (j) => runSync(j));

/** Manual refresh — rate limited to once a minute, respects quota backoff. Runs inline so the UI gets fresh data. */
export async function manualSync(userId: string) {
  const conn = await connFor(userId);
  if (!conn) throw notFound('Google Classroom সংযুক্ত নেই।');
  if (conn.status === 'needs_reauth') throw new AppError('CLASSROOM_REAUTH');
  if (conn.status === 'workspace_blocked') throw new AppError('CLASSROOM_WORKSPACE_BLOCKED');
  const now = Date.now();
  if (conn.lastManualSyncAt && now - conn.lastManualSyncAt.getTime() < MANUAL_SYNC_COOLDOWN_MS) {
    throw new AppError('RATE_LIMITED', 'এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন।', { retryAfterSec: Math.ceil((MANUAL_SYNC_COOLDOWN_MS - (now - conn.lastManualSyncAt.getTime())) / 1000) });
  }
  if (conn.nextSyncAllowedAt && conn.nextSyncAllowedAt.getTime() > now) throw new AppError('CLASSROOM_QUOTA', undefined, { retryAfterSec: Math.ceil((conn.nextSyncAllowedAt.getTime() - now) / 1000) });
  await coreDb.update(googleConnections).set({ lastManualSyncAt: new Date() }).where(eq(googleConnections.id, conn.id));
  await runSync({ userId, trigger: 'manual' });
  return getStatus(userId);
}

// ---------------------------------------------------------------- push (Pub/Sub)

/** Registers COURSE_WORK_CHANGES per course; renews registrations expiring within 2 days. */
export async function renewRegistrations(job: { userId?: string }) {
  const g = await getIntegration('google');
  const topic = g.pubsub_topic;
  const conns = job.userId
    ? await coreDb.select().from(googleConnections).where(and(eq(googleConnections.userId, job.userId), eq(googleConnections.status, 'active')))
    : await coreDb.select().from(googleConnections).where(eq(googleConnections.status, 'active'));
  for (const conn of conns) {
    const client = await clientFor(conn);
    if (!client.fake && !topic) continue;
    try {
      const at = await accessToken(conn, client);
      const courses = await coreDb.select({ id: classroomCourses.gcCourseId }).from(classroomCourses).where(eq(classroomCourses.userId, conn.userId));
      const regs = await coreDb.select().from(classroomPushRegistrations).where(eq(classroomPushRegistrations.userId, conn.userId));
      for (const c of courses) {
        const r = regs.find((x) => x.gcCourseId === c.id);
        if (r && r.expiresAt.getTime() > Date.now() + 2 * 86_400_000) continue;
        const reg = await client.registerCourseWorkPush(at, c.id, topic ?? 'fake-topic');
        if (r) await coreDb.update(classroomPushRegistrations).set({ registrationId: reg.registrationId, expiresAt: reg.expiresAt }).where(eq(classroomPushRegistrations.id, r.id));
        else await coreDb.insert(classroomPushRegistrations).values({ userId: conn.userId, feedType: 'COURSE_WORK_CHANGES', gcCourseId: c.id, registrationId: reg.registrationId, expiresAt: reg.expiresAt }).onConflictDoNothing();
      }
    } catch (err) {
      await markError(conn, err);
    }
  }
}
defineJob<{ userId?: string }>('classroom.renewRegistrations', renewRegistrations);

/** Pub/Sub push endpoint: verify token, map registration → user/course, enqueue a targeted incremental sync. */
export async function handlePubsub(token: string | undefined, body: { message: { data?: string; attributes?: Record<string, string> } }) {
  const g = await getIntegration('google');
  const expected = g.pubsub_verification_token;
  if (expected ? token !== expected : isProd) throw new AppError('UNAUTHORIZED');
  await enqueue('classroom.pubsubConsume', body.message);
}

export async function consumePubsub(message: { data?: string; attributes?: Record<string, string> }) {
  let payload: { collection?: string; eventType?: string; resourceId?: { courseId?: string; id?: string }; registrationId?: string } = {};
  try {
    payload = message.data ? JSON.parse(Buffer.from(message.data, 'base64').toString('utf8')) : {};
  } catch {
    return;
  }
  const registrationId = message.attributes?.registrationId ?? payload.registrationId;
  if (!registrationId) return;
  const [reg] = await coreDb.select().from(classroomPushRegistrations).where(eq(classroomPushRegistrations.registrationId, registrationId));
  if (!reg) return;
  await enqueue('classroom.incrementalSync', { userId: reg.userId, trigger: 'push', courseId: payload.resourceId?.courseId ?? reg.gcCourseId ?? undefined }, { jobId: `push-${reg.userId}-${Math.floor(Date.now() / 30_000)}` });
}
defineJob('classroom.pubsubConsume', consumePubsub);

/** Fallback polling: every 15 min for users active in the last 7 days whose last sync is older than 15 min. */
export async function pollActive() {
  const cutoff = new Date(Date.now() - 15 * 60_000);
  const active = new Date(Date.now() - 7 * 86_400_000);
  const rows = await coreDb
    .select({ userId: googleConnections.userId })
    .from(googleConnections)
    .innerJoin(users, eq(users.id, googleConnections.userId))
    .where(and(eq(googleConnections.status, 'active'), or(isNull(googleConnections.lastSyncAt), lt(googleConnections.lastSyncAt, cutoff)), gte(users.lastActiveAt, active), or(isNull(googleConnections.nextSyncAllowedAt), lt(googleConnections.nextSyncAllowedAt, new Date()))))
    .limit(5000);
  for (const r of rows) await enqueue('classroom.incrementalSync', { userId: r.userId, trigger: 'poll' }, { jobId: `poll-${r.userId}-${Math.floor(Date.now() / 900_000)}` });
  return rows.length;
}
defineJob('classroom.poll', pollActive);

// ---------------------------------------------------------------- reminders (every 5 min)

export async function runDeadlineReminders(now = new Date()) {
  const windows = [
    { type: 'classroom.due_24h' as const, from: 23.5, to: 24.5, label: 'আগামীকাল এই সময়ে' },
    { type: 'classroom.due_2h' as const, from: 1.5, to: 2.5, label: '২ ঘণ্টা পর' },
  ];
  let sent = 0;
  for (const w of windows) {
    const rows = await coreDb
      .select({ cw: classroomCoursework, sub: classroomSubmissions, course: classroomCourses })
      .from(classroomCoursework)
      .innerJoin(googleConnections, and(eq(googleConnections.userId, classroomCoursework.userId), eq(googleConnections.status, 'active')))
      .innerJoin(classroomCourses, and(eq(classroomCourses.userId, classroomCoursework.userId), eq(classroomCourses.gcCourseId, classroomCoursework.gcCourseId)))
      .leftJoin(classroomSubmissions, and(eq(classroomSubmissions.userId, classroomCoursework.userId), eq(classroomSubmissions.gcCourseworkId, classroomCoursework.gcCourseworkId)))
      .where(and(gte(classroomCoursework.dueAt, new Date(now.getTime() + w.from * 3_600_000)), lte(classroomCoursework.dueAt, new Date(now.getTime() + w.to * 3_600_000))));
    for (const r of rows) {
      if (r.sub && ['TURNED_IN', 'RETURNED'].includes(r.sub.state)) continue;
      const ok = await notify({
        userId: r.cw.userId,
        type: w.type,
        ref: r.cw.gcCourseworkId,
        title: `${w.type === 'classroom.due_2h' ? 'আর ২ ঘণ্টা' : 'আগামীকাল জমা'} · ${shortCourse(r.course.name)}`,
        body: `${r.cw.title} — জমা ${dhaka(r.cw.dueAt!)}`,
        link: `/classroom/assignments?id=${encodeURIComponent(r.cw.gcCourseworkId)}`,
        email: { template: 'classroom_deadline', data: { title: r.cw.title, courseName: r.course.name, when: dhaka(r.cw.dueAt!), link: r.cw.alternateLink } },
      });
      if (ok) sent++;
    }
  }
  // one overdue nudge, only for items that became overdue in the last 24 h
  const overdue = await coreDb
    .select({ cw: classroomCoursework, sub: classroomSubmissions, course: classroomCourses })
    .from(classroomCoursework)
    .innerJoin(googleConnections, and(eq(googleConnections.userId, classroomCoursework.userId), eq(googleConnections.status, 'active')))
    .innerJoin(classroomCourses, and(eq(classroomCourses.userId, classroomCoursework.userId), eq(classroomCourses.gcCourseId, classroomCoursework.gcCourseId)))
    .leftJoin(classroomSubmissions, and(eq(classroomSubmissions.userId, classroomCoursework.userId), eq(classroomSubmissions.gcCourseworkId, classroomCoursework.gcCourseworkId)))
    .where(and(lt(classroomCoursework.dueAt, now), gt(classroomCoursework.dueAt, new Date(now.getTime() - 86_400_000))));
  for (const r of overdue) {
    if (r.sub && ['TURNED_IN', 'RETURNED'].includes(r.sub.state)) continue;
    if (await notify({ userId: r.cw.userId, type: 'classroom.overdue', ref: r.cw.gcCourseworkId, title: `সময় পেরিয়ে গেছে · ${shortCourse(r.course.name)}`, body: `${r.cw.title} — দেরিতে হলেও Classroom-এ জমা দিন`, link: `/classroom/assignments?id=${encodeURIComponent(r.cw.gcCourseworkId)}` })) sent++;
  }
  return sent;
}
defineJob('reminders.deadlines', () => runDeadlineReminders());

// ---------------------------------------------------------------- disconnect

export async function disconnect(userId: string) {
  const conn = await connFor(userId);
  if (!conn) return;
  try {
    const client = await clientFor(conn);
    const regs = await coreDb.select().from(classroomPushRegistrations).where(eq(classroomPushRegistrations.userId, userId));
    if (regs.length) {
      const at = await accessToken(conn, client).catch(() => null);
      if (at) for (const r of regs) await client.deleteRegistration(at, r.registrationId);
    }
    await client.revoke(decrypt(conn.refreshTokenEnc));
  } catch (err) {
    logger.warn({ err: (err as Error).message }, 'classroom revoke failed (continuing with local delete)');
  }
  for (const t of [classroomCoursework, classroomSubmissions, classroomMaterials, classroomAnnouncements, classroomFeedItems, classroomPushRegistrations, classroomCourses, classroomSyncRuns] as const) {
    await coreDb.delete(t).where(eq(t.userId, userId));
  }
  // Classroom-derived notifications contain course content — delete them too.
  await coreDb.delete(notifications).where(and(eq(notifications.userId, userId), sql`${notifications.type} like 'classroom.%'`));
  await coreDb.delete(googleConnections).where(eq(googleConnections.id, conn.id));
  await dropDash(userId);
}

// ---------------------------------------------------------------- read models

export async function getStatus(userId: string): Promise<ClassroomStatus> {
  const conn = await connFor(userId);
  if (!conn) return { connected: false, status: null, email: null, lastSyncAt: null, lastFullSyncAt: null, nextManualSyncAt: null, errorCode: null, courseCount: 0, isFake: false };
  const [c] = await coreDb.select({ value: count() }).from(classroomCourses).where(eq(classroomCourses.userId, userId));
  const nextManual = conn.lastManualSyncAt ? new Date(conn.lastManualSyncAt.getTime() + MANUAL_SYNC_COOLDOWN_MS) : null;
  return {
    connected: true,
    status: conn.status as ClassroomStatus['status'],
    email: conn.email,
    lastSyncAt: conn.lastSyncAt?.toISOString() ?? null,
    lastFullSyncAt: conn.lastFullSyncAt?.toISOString() ?? null,
    nextManualSyncAt: nextManual && nextManual > new Date() ? nextManual.toISOString() : null,
    errorCode: conn.errorCode,
    courseCount: c?.value ?? 0,
    isFake: conn.isFake,
  };
}

type CourseRow = typeof classroomCourses.$inferSelect;
const mats = (v: unknown) => (Array.isArray(v) ? (v as Material[]) : []);
const n = (v: string | null) => (v === null ? null : Number(v));

async function coursesMap(userId: string) {
  const rows = await coreDb.select().from(classroomCourses).where(eq(classroomCourses.userId, userId)).orderBy(asc(classroomCourses.name));
  return new Map(rows.map((r) => [r.gcCourseId, r]));
}

function toAssignment(cw: typeof classroomCoursework.$inferSelect, sub: typeof classroomSubmissions.$inferSelect | null | undefined, course: CourseRow | undefined, now = new Date()) {
  return {
    id: cw.gcCourseworkId,
    courseId: cw.gcCourseId,
    courseName: course?.name ?? '',
    courseColor: course?.color ?? COLORS[0]!,
    title: cw.title,
    description: cw.description,
    workType: cw.workType,
    dueAt: cw.dueAt?.toISOString() ?? null,
    maxPoints: n(cw.maxPoints),
    status: assignmentStatus(sub ? { state: sub.state, assignedGrade: sub.assignedGrade } : undefined, cw.dueAt, now),
    late: sub?.late ?? false,
    grade: sub ? n(sub.assignedGrade) : null,
    materials: mats(cw.materialsJson),
    alternateLink: sub?.alternateLink ?? cw.alternateLink,
    postedAt: cw.createdAtGc?.toISOString() ?? null,
  };
}

async function assignmentRows(userId: string, extra: SQL[] = []) {
  return coreDb
    .select({ cw: classroomCoursework, sub: classroomSubmissions })
    .from(classroomCoursework)
    .leftJoin(classroomSubmissions, and(eq(classroomSubmissions.userId, classroomCoursework.userId), eq(classroomSubmissions.gcCourseworkId, classroomCoursework.gcCourseworkId)))
    .where(and(eq(classroomCoursework.userId, userId), ...extra))
    .orderBy(sql`${classroomCoursework.dueAt} asc nulls last`, desc(classroomCoursework.createdAtGc));
}

export async function listCourses(userId: string) {
  const courses = await coursesMap(userId);
  const [unread, rows] = await Promise.all([
    coreDb.select({ c: classroomFeedItems.gcCourseId, value: count() }).from(classroomFeedItems).where(and(eq(classroomFeedItems.userId, userId), isNull(classroomFeedItems.readAt))).groupBy(classroomFeedItems.gcCourseId),
    assignmentRows(userId),
  ]);
  const now = new Date();
  return {
    items: [...courses.values()].map((c) => {
      const list = rows.filter((r) => r.cw.gcCourseId === c.gcCourseId).map((r) => toAssignment(r.cw, r.sub, c, now));
      const pending = list.filter((a) => a.status === 'pending');
      return {
        id: c.gcCourseId,
        name: c.name,
        section: c.section,
        room: c.room,
        teacherNames: c.teacherNames,
        color: c.color,
        alternateLink: c.alternateLink,
        unreadCount: unread.find((u) => u.c === c.gcCourseId)?.value ?? 0,
        pendingCount: pending.length,
        nextDueAt: pending.find((a) => a.dueAt)?.dueAt ?? null,
      };
    }),
  };
}

export async function courseStream(userId: string, courseId: string) {
  const all = await listCourses(userId);
  const course = all.items.find((c) => c.id === courseId);
  if (!course) throw notFound('এই ক্লাসটি পাওয়া যায়নি।');
  const courses = await coursesMap(userId);
  const [work, materials, anns] = await Promise.all([
    assignmentRows(userId, [eq(classroomCoursework.gcCourseId, courseId)]),
    coreDb.select().from(classroomMaterials).where(and(eq(classroomMaterials.userId, userId), eq(classroomMaterials.gcCourseId, courseId))),
    coreDb.select().from(classroomAnnouncements).where(and(eq(classroomAnnouncements.userId, userId), eq(classroomAnnouncements.gcCourseId, courseId))),
  ]);
  const items = [
    ...work.map((r) => {
      const a = toAssignment(r.cw, r.sub, courses.get(courseId));
      return { id: a.id, kind: 'assignment' as const, title: a.title, body: a.description, author: null, materials: a.materials, postedAt: a.postedAt, alternateLink: a.alternateLink, assignment: a };
    }),
    ...materials.map((m) => ({ id: m.gcMaterialId, kind: 'material' as const, title: m.title, body: m.description, author: null, materials: mats(m.materialsJson), postedAt: m.createdAtGc?.toISOString() ?? null, alternateLink: m.alternateLink, assignment: null })),
    ...anns.map((a) => ({ id: a.gcAnnouncementId, kind: 'announcement' as const, title: a.text.split('\n')[0]!.slice(0, 120), body: a.text, author: a.authorName, materials: mats(a.materialsJson), postedAt: a.createdAtGc?.toISOString() ?? null, alternateLink: a.alternateLink, assignment: null })),
  ].sort((x, y) => (y.postedAt ?? '').localeCompare(x.postedAt ?? ''));
  return { course, items };
}

export async function listAssignments(userId: string, q: { status: string; courseId?: string; from?: string; to?: string }) {
  const extra: SQL[] = [];
  if (q.courseId) extra.push(eq(classroomCoursework.gcCourseId, q.courseId));
  if (q.from) extra.push(gte(classroomCoursework.dueAt, new Date(q.from)));
  if (q.to) extra.push(lte(classroomCoursework.dueAt, new Date(q.to)));
  const [rows, courses] = await Promise.all([assignmentRows(userId, extra), coursesMap(userId)]);
  const now = new Date();
  let items = rows.map((r) => toAssignment(r.cw, r.sub, courses.get(r.cw.gcCourseId), now));
  if (q.status === 'upcoming') items = items.filter((a) => a.status === 'pending' || a.status === 'missing');
  else if (q.status !== 'all') items = items.filter((a) => a.status === q.status);
  return { items };
}

export async function getAssignment(userId: string, id: string) {
  const [rows, courses] = await Promise.all([assignmentRows(userId, [eq(classroomCoursework.gcCourseworkId, id)]), coursesMap(userId)]);
  const r = rows[0];
  if (!r) throw notFound('অ্যাসাইনমেন্টটি পাওয়া যায়নি।');
  return toAssignment(r.cw, r.sub, courses.get(r.cw.gcCourseId));
}

export async function calendar(userId: string, from: string, to: string) {
  const [rows, courses, exams] = await Promise.all([
    assignmentRows(userId, [isNotNull(classroomCoursework.dueAt), gte(classroomCoursework.dueAt, new Date(from)), lte(classroomCoursework.dueAt, new Date(to))]),
    coursesMap(userId),
    coreDb.select().from(examPlans).where(and(eq(examPlans.studentId, userId), eq(examPlans.active, true), gte(examPlans.examDate, new Date(from)), lte(examPlans.examDate, new Date(to)))),
  ]);
  const items = [
    ...rows.map((r) => {
      const a = toAssignment(r.cw, r.sub, courses.get(r.cw.gcCourseId));
      return { id: a.id, kind: 'assignment' as const, title: a.title, at: a.dueAt!, courseName: a.courseName, color: a.courseColor, status: a.status, link: `/classroom/assignments?id=${encodeURIComponent(a.id)}` };
    }),
    ...exams.map((e) => ({ id: e.id, kind: 'exam' as const, title: `${e.subjectName} ${e.examType === 'final' ? 'Final' : e.examType === 'midterm' ? 'Midterm' : e.examType}`, at: e.examDate.toISOString(), courseName: e.courseCode, color: '#F59E0B', status: null, link: `/exam-mode/${e.id}` })),
  ].sort((a, b) => a.at.localeCompare(b.at));
  return { items };
}

export async function feed(userId: string, q: { cursor?: string; limit: number; courseId?: string; unread?: boolean }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(classroomFeedItems.userId, userId)];
  if (q.courseId) conds.push(eq(classroomFeedItems.gcCourseId, q.courseId));
  if (q.unread) conds.push(isNull(classroomFeedItems.readAt));
  if (cur) conds.push(or(lt(classroomFeedItems.occurredAt, new Date(String(cur[0]))), and(eq(classroomFeedItems.occurredAt, new Date(String(cur[0]))), lt(classroomFeedItems.id, cur[1])))!);
  const rows = await coreDb.select().from(classroomFeedItems).where(and(...conds)).orderBy(desc(classroomFeedItems.occurredAt), desc(classroomFeedItems.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.occurredAt.toISOString(), r.id]);
  return { items: await hydrateFeed(userId, pg.items), nextCursor: pg.nextCursor };
}

/** Batch-load the referenced coursework/material/announcement rows for feed items (no N+1). */
async function hydrateFeed(userId: string, items: (typeof classroomFeedItems.$inferSelect)[]) {
  if (!items.length) return [];
  const courses = await coursesMap(userId);
  const ids = (k: string[]) => items.filter((i) => k.includes(i.kind)).map((i) => i.refId);
  const [cws, subs, mts, ans] = await Promise.all([
    ids(['assignment', 'graded']).length ? coreDb.select().from(classroomCoursework).where(and(eq(classroomCoursework.userId, userId), inArray(classroomCoursework.gcCourseworkId, ids(['assignment', 'graded'])))) : [],
    ids(['graded']).length ? coreDb.select().from(classroomSubmissions).where(and(eq(classroomSubmissions.userId, userId), inArray(classroomSubmissions.gcCourseworkId, ids(['graded'])))) : [],
    ids(['material']).length ? coreDb.select().from(classroomMaterials).where(and(eq(classroomMaterials.userId, userId), inArray(classroomMaterials.gcMaterialId, ids(['material'])))) : [],
    ids(['announcement']).length ? coreDb.select().from(classroomAnnouncements).where(and(eq(classroomAnnouncements.userId, userId), inArray(classroomAnnouncements.gcAnnouncementId, ids(['announcement'])))) : [],
  ]);
  return items.map((i) => {
    const c = courses.get(i.gcCourseId);
    const base = { id: i.id, kind: i.kind as 'assignment' | 'material' | 'announcement' | 'graded', refId: i.refId, courseId: i.gcCourseId, courseName: c?.name ?? '', courseColor: c?.color ?? COLORS[0]!, title: i.title, occurredAt: i.occurredAt.toISOString(), read: !!i.readAt };
    if (i.kind === 'assignment' || i.kind === 'graded') {
      const cw = cws.find((x) => x.gcCourseworkId === i.refId);
      const s = subs.find((x) => x.gcCourseworkId === i.refId);
      return { ...base, body: cw?.description ?? null, materials: mats(cw?.materialsJson), grade: i.kind === 'graded' ? { assigned: s ? n(s.assignedGrade) : null, max: cw ? n(cw.maxPoints) : null } : null, alternateLink: cw?.alternateLink ?? null };
    }
    if (i.kind === 'material') {
      const m = mts.find((x) => x.gcMaterialId === i.refId);
      return { ...base, body: m?.description ?? null, materials: mats(m?.materialsJson), grade: null, alternateLink: m?.alternateLink ?? null };
    }
    const a = ans.find((x) => x.gcAnnouncementId === i.refId);
    return { ...base, body: a?.text ?? null, materials: mats(a?.materialsJson), grade: null, alternateLink: a?.alternateLink ?? null };
  });
}

export async function markFeedRead(userId: string, b: { ids?: string[]; all?: boolean }) {
  const conds: SQL[] = [eq(classroomFeedItems.userId, userId), isNull(classroomFeedItems.readAt)];
  if (!b.all) {
    if (!b.ids?.length) return;
    conds.push(inArray(classroomFeedItems.id, b.ids));
  }
  await coreDb.update(classroomFeedItems).set({ readAt: new Date() }).where(and(...conds));
  await dropDash(userId);
}

/** Dashboard pieces (used by modules/dashboard). */
export async function dueSoonGroups(userId: string, now = new Date()) {
  const weekEnd = new Date(now.getTime() + 7 * 86_400_000);
  const rows = await assignmentRows(userId, [isNotNull(classroomCoursework.dueAt), gte(classroomCoursework.dueAt, new Date(now.getTime() - 7 * 86_400_000)), lte(classroomCoursework.dueAt, weekEnd)]);
  const courses = await coursesMap(userId);
  const list = rows.map((r) => toAssignment(r.cw, r.sub, courses.get(r.cw.gcCourseId), now)).filter((a) => a.status === 'pending' || a.status === 'missing' || (a.status === 'turned_in' && new Date(a.dueAt!) > now));
  const key = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);
  const today = key(now);
  const tomorrow = key(new Date(now.getTime() + 86_400_000));
  const groups: Record<'overdue' | 'today' | 'tomorrow' | 'this_week', typeof list> = { overdue: [], today: [], tomorrow: [], this_week: [] };
  for (const a of list) {
    const due = new Date(a.dueAt!);
    if (a.status === 'missing') groups.overdue.push(a);
    else if (key(due) === today) groups.today.push(a);
    else if (key(due) === tomorrow) groups.tomorrow.push(a);
    else groups.this_week.push(a);
  }
  return { groups: (['overdue', 'today', 'tomorrow', 'this_week'] as const).map((k) => ({ key: k, items: groups[k] })).filter((g) => g.items.length), total: list.length };
}

export async function unreadFeedCount(userId: string) {
  const [r] = await coreDb.select({ value: count() }).from(classroomFeedItems).where(and(eq(classroomFeedItems.userId, userId), isNull(classroomFeedItems.readAt)));
  return r?.value ?? 0;
}

export async function deadlineCountsByDay(userId: string, from: Date, to: Date) {
  const rows = await coreDb
    .select({ day: sql<string>`to_char(${classroomCoursework.dueAt} at time zone 'Asia/Dhaka', 'YYYY-MM-DD')`, value: count() })
    .from(classroomCoursework)
    .where(and(eq(classroomCoursework.userId, userId), gte(classroomCoursework.dueAt, from), lt(classroomCoursework.dueAt, to)))
    .groupBy(sql`1`);
  return new Map(rows.map((r) => [r.day, r.value]));
}

registerIntegrationTest('google', async (c) => {
  if (!c.client_id || !c.client_secret) return { ok: false, message: 'Client id ও secret দিন' };
  // An invalid code returns invalid_grant when the client credentials are valid, invalid_client otherwise.
  const res = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ client_id: c.client_id, client_secret: c.client_secret, grant_type: 'authorization_code', code: 'test', redirect_uri: redirectUri() }) });
  const data = (await res.json().catch(() => ({}))) as { error?: string };
  if (data.error === 'invalid_client' || data.error === 'unauthorized_client') return { ok: false, message: `Google: ${data.error} — client id/secret ঠিক নেই` };
  return { ok: true, message: `Google OAuth client ঠিক আছে। Redirect URI: ${redirectUri()}` };
});

