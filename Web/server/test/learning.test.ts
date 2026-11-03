import { describe, expect, it } from 'vitest';
import { and, eq, gt } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedAll } from '../src/database/seed';
import { contentDb, coreDb } from '../src/database/client';
import { enrollments, notifications, users } from '../src/database/core/schema';
import { courses, lessons } from '../src/database/content/schema';
import { issueSession } from '../src/modules/auth/auth.service';
import { getDashboard } from '../src/modules/dashboard/dashboard.service';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any

async function setup(enroll = true) {
  const app = await getApp();
  await seedAll();
  const [course] = await contentDb.select().from(courses).where(and(eq(courses.status, 'published'), gt(courses.price, 0), gt(courses.lessonCount, 3))).limit(1);
  const student = await createUser({ name: 'নুসরাত জাহান', phone: '+8801712345678' });
  if (enroll) await coreDb.insert(enrollments).values({ studentId: student.user.id, courseId: course!.id, status: 'active' });
  return { app, course: course!, student };
}
const seller = async (sellerId: string) => {
  const [u] = await coreDb.select().from(users).where(eq(users.id, sellerId));
  const s = await issueSession(u!, { platform: 'android' });
  return { authorization: `Bearer ${s.accessToken}` };
};

describe('learning', () => {
  it('outline locks non-preview lessons for guests-of-course; enrolled sees everything', async () => {
    const { app, course, student } = await setup(false);
    const o = j(await app.inject({ url: `/api/v1/learn/${course.id}`, headers: student.auth }));
    const ls = o.sections.flatMap((s: any) => s.chapters.flatMap((c: any) => c.lessons)); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(o.enrolled).toBe(false);
    expect(ls.some((l: { locked: boolean }) => l.locked)).toBe(true);
    expect(ls.filter((l: { isPreview: boolean }) => l.isPreview).every((l: { locked: boolean }) => !l.locked)).toBe(true);
    expect(o.resources).toEqual([]);
    const locked = ls.find((l: { locked: boolean; type: string }) => l.locked)!;
    expect((await app.inject({ url: `/api/v1/lessons/${locked.id}`, headers: student.auth })).statusCode).toBe(403);
    expect((await app.inject({ method: 'POST', url: `/api/v1/lessons/${locked.id}/playback`, headers: student.auth, payload: { deviceId: 'device-aaaa' } })).statusCode).toBe(403);
    await coreDb.insert(enrollments).values({ studentId: student.user.id, courseId: course.id, status: 'active' });
    const o2 = j(await app.inject({ url: `/api/v1/learn/${course.id}`, headers: student.auth }));
    expect(o2.enrolled).toBe(true);
    expect(o2.sections.flatMap((s: any) => s.chapters.flatMap((c: any) => c.lessons)).every((l: { locked: boolean }) => !l.locked)).toBe(true); // eslint-disable-line @typescript-eslint/no-explicit-any
    // expired access
    await coreDb.update(enrollments).set({ accessUntil: new Date(Date.now() - 1000) }).where(eq(enrollments.studentId, student.user.id));
    const r = await app.inject({ url: `/api/v1/lessons/${locked.id}`, headers: student.auth });
    expect(r.statusCode).toBe(403);
    expect(j(r).error.message).toContain('মেয়াদ');
  });

  it('playback: signed URL + watermark, device limit 3, one stream at a time; heartbeat completes at 90%', async () => {
    const { app, course, student } = await setup();
    const [v0] = await contentDb.select().from(lessons).where(and(eq(lessons.courseId, course.id), eq(lessons.type, 'video'))).limit(1);
    const [v] = await contentDb.update(lessons).set({ durationSec: 300 }).where(eq(lessons.id, v0!.id)).returning(); // 9 heartbeats (rate limit 20/min)
    const p1 = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/playback`, headers: student.auth, payload: { deviceId: 'device-1111' } }));
    expect(p1.kind).toBe('hls');
    expect(p1.url).toMatch(/m3u8/);
    expect(p1.watermark).toContain('••5678');
    for (const d of ['device-2222', 'device-3333']) expect((await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/playback`, headers: student.auth, payload: { deviceId: d } })).statusCode).toBe(200);
    const fourth = await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/playback`, headers: student.auth, payload: { deviceId: 'device-4444' } });
    expect(fourth.statusCode).toBe(403);
    const p2 = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/playback`, headers: student.auth, payload: { deviceId: 'device-1111' } }));
    // old stream was taken over → earns nothing
    const stale = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/progress`, headers: student.auth, payload: { positionSec: 30, deltaSec: 15, streamToken: p1.streamToken } }));
    expect(stale.streamActive).toBe(false);
    // watch ~90% via heartbeats (capped at 30 s each)
    const beats = Math.ceil((v!.durationSec * 0.9) / 30);
    let last;
    for (let i = 1; i <= beats; i++) last = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${v!.id}/progress`, headers: student.auth, payload: { positionSec: i * 30, deltaSec: 60, streamToken: p2.streamToken } }));
    expect(last.streamActive).toBe(true);
    expect(last.completed).toBe(true);
    expect(last.progressPercent).toBeGreaterThan(0);
    const lesson = j(await app.inject({ url: `/api/v1/lessons/${v!.id}`, headers: student.auth }));
    expect(lesson.completed).toBe(true);
    expect(lesson.lastPositionSec).toBe(beats * 30);
  });

  it('manual complete, My Courses filters and dashboard continue-learning', async () => {
    const { app, course, student } = await setup();
    const ls = await contentDb.select().from(lessons).where(eq(lessons.courseId, course.id));
    const target = ls.find((l) => l.type !== 'video') ?? ls[1]!;
    const r = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${target.id}/complete`, headers: student.auth, payload: { completed: true } }));
    expect(r.completed).toBe(true);
    const mc = j(await app.inject({ url: '/api/v1/me/courses', headers: student.auth }));
    expect(mc.items).toHaveLength(1);
    expect(mc.items[0]).toMatchObject({ courseId: course.id, completedLessons: 1, resumeLessonId: target.id });
    expect(mc.items[0].progressPercent).toBe(Math.round(100 / mc.items[0].totalLessons));
    expect(j(await app.inject({ url: '/api/v1/me/courses?filter=completed', headers: student.auth })).items).toHaveLength(0);
    expect(j(await app.inject({ url: '/api/v1/me/courses?filter=in_progress', headers: student.auth })).items).toHaveLength(1);
    const d = await getDashboard(student.user.id, { fresh: true });
    expect(d.continueLearning.data?.courseId).toBe(course.id);
    const undo = j(await app.inject({ method: 'POST', url: `/api/v1/lessons/${target.id}/complete`, headers: student.auth, payload: { completed: false } }));
    expect(undo.progressPercent).toBe(0);
    // non-enrolled can't track progress
    const other = await createUser({ name: 'Other Student' });
    expect((await app.inject({ method: 'POST', url: `/api/v1/lessons/${target.id}/complete`, headers: other.auth, payload: { completed: true } })).statusCode).toBe(403);
  });

  it('notes and bookmarks: CRUD, search, ownership', async () => {
    const { app, course, student } = await setup();
    const [l] = await contentDb.select().from(lessons).where(eq(lessons.courseId, course.id)).limit(1);
    const n = await app.inject({ method: 'POST', url: '/api/v1/notes', headers: student.auth, payload: { lessonId: l!.id, timestampSec: 125, body: 'BST delete: inorder successor নিতে হবে' } });
    expect(n.statusCode).toBe(201);
    expect(j(n)).toMatchObject({ lessonTitle: l!.title, courseTitle: course.title, timestampSec: 125 });
    await app.inject({ method: 'POST', url: '/api/v1/notes', headers: student.auth, payload: { lessonId: l!.id, body: 'Heap property মনে রাখো' } });
    expect(j(await app.inject({ url: '/api/v1/notes?q=successor', headers: student.auth })).items).toHaveLength(1);
    expect(j(await app.inject({ url: `/api/v1/notes?courseId=${course.id}`, headers: student.auth })).items).toHaveLength(2);
    const other = await createUser({ name: 'Other Student' });
    expect((await app.inject({ method: 'PATCH', url: `/api/v1/notes/${j(n).id}`, headers: other.auth, payload: { body: 'hack' } })).statusCode).toBe(403);
    expect((await app.inject({ method: 'POST', url: '/api/v1/notes', headers: other.auth, payload: { lessonId: (await contentDb.select().from(lessons).where(and(eq(lessons.courseId, course.id), eq(lessons.isPreview, false))).limit(1))[0]!.id, body: 'x' } })).statusCode).toBe(403);
    expect(j(await app.inject({ method: 'PATCH', url: `/api/v1/notes/${j(n).id}`, headers: student.auth, payload: { body: 'updated' } })).body).toBe('updated');
    expect((await app.inject({ method: 'DELETE', url: `/api/v1/notes/${j(n).id}`, headers: student.auth })).statusCode).toBe(200);
    const b = j(await app.inject({ method: 'POST', url: '/api/v1/bookmarks', headers: student.auth, payload: { lessonId: l!.id, timestampSec: 60 } }));
    expect(j(await app.inject({ url: '/api/v1/bookmarks', headers: student.auth })).items[0].id).toBe(b.id);
    expect((await app.inject({ method: 'DELETE', url: `/api/v1/bookmarks/${b.id}`, headers: other.auth })).statusCode).toBe(403);
  });

  it('Q&A: student asks → teacher notified; teacher answers → student notified; outsiders blocked', async () => {
    const { app, course, student } = await setup();
    const [l] = await contentDb.select().from(lessons).where(eq(lessons.courseId, course.id)).limit(1);
    const q = await app.inject({ method: 'POST', url: `/api/v1/lessons/${l!.id}/questions`, headers: student.auth, payload: { body: 'AVL rotation-এ double rotation কখন লাগে?', timestampSec: 300 } });
    expect(q.statusCode).toBe(201);
    expect(j(q).authorName).toBe('নুসরাত জা.');
    expect((await coreDb.select().from(notifications).where(and(eq(notifications.userId, course.sellerId), eq(notifications.type, 'seller.qa_new')))).length).toBe(1);
    const t = await seller(course.sellerId);
    const inbox = j(await app.inject({ url: '/api/v1/seller/questions', headers: t }));
    expect(inbox.items[0]).toMatchObject({ id: j(q).id, courseTitle: course.title });
    const a = j(await app.inject({ method: 'POST', url: `/api/v1/questions/${j(q).id}/answers`, headers: t, payload: { body: 'LR বা RL কেস হলে — ছবিসহ পরের লেসনে আছে।' } }));
    expect(a.status).toBe('answered');
    expect(a.answers[0].isSeller).toBe(true);
    expect((await coreDb.select().from(notifications).where(and(eq(notifications.userId, student.user.id), eq(notifications.type, 'course.qa_answered')))).length).toBe(1);
    expect(j(await app.inject({ url: '/api/v1/seller/questions?status=open', headers: t })).items).toHaveLength(0);
    const outsider = await createUser({ name: 'Outsider' });
    expect((await app.inject({ url: `/api/v1/lessons/${l!.id}/questions`, headers: outsider.auth })).statusCode).toBe(403);
    expect(j(await app.inject({ url: `/api/v1/lessons/${l!.id}/questions`, headers: student.auth })).items[0].mine).toBe(true);
  });

  it('reviews: need ≥ 20% progress; one per student (edit); rating aggregate; seller replies once', async () => {
    const { app, course, student } = await setup();
    const early = await app.inject({ method: 'PUT', url: `/api/v1/courses/${course.id}/review`, headers: student.auth, payload: { rating: 5, body: 'দারুণ' } });
    expect(early.statusCode).toBe(403);
    await coreDb.update(enrollments).set({ progressPercent: 40 }).where(eq(enrollments.studentId, student.user.id));
    const r = j(await app.inject({ method: 'PUT', url: `/api/v1/courses/${course.id}/review`, headers: student.auth, payload: { rating: 4, body: 'Midterm-এর আগে খুব কাজে লেগেছে।' } }));
    expect(r).toMatchObject({ rating: 4, verified: true });
    const again = j(await app.inject({ method: 'PUT', url: `/api/v1/courses/${course.id}/review`, headers: student.auth, payload: { rating: 5 } }));
    expect(again.id).toBe(r.id);
    const list = j(await app.inject({ url: `/api/v1/courses/${course.id}/reviews` }));
    expect(list.items.find((x: { id: string }) => x.id === r.id).rating).toBe(5);
    const [c] = await contentDb.select().from(courses).where(eq(courses.id, course.id));
    expect(c!.ratingCount).toBe(list.items.length);
    const t = await seller(course.sellerId);
    expect((await app.inject({ method: 'PUT', url: `/api/v1/seller/reviews/${r.id}/reply`, headers: student.auth, payload: { reply: 'হ্যাক' } })).statusCode).toBe(403);
    expect((await app.inject({ method: 'PUT', url: `/api/v1/seller/reviews/${r.id}/reply`, headers: t, payload: { reply: 'ধন্যবাদ, Final-এর জন্যও আসছে।' } })).statusCode).toBe(200);
    expect((await app.inject({ method: 'PUT', url: `/api/v1/seller/reviews/${r.id}/reply`, headers: t, payload: { reply: 'আবার' } })).statusCode).toBe(409);
    const o = j(await app.inject({ url: `/api/v1/learn/${course.id}`, headers: student.auth }));
    expect(o.myReview.rating).toBe(5);
    expect(o.canReview).toBe(true);
  });
});
