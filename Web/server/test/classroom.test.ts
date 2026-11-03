import { afterEach, describe, expect, it, vi } from 'vitest';
import { and, eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { coreDb } from '../src/database/client';
import { classroomCourses, classroomCoursework, classroomFeedItems, googleConnections, notifications, classroomPushRegistrations } from '../src/database/core/schema';
import { FakeClassroomClient, RealClassroomClient, mapDue, mapMaterials } from '../src/services/google/classroom';
import { __setClassroomClientFactory, assignmentStatus, runDeadlineReminders, runSync, pollActive } from '../src/modules/classroom/classroom.service';
import { updateIntegration } from '../src/modules/settings/settings.service';
import { CLASSROOM_SCOPES } from '@shikkhasathi/shared';
import { FakePushProvider } from '../src/services/push';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any
const HOUR = 3_600_000;

let clock = Date.now();
function useClock(anchorOffset = 0) {
  clock = Date.now() + anchorOffset;
  __setClassroomClientFactory((conn) => new FakeClassroomClient(conn?.createdAt ?? new Date(), () => new Date(clock)));
}
afterEach(() => {
  __setClassroomClientFactory(null);
  vi.useRealTimers();
});

async function connect(auth: Record<string, string>) {
  const app = await getApp();
  const r = j(await app.inject({ url: '/api/v1/classroom/connect-url?returnTo=/home', headers: auth }));
  expect(r.fake).toBe(true);
  const u = new URL(r.url);
  expect(u.pathname).toBe('/api/v1/classroom/oauth/callback');
  const cb = await app.inject({ url: `${u.pathname}${u.search}` });
  expect(cb.statusCode).toBe(302);
  return cb.headers.location as string;
}

describe('Classroom connect + full sync', () => {
  it('OAuth (fake) → encrypted tokens → full sync → classes, assignments, feed; no notification burst on first sync', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    expect(await connect(s.auth)).toBe('/home?classroom=connected');

    const [conn] = await coreDb.select().from(googleConnections).where(eq(googleConnections.userId, s.user.id));
    expect(conn!.refreshTokenEnc).not.toContain('fake-refresh');
    expect(conn!.lastFullSyncAt).toBeTruthy();

    const status = j(await app.inject({ url: '/api/v1/classroom/status', headers: s.auth }));
    expect(status).toMatchObject({ connected: true, status: 'active', courseCount: 5, isFake: true });

    const courses = j(await app.inject({ url: '/api/v1/classroom/courses', headers: s.auth }));
    expect(courses.items.map((c: { name: string }) => c.name)).toContain('CSE-2203 Data Structures');
    expect(new Set(courses.items.map((c: { color: string }) => c.color)).size).toBe(5);

    const all = j(await app.inject({ url: '/api/v1/classroom/assignments', headers: s.auth })).items as { id: string; status: string }[];
    const st = Object.fromEntries(all.map((a) => [a.id, a.status]));
    expect(st).toMatchObject({ 'cw-1': 'missing', 'cw-2': 'pending', 'cw-5': 'turned_in', 'cw-6': 'graded' });
    expect(st['cw-10']).toBeUndefined(); // not posted yet

    const feed = j(await app.inject({ url: '/api/v1/classroom/feed?limit=50', headers: s.auth }));
    expect(feed.items.length).toBeGreaterThan(10);
    expect(feed.items.map((f: { kind: string }) => f.kind)).toEqual(expect.arrayContaining(['assignment', 'material', 'announcement', 'graded']));
    expect(await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id))).toHaveLength(0);

    const stream = j(await app.inject({ url: '/api/v1/classroom/courses/gc-cse2203/stream', headers: s.auth }));
    expect(stream.items.map((i: { kind: string }) => i.kind)).toEqual(expect.arrayContaining(['assignment', 'material', 'announcement']));
    expect((await app.inject({ url: '/api/v1/classroom/courses/nope/stream', headers: s.auth })).statusCode).toBe(404);
  });

  it('rejects a tampered OAuth state', async () => {
    const app = await getApp();
    const r = await app.inject({ url: '/api/v1/classroom/oauth/callback?code=fake-x&state=garbage' });
    expect(r.headers.location).toBe('/home?classroom=error');
  });

  it('only requests the minimal read-only scopes', () => {
    const c = new RealClassroomClient({ clientId: 'cid', clientSecret: 's', scopes: CLASSROOM_SCOPES });
    const url = new URL(c.authUrl({ state: 's', codeChallenge: 'c', redirectUri: 'https://shikkhasathi.com/cb' }));
    const scopes = url.searchParams.get('scope')!.split(' ');
    expect(scopes.every((x) => x === 'openid' || x === 'email' || x === 'profile' || x.endsWith('.readonly') || x.endsWith('classroom.push-notifications'))).toBe(true);
    expect(url.searchParams.get('access_type')).toBe('offline');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
  });
});

