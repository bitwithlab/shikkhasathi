import { createHash } from 'node:crypto';
import { and, asc, count, desc, eq, gte, inArray, isNotNull, sql } from 'drizzle-orm';
import type { Attempt, AttemptResult, ExamPlanDetail, ExamPlanSummary, MockSummary, PlanDay } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { bookmarks, enrollments, examPlans, flashcardReviews, lessonProgress, mockAnswers, mockAttempts, notes } from '../../database/core/schema';
import { courseChapters, courseSections, courses, documents, flashcards, lessons, mockOptions, mockQuestions, mockTests } from '../../database/content/schema';
import { badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { defineJob } from '../../common/jobs';
import { audit } from '../../common/audit';
import { notify } from '../notifications/notifications.service';
import { invalidateDashboard } from '../dashboard/dashboard.service';
import { daysBetween, effectiveMinutes, generatePlan, readiness, type GenDay, type PlanLesson } from './planner';

type PlanRow = typeof examPlans.$inferSelect;
const GRACE_MS = 30_000;
export const dhakaDay = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);
const dhakaStart = (ymd: string) => new Date(`${ymd}T00:00:00+06:00`);

const summary = (p: PlanRow, now = new Date()): ExamPlanSummary => ({
  id: p.id,
  subjectName: p.subjectName,
  courseCode: p.courseCode,
  examType: p.examType,
  examDate: p.examDate.toISOString(),
  daysLeft: Math.max(0, daysBetween(dhakaDay(now), dhakaDay(p.examDate))),
  readiness: p.readiness,
  courseIds: p.courseIds,
  active: p.active,
  past: p.examDate.getTime() < now.getTime() - 12 * 3_600_000,
});

async function activeCourseIds(userId: string, ids: string[]) {
  if (!ids.length) return [];
  const rows = await coreDb.select({ courseId: enrollments.courseId, accessUntil: enrollments.accessUntil }).from(enrollments).where(and(eq(enrollments.studentId, userId), inArray(enrollments.courseId, ids), eq(enrollments.status, 'active')));
  return rows.filter((r) => !r.accessUntil || r.accessUntil > new Date()).map((r) => r.courseId);
}

async function ownPlan(userId: string, id: string) {
  const [p] = await coreDb.select().from(examPlans).where(eq(examPlans.id, id));
  if (!p) throw notFound('পরীক্ষার প্ল্যানটি পাওয়া যায়নি।');
  if (p.studentId !== userId) throw forbidden();
  return p;
}

// ---------------------------------------------------------------- CRUD

export async function listPlans(userId: string) {
  const rows = await coreDb.select().from(examPlans).where(eq(examPlans.studentId, userId)).orderBy(asc(examPlans.examDate));
  const now = new Date();
  const items = rows.map((r) => summary(r, now));
  // upcoming active first (nearest), then past/archived (most recent)
  items.sort((a, b) => Number(a.past || !a.active) - Number(b.past || !b.active) || (a.past ? Date.parse(b.examDate) - Date.parse(a.examDate) : Date.parse(a.examDate) - Date.parse(b.examDate)));
  return { items };
}

export async function createPlan(userId: string, b: { subjectName: string; subjectMasterId?: string | null; courseCode?: string | null; examType: string; examDate: string; courseIds: string[] }) {
  const date = new Date(b.examDate);
  if (date.getTime() < Date.now() - 3_600_000) throw badRequest('পরীক্ষার তারিখ আজ বা সামনের কোনো দিন দিন।');
  if (date.getTime() > Date.now() + 365 * 86_400_000) throw badRequest('এক বছরের মধ্যের তারিখ দিন।');
  const ids = [...new Set(b.courseIds)];
  const ok = await activeCourseIds(userId, ids);
  if (ok.length !== ids.length) throw forbidden('শুধু আপনার কেনা কোর্স যুক্ত করা যায়।');
  const [n] = await coreDb.select({ n: count() }).from(examPlans).where(and(eq(examPlans.studentId, userId), eq(examPlans.active, true), gte(examPlans.examDate, new Date())));
  if ((n?.n ?? 0) >= 12) throw conflict('একসঙ্গে সর্বোচ্চ ১২টি পরীক্ষার প্ল্যান রাখা যায়।');
  const [p] = await coreDb.insert(examPlans).values({ studentId: userId, subjectName: b.subjectName, subjectMasterId: b.subjectMasterId ?? null, courseCode: b.courseCode ?? null, examType: b.examType, examDate: date, courseIds: ids }).returning();
  await refreshReadiness(p!);
  await invalidateDashboard(userId);
  return summary((await coreDb.select().from(examPlans).where(eq(examPlans.id, p!.id)))[0]!);
}

