import { z } from 'zod';
import { IdParams, Ok, endpoint, patchOf } from './common';
import { EXAM_TYPES } from '../types';

// ---------------------------------------------------------------- exam plans

export const ExamPlanInput = z.object({
  subjectName: z.string().trim().min(2).max(160),
  subjectMasterId: z.uuid().nullable().optional(),
  courseCode: z.string().trim().max(30).nullable().optional(),
  examType: z.enum(EXAM_TYPES),
  /** exam start (date + time, Asia/Dhaka offset) */
  examDate: z.iso.datetime({ offset: true }),
  courseIds: z.array(z.uuid()).max(5).default([]),
});

export const ExamPlanSummary = z.object({
  id: z.uuid(),
  subjectName: z.string(),
  courseCode: z.string().nullable(),
  examType: z.string(),
  examDate: z.string(),
  daysLeft: z.number().int(),
  readiness: z.number().int(),
  courseIds: z.array(z.uuid()),
  active: z.boolean(),
  past: z.boolean(),
});
export type ExamPlanSummary = z.infer<typeof ExamPlanSummary>;

export const PlanItem = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('lesson'), lessonId: z.uuid(), courseId: z.uuid(), title: z.string(), chapterTitle: z.string(), durationSec: z.number().int(), lessonType: z.string(), isImportant: z.boolean(), done: z.boolean() }),
  z.object({ kind: z.literal('mock'), testId: z.uuid(), title: z.string(), questionCount: z.number().int(), done: z.boolean() }),
  z.object({ kind: z.literal('flashcards'), count: z.number().int(), done: z.boolean() }),
  z.object({ kind: z.literal('revision'), title: z.string(), done: z.boolean() }),
]);
export type PlanItem = z.infer<typeof PlanItem>;

export const PlanDay = z.object({ date: z.string(), items: z.array(PlanItem), totalMinutes: z.number().int(), isRevision: z.boolean() });
export type PlanDay = z.infer<typeof PlanDay>;

export const SyllabusChapter = z.object({ courseId: z.uuid(), chapterId: z.uuid(), title: z.string(), sectionTitle: z.string(), done: z.number().int(), total: z.number().int(), important: z.number().int(), status: z.enum(['todo', 'doing', 'done']) });

export const MockSummary = z.object({ id: z.uuid(), courseId: z.uuid(), courseTitle: z.string(), title: z.string(), questionCount: z.number().int(), durationSec: z.number().int(), attempts: z.number().int(), bestScore: z.number().int().nullable(), lastScore: z.number().int().nullable(), inProgressAttemptId: z.uuid().nullable() });
export type MockSummary = z.infer<typeof MockSummary>;

export const ExamPlanDetail = ExamPlanSummary.extend({
  readinessBreakdown: z.object({ lessons: z.number().int(), mock: z.number().int().nullable(), flashcards: z.number().int().nullable() }),
  courses: z.array(z.object({ id: z.uuid(), slug: z.string(), title: z.string(), enrolled: z.boolean() })),
  today: PlanDay.nullable(),
  days: z.array(PlanDay),
  syllabus: z.array(SyllabusChapter),
  important: z.array(z.object({ lessonId: z.uuid(), courseId: z.uuid(), title: z.string(), done: z.boolean(), durationSec: z.number().int() })),
  shortLectures: z.array(z.object({ lessonId: z.uuid(), courseId: z.uuid(), title: z.string(), durationSec: z.number().int(), done: z.boolean() })),
  previousQuestions: z.array(z.object({ id: z.uuid(), title: z.string(), year: z.number().int().nullable(), courseId: z.uuid() })),
  mockTests: z.array(MockSummary),
  flashcards: z.object({ total: z.number().int(), known: z.number().int() }),
  /** "পরীক্ষার আগের রাত" pack unlocks T-1 day */
  revisionUnlocked: z.boolean(),
  revisionPack: z.object({ importantLessons: z.number().int(), notes: z.number().int(), bookmarks: z.number().int(), wrongAnswers: z.number().int() }),
});
export type ExamPlanDetail = z.infer<typeof ExamPlanDetail>;

