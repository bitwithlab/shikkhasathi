import { inArray } from 'drizzle-orm';
import { contentDb } from '../../database/client';
import { departments, institutions, programs } from '../../database/content/schema';

/** Batched cross-database lookups (CORE rows → CONTENT names). Never one query per row. */
export async function lookupAcademicNames(ids: { institutionIds: string[]; departmentIds: string[]; programIds: string[] }) {
  const uniq = (a: string[]) => [...new Set(a.filter(Boolean))];
  const [insts, depts, progs] = await Promise.all([
    uniq(ids.institutionIds).length
      ? contentDb.select({ id: institutions.id, name: institutions.name, shortName: institutions.shortName, slug: institutions.slug }).from(institutions).where(inArray(institutions.id, uniq(ids.institutionIds)))
      : [],
    uniq(ids.departmentIds).length
      ? contentDb.select({ id: departments.id, name: departments.name, shortName: departments.shortName }).from(departments).where(inArray(departments.id, uniq(ids.departmentIds)))
      : [],
    uniq(ids.programIds).length ? contentDb.select({ id: programs.id, name: programs.name }).from(programs).where(inArray(programs.id, uniq(ids.programIds))) : [],
  ]);
  return {
    institution: new Map(insts.map((r) => [r.id, r])),
    department: new Map(depts.map((r) => [r.id, r])),
    program: new Map(progs.map((r) => [r.id, r])),
  };
}