export async function updatePlan(userId: string, id: string, b: { subjectName?: string; courseCode?: string | null; examType?: string; examDate?: string; courseIds?: string[]; active?: boolean; subjectMasterId?: string | null }) {
  await ownPlan(userId, id);
  const set: Partial<typeof examPlans.$inferInsert> = { updatedAt: new Date() };
  if (b.subjectName !== undefined) set.subjectName = b.subjectName;
  if (b.courseCode !== undefined) set.courseCode = b.courseCode;
  if (b.subjectMasterId !== undefined) set.subjectMasterId = b.subjectMasterId;
  if (b.examType !== undefined) set.examType = b.examType;
  if (b.active !== undefined) set.active = b.active;
  if (b.examDate !== undefined) set.examDate = new Date(b.examDate);
  if (b.courseIds !== undefined) {
    const ids = [...new Set(b.courseIds)];
    if ((await activeCourseIds(userId, ids)).length !== ids.length) throw forbidden('শুধু আপনার কেনা কোর্স যুক্ত করা যায়।');
    set.courseIds = ids;
  }
  const [p] = await coreDb.update(examPlans).set(set).where(eq(examPlans.id, id)).returning();
  await refreshReadiness(p!);
  await invalidateDashboard(userId);
  return summary((await coreDb.select().from(examPlans).where(eq(examPlans.id, id)))[0]!);
}

export async function deletePlan(userId: string, id: string) {
  await ownPlan(userId, id);
  await coreDb.delete(examPlans).where(eq(examPlans.id, id));
  await invalidateDashboard(userId);
}

// ---------------------------------------------------------------- plan detail

async function curriculum(courseIds: string[]) {
  if (!courseIds.length) return [];
  return contentDb
    .select({ id: lessons.id, courseId: lessons.courseId, title: lessons.title, type: lessons.type, durationSec: lessons.durationSec, isImportant: lessons.isImportant, chapterId: courseChapters.id, chapterTitle: courseChapters.title, sectionTitle: courseSections.title, sp: courseSections.position, cp: courseChapters.position, lp: lessons.position })
    .from(lessons)
    .innerJoin(courseChapters, eq(courseChapters.id, lessons.chapterId))
    .innerJoin(courseSections, eq(courseSections.id, courseChapters.sectionId))
    .where(and(inArray(lessons.courseId, courseIds), inArray(lessons.type, ['video', 'pdf', 'text'])))
    .orderBy(asc(courseSections.position), asc(courseChapters.position), asc(lessons.position));
}

async function mockSummaries(userId: string, courseIds: string[]): Promise<MockSummary[]> {
  if (!courseIds.length) return [];
  const tests = await contentDb.select().from(mockTests).where(inArray(mockTests.courseId, courseIds)).orderBy(asc(mockTests.createdAt));
  if (!tests.length) return [];
  const [qc, cs, atts] = await Promise.all([
    contentDb.select({ testId: mockQuestions.testId, n: sql<number>`count(*)::int` }).from(mockQuestions).where(inArray(mockQuestions.testId, tests.map((t) => t.id))).groupBy(mockQuestions.testId),
    contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(inArray(courses.id, courseIds)),
    coreDb.select().from(mockAttempts).where(and(eq(mockAttempts.studentId, userId), inArray(mockAttempts.testId, tests.map((t) => t.id)))).orderBy(desc(mockAttempts.startedAt)),
  ]);
  const now = Date.now();
  return tests.map((t) => {
    const mine = atts.filter((a) => a.testId === t.id);
    const scored = mine.filter((a) => a.submittedAt && a.score !== null);
    const open = mine.find((a) => !a.submittedAt && a.deadlineAt.getTime() + GRACE_MS > now);
    return {
      id: t.id,
      courseId: t.courseId,
      courseTitle: cs.find((c) => c.id === t.courseId)?.title ?? '',
      title: t.title,
      questionCount: qc.find((q) => q.testId === t.id)?.n ?? 0,
      durationSec: t.durationSec,
      attempts: scored.length,
      bestScore: scored.length ? Math.max(...scored.map((a) => a.score!)) : null,
      lastScore: scored[0]?.score ?? null,
      inProgressAttemptId: open?.id ?? null,
    };
  });
}

async function flashStats(userId: string, courseIds: string[]) {
  if (!courseIds.length) return { total: 0, known: 0 };
  const [[t], [k]] = await Promise.all([
    contentDb.select({ n: sql<number>`count(*)::int` }).from(flashcards).where(inArray(flashcards.courseId, courseIds)),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(flashcardReviews).where(and(eq(flashcardReviews.studentId, userId), inArray(flashcardReviews.courseId, courseIds), eq(flashcardReviews.known, true))),
  ]);
  return { total: t?.n ?? 0, known: Math.min(k?.n ?? 0, t?.n ?? 0) };
}

