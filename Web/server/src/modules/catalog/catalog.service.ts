import { and, asc, eq, gt, ilike, inArray, isNull, ne, or, sql, type SQL } from 'drizzle-orm';
import type { z } from 'zod';
import type { InstitutionSubjectUpsert, InstitutionUpsert, SubjectUpsert, DepartmentUpsert, ProgramUpsert, InstitutionRequestDecision } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { courseTargets, courses, departments, institutionRequests, institutionSubjects, institutions, programs, subjectMasters } from '../../database/content/schema';
import { examPlans, studentProfiles, users } from '../../database/core/schema';
import { badRequest, conflict, notFound } from '../../common/errors';
import { decodeCursor, toPage } from '../../common/pagination';
import { audit } from '../../common/audit';
import { getMe } from '../auth/auth.service';

type InstRow = typeof institutions.$inferSelect;

/** courseCount / teacherCount per institution from published courses' targets (one grouped query). */
async function institutionCounts(ids: string[]) {
  if (!ids.length) return new Map<string, { courses: number; teachers: number }>();
  const rows = await contentDb
    .select({
      id: courseTargets.institutionId,
      courses: sql<number>`count(distinct ${courses.id})::int`,
      teachers: sql<number>`count(distinct ${courses.sellerId})::int`,
    })
    .from(courseTargets)
    .innerJoin(courses, eq(courses.id, courseTargets.courseId))
    .where(and(inArray(courseTargets.institutionId, ids), eq(courses.status, 'published'), isNull(courses.deletedAt)))
    .groupBy(courseTargets.institutionId);
  return new Map(rows.map((r) => [r.id!, { courses: r.courses, teachers: r.teachers }]));
}

const toInstitution = (r: InstRow, c?: { courses: number; teachers: number }) => ({
  id: r.id,
  name: r.name,
  nameBn: r.nameBn,
  shortName: r.shortName,
  slug: r.slug,
  type: r.type as 'public' | 'private' | 'college' | 'polytechnic',
  city: r.city,
  logoUrl: r.logoUrl,
  courseCount: c?.courses ?? 0,
  teacherCount: c?.teachers ?? 0,
});

function textMatch(col: Parameters<typeof ilike>[0], q: string) {
  return ilike(col, `%${q.replace(/[%_]/g, (m) => `\\${m}`)}%`);
}

export async function listInstitutions(q: { q?: string; type?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(institutions.status, 'approved')];
  if (q.type) conds.push(eq(institutions.type, q.type));
  if (q.q) {
    const term = q.q.trim();
    conds.push(
      or(
        textMatch(institutions.name, term),
        textMatch(institutions.shortName, term),
        textMatch(institutions.nameBn, term),
        sql`exists (select 1 from unnest(${institutions.aliases}) a where a ilike ${'%' + term + '%'})`,
      )!,
    );
  }
  if (cur) conds.push(or(gt(institutions.name, String(cur[0])), and(eq(institutions.name, String(cur[0])), gt(institutions.id, cur[1])))!);
  const rows = await contentDb.select().from(institutions).where(and(...conds)).orderBy(asc(institutions.name), asc(institutions.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.name, r.id]);
  const counts = await institutionCounts(pg.items.map((r) => r.id));
  // Most active first within the page keeps alphabetical paging stable while surfacing launch universities.
  return { items: pg.items.map((r) => toInstitution(r, counts.get(r.id))), nextCursor: pg.nextCursor };
}

