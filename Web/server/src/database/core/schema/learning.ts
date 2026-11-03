import { boolean, index, integer, jsonb, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';
import { users } from './identity';

// Learning ACTIVITY lives in CORE; lesson/course/test ids reference CONTENT rows (no cross-db FK).

export const lessonProgress = pgTable(
  'lesson_progress',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id').notNull(),
    lessonId: uuid('lesson_id').notNull(),
    watchedSeconds: integer('watched_seconds').notNull().default(0),
    lastPositionSec: integer('last_position_sec').notNull().default(0),
    completedAt: ts('completed_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('lesson_progress_uq').on(t.studentId, t.lessonId), index('lesson_progress_course_idx').on(t.studentId, t.courseId)],
);

export const notes = pgTable(
  'notes',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id').notNull(),
    lessonId: uuid('lesson_id').notNull(),
    timestampSec: integer('timestamp_sec'),
    body: text('body').notNull(),
    ...timestamps,
  },
  (t) => [index('notes_student_idx').on(t.studentId, t.createdAt)],
);

export const bookmarks = pgTable(
  'bookmarks',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id').notNull(),
    lessonId: uuid('lesson_id').notNull(),
    timestampSec: integer('timestamp_sec'),
    body: text('body'),
    ...timestamps,
  },
  (t) => [index('bookmarks_student_idx').on(t.studentId, t.createdAt)],
);

export const questions = pgTable(
  'questions',
  {
    id: id(),
    lessonId: uuid('lesson_id').notNull(),
    courseId: uuid('course_id').notNull(),
    sellerId: uuid('seller_id').notNull(),
    authorId: uuid('author_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    body: text('body').notNull(),
    timestampSec: integer('timestamp_sec'),
    status: text('status').notNull().default('open'), // open | answered | hidden
    ...timestamps,
  },
  (t) => [index('questions_lesson_idx').on(t.lessonId, t.createdAt), index('questions_seller_idx').on(t.sellerId, t.status)],
);

export const answers = pgTable(
  'answers',
  {
    id: id(),
    questionId: uuid('question_id')
      .notNull()
      .references(() => questions.id, { onDelete: 'cascade' }),
    authorId: uuid('author_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    body: text('body').notNull(),
    isSeller: boolean('is_seller').notNull().default(false),
    status: text('status').notNull().default('visible'),
    ...timestamps,
  },
  (t) => [index('answers_question_idx').on(t.questionId)],
);

export const reviews = pgTable(
  'reviews',
  {
    id: id(),
    courseId: uuid('course_id').notNull(),
    sellerId: uuid('seller_id').notNull(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    rating: integer('rating').notNull(),
    body: text('body'),
    sellerReply: text('seller_reply'),
    sellerRepliedAt: ts('seller_replied_at'),
    verified: boolean('verified').notNull().default(true),
    status: text('status').notNull().default('visible'),
    ...timestamps,
  },
  (t) => [uniqueIndex('reviews_course_student_uq').on(t.courseId, t.studentId), index('reviews_course_idx').on(t.courseId, t.createdAt)],
);

export const examPlans = pgTable(
  'exam_plans',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    subjectMasterId: uuid('subject_master_id'),
    subjectName: text('subject_name').notNull(),
    courseCode: text('course_code'),
    examType: text('exam_type').notNull(),
    examDate: ts('exam_date').notNull(),
    courseIds: uuid('course_ids').array().notNull().default([]),
    planJson: jsonb('plan_json').$type<unknown>(),
    readiness: integer('readiness').notNull().default(0),
    active: boolean('active').notNull().default(true),
    ...timestamps,
  },
  (t) => [index('exam_plans_student_idx').on(t.studentId, t.examDate)],
);

export const flashcardReviews = pgTable(
  'flashcard_reviews',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    flashcardId: uuid('flashcard_id').notNull(),
    courseId: uuid('course_id').notNull(),
    known: boolean('known').notNull(),
    ...timestamps,
  },
  (t) => [uniqueIndex('flashcard_reviews_uq').on(t.studentId, t.flashcardId)],
);

export const mockAttempts = pgTable(
  'mock_attempts',
  {
    id: id(),
    testId: uuid('test_id').notNull(),
    courseId: uuid('course_id').notNull(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    startedAt: ts('started_at').notNull().defaultNow(),
    deadlineAt: ts('deadline_at').notNull(),
    submittedAt: ts('submitted_at'),
    questionOrder: uuid('question_order').array().notNull(),
    score: integer('score'),
    correctCount: integer('correct_count'),
    wrongCount: integer('wrong_count'),
    skippedCount: integer('skipped_count'),
    resultJson: jsonb('result_json'),
    ...timestamps,
  },
  (t) => [index('mock_attempts_student_idx').on(t.studentId, t.testId)],
);

export const mockAnswers = pgTable(
  'mock_answers',
  {
    id: id(),
    attemptId: uuid('attempt_id')
      .notNull()
      .references(() => mockAttempts.id, { onDelete: 'cascade' }),
    questionId: uuid('question_id').notNull(),
    optionId: uuid('option_id'),
    flagged: boolean('flagged').notNull().default(false),
    ...timestamps,
  },
  (t) => [uniqueIndex('mock_answers_uq').on(t.attemptId, t.questionId)],
);

export const wishlists = pgTable(
  'wishlists',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id').notNull(),
    ...timestamps,
  },
  (t) => [uniqueIndex('wishlists_uq').on(t.userId, t.courseId)],
);

export const courseRequests = pgTable(
  'course_requests',
  {
    id: id(),
    requesterId: uuid('requester_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    institutionId: uuid('institution_id'),
    subjectMasterId: uuid('subject_master_id'),
    subjectName: text('subject_name').notNull(),
    courseCode: text('course_code'),
    examType: text('exam_type').notNull(),
    note: text('note'),
    status: text('status').notNull().default('open'),
    voteCount: integer('vote_count').notNull().default(1),
    claimedBy: uuid('claimed_by'),
    linkedCourseId: uuid('linked_course_id'),
    /** dedupe key: institution|subject|examType (normalised) */
    dedupeKey: text('dedupe_key').notNull(),
    ...timestamps,
  },
  (t) => [uniqueIndex('course_requests_dedupe_uq').on(t.dedupeKey), index('course_requests_status_idx').on(t.status, t.voteCount)],
);

export const courseRequestVotes = pgTable(
  'course_request_votes',
  {
    id: id(),
    requestId: uuid('request_id')
      .notNull()
      .references(() => courseRequests.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    ...timestamps,
  },
  (t) => [uniqueIndex('course_request_votes_uq').on(t.requestId, t.userId)],
);