/** Recompute + store readiness (dashboard widget reads the column). */
async function refreshReadiness(p: PlanRow) {
  const ids = await activeCourseIds(p.studentId, p.courseIds);
  const [ls, mocks, fl] = await Promise.all([curriculum(ids), mockSummaries(p.studentId, ids), flashStats(p.studentId, ids)]);
  const done = ls.length ? await coreDb.select({ id: lessonProgress.lessonId }).from(lessonProgress).where(and(eq(lessonProgress.studentId, p.studentId), inArray(lessonProgress.lessonId, ls.map((l) => l.id)), isNotNull(lessonProgress.completedAt))) : [];
  const scored = mocks.filter((m) => m.bestScore !== null);
  const r = readiness({ lessonsDone: done.length, lessonsTotal: ls.length, mockScore: mocks.length ? (scored.length ? Math.round(scored.reduce((a, m) => a + m.bestScore!, 0) / mocks.length) : 0) : null, flashKnown: fl.known, flashTotal: fl.total });
  if (r.total !== p.readiness) await coreDb.update(examPlans).set({ readiness: r.total }).where(eq(examPlans.id, p.id));
  return { r, ids, ls, mocks, fl, doneIds: new Set(done.map((d) => d.id)) };
}

function toPlanDay(d: GenDay): PlanDay {
  return {
    date: d.date,
    isRevision: d.isRevision,
    totalMinutes: d.totalMinutes,
    items: d.items.map((i) => {
      if (i.kind === 'lesson') return { kind: 'lesson' as const, lessonId: i.lesson.id, courseId: i.lesson.courseId, title: i.lesson.title, chapterTitle: i.lesson.chapterTitle, durationSec: i.lesson.durationSec, lessonType: i.lesson.type, isImportant: i.lesson.isImportant, done: i.done };
      if (i.kind === 'mock') return { kind: 'mock' as const, testId: i.mock.id, title: i.mock.title, questionCount: i.mock.questionCount, done: i.done };
      if (i.kind === 'flashcards') return { kind: 'flashcards' as const, count: i.count, done: i.done };
      return { kind: 'revision' as const, title: 'পরীক্ষার আগের রাতের রিভিশন', done: false };
    }),
  };
}

