import { describe, expect, it } from 'vitest';
import { and, eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedAll } from '../src/database/seed';
import { contentDb, coreDb } from '../src/database/client';
import { enrollments, examPlans, mockAttempts, notifications, users } from '../src/database/core/schema';
import { lessons, mockOptions, mockQuestions, mockTests } from '../src/database/content/schema';
import { issueSession } from '../src/modules/auth/auth.service';
import { dailyPlanNudges } from '../src/modules/exam/exam.service';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any
const inDays = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString();

async function setup() {
  const app = await getApp();
  const seeded = await seedAll();
  const ds = seeded.courses['data-structures-midterm-crash-cse-2203']!;
  const student = await createUser({ name: 'তানভীর আহমেদ' });
  await coreDb.insert(enrollments).values({ studentId: student.user.id, courseId: ds, status: 'active' });
  return { app, ds, student, seeded };
}

describe('exam mode', () => {
  it('creates a plan only with owned courses; detail has countdown, daily plan, syllabus, resources, readiness', async () => {
    const { app, ds, student, seeded } = await setup();
    const algo = seeded.courses['algorithm-analysis-design-cse-2201']!;
    expect((await app.inject({ method: 'POST', url: '/api/v1/exam-plans', headers: student.auth, payload: { subjectName: 'Algorithms', examType: 'midterm', examDate: inDays(5), courseIds: [algo] } })).statusCode).toBe(403);
    expect((await app.inject({ method: 'POST', url: '/api/v1/exam-plans', headers: student.auth, payload: { subjectName: 'Past', examType: 'midterm', examDate: inDays(-3), courseIds: [] } })).statusCode).toBe(400);
    const c = await app.inject({ method: 'POST', url: '/api/v1/exam-plans', headers: student.auth, payload: { subjectName: 'Data Structures', courseCode: 'CSE-2203', examType: 'midterm', examDate: inDays(6), courseIds: [ds] } });
    expect(c.statusCode).toBe(201);
    const p = j(c);
    expect(p.daysLeft).toBeGreaterThanOrEqual(5);
    const d = j(await app.inject({ url: `/api/v1/exam-plans/${p.id}`, headers: student.auth }));
    expect(d.today.items.length).toBeGreaterThan(0);
    expect(d.today.items[0].kind).toBe('lesson');
    expect(d.today.items[0].isImportant).toBe(true); // ⭐ first
    expect(d.days.at(-1).isRevision).toBe(true);
    expect(d.days.at(-1).items.some((i: { kind: string }) => i.kind === 'mock')).toBe(true);
    expect(d.syllabus.length).toBeGreaterThan(0);
    expect(d.syllabus.every((s: { status: string }) => s.status === 'todo')).toBe(true);
    expect(d.mockTests.length).toBe(2);
    expect(d.flashcards).toEqual({ total: 12, known: 0 });
    expect(d.previousQuestions.length).toBeGreaterThan(0);
    expect(d.revisionUnlocked).toBe(false);
    expect(d.readiness).toBe(0);
    // other students can't see it
    const other = await createUser({ name: 'Other' });
    expect((await app.inject({ url: `/api/v1/exam-plans/${p.id}`, headers: other.auth })).statusCode).toBe(403);
    // dashboard shows the countdown
    const dash = j(await app.inject({ url: '/api/v1/dashboard?fresh=1', headers: student.auth }));
    expect(dash.exam.data.planId).toBe(p.id);
  });

  it('readiness rises with lessons, flashcards and mock score; syllabus status moves', async () => {
    const { app, ds, student } = await setup();
    const p = j(await app.inject({ method: 'POST', url: '/api/v1/exam-plans', headers: student.auth, payload: { subjectName: 'DS', examType: 'midterm', examDate: inDays(4), courseIds: [ds] } }));
    const d0 = j(await app.inject({ url: `/api/v1/exam-plans/${p.id}`, headers: student.auth }));
    const first = d0.today.items[0];
    await app.inject({ method: 'POST', url: `/api/v1/lessons/${first.lessonId}/complete`, headers: student.auth, payload: { completed: true } });
    const cards = j(await app.inject({ url: `/api/v1/exam-plans/${p.id}/flashcards`, headers: student.auth })).items;
    expect(cards).toHaveLength(12);
    for (const c of cards.slice(0, 6)) await app.inject({ method: 'POST', url: `/api/v1/flashcards/${c.id}/review`, headers: student.auth, payload: { known: true } });
    await app.inject({ method: 'POST', url: `/api/v1/flashcards/${cards[6].id}/review`, headers: student.auth, payload: { known: false } });
    expect(j(await app.inject({ url: `/api/v1/exam-plans/${p.id}/flashcards`, headers: student.auth })).items[0].known).toBe(false); // not-yet-known first
    const d1 = j(await app.inject({ url: `/api/v1/exam-plans/${p.id}`, headers: student.auth }));
    expect(d1.today.items[0]).toMatchObject({ lessonId: first.lessonId, done: true }); // stays in today's list, ticked
    expect(d1.flashcards.known).toBe(6);
    expect(d1.readinessBreakdown.flashcards).toBe(50);
    expect(d1.readiness).toBeGreaterThan(0);
    expect(d1.syllabus.some((s: { status: string }) => s.status !== 'todo')).toBe(true);
    const [row] = await coreDb.select().from(examPlans).where(eq(examPlans.id, p.id));
    expect(row!.readiness).toBe(d1.readiness);
  });

  it('mock test: shuffled attempt, resume, autosave, submit → score + chapter breakdown + review; retry is a new attempt', async () => {
    const { app, ds, student } = await setup();
    const list = j(await app.inject({ url: '/api/v1/mock-tests', headers: student.auth })).items;
    const t = list.find((x: { courseId: string; questionCount: number }) => x.courseId === ds && x.questionCount === 12);
    expect(t).toBeTruthy();
    const a = await app.inject({ method: 'POST', url: `/api/v1/mock-tests/${t.id}/attempts`, headers: student.auth });
    expect(a.statusCode).toBe(201);
    const att = j(a);
    expect(att.questions).toHaveLength(12);
    expect(JSON.stringify(att)).not.toContain('isCorrect');
    // resume returns the same attempt
    expect(j(await app.inject({ method: 'POST', url: `/api/v1/mock-tests/${t.id}/attempts`, headers: student.auth })).id).toBe(att.id);
    const correct = await contentDb.select({ id: mockOptions.id, q: mockOptions.questionId }).from(mockOptions).innerJoin(mockQuestions, eq(mockQuestions.id, mockOptions.questionId)).where(and(eq(mockQuestions.testId, t.id), eq(mockOptions.isCorrect, true)));
    // answer 8 correctly, 2 wrong, 2 skipped; flag one
    for (const [i, q] of att.questions.entries()) {
      if (i >= 10) continue;
      const right = correct.find((c) => c.q === q.id)!.id;
      const wrong = q.options.find((o: { id: string }) => o.id !== right).id;
      const r = await app.inject({ method: 'PUT', url: `/api/v1/mock-attempts/${att.id}/answers`, headers: student.auth, payload: { questionId: q.id, optionId: i < 8 ? right : wrong, flagged: i === 3 } });
      expect(r.statusCode).toBe(200);
    }
    expect((await app.inject({ method: 'PUT', url: `/api/v1/mock-attempts/${att.id}/answers`, headers: student.auth, payload: { questionId: att.questions[0].id, optionId: att.questions[1].options[0].id } })).statusCode).toBe(400);
    const resumed = j(await app.inject({ url: `/api/v1/mock-attempts/${att.id}`, headers: student.auth }));
    expect(resumed.answers.filter((x: { flagged: boolean }) => x.flagged)).toHaveLength(1);
    const other = await createUser({ name: 'Other' });
    expect((await app.inject({ url: `/api/v1/mock-attempts/${att.id}`, headers: other.auth })).statusCode).toBe(403);
    const res = j(await app.inject({ method: 'POST', url: `/api/v1/mock-attempts/${att.id}/submit`, headers: student.auth }));
    expect(res).toMatchObject({ correct: 8, wrong: 2, skipped: 2, total: 12, score: 67, autoSubmitted: false, attempts: 1, bestScore: 67 });
    expect(res.chapters.reduce((s: number, c: { total: number }) => s + c.total, 0)).toBe(12);
    expect(res.review[0].correctOptionId).toBeTruthy();
    expect((await app.inject({ method: 'PUT', url: `/api/v1/mock-attempts/${att.id}/answers`, headers: student.auth, payload: { questionId: att.questions[11].id, optionId: null } })).statusCode).toBe(409);
    const again = j(await app.inject({ method: 'POST', url: `/api/v1/mock-tests/${t.id}/attempts`, headers: student.auth }));
    expect(again.id).not.toBe(att.id);
    expect(again.answers).toHaveLength(0);
  });

  it('expired attempt auto-submits on resume; non-buyers cannot start', async () => {
    const { app, ds, student } = await setup();
    const [t] = await contentDb.select().from(mockTests).where(eq(mockTests.courseId, ds)).limit(1);
    const att = j(await app.inject({ method: 'POST', url: `/api/v1/mock-tests/${t!.id}/attempts`, headers: student.auth }));
    await coreDb.update(mockAttempts).set({ deadlineAt: new Date(Date.now() - 60_000) }).where(eq(mockAttempts.id, att.id));
    const r = j(await app.inject({ url: `/api/v1/mock-attempts/${att.id}`, headers: student.auth }));
    expect(r.submitted).toBe(true);
    const res = j(await app.inject({ url: `/api/v1/mock-attempts/${att.id}/result`, headers: student.auth }));
    expect(res).toMatchObject({ autoSubmitted: true, skipped: res.total, score: 0 });
    const outsider = await createUser({ name: 'Outsider' });
    expect((await app.inject({ method: 'POST', url: `/api/v1/mock-tests/${t!.id}/attempts`, headers: outsider.auth })).statusCode).toBe(403);
  });

  it('daily plan nudge: one notification per plan per day', async () => {
    const { app, ds, student } = await setup();
    await app.inject({ method: 'POST', url: '/api/v1/exam-plans', headers: student.auth, payload: { subjectName: 'DS', examType: 'midterm', examDate: inDays(3), courseIds: [ds] } });
    await dailyPlanNudges();
    await dailyPlanNudges();
    const ns = await coreDb.select().from(notifications).where(and(eq(notifications.userId, student.user.id), eq(notifications.type, 'exam.daily_plan')));
    expect(ns).toHaveLength(1);
    expect(ns[0]!.title).toContain('আজকের প্ল্যান');
  });

  it('seller authoring: exactly one correct option, replace, flashcards, ownership', async () => {
    const { app, ds, student } = await setup();
    const [c] = await contentDb.select({ sellerId: lessons.courseId }).from(lessons).where(eq(lessons.courseId, ds)).limit(1);
    expect(c).toBeTruthy();
    const { courses } = await import('../src/database/content/schema');
    const [course] = await contentDb.select().from(courses).where(eq(courses.id, ds));
    const [u] = await coreDb.select().from(users).where(eq(users.id, course!.sellerId));
    const t = { authorization: `Bearer ${(await issueSession(u!, { platform: 'android' })).accessToken}` };
    const bad = await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${ds}/mock-tests`, headers: t, payload: { title: 'Bad', questions: [{ body: 'Q1?', options: [{ body: 'a', isCorrect: true }, { body: 'b', isCorrect: true }] }] } });
    expect(bad.statusCode).toBe(400);
    const ok = await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${ds}/mock-tests`, headers: t, payload: { title: 'Quiz 2', durationSec: 600, questions: [{ body: 'Stack কোন নীতিতে চলে?', chapterTag: 'Stack', options: [{ body: 'LIFO', isCorrect: true }, { body: 'FIFO', isCorrect: false }] }] } });
    expect(ok.statusCode).toBe(201);
    const up = j(await app.inject({ method: 'PUT', url: `/api/v1/seller/mock-tests/${j(ok).id}`, headers: t, payload: { title: 'Quiz 2 (updated)', durationSec: 900, questions: [{ body: 'Queue?', options: [{ body: 'FIFO', isCorrect: true }, { body: 'LIFO', isCorrect: false }] }, { body: 'Heap?', options: [{ body: 'tree', isCorrect: true }, { body: 'list', isCorrect: false }] }] } }));
    expect(up.questions).toHaveLength(2);
    expect(j(await app.inject({ url: `/api/v1/seller/courses/${ds}/mock-tests`, headers: t })).items).toHaveLength(3);
    expect((await app.inject({ url: `/api/v1/seller/courses/${ds}/mock-tests`, headers: student.auth })).statusCode).toBe(403);
    const fc = j(await app.inject({ method: 'PUT', url: `/api/v1/seller/courses/${ds}/flashcards`, headers: t, payload: { cards: [{ front: 'LIFO?', back: 'Stack' }] } }));
    expect(fc.items).toHaveLength(1);
    expect((await app.inject({ method: 'DELETE', url: `/api/v1/seller/mock-tests/${j(ok).id}`, headers: t })).statusCode).toBe(200);
  });
});
