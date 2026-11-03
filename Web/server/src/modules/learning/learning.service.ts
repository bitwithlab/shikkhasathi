import { randomBytes } from 'node:crypto';
import { and, desc, eq, ilike, inArray, isNull, lt, or, sql, type SQL } from 'drizzle-orm';
import type { Bookmark, LessonDetail, MyCourse, Note, PlayerOutline, Question, Review } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { answers, bookmarks, enrollments, lessonProgress, notes, questions, reviews, sellerProfiles, users } from '../../database/core/schema';
import { courseChapters, courseSections, courses, courseTargets, documents, lessons, videoAssets } from '../../database/content/schema';
import { badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { decodeCursor, toPage } from '../../common/pagination';
import { redis } from '../../common/redis';
import { storage, type Bucket } from '../../services/storage';
import { videoProvider } from '../../services/video';
import { notify } from '../notifications/notifications.service';
import { invalidateDashboard, setDashboardProvider } from '../dashboard/dashboard.service';
import { lookupAcademicNames } from '../catalog/catalog.lookup';

type CourseRow = typeof courses.$inferSelect;
type LessonRow = typeof lessons.$inferSelect;
type EnrollmentRow = typeof enrollments.$inferSelect;

const PLAYBACK_TTL_SEC = 2 * 3600;
const STREAM_TTL_SEC = 90;
const MAX_DEVICES = 3;
const DEVICE_WINDOW_MS = 30 * 86_400_000;
const REVIEW_MIN_PROGRESS = 20;

/** "নুসরাত জাহান" → "নুসরাত জা." — public surfaces never show full names. */
export const shortName = (name: string) => {
  const [a, b] = name.trim().split(/\s+/);
  return b ? `${a} ${[...b].slice(0, 2).join('')}.` : (a ?? '');
};

// ---------------------------------------------------------------- access

const activeEnrollment = (e: EnrollmentRow | undefined) => !!e && e.status === 'active' && (!e.accessUntil || e.accessUntil > new Date());

interface Access {
  course: CourseRow;
  enrollment: EnrollmentRow | null;
  enrolled: boolean;
  isOwner: boolean;
  isAdmin: boolean;
}

async function access(userId: string, courseId: string, roles: string[] = []): Promise<Access> {
  const [[c], [e]] = await Promise.all([
    contentDb.select().from(courses).where(and(eq(courses.id, courseId), isNull(courses.deletedAt))),
    coreDb.select().from(enrollments).where(and(eq(enrollments.studentId, userId), eq(enrollments.courseId, courseId))),
  ]);
  if (!c) throw notFound('কোর্সটি পাওয়া যায়নি।');
  const isOwner = c.sellerId === userId;
  return { course: c, enrollment: e ?? null, enrolled: activeEnrollment(e), isOwner, isAdmin: roles.includes('admin') };
}

const canSeeAll = (a: Access) => a.enrolled || a.isOwner || a.isAdmin;

function requireLessonAccess(a: Access, l: LessonRow) {
  if (canSeeAll(a) || l.isPreview) return;
  if (a.enrollment && !a.enrolled) throw forbidden('কোর্সের মেয়াদ শেষ হয়ে গেছে।');
  throw forbidden('এই লেসন দেখতে কোর্সটি কিনুন।');
}

/** Curriculum in display order (section → chapter → lesson position). */
async function orderedCurriculum(courseId: string) {
  const [secs, chs, ls] = await Promise.all([
    contentDb.select().from(courseSections).where(eq(courseSections.courseId, courseId)).orderBy(courseSections.position),
    contentDb.select().from(courseChapters).where(eq(courseChapters.courseId, courseId)).orderBy(courseChapters.position),
    contentDb.select().from(lessons).where(eq(lessons.courseId, courseId)).orderBy(lessons.position),
  ]);
  const tree = secs.map((s) => ({ ...s, chapters: chs.filter((c) => c.sectionId === s.id).map((c) => ({ ...c, lessons: ls.filter((l) => l.chapterId === c.id) })) }));
  const flat = tree.flatMap((s) => s.chapters.flatMap((c) => c.lessons));
  return { tree, flat };
}

async function lessonOr404(id: string) {
  const [l] = await contentDb.select().from(lessons).where(eq(lessons.id, id));
  if (!l) throw notFound('লেসনটি পাওয়া যায়নি।');
  return l;
}

// ---------------------------------------------------------------- progress

async function recomputeProgress(studentId: string, courseId: string, lastLessonId?: string) {
  const [[tot], [done]] = await Promise.all([
    contentDb.select({ n: sql<number>`count(*)::int` }).from(lessons).where(and(eq(lessons.courseId, courseId), inArray(lessons.type, ['video', 'pdf', 'text']))),
    coreDb.select({ n: sql<number>`count(*)::int` }).from(lessonProgress).where(and(eq(lessonProgress.studentId, studentId), eq(lessonProgress.courseId, courseId), sql`${lessonProgress.completedAt} is not null`)),
  ]);
  const pct = tot?.n ? Math.min(100, Math.round(((done?.n ?? 0) / tot.n) * 100)) : 0;
  const now = new Date();
  await coreDb
    .update(enrollments)
    .set({ progressPercent: pct, lastAccessedAt: now, ...(lastLessonId ? { lastLessonId } : {}), completedAt: pct >= 100 ? sql`coalesce(${enrollments.completedAt}, now())` : null })
    .where(and(eq(enrollments.studentId, studentId), eq(enrollments.courseId, courseId)));
  await invalidateDashboard(studentId);
  return pct;
}

export async function heartbeat(userId: string, lessonId: string, b: { positionSec: number; deltaSec: number; streamToken?: string }) {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId);
  requireLessonAccess(a, l);
  let streamActive = true;
  if (b.streamToken) {
    const cur = await redis().get(`ps:${userId}`);
    streamActive = cur === b.streamToken;
    if (streamActive) await redis().expire(`ps:${userId}`, STREAM_TTL_SEC);
  }
  if (!a.enrolled) return { completed: false, progressPercent: 0, streamActive };
  // a stream that was taken over by another device doesn't earn watch time
  const delta = streamActive ? Math.min(b.deltaSec, 30) : 0;
  const cap = Math.max(l.durationSec, 60) * 2;
  const [row] = await coreDb
    .insert(lessonProgress)
    .values({ studentId: userId, courseId: l.courseId, lessonId, watchedSeconds: delta, lastPositionSec: b.positionSec })
    .onConflictDoUpdate({ target: [lessonProgress.studentId, lessonProgress.lessonId], set: { watchedSeconds: sql`least(${lessonProgress.watchedSeconds} + ${delta}, ${cap})`, lastPositionSec: b.positionSec, updatedAt: new Date() } })
    .returning();
  let completed = !!row?.completedAt;
  if (!completed && l.durationSec > 0 && row && row.watchedSeconds >= l.durationSec * 0.9) {
    await coreDb.update(lessonProgress).set({ completedAt: new Date() }).where(eq(lessonProgress.id, row.id));
    completed = true;
  }
  const pct = await recomputeProgress(userId, l.courseId, lessonId);
  return { completed, progressPercent: pct, streamActive };
}