describe('incremental sync, notifications, reminders', () => {
  it('new coursework/material/announcement after connect → feed + deduped notifications + push', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-03T06:00:00Z')); // 12:00 Dhaka — outside quiet hours
    useClock();
    const app = await getApp();
    const s = await createUser();
    await coreDb.insert((await import('../src/database/core/schema')).pushTokens).values({ userId: s.user.id, platform: 'android', token: 'fcm-token-123456' });
    await connect(s.auth);
    clock += 7 * HOUR; // cw-10 (+20m), mt-5 (+2h), an-4 (+6h) become visible
    await runSync({ userId: s.user.id, trigger: 'poll' });
    const types = (await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id))).map((n) => n.type).sort();
    expect(types).toEqual(['classroom.announcement', 'classroom.assignment_new', 'classroom.material_new']);
    await runSync({ userId: s.user.id, trigger: 'poll' });
    expect(await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id))).toHaveLength(3);
    expect(FakePushProvider.sent.length).toBeGreaterThanOrEqual(1);

    const ur = await app.inject({ url: '/api/v1/notifications/unread-count', headers: s.auth });
    const unread = j(ur);
    expect(unread.count).toBe(3);
    await app.inject({ method: 'POST', url: '/api/v1/notifications/read', headers: s.auth, payload: { all: true } });
    expect(j(await app.inject({ url: '/api/v1/notifications/unread-count', headers: s.auth })).count).toBe(0);
  });

  it('24 h and 2 h reminders skip turned-in work; overdue nudge once', async () => {
    useClock();
    const s = await createUser();
    await connect(s.auth);
    const [conn] = await coreDb.select().from(googleConnections).where(eq(googleConnections.userId, s.user.id));
    const anchor = conn!.createdAt.getTime();
    // cw-2 due anchor+9h → 2 h window at anchor+7h
    expect(await runDeadlineReminders(new Date(anchor + 7 * HOUR))).toBeGreaterThanOrEqual(1);
    expect(await runDeadlineReminders(new Date(anchor + 7 * HOUR))).toBe(0); // deduped
    const rows = await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id));
    expect(rows.some((r) => r.type === 'classroom.due_2h' && r.body.includes('BST'))).toBe(true);
    await runDeadlineReminders(new Date(anchor + 10 * HOUR)); // cw-2 now 1 h overdue
    await runDeadlineReminders(new Date(anchor + 11 * HOUR));
    const nudges = (await coreDb.select().from(notifications).where(and(eq(notifications.userId, s.user.id), eq(notifications.type, 'classroom.overdue')))).filter((r) => r.body.includes('BST'));
    expect(nudges).toHaveLength(1);
    // cw-5 (turned in) due anchor+5d → no 24 h reminder
    await runDeadlineReminders(new Date(anchor + 4 * 24 * HOUR));
    const after = await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id));
    expect(after.some((r) => r.body.includes('Essay'))).toBe(false);
  });

  it('push can be switched off per type', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    await app.inject({ method: 'PUT', url: '/api/v1/me/notification-preferences', headers: s.auth, payload: { items: [{ type: 'classroom.announcement', channel: 'push', enabled: false }, { type: 'classroom.announcement', channel: 'in_app', enabled: false }] } });
    await connect(s.auth);
    clock += 7 * HOUR;
    await runSync({ userId: s.user.id, trigger: 'poll' });
    const ann = (await coreDb.select().from(notifications).where(and(eq(notifications.userId, s.user.id), eq(notifications.type, 'classroom.announcement'))))[0];
    expect(ann!.readAt).toBeTruthy(); // in-app off → stored as read (kept for dedupe), never shown as unread
    const prefs = j(await app.inject({ url: '/api/v1/me/notification-preferences', headers: s.auth }));
    expect(prefs.items.find((p: { type: string; channel: string }) => p.type === 'classroom.announcement' && p.channel === 'push').enabled).toBe(false);
  });

  it('manual sync is rate limited to 1/min', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    await connect(s.auth);
    expect((await app.inject({ method: 'POST', url: '/api/v1/classroom/sync', headers: s.auth })).statusCode).toBe(200);
    const second = await app.inject({ method: 'POST', url: '/api/v1/classroom/sync', headers: s.auth });
    expect(second.statusCode).toBe(429);
    expect(j(second).error.details.retryAfterSec).toBeGreaterThan(0);
  });

  it('polls only users active in the last 7 days', async () => {
    useClock();
    const a = await createUser();
    await connect(a.auth);
    await coreDb.update(googleConnections).set({ lastSyncAt: new Date(Date.now() - HOUR) });
    expect(await pollActive()).toBe(1);
    await coreDb.update((await import('../src/database/core/schema')).users).set({ lastActiveAt: new Date(Date.now() - 8 * 24 * HOUR) });
    expect(await pollActive()).toBe(0);
  });
});

