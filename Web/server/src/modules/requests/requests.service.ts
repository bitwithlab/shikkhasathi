import { and, desc, eq, ilike, inArray, or, sql, type SQL } from 'drizzle-orm';
import type { z } from 'zod';
import type { CreateCourseRequest } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { courseRequestVotes, courseRequests, sellerProfiles } from '../../database/core/schema';
import { courses, institutions, subjectMasters } from '../../database/content/schema';
import { badRequest, forbidden, notFound } from '../../common/errors';
import { lookupAcademicNames } from '../catalog/catalog.lookup';
import { notify } from '../notifications/notifications.service';
import { getBusinessFlag } from '../settings/settings.service';
import { AppError } from '../../common/errors';

type Row = typeof courseRequests.$inferSelect;

const dedupeKey = (institutionId: string | null, subject: string, examType: string) => `${institutionId ?? 'any'}|${subject.toLowerCase().replace(/[^a-z0-9ঀ-৿]+/g, '')}|${examType}`;

async function hydrate(rows: Row[], userId?: string) {
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id);
  const [names, votes, claimers, linked] = await Promise.all([
    lookupAcademicNames({ institutionIds: rows.map((r) => r.institutionId).filter((v): v is string => !!v), departmentIds: [], programIds: [] }),
    userId ? coreDb.select({ id: courseRequestVotes.requestId }).from(courseRequestVotes).where(and(eq(courseRequestVotes.userId, userId), inArray(courseRequestVotes.requestId, ids))) : [],
    coreDb.select({ userId: sellerProfiles.userId, name: sellerProfiles.displayName }).from(sellerProfiles).where(inArray(sellerProfiles.userId, rows.map((r) => r.claimedBy).filter((v): v is string => !!v).concat(['00000000-0000-0000-0000-000000000000']))),
    contentDb.select({ id: courses.id, slug: courses.slug }).from(courses).where(inArray(courses.id, rows.map((r) => r.linkedCourseId).filter((v): v is string => !!v).concat(['00000000-0000-0000-0000-000000000000']))),
  ]);
  return rows.map((r) => ({
    id: r.id,
    institutionId: r.institutionId,
    institutionShort: r.institutionId ? (names.institution.get(r.institutionId)?.shortName ?? null) : null,
    subjectId: r.subjectMasterId,
    subjectName: r.subjectName,
    courseCode: r.courseCode,
    examType: r.examType as 'midterm',
    note: r.note,
    status: r.status as 'open',
    voteCount: r.voteCount,
    voted: votes.some((v) => v.id === r.id),
    mine: r.requesterId === userId,
    claimedByName: claimers.find((c) => c.userId === r.claimedBy)?.name ?? null,
    linkedCourseSlug: linked.find((c) => c.id === r.linkedCourseId)?.slug ?? null,
    createdAt: r.createdAt.toISOString(),
  }));
}

async function ensureEnabled() {
  if (!(await getBusinessFlag('feature_course_requests'))) throw new AppError('INTEGRATION_DISABLED', 'কোর্স অনুরোধ এখন বন্ধ আছে।');
}

export async function list(q: { status: string; institutionId?: string; q?: string; sort: string; cursor?: string; limit: number }, userId?: string) {
  const offset = q.cursor ? Number(Buffer.from(q.cursor, 'base64url').toString()) || 0 : 0;
  const conds: SQL[] = [];
  if (q.status !== 'all') conds.push(eq(courseRequests.status, q.status));
  if (q.institutionId) conds.push(eq(courseRequests.institutionId, q.institutionId));
  if (q.q) conds.push(or(ilike(courseRequests.subjectName, `%${q.q}%`), ilike(courseRequests.courseCode, `%${q.q}%`))!);
  const rows = await coreDb
    .select()
    .from(courseRequests)
    .where(conds.length ? and(...conds) : undefined)
    .orderBy(...(q.sort === 'newest' ? [desc(courseRequests.createdAt)] : [sql`case ${courseRequests.status} when 'open' then 0 when 'teacher_working' then 1 else 2 end`, desc(courseRequests.voteCount), desc(courseRequests.createdAt)]))
    .limit(q.limit + 1)
    .offset(offset);
  const more = rows.length > q.limit;
  return { items: await hydrate(rows.slice(0, q.limit), userId), nextCursor: more ? Buffer.from(String(offset + q.limit)).toString('base64url') : null };
}