export async function setComplete(userId: string, lessonId: string, completed: boolean) {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId);
  if (!a.enrolled) throw forbidden('এনরোল করা শিক্ষার্থীরাই প্রগ্রেস রাখতে পারেন।');
  await coreDb
    .insert(lessonProgress)
    .values({ studentId: userId, courseId: l.courseId, lessonId, completedAt: completed ? new Date() : null })
    .onConflictDoUpdate({ target: [lessonProgress.studentId, lessonProgress.lessonId], set: { completedAt: completed ? sql`coalesce(${lessonProgress.completedAt}, now())` : null, updatedAt: new Date() } });
  return { completed, progressPercent: await recomputeProgress(userId, l.courseId, lessonId), streamActive: true };
}

// ---------------------------------------------------------------- My Courses

export async function myCourses(userId: string, filter: 'all' | 'in_progress' | 'completed' | 'expiring'): Promise<{ items: MyCourse[] }> {
  const enr = await coreDb.select().from(enrollments).where(and(eq(enrollments.studentId, userId), inArray(enrollments.status, ['active', 'expired']))).orderBy(sql`${enrollments.lastAccessedAt} desc nulls last`, desc(enrollments.createdAt));
  if (!enr.length) return { items: [] };
  const ids = enr.map((e) => e.courseId);
  const [cs, ts, counts, done] = await Promise.all([
    contentDb.select().from(courses).where(inArray(courses.id, ids)),
    contentDb.select({ courseId: courseTargets.courseId, institutionId: courseTargets.institutionId, code: courseTargets.courseCode }).from(courseTargets).where(inArray(courseTargets.courseId, ids)),
    contentDb.select({ courseId: lessons.courseId, n: sql<number>`count(*)::int` }).from(lessons).where(and(inArray(lessons.courseId, ids), inArray(lessons.type, ['video', 'pdf', 'text']))).groupBy(lessons.courseId),
    coreDb.select({ courseId: lessonProgress.courseId, n: sql<number>`count(*)::int` }).from(lessonProgress).where(and(eq(lessonProgress.studentId, userId), inArray(lessonProgress.courseId, ids), sql`${lessonProgress.completedAt} is not null`)).groupBy(lessonProgress.courseId),
  ]);
  const sellerIds = [...new Set(cs.map((c) => c.sellerId))];
  const lastIds = enr.map((e) => e.lastLessonId).filter((v): v is string => !!v);
  const [sellers, names, lastLessons, firsts] = await Promise.all([
    coreDb.select({ id: sellerProfiles.userId, name: sellerProfiles.displayName }).from(sellerProfiles).where(inArray(sellerProfiles.userId, sellerIds)),
    lookupAcademicNames({ institutionIds: [...new Set(ts.map((t) => t.institutionId).filter((v): v is string => !!v))], departmentIds: [], programIds: [] }),
    lastIds.length ? contentDb.select({ id: lessons.id, title: lessons.title }).from(lessons).where(inArray(lessons.id, lastIds)) : [],
    contentDb
      .select({ courseId: lessons.courseId, id: lessons.id, title: lessons.title, sp: courseSections.position, cp: courseChapters.position, lp: lessons.position })
      .from(lessons)
      .innerJoin(courseChapters, eq(courseChapters.id, lessons.chapterId))
      .innerJoin(courseSections, eq(courseSections.id, courseChapters.sectionId))
      .where(inArray(lessons.courseId, ids))
      .orderBy(courseSections.position, courseChapters.position, lessons.position),
  ]);
  const now = Date.now();
  const items: MyCourse[] = enr.flatMap((e) => {
    const c = cs.find((x) => x.id === e.courseId);
    if (!c) return [];
    const t = ts.find((x) => x.courseId === c.id);
    const total = counts.find((x) => x.courseId === c.id)?.n ?? 0;
    const completed = done.find((x) => x.courseId === c.id)?.n ?? 0;
    const first = firsts.find((f) => f.courseId === c.id);
    const last = lastLessons.find((l) => l.id === e.lastLessonId);
    const until = e.accessUntil?.getTime() ?? null;
    const expired = e.status === 'expired' || (until !== null && until < now);
    return [
      {
        courseId: c.id,
        slug: c.slug,
        title: c.title,
        courseCode: t?.code ?? null,
        institutionShort: (t?.institutionId && names.institution.get(t.institutionId)?.shortName) || null,
        examType: c.examType,
        sellerName: sellers.find((s) => s.id === c.sellerId)?.name ?? '',
        coverColor: c.coverColor,
        progressPercent: e.progressPercent,
        completedLessons: completed,
        totalLessons: total,
        resumeLessonId: last?.id ?? first?.id ?? null,
        resumeLessonTitle: last?.title ?? first?.title ?? null,
        lastAccessedAt: e.lastAccessedAt?.toISOString() ?? null,
        completedAt: e.completedAt?.toISOString() ?? null,
        accessUntil: e.accessUntil?.toISOString() ?? null,
        expiringSoon: !expired && until !== null && until - now < 7 * 86_400_000,
        expired,
      },
    ];
  });
  const f = items.filter((i) => (filter === 'in_progress' ? !i.completedAt && !i.expired : filter === 'completed' ? !!i.completedAt : filter === 'expiring' ? i.expiringSoon : true));
  return { items: f };
}

