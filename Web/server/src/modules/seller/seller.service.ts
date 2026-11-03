import { and, asc, count, desc, eq, inArray, isNull, lt, notInArray, or, sql, type SQL } from 'drizzle-orm';
import type { z } from 'zod';
import type { CourseBasics, CoursePricing, CurriculumBody, SellerApplyBody, SellerCourse, TargetInput, UploadBody } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { authOtps, sellerProfiles, users } from '../../database/core/schema';
import { courseChapters, courseRevisions, courseSections, courseTargets, courses, departments, documents, institutions, lessons, subjectMasters, videoAssets } from '../../database/content/schema';
import { AppError, badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { decrypt, encrypt, hmac, randomDigits, safeEqual } from '../../common/crypto';
import { audit } from '../../common/audit';
import { decodeCursor, toPage } from '../../common/pagination';
import { enqueue } from '../../common/jobs';
import { setVerifiedSellerCheck } from '../../common/route';
import { hitLimit } from '../../common/redis';
import { sendEmail } from '../../services/email';
import { storage, EXT_BY_MIME, objectKey } from '../../services/storage';
import { videoProvider } from '../../services/video';
import { notify } from '../notifications/notifications.service';
import { getBusinessNumber } from '../settings/settings.service';
import { lookupAcademicNames } from '../catalog/catalog.lookup';
import { onCoursePublished } from '../requests/requests.service';

type SP = typeof sellerProfiles.$inferSelect;
type CourseRow = typeof courses.$inferSelect;

const COVER_COLORS = ['#4F46E5', '#0EA5E9', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#14B8A6', '#F97316'];

function maskAccount(v: string) {
  const d = v.replace(/\s/g, '');
  return d.length <= 4 ? '••••' : `${'•'.repeat(Math.min(6, d.length - 4))}${d.slice(-4)}`;
}

async function toProfile(p: SP) {
  const names = await lookupAcademicNames({ institutionIds: p.institutionId ? [p.institutionId] : [], departmentIds: [], programIds: [] });
  const ev = (p.verificationData ?? {}) as Record<string, unknown>;
  return {
    userId: p.userId,
    slug: p.slug,
    displayName: p.displayName,
    headline: p.headline,
    bio: p.bio,
    coverUrl: p.coverUrl,
    institutionId: p.institutionId,
    institutionName: p.institutionId ? (names.institution.get(p.institutionId)?.name ?? null) : null,
    verificationStatus: p.verificationStatus as 'none' | 'pending' | 'verified' | 'rejected',
    verificationMethod: (p.verificationMethod as 'university_email' | 'id_card' | 'linkedin' | 'reference' | null) ?? null,
    verificationNote: p.verificationNote,
    universityEmailVerified: ev.universityEmailVerified === true,
    payoutMethod: (p.payoutMethod as 'bkash' | 'nagad' | 'bank' | null) ?? null,
    payoutAccountMasked: p.payoutAccountMasked,
    commissionRate: p.commissionRate,
    verifiedAt: p.verifiedAt?.toISOString() ?? null,
  };
}

const profileOf = async (userId: string) => (await coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.userId, userId)))[0];

setVerifiedSellerCheck(async (userId) => (await profileOf(userId))?.verificationStatus === 'verified');

// ---------------------------------------------------------------- application

export async function getProfile(userId: string) {
  const p = await profileOf(userId);
  return { profile: p ? await toProfile(p) : null };
}

export async function apply(userId: string, b: z.infer<typeof SellerApplyBody>) {
  const [clash] = await coreDb.select({ userId: sellerProfiles.userId }).from(sellerProfiles).where(eq(sellerProfiles.slug, b.slug));
  if (clash && clash.userId !== userId) throw conflict('এই লিংক (slug) অন্য কেউ ব্যবহার করছেন।');
  if (b.method === 'university_email' && !b.evidence.universityEmail) throw badRequest('বিশ্ববিদ্যালয়ের ইমেইল দিন।');
  if (b.method === 'id_card' && !b.evidence.idCardKey) throw badRequest('আইডি কার্ডের ছবি আপলোড করুন।');
  if (b.method === 'linkedin' && !b.evidence.linkedinUrl) throw badRequest('LinkedIn প্রোফাইল লিংক দিন।');
  if (b.method === 'reference' && !(b.evidence.referenceName && b.evidence.referenceContact)) throw badRequest('রেফারেন্সের নাম ও যোগাযোগ দিন।');
  if (b.institutionId) {
    const [i] = await contentDb.select({ id: institutions.id }).from(institutions).where(eq(institutions.id, b.institutionId));
    if (!i) throw badRequest('প্রতিষ্ঠানটি তালিকায় নেই।');
  }
  const existing = await profileOf(userId);
  const prevEv = (existing?.verificationData ?? {}) as Record<string, unknown>;
  const evidence = { ...b.evidence, universityEmailVerified: prevEv.universityEmailVerified === true && prevEv.universityEmail === b.evidence.universityEmail };
  const status = existing?.verificationStatus === 'verified' ? 'verified' : 'pending';
  const values = {
    slug: b.slug,
    displayName: b.displayName,
    headline: b.headline ?? null,
    bio: b.bio ?? null,
    institutionId: b.institutionId,
    verificationStatus: status,
    verificationMethod: b.method,
    verificationData: evidence,
    payoutMethod: b.payoutMethod,
    payoutAccountEnc: encrypt(b.payoutAccount),
    payoutAccountMasked: maskAccount(b.payoutAccount),
  };
  if (existing) await coreDb.update(sellerProfiles).set({ ...values, updatedAt: new Date() }).where(eq(sellerProfiles.userId, userId));
  else await coreDb.insert(sellerProfiles).values({ userId, ...values });
  await coreDb.update(users).set({ roles: sql`(select array_agg(distinct r) from unnest(${users.roles} || ARRAY['seller']::text[]) r)` }).where(eq(users.id, userId));
  await audit({ actorId: userId, action: existing ? 'seller.application_update' : 'seller.apply', entity: 'seller_profiles', entityId: userId, after: { method: b.method, status } });
  return toProfile((await profileOf(userId))!);
}

