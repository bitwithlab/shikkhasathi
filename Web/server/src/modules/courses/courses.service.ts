import { and, asc, count, desc, eq, inArray, isNull, sql } from 'drizzle-orm';
import type { CourseDetail } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { courseChapters, courseSections, courseTargets, courses, departments, documents, flashcards, institutions, lessons, mockTests, subjectMasters } from '../../database/content/schema';
import { enrollments, reviews, sellerProfiles, wishlists } from '../../database/core/schema';
import { notFound } from '../../common/errors';
import { lookupAcademicNames } from '../catalog/catalog.lookup';
import { search, studentContext, toCards } from '../search/search.service';
import { setDashboardProvider } from '../dashboard/dashboard.service';
import { ordinalBn } from './format';

async function sellerCard(sellerId: string) {
  const [[p], agg] = await Promise.all([
    coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.userId, sellerId)),
    contentDb
      .select({ students: sql<number>`coalesce(sum(${courses.enrollmentCount}),0)::int`, n: count(), rating: sql<number>`coalesce(sum(${courses.ratingAvg} * ${courses.ratingCount}) / nullif(sum(${courses.ratingCount}),0), 0)::float` })
      .from(courses)
      .where(and(eq(courses.sellerId, sellerId), eq(courses.status, 'published'), isNull(courses.deletedAt))),
  ]);
  return { p, students: agg[0]?.students ?? 0, courses: agg[0]?.n ?? 0, rating: Math.round((agg[0]?.rating ?? 0) * 100) / 100 };
}

