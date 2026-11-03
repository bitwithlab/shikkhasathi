import { and, desc, eq, gte, ilike, inArray, isNull, lte, or, sql, type SQL } from 'drizzle-orm';
import type { CourseCardDto, SearchQuery } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { courseTargets, courses, institutionSubjects, institutions, subjectMasters } from '../../database/content/schema';
import { examPlans, sellerProfiles, studentProfiles } from '../../database/core/schema';
import { lookupAcademicNames } from '../catalog/catalog.lookup';
import { compatibility, rankScore, type RankStudent, type RankTarget } from './ranking';
import { expandQuery, normalizeCode } from './synonyms';
import { meili } from '../../services/search';

type CourseRow = typeof courses.$inferSelect;
type TargetRow = typeof courseTargets.$inferSelect;

const like = (s: string) => `%${s.replace(/[%_\\]/g, (m) => `\\${m}`)}%`;
const DURATION: Record<string, [number, number]> = { short: [0, 2 * 3600], medium: [2 * 3600, 6 * 3600], long: [6 * 3600, 1e9] };

export async function studentContext(userId: string | undefined): Promise<RankStudent | null> {
  if (!userId) return null;
  const [p] = await coreDb.select().from(studentProfiles).where(eq(studentProfiles.userId, userId));
  const exams = await coreDb.select({ subjectMasterId: examPlans.subjectMasterId, examType: examPlans.examType, examDate: examPlans.examDate }).from(examPlans).where(and(eq(examPlans.studentId, userId), eq(examPlans.active, true)));
  let subjectIds: string[] = [];
  if (p?.institutionId) {
    const conds: SQL[] = [eq(institutionSubjects.institutionId, p.institutionId)];
    if (p.departmentId) conds.push(eq(institutionSubjects.departmentId, p.departmentId));
    if (p.semester) conds.push(eq(institutionSubjects.semester, p.semester));
    subjectIds = (await contentDb.select({ id: institutionSubjects.subjectMasterId }).from(institutionSubjects).where(and(...conds))).map((r) => r.id);
  }
  subjectIds = [...new Set([...subjectIds, ...exams.map((e) => e.subjectMasterId).filter((v): v is string => !!v)])];
  return { institutionId: p?.institutionId ?? null, departmentId: p?.departmentId ?? null, semester: p?.semester ?? null, batch: p?.batch ?? null, subjectIds, exams };
}

/** Build course card DTOs for rows (batched cross-DB lookups). */
export async function toCards(rows: CourseRow[], student: RankStudent | null, scores?: Map<string, number>): Promise<CourseCardDto[]> {
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id);
  const [targets, sellers] = await Promise.all([
    contentDb.select().from(courseTargets).where(inArray(courseTargets.courseId, ids)),
    coreDb.select({ userId: sellerProfiles.userId, slug: sellerProfiles.slug }).from(sellerProfiles).where(inArray(sellerProfiles.userId, [...new Set(rows.map((r) => r.sellerId))])),
  ]);
  const names = await lookupAcademicNames({ institutionIds: targets.map((t) => t.institutionId).filter((v): v is string => !!v), departmentIds: [], programIds: [] });
  return rows.map((c) => {
    const ts = targets.filter((t) => t.courseId === c.id);
    // Prefer the student's own institution in the card pill.
    const primary = (student?.institutionId && ts.find((t) => t.institutionId === student.institutionId)) || ts.find((t) => !t.isGeneral) || ts[0];
    return {
      id: c.id,
      slug: c.slug,
      title: c.title,
      shortDescription: c.shortDescription,
      courseCode: primary?.courseCode ?? null,
      institutionShort: primary?.institutionId ? (names.institution.get(primary.institutionId)?.shortName ?? null) : null,
      sellerName: c.sellerName,
      sellerSlug: sellers.find((s) => s.userId === c.sellerId)?.slug ?? null,
      sellerVerified: c.sellerVerified,
      examType: c.examType,
      courseType: c.courseType as CourseCardDto['courseType'],
      price: c.price,
      compareAtPrice: c.compareAtPrice,
      ratingAvg: Number(c.ratingAvg),
      ratingCount: c.ratingCount,
      coverUrl: c.coverUrl,
      coverColor: c.coverColor,
      lessonCount: c.lessonCount,
      totalDurationSec: c.totalDurationSec,
      enrollmentCount: c.enrollmentCount,
      compatibility: compatibility(ts, student),
      ...(scores?.has(c.id) ? { score: Math.round(scores.get(c.id)! * 10) / 10 } : {}),
    };
  });
}

