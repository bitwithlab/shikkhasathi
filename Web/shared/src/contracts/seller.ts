import { z } from 'zod';
import { CursorQuery, Email, IdParams, Ok, endpoint, page } from './common';
import { ACCESS_TYPES, COURSE_STATUSES, COURSE_TYPES, DOCUMENT_KINDS, EXAM_TYPES, LESSON_TYPES, PAYOUT_METHODS, SELLER_VERIFICATION_METHODS, SELLER_VERIFICATION_STATUSES } from '../types';

// ---------------------------------------------------------------- application & verification

export const SellerProfile = z.object({
  userId: z.uuid(),
  slug: z.string(),
  displayName: z.string(),
  headline: z.string().nullable(),
  bio: z.string().nullable(),
  coverUrl: z.string().nullable(),
  institutionId: z.uuid().nullable(),
  institutionName: z.string().nullable(),
  verificationStatus: z.enum(SELLER_VERIFICATION_STATUSES),
  verificationMethod: z.enum(SELLER_VERIFICATION_METHODS).nullable(),
  verificationNote: z.string().nullable(),
  universityEmailVerified: z.boolean(),
  payoutMethod: z.enum(PAYOUT_METHODS).nullable(),
  payoutAccountMasked: z.string().nullable(),
  commissionRate: z.number().int().nullable(),
  verifiedAt: z.string().nullable(),
});
export type SellerProfile = z.infer<typeof SellerProfile>;

export const Slug = z
  .string()
  .trim()
  .toLowerCase()
  .regex(/^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$/, 'ছোট হাতের ইংরেজি অক্ষর, সংখ্যা ও - দিয়ে ৩–৪০ অক্ষর');

export const SellerApplyBody = z.object({
  displayName: z.string().trim().min(2).max(80),
  slug: Slug,
  headline: z.string().trim().max(120).optional(),
  bio: z.string().trim().max(2000).optional(),
  institutionId: z.uuid().nullable(),
  method: z.enum(SELLER_VERIFICATION_METHODS),
  evidence: z.object({
    universityEmail: Email.optional(),
    idCardKey: z.string().max(300).optional(),
    linkedinUrl: z.url().optional(),
    referenceName: z.string().max(120).optional(),
    referenceContact: z.string().max(120).optional(),
  }),
  payoutMethod: z.enum(PAYOUT_METHODS),
  payoutAccount: z.string().trim().min(6).max(60),
});

export const sellerApply = {
  profile: endpoint({ method: 'GET', path: '/seller/profile', summary: 'Own seller profile / application status (null if none)', tag: 'seller', auth: 'user', response: z.object({ profile: SellerProfile.nullable() }) }),
  apply: endpoint({ method: 'POST', path: '/seller/apply', summary: 'Apply / update application (adds seller role)', tag: 'seller', auth: 'user', body: SellerApplyBody, response: SellerProfile }),
  emailCode: endpoint({ method: 'POST', path: '/seller/verify-email/request', summary: 'Email a code to a university address', tag: 'seller', auth: 'user', body: z.object({ email: Email }), response: Ok }),
  emailConfirm: endpoint({ method: 'POST', path: '/seller/verify-email/confirm', summary: 'Confirm university email code', tag: 'seller', auth: 'user', body: z.object({ email: Email, code: z.string().regex(/^\d{6}$/) }), response: SellerProfile }),
  updateStorefront: endpoint({ method: 'PATCH', path: '/seller/storefront', summary: 'Storefront settings (slug, bio, cover)', tag: 'seller', auth: 'seller', body: z.object({ slug: Slug.optional(), displayName: z.string().trim().min(2).max(80).optional(), headline: z.string().trim().max(120).nullable().optional(), bio: z.string().trim().max(2000).nullable().optional(), coverUrl: z.url().nullable().optional() }), response: SellerProfile }),
};

export const AdminSellerItem = SellerProfile.extend({ name: z.string(), email: z.string().nullable(), phone: z.string().nullable(), evidence: z.record(z.string(), z.unknown()).nullable(), idCardUrl: z.string().nullable(), appliedAt: z.string(), courseCount: z.number().int() });

