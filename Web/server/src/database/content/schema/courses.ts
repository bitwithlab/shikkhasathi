import { bigint, boolean, index, integer, jsonb, numeric, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';
import { departments, institutionSubjects, institutions, programs, subjectMasters } from './catalog';

const money = (name: string) => bigint(name, { mode: 'number' });

export const videoAssets = pgTable(
  'video_assets',
  {
    id: id(),
    ownerId: uuid('owner_id').notNull(), // CORE users.id
    provider: text('provider').notNull(), // bunny | cloudflare | mux | external_link | fake
    providerVideoId: text('provider_video_id'),
    externalUrl: text('external_url'),
    title: text('title'),
    status: text('status').notNull().default('uploading'), // uploading | processing | ready | failed
    durationSec: integer('duration_sec').notNull().default(0),
    thumbnailUrl: text('thumbnail_url'),
    ...timestamps,
  },
  (t) => [index('video_assets_owner_idx').on(t.ownerId), uniqueIndex('video_assets_provider_uq').on(t.provider, t.providerVideoId)],
);

export const courses = pgTable(
  'courses',
  {
    id: id(),
    sellerId: uuid('seller_id').notNull(), // CORE users.id
    title: text('title').notNull(),
    slug: text('slug').notNull(),
    shortDescription: text('short_description').notNull().default(''),
    description: text('description').notNull().default(''),
    learnings: text('learnings').array().notNull().default([]),
    notFor: text('not_for').array().notNull().default([]),
    faq: jsonb('faq').$type<{ q: string; a: string }[]>().notNull().default([]),
    coverUrl: text('cover_url'),
    coverColor: text('cover_color'),
    introVideoId: uuid('intro_video_id').references(() => videoAssets.id, { onDelete: 'set null' }),
    language: text('language').notNull().default('bn'),
    examType: text('exam_type').notNull().default('midterm'),
    courseType: text('course_type').notNull().default('crash'),
    price: money('price').notNull().default(0),
    compareAtPrice: money('compare_at_price'),
    accessType: text('access_type').notNull().default('lifetime'),
    accessDays: integer('access_days'),
    accessUntil: ts('access_until'),
    status: text('status').notNull().default('draft'),
    reviewNote: text('review_note'),
    submittedAt: ts('submitted_at'),
    publishedAt: ts('published_at'),
    contentUpdatedAt: ts('content_updated_at'),
    needsSpotReview: boolean('needs_spot_review').notNull().default(false),
    ratingAvg: numeric('rating_avg', { precision: 3, scale: 2 }).notNull().default('0'),
    ratingCount: integer('rating_count').notNull().default(0),
    enrollmentCount: integer('enrollment_count').notNull().default(0),
    enrollments30d: integer('enrollments_30d').notNull().default(0),
    totalDurationSec: integer('total_duration_sec').notNull().default(0),
    lessonCount: integer('lesson_count').notNull().default(0),
    viewCount: integer('view_count').notNull().default(0),
    sellerVerified: boolean('seller_verified').notNull().default(false),
    sellerName: text('seller_name').notNull().default(''),
    deletedAt: ts('deleted_at'),
    ...timestamps,
  },
  (t) => [
    uniqueIndex('courses_slug_uq').on(t.slug),
    index('courses_status_published_idx').on(t.status, t.publishedAt),
    index('courses_seller_idx').on(t.sellerId),
  ],
);

export const courseTargets = pgTable(
  'course_targets',
  {
    id: id(),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    isGeneral: boolean('is_general').notNull().default(false),
    institutionId: uuid('institution_id').references(() => institutions.id),
    departmentId: uuid('department_id').references(() => departments.id),
    programId: uuid('program_id').references(() => programs.id),
    academicLevel: text('academic_level'),
    semester: integer('semester'),
    subjectMasterId: uuid('subject_master_id').references(() => subjectMasters.id),
    institutionSubjectId: uuid('institution_subject_id').references(() => institutionSubjects.id),
    courseCode: text('course_code'),
    batches: text('batches').array().notNull().default([]),
    sections: text('sections').array().notNull().default([]),
    ...timestamps,
  },
  (t) => [
    index('course_targets_match_idx').on(t.institutionId, t.departmentId, t.semester, t.subjectMasterId),
    index('course_targets_code_idx').on(t.courseCode),
    index('course_targets_course_idx').on(t.courseId),
  ],
);

export const courseSections = pgTable(
  'course_sections',
  {
    id: id(),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    title: text('title').notNull(),
    position: integer('position').notNull(),
    ...timestamps,
  },
  (t) => [index('course_sections_course_idx').on(t.courseId, t.position)],
);

export const courseChapters = pgTable(
  'course_chapters',
  {
    id: id(),
    sectionId: uuid('section_id')
      .notNull()
      .references(() => courseSections.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    title: text('title').notNull(),
    position: integer('position').notNull(),
    ...timestamps,
  },
  (t) => [index('course_chapters_section_idx').on(t.sectionId, t.position)],
);

export const documents = pgTable(
  'documents',
  {
    id: id(),
    courseId: uuid('course_id').references(() => courses.id, { onDelete: 'cascade' }),
    ownerId: uuid('owner_id').notNull(),
    kind: text('kind').notNull(), // notes | pdf | previous_questions | slides | link
    title: text('title').notNull(),
    year: integer('year'),
    storageBucket: text('storage_bucket'),
    storageKey: text('storage_key'),
    externalUrl: text('external_url'),
    mime: text('mime'),
    size: integer('size'),
    pages: integer('pages'),
    ...timestamps,
  },
  (t) => [index('documents_course_idx').on(t.courseId, t.kind)],
);

export const lessons = pgTable(
  'lessons',
  {
    id: id(),
    chapterId: uuid('chapter_id')
      .notNull()
      .references(() => courseChapters.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    title: text('title').notNull(),
    type: text('type').notNull(), // video | pdf | text | quiz | mock
    position: integer('position').notNull(),
    durationSec: integer('duration_sec').notNull().default(0),
    videoAssetId: uuid('video_asset_id').references(() => videoAssets.id, { onDelete: 'set null' }),
    documentId: uuid('document_id').references(() => documents.id, { onDelete: 'set null' }),
    mockTestId: uuid('mock_test_id'),
    body: text('body'),
    isPreview: boolean('is_preview').notNull().default(false),
    isImportant: boolean('is_important').notNull().default(false),
    ...timestamps,
  },
  (t) => [index('lessons_chapter_idx').on(t.chapterId, t.position), index('lessons_course_idx').on(t.courseId)],
);

export const courseRevisions = pgTable(
  'course_revisions',
  {
    id: id(),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    payloadJson: jsonb('payload_json').notNull(),
    status: text('status').notNull().default('pending'), // pending | approved | rejected
    reviewerId: uuid('reviewer_id'),
    notes: text('notes'),
    ...timestamps,
  },
  (t) => [index('course_revisions_course_idx').on(t.courseId, t.status)],
);

export const bundles = pgTable(
  'bundles',
  {
    id: id(),
    sellerId: uuid('seller_id').notNull(),
    title: text('title').notNull(),
    slug: text('slug').notNull(),
    description: text('description'),
    price: money('price').notNull(),
    compareAtPrice: money('compare_at_price'),
    status: text('status').notNull().default('published'),
    ...timestamps,
  },
  (t) => [uniqueIndex('bundles_slug_uq').on(t.slug)],
);

export const bundleItems = pgTable(
  'bundle_items',
  {
    id: id(),
    bundleId: uuid('bundle_id')
      .notNull()
      .references(() => bundles.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id')
      .notNull()
      .references(() => courses.id, { onDelete: 'cascade' }),
    ...timestamps,
  },
  (t) => [uniqueIndex('bundle_items_uq').on(t.bundleId, t.courseId)],
);
