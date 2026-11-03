import { and, eq, inArray } from 'drizzle-orm';
import type { SearchQuery } from '@shikkhasathi/shared';
import { contentDb } from '../../database/client';
import { courseTargets, courses, institutions, subjectMasters } from '../../database/content/schema';
import { defineJob } from '../../common/jobs';
import { logger } from '../../common/logger';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';

const INDEX = 'courses';

/**
 * Meilisearch: one document per course with the target fields as arrays (filterable). Text relevance only —
 * the server re-ranks with the spec §6.5 score. Synonyms are configured on the index (ADR-017).
 */
export class MeiliClient {
  constructor(private readonly host: string, private readonly key: string | undefined, private readonly fetchImpl: typeof fetch = fetch) {}
  private async req(path: string, init: RequestInit = {}) {
    const res = await this.fetchImpl(`${this.host.replace(/\/$/, '')}${path}`, { ...init, headers: { 'content-type': 'application/json', ...(this.key ? { authorization: `Bearer ${this.key}` } : {}), ...(init.headers ?? {}) }, signal: AbortSignal.timeout(5000) });
    if (!res.ok) throw new Error(`meili ${res.status}`);
    return res.json() as Promise<Record<string, unknown>>;
  }
  async health() {
    return this.req('/health');
  }
  async configure() {
    await this.req(`/indexes/${INDEX}/settings`, {
      method: 'PATCH',
      body: JSON.stringify({
        searchableAttributes: ['title', 'courseCodes', 'subjectNames', 'institutionNames', 'sellerName', 'shortDescription'],
        filterableAttributes: ['institutionIds', 'departmentIds', 'semesters', 'subjectIds', 'courseCodesNorm', 'examType', 'courseType', 'language', 'price', 'sellerId'],
        synonyms: { ds: ['data structures'], 'data structures': ['ds'], dbms: ['database management systems'], oop: ['object oriented programming'], dld: ['digital logic design'], 'ডাটা স্ট্রাকচার': ['data structures'], 'অ্যালগরিদম': ['algorithms'] },
        typoTolerance: { enabled: true, minWordSizeForTypos: { oneTypo: 4, twoTypos: 8 } },
      }),
    });
  }
  async upsert(docs: Record<string, unknown>[]) {
    if (docs.length) await this.req(`/indexes/${INDEX}/documents?primaryKey=id`, { method: 'POST', body: JSON.stringify(docs) });
  }
  async remove(id: string) {
    await this.req(`/indexes/${INDEX}/documents/${id}`, { method: 'DELETE' });
  }
  async searchIds(q: string, f: Partial<SearchQuery>) {
    const filter: string[] = [];
    if (f.institutionId) filter.push(`institutionIds = "${f.institutionId}"`);
    if (f.subjectId) filter.push(`subjectIds = "${f.subjectId}"`);
    if (f.examType) filter.push(`examType = "${f.examType}"`);
    const r = (await this.req(`/indexes/${INDEX}/search`, { method: 'POST', body: JSON.stringify({ q, limit: 300, attributesToRetrieve: ['id'], ...(filter.length ? { filter } : {}) }) })) as { hits: { id: string }[] };
    return r.hits.map((h) => h.id);
  }
}

export async function meili(): Promise<MeiliClient | null> {
  const c = await getIntegration('search');
  return c.host ? new MeiliClient(c.host, c.master_key) : null;
}

export async function courseDocument(courseId: string) {
  const [c] = await contentDb.select().from(courses).where(eq(courses.id, courseId));
  if (!c) return null;
  const ts = await contentDb.select().from(courseTargets).where(eq(courseTargets.courseId, courseId));
  const instIds = ts.map((t) => t.institutionId).filter((v): v is string => !!v);
  const subjIds = ts.map((t) => t.subjectMasterId).filter((v): v is string => !!v);
  const [insts, subs] = await Promise.all([
    instIds.length ? contentDb.select().from(institutions).where(inArray(institutions.id, instIds)) : [],
    subjIds.length ? contentDb.select().from(subjectMasters).where(inArray(subjectMasters.id, subjIds)) : [],
  ]);
  return {
    id: c.id,
    title: c.title,
    shortDescription: c.shortDescription,
    sellerName: c.sellerName,
    sellerId: c.sellerId,
    examType: c.examType,
    courseType: c.courseType,
    language: c.language,
    price: c.price,
    status: c.status,
    institutionIds: instIds,
    departmentIds: ts.map((t) => t.departmentId).filter(Boolean),
    semesters: ts.map((t) => t.semester).filter((v) => v !== null),
    subjectIds: subjIds,
    courseCodes: ts.map((t) => t.courseCode).filter(Boolean),
    courseCodesNorm: ts.map((t) => (t.courseCode ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '')).filter(Boolean),
    subjectNames: subs.flatMap((s) => [s.name, s.nameBn, ...s.aliases].filter(Boolean)),
    institutionNames: insts.flatMap((i) => [i.name, i.nameBn, i.shortName, ...i.aliases].filter(Boolean)),
  };
}

/** search.indexCourse — upsert published courses, remove everything else. No-op when Meili is not configured. */
defineJob<{ courseId?: string; all?: boolean }>('search.indexCourse', async ({ courseId, all }) => {
  const m = await meili();
  if (!m) return;
  try {
    if (all) {
      await m.configure();
      const ids = (await contentDb.select({ id: courses.id }).from(courses).where(and(eq(courses.status, 'published')))).map((r) => r.id);
      const docs = (await Promise.all(ids.map(courseDocument))).filter(Boolean) as Record<string, unknown>[];
      await m.upsert(docs);
      return;
    }
    if (!courseId) return;
    const doc = await courseDocument(courseId);
    if (doc && doc.status === 'published') await m.upsert([doc]);
    else await m.remove(courseId);
  } catch (err) {
    logger.warn({ err: (err as Error).message }, 'meili index failed');
    throw err;
  }
});

registerIntegrationTest('search', async (c) => {
  if (!c.host) return { ok: false, message: 'Meilisearch host দিন (না দিলে Postgres fallback চলবে)' };
  const m = new MeiliClient(c.host, c.master_key);
  await m.health();
  await m.configure();
  return { ok: true, message: 'Meilisearch সংযোগ ঠিক আছে — index settings আপডেট হয়েছে' };
});
