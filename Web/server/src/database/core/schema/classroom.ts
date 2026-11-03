import { boolean, index, integer, jsonb, numeric, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';
import { users } from './identity';

// External cache of Google Classroom data — never mixed with marketplace tables (spec §7.6).

export const googleConnections = pgTable(
  'google_connections',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    googleUserId: text('google_user_id').notNull(),
    email: text('email').notNull(),
    refreshTokenEnc: text('refresh_token_enc').notNull(),
    accessTokenEnc: text('access_token_enc'),
    accessTokenExpiresAt: ts('access_token_expires_at'),
    scopes: text('scopes').array().notNull(),
    status: text('status').notNull().default('active'),
    lastFullSyncAt: ts('last_full_sync_at'),
    lastSyncAt: ts('last_sync_at'),
    lastManualSyncAt: ts('last_manual_sync_at'),
    nextSyncAllowedAt: ts('next_sync_allowed_at'),
    errorCode: text('error_code'),
    errorMessage: text('error_message'),
    /** true when data comes from the fake Classroom adapter (dev/demo). */
    isFake: boolean('is_fake').notNull().default(false),
    ...timestamps,
  },
  (t) => [uniqueIndex('google_connections_user_uq').on(t.userId), index('google_connections_status_idx').on(t.status, t.lastSyncAt)],
);

export const classroomCourses = pgTable(
  'classroom_courses',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    gcCourseId: text('gc_course_id').notNull(),
    name: text('name').notNull(),
    section: text('section'),
    room: text('room'),
    descriptionHeading: text('description_heading'),
    teacherNames: text('teacher_names').array().notNull().default([]),
    alternateLink: text('alternate_link'),
    color: text('color').notNull(),
    state: text('state').notNull().default('ACTIVE'),
    updatedAtGc: ts('updated_at_gc'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_courses_uq').on(t.userId, t.gcCourseId)],
);

export const classroomCoursework = pgTable(
  'classroom_coursework',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    gcCourseId: text('gc_course_id').notNull(),
    gcCourseworkId: text('gc_coursework_id').notNull(),
    title: text('title').notNull(),
    description: text('description'),
    workType: text('work_type').notNull().default('ASSIGNMENT'),
    dueAt: ts('due_at'),
    maxPoints: numeric('max_points'),
    materialsJson: jsonb('materials_json').$type<unknown[]>().notNull().default([]),
    alternateLink: text('alternate_link'),
    state: text('state').notNull().default('PUBLISHED'),
    createdAtGc: ts('created_at_gc'),
    updatedAtGc: ts('updated_at_gc'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_coursework_uq').on(t.userId, t.gcCourseworkId), index('classroom_coursework_due_idx').on(t.userId, t.dueAt)],
);

export const classroomSubmissions = pgTable(
  'classroom_submissions',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    gcCourseId: text('gc_course_id').notNull(),
    gcCourseworkId: text('gc_coursework_id').notNull(),
    gcSubmissionId: text('gc_submission_id').notNull(),
    state: text('state').notNull(),
    late: boolean('late').notNull().default(false),
    assignedGrade: numeric('assigned_grade'),
    alternateLink: text('alternate_link'),
    updatedAtGc: ts('updated_at_gc'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_submissions_uq').on(t.userId, t.gcCourseworkId)],
);

export const classroomMaterials = pgTable(
  'classroom_materials',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    gcCourseId: text('gc_course_id').notNull(),
    gcMaterialId: text('gc_material_id').notNull(),
    title: text('title').notNull(),
    description: text('description'),
    materialsJson: jsonb('materials_json').$type<unknown[]>().notNull().default([]),
    alternateLink: text('alternate_link'),
    createdAtGc: ts('created_at_gc'),
    updatedAtGc: ts('updated_at_gc'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_materials_uq').on(t.userId, t.gcMaterialId)],
);

export const classroomAnnouncements = pgTable(
  'classroom_announcements',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    gcCourseId: text('gc_course_id').notNull(),
    gcAnnouncementId: text('gc_announcement_id').notNull(),
    text: text('text').notNull(),
    authorName: text('author_name'),
    materialsJson: jsonb('materials_json').$type<unknown[]>().notNull().default([]),
    alternateLink: text('alternate_link'),
    createdAtGc: ts('created_at_gc'),
    updatedAtGc: ts('updated_at_gc'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_announcements_uq').on(t.userId, t.gcAnnouncementId)],
);

export const classroomFeedItems = pgTable(
  'classroom_feed_items',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    kind: text('kind').notNull(), // assignment | material | announcement | graded
    gcCourseId: text('gc_course_id').notNull(),
    /** gc id of the coursework/material/announcement */
    refId: text('ref_id').notNull(),
    title: text('title').notNull(),
    occurredAt: ts('occurred_at').notNull(),
    readAt: ts('read_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_feed_uq').on(t.userId, t.kind, t.refId), index('classroom_feed_user_idx').on(t.userId, t.occurredAt)],
);

export const classroomPushRegistrations = pgTable(
  'classroom_push_registrations',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    feedType: text('feed_type').notNull(), // COURSE_WORK_CHANGES | COURSE_ROSTER_CHANGES
    gcCourseId: text('gc_course_id'),
    registrationId: text('registration_id').notNull(),
    expiresAt: ts('expires_at').notNull(),
    ...timestamps,
  },
  (t) => [uniqueIndex('classroom_push_reg_uq').on(t.registrationId), index('classroom_push_reg_exp_idx').on(t.expiresAt)],
);

export const classroomSyncRuns = pgTable(
  'classroom_sync_runs',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    kind: text('kind').notNull(), // full | incremental
    trigger: text('trigger').notNull(), // connect | push | poll | manual
    startedAt: ts('started_at').notNull().defaultNow(),
    finishedAt: ts('finished_at'),
    ok: boolean('ok'),
    changes: integer('changes').notNull().default(0),
    error: text('error'),
    ...timestamps,
  },
  (t) => [index('classroom_sync_runs_user_idx').on(t.userId, t.startedAt)],
);