export async function requestUniversityEmailCode(userId: string, email: string) {
  const domain = email.split('@')[1] ?? '';
  if (/^(gmail|yahoo|outlook|hotmail|icloud|proton)\./i.test(domain)) throw badRequest('বিশ্ববিদ্যালয়ের অফিসিয়াল ইমেইল দিন (ব্যক্তিগত Gmail নয়)।');
  const lim = await hitLimit(`seller-email:${userId}`, 5, 3600);
  if (!lim.allowed) throw new AppError('RATE_LIMITED', undefined, { retryAfterSec: lim.retryAfterSec });
  const code = randomDigits(6);
  await coreDb.insert(authOtps).values({ target: `seller:${userId}:${email}`, codeHash: hmac(`seller:${userId}:${email}:${code}`), purpose: 'seller_email', expiresAt: new Date(Date.now() + 10 * 60_000) });
  await sendEmail({ to: email, template: 'email_otp', data: { code }, relatedEntity: `seller:${userId}` });
}

export async function confirmUniversityEmail(userId: string, email: string, code: string) {
  const target = `seller:${userId}:${email}`;
  const [otp] = await coreDb.select().from(authOtps).where(and(eq(authOtps.target, target), eq(authOtps.purpose, 'seller_email'), isNull(authOtps.consumedAt))).orderBy(desc(authOtps.createdAt)).limit(1);
  if (!otp || otp.expiresAt < new Date() || otp.attempts >= 5) throw badRequest('কোডের মেয়াদ শেষ। নতুন কোড নিন।');
  if (!safeEqual(otp.codeHash, hmac(`${target}:${code}`))) {
    await coreDb.update(authOtps).set({ attempts: otp.attempts + 1 }).where(eq(authOtps.id, otp.id));
    throw badRequest('কোডটি সঠিক নয়।');
  }
  await coreDb.update(authOtps).set({ consumedAt: new Date() }).where(eq(authOtps.id, otp.id));
  const p = await profileOf(userId);
  if (!p) throw badRequest('আগে আবেদন ফর্ম পূরণ করুন।');
  const ev = { ...((p.verificationData ?? {}) as Record<string, unknown>), universityEmail: email, universityEmailVerified: true };
  await coreDb.update(sellerProfiles).set({ verificationData: ev, verificationMethod: 'university_email' }).where(eq(sellerProfiles.userId, userId));
  return toProfile((await profileOf(userId))!);
}

export async function updateStorefront(userId: string, b: { slug?: string; displayName?: string; headline?: string | null; bio?: string | null; coverUrl?: string | null }) {
  const p = await profileOf(userId);
  if (!p) throw notFound();
  if (b.slug && b.slug !== p.slug) {
    const [clash] = await coreDb.select({ userId: sellerProfiles.userId }).from(sellerProfiles).where(eq(sellerProfiles.slug, b.slug));
    if (clash) throw conflict('এই লিংক (slug) অন্য কেউ ব্যবহার করছেন।');
  }
  await coreDb.update(sellerProfiles).set({ ...b, updatedAt: new Date() }).where(eq(sellerProfiles.userId, userId));
  if (b.displayName) await contentDb.update(courses).set({ sellerName: b.displayName }).where(eq(courses.sellerId, userId));
  return toProfile((await profileOf(userId))!);
}

// ---------------------------------------------------------------- admin verification

async function adminItem(p: SP) {
  const [u] = await coreDb.select({ name: users.name, email: users.email, phone: users.phone }).from(users).where(eq(users.id, p.userId));
  const [c] = await contentDb.select({ value: count() }).from(courses).where(eq(courses.sellerId, p.userId));
  const ev = (p.verificationData ?? null) as Record<string, unknown> | null;
  const idKey = typeof ev?.idCardKey === 'string' ? ev.idCardKey : null;
  return {
    ...(await toProfile(p)),
    name: u?.name ?? '',
    email: u?.email ?? null,
    phone: u?.phone ?? null,
    evidence: ev,
    idCardUrl: idKey ? await storage().signedDownloadUrl('verification-docs', idKey, 600) : null,
    appliedAt: p.createdAt.toISOString(),
    courseCount: c?.value ?? 0,
  };
}

export async function adminListSellers(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(sellerProfiles.verificationStatus, q.status)];
  if (cur) conds.push(or(lt(sellerProfiles.updatedAt, new Date(String(cur[0]))), and(eq(sellerProfiles.updatedAt, new Date(String(cur[0]))), lt(sellerProfiles.userId, cur[1])))!);
  const rows = await coreDb.select().from(sellerProfiles).where(and(...conds)).orderBy(desc(sellerProfiles.updatedAt), desc(sellerProfiles.userId)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.updatedAt.toISOString(), r.userId]);
  return { items: await Promise.all(pg.items.map(adminItem)), nextCursor: pg.nextCursor };
}