export async function planDetail(userId: string, id: string, now = new Date()): Promise<ExamPlanDetail> {
  const p = await ownPlan(userId, id);
  const { r, ids, ls, mocks, fl, doneIds } = await refreshReadiness(p);
  const today = dhakaDay(now);
  const todayStart = dhakaStart(today);
  const doneToday = ls.length ? await coreDb.select({ id: lessonProgress.lessonId }).from(lessonProgress).where(and(eq(lessonProgress.studentId, userId), inArray(lessonProgress.lessonId, ls.map((l) => l.id)), gte(lessonProgress.completedAt, todayStart))) : [];
  const todaySet = new Set(doneToday.map((d) => d.id));
  const planLessons: PlanLesson[] = ls.map((l) => ({ id: l.id, courseId: l.courseId, title: l.title, chapterTitle: l.chapterTitle, durationSec: l.durationSec, type: l.type, isImportant: l.isImportant, doneToday: todaySet.has(l.id), doneBefore: doneIds.has(l.id) && !todaySet.has(l.id) }));
  const days = generatePlan({ today, examDay: dhakaDay(p.examDate), lessons: planLessons, mocks: mocks.map((m) => ({ id: m.id, title: m.title, questionCount: m.questionCount, attempted: m.attempts > 0 })), flashcards: fl }).map(toPlanDay);

  const [cs, docs, enr, nCount, bCount, wrong] = await Promise.all([
    p.courseIds.length ? contentDb.select({ id: courses.id, slug: courses.slug, title: courses.title }).from(courses).where(inArray(courses.id, p.courseIds)) : [],
    ids.length ? contentDb.select({ id: documents.id, title: documents.title, year: documents.year, courseId: documents.courseId }).from(documents).where(and(inArray(documents.courseId, ids), eq(documents.kind, 'previous_questions'))).orderBy(desc(documents.year)) : [],
    activeCourseIds(userId, p.courseIds),
    ids.length ? coreDb.select({ n: sql<number>`count(*)::int` }).from(notes).where(and(eq(notes.studentId, userId), inArray(notes.courseId, ids))) : [{ n: 0 }],
    ids.length ? coreDb.select({ n: sql<number>`count(*)::int` }).from(bookmarks).where(and(eq(bookmarks.studentId, userId), inArray(bookmarks.courseId, ids))) : [{ n: 0 }],
    mocks.length ? coreDb.select({ n: sql<number>`coalesce(sum(${mockAttempts.wrongCount}),0)::int` }).from(mockAttempts).where(and(eq(mockAttempts.studentId, userId), inArray(mockAttempts.testId, mocks.map((m) => m.id)), isNotNull(mockAttempts.submittedAt))) : [{ n: 0 }],
  ]);

  // syllabus checklist by chapter
  const chapters = new Map<string, { courseId: string; chapterId: string; title: string; sectionTitle: string; done: number; total: number; important: number }>();
  for (const l of ls) {
    const c = chapters.get(l.chapterId) ?? { courseId: l.courseId, chapterId: l.chapterId, title: l.chapterTitle, sectionTitle: l.sectionTitle, done: 0, total: 0, important: 0 };
    c.total++;
    if (doneIds.has(l.id)) c.done++;
    if (l.isImportant) c.important++;
    chapters.set(l.chapterId, c);
  }
  const daysLeft = daysBetween(today, dhakaDay(p.examDate));
  return {
    ...summary({ ...p, readiness: r.total }, now),
    readinessBreakdown: { lessons: r.lessons, mock: r.mock, flashcards: r.flashcards },
    courses: cs.map((c) => ({ ...c, enrolled: enr.includes(c.id) })),
    today: days.find((d) => d.date === today) ?? null,
    days,
    syllabus: [...chapters.values()].map((c) => ({ ...c, status: c.done === 0 ? ('todo' as const) : c.done >= c.total ? ('done' as const) : ('doing' as const) })),
    important: ls.filter((l) => l.isImportant).map((l) => ({ lessonId: l.id, courseId: l.courseId, title: l.title, done: doneIds.has(l.id), durationSec: l.durationSec })),
    shortLectures: ls.filter((l) => l.type === 'video' && l.durationSec > 0 && l.durationSec <= 10 * 60).map((l) => ({ lessonId: l.id, courseId: l.courseId, title: l.title, durationSec: l.durationSec, done: doneIds.has(l.id) })),
    previousQuestions: docs.flatMap((d) => (d.courseId ? [{ id: d.id, title: d.title, year: d.year, courseId: d.courseId }] : [])),
    mockTests: mocks,
    flashcards: fl,
    revisionUnlocked: daysLeft <= 1,
    revisionPack: { importantLessons: ls.filter((l) => l.isImportant).length, notes: nCount[0]?.n ?? 0, bookmarks: bCount[0]?.n ?? 0, wrongAnswers: wrong[0]?.n ?? 0 },
  };
}

// ---------------------------------------------------------------- flashcards

export async function planFlashcards(userId: string, id: string) {
  const p = await ownPlan(userId, id);
  const ids = await activeCourseIds(userId, p.courseIds);
  if (!ids.length) return { items: [] };
  const [cards, revs] = await Promise.all([
    contentDb.select().from(flashcards).where(inArray(flashcards.courseId, ids)).orderBy(asc(flashcards.position)),
    coreDb.select().from(flashcardReviews).where(and(eq(flashcardReviews.studentId, userId), inArray(flashcardReviews.courseId, ids))),
  ]);
  const items = cards.map((c) => ({ id: c.id, courseId: c.courseId, front: c.front, back: c.back, known: revs.find((r) => r.flashcardId === c.id)?.known ?? null }));
  // not-yet-known first, then unseen, then known
  const rank = (k: boolean | null) => (k === false ? 0 : k === null ? 1 : 2);
  items.sort((a, b) => rank(a.known) - rank(b.known));
  return { items };
}

export async function reviewFlashcard(userId: string, cardId: string, known: boolean) {
  const [c] = await contentDb.select().from(flashcards).where(eq(flashcards.id, cardId));
  if (!c) throw notFound();
  if (!(await activeCourseIds(userId, [c.courseId])).length) throw forbidden('এই ফ্ল্যাশকার্ড দেখতে কোর্সটি কিনুন।');
  await coreDb.insert(flashcardReviews).values({ studentId: userId, flashcardId: cardId, courseId: c.courseId, known }).onConflictDoUpdate({ target: [flashcardReviews.studentId, flashcardReviews.flashcardId], set: { known, updatedAt: new Date() } });
}

// ---------------------------------------------------------------- mock attempts

const shuffleBy = <T extends { id: string }>(xs: T[], seed: string) => [...xs].sort((a, b) => createHash('sha1').update(seed + a.id).digest('hex').localeCompare(createHash('sha1').update(seed + b.id).digest('hex')));