export const adminSellers = {
  list: endpoint({ method: 'GET', path: '/admin/sellers', summary: 'Seller verification queue', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(SELLER_VERIFICATION_STATUSES).default('pending') }), response: page(AdminSellerItem) }),
  decide: endpoint({ method: 'PATCH', path: '/admin/sellers/:id/verification', summary: 'Verify / reject seller (audit-logged, notifies + emails)', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ decision: z.enum(['verified', 'rejected']), note: z.string().max(500).optional(), commissionRate: z.number().int().min(0).max(50).optional() }), response: AdminSellerItem }),
};

// ---------------------------------------------------------------- courses (authoring)

export const TargetInput = z.object({
  isGeneral: z.boolean().default(false),
  institutionId: z.uuid().nullable().optional(),
  departmentId: z.uuid().nullable().optional(),
  programId: z.uuid().nullable().optional(),
  academicLevel: z.string().max(40).nullable().optional(),
  semester: z.number().int().min(1).max(16).nullable().optional(),
  subjectMasterId: z.uuid().nullable().optional(),
  courseCode: z.string().trim().max(30).nullable().optional(),
  batches: z.array(z.string().trim().min(1).max(12)).max(20).default([]),
  sections: z.array(z.string().trim().min(1).max(12)).max(20).default([]),
});
export const Target = TargetInput.extend({ id: z.uuid(), institutionShort: z.string().nullable(), departmentShort: z.string().nullable(), subjectName: z.string().nullable() });

export const LessonInput = z.object({
  id: z.uuid().optional(),
  title: z.string().trim().min(1).max(200),
  type: z.enum(LESSON_TYPES),
  durationSec: z.number().int().min(0).max(36_000).default(0),
  videoAssetId: z.uuid().nullable().optional(),
  documentId: z.uuid().nullable().optional(),
  body: z.string().max(50_000).nullable().optional(),
  isPreview: z.boolean().default(false),
  isImportant: z.boolean().default(false),
});
export const ChapterInput = z.object({ id: z.uuid().optional(), title: z.string().trim().min(1).max(200), lessons: z.array(LessonInput).max(100) });
export const SectionInput = z.object({ id: z.uuid().optional(), title: z.string().trim().min(1).max(200), chapters: z.array(ChapterInput).max(50) });
export const CurriculumBody = z.object({ sections: z.array(SectionInput).max(30) });

export const VideoAssetInfo = z.object({ id: z.uuid(), provider: z.string(), status: z.string(), durationSec: z.number().int(), title: z.string().nullable(), externalUrl: z.string().nullable() });
export const DocumentInfo = z.object({ id: z.uuid(), kind: z.enum(DOCUMENT_KINDS), title: z.string(), year: z.number().int().nullable(), mime: z.string().nullable(), size: z.number().int().nullable(), externalUrl: z.string().nullable(), hasFile: z.boolean() });

export const Lesson = LessonInput.extend({ id: z.uuid(), video: VideoAssetInfo.nullable(), document: DocumentInfo.nullable() });
export const Chapter = z.object({ id: z.uuid(), title: z.string(), lessons: z.array(Lesson) });
export const Section = z.object({ id: z.uuid(), title: z.string(), chapters: z.array(Chapter) });