export const Flashcard = z.object({ id: z.uuid(), courseId: z.uuid(), front: z.string(), back: z.string(), known: z.boolean().nullable() });
export type Flashcard = z.infer<typeof Flashcard>;

// ---------------------------------------------------------------- mock tests

export const AttemptQuestion = z.object({ id: z.uuid(), body: z.string(), chapterTag: z.string().nullable(), options: z.array(z.object({ id: z.uuid(), body: z.string() })) });
export const Attempt = z.object({
  id: z.uuid(),
  testId: z.uuid(),
  courseId: z.uuid(),
  title: z.string(),
  durationSec: z.number().int(),
  startedAt: z.string(),
  deadlineAt: z.string(),
  /** for client clock skew */
  serverNow: z.string(),
  submitted: z.boolean(),
  questions: z.array(AttemptQuestion),
  answers: z.array(z.object({ questionId: z.uuid(), optionId: z.uuid().nullable(), flagged: z.boolean() })),
});
export type Attempt = z.infer<typeof Attempt>;

export const AttemptResult = z.object({
  attemptId: z.uuid(),
  testId: z.uuid(),
  courseId: z.uuid(),
  title: z.string(),
  score: z.number().int(),
  correct: z.number().int(),
  wrong: z.number().int(),
  skipped: z.number().int(),
  total: z.number().int(),
  timeUsedSec: z.number().int(),
  autoSubmitted: z.boolean(),
  bestScore: z.number().int(),
  attempts: z.number().int(),
  chapters: z.array(z.object({ tag: z.string(), correct: z.number().int(), total: z.number().int() })),
  review: z.array(z.object({ questionId: z.uuid(), body: z.string(), chapterTag: z.string().nullable(), explanation: z.string().nullable(), chosenOptionId: z.uuid().nullable(), correctOptionId: z.uuid().nullable(), options: z.array(z.object({ id: z.uuid(), body: z.string() })) })),
});
export type AttemptResult = z.infer<typeof AttemptResult>;

export const exam = {
  plans: endpoint({ method: 'GET', path: '/exam-plans', summary: 'My exam plans (upcoming first)', tag: 'exam', auth: 'user', response: z.object({ items: z.array(ExamPlanSummary) }) }),
  create: endpoint({ method: 'POST', path: '/exam-plans', summary: 'Create an exam plan', tag: 'exam', auth: 'user', body: ExamPlanInput, response: ExamPlanSummary, status: 201 }),
  get: endpoint({ method: 'GET', path: '/exam-plans/:id', summary: 'Exam Mode: countdown, readiness, daily plan, syllabus, resources', tag: 'exam', auth: 'user', params: IdParams, response: ExamPlanDetail }),
  update: endpoint({ method: 'PATCH', path: '/exam-plans/:id', summary: 'Change date / courses / archive', tag: 'exam', auth: 'user', params: IdParams, body: patchOf(ExamPlanInput).extend({ active: z.boolean().optional() }), response: ExamPlanSummary }),
  remove: endpoint({ method: 'DELETE', path: '/exam-plans/:id', summary: 'Delete exam plan', tag: 'exam', auth: 'user', params: IdParams, response: Ok }),
  flashcards: endpoint({ method: 'GET', path: '/exam-plans/:id/flashcards', summary: 'Flashcards from linked courses (unknown first)', tag: 'exam', auth: 'user', params: IdParams, response: z.object({ items: z.array(Flashcard) }) }),
  reviewCard: endpoint({ method: 'POST', path: '/flashcards/:id/review', summary: 'Mark a flashcard known / not yet', tag: 'exam', auth: 'user', params: IdParams, body: z.object({ known: z.boolean() }), response: Ok }),

  mockTests: endpoint({ method: 'GET', path: '/mock-tests', summary: 'Mock tests across my courses', tag: 'exam', auth: 'user', response: z.object({ items: z.array(MockSummary) }) }),
  start: endpoint({ method: 'POST', path: '/mock-tests/:id/attempts', summary: 'Start (or resume) a timed attempt; questions shuffled per attempt', tag: 'exam', auth: 'user', params: IdParams, response: Attempt, status: 201 }),
  attempt: endpoint({ method: 'GET', path: '/mock-attempts/:id', summary: 'Resume an attempt (auto-submits after the deadline)', tag: 'exam', auth: 'user', params: IdParams, response: Attempt }),
  answer: endpoint({ method: 'PUT', path: '/mock-attempts/:id/answers', summary: 'Save an answer / flag (autosave)', tag: 'exam', auth: 'user', params: IdParams, body: z.object({ questionId: z.uuid(), optionId: z.uuid().nullable(), flagged: z.boolean().optional() }), response: Ok }),
  submit: endpoint({ method: 'POST', path: '/mock-attempts/:id/submit', summary: 'Submit and score', tag: 'exam', auth: 'user', params: IdParams, response: AttemptResult }),
  result: endpoint({ method: 'GET', path: '/mock-attempts/:id/result', summary: 'Result + review', tag: 'exam', auth: 'user', params: IdParams, response: AttemptResult }),
};