describe('errors, push, disconnect', () => {
  it('revoked refresh token → needs_reauth, manual sync returns CLASSROOM_REAUTH', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    await connect(s.auth);
    const { encrypt } = await import('../src/common/crypto');
    await coreDb.update(googleConnections).set({ refreshTokenEnc: encrypt('fake-revoked'), accessTokenExpiresAt: new Date(0) }).where(eq(googleConnections.userId, s.user.id));
    await runSync({ userId: s.user.id, trigger: 'poll' });
    expect(j(await app.inject({ url: '/api/v1/classroom/status', headers: s.auth })).status).toBe('needs_reauth');
    const r = await app.inject({ method: 'POST', url: '/api/v1/classroom/sync', headers: s.auth });
    expect(j(r).error.code).toBe('CLASSROOM_REAUTH');
    expect(j(r).error.message).toContain('আবার সংযুক্ত');
  });

  it('Pub/Sub push: verifies token, maps registration → incremental sync', async () => {
    useClock();
    const app = await getApp();
    const admin = await createUser({ roles: ['admin'] });
    await updateIntegration('google', { pubsub_verification_token: 'secret-push-token' }, admin.user.id);
    const s = await createUser();
    await connect(s.auth);
    const [reg] = await coreDb.select().from(classroomPushRegistrations).where(eq(classroomPushRegistrations.gcCourseId, 'gc-cse2203'));
    expect(reg).toBeTruthy();
    const data = Buffer.from(JSON.stringify({ collection: 'courses.courseWork', eventType: 'CREATED', resourceId: { courseId: 'gc-cse2203', id: 'cw-10' } })).toString('base64');
    const bad = await app.inject({ method: 'POST', url: '/api/v1/webhooks/google-pubsub?token=wrong', payload: { message: { data, attributes: { registrationId: reg!.registrationId } } } });
    expect(bad.statusCode).toBe(401);
    clock += HOUR;
    const ok = await app.inject({ method: 'POST', url: '/api/v1/webhooks/google-pubsub?token=secret-push-token', payload: { message: { data, attributes: { registrationId: reg!.registrationId } } } });
    expect(ok.statusCode).toBe(200);
    const cw10 = await coreDb.select().from(classroomCoursework).where(and(eq(classroomCoursework.userId, s.user.id), eq(classroomCoursework.gcCourseworkId, 'cw-10')));
    expect(cw10).toHaveLength(1);
  });

  it('disconnect deletes every cached row and Classroom notifications', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    await connect(s.auth);
    clock += 7 * HOUR;
    await runSync({ userId: s.user.id, trigger: 'poll' });
    expect((await app.inject({ method: 'DELETE', url: '/api/v1/classroom/connection', headers: s.auth })).statusCode).toBe(200);
    for (const t of [classroomCourses, classroomCoursework, classroomFeedItems, googleConnections]) expect(await coreDb.select().from(t).where(eq(t.userId, s.user.id))).toHaveLength(0);
    expect(await coreDb.select().from(notifications).where(eq(notifications.userId, s.user.id))).toHaveLength(0);
    expect(j(await app.inject({ url: '/api/v1/classroom/status', headers: s.auth })).connected).toBe(false);
  });
});

