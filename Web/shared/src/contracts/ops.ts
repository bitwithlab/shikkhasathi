import { z } from 'zod';
import { CursorQuery, IdParams, endpoint, page } from './common';
import { PAYOUT_STATUSES, REPORT_STATUSES, ROLES } from '../types';

// ---------------------------------------------------------------- payouts

export const Payout = z.object({ id: z.uuid(), amount: z.number().int(), method: z.string(), accountMasked: z.string().nullable(), status: z.enum(PAYOUT_STATUSES), reference: z.string().nullable(), note: z.string().nullable(), createdAt: z.string(), processedAt: z.string().nullable() });
export type Payout = z.infer<typeof Payout>;
export const AdminPayout = Payout.extend({ sellerId: z.uuid(), sellerName: z.string(), sellerSlug: z.string().nullable(), available: z.number().int() });

export const sellerOps = {
  payouts: endpoint({ method: 'GET', path: '/seller/payouts', summary: 'My payout requests', tag: 'seller', auth: 'seller', response: z.object({ items: z.array(Payout) }) }),
  requestPayout: endpoint({ method: 'POST', path: '/seller/payouts', summary: 'Request a payout of available balance (≥ minimum, one open request at a time)', tag: 'seller', auth: 'verified_seller', body: z.object({ amount: z.number().int().positive() }), response: Payout, status: 201 }),
  students: endpoint({
    method: 'GET',
    path: '/seller/students',
    summary: 'Students enrolled in my courses (progress, last active)',
    tag: 'seller',
    auth: 'seller',
    query: CursorQuery.extend({ courseId: z.uuid().optional(), q: z.string().max(80).optional() }),
    response: page(z.object({ enrollmentId: z.uuid(), studentName: z.string(), institutionShort: z.string().nullable(), courseId: z.uuid(), courseTitle: z.string(), progressPercent: z.number().int(), enrolledAt: z.string(), lastAccessedAt: z.string().nullable(), completed: z.boolean() })).extend({ total: z.number().int() }),
  }),
  analytics: endpoint({
    method: 'GET',
    path: '/seller/analytics',
    summary: 'Revenue, enrollments, completion and rating per course',
    tag: 'seller',
    auth: 'seller',
    query: z.object({ days: z.coerce.number().int().min(7).max(365).default(30) }),
    response: z.object({
      totals: z.object({ revenue: z.number().int(), enrollments: z.number().int(), students: z.number().int(), avgCompletion: z.number().int(), ratingAvg: z.number(), ratingCount: z.number().int(), refunds: z.number().int(), openQuestions: z.number().int() }),
      series: z.array(z.object({ date: z.string(), revenue: z.number().int(), enrollments: z.number().int() })),
      courses: z.array(z.object({ id: z.uuid(), title: z.string(), status: z.string(), enrollments: z.number().int(), revenue: z.number().int(), avgCompletion: z.number().int(), ratingAvg: z.number(), ratingCount: z.number().int() })),
    }),
  }),
};

// ---------------------------------------------------------------- admin

export const AdminUser = z.object({ id: z.uuid(), name: z.string(), email: z.string().nullable(), phone: z.string().nullable(), roles: z.array(z.enum(ROLES)), status: z.enum(['active', 'suspended']), createdAt: z.string(), lastActiveAt: z.string().nullable(), enrollments: z.number().int(), sellerStatus: z.string().nullable() });
export type AdminUser = z.infer<typeof AdminUser>;

export const REPORT_TARGETS = ['course', 'review', 'question', 'answer', 'user'] as const;
export const Report = z.object({ id: z.uuid(), targetType: z.enum(REPORT_TARGETS), targetId: z.uuid(), targetPreview: z.string(), targetLink: z.string().nullable(), targetHidden: z.boolean(), reason: z.string(), details: z.string().nullable(), reporterName: z.string(), status: z.enum(REPORT_STATUSES), resolution: z.string().nullable(), createdAt: z.string(), sameTargetCount: z.number().int() });
export type Report = z.infer<typeof Report>;

export const BroadcastBody = z.object({
  title: z.string().trim().min(3).max(120),
  body: z.string().trim().min(3).max(1000),
  link: z.string().trim().max(300).regex(/^\/[\w\-/?=&.%]*$/, 'সাইটের ভেতরের লিংক দিন, যেমন /explore').nullable().optional(),
  audience: z.enum(['all', 'students', 'sellers', 'institution']),
  institutionId: z.uuid().nullable().optional(),
  email: z.boolean().default(false),
});
export const Broadcast = z.object({ id: z.string(), title: z.string(), body: z.string(), audience: z.string(), recipients: z.number().int(), email: z.boolean(), createdAt: z.string(), actorName: z.string() });

export const EmailLog = z.object({ id: z.uuid(), toEmail: z.string(), template: z.string(), subject: z.string().nullable(), status: z.string(), provider: z.string().nullable(), error: z.string().nullable(), attempts: z.number().int(), createdAt: z.string(), sentAt: z.string().nullable() });
export const AuditEntry = z.object({ id: z.uuid(), actorName: z.string().nullable(), action: z.string(), entity: z.string(), entityId: z.string().nullable(), before: z.unknown(), after: z.unknown(), ip: z.string().nullable(), createdAt: z.string() });