export async function institutionBySlug(slug: string) {
  let [inst] = await contentDb.select().from(institutions).where(eq(institutions.slug, slug));
  if (inst?.status === 'merged' && inst.mergedIntoId) [inst] = await contentDb.select().from(institutions).where(eq(institutions.id, inst.mergedIntoId));
  if (!inst || inst.status !== 'approved') throw notFound('এই প্রতিষ্ঠানটি পাওয়া যায়নি।');
  const [depts, subs, counts] = await Promise.all([
    contentDb.select().from(departments).where(eq(departments.institutionId, inst.id)).orderBy(asc(departments.position), asc(departments.name)),
    contentDb
      .select({ m: institutionSubjects, s: subjectMasters })
      .from(institutionSubjects)
      .innerJoin(subjectMasters, eq(subjectMasters.id, institutionSubjects.subjectMasterId))
      .where(eq(institutionSubjects.institutionId, inst.id))
      .orderBy(asc(institutionSubjects.semester), asc(institutionSubjects.courseCode)),
    institutionCounts([inst.id]),
  ]);
  const progs = depts.length ? await contentDb.select().from(programs).where(inArray(programs.departmentId, depts.map((d) => d.id))).orderBy(asc(programs.name)) : [];
  // course counts per (subject) within this institution
  const subjectCounts = subs.length
    ? await contentDb
        .select({ sid: courseTargets.subjectMasterId, n: sql<number>`count(distinct ${courses.id})::int` })
        .from(courseTargets)
        .innerJoin(courses, eq(courses.id, courseTargets.courseId))
        .where(and(eq(courseTargets.institutionId, inst.id), eq(courses.status, 'published')))
        .groupBy(courseTargets.subjectMasterId)
    : [];
  const sc = new Map(subjectCounts.map((r) => [r.sid, r.n]));
  return {
    ...toInstitution(inst, counts.get(inst.id)),
    website: inst.website,
    aliases: inst.aliases,
    departments: depts.map((d) => ({
      id: d.id,
      institutionId: d.institutionId,
      name: d.name,
      nameBn: d.nameBn,
      shortName: d.shortName,
      icon: d.icon,
      programs: progs.filter((p) => p.departmentId === d.id).map((p) => ({ id: p.id, departmentId: p.departmentId, name: p.name, level: p.level, semesters: p.semesters })),
      subjectCount: subs.filter((s) => s.m.departmentId === d.id).length,
    })),
    subjects: subs.map(({ m, s }) => ({ id: m.id, subjectId: s.id, subjectName: s.name, subjectSlug: s.slug, departmentId: m.departmentId, semester: m.semester, courseCode: m.courseCode, courseCount: sc.get(s.id) ?? 0 })),
  };
}

export async function listDepartments(institutionId: string) {
  const rows = await contentDb.select().from(departments).where(eq(departments.institutionId, institutionId)).orderBy(asc(departments.position), asc(departments.name));
  return { items: rows.map((d) => ({ id: d.id, institutionId: d.institutionId, name: d.name, nameBn: d.nameBn, shortName: d.shortName, icon: d.icon })) };
}

export async function listPrograms(departmentId: string) {
  const rows = await contentDb.select().from(programs).where(eq(programs.departmentId, departmentId)).orderBy(asc(programs.name));
  return { items: rows.map((p) => ({ id: p.id, departmentId: p.departmentId, name: p.name, level: p.level, semesters: p.semesters })) };
}

async function subjectCourseCounts(ids: string[]) {
  if (!ids.length) return new Map<string, number>();
  const rows = await contentDb
    .select({ sid: courseTargets.subjectMasterId, n: sql<number>`count(distinct ${courses.id})::int` })
    .from(courseTargets)
    .innerJoin(courses, eq(courses.id, courseTargets.courseId))
    .where(and(inArray(courseTargets.subjectMasterId, ids), eq(courses.status, 'published')))
    .groupBy(courseTargets.subjectMasterId);
  return new Map(rows.map((r) => [r.sid!, r.n]));
}

type SubjectRow = typeof subjectMasters.$inferSelect;
const toSubject = (s: SubjectRow, n = 0) => ({ id: s.id, name: s.name, nameBn: s.nameBn, slug: s.slug, aliases: s.aliases, category: s.category, courseCount: n });

export async function listSubjects(q: { q?: string; institutionId?: string; departmentId?: string; semester?: number }) {
  if (q.institutionId) {
    const conds: SQL[] = [eq(institutionSubjects.institutionId, q.institutionId)];
    if (q.departmentId) conds.push(eq(institutionSubjects.departmentId, q.departmentId));
    if (q.semester) conds.push(eq(institutionSubjects.semester, q.semester));
    const rows = await contentDb.select({ s: subjectMasters, code: institutionSubjects.courseCode }).from(institutionSubjects).innerJoin(subjectMasters, eq(subjectMasters.id, institutionSubjects.subjectMasterId)).where(and(...conds)).orderBy(asc(institutionSubjects.courseCode));
    const counts = await subjectCourseCounts(rows.map((r) => r.s.id));
    return { items: rows.map((r) => ({ ...toSubject(r.s, counts.get(r.s.id)), courseCode: r.code })) };
  }
  const conds: SQL[] = [eq(subjectMasters.status, 'approved')];
  if (q.q) conds.push(or(textMatch(subjectMasters.name, q.q), textMatch(subjectMasters.nameBn, q.q), sql`exists (select 1 from unnest(${subjectMasters.aliases}) a where a ilike ${'%' + q.q + '%'})`)!);
  const rows = await contentDb.select().from(subjectMasters).where(and(...conds)).orderBy(asc(subjectMasters.name)).limit(200);
  const counts = await subjectCourseCounts(rows.map((r) => r.id));
  return { items: rows.map((r) => toSubject(r, counts.get(r.id))) };
}