setDashboardProvider('continueLearning', async (userId) => {
  const { items } = await myCourses(userId, 'in_progress');
  const c = items[0];
  if (!c) return null;
  return { courseId: c.courseId, courseSlug: c.slug, title: c.title, courseCode: c.courseCode, sellerName: c.sellerName, progressPercent: c.progressPercent, completedLessons: c.completedLessons, totalLessons: c.totalLessons, lastLessonId: c.resumeLessonId, lastLessonTitle: c.resumeLessonTitle, coverColor: c.coverColor };
});

// ---------------------------------------------------------------- player

export async function outline(userId: string, courseId: string, roles: string[]): Promise<PlayerOutline> {
  const a = await access(userId, courseId, roles);
  const c = a.course;
  if (c.status !== 'published' && !a.isOwner && !a.isAdmin && !a.enrollment) throw notFound('কোর্সটি পাওয়া যায়নি।');
  const [{ tree, flat }, prog, [seller], docs, [rev], [t]] = await Promise.all([
    orderedCurriculum(courseId),
    coreDb.select().from(lessonProgress).where(and(eq(lessonProgress.studentId, userId), eq(lessonProgress.courseId, courseId))),
    coreDb.select({ name: sellerProfiles.displayName, slug: sellerProfiles.slug }).from(sellerProfiles).where(eq(sellerProfiles.userId, c.sellerId)),
    contentDb.select({ id: documents.id, kind: documents.kind, title: documents.title, year: documents.year }).from(documents).where(eq(documents.courseId, courseId)).orderBy(desc(documents.year), documents.title),
    coreDb.select({ id: reviews.id, rating: reviews.rating, body: reviews.body }).from(reviews).where(and(eq(reviews.courseId, courseId), eq(reviews.studentId, userId))),
    contentDb.select({ code: courseTargets.courseCode }).from(courseTargets).where(eq(courseTargets.courseId, courseId)).limit(1),
  ]);
  const all = canSeeAll(a);
  const countable = flat.filter((l) => ['video', 'pdf', 'text'].includes(l.type));
  const completedLessons = countable.filter((l) => prog.find((p) => p.lessonId === l.id)?.completedAt).length;
  const pct = a.enrollment?.progressPercent ?? 0;
  const resume = a.enrollment?.lastLessonId && flat.some((l) => l.id === a.enrollment!.lastLessonId) ? a.enrollment.lastLessonId : (flat.find((l) => all || l.isPreview)?.id ?? null);
  return {
    course: { id: c.id, slug: c.slug, title: c.title, courseCode: t?.code ?? null, sellerId: c.sellerId, sellerName: seller?.name ?? '', sellerSlug: seller?.slug ?? null },
    enrolled: a.enrolled,
    isOwner: a.isOwner,
    accessUntil: a.enrollment?.accessUntil?.toISOString() ?? null,
    progressPercent: pct,
    completedLessons,
    totalLessons: countable.length,
    resumeLessonId: resume,
    sections: tree.map((s) => ({
      id: s.id,
      title: s.title,
      chapters: s.chapters.map((ch) => ({
        id: ch.id,
        title: ch.title,
        lessons: ch.lessons.map((l) => {
          const p = prog.find((x) => x.lessonId === l.id);
          return { id: l.id, title: l.title, type: l.type as 'video', durationSec: l.durationSec, isPreview: l.isPreview, isImportant: l.isImportant, completed: !!p?.completedAt, lastPositionSec: p?.lastPositionSec ?? 0, locked: !all && !l.isPreview };
        }),
      })),
    })),
    resources: all ? docs : [],
    myReview: rev ? { id: rev.id, rating: rev.rating, body: rev.body } : null,
    canReview: a.enrolled && pct >= REVIEW_MIN_PROGRESS,
  };
}