export async function courseDetail(slug: string, userId?: string, roles: string[] = []): Promise<CourseDetail> {
  const [c] = await contentDb.select().from(courses).where(and(eq(courses.slug, slug), isNull(courses.deletedAt)));
  const isOwner = !!c && c.sellerId === userId;
  if (!c || (c.status !== 'published' && !isOwner && !roles.includes('admin'))) throw notFound('কোর্সটি পাওয়া যায়নি।');
  const student = await studentContext(userId);
  const [[card], secs, chs, ls, docs, mockN, flashN, seller, dist, enr, wish, ts] = await Promise.all([
    toCards([c], student),
    contentDb.select().from(courseSections).where(eq(courseSections.courseId, c.id)).orderBy(asc(courseSections.position)),
    contentDb.select().from(courseChapters).where(eq(courseChapters.courseId, c.id)).orderBy(asc(courseChapters.position)),
    contentDb.select({ id: lessons.id, chapterId: lessons.chapterId, title: lessons.title, type: lessons.type, durationSec: lessons.durationSec, isPreview: lessons.isPreview, isImportant: lessons.isImportant }).from(lessons).where(eq(lessons.courseId, c.id)).orderBy(asc(lessons.position)),
    contentDb.select({ kind: documents.kind, year: documents.year }).from(documents).where(eq(documents.courseId, c.id)),
    contentDb.select({ value: count() }).from(mockTests).where(eq(mockTests.courseId, c.id)),
    contentDb.select({ value: count() }).from(flashcards).where(eq(flashcards.courseId, c.id)),
    sellerCard(c.sellerId),
    coreDb.select({ r: reviews.rating, n: count() }).from(reviews).where(and(eq(reviews.courseId, c.id), eq(reviews.status, 'visible'))).groupBy(reviews.rating),
    userId ? coreDb.select().from(enrollments).where(and(eq(enrollments.studentId, userId), eq(enrollments.courseId, c.id), eq(enrollments.status, 'active'))) : [],
    userId ? coreDb.select({ id: wishlists.id }).from(wishlists).where(and(eq(wishlists.userId, userId), eq(wishlists.courseId, c.id))) : [],
    contentDb.select().from(courseTargets).where(eq(courseTargets.courseId, c.id)),
  ]);
  const names = await lookupAcademicNames({ institutionIds: ts.map((t) => t.institutionId).filter((v): v is string => !!v), departmentIds: ts.map((t) => t.departmentId).filter((v): v is string => !!v), programIds: [] });
  const subjIds = ts.map((t) => t.subjectMasterId).filter((v): v is string => !!v);
  const [subs, instRows, deptRows] = await Promise.all([
    subjIds.length ? contentDb.select().from(subjectMasters).where(inArray(subjectMasters.id, subjIds)) : [],
    ts.some((t) => t.institutionId) ? contentDb.select({ id: institutions.id, slug: institutions.slug }).from(institutions).where(inArray(institutions.id, ts.map((t) => t.institutionId).filter((v): v is string => !!v))) : [],
    ts.some((t) => t.departmentId) ? contentDb.select({ id: departments.id, shortName: departments.shortName }).from(departments).where(inArray(departments.id, ts.map((t) => t.departmentId).filter((v): v is string => !!v))) : [],
  ]);
  // breadcrumb from the best target (student's own institution first)
  const primary = (student?.institutionId && ts.find((t) => t.institutionId === student.institutionId)) || ts.find((t) => !t.isGeneral) || ts[0];
  const breadcrumb: { label: string; href: string }[] = [];
  if (primary?.institutionId) {
    const inst = names.institution.get(primary.institutionId);
    const slugRow = instRows.find((r) => r.id === primary.institutionId);
    if (inst && slugRow) breadcrumb.push({ label: inst.name, href: `/u/${slugRow.slug}` });
    const dept = primary.departmentId ? deptRows.find((d) => d.id === primary.departmentId) : undefined;
    if (dept && slugRow) breadcrumb.push({ label: dept.shortName, href: `/u/${slugRow.slug}?dept=${primary.departmentId}` });
    if (primary.semester && slugRow) breadcrumb.push({ label: `${ordinalBn(primary.semester)} সেমিস্টার`, href: `/u/${slugRow.slug}?dept=${primary.departmentId ?? ''}&sem=${primary.semester}` });
  }
  const subj = subs.find((s) => s.id === primary?.subjectMasterId);
  if (subj) breadcrumb.push({ label: subj.name, href: `/s/${subj.slug}` });

  const distribution = [1, 2, 3, 4, 5].map((r) => dist.find((d) => d.r === r)?.n ?? 0);
  const lessonList = ls;
  const firstLesson = lessonList[0]?.id ?? null;
  let resume: string | null = null;
  if (enr[0]) resume = enr[0].lastLessonId ?? firstLesson;
  return {
    ...card!,
    description: c.description,
    learnings: c.learnings,
    notFor: c.notFor,
    faq: c.faq,
    language: c.language,
    accessType: c.accessType,
    accessDays: c.accessDays,
    publishedAt: c.publishedAt?.toISOString() ?? null,
    updatedAt: (c.contentUpdatedAt ?? c.updatedAt).toISOString(),
    breadcrumb,
    targets: ts.map((t) => ({
      label: t.isGeneral ? 'General / Cross-University' : [t.institutionId ? names.institution.get(t.institutionId)?.shortName : null, t.departmentId ? names.department.get(t.departmentId)?.shortName : null, t.courseCode].filter(Boolean).join(' · '),
      institutionSlug: instRows.find((r) => r.id === t.institutionId)?.slug ?? null,
      courseCode: t.courseCode,
      semester: t.semester,
      batches: t.batches,
    })),
    seller: { id: c.sellerId, slug: seller.p?.slug ?? '', name: c.sellerName, headline: seller.p?.headline ?? null, verified: c.sellerVerified, ratingAvg: seller.rating, studentCount: seller.students, courseCount: seller.courses },
    sections: secs.map((s) => ({ id: s.id, title: s.title, chapters: chs.filter((ch) => ch.sectionId === s.id).map((ch) => ({ id: ch.id, title: ch.title, lessons: lessonList.filter((l) => l.chapterId === ch.id).map((l) => ({ id: l.id, title: l.title, type: l.type as 'video', durationSec: l.durationSec, isPreview: l.isPreview, isImportant: l.isImportant })) })) })),
    resources: {
      videos: lessonList.filter((l) => l.type === 'video').length,
      notes: docs.filter((d) => d.kind === 'notes' || d.kind === 'pdf' || d.kind === 'slides').length + lessonList.filter((l) => l.type === 'pdf').length,
      previousQuestionYears: [...new Set(docs.filter((d) => d.kind === 'previous_questions' && d.year).map((d) => d.year!))].sort((a, b) => b - a),
      mockTests: mockN[0]?.value ?? 0,
      flashcards: flashN[0]?.value ?? 0,
    },
    ratingDistribution: distribution,
    viewer: { enrolled: !!enr[0], wishlisted: !!wish[0], firstLessonId: firstLesson, resumeLessonId: resume, isOwner },
    status: c.status,
  };
}