export const CourseBasics = z.object({
  title: z.string().trim().min(5, 'শিরোনাম কমপক্ষে ৫ অক্ষর').max(160),
  shortDescription: z.string().trim().max(300).default(''),
  description: z.string().trim().max(10_000).default(''),
  learnings: z.array(z.string().trim().min(1).max(200)).max(15).default([]),
  notFor: z.array(z.string().trim().min(1).max(200)).max(10).default([]),
  faq: z.array(z.object({ q: z.string().trim().min(1).max(300), a: z.string().trim().min(1).max(2000) })).max(15).default([]),
  coverUrl: z.string().max(500).nullable().optional(),
  coverColor: z.string().regex(/^#[0-9a-fA-F]{6}$/).nullable().optional(),
  introVideoId: z.uuid().nullable().optional(),
  language: z.enum(['bn', 'en', 'mixed']).default('bn'),
  examType: z.enum(EXAM_TYPES).default('midterm'),
  courseType: z.enum(COURSE_TYPES).default('crash'),
});
export const CoursePricing = z.object({
  price: z.number().int().min(0).max(5_000_000), // paisa
  compareAtPrice: z.number().int().min(0).max(5_000_000).nullable().optional(),
  accessType: z.enum(ACCESS_TYPES).default('lifetime'),
  accessDays: z.number().int().min(1).max(3650).nullable().optional(),
  accessUntil: z.iso.datetime({ offset: true }).nullable().optional(),
});

export const SellerCourse = CourseBasics.extend({
  id: z.uuid(),
  slug: z.string(),
  status: z.enum(COURSE_STATUSES),
  reviewNote: z.string().nullable(),
  ...CoursePricing.shape,
  targets: z.array(Target),
  sections: z.array(Section),
  documents: z.array(DocumentInfo),
  lessonCount: z.number().int(),
  totalDurationSec: z.number().int(),
  updatedAt: z.string(),
  publishedAt: z.string().nullable(),
  submittedAt: z.string().nullable(),
  pendingRevision: z.boolean(),
});
export type SellerCourse = z.infer<typeof SellerCourse>;

export const SellerCourseListItem = z.object({ id: z.uuid(), slug: z.string(), title: z.string(), status: z.enum(COURSE_STATUSES), price: z.number().int(), lessonCount: z.number().int(), enrollmentCount: z.number().int(), ratingAvg: z.number(), ratingCount: z.number().int(), updatedAt: z.string(), coverColor: z.string().nullable(), courseCode: z.string().nullable() });

export const ChecklistItem = z.object({ key: z.string(), ok: z.boolean(), message: z.string() });

export const UploadBody = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('video'), title: z.string().trim().min(1).max(200), size: z.number().int().positive().max(8 * 1024 ** 3).optional() }),
  z.object({ kind: z.literal('video_link'), title: z.string().trim().min(1).max(200), url: z.url(), durationSec: z.number().int().min(0).max(36_000).default(0) }),
  z.object({ kind: z.literal('document'), courseId: z.uuid(), docKind: z.enum(DOCUMENT_KINDS), title: z.string().trim().min(1).max(200), year: z.number().int().min(1990).max(2100).nullable().optional(), mime: z.string().max(100), size: z.number().int().positive().max(100 * 1024 ** 2) }),
  z.object({ kind: z.literal('document_link'), courseId: z.uuid(), docKind: z.enum(DOCUMENT_KINDS), title: z.string().trim().min(1).max(200), year: z.number().int().min(1990).max(2100).nullable().optional(), url: z.url() }),
  z.object({ kind: z.literal('cover'), mime: z.enum(['image/jpeg', 'image/png', 'image/webp']), size: z.number().int().positive().max(5 * 1024 ** 2) }),
  z.object({ kind: z.literal('id_card'), mime: z.enum(['image/jpeg', 'image/png', 'image/webp', 'application/pdf']), size: z.number().int().positive().max(10 * 1024 ** 2) }),
]);
export const UploadInstructions = z.object({
  /** created row id (video asset / document), or null for cover/id card */
  id: z.uuid().nullable(),
  /** where the browser PUTs the file (signed); null for link kinds */
  uploadUrl: z.string().nullable(),
  method: z.enum(['PUT', 'POST', 'TUS']).nullable(),
  headers: z.record(z.string(), z.string()),
  /** storage key to reference later (cover / id card) */
  key: z.string().nullable(),
  /** public URL for covers */
  publicUrl: z.string().nullable(),
});