async function testAccess(userId: string, testId: string) {
  const [t] = await contentDb.select().from(mockTests).where(eq(mockTests.id, testId));
  if (!t) throw notFound('মক টেস্টটি পাওয়া যায়নি।');
  const [c] = await contentDb.select({ sellerId: courses.sellerId }).from(courses).where(eq(courses.id, t.courseId));
  const owner = c?.sellerId === userId;
  if (!owner && !(await activeCourseIds(userId, [t.courseId])).length) throw forbidden('মক টেস্ট দিতে কোর্সটি কিনুন।');
  return t;
}

export async function studentMockTests(userId: string) {
  const enr = await coreDb.select({ courseId: enrollments.courseId }).from(enrollments).where(and(eq(enrollments.studentId, userId), eq(enrollments.status, 'active')));
  return { items: await mockSummaries(userId, await activeCourseIds(userId, enr.map((e) => e.courseId))) };
}

async function toAttempt(a: typeof mockAttempts.$inferSelect): Promise<Attempt> {
  const [[t], qs, ans] = await Promise.all([
    contentDb.select().from(mockTests).where(eq(mockTests.id, a.testId)),
    contentDb.select().from(mockQuestions).where(inArray(mockQuestions.id, a.questionOrder)),
    coreDb.select().from(mockAnswers).where(eq(mockAnswers.attemptId, a.id)),
  ]);
  const opts = qs.length ? await contentDb.select({ id: mockOptions.id, questionId: mockOptions.questionId, body: mockOptions.body }).from(mockOptions).where(inArray(mockOptions.questionId, qs.map((q) => q.id))) : [];
  return {
    id: a.id,
    testId: a.testId,
    courseId: a.courseId,
    title: t?.title ?? '',
    durationSec: t?.durationSec ?? 0,
    startedAt: a.startedAt.toISOString(),
    deadlineAt: a.deadlineAt.toISOString(),
    serverNow: new Date().toISOString(),
    submitted: !!a.submittedAt,
    questions: a.questionOrder.flatMap((qid) => {
      const q = qs.find((x) => x.id === qid);
      if (!q) return [];
      return [{ id: q.id, body: q.body, chapterTag: q.chapterTag, options: shuffleBy(opts.filter((o) => o.questionId === q.id), a.id).map((o) => ({ id: o.id, body: o.body })) }];
    }),
    answers: ans.map((x) => ({ questionId: x.questionId, optionId: x.optionId, flagged: x.flagged })),
  };
}

async function ownAttempt(userId: string, id: string) {
  const [a] = await coreDb.select().from(mockAttempts).where(eq(mockAttempts.id, id));
  if (!a) throw notFound();
  if (a.studentId !== userId) throw forbidden();
  return a;
}

export async function startAttempt(userId: string, testId: string) {
  const t = await testAccess(userId, testId);
  const [open] = await coreDb.select().from(mockAttempts).where(and(eq(mockAttempts.studentId, userId), eq(mockAttempts.testId, testId), sql`${mockAttempts.submittedAt} is null`)).orderBy(desc(mockAttempts.startedAt)).limit(1);
  if (open) {
    if (open.deadlineAt.getTime() + GRACE_MS > Date.now()) return toAttempt(open);
    await finalize(open, true);
  }
  const qs = await contentDb.select({ id: mockQuestions.id, position: mockQuestions.position }).from(mockQuestions).where(eq(mockQuestions.testId, testId)).orderBy(asc(mockQuestions.position));
  if (!qs.length) throw badRequest('এই মক টেস্টে এখনো প্রশ্ন নেই।');
  const seed = `${userId}:${Date.now()}`;
  const order = t.shuffle ? shuffleBy(qs, seed).map((q) => q.id) : qs.map((q) => q.id);
  const now = new Date();
  const [a] = await coreDb.insert(mockAttempts).values({ testId, courseId: t.courseId, studentId: userId, startedAt: now, deadlineAt: new Date(now.getTime() + t.durationSec * 1000), questionOrder: order }).returning();
  return toAttempt(a!);
}

export async function getAttempt(userId: string, id: string) {
  const a = await ownAttempt(userId, id);
  if (!a.submittedAt && a.deadlineAt.getTime() + GRACE_MS < Date.now()) {
    await finalize(a, true);
    return toAttempt((await coreDb.select().from(mockAttempts).where(eq(mockAttempts.id, id)))[0]!);
  }
  return toAttempt(a);
}