export async function subjectBySlug(slug: string) {
  const [s] = await contentDb.select().from(subjectMasters).where(and(eq(subjectMasters.slug, slug), eq(subjectMasters.status, 'approved')));
  if (!s) throw notFound('এই সাবজেক্টটি পাওয়া যায়নি।');
  const offerings = await contentDb
    .select({ i: institutions, code: institutionSubjects.courseCode, semester: institutionSubjects.semester })
    .from(institutionSubjects)
    .innerJoin(institutions, eq(institutions.id, institutionSubjects.institutionId))
    .where(and(eq(institutionSubjects.subjectMasterId, s.id), eq(institutions.status, 'approved')))
    .orderBy(asc(institutions.name));
  const counts = await subjectCourseCounts([s.id]);
  return {
    ...toSubject(s, counts.get(s.id)),
    offerings: offerings.map((o) => ({ institutionId: o.i.id, institutionName: o.i.name, institutionShort: o.i.shortName, institutionSlug: o.i.slug, courseCode: o.code, semester: o.semester })),
  };
}

// ---------------------------------------------------------------- student academic profile

async function validateAcademic(a: { institutionId: string | null; departmentId?: string | null; programId?: string | null }) {
  if (!a.institutionId) return;
  const [inst] = await contentDb.select({ id: institutions.id, status: institutions.status }).from(institutions).where(eq(institutions.id, a.institutionId));
  if (!inst || inst.status !== 'approved') throw badRequest('ইউনিভার্সিটিটি তালিকায় নেই।');
  if (a.departmentId) {
    const [d] = await contentDb.select({ institutionId: departments.institutionId }).from(departments).where(eq(departments.id, a.departmentId));
    if (!d || d.institutionId !== a.institutionId) throw badRequest('বিভাগটি এই ইউনিভার্সিটির নয়।');
  }
  if (a.programId) {
    const [p] = await contentDb.select({ departmentId: programs.departmentId }).from(programs).where(eq(programs.id, a.programId));
    if (!p || p.departmentId !== a.departmentId) throw badRequest('প্রোগ্রামটি এই বিভাগের নয়।');
  }
}

export async function setAcademicProfile(userId: string, a: { institutionId: string | null; departmentId?: string | null; programId?: string | null; semester?: number | null; batch?: string | null; section?: string | null }) {
  await validateAcademic(a);
  const v = {
    institutionId: a.institutionId,
    departmentId: a.institutionId ? (a.departmentId ?? null) : null,
    programId: a.institutionId ? (a.programId ?? null) : null,
    semester: a.semester ?? null,
    batch: a.batch ?? null,
    section: a.section ?? null,
  };
  await coreDb.insert(studentProfiles).values({ userId, ...v }).onConflictDoUpdate({ target: studentProfiles.userId, set: { ...v, updatedAt: new Date() } });
  return getMe(userId);
}

export async function completeOnboarding(userId: string, body: { academic?: Parameters<typeof setAcademicProfile>[1]; nextExam?: { subjectId?: string | null; subjectName: string; courseCode?: string | null; examType: string; examDate: string } | null }) {
  if (body.academic) await setAcademicProfile(userId, body.academic);
  if (body.nextExam) {
    const date = new Date(body.nextExam.examDate);
    if (date.getTime() < Date.now() - 86_400_000) throw badRequest('পরীক্ষার তারিখ আজ বা পরের কোনো দিন দিন।');
    await coreDb.insert(examPlans).values({
      studentId: userId,
      subjectMasterId: body.nextExam.subjectId ?? null,
      subjectName: body.nextExam.subjectName,
      courseCode: body.nextExam.courseCode ?? null,
      examType: body.nextExam.examType,
      examDate: date,
    });
  }
  await coreDb.update(users).set({ onboardingCompletedAt: new Date() }).where(eq(users.id, userId));
  return getMe(userId);
}