export async function adminDecideSeller(actorId: string, userId: string, d: { decision: 'verified' | 'rejected'; note?: string; commissionRate?: number }) {
  const p = await profileOf(userId);
  if (!p) throw notFound();
  await coreDb
    .update(sellerProfiles)
    .set({ verificationStatus: d.decision, verificationNote: d.note ?? null, verifiedAt: d.decision === 'verified' ? new Date() : null, ...(d.commissionRate !== undefined ? { commissionRate: d.commissionRate } : {}) })
    .where(eq(sellerProfiles.userId, userId));
  await contentDb.update(courses).set({ sellerVerified: d.decision === 'verified' }).where(eq(courses.sellerId, userId));
  await audit({ actorId, action: `seller.${d.decision}`, entity: 'seller_profiles', entityId: userId, before: { status: p.verificationStatus }, after: { status: d.decision, note: d.note } });
  await notify({
    userId,
    type: 'seller.application_result',
    ref: `${d.decision}:${Date.now()}`,
    title: d.decision === 'verified' ? 'আপনি এখন ভেরিফায়েড শিক্ষক ✓' : 'শিক্ষক আবেদন আবার দেখুন',
    body: d.decision === 'verified' ? 'কোর্স বানিয়ে রিভিউয়ের জন্য পাঠাতে পারবেন।' : (d.note ?? 'আবেদনে কিছু তথ্য ঠিক নেই।'),
    link: d.decision === 'verified' ? '/teach' : '/teach/verification',
    email: { template: 'seller_application_result', data: { status: d.decision, note: d.note ?? '' } },
  });
  return adminItem((await profileOf(userId))!);
}

// ---------------------------------------------------------------- course read model

const ownCourse = async (sellerId: string, id: string) => {
  const [c] = await contentDb.select().from(courses).where(and(eq(courses.id, id), isNull(courses.deletedAt)));
  if (!c) throw notFound('কোর্সটি পাওয়া যায়নি।');
  if (c.sellerId !== sellerId) throw forbidden();
  return c;
};

export async function loadCourse(c: CourseRow): Promise<SellerCourse> {
  const [targets, secs, chs, ls, docs, revs] = await Promise.all([
    contentDb.select().from(courseTargets).where(eq(courseTargets.courseId, c.id)),
    contentDb.select().from(courseSections).where(eq(courseSections.courseId, c.id)).orderBy(asc(courseSections.position)),
    contentDb.select().from(courseChapters).where(eq(courseChapters.courseId, c.id)).orderBy(asc(courseChapters.position)),
    contentDb.select().from(lessons).where(eq(lessons.courseId, c.id)).orderBy(asc(lessons.position)),
    contentDb.select().from(documents).where(eq(documents.courseId, c.id)).orderBy(asc(documents.createdAt)),
    contentDb.select({ id: courseRevisions.id, payload: courseRevisions.payloadJson }).from(courseRevisions).where(and(eq(courseRevisions.courseId, c.id), eq(courseRevisions.status, 'pending'))),
  ]);
  const videoIds = [...new Set(ls.map((l) => l.videoAssetId).filter((v): v is string => !!v))];
  const vids = videoIds.length ? await contentDb.select().from(videoAssets).where(inArray(videoAssets.id, videoIds)) : [];
  const instIds = targets.map((t) => t.institutionId).filter((v): v is string => !!v);
  const deptIds = targets.map((t) => t.departmentId).filter((v): v is string => !!v);
  const subjIds = targets.map((t) => t.subjectMasterId).filter((v): v is string => !!v);
  const [names, subjects] = await Promise.all([
    lookupAcademicNames({ institutionIds: instIds, departmentIds: deptIds, programIds: [] }),
    subjIds.length ? contentDb.select({ id: subjectMasters.id, name: subjectMasters.name }).from(subjectMasters).where(inArray(subjectMasters.id, subjIds)) : [],
  ]);
  const toDoc = (d: typeof documents.$inferSelect) => ({ id: d.id, kind: d.kind as 'notes', title: d.title, year: d.year, mime: d.mime, size: d.size, externalUrl: d.externalUrl, hasFile: !!d.storageKey });
  const toVid = (v: typeof videoAssets.$inferSelect) => ({ id: v.id, provider: v.provider, status: v.status, durationSec: v.durationSec, title: v.title, externalUrl: v.externalUrl });
  const rev = (revs[0]?.payload ?? null) as Record<string, unknown> | null;
  const base: SellerCourse = {
    id: c.id,
    slug: c.slug,
    status: c.status as SellerCourse['status'],
    reviewNote: c.reviewNote,
    title: c.title,
    shortDescription: c.shortDescription,
    description: c.description,
    learnings: c.learnings,
    notFor: c.notFor,
    faq: c.faq,
    coverUrl: c.coverUrl,
    coverColor: c.coverColor,
    introVideoId: c.introVideoId,
    language: c.language as 'bn',
    examType: c.examType as SellerCourse['examType'],
    courseType: c.courseType as SellerCourse['courseType'],
    price: c.price,
    compareAtPrice: c.compareAtPrice,
    accessType: c.accessType as SellerCourse['accessType'],
    accessDays: c.accessDays,
    accessUntil: c.accessUntil?.toISOString() ?? null,
    targets: targets.map((t) => ({
      id: t.id,
      isGeneral: t.isGeneral,
      institutionId: t.institutionId,
      departmentId: t.departmentId,
      programId: t.programId,
      academicLevel: t.academicLevel,
      semester: t.semester,
      subjectMasterId: t.subjectMasterId,
      courseCode: t.courseCode,
      batches: t.batches,
      sections: t.sections,
      institutionShort: t.institutionId ? (names.institution.get(t.institutionId)?.shortName ?? null) : null,
      departmentShort: t.departmentId ? (names.department.get(t.departmentId)?.shortName ?? null) : null,
      subjectName: subjects.find((s) => s.id === t.subjectMasterId)?.name ?? null,
    })),
    sections: secs.map((s) => ({
      id: s.id,
      title: s.title,
      chapters: chs
        .filter((ch) => ch.sectionId === s.id)
        .map((ch) => ({
          id: ch.id,
          title: ch.title,
          lessons: ls
            .filter((l) => l.chapterId === ch.id)
            .map((l) => {
              const v = vids.find((x) => x.id === l.videoAssetId);
              const d = docs.find((x) => x.id === l.documentId);
              return { id: l.id, title: l.title, type: l.type as 'video', durationSec: l.durationSec, videoAssetId: l.videoAssetId, documentId: l.documentId, body: l.body, isPreview: l.isPreview, isImportant: l.isImportant, video: v ? toVid(v) : null, document: d ? toDoc(d) : null };
            }),
        })),
    })),
    documents: docs.map(toDoc),
    lessonCount: c.lessonCount,
    totalDurationSec: c.totalDurationSec,
    updatedAt: c.updatedAt.toISOString(),
    publishedAt: c.publishedAt?.toISOString() ?? null,
    submittedAt: c.submittedAt?.toISOString() ?? null,
    pendingRevision: !!rev,
  };
  // Sellers see their pending revision values (what they are editing); the public sees the live course.
  if (rev) Object.assign(base, Object.fromEntries(Object.entries(rev).filter(([k]) => k !== 'targets')));
  return base;
}