const toRankTargets = (ts: TargetRow[]): RankTarget[] => ts.map((t) => ({ isGeneral: t.isGeneral, institutionId: t.institutionId, departmentId: t.departmentId, semester: t.semester, subjectMasterId: t.subjectMasterId, batches: t.batches }));

/** Text relevance for the Postgres engine (Meili provides its own ordering). */
function textScore(c: CourseRow, ts: TargetRow[], terms: string[], code: string | null, subjectHits: Set<string>, instHits: Set<string>) {
  let s = 0;
  const title = c.title.toLowerCase();
  if (code && ts.some((t) => t.courseCode && normalizeCode(t.courseCode) === code)) s += 40;
  if (terms.some((t) => title.includes(t))) s += 30;
  if (ts.some((t) => t.subjectMasterId && subjectHits.has(t.subjectMasterId))) s += 20;
  if (ts.some((t) => t.institutionId && instHits.has(t.institutionId))) s += 15;
  if (terms.some((t) => c.sellerName.toLowerCase().includes(t))) s += 10;
  if (terms.some((t) => c.shortDescription.toLowerCase().includes(t))) s += 5;
  return s;
}

function encodeOffset(n: number) {
  return Buffer.from(String(n)).toString('base64url');
}
function decodeOffset(c?: string) {
  if (!c) return 0;
  const n = Number(Buffer.from(c, 'base64url').toString('utf8'));
  return Number.isFinite(n) && n >= 0 ? n : 0;
}