export async function requestInstitution(userId: string, b: { name: string; type: string; city?: string; note?: string }) {
  const [dup] = await contentDb.select({ id: institutionRequests.id }).from(institutionRequests).where(and(eq(institutionRequests.requestedBy, userId), eq(institutionRequests.status, 'pending'), ilike(institutionRequests.name, b.name)));
  if (dup) throw conflict('এই প্রতিষ্ঠানের জন্য আপনার অনুরোধ আগেই জমা আছে।');
  const [row] = await contentDb.insert(institutionRequests).values({ requestedBy: userId, name: b.name, type: b.type, city: b.city ?? null, note: b.note ?? null }).returning();
  return { id: row!.id, status: row!.status };
}

// ---------------------------------------------------------------- admin

export async function adminListRequests(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(institutionRequests.status, q.status)];
  if (cur) conds.push(or(gt(institutionRequests.createdAt, new Date(String(cur[0]))), and(eq(institutionRequests.createdAt, new Date(String(cur[0]))), gt(institutionRequests.id, cur[1])))!);
  const rows = await contentDb.select().from(institutionRequests).where(and(...conds)).orderBy(asc(institutionRequests.createdAt), asc(institutionRequests.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  // batched cross-DB lookup of requesters (CORE)
  const requesterIds = [...new Set(pg.items.map((r) => r.requestedBy))];
  const people = requesterIds.length ? await coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.id, requesterIds)) : [];
  const byId = new Map(people.map((p) => [p.id, p]));
  const all = await contentDb.select({ id: institutions.id, name: institutions.name, shortName: institutions.shortName, aliases: institutions.aliases }).from(institutions).where(ne(institutions.status, 'merged'));
  const norm = (s: string) => s.toLowerCase().replace(/university|বিশ্ববিদ্যালয়|ইউনিভার্সিটি|[^a-z0-9ঀ-৿]/g, '');
  return {
    items: pg.items.map((r) => ({
      id: r.id,
      name: r.name,
      type: r.type as 'public' | 'private' | 'college' | 'polytechnic',
      city: r.city,
      note: r.note,
      status: r.status,
      requestedBy: byId.get(r.requestedBy) ?? null,
      similar: all
        .filter((i) => {
          const a = norm(r.name);
          return [i.name, i.shortName, ...i.aliases].some((n) => {
            const b = norm(n);
            return b.length > 1 && (a.includes(b) || b.includes(a));
          });
        })
        .slice(0, 5)
        .map(({ id, name, shortName }) => ({ id, name, shortName })),
      createdAt: r.createdAt.toISOString(),
    })),
    nextCursor: pg.nextCursor,
  };
}

const slugify = (s: string) =>
  s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60);

export async function adminDecideRequest(id: string, d: z.infer<typeof InstitutionRequestDecision>, actorId: string) {
  const [req] = await contentDb.select().from(institutionRequests).where(eq(institutionRequests.id, id));
  if (!req) throw notFound();
  if (req.status !== 'pending') throw conflict('এই অনুরোধের সিদ্ধান্ত আগেই হয়েছে।');
  let resolved: string | null = null;
  let status: string;
  if (d.action === 'approve') {
    const name = d.name ?? req.name;
    const slug = d.slug ?? (slugify(d.shortName) || slugify(name));
    const [exists] = await contentDb.select({ id: institutions.id }).from(institutions).where(eq(institutions.slug, slug));
    if (exists) throw conflict('এই slug আগেই আছে — merge করুন বা অন্য slug দিন।');
    const [inst] = await contentDb.insert(institutions).values({ name, shortName: d.shortName, slug, type: req.type, city: d.city ?? req.city, status: 'approved' }).returning();
    resolved = inst!.id;
    status = 'approved';
  } else if (d.action === 'merge') {
    const [target] = await contentDb.select().from(institutions).where(eq(institutions.id, d.institutionId));
    if (!target) throw notFound();
    if (!target.aliases.includes(req.name)) await contentDb.update(institutions).set({ aliases: [...target.aliases, req.name] }).where(eq(institutions.id, target.id));
    resolved = target.id;
    status = 'merged';
  } else status = 'rejected';
  await contentDb.update(institutionRequests).set({ status, resolvedInstitutionId: resolved, reviewedBy: actorId, reviewedAt: new Date() }).where(eq(institutionRequests.id, id));
  await audit({ actorId, action: `institution_request.${d.action}`, entity: 'institution_requests', entityId: id, before: { status: req.status }, after: { status, resolved } });
  return { ok: true as const };
}