describe('dashboard aggregate', () => {
  it('not connected: Classroom sections null, other widgets still render', async () => {
    const app = await getApp();
    const s = await createUser({ name: 'সাদমান' });
    const d = j(await app.inject({ url: '/api/v1/dashboard', headers: s.auth }));
    expect(d.header).toMatchObject({ name: 'সাদমান', classroom: { connected: false } });
    expect(d.dueSoon).toEqual({ data: null, error: null });
    expect(d.week.data).toHaveLength(7);
  });
  it('connected: due-soon groups (overdue/today/…), feed with unread count, exam countdown', async () => {
    useClock();
    const app = await getApp();
    const s = await createUser();
    await app.inject({ method: 'POST', url: '/api/v1/me/onboarding', headers: s.auth, payload: { nextExam: { subjectName: 'Data Structures', courseCode: 'CSE-2203', examType: 'midterm', examDate: new Date(Date.now() + 5 * 24 * HOUR).toISOString() } } });
    await connect(s.auth);
    const d = j(await app.inject({ url: '/api/v1/dashboard?fresh=1', headers: s.auth }));
    expect(d.dueSoon.data.groups[0].key).toBe('overdue');
    expect(d.dueSoon.data.total).toBeGreaterThanOrEqual(4);
    expect(d.feed.data.unread).toBeGreaterThan(5);
    expect(d.exam.data).toMatchObject({ courseCode: 'CSE-2203', daysLeft: 5 });
    expect(d.week.data.reduce((a: number, w: { deadlines: number }) => a + w.deadlines, 0)).toBeGreaterThan(0);
  });
});

describe('Google REST mapping', () => {
  it('maps due dates (UTC) and materials', () => {
    expect(mapDue({ year: 2026, month: 11, day: 5 }, { hours: 17, minutes: 59 })!.toISOString()).toBe('2026-11-05T17:59:00.000Z');
    expect(mapDue({ year: 2026, month: 11, day: 5 }, undefined)!.toISOString()).toBe('2026-11-05T23:59:00.000Z');
    expect(mapDue(undefined, undefined)).toBeNull();
    const m = mapMaterials([{ driveFile: { driveFile: { title: 'a.pdf', alternateLink: 'https://d' } } }, { link: { url: 'https://x', title: 'X' } }, { youtubeVideo: { title: 'Y', alternateLink: 'https://y' } }, { form: { title: 'F', formUrl: 'https://f' } }, {}]);
    expect(m.map((x) => x.kind)).toEqual(['drive', 'link', 'youtube', 'form']);
  });
  it('classifies API errors (quota, workspace, reauth) and pages through results', async () => {
    const mk = (status: number, body: unknown) => new Response(JSON.stringify(body), { status });
    const f = vi
      .fn()
      .mockResolvedValueOnce(mk(200, { courses: [{ id: '1', name: 'A' }], nextPageToken: 'p2' }))
      .mockResolvedValueOnce(mk(200, { courses: [{ id: '2', name: 'B' }] }))
      .mockResolvedValueOnce(mk(429, { error: { message: 'Quota exceeded' } }))
      .mockResolvedValueOnce(mk(403, { error: { message: '@ClassroomApiDisabled The user is not permitted to access the Classroom API.' } }))
      .mockResolvedValueOnce(mk(401, { error: { message: 'Invalid Credentials' } }));
    const c = new RealClassroomClient({ clientId: 'a', clientSecret: 'b', scopes: CLASSROOM_SCOPES }, f as unknown as typeof fetch);
    expect((await c.listCourses('tok')).map((x) => x.id)).toEqual(['1', '2']);
    await expect(c.listCourses('tok')).rejects.toMatchObject({ kind: 'quota' });
    await expect(c.listCourses('tok')).rejects.toMatchObject({ kind: 'workspace' });
    await expect(c.listCourses('tok')).rejects.toMatchObject({ kind: 'reauth' });
  });
  it('derives student-facing status', () => {
    const now = new Date('2026-11-03T12:00:00Z');
    expect(assignmentStatus(undefined, new Date('2026-11-02T00:00:00Z'), now)).toBe('missing');
    expect(assignmentStatus({ state: 'NEW', assignedGrade: null }, new Date('2026-11-04T00:00:00Z'), now)).toBe('pending');
    expect(assignmentStatus({ state: 'TURNED_IN', assignedGrade: null }, new Date('2026-11-02T00:00:00Z'), now)).toBe('turned_in');
    expect(assignmentStatus({ state: 'RETURNED', assignedGrade: '18' }, null, now)).toBe('graded');
  });
});
