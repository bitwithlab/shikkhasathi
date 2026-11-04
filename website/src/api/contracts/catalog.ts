import { z } from 'zod';
import { CursorQuery, IdParams, Ok, SlugParams, endpoint, page, patchOf } from './common';
import { AcademicSummary, Me } from './auth';
import { EXAM_TYPES, INSTITUTION_STATUSES, INSTITUTION_TYPES } from '@/types';

export const Institution = z.object({
  id: z.uuid(),
  name: z.string(),
  nameBn: z.string().nullable(),
  shortName: z.string(),
  slug: z.string(),
  type: z.enum(INSTITUTION_TYPES),
  city: z.string().nullable(),
  logoUrl: z.string().nullable(),
  courseCount: z.number().int(),
  teacherCount: z.number().int(),
});
export type Institution = z.infer<typeof Institution>;

export const Department = z.object({
  id: z.uuid(),
  institutionId: z.uuid(),
  name: z.string(),
  nameBn: z.string().nullable(),
  shortName: z.string(),
  icon: z.string().nullable(),
});
export const Program = z.object({ id: z.uuid(), departmentId: z.uuid(), name: z.string(), level: z.string(), semesters: z.number().int() });

export const Subject = z.object({
  id: z.uuid(),
  name: z.string(),
  nameBn: z.string().nullable(),
  slug: z.string(),
  aliases: z.array(z.string()),
  category: z.string(),
  courseCount: z.number().int(),
});
export type Subject = z.infer<typeof Subject>;

export const InstitutionSubject = z.object({
  id: z.uuid(),
  subjectId: z.uuid(),
  subjectName: z.string(),
  subjectSlug: z.string(),
  departmentId: z.uuid().nullable(),
  semester: z.number().int().nullable(),
  courseCode: z.string().nullable(),
  courseCount: z.number().int(),
});

export const InstitutionDetail = Institution.extend({
  website: z.string().nullable(),
  aliases: z.array(z.string()),
  departments: z.array(Department.extend({ programs: z.array(Program), subjectCount: z.number().int() })),
  subjects: z.array(InstitutionSubject),
});