export const sellerCourses = {
  list: endpoint({ method: 'GET', path: '/seller/courses', summary: 'My courses', tag: 'seller', auth: 'seller', response: z.object({ items: z.array(SellerCourseListItem) }) }),
  create: endpoint({ method: 'POST', path: '/seller/courses', summary: 'Create a draft', tag: 'seller', auth: 'seller', body: z.object({ title: z.string().trim().min(5).max(160) }), response: SellerCourse, status: 201 }),
  get: endpoint({ method: 'GET', path: '/seller/courses/:id', summary: 'Editable course (basics, targets, curriculum, pricing)', tag: 'seller', auth: 'seller', params: IdParams, response: SellerCourse }),
  update: endpoint({ method: 'PATCH', path: '/seller/courses/:id', summary: 'Autosave basics / pricing', tag: 'seller', auth: 'seller', params: IdParams, body: CourseBasics.partial().extend(CoursePricing.partial().shape), response: SellerCourse }),
  targets: endpoint({ method: 'PUT', path: '/seller/courses/:id/targets', summary: 'Replace targets', tag: 'seller', auth: 'seller', params: IdParams, body: z.object({ targets: z.array(TargetInput).min(1).max(20) }), response: SellerCourse }),
  curriculum: endpoint({ method: 'PUT', path: '/seller/courses/:id/curriculum', summary: 'Replace section → chapter → lesson tree (keeps ids)', tag: 'seller', auth: 'seller', params: IdParams, body: CurriculumBody, response: SellerCourse }),
  checklist: endpoint({ method: 'GET', path: '/seller/courses/:id/checklist', summary: 'Validation before submit', tag: 'seller', auth: 'seller', params: IdParams, response: z.object({ items: z.array(ChecklistItem), canSubmit: z.boolean() }) }),
  submit: endpoint({ method: 'POST', path: '/seller/courses/:id/submit', summary: 'Send for review (verified sellers; declaration required)', tag: 'seller', auth: 'verified_seller', params: IdParams, body: z.object({ declaration: z.literal(true) }), response: SellerCourse }),
  archive: endpoint({ method: 'POST', path: '/seller/courses/:id/archive', summary: 'Unlist / archive a course', tag: 'seller', auth: 'seller', params: IdParams, body: z.object({ status: z.enum(['unlisted', 'archived', 'published']) }), response: SellerCourse }),
  remove: endpoint({ method: 'DELETE', path: '/seller/courses/:id', summary: 'Delete a draft', tag: 'seller', auth: 'seller', params: IdParams, response: Ok }),
  upload: endpoint({ method: 'POST', path: '/seller/uploads', summary: 'Create upload (video via provider, document/cover/id card via signed storage URL) or register a link', tag: 'seller', auth: 'user', body: UploadBody, response: UploadInstructions, status: 201 }),
  uploadComplete: endpoint({ method: 'POST', path: '/seller/uploads/:id/complete', summary: 'Mark a direct upload finished (documents)', tag: 'seller', auth: 'seller', params: IdParams, response: Ok }),
  proposeSubject: endpoint({ method: 'POST', path: '/subjects/propose', summary: 'Propose a new subject master (admin approves)', tag: 'seller', auth: 'seller', body: z.object({ name: z.string().trim().min(2).max(160), aliases: z.array(z.string().trim().max(80)).max(10).default([]) }), response: z.object({ id: z.uuid(), status: z.string() }), status: 201 }),
};

// ---------------------------------------------------------------- admin review queue

export const ReviewItem = z.object({
  id: z.uuid(),
  title: z.string(),
  sellerId: z.uuid(),
  sellerName: z.string(),
  sellerVerified: z.boolean(),
  status: z.enum(COURSE_STATUSES),
  submittedAt: z.string().nullable(),
  price: z.number().int(),
  lessonCount: z.number().int(),
  targets: z.array(z.string()),
  kind: z.enum(['new', 'revision', 'spot_check']),
});

export const adminReview = {
  queue: endpoint({ method: 'GET', path: '/admin/courses/review', summary: 'Course review queue (new, revisions, spot checks)', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(['in_review', 'changes_requested', 'rejected', 'published']).default('in_review') }), response: page(ReviewItem) }),
  detail: endpoint({ method: 'GET', path: '/admin/courses/:id', summary: 'Course detail for the review drawer', tag: 'admin', auth: 'admin', params: IdParams, response: SellerCourse.extend({ sellerName: z.string(), revision: z.record(z.string(), z.unknown()).nullable() }) }),
  decide: endpoint({
    method: 'POST',
    path: '/admin/courses/:id/decision',
    summary: 'Publish / request changes / reject (checklist, audit-logged, seller notified + emailed)',
    tag: 'admin',
    auth: 'admin',
    params: IdParams,
    body: z.object({ decision: z.enum(['publish', 'changes_requested', 'reject']), note: z.string().max(2000).optional(), checklist: z.object({ quality: z.boolean(), noGuaranteeClaims: z.boolean(), targeting: z.boolean(), copyright: z.boolean() }) }),
    response: ReviewItem,
  }),
};