export async function search(q: SearchQuery, userId?: string) {
  const student = await studentContext(userId);
  const exp = expandQuery(q.q ?? '');
  const examType = q.examType ?? exp.examType ?? undefined;

  // ---- candidate ids: Meili when configured, else Postgres
  let engine: 'meilisearch' | 'postgres' = 'postgres';
  let meiliIds: string[] | null = null;
  const m = await meili();
  if (m && q.q) {
    try {
      meiliIds = await m.searchIds(q.q, q);
      engine = 'meilisearch';
    } catch {
      meiliIds = null;
    }
  }

  const conds: SQL[] = [eq(courses.status, 'published'), isNull(courses.deletedAt)];
  if (examType) conds.push(eq(courses.examType, examType));
  if (q.courseType) conds.push(eq(courses.courseType, q.courseType));
  if (q.language) conds.push(eq(courses.language, q.language));
  if (q.price === 'free') conds.push(eq(courses.price, 0));
  if (q.price === 'paid') conds.push(sql`${courses.price} > 0`);
  if (q.minPrice !== undefined) conds.push(gte(courses.price, q.minPrice));
  if (q.maxPrice !== undefined) conds.push(lte(courses.price, q.maxPrice));
  if (q.rating !== undefined) conds.push(gte(courses.ratingAvg, String(q.rating)));
  if (q.duration) conds.push(and(gte(courses.totalDurationSec, DURATION[q.duration]![0]), lte(courses.totalDurationSec, DURATION[q.duration]![1]))!);
  if (q.sellerId) conds.push(eq(courses.sellerId, q.sellerId));

  // target filters (any matching target)
  const tconds: SQL[] = [];
  if (q.institutionId) tconds.push(or(eq(courseTargets.institutionId, q.institutionId), eq(courseTargets.isGeneral, true))!);
  if (q.departmentId) tconds.push(or(eq(courseTargets.departmentId, q.departmentId), isNull(courseTargets.departmentId))!);
  if (q.semester) tconds.push(or(eq(courseTargets.semester, q.semester), isNull(courseTargets.semester))!);
  if (q.subjectId) tconds.push(eq(courseTargets.subjectMasterId, q.subjectId));
  const filterCode = q.courseCode ? normalizeCode(q.courseCode) : null;
  if (filterCode) tconds.push(sql`regexp_replace(upper(${courseTargets.courseCode}), '[^A-Z0-9]', '', 'g') = ${filterCode}`);
  if (q.batch) tconds.push(or(sql`${q.batch} = any(${courseTargets.batches})`, sql`cardinality(${courseTargets.batches}) = 0`)!);
  if (q.section) tconds.push(or(sql`${q.section} = any(${courseTargets.sections})`, sql`cardinality(${courseTargets.sections}) = 0`)!);
  if (tconds.length) conds.push(sql`exists (select 1 from ${courseTargets} where ${courseTargets.courseId} = ${courses.id} and ${and(...tconds)})`);

  // text match (Postgres engine)
  let subjectHits = new Set<string>();
  let instHits = new Set<string>();
  if (meiliIds) conds.push(meiliIds.length ? inArray(courses.id, meiliIds) : sql`false`);
  else if (q.q && (exp.terms.length || exp.code)) {
    const terms = exp.terms;
    const [subs, insts] = await Promise.all([
      terms.length ? contentDb.select({ id: subjectMasters.id }).from(subjectMasters).where(or(...terms.flatMap((t) => [ilike(subjectMasters.name, like(t)), ilike(subjectMasters.nameBn, like(t)), sql`exists (select 1 from unnest(${subjectMasters.aliases}) a where lower(a) = ${t})`]))) : [],
      terms.length ? contentDb.select({ id: institutions.id }).from(institutions).where(or(...terms.flatMap((t) => [ilike(institutions.name, like(t)), ilike(institutions.nameBn, like(t)), sql`lower(${institutions.shortName}) = ${t}`, sql`exists (select 1 from unnest(${institutions.aliases}) a where lower(a) = ${t})`]))) : [],
    ]);
    subjectHits = new Set(subs.map((s) => s.id));
    instHits = new Set(insts.map((s) => s.id));
    const textOr: SQL[] = terms.flatMap((t) => [ilike(courses.title, like(t)), ilike(courses.shortDescription, like(t)), ilike(courses.sellerName, like(t))]);
    if (subjectHits.size) textOr.push(sql`exists (select 1 from ${courseTargets} where ${courseTargets.courseId} = ${courses.id} and ${inArray(courseTargets.subjectMasterId, [...subjectHits])})`);
    if (instHits.size) textOr.push(sql`exists (select 1 from ${courseTargets} where ${courseTargets.courseId} = ${courses.id} and ${inArray(courseTargets.institutionId, [...instHits])})`);
    if (exp.code) textOr.push(sql`exists (select 1 from ${courseTargets} where ${courseTargets.courseId} = ${courses.id} and regexp_replace(upper(${courseTargets.courseCode}), '[^A-Z0-9]', '', 'g') = ${exp.code})`);
    conds.push(or(...textOr)!);
  }

  const rows = await contentDb.select().from(courses).where(and(...conds)).limit(500);
  const targets = rows.length ? await contentDb.select().from(courseTargets).where(inArray(courseTargets.courseId, rows.map((r) => r.id))) : [];
  const now = new Date();
  const scores = new Map<string, number>();
  for (const c of rows) {
    const ts = targets.filter((t) => t.courseId === c.id);
    const base = rankScore({ examType: c.examType, ratingAvg: Number(c.ratingAvg), ratingCount: c.ratingCount, enrollments30d: c.enrollments30d, sellerVerified: c.sellerVerified, updatedAt: c.contentUpdatedAt ?? c.updatedAt, targets: toRankTargets(ts) }, student, now);
    const text = q.q && !meiliIds ? textScore(c, ts, exp.terms, exp.code, subjectHits, instHits) : meiliIds ? 60 - Math.min(60, meiliIds.indexOf(c.id)) : 0;
    scores.set(c.id, base + text);
  }
  const sorted = [...rows].sort((a, b) => {
    switch (q.sort) {
      case 'newest':
        return (b.publishedAt?.getTime() ?? 0) - (a.publishedAt?.getTime() ?? 0);
      case 'rating':
        return Number(b.ratingAvg) - Number(a.ratingAvg) || b.ratingCount - a.ratingCount;
      case 'price_asc':
        return a.price - b.price;
      case 'price_desc':
        return b.price - a.price;
      case 'popular':
        return b.enrollments30d - a.enrollments30d || b.enrollmentCount - a.enrollmentCount;
      default:
        return scores.get(b.id)! - scores.get(a.id)!;
    }
  });
  const offset = decodeOffset(q.cursor);
  const pageRows = sorted.slice(offset, offset + q.limit);
  return {
    items: await toCards(pageRows, student, scores),
    total: sorted.length,
    nextCursor: offset + q.limit < sorted.length ? encodeOffset(offset + q.limit) : null,
    interpreted: exp.interpreted,
    engine,
  };
}