// ---------------------------------------------------------------- course authoring

const slugify = (s: string) =>
  s
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60);

export async function listMyCourses(sellerId: string) {
  const rows = await contentDb.select().from(courses).where(and(eq(courses.sellerId, sellerId), isNull(courses.deletedAt))).orderBy(desc(courses.updatedAt));
  const codes = rows.length ? await contentDb.select({ courseId: courseTargets.courseId, code: courseTargets.courseCode }).from(courseTargets).where(inArray(courseTargets.courseId, rows.map((r) => r.id))) : [];
  return {
    items: rows.map((c) => ({ id: c.id, slug: c.slug, title: c.title, status: c.status as SellerCourse['status'], price: c.price, lessonCount: c.lessonCount, enrollmentCount: c.enrollmentCount, ratingAvg: Number(c.ratingAvg), ratingCount: c.ratingCount, updatedAt: c.updatedAt.toISOString(), coverColor: c.coverColor, courseCode: codes.find((x) => x.courseId === c.id && x.code)?.code ?? null })),
  };
}

export async function createCourse(sellerId: string, title: string) {
  const p = await profileOf(sellerId);
  if (!p) throw forbidden('আগে শিক্ষক হিসেবে আবেদন করুন।');
  const base = slugify(title) || 'course';
  const slug = `${base}-${Math.random().toString(36).slice(2, 7)}`;
  const [{ value: n } = { value: 0 }] = await contentDb.select({ value: count() }).from(courses).where(eq(courses.sellerId, sellerId));
  const [row] = await contentDb
    .insert(courses)
    .values({ sellerId, title, slug, sellerName: p.displayName, sellerVerified: p.verificationStatus === 'verified', coverColor: COVER_COLORS[n % COVER_COLORS.length]! })
    .returning();
  await audit({ actorId: sellerId, action: 'course.create', entity: 'courses', entityId: row!.id });
  return loadCourse(row!);
}

export const getMyCourse = async (sellerId: string, id: string) => loadCourse(await ownCourse(sellerId, id));

/** Fields that, once published, go through review again (spec §6.12). */
const REVIEWED_FIELDS = ['title', 'price', 'compareAtPrice'] as const;

export async function updateCourse(sellerId: string, id: string, patch: Partial<z.infer<typeof CourseBasics> & z.infer<typeof CoursePricing>>) {
  const c = await ownCourse(sellerId, id);
  if (patch.compareAtPrice != null && patch.price != null && patch.compareAtPrice > 0 && patch.compareAtPrice <= patch.price) throw badRequest('আগের দাম বর্তমান দামের চেয়ে বেশি হতে হবে।');
  const set: Record<string, unknown> = {};
  const revision: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(patch)) {
    if (v === undefined) continue;
    const val = k === 'accessUntil' && v ? new Date(v as string) : v;
    if (c.status === 'published' && (REVIEWED_FIELDS as readonly string[]).includes(k) && (c as Record<string, unknown>)[k] !== v) revision[k] = v;
    else set[k] = val;
  }
  if (Object.keys(set).length) {
    if (c.status === 'published') set.needsSpotReview = true;
    if (c.status === 'changes_requested' || c.status === 'rejected') set.status = 'draft';
    await contentDb.update(courses).set({ ...set, contentUpdatedAt: new Date() }).where(eq(courses.id, id));
  }
  if (Object.keys(revision).length) await upsertRevision(id, revision);
  return loadCourse((await contentDb.select().from(courses).where(eq(courses.id, id)))[0]!);
}

async function upsertRevision(courseId: string, patch: Record<string, unknown>) {
  const [existing] = await contentDb.select().from(courseRevisions).where(and(eq(courseRevisions.courseId, courseId), eq(courseRevisions.status, 'pending')));
  if (existing) await contentDb.update(courseRevisions).set({ payloadJson: { ...(existing.payloadJson as object), ...patch } }).where(eq(courseRevisions.id, existing.id));
  else await contentDb.insert(courseRevisions).values({ courseId, payloadJson: patch });
}