export async function saveAnswer(userId: string, id: string, b: { questionId: string; optionId: string | null; flagged?: boolean }) {
  const a = await ownAttempt(userId, id);
  if (a.submittedAt) throw conflict('এই মক টেস্ট জমা হয়ে গেছে।');
  if (a.deadlineAt.getTime() + GRACE_MS < Date.now()) {
    await finalize(a, true);
    throw conflict('সময় শেষ — উত্তর স্বয়ংক্রিয়ভাবে জমা হয়েছে।');
  }
  if (!a.questionOrder.includes(b.questionId)) throw badRequest('প্রশ্নটি এই টেস্টের নয়।');
  if (b.optionId) {
    const [o] = await contentDb.select({ q: mockOptions.questionId }).from(mockOptions).where(eq(mockOptions.id, b.optionId));
    if (o?.q !== b.questionId) throw badRequest('উত্তরটি এই প্রশ্নের নয়।');
  }
  await coreDb
    .insert(mockAnswers)
    .values({ attemptId: id, questionId: b.questionId, optionId: b.optionId, flagged: b.flagged ?? false })
    .onConflictDoUpdate({ target: [mockAnswers.attemptId, mockAnswers.questionId], set: { optionId: b.optionId, ...(b.flagged !== undefined ? { flagged: b.flagged } : {}), updatedAt: new Date() } });
}

async function finalize(a: typeof mockAttempts.$inferSelect, auto: boolean) {
  const [qs, ans] = await Promise.all([
    contentDb.select({ id: mockQuestions.id, chapterTag: mockQuestions.chapterTag }).from(mockQuestions).where(inArray(mockQuestions.id, a.questionOrder)),
    coreDb.select().from(mockAnswers).where(eq(mockAnswers.attemptId, a.id)),
  ]);
  const correctOpts = qs.length ? await contentDb.select({ id: mockOptions.id, q: mockOptions.questionId }).from(mockOptions).where(and(inArray(mockOptions.questionId, qs.map((q) => q.id)), eq(mockOptions.isCorrect, true))) : [];
  let correct = 0;
  let wrong = 0;
  const chapters = new Map<string, { correct: number; total: number }>();
  for (const q of qs) {
    const chosen = ans.find((x) => x.questionId === q.id)?.optionId ?? null;
    const ok = !!chosen && correctOpts.some((o) => o.q === q.id && o.id === chosen);
    if (chosen && ok) correct++;
    else if (chosen) wrong++;
    const tag = q.chapterTag || 'অন্যান্য';
    const c = chapters.get(tag) ?? { correct: 0, total: 0 };
    c.total++;
    if (ok) c.correct++;
    chapters.set(tag, c);
  }
  const total = qs.length;
  const submittedAt = auto ? new Date(Math.min(Date.now(), a.deadlineAt.getTime())) : new Date();
  const [u] = await coreDb
    .update(mockAttempts)
    .set({ submittedAt, score: total ? Math.round((correct / total) * 100) : 0, correctCount: correct, wrongCount: wrong, skippedCount: total - correct - wrong, resultJson: { auto, chapters: [...chapters.entries()].map(([tag, v]) => ({ tag, ...v })) } })
    .where(and(eq(mockAttempts.id, a.id), sql`${mockAttempts.submittedAt} is null`))
    .returning();
  if (u) {
    const plans = await coreDb.select().from(examPlans).where(and(eq(examPlans.studentId, a.studentId), eq(examPlans.active, true), sql`${a.courseId} = any(${examPlans.courseIds})`));
    for (const p of plans) await refreshReadiness(p);
    await invalidateDashboard(a.studentId);
  }
}

export async function submitAttempt(userId: string, id: string) {
  const a = await ownAttempt(userId, id);
  if (!a.submittedAt) await finalize(a, a.deadlineAt.getTime() + GRACE_MS < Date.now());
  return attemptResult(userId, id);
}