const youtubeId = (u: string) => /(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|shorts\/))([\w-]{11})/.exec(u)?.[1] ?? null;
const externalKind = (u: string): 'youtube' | 'hls' | 'external' => (youtubeId(u) ? 'youtube' : /\.m3u8(\?|$)/.test(u) ? 'hls' : 'external');

export async function lessonDetail(userId: string, lessonId: string, roles: string[]): Promise<LessonDetail> {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId, roles);
  requireLessonAccess(a, l);
  const all = canSeeAll(a);
  const [{ flat }, [p], doc, va] = await Promise.all([
    orderedCurriculum(l.courseId),
    coreDb.select().from(lessonProgress).where(and(eq(lessonProgress.studentId, userId), eq(lessonProgress.lessonId, lessonId))),
    l.documentId ? contentDb.select().from(documents).where(eq(documents.id, l.documentId)) : [],
    l.videoAssetId ? contentDb.select().from(videoAssets).where(eq(videoAssets.id, l.videoAssetId)) : [],
  ]);
  const visible = flat.filter((x) => all || x.isPreview);
  const i = visible.findIndex((x) => x.id === l.id);
  const d = doc[0];
  const v = va[0];
  let document: LessonDetail['document'] = null;
  if (d) {
    const url = d.storageKey && d.storageBucket ? await storage().signedDownloadUrl(d.storageBucket as Bucket, d.storageKey, 900) : (d.externalUrl ?? '');
    document = { id: d.id, title: d.title, url, pages: d.pages };
  }
  // record "last opened" for non-video lessons so My Courses resumes here
  if (a.enrolled && l.type !== 'video') await recomputeProgress(userId, l.courseId, l.id);
  return {
    id: l.id,
    courseId: l.courseId,
    title: l.title,
    type: l.type as 'video',
    durationSec: l.durationSec,
    isImportant: l.isImportant,
    body: l.body,
    document,
    video: v ? { kind: v.provider === 'external_link' && v.externalUrl ? externalKind(v.externalUrl) : 'hls', status: v.status } : null,
    completed: !!p?.completedAt,
    lastPositionSec: p?.lastPositionSec ?? 0,
    prevLessonId: i > 0 ? visible[i - 1]!.id : null,
    nextLessonId: i >= 0 && i < visible.length - 1 ? visible[i + 1]!.id : null,
  };
}

