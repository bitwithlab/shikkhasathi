import { boolean, index, integer, pgTable, text, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps } from '../../columns';
import { courseChapters, courses } from './courses';

export const mockTests = pgTable(
  'mock_tests',
  {
    id: id(),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    title: text('title').notNull(),
    description: text('description'),
    durationSec: integer('duration_sec').notNull().default(1800),
    shuffle: boolean('shuffle').notNull().default(true),
    ...timestamps,
  },
  (t) => [index('mock_tests_course_idx').on(t.courseId)],
);

export const mockQuestions = pgTable(
  'mock_questions',
  {
    id: id(),
    testId: uuid('test_id')
      .notNull()
      .references(() => mockTests.id, { onDelete: 'cascade' }),
    body: text('body').notNull(),
    explanation: text('explanation'),
    chapterTag: text('chapter_tag'),
    chapterId: uuid('chapter_id').references(() => courseChapters.id, { onDelete: 'set null' }),
    position: integer('position').notNull(),
    ...timestamps,
  },
  (t) => [index('mock_questions_test_idx').on(t.testId, t.position)],
);

export const mockOptions = pgTable(
  'mock_options',
  {
    id: id(),
    questionId: uuid('question_id')
      .notNull()
      .references(() => mockQuestions.id, { onDelete: 'cascade' }),
    body: text('body').notNull(),
    isCorrect: boolean('is_correct').notNull().default(false),
    position: integer('position').notNull(),
    ...timestamps,
  },
  (t) => [index('mock_options_question_idx').on(t.questionId)],
);

export const flashcards = pgTable(
  'flashcards',
  {
    id: id(),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    chapterId: uuid('chapter_id').references(() => courseChapters.id, { onDelete: 'set null' }),
    front: text('front').notNull(),
    back: text('back').notNull(),
    position: integer('position').notNull().default(0),
    ...timestamps,
  },
  (t) => [index('flashcards_course_idx').on(t.courseId)],
);