export async function attemptResult(userId: string, id: string): Promise<AttemptResult> {
  let a = await ownAttempt(userId, id);
  if (!a.submittedAt) {
    if (a.deadlineAt.getTime() + GRACE_MS > Date.now()) throw conflict('টেস্টটি এখনো চলছে।');
    await finalize(a, true);
    a = await ownAttempt(userId, id);
  }
  const [[t], qs, ans, all] = await Promise.all([
    contentDb.select().from(mockTests).where(eq(mockTests.id, a.testId)),
    contentDb.select().from(mockQuestions).where(inArray(mockQuestions.id, a.questionOrder)),
    coreDb.select().from(mockAnswers).where(eq(mockAnswers.attemptId, a.id)),
    coreDb.select({ score: mockAttempts.score }).from(mockAttempts).where(and(eq(mockAttempts.studentId, userId), eq(mockAttempts.testId, a.testId), isNotNull(mockAttempts.submittedAt))),
  ]);
  const opts = qs.length ? await contentDb.select().from(mockOptions).where(inArray(mockOptions.questionId, qs.map((q) => q.id))) : [];
  const rj = (a.resultJson ?? {}) as { auto?: boolean; chapters?: { tag: string; correct: number; total: number }[] };
  return {
    attemptId: a.id,
    testId: a.testId,
    courseId: a.courseId,
    title: t?.title ?? '',
    score: a.score ?? 0,
    correct: a.correctCount ?? 0,
    wrong: a.wrongCount ?? 0,
    skipped: a.skippedCount ?? 0,
    total: a.questionOrder.length,
    timeUsedSec: Math.max(0, Math.round(((a.submittedAt ?? new Date()).getTime() - a.startedAt.getTime()) / 1000)),
    autoSubmitted: !!rj.auto,
    bestScore: Math.max(0, ...all.map((x) => x.score ?? 0)),
    attempts: all.length,
    chapters: rj.chapters ?? [],
    review: a.questionOrder.flatMap((qid) => {
      const q = qs.find((x) => x.id === qid);
      if (!q) return [];
      const os = shuffleBy(opts.filter((o) => o.questionId === q.id), a.id);
      return [{ questionId: q.id, body: q.body, chapterTag: q.chapterTag, explanation: q.explanation, chosenOptionId: ans.find((x) => x.questionId === q.id)?.optionId ?? null, correctOptionId: os.find((o) => o.isCorrect)?.id ?? null, options: os.map((o) => ({ id: o.id, body: o.body })) }];
    }),
  };
}

// ---------------------------------------------------------------- seller authoring

async function ownCourse(sellerId: string, courseId: string) {
  const [c] = await contentDb.select({ sellerId: courses.sellerId }).from(courses).where(eq(courses.id, courseId));
  if (!c) throw notFound();
  if (c.sellerId !== sellerId) throw forbidden();
}

async function sellerMock(id: string) {
  const [t] = await contentDb.select().from(mockTests).where(eq(mockTests.id, id));
  const qs = await contentDb.select().from(mockQuestions).where(eq(mockQuestions.testId, id)).orderBy(asc(mockQuestions.position));
  const opts = qs.length ? await contentDb.select().from(mockOptions).where(inArray(mockOptions.questionId, qs.map((q) => q.id))).orderBy(asc(mockOptions.position)) : [];
  const [n] = await coreDb.select({ n: count() }).from(mockAttempts).where(eq(mockAttempts.testId, id));
  return { id: t!.id, title: t!.title, description: t!.description, durationSec: t!.durationSec, attempts: n?.n ?? 0, questions: qs.map((q) => ({ id: q.id, body: q.body, explanation: q.explanation, chapterTag: q.chapterTag, options: opts.filter((o) => o.questionId === q.id).map((o) => ({ body: o.body, isCorrect: o.isCorrect })) })) };
}

type MockInput = { title: string; description?: string | null; durationSec: number; questions: { body: string; explanation?: string | null; chapterTag?: string | null; options: { body: string; isCorrect: boolean }[] }[] };
function checkMock(d: MockInput) {
  d.questions.forEach((q, i) => {
    if (q.options.filter((o) => o.isCorrect).length !== 1) throw badRequest(`প্রশ্ন ${i + 1}: ঠিক একটি সঠিক উত্তর বাছাই করুন।`);
  });
}
async function writeQuestions(tx: Parameters<Parameters<typeof contentDb.transaction>[0]>[0], testId: string, d: MockInput) {
  for (const [i, q] of d.questions.entries()) {
    const [row] = await tx.insert(mockQuestions).values({ testId, body: q.body, explanation: q.explanation ?? null, chapterTag: q.chapterTag ?? null, position: i }).returning({ id: mockQuestions.id });
    await tx.insert(mockOptions).values(q.options.map((o, j) => ({ questionId: row!.id, body: o.body, isCorrect: o.isCorrect, position: j })));
  }
}

export async function listSellerMocks(sellerId: string, courseId: string) {
  await ownCourse(sellerId, courseId);
  const ts = await contentDb.select({ id: mockTests.id }).from(mockTests).where(eq(mockTests.courseId, courseId)).orderBy(asc(mockTests.createdAt));
  return { items: await Promise.all(ts.map((t) => sellerMock(t.id))) };
}