export async function suggest(qs: string) {
  const exp = expandQuery(qs);
  const t = qs.trim();
  const terms = [...new Set([t.toLowerCase(), ...exp.terms])].slice(0, 6);
  const [insts, subs, codes, teachers, crs] = await Promise.all([
    contentDb.select({ id: institutions.id, name: institutions.name, short: institutions.shortName, slug: institutions.slug, city: institutions.city }).from(institutions).where(and(eq(institutions.status, 'approved'), or(...terms.flatMap((x) => [ilike(institutions.name, like(x)), ilike(institutions.nameBn, like(x)), ilike(institutions.shortName, like(x)), sql`exists (select 1 from unnest(${institutions.aliases}) a where a ilike ${like(x)})`])))).limit(3),
    contentDb.select({ id: subjectMasters.id, name: subjectMasters.name, nameBn: subjectMasters.nameBn, slug: subjectMasters.slug }).from(subjectMasters).where(and(eq(subjectMasters.status, 'approved'), or(...terms.flatMap((x) => [ilike(subjectMasters.name, like(x)), ilike(subjectMasters.nameBn, like(x)), sql`exists (select 1 from unnest(${subjectMasters.aliases}) a where a ilike ${like(x)})`])))).limit(3),
    contentDb
      .select({ id: institutionSubjects.id, code: institutionSubjects.courseCode, inst: institutions.shortName, subject: subjectMasters.name })
      .from(institutionSubjects)
      .innerJoin(institutions, eq(institutions.id, institutionSubjects.institutionId))
      .innerJoin(subjectMasters, eq(subjectMasters.id, institutionSubjects.subjectMasterId))
      .where(sql`regexp_replace(upper(${institutionSubjects.courseCode}), '[^A-Z0-9]', '', 'g') like ${`%${normalizeCode(t)}%`}`)
      .limit(normalizeCode(t).length >= 3 ? 4 : 0),
    contentDb.selectDistinct({ sellerId: courses.sellerId, name: courses.sellerName }).from(courses).where(and(eq(courses.status, 'published'), ilike(courses.sellerName, like(t)))).limit(3),
    contentDb.select({ id: courses.id, title: courses.title, slug: courses.slug, seller: courses.sellerName }).from(courses).where(and(eq(courses.status, 'published'), isNull(courses.deletedAt), or(...terms.map((x) => ilike(courses.title, like(x)))))).orderBy(desc(courses.enrollments30d)).limit(4),
  ]);
  const sellerSlugs = teachers.length ? await coreDb.select({ userId: sellerProfiles.userId, slug: sellerProfiles.slug }).from(sellerProfiles).where(inArray(sellerProfiles.userId, teachers.map((x) => x.sellerId))) : [];
  return {
    items: [
      ...insts.map((i) => ({ type: 'institution' as const, id: i.id, label: i.name, sub: [i.short, i.city].filter(Boolean).join(' · '), href: `/u/${i.slug}` })),
      ...subs.map((s) => ({ type: 'subject' as const, id: s.id, label: s.name, sub: s.nameBn, href: `/explore?subjectId=${s.id}` })),
      ...codes.filter((c) => c.code).map((c) => ({ type: 'code' as const, id: c.id, label: c.code!, sub: `${c.inst} · ${c.subject}`, href: `/explore?courseCode=${encodeURIComponent(c.code!)}` })),
      ...teachers.map((x) => ({ type: 'teacher' as const, id: x.sellerId, label: x.name, sub: null, href: `/t/${sellerSlugs.find((s) => s.userId === x.sellerId)?.slug ?? ''}` })),
      ...crs.map((c) => ({ type: 'course' as const, id: c.id, label: c.title, sub: c.seller, href: `/c/${c.slug}` })),
    ],
  };
}