const toAdminInst = (r: InstRow, c?: { courses: number; teachers: number }) => ({ ...toInstitution(r, c), status: r.status as 'pending' | 'approved' | 'merged' | 'rejected', aliases: r.aliases, website: r.website, nameBn: r.nameBn });

export async function adminListInstitutions(q: { q?: string; status?: string }) {
  const conds: SQL[] = [];
  if (q.status) conds.push(eq(institutions.status, q.status));
  if (q.q) conds.push(or(textMatch(institutions.name, q.q), textMatch(institutions.shortName, q.q))!);
  const rows = await contentDb.select().from(institutions).where(conds.length ? and(...conds) : undefined).orderBy(asc(institutions.name)).limit(500);
  const counts = await institutionCounts(rows.map((r) => r.id));
  return { items: rows.map((r) => toAdminInst(r, counts.get(r.id))) };
}

export async function adminUpsertInstitution(actorId: string, body: Partial<z.infer<typeof InstitutionUpsert>>, id?: string) {
  if (body.slug) {
    const [clash] = await contentDb.select({ id: institutions.id }).from(institutions).where(eq(institutions.slug, body.slug));
    if (clash && clash.id !== id) throw conflict('এই slug আগেই ব্যবহার হয়েছে।');
  }
  if (id) {
    const [before] = await contentDb.select().from(institutions).where(eq(institutions.id, id));
    if (!before) throw notFound();
    const [row] = await contentDb.update(institutions).set(body).where(eq(institutions.id, id)).returning();
    await audit({ actorId, action: 'institution.update', entity: 'institutions', entityId: id, before, after: row });
    return toAdminInst(row!);
  }
  const b = body as z.infer<typeof InstitutionUpsert>;
  const [row] = await contentDb.insert(institutions).values({ ...b, status: b.status ?? 'approved' }).returning();
  await audit({ actorId, action: 'institution.create', entity: 'institutions', entityId: row!.id, after: row });
  return toAdminInst(row!);
}

/** Merge duplicate: move departments/subjects/targets/profile refs to `intoId`, keep the name as alias. */
export async function adminMergeInstitution(actorId: string, id: string, intoId: string) {
  if (id === intoId) throw badRequest('একই প্রতিষ্ঠানে merge করা যায় না।');
  const [from] = await contentDb.select().from(institutions).where(eq(institutions.id, id));
  const [into] = await contentDb.select().from(institutions).where(eq(institutions.id, intoId));
  if (!from || !into) throw notFound();
  await contentDb.transaction(async (tx) => {
    await tx.update(departments).set({ institutionId: intoId }).where(eq(departments.institutionId, id)).catch(() => {
      throw conflict('একই নামের বিভাগ দুই জায়গাতেই আছে — আগে বিভাগ মুছুন।');
    });
    await tx.update(institutionSubjects).set({ institutionId: intoId }).where(eq(institutionSubjects.institutionId, id));
    await tx.update(courseTargets).set({ institutionId: intoId }).where(eq(courseTargets.institutionId, id));
    await tx.update(institutions).set({ aliases: [...new Set([...into.aliases, from.name, from.shortName, ...from.aliases])] }).where(eq(institutions.id, intoId));
    await tx.update(institutions).set({ status: 'merged', mergedIntoId: intoId }).where(eq(institutions.id, id));
  });
  // CORE refs (no cross-DB FK) — updated in the service, idempotent.
  await coreDb.update(studentProfiles).set({ institutionId: intoId }).where(eq(studentProfiles.institutionId, id));
  await audit({ actorId, action: 'institution.merge', entity: 'institutions', entityId: id, after: { intoId } });
  return { ok: true as const };
}

const toDept = (d: typeof departments.$inferSelect) => ({ id: d.id, institutionId: d.institutionId, name: d.name, nameBn: d.nameBn, shortName: d.shortName, icon: d.icon });