async function validateTargets(ts: z.infer<typeof TargetInput>[]) {
  const deptIds = ts.map((t) => t.departmentId).filter((v): v is string => !!v);
  const depts = deptIds.length ? await contentDb.select({ id: departments.id, institutionId: departments.institutionId }).from(departments).where(inArray(departments.id, deptIds)) : [];
  for (const t of ts) {
    if (!t.isGeneral && !t.institutionId) throw badRequest('প্রতিটি টার্গেটে ইউনিভার্সিটি দিন, অথবা "General / Cross-University" বেছে নিন।');
    if (t.departmentId) {
      const d = depts.find((x) => x.id === t.departmentId);
      if (!d || (t.institutionId && d.institutionId !== t.institutionId)) throw badRequest('বিভাগটি নির্বাচিত ইউনিভার্সিটির নয়।');
    }
    if (!t.subjectMasterId && !t.courseCode) throw badRequest('প্রতিটি টার্গেটে সাবজেক্ট বা কোর্স কোড দিন।');
  }
}

export async function setTargets(sellerId: string, id: string, ts: z.infer<typeof TargetInput>[]) {
  const c = await ownCourse(sellerId, id);
  await validateTargets(ts);
  if (c.status === 'published') {
    await upsertRevision(id, { targets: ts });
    return loadCourse(c);
  }
  await replaceTargets(id, ts);
  return loadCourse(c);
}

async function replaceTargets(courseId: string, ts: z.infer<typeof TargetInput>[]) {
  await contentDb.transaction(async (tx) => {
    await tx.delete(courseTargets).where(eq(courseTargets.courseId, courseId));
    if (ts.length)
      await tx.insert(courseTargets).values(
        ts.map((t) => ({
          courseId,
          isGeneral: t.isGeneral,
          institutionId: t.isGeneral ? null : (t.institutionId ?? null),
          departmentId: t.isGeneral ? null : (t.departmentId ?? null),
          programId: t.isGeneral ? null : (t.programId ?? null),
          academicLevel: t.academicLevel ?? null,
          semester: t.semester ?? null,
          subjectMasterId: t.subjectMasterId ?? null,
          courseCode: t.courseCode ? t.courseCode.toUpperCase().replace(/\s+/g, '-') : null,
          batches: t.batches,
          sections: t.sections,
        })),
      );
  });
}

/** Replace the curriculum tree; existing ids are kept (progress/notes stay attached), removed ones deleted. */
export async function setCurriculum(sellerId: string, id: string, body: z.infer<typeof CurriculumBody>) {
  const c = await ownCourse(sellerId, id);
  const vidIds = body.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons.map((l) => l.videoAssetId))).filter((v): v is string => !!v);
  const docIds = body.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons.map((l) => l.documentId))).filter((v): v is string => !!v);
  const [vids, docs] = await Promise.all([
    vidIds.length ? contentDb.select().from(videoAssets).where(inArray(videoAssets.id, vidIds)) : [],
    docIds.length ? contentDb.select().from(documents).where(inArray(documents.id, docIds)) : [],
  ]);
  if (vids.some((v) => v.ownerId !== sellerId) || docs.some((d) => d.ownerId !== sellerId)) throw forbidden();
  let total = 0;
  let lessonCount = 0;
  await contentDb.transaction(async (tx) => {
    const keepSec: string[] = [];
    const keepCh: string[] = [];
    const keepL: string[] = [];
    for (const [si, s] of body.sections.entries()) {
      const [sec] = s.id
        ? await tx.update(courseSections).set({ title: s.title, position: si }).where(and(eq(courseSections.id, s.id), eq(courseSections.courseId, id))).returning()
        : await tx.insert(courseSections).values({ courseId: id, title: s.title, position: si }).returning();
      if (!sec) throw badRequest('অজানা সেকশন');
      keepSec.push(sec.id);
      for (const [ci, ch] of s.chapters.entries()) {
        const [chap] = ch.id
          ? await tx.update(courseChapters).set({ title: ch.title, position: ci, sectionId: sec.id }).where(and(eq(courseChapters.id, ch.id), eq(courseChapters.courseId, id))).returning()
          : await tx.insert(courseChapters).values({ courseId: id, sectionId: sec.id, title: ch.title, position: ci }).returning();
        if (!chap) throw badRequest('অজানা চ্যাপ্টার');
        keepCh.push(chap.id);
        for (const [li, l] of ch.lessons.entries()) {
          const vid = vids.find((v) => v.id === l.videoAssetId);
          const duration = l.durationSec || vid?.durationSec || 0;
          const values = { title: l.title, type: l.type, position: li, durationSec: duration, videoAssetId: l.videoAssetId ?? null, documentId: l.documentId ?? null, body: l.body ?? null, isPreview: l.isPreview, isImportant: l.isImportant, chapterId: chap.id };
          const [row] = l.id ? await tx.update(lessons).set(values).where(and(eq(lessons.id, l.id), eq(lessons.courseId, id))).returning() : await tx.insert(lessons).values({ courseId: id, ...values }).returning();
          if (!row) throw badRequest('অজানা লেসন');
          keepL.push(row.id);
          total += duration;
          lessonCount++;
        }
      }
    }
    const del = async (t: typeof lessons | typeof courseChapters | typeof courseSections, keep: string[]) =>
      tx.delete(t).where(and(eq(t.courseId, id), keep.length ? notInArray(t.id, keep) : undefined));
    await del(lessons, keepL);
    await del(courseChapters, keepCh);
    await del(courseSections, keepSec);
    await tx.update(courses).set({ totalDurationSec: total, lessonCount, contentUpdatedAt: new Date(), ...(c.status === 'published' ? { needsSpotReview: true } : {}) }).where(eq(courses.id, id));
  });
  return loadCourse((await contentDb.select().from(courses).where(eq(courses.id, id)))[0]!);
}