export async function createMock(sellerId: string, courseId: string, d: MockInput) {
  await ownCourse(sellerId, courseId);
  checkMock(d);
  const id = await contentDb.transaction(async (tx) => {
    const [t] = await tx.insert(mockTests).values({ courseId, title: d.title, description: d.description ?? null, durationSec: d.durationSec }).returning({ id: mockTests.id });
    await writeQuestions(tx, t!.id, d);
    return t!.id;
  });
  await audit({ actorId: sellerId, action: 'mock.create', entity: 'mock_tests', entityId: id, after: { courseId, questions: d.questions.length } });
  return sellerMock(id);
}

export async function updateMock(sellerId: string, id: string, d: MockInput) {
  const [t] = await contentDb.select().from(mockTests).where(eq(mockTests.id, id));
  if (!t) throw notFound();
  await ownCourse(sellerId, t.courseId);
  checkMock(d);
  // open attempts keep their question ids; finished attempts keep scores (review shows what still exists)
  await contentDb.transaction(async (tx) => {
    await tx.update(mockTests).set({ title: d.title, description: d.description ?? null, durationSec: d.durationSec, updatedAt: new Date() }).where(eq(mockTests.id, id));
    await tx.delete(mockQuestions).where(eq(mockQuestions.testId, id));
    await writeQuestions(tx, id, d);
  });
  return sellerMock(id);
}

export async function deleteMock(sellerId: string, id: string) {
  const [t] = await contentDb.select().from(mockTests).where(eq(mockTests.id, id));
  if (!t) throw notFound();
  await ownCourse(sellerId, t.courseId);
  await contentDb.delete(mockTests).where(eq(mockTests.id, id));
  await audit({ actorId: sellerId, action: 'mock.delete', entity: 'mock_tests', entityId: id });
}

export async function sellerFlashcards(sellerId: string, courseId: string) {
  await ownCourse(sellerId, courseId);
  const rows = await contentDb.select().from(flashcards).where(eq(flashcards.courseId, courseId)).orderBy(asc(flashcards.position));
  return { items: rows.map((r) => ({ id: r.id, front: r.front, back: r.back })) };
}

export async function setFlashcards(sellerId: string, courseId: string, cards: { front: string; back: string }[]) {
  await ownCourse(sellerId, courseId);
  await contentDb.transaction(async (tx) => {
    await tx.delete(flashcards).where(eq(flashcards.courseId, courseId));
    if (cards.length) await tx.insert(flashcards).values(cards.map((c, i) => ({ courseId, front: c.front, back: c.back, position: i })));
  });
  return sellerFlashcards(sellerId, courseId);
}

// ---------------------------------------------------------------- jobs

/** exam.replan (01:00): refresh readiness for active upcoming plans; archive plans 2 days after the exam. */
export async function replanAll() {
  const plans = await coreDb.select().from(examPlans).where(and(eq(examPlans.active, true), gte(examPlans.examDate, new Date(Date.now() - 2 * 86_400_000)))).limit(5000);
  for (const p of plans) await refreshReadiness(p);
  await coreDb.update(examPlans).set({ active: false }).where(and(eq(examPlans.active, true), sql`${examPlans.examDate} < now() - interval '2 days'`));
  return plans.length;
}
defineJob('exam.replan', () => replanAll());

/** exam.dailyPlan (07:30 Dhaka): "আজকের প্ল্যান" push for each upcoming plan with work today. */
export async function dailyPlanNudges(now = new Date()) {
  const plans = await coreDb.select().from(examPlans).where(and(eq(examPlans.active, true), gte(examPlans.examDate, now))).limit(5000);
  let sent = 0;
  for (const p of plans) {
    const d = await planDetail(p.studentId, p.id, now).catch(() => null);
    const items = d?.today?.items.filter((i) => !i.done) ?? [];
    if (!d || !items.length) continue;
    const lessonsN = items.filter((i) => i.kind === 'lesson').length;
    const mins = d.today!.items.reduce((a, i) => a + (i.kind === 'lesson' ? effectiveMinutes({ durationSec: i.durationSec, type: i.lessonType }) : 0), 0);
    const body = d.today!.isRevision ? 'আজ রিভিশনের দিন — মক টেস্ট আর ফ্ল্যাশকার্ড দিয়ে শেষবার ঝালিয়ে নিন।' : `${lessonsN}টি লেসন · প্রায় ${mins} মিনিট। পরীক্ষার আর ${d.daysLeft} দিন বাকি।`;
    if (await notify({ userId: p.studentId, type: 'exam.daily_plan', ref: `${p.id}:${dhakaDay(now)}`, title: `আজকের প্ল্যান: ${p.subjectName}`, body, link: `/exam-mode/${p.id}` })) sent++;
  }
  return sent;
}
defineJob('exam.dailyPlan', () => dailyPlanNudges());