export async function adminUpsertDepartment(actorId: string, body: Partial<z.infer<typeof DepartmentUpsert>>, id?: string) {
  try {
    const [row] = id ? await contentDb.update(departments).set(body).where(eq(departments.id, id)).returning() : await contentDb.insert(departments).values(body as z.infer<typeof DepartmentUpsert>).returning();
    if (!row) throw notFound();
    await audit({ actorId, action: id ? 'department.update' : 'department.create', entity: 'departments', entityId: row.id, after: row });
    return toDept(row);
  } catch (e) {
    if ((e as { code?: string }).code === '23505' || (e as { cause?: { code?: string } }).cause?.code === '23505') throw conflict('এই সংক্ষিপ্ত নামে বিভাগ আগেই আছে।');
    throw e;
  }
}

export async function adminDeleteDepartment(actorId: string, id: string) {
  const [used] = await contentDb.select({ id: courseTargets.id }).from(courseTargets).where(eq(courseTargets.departmentId, id)).limit(1);
  if (used) throw conflict('এই বিভাগে কোর্স টার্গেট আছে — মুছে ফেলা যাবে না।');
  await contentDb.delete(departments).where(eq(departments.id, id));
  await audit({ actorId, action: 'department.delete', entity: 'departments', entityId: id });
  return { ok: true as const };
}

export async function adminCreateProgram(actorId: string, body: z.infer<typeof ProgramUpsert>) {
  const [row] = await contentDb.insert(programs).values(body).returning();
  await audit({ actorId, action: 'program.create', entity: 'programs', entityId: row!.id, after: row });
  return { id: row!.id, departmentId: row!.departmentId, name: row!.name, level: row!.level, semesters: row!.semesters };
}

export async function adminDeleteProgram(actorId: string, id: string) {
  await contentDb.delete(programs).where(eq(programs.id, id));
  await audit({ actorId, action: 'program.delete', entity: 'programs', entityId: id });
  return { ok: true as const };
}

export async function adminListSubjects(q: { q?: string; status?: string }) {
  const conds: SQL[] = [];
  if (q.status) conds.push(eq(subjectMasters.status, q.status));
  if (q.q) conds.push(or(textMatch(subjectMasters.name, q.q), textMatch(subjectMasters.slug, q.q))!);
  const rows = await contentDb.select().from(subjectMasters).where(conds.length ? and(...conds) : undefined).orderBy(asc(subjectMasters.name)).limit(500);
  const counts = await subjectCourseCounts(rows.map((r) => r.id));
  return { items: rows.map((r) => ({ ...toSubject(r, counts.get(r.id)), status: r.status })) };
}

export async function adminUpsertSubject(actorId: string, body: Partial<z.infer<typeof SubjectUpsert>> & { status?: string }, id?: string) {
  if (body.slug) {
    const [clash] = await contentDb.select({ id: subjectMasters.id }).from(subjectMasters).where(eq(subjectMasters.slug, body.slug));
    if (clash && clash.id !== id) throw conflict('এই slug আগেই ব্যবহার হয়েছে।');
  }
  const [row] = id ? await contentDb.update(subjectMasters).set(body).where(eq(subjectMasters.id, id)).returning() : await contentDb.insert(subjectMasters).values(body as z.infer<typeof SubjectUpsert>).returning();
  if (!row) throw notFound();
  await audit({ actorId, action: id ? 'subject.update' : 'subject.create', entity: 'subject_masters', entityId: row.id, after: row });
  return toSubject(row);
}

export async function adminCreateInstitutionSubject(actorId: string, body: z.infer<typeof InstitutionSubjectUpsert>) {
  const [row] = await contentDb
    .insert(institutionSubjects)
    .values({ institutionId: body.institutionId, departmentId: body.departmentId ?? null, programId: body.programId ?? null, semester: body.semester ?? null, subjectMasterId: body.subjectMasterId, courseCode: body.courseCode ?? null })
    .returning({ id: institutionSubjects.id });
  await audit({ actorId, action: 'institution_subject.create', entity: 'institution_subjects', entityId: row!.id, after: body });
  return { id: row!.id };
}

export async function adminDeleteInstitutionSubject(actorId: string, id: string) {
  await contentDb.delete(institutionSubjects).where(eq(institutionSubjects.id, id));
  await audit({ actorId, action: 'institution_subject.delete', entity: 'institution_subjects', entityId: id });
  return { ok: true as const };
}
