import { and, count, desc, eq, gte, ilike, inArray, isNull, lt, or, sql, type SQL } from 'drizzle-orm';
import type { AdminAnalytics, AdminUser, Report } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { answers, auditLogs, emailLogs, enrollments, googleConnections, ledgerEntries, orderItems, orders, payouts, questions, refunds, reports, reviews, sellerProfiles, studentProfiles, users } from '../../database/core/schema';
import { courses, institutionRequests, institutions } from '../../database/content/schema';
import { badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { audit } from '../../common/audit';
import { decodeCursor, toPage } from '../../common/pagination';
import { redis } from '../../common/redis';
import { notify } from '../notifications/notifications.service';
import { authRepo } from '../auth/auth.repository';
import { balance } from '../orders/money.service';
import { ledgerAppend } from '../orders/orders.service';
import { shortName } from '../learning/learning.service';

const dhakaDay = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);
const since = (days: number) => new Date(Date.now() - days * 86_400_000);

// ---------------------------------------------------------------- payouts

type PayoutRow = typeof payouts.$inferSelect;
const toPayout = (p: PayoutRow) => ({ id: p.id, amount: p.amount, method: p.method, accountMasked: p.accountMasked, status: p.status as 'requested', reference: p.reference, note: p.note, createdAt: p.createdAt.toISOString(), processedAt: p.processedAt?.toISOString() ?? null });

export async function myPayouts(sellerId: string) {
  const rows = await coreDb.select().from(payouts).where(and(eq(payouts.sellerId, sellerId), isNull(payouts.deletedAt))).orderBy(desc(payouts.createdAt)).limit(100);
  return { items: rows.map(toPayout) };
}

export async function requestPayout(sellerId: string, amount: number) {
  const [sp] = await coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.userId, sellerId));
  if (!sp?.payoutMethod || !sp.payoutAccountMasked) throw badRequest('আগে প্রোফাইলে পেআউট নম্বর/অ্যাকাউন্ট যোগ করুন।');
  const [open] = await coreDb.select({ id: payouts.id }).from(payouts).where(and(eq(payouts.sellerId, sellerId), inArray(payouts.status, ['requested', 'approved']), isNull(payouts.deletedAt)));
  if (open) throw conflict('একটি পেআউট অনুরোধ আগে থেকেই প্রক্রিয়াধীন আছে।');
  const b = await balance(sellerId);
  if (amount < b.minPayout) throw badRequest(`সর্বনিম্ন পেআউট ৳${b.minPayout / 100}।`);
  if (amount > b.available) throw badRequest(`উত্তোলনযোগ্য ব্যালান্স ৳${b.available / 100}।`);
  const [p] = await coreDb.insert(payouts).values({ sellerId, amount, method: sp.payoutMethod, accountMasked: sp.payoutAccountMasked, status: 'requested' }).returning();
  await audit({ actorId: sellerId, action: 'payout.request', entity: 'payouts', entityId: p!.id, after: { amount } });
  return toPayout(p!);
}

async function adminPayout(p: PayoutRow) {
  const [sp] = await coreDb.select({ name: sellerProfiles.displayName, slug: sellerProfiles.slug }).from(sellerProfiles).where(eq(sellerProfiles.userId, p.sellerId));
  return { ...toPayout(p), sellerId: p.sellerId, sellerName: sp?.name ?? '', sellerSlug: sp?.slug ?? null, available: (await balance(p.sellerId)).available };
}