export async function storefront(slug: string, userId?: string) {
  const [p] = await coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.slug, slug));
  if (!p) throw notFound('এই শিক্ষকের পেজ পাওয়া যায়নি।');
  const student = await studentContext(userId);
  const rows = await contentDb.select().from(courses).where(and(eq(courses.sellerId, p.userId), eq(courses.status, 'published'), isNull(courses.deletedAt))).orderBy(desc(courses.enrollmentCount));
  const ts = rows.length ? await contentDb.select({ inst: courseTargets.institutionId }).from(courseTargets).where(inArray(courseTargets.courseId, rows.map((r) => r.id))) : [];
  const instIds = [...new Set([...ts.map((t) => t.inst), p.institutionId].filter((v): v is string => !!v))];
  const names = await lookupAcademicNames({ institutionIds: instIds, departmentIds: [], programIds: [] });
  const ratingCount = rows.reduce((a, r) => a + r.ratingCount, 0);
  return {
    id: p.userId,
    slug: p.slug,
    name: p.displayName,
    headline: p.headline,
    bio: p.bio,
    coverUrl: p.coverUrl,
    verified: p.verificationStatus === 'verified',
    institutionName: p.institutionId ? (names.institution.get(p.institutionId)?.name ?? null) : null,
    institutionsTaught: [...new Set(ts.map((t) => (t.inst ? names.institution.get(t.inst)?.shortName : null)).filter((v): v is string => !!v))],
    stats: { students: rows.reduce((a, r) => a + r.enrollmentCount, 0), courses: rows.length, ratingAvg: ratingCount ? Math.round((rows.reduce((a, r) => a + Number(r.ratingAvg) * r.ratingCount, 0) / ratingCount) * 100) / 100 : 0, ratingCount },
    courses: await toCards(rows, student),
  };
}

export async function featured(institutionId?: string) {
  const res = await search({ sort: 'popular', limit: 8, institutionId, examType: undefined }, undefined);
  const sellers = await contentDb
    .select({ sellerId: courses.sellerId, students: sql<number>`sum(${courses.enrollmentCount})::int`, n: count(), rating: sql<number>`coalesce(sum(${courses.ratingAvg} * ${courses.ratingCount}) / nullif(sum(${courses.ratingCount}),0), 0)::float` })
    .from(courses)
    .where(and(eq(courses.status, 'published'), eq(courses.sellerVerified, true), isNull(courses.deletedAt)))
    .groupBy(courses.sellerId)
    .orderBy(desc(sql`sum(${courses.enrollmentCount})`))
    .limit(4);
  const profiles = sellers.length ? await coreDb.select().from(sellerProfiles).where(inArray(sellerProfiles.userId, sellers.map((s) => s.sellerId))) : [];
  const names = await lookupAcademicNames({ institutionIds: profiles.map((p) => p.institutionId).filter((v): v is string => !!v), departmentIds: [], programIds: [] });
  const [tot] = await contentDb.select({ students: sql<number>`coalesce(sum(${courses.enrollmentCount}),0)::int`, n: count() }).from(courses).where(eq(courses.status, 'published'));
  const [inst] = await contentDb.select({ n: sql<number>`count(distinct ${courseTargets.institutionId})::int` }).from(courseTargets);
  return {
    courses: res.items,
    teachers: sellers.map((s) => {
      const p = profiles.find((x) => x.userId === s.sellerId)!;
      return { slug: p?.slug ?? '', name: p?.displayName ?? '', headline: p?.headline ?? null, institutionName: p?.institutionId ? (names.institution.get(p.institutionId)?.name ?? null) : null, ratingAvg: Math.round(s.rating * 100) / 100, studentCount: s.students, courseCount: s.n };
    }),
    stats: { students: tot?.students ?? 0, courses: tot?.n ?? 0, institutions: inst?.n ?? 0 },
  };
}

/** "আপনার জন্য কোর্স" — ranked by the spec §6.5 score for this student, excluding enrolled courses. */
export async function recommendations(userId: string, limit = 8) {
  const enrolled = (await coreDb.select({ id: enrollments.courseId }).from(enrollments).where(eq(enrollments.studentId, userId))).map((r) => r.id);
  const res = await search({ sort: 'best', limit: limit + enrolled.length }, userId);
  return { items: res.items.filter((c) => !enrolled.includes(c.id)).slice(0, limit) };
}

setDashboardProvider('recommendations', async (userId) => (await recommendations(userId, 6)).items);

export async function addWishlist(userId: string, courseId: string) {
  const [c] = await contentDb.select({ id: courses.id }).from(courses).where(and(eq(courses.id, courseId), eq(courses.status, 'published')));
  if (!c) throw notFound();
  await coreDb.insert(wishlists).values({ userId, courseId }).onConflictDoNothing();
}
export async function removeWishlist(userId: string, courseId: string) {
  await coreDb.delete(wishlists).where(and(eq(wishlists.userId, userId), eq(wishlists.courseId, courseId)));
}
export async function listWishlist(userId: string) {
  const ids = (await coreDb.select({ id: wishlists.courseId }).from(wishlists).where(eq(wishlists.userId, userId)).orderBy(desc(wishlists.createdAt))).map((r) => r.id);
  if (!ids.length) return { items: [] };
  const rows = await contentDb.select().from(courses).where(and(inArray(courses.id, ids), isNull(courses.deletedAt)));
  const student = await studentContext(userId);
  const cards = await toCards(rows, student);
  return { items: ids.map((id) => cards.find((c) => c.id === id)).filter((c): c is NonNullable<typeof c> => !!c) };
}