export const InstitutionsQuery = CursorQuery.extend({
  q: z.string().trim().max(100).optional(),
  type: z.enum(INSTITUTION_TYPES).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

export const InstitutionRequestBody = z.object({
  name: z.string().trim().min(3, 'প্রতিষ্ঠানের পুরো নাম লিখুন').max(160),
  type: z.enum(INSTITUTION_TYPES),
  city: z.string().trim().max(80).optional(),
  note: z.string().trim().max(500).optional(),
});

export const SubjectDetail = Subject.extend({
  offerings: z.array(z.object({ institutionId: z.uuid(), institutionName: z.string(), institutionShort: z.string(), institutionSlug: z.string(), courseCode: z.string().nullable(), semester: z.number().int().nullable() })),
});

export const AcademicProfileBody = z.object({
  institutionId: z.uuid().nullable(),
  departmentId: z.uuid().nullable().optional(),
  programId: z.uuid().nullable().optional(),
  semester: z.number().int().min(1).max(12).nullable().optional(),
  batch: z.string().trim().max(20).nullable().optional(),
  section: z.string().trim().max(20).nullable().optional(),
});

export const NextExamInput = z.object({
  subjectId: z.uuid().nullable().optional(),
  subjectName: z.string().trim().min(2).max(120),
  courseCode: z.string().trim().max(30).nullable().optional(),
  examType: z.enum(EXAM_TYPES),
  examDate: z.iso.datetime({ offset: true }),
});

export const OnboardingBody = z.object({
  academic: AcademicProfileBody.optional(),
  nextExam: NextExamInput.nullable().optional(),
});

export const catalog = {
  institutions: endpoint({ method: 'GET', path: '/institutions', summary: 'Approved institutions (search, type filter)', tag: 'catalog', auth: 'public', query: InstitutionsQuery, response: page(Institution) }),
  institution: endpoint({ method: 'GET', path: '/institutions/:slug', summary: 'Institution detail with departments, programs, subjects', tag: 'catalog', auth: 'public', params: SlugParams, response: InstitutionDetail }),
  departments: endpoint({ method: 'GET', path: '/departments', summary: 'Departments of an institution', tag: 'catalog', auth: 'public', query: z.object({ institutionId: z.uuid() }), response: z.object({ items: z.array(Department) }) }),
  programs: endpoint({ method: 'GET', path: '/programs', summary: 'Programs of a department', tag: 'catalog', auth: 'public', query: z.object({ departmentId: z.uuid() }), response: z.object({ items: z.array(Program) }) }),
  subjects: endpoint({ method: 'GET', path: '/subjects', summary: 'Subject master list', tag: 'catalog', auth: 'public', query: z.object({ q: z.string().trim().max(100).optional(), institutionId: z.uuid().optional(), departmentId: z.uuid().optional(), semester: z.coerce.number().int().optional() }), response: z.object({ items: z.array(Subject.extend({ courseCode: z.string().nullable().optional() })) }) }),
  subject: endpoint({ method: 'GET', path: '/subjects/:slug', summary: 'Subject detail', tag: 'catalog', auth: 'public', params: SlugParams, response: SubjectDetail }),
  requestInstitution: endpoint({ method: 'POST', path: '/institution-requests', summary: 'Request a missing institution', tag: 'catalog', auth: 'user', body: InstitutionRequestBody, response: z.object({ id: z.uuid(), status: z.string() }), status: 201 }),
  academicProfile: endpoint({ method: 'PUT', path: '/me/academic-profile', summary: 'Set university / department / semester / batch / section', tag: 'profile', auth: 'user', body: AcademicProfileBody, response: Me }),
  completeOnboarding: endpoint({ method: 'POST', path: '/me/onboarding', summary: 'Finish onboarding (optional academic profile + next exam)', tag: 'profile', auth: 'user', body: OnboardingBody, response: Me }),
};

// ---------------------------------------------------------------- admin catalog

export const InstitutionRequestItem = z.object({
  id: z.uuid(),
  name: z.string(),
  type: z.enum(INSTITUTION_TYPES),
  city: z.string().nullable(),
  note: z.string().nullable(),
  status: z.string(),
  requestedBy: z.object({ id: z.uuid(), name: z.string() }).nullable(),
  similar: z.array(z.object({ id: z.uuid(), name: z.string(), shortName: z.string() })),
  createdAt: z.string(),
});

export const InstitutionRequestDecision = z.discriminatedUnion('action', [
  z.object({ action: z.literal('approve'), name: z.string().trim().min(3).max(160).optional(), shortName: z.string().trim().min(2).max(20), slug: z.string().regex(/^[a-z0-9-]+$/).optional(), city: z.string().max(80).optional() }),
  z.object({ action: z.literal('merge'), institutionId: z.uuid() }),
  z.object({ action: z.literal('reject'), note: z.string().max(300).optional() }),
]);

export const AdminInstitution = Institution.extend({ status: z.enum(INSTITUTION_STATUSES), aliases: z.array(z.string()), website: z.string().nullable(), nameBn: z.string().nullable() });
export const InstitutionUpsert = z.object({
  name: z.string().trim().min(3).max(160),
  nameBn: z.string().trim().max(160).nullable().optional(),
  shortName: z.string().trim().min(2).max(20),
  slug: z.string().regex(/^[a-z0-9-]+$/, 'ছোট হাতের ইংরেজি, সংখ্যা ও - ব্যবহার করুন'),
  type: z.enum(INSTITUTION_TYPES),
  city: z.string().trim().max(80).nullable().optional(),
  website: z.url().nullable().optional(),
  logoUrl: z.url().nullable().optional(),
  aliases: z.array(z.string().trim().min(1).max(80)).max(20).default([]),
  status: z.enum(INSTITUTION_STATUSES).optional(),
});
export const DepartmentUpsert = z.object({ institutionId: z.uuid(), name: z.string().trim().min(2).max(160), nameBn: z.string().trim().max(160).nullable().optional(), shortName: z.string().trim().min(2).max(20), icon: z.string().max(40).nullable().optional() });
export const ProgramUpsert = z.object({ departmentId: z.uuid(), name: z.string().trim().min(2).max(160), level: z.string().max(40).default('undergraduate'), semesters: z.number().int().min(1).max(16).default(8) });
export const SubjectUpsert = z.object({ name: z.string().trim().min(2).max(160), nameBn: z.string().trim().max(160).nullable().optional(), slug: z.string().regex(/^[a-z0-9-]+$/), aliases: z.array(z.string().trim().min(1).max(80)).max(30).default([]), category: z.string().max(40).default('general') });
export const InstitutionSubjectUpsert = z.object({ institutionId: z.uuid(), departmentId: z.uuid().nullable().optional(), programId: z.uuid().nullable().optional(), semester: z.number().int().min(1).max(16).nullable().optional(), subjectMasterId: z.uuid(), courseCode: z.string().trim().max(30).nullable().optional() });

export const adminCatalog = {
  institutionRequests: endpoint({ method: 'GET', path: '/admin/institution-requests', summary: 'Institution request queue', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(['pending', 'approved', 'rejected', 'merged']).default('pending') }), response: page(InstitutionRequestItem) }),
  decideInstitutionRequest: endpoint({ method: 'PATCH', path: '/admin/institution-requests/:id', summary: 'Approve / merge / reject a request', tag: 'admin', auth: 'admin', params: IdParams, body: InstitutionRequestDecision, response: Ok }),
  institutions: endpoint({ method: 'GET', path: '/admin/catalog/institutions', summary: 'All institutions incl. pending/merged', tag: 'admin', auth: 'admin', query: z.object({ q: z.string().optional(), status: z.enum(INSTITUTION_STATUSES).optional() }), response: z.object({ items: z.array(AdminInstitution) }) }),
  createInstitution: endpoint({ method: 'POST', path: '/admin/catalog/institutions', summary: 'Create institution', tag: 'admin', auth: 'admin', body: InstitutionUpsert, response: AdminInstitution, status: 201 }),
  updateInstitution: endpoint({ method: 'PATCH', path: '/admin/catalog/institutions/:id', summary: 'Update institution (aliases, logo, status)', tag: 'admin', auth: 'admin', params: IdParams, body: patchOf(InstitutionUpsert), response: AdminInstitution }),
  mergeInstitution: endpoint({ method: 'POST', path: '/admin/catalog/institutions/:id/merge', summary: 'Merge duplicate into target', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ intoId: z.uuid() }), response: Ok }),
  createDepartment: endpoint({ method: 'POST', path: '/admin/catalog/departments', summary: 'Create department', tag: 'admin', auth: 'admin', body: DepartmentUpsert, response: Department, status: 201 }),
  updateDepartment: endpoint({ method: 'PATCH', path: '/admin/catalog/departments/:id', summary: 'Update department', tag: 'admin', auth: 'admin', params: IdParams, body: patchOf(DepartmentUpsert), response: Department }),
  deleteDepartment: endpoint({ method: 'DELETE', path: '/admin/catalog/departments/:id', summary: 'Delete department', tag: 'admin', auth: 'admin', params: IdParams, response: Ok }),
  createProgram: endpoint({ method: 'POST', path: '/admin/catalog/programs', summary: 'Create program', tag: 'admin', auth: 'admin', body: ProgramUpsert, response: Program, status: 201 }),
  deleteProgram: endpoint({ method: 'DELETE', path: '/admin/catalog/programs/:id', summary: 'Delete program', tag: 'admin', auth: 'admin', params: IdParams, response: Ok }),
  subjects: endpoint({ method: 'GET', path: '/admin/catalog/subjects', summary: 'Subject master incl. proposed', tag: 'admin', auth: 'admin', query: z.object({ q: z.string().optional(), status: z.enum(['approved', 'proposed', 'rejected']).optional() }), response: z.object({ items: z.array(Subject.extend({ status: z.string() })) }) }),
  createSubject: endpoint({ method: 'POST', path: '/admin/catalog/subjects', summary: 'Create subject master', tag: 'admin', auth: 'admin', body: SubjectUpsert, response: Subject, status: 201 }),
  updateSubject: endpoint({ method: 'PATCH', path: '/admin/catalog/subjects/:id', summary: 'Update / approve subject', tag: 'admin', auth: 'admin', params: IdParams, body: patchOf(SubjectUpsert).extend({ status: z.enum(['approved', 'proposed', 'rejected']).optional() }), response: Subject }),
  createInstitutionSubject: endpoint({ method: 'POST', path: '/admin/catalog/institution-subjects', summary: 'Map subject → institution/dept/semester/course code', tag: 'admin', auth: 'admin', body: InstitutionSubjectUpsert, response: z.object({ id: z.uuid() }), status: 201 }),
  deleteInstitutionSubject: endpoint({ method: 'DELETE', path: '/admin/catalog/institution-subjects/:id', summary: 'Remove mapping', tag: 'admin', auth: 'admin', params: IdParams, response: Ok }),
};

export { AcademicSummary };