// ---------------------------------------------------------------- seller authoring

export const MockQuestionInput = z.object({
  body: z.string().trim().min(3).max(3000),
  explanation: z.string().trim().max(3000).nullable().optional(),
  chapterTag: z.string().trim().max(80).nullable().optional(),
  options: z.array(z.object({ body: z.string().trim().min(1).max(1000), isCorrect: z.boolean() })).min(2).max(6),
});
export const MockTestInput = z.object({
  title: z.string().trim().min(3).max(160),
  description: z.string().trim().max(1000).nullable().optional(),
  durationSec: z.number().int().min(300).max(4 * 3600).default(1800),
  questions: z.array(MockQuestionInput).min(1).max(150),
});
export const SellerMockTest = z.object({ id: z.uuid(), title: z.string(), description: z.string().nullable(), durationSec: z.number().int(), questions: z.array(MockQuestionInput.extend({ id: z.uuid() })), attempts: z.number().int() });
export const FlashcardInput = z.object({ front: z.string().trim().min(1).max(500), back: z.string().trim().min(1).max(2000) });

const CourseParams = z.object({ courseId: z.uuid() });
export const sellerExam = {
  mockTests: endpoint({ method: 'GET', path: '/seller/courses/:courseId/mock-tests', summary: 'Mock tests of my course', tag: 'seller', auth: 'seller', params: CourseParams, response: z.object({ items: z.array(SellerMockTest) }) }),
  createMock: endpoint({ method: 'POST', path: '/seller/courses/:courseId/mock-tests', summary: 'Create MCQ mock test (one correct option each)', tag: 'seller', auth: 'seller', params: CourseParams, body: MockTestInput, response: SellerMockTest, status: 201 }),
  updateMock: endpoint({ method: 'PUT', path: '/seller/mock-tests/:id', summary: 'Replace mock test content', tag: 'seller', auth: 'seller', params: IdParams, body: MockTestInput, response: SellerMockTest }),
  deleteMock: endpoint({ method: 'DELETE', path: '/seller/mock-tests/:id', summary: 'Delete mock test', tag: 'seller', auth: 'seller', params: IdParams, response: Ok }),
  flashcards: endpoint({ method: 'GET', path: '/seller/courses/:courseId/flashcards', summary: 'Flashcards of my course', tag: 'seller', auth: 'seller', params: CourseParams, response: z.object({ items: z.array(FlashcardInput.extend({ id: z.uuid() })) }) }),
  setFlashcards: endpoint({ method: 'PUT', path: '/seller/courses/:courseId/flashcards', summary: 'Replace flashcards', tag: 'seller', auth: 'seller', params: CourseParams, body: z.object({ cards: z.array(FlashcardInput).max(300) }), response: z.object({ items: z.array(FlashcardInput.extend({ id: z.uuid() })) }) }),
};