/** Max 3 playback devices per 30 days; a new playback takes over the single active stream. */
async function claimDevice(userId: string, deviceId: string) {
  const k = `pd:${userId}`;
  const now = Date.now();
  await redis().zremrangebyscore(k, 0, now - DEVICE_WINDOW_MS);
  const known = await redis().zscore(k, deviceId);
  if (known === null && (await redis().zcard(k)) >= MAX_DEVICES) throw forbidden(`সর্বোচ্চ ${MAX_DEVICES}টি ডিভাইসে কোর্স দেখা যায়। পুরোনো ডিভাইস ৩০ দিন ব্যবহার না হলে নিজে থেকেই সরে যায় — জরুরি হলে সাপোর্টে যোগাযোগ করুন।`);
  await redis().zadd(k, now, deviceId);
  await redis().pexpire(k, DEVICE_WINDOW_MS);
}

export async function playback(userId: string, lessonId: string, b: { deviceId: string }, roles: string[]) {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId, roles);
  requireLessonAccess(a, l);
  if (l.type !== 'video' || !l.videoAssetId) throw badRequest('এই লেসনে ভিডিও নেই।');
  const [v] = await contentDb.select().from(videoAssets).where(eq(videoAssets.id, l.videoAssetId));
  if (!v) throw notFound('ভিডিওটি পাওয়া যায়নি।');
  if (v.provider !== 'external_link' && v.status !== 'ready') throw conflict('ভিডিওটি এখনো প্রসেস হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করুন।');
  if (!a.isOwner && !a.isAdmin) await claimDevice(userId, b.deviceId);
  const streamToken = randomBytes(12).toString('base64url');
  await redis().set(`ps:${userId}`, streamToken, 'EX', STREAM_TTL_SEC);
  const [[u], [p]] = await Promise.all([
    coreDb.select({ phone: users.phone, email: users.email }).from(users).where(eq(users.id, userId)),
    coreDb.select({ pos: lessonProgress.lastPositionSec }).from(lessonProgress).where(and(eq(lessonProgress.studentId, userId), eq(lessonProgress.lessonId, lessonId))),
  ]);
  const who = u?.phone ? `••${u.phone.slice(-4)}` : (u?.email?.split('@')[0]?.slice(0, 6) ?? '');
  const watermark = `ShikkhaSathi · ${who} · ${userId.slice(-6)}`;
  let kind: 'hls' | 'youtube' | 'external' = 'hls';
  let url: string;
  if (v.provider === 'external_link') {
    kind = externalKind(v.externalUrl ?? '');
    url = kind === 'youtube' ? `https://www.youtube-nocookie.com/embed/${youtubeId(v.externalUrl!)}?rel=0&modestbranding=1` : (v.externalUrl ?? '');
  } else {
    url = await (await videoProvider()).playbackUrl(v.providerVideoId ?? '', PLAYBACK_TTL_SEC);
  }
  const pos = p?.pos ?? 0;
  return { kind, url, expiresAt: new Date(Date.now() + PLAYBACK_TTL_SEC * 1000).toISOString(), streamToken, watermark, startAtSec: l.durationSec && pos > l.durationSec - 10 ? 0 : pos };
}

export async function documentUrl(userId: string, id: string, roles: string[]) {
  const [d] = await contentDb.select().from(documents).where(eq(documents.id, id));
  if (!d?.courseId) throw notFound();
  const a = await access(userId, d.courseId, roles);
  if (!canSeeAll(a)) throw forbidden('এই রিসোর্স দেখতে কোর্সটি কিনুন।');
  const url = d.storageKey && d.storageBucket ? await storage().signedDownloadUrl(d.storageBucket as Bucket, d.storageKey, 900) : (d.externalUrl ?? '');
  if (!url) throw notFound();
  return { url, title: d.title };
}

// ---------------------------------------------------------------- notes & bookmarks

async function lessonTitles(lessonIds: string[]) {
  if (!lessonIds.length) return { ls: [] as { id: string; title: string; courseId: string }[], cs: [] as { id: string; title: string }[] };
  const ls = await contentDb.select({ id: lessons.id, title: lessons.title, courseId: lessons.courseId }).from(lessons).where(inArray(lessons.id, [...new Set(lessonIds)]));
  const cs = ls.length ? await contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(inArray(courses.id, [...new Set(ls.map((l) => l.courseId))])) : [];
  return { ls, cs };
}