export async function checklist(sellerId: string, id: string) {
  const c = await getMyCourse(sellerId, id);
  const p = await profileOf(sellerId);
  const lessonsAll = c.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons));
  const items = [
    { key: 'verified', ok: p?.verificationStatus === 'verified', message: 'শিক্ষক ভেরিফিকেশন সম্পন্ন' },
    { key: 'title', ok: c.title.length >= 10, message: 'শিরোনাম অন্তত ১০ অক্ষর' },
    { key: 'description', ok: c.shortDescription.length >= 30, message: 'ছোট বিবরণ (অন্তত ৩০ অক্ষর)' },
    { key: 'learnings', ok: c.learnings.length >= 3, message: '"যা শিখবেন" — অন্তত ৩টি পয়েন্ট' },
    { key: 'targets', ok: c.targets.length >= 1, message: 'অন্তত একটি টার্গেট (ইউনিভার্সিটি/সাবজেক্ট)' },
    { key: 'lessons', ok: lessonsAll.length >= 3, message: 'অন্তত ৩টি লেসন' },
    { key: 'preview', ok: lessonsAll.some((l) => l.isPreview), message: 'অন্তত একটি প্রিভিউ লেসন' },
    { key: 'media', ok: lessonsAll.filter((l) => l.type === 'video').every((l) => l.video && l.video.status !== 'failed') && lessonsAll.filter((l) => l.type === 'pdf').every((l) => l.document), message: 'সব ভিডিও/PDF লেসনে ফাইল যুক্ত' },
    { key: 'price', ok: c.price === 0 || c.price >= 5000, message: 'দাম ফ্রি অথবা অন্তত ৳৫০' },
    { key: 'noGuarantee', ok: !/(পরীক্ষায় আসবেই|১০০% কমন|নিশ্চিত প্রশ্ন|guaranteed question|100% common)/i.test(`${c.title} ${c.shortDescription} ${c.description}`), message: 'কোনো "নিশ্চিত প্রশ্ন/১০০% কমন" দাবি নেই' },
  ];
  return { items, canSubmit: items.every((i) => i.ok) && ['draft', 'changes_requested', 'rejected'].includes(c.status) };
}

export async function submit(sellerId: string, id: string) {
  const cl = await checklist(sellerId, id);
  if (!cl.canSubmit) throw new AppError('VALIDATION_FAILED', 'রিভিউয়ের আগে চেকলিস্টের সবগুলো পূরণ করুন।', cl.items.filter((i) => !i.ok));
  await contentDb.update(courses).set({ status: 'in_review', submittedAt: new Date(), reviewNote: null }).where(eq(courses.id, id));
  await audit({ actorId: sellerId, action: 'course.submit', entity: 'courses', entityId: id, after: { status: 'in_review' } });
  return getMyCourse(sellerId, id);
}

export async function setVisibility(sellerId: string, id: string, status: 'unlisted' | 'archived' | 'published') {
  const c = await ownCourse(sellerId, id);
  if (!['published', 'unlisted', 'archived'].includes(c.status) || (status === 'published' && !c.publishedAt)) throw badRequest('প্রকাশিত কোর্সই শুধু লুকানো বা আর্কাইভ করা যায়।');
  await contentDb.update(courses).set({ status }).where(eq(courses.id, id));
  await audit({ actorId: sellerId, action: `course.${status}`, entity: 'courses', entityId: id, before: { status: c.status }, after: { status } });
  await enqueue('search.indexCourse', { courseId: id });
  return getMyCourse(sellerId, id);
}

export async function removeDraft(sellerId: string, id: string) {
  const c = await ownCourse(sellerId, id);
  if (c.publishedAt) throw badRequest('প্রকাশিত কোর্স মুছা যায় না — আর্কাইভ করুন।');
  await contentDb.update(courses).set({ deletedAt: new Date() }).where(eq(courses.id, id));
  await audit({ actorId: sellerId, action: 'course.delete', entity: 'courses', entityId: id });
}

// ---------------------------------------------------------------- uploads