export const AdminAnalytics = z.object({
  totals: z.object({ gmv: z.number().int(), platformRevenue: z.number().int(), orders: z.number().int(), refunds: z.number().int(), refundAmount: z.number().int(), newStudents: z.number().int(), newSellers: z.number().int(), enrollments: z.number().int(), classroomConnected: z.number().int(), activeStudents7d: z.number().int() }),
  queues: z.object({ pendingSellers: z.number().int(), pendingCourses: z.number().int(), pendingInstitutions: z.number().int(), openReports: z.number().int(), refundRequests: z.number().int(), payoutRequests: z.number().int() }),
  series: z.array(z.object({ date: z.string(), gmv: z.number().int(), orders: z.number().int(), signups: z.number().int() })),
  topCourses: z.array(z.object({ id: z.uuid(), title: z.string(), sellerName: z.string(), sales: z.number().int(), revenue: z.number().int() })),
  topSellers: z.array(z.object({ id: z.uuid(), name: z.string(), revenue: z.number().int(), students: z.number().int() })),
});
export type AdminAnalytics = z.infer<typeof AdminAnalytics>;

export const reports = {
  create: endpoint({ method: 'POST', path: '/reports', summary: 'Report a course, review, question, answer or user', tag: 'moderation', auth: 'user', body: z.object({ targetType: z.enum(REPORT_TARGETS), targetId: z.uuid(), reason: z.enum(['spam', 'abuse', 'copyright', 'misleading', 'guarantee_claim', 'other']), details: z.string().trim().max(1000).optional() }), response: z.object({ id: z.uuid(), duplicate: z.boolean() }), status: 201 }),
};

export const adminOps = {
  payouts: endpoint({ method: 'GET', path: '/admin/payouts', summary: 'Payout requests', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(PAYOUT_STATUSES).default('requested') }), response: page(AdminPayout) }),
  decidePayout: endpoint({ method: 'PATCH', path: '/admin/payouts/:id', summary: 'Approve / reject / mark paid (ledger debit on paid)', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ decision: z.enum(['approve', 'reject', 'paid']), reference: z.string().trim().max(100).optional(), note: z.string().trim().max(500).optional() }), response: AdminPayout }),
  users: endpoint({ method: 'GET', path: '/admin/users', summary: 'Users (search name/phone/email, role, status)', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ q: z.string().max(80).optional(), role: z.enum(ROLES).optional(), status: z.enum(['active', 'suspended']).optional() }), response: page(AdminUser) }),
  updateUser: endpoint({ method: 'PATCH', path: '/admin/users/:id', summary: 'Suspend / reactivate, change roles (audited)', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ status: z.enum(['active', 'suspended']).optional(), roles: z.array(z.enum(ROLES)).min(1).optional(), reason: z.string().trim().max(300).optional() }), response: AdminUser }),
  reports: endpoint({ method: 'GET', path: '/admin/reports', summary: 'Moderation queue', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(REPORT_STATUSES).default('open') }), response: page(Report) }),
  resolveReport: endpoint({ method: 'PATCH', path: '/admin/reports/:id', summary: 'Hide target / dismiss / mark reviewing (audited)', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ action: z.enum(['hide', 'restore', 'dismiss', 'reviewing']), resolution: z.string().trim().max(500).optional() }), response: Report }),
  broadcasts: endpoint({ method: 'GET', path: '/admin/broadcasts', summary: 'Sent broadcasts', tag: 'admin', auth: 'admin', response: z.object({ items: z.array(Broadcast) }) }),
  broadcast: endpoint({ method: 'POST', path: '/admin/broadcasts', summary: 'Send an announcement (in-app + push, optional email)', tag: 'admin', auth: 'admin', body: BroadcastBody, response: Broadcast, status: 201 }),
  broadcastPreview: endpoint({ method: 'POST', path: '/admin/broadcasts/preview', summary: 'Count recipients for an audience', tag: 'admin', auth: 'admin', body: BroadcastBody.pick({ audience: true, institutionId: true }), response: z.object({ recipients: z.number().int() }) }),
  emailLogs: endpoint({ method: 'GET', path: '/admin/email-logs', summary: 'Email delivery log (Brevo / SMTP / fake)', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.string().max(20).optional(), q: z.string().max(120).optional() }), response: page(EmailLog) }),
  audit: endpoint({ method: 'GET', path: '/admin/audit', summary: 'Audit log', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ entity: z.string().max(40).optional(), action: z.string().max(60).optional() }), response: page(AuditEntry) }),
  analytics: endpoint({ method: 'GET', path: '/admin/analytics', summary: 'Platform KPIs, queues, series, top courses/sellers', tag: 'admin', auth: 'admin', query: z.object({ days: z.coerce.number().int().min(7).max(365).default(30) }), response: AdminAnalytics }),
};