async function toNotes(rows: (typeof notes.$inferSelect)[]): Promise<Note[]> {
  const { ls, cs } = await lessonTitles(rows.map((r) => r.lessonId));
  return rows.map((n) => ({ id: n.id, courseId: n.courseId, lessonId: n.lessonId, lessonTitle: ls.find((l) => l.id === n.lessonId)?.title ?? '', courseTitle: cs.find((c) => c.id === n.courseId)?.title ?? '', timestampSec: n.timestampSec, body: n.body, createdAt: n.createdAt.toISOString(), updatedAt: n.updatedAt.toISOString() }));
}
async function toBookmarks(rows: (typeof bookmarks.$inferSelect)[]): Promise<Bookmark[]> {
  const { ls, cs } = await lessonTitles(rows.map((r) => r.lessonId));
  return rows.map((n) => ({ id: n.id, courseId: n.courseId, lessonId: n.lessonId, lessonTitle: ls.find((l) => l.id === n.lessonId)?.title ?? '', courseTitle: cs.find((c) => c.id === n.courseId)?.title ?? '', timestampSec: n.timestampSec, body: n.body, createdAt: n.createdAt.toISOString() }));
}

async function lessonForWrite(userId: string, lessonId: string) {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId);
  requireLessonAccess(a, l);
  return l;
}

export async function listNotes(userId: string, q: { courseId?: string; lessonId?: string; q?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(notes.studentId, userId)];
  if (q.courseId) conds.push(eq(notes.courseId, q.courseId));
  if (q.lessonId) conds.push(eq(notes.lessonId, q.lessonId));
  if (q.q) conds.push(ilike(notes.body, `%${q.q.replace(/[%_]/g, '')}%`));
  if (cur) conds.push(or(lt(notes.createdAt, new Date(String(cur[0]))), and(eq(notes.createdAt, new Date(String(cur[0]))), lt(notes.id, cur[1])))!);
  const rows = await coreDb.select().from(notes).where(and(...conds)).orderBy(desc(notes.createdAt), desc(notes.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await toNotes(pg.items), nextCursor: pg.nextCursor };
}

export async function createNote(userId: string, b: { lessonId: string; timestampSec?: number | null; body: string }) {
  const l = await lessonForWrite(userId, b.lessonId);
  const [n] = await coreDb.insert(notes).values({ studentId: userId, courseId: l.courseId, lessonId: l.id, timestampSec: b.timestampSec ?? null, body: b.body }).returning();
  return (await toNotes([n!]))[0]!;
}

async function ownNote(userId: string, id: string) {
  const [n] = await coreDb.select().from(notes).where(eq(notes.id, id));
  if (!n) throw notFound();
  if (n.studentId !== userId) throw forbidden();
  return n;
}
export async function updateNote(userId: string, id: string, body: string) {
  await ownNote(userId, id);
  const [n] = await coreDb.update(notes).set({ body, updatedAt: new Date() }).where(eq(notes.id, id)).returning();
  return (await toNotes([n!]))[0]!;
}
export async function deleteNote(userId: string, id: string) {
  await ownNote(userId, id);
  await coreDb.delete(notes).where(eq(notes.id, id));
}

export async function listBookmarks(userId: string, q: { courseId?: string; lessonId?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(bookmarks.studentId, userId)];
  if (q.courseId) conds.push(eq(bookmarks.courseId, q.courseId));
  if (q.lessonId) conds.push(eq(bookmarks.lessonId, q.lessonId));
  if (cur) conds.push(or(lt(bookmarks.createdAt, new Date(String(cur[0]))), and(eq(bookmarks.createdAt, new Date(String(cur[0]))), lt(bookmarks.id, cur[1])))!);
  const rows = await coreDb.select().from(bookmarks).where(and(...conds)).orderBy(desc(bookmarks.createdAt), desc(bookmarks.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await toBookmarks(pg.items), nextCursor: pg.nextCursor };
}
export async function createBookmark(userId: string, b: { lessonId: string; timestampSec?: number | null; body?: string }) {
  const l = await lessonForWrite(userId, b.lessonId);
  const [n] = await coreDb.insert(bookmarks).values({ studentId: userId, courseId: l.courseId, lessonId: l.id, timestampSec: b.timestampSec ?? null, body: b.body ?? null }).returning();
  return (await toBookmarks([n!]))[0]!;
}
export async function deleteBookmark(userId: string, id: string) {
  const [n] = await coreDb.select().from(bookmarks).where(eq(bookmarks.id, id));
  if (!n) throw notFound();
  if (n.studentId !== userId) throw forbidden();
  await coreDb.delete(bookmarks).where(eq(bookmarks.id, id));
}

// ---------------------------------------------------------------- Q&A

async function toQuestions(rows: (typeof questions.$inferSelect)[], viewerId: string): Promise<Question[]> {
  if (!rows.length) return [];
  const ans = await coreDb.select().from(answers).where(and(inArray(answers.questionId, rows.map((r) => r.id)), eq(answers.status, 'visible'))).orderBy(answers.createdAt);
  const userIds = [...new Set([...rows.map((r) => r.authorId), ...ans.map((a) => a.authorId)])];
  const [people, sellers, { ls }] = await Promise.all([
    coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.id, userIds)),
    coreDb.select({ id: sellerProfiles.userId, name: sellerProfiles.displayName }).from(sellerProfiles).where(inArray(sellerProfiles.userId, userIds)),
    lessonTitles(rows.map((r) => r.lessonId)),
  ]);
  const nameOf = (id: string, isSeller = false) => (isSeller ? sellers.find((s) => s.id === id)?.name : undefined) ?? shortName(people.find((p) => p.id === id)?.name ?? '');
  return rows.map((q) => ({
    id: q.id,
    lessonId: q.lessonId,
    lessonTitle: ls.find((l) => l.id === q.lessonId)?.title ?? null,
    body: q.body,
    timestampSec: q.timestampSec,
    authorName: nameOf(q.authorId),
    mine: q.authorId === viewerId,
    status: q.status as 'open',
    createdAt: q.createdAt.toISOString(),
    answers: ans.filter((a) => a.questionId === q.id).map((a) => ({ id: a.id, body: a.body, authorName: nameOf(a.authorId, a.isSeller), isSeller: a.isSeller, createdAt: a.createdAt.toISOString() })),
  }));
}