export async function createUpload(userId: string, b: z.infer<typeof UploadBody>) {
  const isSeller = !!(await profileOf(userId));
  if (b.kind !== 'id_card' && !isSeller) throw forbidden('আগে শিক্ষক হিসেবে আবেদন করুন।');
  switch (b.kind) {
    case 'video': {
      const vp = await videoProvider();
      const up = await vp.createUpload(b.title);
      const [row] = await contentDb.insert(videoAssets).values({ ownerId: userId, provider: vp.name, providerVideoId: up.providerVideoId, title: b.title, status: vp.name === 'fake' ? 'ready' : 'uploading', durationSec: vp.name === 'fake' ? 600 : 0 }).returning({ id: videoAssets.id });
      return { id: row!.id, uploadUrl: up.uploadUrl || null, method: up.uploadUrl ? ('TUS' as const) : null, headers: up.headers, key: null, publicUrl: null };
    }
    case 'video_link': {
      const u = new URL(b.url);
      if (u.protocol !== 'https:') throw badRequest('https লিংক দিন।');
      const [row] = await contentDb.insert(videoAssets).values({ ownerId: userId, provider: 'external_link', externalUrl: b.url, title: b.title, status: 'ready', durationSec: b.durationSec }).returning({ id: videoAssets.id });
      return { id: row!.id, uploadUrl: null, method: null, headers: {}, key: null, publicUrl: null };
    }
    case 'document': {
      await ownCourse(userId, b.courseId);
      const ext = EXT_BY_MIME[b.mime];
      if (!ext) throw badRequest('শুধু PDF, PPTX বা ছবি আপলোড করা যায়।');
      const key = objectKey(`courses/${b.courseId}`, `f.${ext}`);
      const signed = await storage().signedUploadUrl('course-docs', key, b.mime);
      const [row] = await contentDb.insert(documents).values({ courseId: b.courseId, ownerId: userId, kind: b.docKind, title: b.title, year: b.year ?? null, storageBucket: 'course-docs', storageKey: key, mime: b.mime, size: b.size }).returning({ id: documents.id });
      return { id: row!.id, uploadUrl: signed.url, method: 'PUT' as const, headers: signed.headers, key, publicUrl: null };
    }
    case 'document_link': {
      await ownCourse(userId, b.courseId);
      const [row] = await contentDb.insert(documents).values({ courseId: b.courseId, ownerId: userId, kind: b.docKind, title: b.title, year: b.year ?? null, externalUrl: b.url }).returning({ id: documents.id });
      return { id: row!.id, uploadUrl: null, method: null, headers: {}, key: null, publicUrl: null };
    }
    case 'cover': {
      const key = objectKey(`covers/${userId}`, `c.${EXT_BY_MIME[b.mime]}`);
      const signed = await storage().signedUploadUrl('course-covers', key, b.mime);
      return { id: null, uploadUrl: signed.url, method: 'PUT' as const, headers: signed.headers, key, publicUrl: storage().publicUrl('course-covers', key) };
    }
    case 'id_card': {
      const key = objectKey(`id-cards/${userId}`, `id.${EXT_BY_MIME[b.mime]}`);
      const signed = await storage().signedUploadUrl('verification-docs', key, b.mime);
      return { id: null, uploadUrl: signed.url, method: 'PUT' as const, headers: signed.headers, key, publicUrl: null };
    }
  }
}

export async function completeUpload(sellerId: string, id: string) {
  const [d] = await contentDb.select().from(documents).where(eq(documents.id, id));
  if (d) {
    if (d.ownerId !== sellerId) throw forbidden();
    if (d.storageKey && !(await storage().exists('course-docs', d.storageKey))) throw badRequest('ফাইলটি এখনো আপলোড হয়নি।');
    return;
  }
  const [v] = await contentDb.select().from(videoAssets).where(eq(videoAssets.id, id));
  if (!v || v.ownerId !== sellerId) throw notFound();
  if (v.provider === 'bunny' && v.providerVideoId) {
    await contentDb.update(videoAssets).set({ status: 'processing' }).where(eq(videoAssets.id, id));
    await enqueue('video.processed', { videoAssetId: id }, { delay: 30_000 });
  }
}

export async function proposeSubject(sellerId: string, name: string, aliases: string[]) {
  const slug = slugify(name) || `subject-${Date.now()}`;
  const [exists] = await contentDb.select({ id: subjectMasters.id, status: subjectMasters.status }).from(subjectMasters).where(eq(subjectMasters.slug, slug));
  if (exists) return { id: exists.id, status: exists.status };
  const [row] = await contentDb.insert(subjectMasters).values({ name, slug, aliases, status: 'proposed', proposedBy: sellerId }).returning({ id: subjectMasters.id, status: subjectMasters.status });
  return row!;
}

// ---------------------------------------------------------------- admin review

async function reviewItem(c: CourseRow, kind: 'new' | 'revision' | 'spot_check') {
  const targets = await contentDb.select().from(courseTargets).where(eq(courseTargets.courseId, c.id));
  const names = await lookupAcademicNames({ institutionIds: targets.map((t) => t.institutionId).filter((v): v is string => !!v), departmentIds: targets.map((t) => t.departmentId).filter((v): v is string => !!v), programIds: [] });
  return {
    id: c.id,
    title: c.title,
    sellerId: c.sellerId,
    sellerName: c.sellerName,
    sellerVerified: c.sellerVerified,
    status: c.status as SellerCourse['status'],
    submittedAt: c.submittedAt?.toISOString() ?? null,
    price: c.price,
    lessonCount: c.lessonCount,
    targets: targets.map((t) => (t.isGeneral ? 'General' : [t.institutionId ? names.institution.get(t.institutionId)?.shortName : null, t.departmentId ? names.department.get(t.departmentId)?.shortName : null, t.courseCode].filter(Boolean).join(' · '))),
    kind,
  };
}

export async function adminQueue(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  let conds: SQL;
  if (q.status === 'in_review') {
    const revCourseIds = (await contentDb.select({ id: courseRevisions.courseId }).from(courseRevisions).where(eq(courseRevisions.status, 'pending'))).map((r) => r.id);
    conds = or(eq(courses.status, 'in_review'), and(eq(courses.status, 'published'), or(eq(courses.needsSpotReview, true), revCourseIds.length ? inArray(courses.id, revCourseIds) : sql`false`)))!;
    const rows = await contentDb.select().from(courses).where(and(conds, isNull(courses.deletedAt), cur ? lt(courses.updatedAt, new Date(String(cur[0]))) : undefined)).orderBy(asc(courses.submittedAt), desc(courses.updatedAt)).limit(q.limit + 1);
    const pg = toPage(rows, q.limit, (r) => [r.updatedAt.toISOString(), r.id]);
    return { items: await Promise.all(pg.items.map((c) => reviewItem(c, c.status === 'in_review' ? 'new' : revCourseIds.includes(c.id) ? 'revision' : 'spot_check'))), nextCursor: pg.nextCursor };
  }
  const rows = await contentDb.select().from(courses).where(and(eq(courses.status, q.status), isNull(courses.deletedAt), cur ? lt(courses.updatedAt, new Date(String(cur[0]))) : undefined)).orderBy(desc(courses.updatedAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.updatedAt.toISOString(), r.id]);
  return { items: await Promise.all(pg.items.map((c) => reviewItem(c, 'new'))), nextCursor: pg.nextCursor };
}