export async function adminPayouts(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(payouts.status, q.status), isNull(payouts.deletedAt)];
  if (cur) conds.push(lt(payouts.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(payouts).where(and(...conds)).orderBy(desc(payouts.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await Promise.all(pg.items.map(adminPayout)), nextCursor: pg.nextCursor };
}

export async function decidePayout(actorId: string, id: string, d: { decision: 'approve' | 'reject' | 'paid'; reference?: string; note?: string }) {
  const [p] = await coreDb.select().from(payouts).where(eq(payouts.id, id));
  if (!p) throw notFound();
  const allowed: Record<string, string[]> = { approve: ['requested'], reject: ['requested', 'approved'], paid: ['requested', 'approved'] };
  if (!allowed[d.decision]!.includes(p.status)) throw conflict('এই অবস্থায় এই সিদ্ধান্ত নেওয়া যায় না।');
  if (d.decision === 'paid' && !d.reference) throw badRequest('পাঠানোর রেফারেন্স (bKash TrxID / ব্যাংক রেফারেন্স) দিন।');
  const status = d.decision === 'approve' ? 'approved' : d.decision === 'reject' ? 'rejected' : 'paid';
  const [u] = await coreDb.update(payouts).set({ status, reference: d.reference ?? p.reference, note: d.note ?? p.note, processedBy: actorId, processedAt: new Date() }).where(and(eq(payouts.id, id), eq(payouts.status, p.status))).returning();
  if (!u) throw conflict('অন্য কেউ এটি আপডেট করেছেন — রিফ্রেশ করুন।');
  if (status === 'paid') {
    await ledgerAppend(p.sellerId, { type: 'payout', amount: -p.amount, payoutId: p.id, description: `Payout ${d.reference}`, availableAt: new Date(), dedupeKey: `payout:${p.id}` });
    await notify({ userId: p.sellerId, type: 'seller.payout_paid', ref: p.id, title: 'পেআউট পাঠানো হয়েছে', body: `৳${p.amount / 100} · ${p.method} ${p.accountMasked ?? ''}`, link: '/teach/earnings', email: { template: 'payout_paid', data: { amount: p.amount, method: p.method, account: p.accountMasked ?? '', reference: d.reference ?? '' } } });
  } else if (status === 'rejected') {
    await notify({ userId: p.sellerId, type: 'seller.payout_paid', ref: `rejected:${p.id}`, title: 'পেআউট অনুরোধ বাতিল হয়েছে', body: d.note ?? 'বিস্তারিত জানতে সাপোর্টে যোগাযোগ করুন।', link: '/teach/earnings' });
  }
  await audit({ actorId, action: `payout.${d.decision}`, entity: 'payouts', entityId: id, before: { status: p.status }, after: { status, reference: d.reference } });
  return adminPayout(u);
}

// ---------------------------------------------------------------- users

async function toAdminUsers(rows: (typeof users.$inferSelect)[]): Promise<AdminUser[]> {
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id);
  const [enr, sps] = await Promise.all([
    coreDb.select({ id: enrollments.studentId, n: sql<number>`count(*)::int` }).from(enrollments).where(inArray(enrollments.studentId, ids)).groupBy(enrollments.studentId),
    coreDb.select({ id: sellerProfiles.userId, s: sellerProfiles.verificationStatus }).from(sellerProfiles).where(inArray(sellerProfiles.userId, ids)),
  ]);
  return rows.map((u) => ({ id: u.id, name: u.name, email: u.email, phone: u.phone, roles: u.roles as AdminUser['roles'], status: u.status === 'suspended' ? 'suspended' : 'active', createdAt: u.createdAt.toISOString(), lastActiveAt: u.lastActiveAt?.toISOString() ?? null, enrollments: enr.find((e) => e.id === u.id)?.n ?? 0, sellerStatus: sps.find((s) => s.id === u.id)?.s ?? null }));
}

export async function adminUsers(q: { q?: string; role?: string; status?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [isNull(users.deletedAt)];
  if (q.q) {
    const like = `%${q.q.replace(/[%_]/g, '')}%`;
    conds.push(or(ilike(users.name, like), ilike(users.email, like), ilike(users.phone, like))!);
  }
  if (q.role) conds.push(sql`${q.role} = any(${users.roles})`);
  if (q.status) conds.push(eq(users.status, q.status));
  if (cur) conds.push(lt(users.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(users).where(and(...conds)).orderBy(desc(users.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await toAdminUsers(pg.items), nextCursor: pg.nextCursor };
}

export const SUSPEND_KEY = (id: string) => `susp:${id}`;

export async function updateUser(actorId: string, id: string, d: { status?: 'active' | 'suspended'; roles?: string[]; reason?: string }) {
  const [u] = await coreDb.select().from(users).where(eq(users.id, id));
  if (!u) throw notFound();
  if (id === actorId && (d.status === 'suspended' || (d.roles && !d.roles.includes('admin')))) throw forbidden('নিজের অ্যাডমিন অ্যাক্সেস বা অ্যাকাউন্ট বন্ধ করা যায় না।');
  const set: Partial<typeof users.$inferInsert> = { updatedAt: new Date() };
  if (d.status) set.status = d.status;
  if (d.roles) set.roles = [...new Set(d.roles.includes('student') ? d.roles : ['student', ...d.roles])];
  const [row] = await coreDb.update(users).set(set).where(eq(users.id, id)).returning();
  if (d.status === 'suspended') {
    const ss = await authRepo.activeSessions(id);
    await Promise.all(ss.map((s) => authRepo.revokeFamily(s.familyId)));
    await redis().set(SUSPEND_KEY(id), '1', 'EX', 20 * 60); // outlives any access token (15 min)
  } else if (d.status === 'active') await redis().del(SUSPEND_KEY(id));
  if (d.roles) {
    // roles live in the access token — end sessions so the next login carries the new roles
    const ss = await authRepo.activeSessions(id);
    await Promise.all(ss.map((s) => authRepo.revokeFamily(s.familyId)));
  }
  await audit({ actorId, action: d.status ? `user.${d.status === 'suspended' ? 'suspend' : 'reactivate'}` : 'user.roles', entity: 'users', entityId: id, before: { status: u.status, roles: u.roles }, after: { status: row!.status, roles: row!.roles, reason: d.reason } });
  return (await toAdminUsers([row!]))[0]!;
}

// ---------------------------------------------------------------- reports / moderation

export async function createReport(userId: string, b: { targetType: Report['targetType']; targetId: string; reason: string; details?: string }) {
  await target(b.targetType, b.targetId); // 404 when the target doesn't exist
  const [dup] = await coreDb.select({ id: reports.id }).from(reports).where(and(eq(reports.reporterId, userId), eq(reports.targetType, b.targetType), eq(reports.targetId, b.targetId), inArray(reports.status, ['open', 'reviewing'])));
  if (dup) return { id: dup.id, duplicate: true };
  const [r] = await coreDb.insert(reports).values({ reporterId: userId, targetType: b.targetType, targetId: b.targetId, reason: b.reason, details: b.details ?? null }).returning({ id: reports.id });
  return { id: r!.id, duplicate: false };
}

/** Resolve a report target across both databases: preview text, link, hidden flag. */
async function target(type: Report['targetType'], id: string) {
  if (type === 'course') {
    const [c] = await contentDb.select({ title: courses.title, slug: courses.slug, status: courses.status }).from(courses).where(eq(courses.id, id));
    if (!c) throw notFound('রিপোর্ট করা বিষয়টি পাওয়া যায়নি।');
    return { preview: c.title, link: `/c/${c.slug}`, hidden: c.status === 'unlisted' };
  }
  if (type === 'review') {
    const [r] = await coreDb.select().from(reviews).where(eq(reviews.id, id));
    if (!r) throw notFound('রিপোর্ট করা বিষয়টি পাওয়া যায়নি।');
    const [c] = await contentDb.select({ slug: courses.slug }).from(courses).where(eq(courses.id, r.courseId));
    return { preview: `${'★'.repeat(r.rating)} ${r.body ?? ''}`.trim(), link: c ? `/c/${c.slug}#reviews` : null, hidden: r.status === 'hidden' };
  }
  if (type === 'question') {
    const [q] = await coreDb.select().from(questions).where(eq(questions.id, id));
    if (!q) throw notFound('রিপোর্ট করা বিষয়টি পাওয়া যায়নি।');
    return { preview: q.body, link: `/learn/${q.courseId}/${q.lessonId}?tab=qa`, hidden: q.status === 'hidden' };
  }
  if (type === 'answer') {
    const [a] = await coreDb.select().from(answers).where(eq(answers.id, id));
    if (!a) throw notFound('রিপোর্ট করা বিষয়টি পাওয়া যায়নি।');
    return { preview: a.body, link: null, hidden: a.status === 'hidden' };
  }
  const [u] = await coreDb.select({ name: users.name, status: users.status }).from(users).where(eq(users.id, id));
  if (!u) throw notFound('রিপোর্ট করা বিষয়টি পাওয়া যায়নি।');
  return { preview: u.name, link: null, hidden: u.status === 'suspended' };
}

async function toReport(r: typeof reports.$inferSelect): Promise<Report> {
  const [[rep], [same], tg] = await Promise.all([
    coreDb.select({ name: users.name }).from(users).where(eq(users.id, r.reporterId)),
    coreDb.select({ n: count() }).from(reports).where(and(eq(reports.targetType, r.targetType), eq(reports.targetId, r.targetId))),
    target(r.targetType as Report['targetType'], r.targetId).catch(() => ({ preview: '(মুছে ফেলা হয়েছে)', link: null, hidden: true })),
  ]);
  return { id: r.id, targetType: r.targetType as Report['targetType'], targetId: r.targetId, targetPreview: tg.preview.slice(0, 280), targetLink: tg.link, targetHidden: tg.hidden, reason: r.reason, details: r.details, reporterName: shortName(rep?.name ?? ''), status: r.status as Report['status'], resolution: r.resolution, createdAt: r.createdAt.toISOString(), sameTargetCount: same?.n ?? 1 };
}

export async function adminReports(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(reports.status, q.status)];
  if (cur) conds.push(lt(reports.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(reports).where(and(...conds)).orderBy(desc(reports.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await Promise.all(pg.items.map(toReport)), nextCursor: pg.nextCursor };
}

async function setHidden(type: string, id: string, hidden: boolean, actorId: string) {
  if (type === 'review') {
    const [r] = await coreDb.update(reviews).set({ status: hidden ? 'hidden' : 'visible' }).where(eq(reviews.id, id)).returning({ courseId: reviews.courseId });
    if (r) {
      const [x] = await coreDb.select({ avg: sql<string>`coalesce(round(avg(${reviews.rating})::numeric, 2), 0)::text`, n: sql<number>`count(*)::int` }).from(reviews).where(and(eq(reviews.courseId, r.courseId), eq(reviews.status, 'visible')));
      await contentDb.update(courses).set({ ratingAvg: x?.avg ?? '0', ratingCount: x?.n ?? 0 }).where(eq(courses.id, r.courseId));
    }
  } else if (type === 'question') await coreDb.update(questions).set({ status: hidden ? 'hidden' : 'open' }).where(eq(questions.id, id));
  else if (type === 'answer') await coreDb.update(answers).set({ status: hidden ? 'hidden' : 'visible' }).where(eq(answers.id, id));
  else if (type === 'course') {
    const [c] = await contentDb.select({ status: courses.status }).from(courses).where(eq(courses.id, id));
    if (c && (hidden ? c.status === 'published' : c.status === 'unlisted')) await contentDb.update(courses).set({ status: hidden ? 'unlisted' : 'published' }).where(eq(courses.id, id));
  } else if (type === 'user') await updateUser(actorId, id, { status: hidden ? 'suspended' : 'active', reason: 'moderation' });
}

export async function resolveReport(actorId: string, id: string, d: { action: 'hide' | 'restore' | 'dismiss' | 'reviewing'; resolution?: string }) {
  const [r] = await coreDb.select().from(reports).where(eq(reports.id, id));
  if (!r) throw notFound();
  if (d.action === 'hide' || d.action === 'restore') await setHidden(r.targetType, r.targetId, d.action === 'hide', actorId);
  const status = d.action === 'reviewing' ? 'reviewing' : d.action === 'dismiss' ? 'dismissed' : 'resolved';
  // resolving one report resolves every open report on the same target
  const scope = d.action === 'reviewing' ? eq(reports.id, id) : and(eq(reports.targetType, r.targetType), eq(reports.targetId, r.targetId), inArray(reports.status, ['open', 'reviewing']));
  await coreDb.update(reports).set({ status, resolvedBy: actorId, resolution: d.resolution ?? (d.action === 'hide' ? 'hidden' : d.action === 'restore' ? 'restored' : null) }).where(d.action === 'reviewing' ? scope : or(scope, eq(reports.id, id)));
  await audit({ actorId, action: `report.${d.action}`, entity: r.targetType, entityId: r.targetId, before: { status: r.status }, after: { status, resolution: d.resolution } });
  return toReport((await coreDb.select().from(reports).where(eq(reports.id, id)))[0]!);
}

// ---------------------------------------------------------------- broadcasts

async function audienceIds(a: { audience: string; institutionId?: string | null }) {
  const base = and(eq(users.status, 'active'), isNull(users.deletedAt));
  if (a.audience === 'sellers') return (await coreDb.select({ id: users.id }).from(users).where(and(base, sql`'seller' = any(${users.roles})`))).map((r) => r.id);
  if (a.audience === 'students') return (await coreDb.select({ id: users.id }).from(users).where(and(base, sql`not ('seller' = any(${users.roles}))`))).map((r) => r.id);
  if (a.audience === 'institution') {
    if (!a.institutionId) throw badRequest('প্রতিষ্ঠান বাছাই করুন।');
    return (await coreDb.select({ id: users.id }).from(users).innerJoin(studentProfiles, eq(studentProfiles.userId, users.id)).where(and(base, eq(studentProfiles.institutionId, a.institutionId)))).map((r) => r.id);
  }
  return (await coreDb.select({ id: users.id }).from(users).where(base)).map((r) => r.id);
}

export async function previewBroadcast(a: { audience: string; institutionId?: string | null }) {
  return { recipients: (await audienceIds(a)).length };
}

export async function sendBroadcast(actorId: string, b: { title: string; body: string; link?: string | null; audience: string; institutionId?: string | null; email: boolean }) {
  const ids = await audienceIds(b);
  if (!ids.length) throw badRequest('এই অডিয়েন্সে কেউ নেই।');
  const key = `bc-${Date.now().toString(36)}`;
  // in-app + push for everyone, email only when chosen (each user's preferences still apply)
  for (let i = 0; i < ids.length; i += 200) {
    await Promise.all(ids.slice(i, i + 200).map((userId) => notify({ userId, type: 'admin.broadcast', ref: key, title: b.title, body: b.body, link: b.link ?? null, ...(b.email ? { email: { template: 'admin_broadcast' as const, data: { subject: b.title, body: b.body } }, emailByDefault: true } : {}) })));
  }
  const [row] = await coreDb.insert(auditLogs).values({ actorId, action: 'broadcast.send', entity: 'broadcast', entityId: key, afterJson: { title: b.title, body: b.body, link: b.link ?? null, audience: b.audience, institutionId: b.institutionId ?? null, email: b.email, recipients: ids.length } }).returning();
  const [a] = await coreDb.select({ name: users.name }).from(users).where(eq(users.id, actorId));
  return { id: key, title: b.title, body: b.body, audience: b.audience, recipients: ids.length, email: b.email, createdAt: row!.createdAt.toISOString(), actorName: a?.name ?? '' };
}

export async function listBroadcasts() {
  const rows = await coreDb.select().from(auditLogs).where(eq(auditLogs.action, 'broadcast.send')).orderBy(desc(auditLogs.createdAt)).limit(50);
  const actors = rows.length ? await coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.id, rows.map((r) => r.actorId).filter((v): v is string => !!v))) : [];
  return {
    items: rows.map((r) => {
      const a = (r.afterJson ?? {}) as { title?: string; body?: string; audience?: string; email?: boolean; recipients?: number };
      return { id: r.entityId ?? r.id, title: a.title ?? '', body: a.body ?? '', audience: a.audience ?? 'all', recipients: a.recipients ?? 0, email: !!a.email, createdAt: r.createdAt.toISOString(), actorName: actors.find((x) => x.id === r.actorId)?.name ?? '' };
    }),
  };
}

// ---------------------------------------------------------------- logs

export async function adminEmailLogs(q: { status?: string; q?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [];
  if (q.status) conds.push(eq(emailLogs.status, q.status));
  if (q.q) conds.push(or(ilike(emailLogs.toEmail, `%${q.q}%`), ilike(emailLogs.template, `%${q.q}%`))!);
  if (cur) conds.push(lt(emailLogs.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(emailLogs).where(conds.length ? and(...conds) : undefined).orderBy(desc(emailLogs.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: pg.items.map((e) => ({ id: e.id, toEmail: e.toEmail, template: e.template, subject: e.subject, status: e.status, provider: e.provider, error: e.error, attempts: e.attempts, createdAt: e.createdAt.toISOString(), sentAt: e.sentAt?.toISOString() ?? null })), nextCursor: pg.nextCursor };
}

const SECRETISH = /secret|password|token|key|otp|code_hash|account_enc/i;
function redact(v: unknown): unknown {
  if (!v || typeof v !== 'object') return v;
  if (Array.isArray(v)) return v.map(redact);
  return Object.fromEntries(Object.entries(v as Record<string, unknown>).map(([k, x]) => [k, SECRETISH.test(k) ? '••••' : redact(x)]));
}

export async function adminAudit(q: { entity?: string; action?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [];
  if (q.entity) conds.push(eq(auditLogs.entity, q.entity));
  if (q.action) conds.push(ilike(auditLogs.action, `${q.action}%`));
  if (cur) conds.push(lt(auditLogs.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(auditLogs).where(conds.length ? and(...conds) : undefined).orderBy(desc(auditLogs.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  const actorIds = [...new Set(pg.items.map((r) => r.actorId).filter((v): v is string => !!v))];
  const actors = actorIds.length ? await coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.id, actorIds)) : [];
  return { items: pg.items.map((r) => ({ id: r.id, actorName: actors.find((a) => a.id === r.actorId)?.name ?? null, action: r.action, entity: r.entity, entityId: r.entityId, before: redact(r.beforeJson), after: redact(r.afterJson), ip: r.ip, createdAt: r.createdAt.toISOString() })), nextCursor: pg.nextCursor };
}

// ---------------------------------------------------------------- analytics

function daySeries(days: number) {
  return Array.from({ length: days }, (_, i) => dhakaDay(new Date(Date.now() - (days - 1 - i) * 86_400_000)));
}

export async function adminAnalytics(days: number): Promise<AdminAnalytics> {
  const from = since(days);
  const [paid, ref, newStudents, newSellers, enr, gc, active, pendSellers, pendCourses, pendInst, openReports, refundReq, payoutReq, comm] = await Promise.all([
    coreDb.select({ day: sql<string>`to_char(${orders.paidAt} at time zone 'Asia/Dhaka', 'YYYY-MM-DD')`, gmv: sql<number>`coalesce(sum(${orders.total}),0)::int`, n: sql<number>`count(*)::int` }).from(orders).where(and(inArray(orders.status, ['paid', 'refunded']), gte(orders.paidAt, from), sql`${orders.total} > 0`)).groupBy(sql`1`),
    coreDb.select({ n: sql<number>`count(*)::int`, s: sql<number>`coalesce(sum(${refunds.amount}),0)::int` }).from(refunds).where(and(eq(refunds.status, 'processed'), gte(refunds.processedAt, from))),
    coreDb.select({ day: sql<string>`to_char(${users.createdAt} at time zone 'Asia/Dhaka', 'YYYY-MM-DD')`, n: sql<number>`count(*)::int` }).from(users).where(gte(users.createdAt, from)).groupBy(sql`1`),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(sellerProfiles).where(gte(sellerProfiles.createdAt, from)),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(enrollments).where(gte(enrollments.createdAt, from)),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(googleConnections).where(eq(googleConnections.status, 'active')),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(users).where(gte(users.lastActiveAt, since(7))),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(sellerProfiles).where(eq(sellerProfiles.verificationStatus, 'pending')),
    contentDb.select({ n: sql<number>`count(*)::int` }).from(courses).where(eq(courses.status, 'in_review')),
    contentDb.select({ n: sql<number>`count(*)::int` }).from(institutionRequests).where(eq(institutionRequests.status, 'pending')),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(reports).where(inArray(reports.status, ['open', 'reviewing'])),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(refunds).where(eq(refunds.status, 'requested')),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(payouts).where(inArray(payouts.status, ['requested', 'approved'])),
    coreDb.select({ s: sql<number>`coalesce(-sum(${ledgerEntries.amount}),0)::int` }).from(ledgerEntries).where(and(eq(ledgerEntries.type, 'commission'), gte(ledgerEntries.createdAt, from))),
  ]);
  const top = await coreDb
    .select({ courseId: orderItems.courseId, sales: sql<number>`count(*)::int`, revenue: sql<number>`coalesce(sum(${orderItems.price} - ${orderItems.discount}),0)::int`, sellerId: orderItems.sellerId })
    .from(orderItems)
    .innerJoin(orders, eq(orders.id, orderItems.orderId))
    .where(and(eq(orders.status, 'paid'), gte(orders.paidAt, from), sql`${orderItems.courseId} is not null`))
    .groupBy(orderItems.courseId, orderItems.sellerId)
    .orderBy(sql`3 desc`)
    .limit(8);
  const sellersTop = await coreDb
    .select({ sellerId: orderItems.sellerId, revenue: sql<number>`coalesce(sum(${orderItems.price} - ${orderItems.discount}),0)::int`, students: sql<number>`count(distinct ${orders.studentId})::int` })
    .from(orderItems)
    .innerJoin(orders, eq(orders.id, orderItems.orderId))
    .where(and(eq(orders.status, 'paid'), gte(orders.paidAt, from)))
    .groupBy(orderItems.sellerId)
    .orderBy(sql`2 desc`)
    .limit(5);
  const cIds = top.map((x) => x.courseId).filter((v): v is string => !!v);
  const sIds = [...new Set([...top.map((x) => x.sellerId), ...sellersTop.map((x) => x.sellerId)])];
  const [cs, sps] = await Promise.all([cIds.length ? contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(inArray(courses.id, cIds)) : [], sIds.length ? coreDb.select({ id: sellerProfiles.userId, name: sellerProfiles.displayName }).from(sellerProfiles).where(inArray(sellerProfiles.userId, sIds)) : []]);
  return {
    totals: { gmv: paid.reduce((a, x) => a + x.gmv, 0), platformRevenue: comm[0]?.s ?? 0, orders: paid.reduce((a, x) => a + x.n, 0), refunds: ref[0]?.n ?? 0, refundAmount: ref[0]?.s ?? 0, newStudents: newStudents.reduce((a, x) => a + x.n, 0), newSellers: newSellers[0]?.n ?? 0, enrollments: enr[0]?.n ?? 0, classroomConnected: gc[0]?.n ?? 0, activeStudents7d: active[0]?.n ?? 0 },
    queues: { pendingSellers: pendSellers[0]?.n ?? 0, pendingCourses: pendCourses[0]?.n ?? 0, pendingInstitutions: pendInst[0]?.n ?? 0, openReports: openReports[0]?.n ?? 0, refundRequests: refundReq[0]?.n ?? 0, payoutRequests: payoutReq[0]?.n ?? 0 },
    series: daySeries(days).map((date) => ({ date, gmv: paid.find((p) => p.day === date)?.gmv ?? 0, orders: paid.find((p) => p.day === date)?.n ?? 0, signups: newStudents.find((p) => p.day === date)?.n ?? 0 })),
    topCourses: top.map((x) => ({ id: x.courseId!, title: cs.find((c) => c.id === x.courseId)?.title ?? '', sellerName: sps.find((s) => s.id === x.sellerId)?.name ?? '', sales: x.sales, revenue: x.revenue })),
    topSellers: sellersTop.map((x) => ({ id: x.sellerId, name: sps.find((s) => s.id === x.sellerId)?.name ?? '', revenue: x.revenue, students: x.students })),
  };
}

// ---------------------------------------------------------------- seller students & analytics

export async function sellerStudents(sellerId: string, q: { courseId?: string; q?: string; cursor?: string; limit: number }) {
  const mine = await contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(and(eq(courses.sellerId, sellerId), isNull(courses.deletedAt)));
  const ids = q.courseId ? mine.filter((c) => c.id === q.courseId).map((c) => c.id) : mine.map((c) => c.id);
  if (!ids.length) return { items: [], nextCursor: null, total: 0 };
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [inArray(enrollments.courseId, ids), eq(enrollments.status, 'active')];
  if (q.q) conds.push(ilike(users.name, `%${q.q.replace(/[%_]/g, '')}%`));
  const [tot] = await coreDb.select({ n: count() }).from(enrollments).innerJoin(users, eq(users.id, enrollments.studentId)).where(and(...conds));
  if (cur) conds.push(lt(enrollments.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select({ e: enrollments, name: users.name }).from(enrollments).innerJoin(users, eq(users.id, enrollments.studentId)).where(and(...conds)).orderBy(desc(enrollments.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.e.createdAt.toISOString(), r.e.id]);
  const sids = pg.items.map((r) => r.e.studentId);
  const profs = sids.length ? await coreDb.select({ id: studentProfiles.userId, inst: studentProfiles.institutionId }).from(studentProfiles).where(inArray(studentProfiles.userId, sids)) : [];
  const instIds = [...new Set(profs.map((p) => p.inst).filter((v): v is string => !!v))];
  const insts = instIds.length ? await contentDb.select({ id: institutions.id, short: institutions.shortName }).from(institutions).where(inArray(institutions.id, instIds)) : [];
  return {
    // students appear by short name only — teachers never see contact details
    items: pg.items.map((r) => ({ enrollmentId: r.e.id, studentName: shortName(r.name), institutionShort: insts.find((i) => i.id === profs.find((p) => p.id === r.e.studentId)?.inst)?.short ?? null, courseId: r.e.courseId, courseTitle: mine.find((c) => c.id === r.e.courseId)?.title ?? '', progressPercent: r.e.progressPercent, enrolledAt: r.e.createdAt.toISOString(), lastAccessedAt: r.e.lastAccessedAt?.toISOString() ?? null, completed: !!r.e.completedAt })),
    nextCursor: pg.nextCursor,
    total: tot?.n ?? 0,
  };
}

export async function sellerAnalytics(sellerId: string, days: number) {
  const from = since(days);
  const mine = await contentDb.select().from(courses).where(and(eq(courses.sellerId, sellerId), isNull(courses.deletedAt)));
  const ids = mine.map((c) => c.id);
  const [sales, enrDays, perCourse, refundsN, openQ] = await Promise.all([
    coreDb.select({ day: sql<string>`to_char(${ledgerEntries.createdAt} at time zone 'Asia/Dhaka', 'YYYY-MM-DD')`, s: sql<number>`coalesce(sum(${ledgerEntries.amount}),0)::int` }).from(ledgerEntries).where(and(eq(ledgerEntries.sellerId, sellerId), inArray(ledgerEntries.type, ['sale', 'commission', 'refund']), gte(ledgerEntries.createdAt, from))).groupBy(sql`1`),
    ids.length ? coreDb.select({ day: sql<string>`to_char(${enrollments.createdAt} at time zone 'Asia/Dhaka', 'YYYY-MM-DD')`, n: sql<number>`count(*)::int` }).from(enrollments).where(and(inArray(enrollments.courseId, ids), gte(enrollments.createdAt, from))).groupBy(sql`1`) : [],
    ids.length ? coreDb.select({ courseId: enrollments.courseId, n: sql<number>`count(*)::int`, avg: sql<number>`coalesce(round(avg(${enrollments.progressPercent})),0)::int`, students: sql<number>`count(distinct ${enrollments.studentId})::int` }).from(enrollments).where(and(inArray(enrollments.courseId, ids), eq(enrollments.status, 'active'))).groupBy(enrollments.courseId) : [],
    coreDb.select({ n: sql<number>`count(*)::int` }).from(refunds).innerJoin(orderItems, eq(orderItems.orderId, refunds.orderId)).where(and(eq(orderItems.sellerId, sellerId), eq(refunds.status, 'processed'), gte(refunds.processedAt, from))),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(questions).where(and(eq(questions.sellerId, sellerId), eq(questions.status, 'open'))),
  ]);
  const revByCourse = ids.length
    ? await coreDb.select({ courseId: orderItems.courseId, s: sql<number>`coalesce(sum(${orderItems.price} - ${orderItems.discount}),0)::int`, n: sql<number>`count(*)::int` }).from(orderItems).innerJoin(orders, eq(orders.id, orderItems.orderId)).where(and(eq(orderItems.sellerId, sellerId), eq(orders.status, 'paid'), gte(orders.paidAt, from))).groupBy(orderItems.courseId)
    : [];
  const rated = mine.filter((c) => c.ratingCount > 0);
  const totalStudents = perCourse.reduce((a, x) => a + x.students, 0);
  return {
    totals: {
      revenue: sales.reduce((a, x) => a + x.s, 0),
      enrollments: enrDays.reduce((a, x) => a + x.n, 0),
      students: totalStudents,
      avgCompletion: totalStudents ? Math.round(perCourse.reduce((a, x) => a + x.avg * x.students, 0) / totalStudents) : 0,
      ratingAvg: rated.length ? Math.round((rated.reduce((a, c) => a + Number(c.ratingAvg) * c.ratingCount, 0) / rated.reduce((a, c) => a + c.ratingCount, 0)) * 10) / 10 : 0,
      ratingCount: rated.reduce((a, c) => a + c.ratingCount, 0),
      refunds: refundsN[0]?.n ?? 0,
      openQuestions: openQ[0]?.n ?? 0,
    },
    series: daySeries(days).map((date) => ({ date, revenue: sales.find((x) => x.day === date)?.s ?? 0, enrollments: enrDays.find((x) => x.day === date)?.n ?? 0 })),
    courses: mine
      .map((c) => ({ id: c.id, title: c.title, status: c.status, enrollments: perCourse.find((x) => x.courseId === c.id)?.n ?? 0, revenue: revByCourse.find((x) => x.courseId === c.id)?.s ?? 0, avgCompletion: perCourse.find((x) => x.courseId === c.id)?.avg ?? 0, ratingAvg: Number(c.ratingAvg), ratingCount: c.ratingCount }))
      .sort((a, b) => b.revenue - a.revenue || b.enrollments - a.enrollments),
  };
}

