import { index, integer, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';

export const institutions = pgTable(
  'institutions',
  {
    id: id(),
    name: text('name').notNull(),
    nameBn: text('name_bn'),
    shortName: text('short_name').notNull(),
    slug: text('slug').notNull(),
    type: text('type').notNull(),
    city: text('city'),
    logoUrl: text('logo_url'),
    website: text('website'),
    aliases: text('aliases').array().notNull().default([]),
    status: text('status').notNull().default('approved'),
    mergedIntoId: uuid('merged_into_id'),
    ...timestamps,
  },
  (t) => [uniqueIndex('institutions_slug_uq').on(t.slug), index('institutions_status_idx').on(t.status, t.type)],
);

export const institutionRequests = pgTable(
  'institution_requests',
  {
    id: id(),
    requestedBy: uuid('requested_by').notNull(), // CORE users.id
    name: text('name').notNull(),
    type: text('type').notNull(),
    city: text('city'),
    note: text('note'),
    status: text('status').notNull().default('pending'),
    resolvedInstitutionId: uuid('resolved_institution_id'),
    reviewedBy: uuid('reviewed_by'),
    reviewedAt: ts('reviewed_at'),
    ...timestamps,
  },
  (t) => [index('institution_requests_status_idx').on(t.status, t.createdAt)],
);

export const departments = pgTable(
  'departments',
  {
    id: id(),
    institutionId: uuid('institution_id')
      .notNull()
      .references(() => institutions.id, { onDelete: 'cascade' }),
    name: text('name').notNull(),
    nameBn: text('name_bn'),
    shortName: text('short_name').notNull(),
    icon: text('icon'),
    position: integer('position').notNull().default(0),
    ...timestamps,
  },
  (t) => [uniqueIndex('departments_inst_short_uq').on(t.institutionId, t.shortName)],
);

export const programs = pgTable(
  'programs',
  {
    id: id(),
    departmentId: uuid('department_id')
      .notNull()
      .references(() => departments.id, { onDelete: 'cascade' }),
    name: text('name').notNull(),
    level: text('level').notNull().default('undergraduate'),
    semesters: integer('semesters').notNull().default(8),
    ...timestamps,
  },
  (t) => [index('programs_department_idx').on(t.departmentId)],
);

export const subjectMasters = pgTable(
  'subject_masters',
  {
    id: id(),
    name: text('name').notNull(),
    nameBn: text('name_bn'),
    slug: text('slug').notNull(),
    aliases: text('aliases').array().notNull().default([]),
    category: text('category').notNull().default('general'),
    status: text('status').notNull().default('approved'),
    proposedBy: uuid('proposed_by'),
    ...timestamps,
  },
  (t) => [uniqueIndex('subject_masters_slug_uq').on(t.slug)],
);

export const institutionSubjects = pgTable(
  'institution_subjects',
  {
    id: id(),
    institutionId: uuid('institution_id')
      .notNull()
      .references(() => institutions.id, { onDelete: 'cascade' }),
    departmentId: uuid('department_id').references(() => departments.id, { onDelete: 'set null' }),
    programId: uuid('program_id').references(() => programs.id, { onDelete: 'set null' }),
    semester: integer('semester'),
    subjectMasterId: uuid('subject_master_id')
      .notNull()
      .references(() => subjectMasters.id),
    courseCode: text('course_code'),
    credits: integer('credits'),
    ...timestamps,
  },
  (t) => [
    index('institution_subjects_lookup_idx').on(t.institutionId, t.departmentId, t.semester),
    index('institution_subjects_code_idx').on(t.courseCode),
  ],
);