export async function adminDetail(id: string) {
  const [c] = await contentDb.select().from(courses).where(eq(courses.id, id));
  if (!c) throw notFound();
  const [rev] = await contentDb.select().from(courseRevisions).where(and(eq(courseRevisions.courseId, id), eq(courseRevisions.status, 'pending')));
  // Admin reviews the live values; the revision is shown separately as a diff.
  const live = await loadCourse(c);
  if (rev) Object.assign(live, { title: c.title, price: c.price, compareAtPrice: c.compareAtPrice });
  return { ...live, sellerName: c.sellerName, revision: (rev?.payloadJson as Record<string, unknown>) ?? null };
}

export async function adminDecide(actorId: string, id: string, d: { decision: 'publish' | 'changes_requested' | 'reject'; note?: string; checklist: Record<string, boolean> }) {
  const [c] = await contentDb.select().from(courses).where(eq(courses.id, id));
  if (!c) throw notFound();
  const [rev] = await contentDb.select().from(courseRevisions).where(and(eq(courseRevisions.courseId, id), eq(courseRevisions.status, 'pending')));
  if (d.decision === 'publish' && !Object.values(d.checklist).every(Boolean)) throw badRequest('প্রকাশের আগে চেকলিস্টের সবগুলো নিশ্চিত করুন।');
  if (d.decision !== 'publish' && !d.note) throw badRequest('শিক্ষককে কী বদলাতে হবে, নোটে লিখুন।');
  const isRevision = c.status === 'published' && !!rev;
  if (d.decision === 'publish') {
    const p = await profileOf(c.sellerId);
    if (rev) {
      const { targets, ...fields } = rev.payloadJson as Record<string, unknown> & { targets?: z.infer<typeof TargetInput>[] };
      if (targets) await replaceTargets(id, targets);
      if (Object.keys(fields).length) await contentDb.update(courses).set(fields).where(eq(courses.id, id));
      await contentDb.update(courseRevisions).set({ status: 'approved', reviewerId: actorId, notes: d.note ?? null }).where(eq(courseRevisions.id, rev.id));
    }
    await contentDb.update(courses).set({ status: 'published', publishedAt: c.publishedAt ?? new Date(), reviewNote: d.note ?? null, needsSpotReview: false, sellerVerified: p?.verificationStatus === 'verified', sellerName: p?.displayName ?? c.sellerName }).where(eq(courses.id, id));
    await enqueue('search.indexCourse', { courseId: id });
    await onCoursePublished(id);
  } else if (isRevision) {
    await contentDb.update(courseRevisions).set({ status: 'rejected', reviewerId: actorId, notes: d.note ?? null }).where(eq(courseRevisions.id, rev!.id));
    await contentDb.update(courses).set({ reviewNote: d.note ?? null }).where(eq(courses.id, id));
  } else if (c.status === 'published') {
    // spot-check failure: take it down until fixed
    await contentDb.update(courses).set({ status: d.decision === 'reject' ? 'rejected' : 'changes_requested', reviewNote: d.note ?? null, needsSpotReview: false }).where(eq(courses.id, id));
    await enqueue('search.indexCourse', { courseId: id });
  } else {
    await contentDb.update(courses).set({ status: d.decision === 'reject' ? 'rejected' : 'changes_requested', reviewNote: d.note ?? null }).where(eq(courses.id, id));
  }
  await audit({ actorId, action: `course.review.${d.decision}`, entity: 'courses', entityId: id, before: { status: c.status }, after: { decision: d.decision, note: d.note, checklist: d.checklist, revision: !!rev } });
  const decision = d.decision === 'publish' ? 'published' : d.decision === 'reject' ? 'rejected' : 'changes_requested';
  await notify({
    userId: c.sellerId,
    type: 'seller.course_decision',
    ref: `${id}:${Date.now()}`,
    title: decision === 'published' ? `কোর্স প্রকাশিত হয়েছে: ${c.title}` : decision === 'changes_requested' ? `কিছু পরিবর্তন দরকার: ${c.title}` : `কোর্সটি প্রকাশ করা যায়নি: ${c.title}`,
    body: d.note ?? (decision === 'published' ? 'শিক্ষার্থীরা এখন কোর্সটি খুঁজে পাবে।' : ''),
    link: `/teach/courses/${id}/edit`,
    email: { template: 'course_review_decision', data: { courseTitle: c.title, decision, note: d.note ?? '' } },
  });
  return reviewItem((await contentDb.select().from(courses).where(eq(courses.id, id)))[0]!, isRevision ? 'revision' : 'new');
}

export async function payoutAccount(sellerId: string) {
  const p = await profileOf(sellerId);
  return p?.payoutAccountEnc ? decrypt(p.payoutAccountEnc) : null;
}

export async function defaultCommission(sellerId: string) {
  const p = await profileOf(sellerId);
  return p?.commissionRate ?? (await getBusinessNumber('commission_percent', 20));
}