async function qaAccess(userId: string, lessonId: string, roles: string[] = []) {
  const l = await lessonOr404(lessonId);
  const a = await access(userId, l.courseId, roles);
  if (!canSeeAll(a)) throw forbidden('প্রশ্নোত্তর দেখতে কোর্সটিতে এনরোল করুন।');
  return { l, a };
}

export async function listQuestions(userId: string, lessonId: string, q: { cursor?: string; limit: number }, roles: string[]) {
  await qaAccess(userId, lessonId, roles);
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(questions.lessonId, lessonId), or(sql`${questions.status} <> 'hidden'`, eq(questions.authorId, userId))!];
  if (cur) conds.push(lt(questions.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(questions).where(and(...conds)).orderBy(desc(questions.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await toQuestions(pg.items, userId), nextCursor: pg.nextCursor };
}

export async function ask(userId: string, lessonId: string, b: { body: string; timestampSec?: number | null }) {
  const { l, a } = await qaAccess(userId, lessonId);
  if (a.isOwner) throw badRequest('নিজের কোর্সে প্রশ্ন না করে উত্তর দিন।');
  const [q] = await coreDb.insert(questions).values({ lessonId, courseId: l.courseId, sellerId: a.course.sellerId, authorId: userId, body: b.body, timestampSec: b.timestampSec ?? null }).returning();
  await notify({ userId: a.course.sellerId, type: 'seller.qa_new', ref: q!.id, title: 'নতুন প্রশ্ন এসেছে', body: `${l.title}: ${b.body.slice(0, 120)}`, link: `/learn/${l.courseId}/${l.id}?tab=qa` });
  return (await toQuestions([q!], userId))[0]!;
}

export async function answer(userId: string, questionId: string, body: string) {
  const [q] = await coreDb.select().from(questions).where(eq(questions.id, questionId));
  if (!q || q.status === 'hidden') throw notFound();
  const { a, l } = await qaAccess(userId, q.lessonId);
  const [ans] = await coreDb.insert(answers).values({ questionId, authorId: userId, body, isSeller: a.isOwner }).returning();
  if (a.isOwner) await coreDb.update(questions).set({ status: 'answered', updatedAt: new Date() }).where(eq(questions.id, questionId));
  if (q.authorId !== userId) await notify({ userId: q.authorId, type: 'course.qa_answered', ref: ans!.id, title: a.isOwner ? 'শিক্ষক আপনার প্রশ্নের উত্তর দিয়েছেন' : 'আপনার প্রশ্নে নতুন উত্তর', body: body.slice(0, 140), link: `/learn/${l.courseId}/${l.id}?tab=qa` });
  return (await toQuestions([(await coreDb.select().from(questions).where(eq(questions.id, questionId)))[0]!], userId))[0]!;
}

export async function sellerQuestions(sellerId: string, q: { status: 'open' | 'answered' | 'all'; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(questions.sellerId, sellerId), sql`${questions.status} <> 'hidden'`];
  if (q.status !== 'all') conds.push(eq(questions.status, q.status));
  if (cur) conds.push(lt(questions.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(questions).where(and(...conds)).orderBy(desc(questions.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  const base = await toQuestions(pg.items, sellerId);
  const cs = pg.items.length ? await contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(inArray(courses.id, [...new Set(pg.items.map((x) => x.courseId))])) : [];
  return { items: base.map((b, i) => ({ ...b, courseId: pg.items[i]!.courseId, courseTitle: cs.find((c) => c.id === pg.items[i]!.courseId)?.title ?? '' })), nextCursor: pg.nextCursor };
}

// ---------------------------------------------------------------- reviews

async function toReviews(rows: (typeof reviews.$inferSelect)[]): Promise<Review[]> {
  if (!rows.length) return [];
  const people = await coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.id, [...new Set(rows.map((r) => r.studentId))]));
  return rows.map((r) => ({ id: r.id, rating: r.rating, body: r.body, authorName: shortName(people.find((p) => p.id === r.studentId)?.name ?? ''), createdAt: r.createdAt.toISOString(), sellerReply: r.sellerReply, sellerRepliedAt: r.sellerRepliedAt?.toISOString() ?? null, verified: r.verified }));
}

export async function listReviews(courseId: string, q: { rating?: number; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(reviews.courseId, courseId), eq(reviews.status, 'visible')];
  if (q.rating) conds.push(eq(reviews.rating, q.rating));
  if (cur) conds.push(lt(reviews.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(reviews).where(and(...conds)).orderBy(desc(reviews.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await toReviews(pg.items), nextCursor: pg.nextCursor };
}

async function recomputeRating(courseId: string) {
  const [r] = await coreDb.select({ avg: sql<string>`coalesce(round(avg(${reviews.rating})::numeric, 2), 0)::text`, n: sql<number>`count(*)::int` }).from(reviews).where(and(eq(reviews.courseId, courseId), eq(reviews.status, 'visible')));
  await contentDb.update(courses).set({ ratingAvg: r?.avg ?? '0', ratingCount: r?.n ?? 0 }).where(eq(courses.id, courseId));
}

export async function upsertReview(userId: string, courseId: string, b: { rating: number; body?: string }) {
  const a = await access(userId, courseId);
  if (!a.enrolled) throw forbidden('শুধু কোর্স কেনা শিক্ষার্থীরা রিভিউ দিতে পারেন।');
  if ((a.enrollment?.progressPercent ?? 0) < REVIEW_MIN_PROGRESS) throw forbidden(`কোর্সের অন্তত ${REVIEW_MIN_PROGRESS}% শেষ করে রিভিউ দিন — তাহলে রিভিউটা অন্যদের কাজে লাগবে।`);
  const [r] = await coreDb
    .insert(reviews)
    .values({ courseId, sellerId: a.course.sellerId, studentId: userId, rating: b.rating, body: b.body || null })
    .onConflictDoUpdate({ target: [reviews.courseId, reviews.studentId], set: { rating: b.rating, body: b.body || null, updatedAt: new Date() } })
    .returning();
  await recomputeRating(courseId);
  return (await toReviews([r!]))[0]!;
}

export async function sellerReviews(sellerId: string, q: { cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(reviews.sellerId, sellerId), eq(reviews.status, 'visible')];
  if (cur) conds.push(lt(reviews.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(reviews).where(and(...conds)).orderBy(desc(reviews.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  const base = await toReviews(pg.items);
  const cs = pg.items.length ? await contentDb.select({ id: courses.id, title: courses.title }).from(courses).where(inArray(courses.id, [...new Set(pg.items.map((x) => x.courseId))])) : [];
  return { items: base.map((b, i) => ({ ...b, courseId: pg.items[i]!.courseId, courseTitle: cs.find((c) => c.id === pg.items[i]!.courseId)?.title ?? '' })), nextCursor: pg.nextCursor };
}

export async function replyReview(sellerId: string, id: string, reply: string) {
  const [r] = await coreDb.select().from(reviews).where(eq(reviews.id, id));
  if (!r) throw notFound();
  if (r.sellerId !== sellerId) throw forbidden();
  if (r.sellerReply) throw conflict('এই রিভিউর উত্তর আগেই দেওয়া হয়েছে।');
  const [u] = await coreDb.update(reviews).set({ sellerReply: reply, sellerRepliedAt: new Date() }).where(eq(reviews.id, id)).returning();
  return (await toReviews([u!]))[0]!;
}