export async function create(userId: string, b: z.infer<typeof CreateCourseRequest>) {
  await ensureEnabled();
  let subjectName = b.subjectName;
  if (b.subjectId) {
    const [s] = await contentDb.select({ name: subjectMasters.name }).from(subjectMasters).where(eq(subjectMasters.id, b.subjectId));
    if (!s) throw badRequest('সাবজেক্টটি পাওয়া যায়নি।');
    subjectName = s.name;
  }
  if (b.institutionId) {
    const [i] = await contentDb.select({ id: institutions.id }).from(institutions).where(eq(institutions.id, b.institutionId));
    if (!i) throw badRequest('প্রতিষ্ঠানটি পাওয়া যায়নি।');
  }
  const key = dedupeKey(b.institutionId, subjectName, b.examType);
  const [existing] = await coreDb.select().from(courseRequests).where(eq(courseRequests.dedupeKey, key));
  if (existing) {
    // Duplicate → count it as an upvote (spec §6.11 duplicate detection).
    await vote(userId, existing.id, true);
    const [r] = await coreDb.select().from(courseRequests).where(eq(courseRequests.id, existing.id));
    return { request: (await hydrate([r!], userId))[0]!, duplicate: true };
  }
  const [row] = await coreDb.insert(courseRequests).values({ requesterId: userId, institutionId: b.institutionId, subjectMasterId: b.subjectId ?? null, subjectName, courseCode: b.courseCode ?? null, examType: b.examType, note: b.note ?? null, dedupeKey: key, voteCount: 1 }).returning();
  await coreDb.insert(courseRequestVotes).values({ requestId: row!.id, userId }).onConflictDoNothing();
  return { request: (await hydrate([row!], userId))[0]!, duplicate: false };
}

export async function vote(userId: string, id: string, forceOn = false) {
  const [r] = await coreDb.select().from(courseRequests).where(eq(courseRequests.id, id));
  if (!r) throw notFound();
  const [mine] = await coreDb.select().from(courseRequestVotes).where(and(eq(courseRequestVotes.requestId, id), eq(courseRequestVotes.userId, userId)));
  if (mine && !forceOn) {
    await coreDb.delete(courseRequestVotes).where(eq(courseRequestVotes.id, mine.id));
  } else if (!mine) {
    await coreDb.insert(courseRequestVotes).values({ requestId: id, userId }).onConflictDoNothing();
  }
  await coreDb.update(courseRequests).set({ voteCount: sql`(select count(*)::int from ${courseRequestVotes} where ${courseRequestVotes.requestId} = ${id})` }).where(eq(courseRequests.id, id));
  const [after] = await coreDb.select().from(courseRequests).where(eq(courseRequests.id, id));
  return (await hydrate([after!], userId))[0]!;
}

export async function claim(sellerId: string, id: string, courseId?: string) {
  const [r] = await coreDb.select().from(courseRequests).where(eq(courseRequests.id, id));
  if (!r) throw notFound();
  if (r.claimedBy && r.claimedBy !== sellerId) throw forbidden('অন্য একজন শিক্ষক এটি বানাচ্ছেন।');
  if (courseId) {
    const [c] = await contentDb.select({ sellerId: courses.sellerId, status: courses.status }).from(courses).where(eq(courses.id, courseId));
    if (!c || c.sellerId !== sellerId) throw forbidden();
  }
  await coreDb.update(courseRequests).set({ claimedBy: sellerId, status: r.status === 'published' ? 'published' : 'teacher_working', ...(courseId ? { linkedCourseId: courseId } : {}) }).where(eq(courseRequests.id, id));
  if (courseId) await onCoursePublished(courseId);
  const [after] = await coreDb.select().from(courseRequests).where(eq(courseRequests.id, id));
  return (await hydrate([after!], sellerId))[0]!;
}

/** When a linked course is published, mark requests Published and notify everyone who asked/voted. */
export async function onCoursePublished(courseId: string) {
  const [c] = await contentDb.select({ status: courses.status, slug: courses.slug, title: courses.title }).from(courses).where(eq(courses.id, courseId));
  if (!c || c.status !== 'published') return;
  const reqs = await coreDb.select().from(courseRequests).where(and(eq(courseRequests.linkedCourseId, courseId), sql`${courseRequests.status} <> 'published'`));
  for (const r of reqs) {
    await coreDb.update(courseRequests).set({ status: 'published' }).where(eq(courseRequests.id, r.id));
    const voters = await coreDb.select({ userId: courseRequestVotes.userId }).from(courseRequestVotes).where(eq(courseRequestVotes.requestId, r.id));
    for (const v of voters) await notify({ userId: v.userId, type: 'request.published', ref: `${r.id}`, title: 'আপনার অনুরোধ করা কোর্স এসেছে', body: c.title, link: `/c/${c.slug}` });
  }
}
