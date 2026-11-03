import { describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedCatalog } from '../src/database/seed/catalog';
import { contentDb, coreDb } from '../src/database/client';
import { institutions } from '../src/database/content/schema';
import { auditLogs, examPlans } from '../src/database/core/schema';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any

describe('catalog (public)', () => {
  it('lists, searches (name, alias, Bangla), filters and paginates institutions', async () => {
    const app = await getApp();
    await seedCatalog();
    const all = j(await app.inject({ url: '/api/v1/institutions?limit=5' }));
    expect(all.items).toHaveLength(5);
    expect(all.nextCursor).toBeTruthy();
    const p2 = j(await app.inject({ url: `/api/v1/institutions?limit=5&cursor=${all.nextCursor}` }));
    expect(p2.items[0].id).not.toBe(all.items[0].id);
    expect(j(await app.inject({ url: '/api/v1/institutions?q=MU%20Sylhet' })).items[0].slug).toBe('metropolitan-university');
    expect(j(await app.inject({ url: `/api/v1/institutions?q=${encodeURIComponent('বুয়েট')}` })).items[0].shortName).toBe('BUET');
    const pub = j(await app.inject({ url: '/api/v1/institutions?type=public' }));
    expect(pub.items.every((i: { type: string }) => i.type === 'public')).toBe(true);
  });

  it('returns institution detail with departments, programs and course codes', async () => {
    const app = await getApp();
    await seedCatalog();
    const r = await app.inject({ url: '/api/v1/institutions/metropolitan-university' });
    expect(r.statusCode).toBe(200);
    expect(r.headers['cache-control']).toContain('max-age=300');
    const d = j(r);
    const cse = d.departments.find((x: { shortName: string }) => x.shortName === 'CSE');
    expect(cse.programs.map((p: { name: string }) => p.name)).toContain('B.Sc. in CSE (Day)');
    expect(d.subjects.find((s: { courseCode: string }) => s.courseCode === 'CSE-2203').subjectSlug).toBe('data-structures');
    expect((await app.inject({ url: '/api/v1/institutions/nope' })).statusCode).toBe(404);
  });

  it('filters subjects by institution/semester and by alias', async () => {
    const app = await getApp();
    const ids = await seedCatalog();
    const sem4 = j(await app.inject({ url: `/api/v1/subjects?institutionId=${ids.inst['metropolitan-university']}&semester=4` }));
    expect(sem4.items.map((s: { courseCode: string }) => s.courseCode).sort()).toEqual(['CSE-2203', 'CSE-2205']);
    expect(j(await app.inject({ url: '/api/v1/subjects?q=DBMS' })).items[0].slug).toBe('database-management-systems');
    const ds = j(await app.inject({ url: '/api/v1/subjects/data-structures' }));
    expect(ds.offerings.length).toBeGreaterThanOrEqual(8);
  });
});

describe('academic profile + onboarding', () => {
  it('validates the department belongs to the institution and completes onboarding with an exam plan', async () => {
    const app = await getApp();
    const ids = await seedCatalog();
    const s = await createUser();
    const bad = await app.inject({ method: 'PUT', url: '/api/v1/me/academic-profile', headers: s.auth, payload: { institutionId: ids.inst['metropolitan-university'], departmentId: ids.dept['brac-university:CSE'] } });
    expect(bad.statusCode).toBe(400);
    const examDate = new Date(Date.now() + 5 * 86_400_000).toISOString();
    const ok = await app.inject({
      method: 'POST',
      url: '/api/v1/me/onboarding',
      headers: s.auth,
      payload: {
        academic: { institutionId: ids.inst['metropolitan-university'], departmentId: ids.dept['metropolitan-university:CSE'], programId: ids.prog['metropolitan-university:CSE:0'], semester: 5, batch: '56', section: 'A' },
        nextExam: { subjectId: ids.subj['data-structures'], subjectName: 'Data Structures', courseCode: 'CSE-2203', examType: 'midterm', examDate },
      },
    });
    expect(ok.statusCode).toBe(200);
    const me = j(ok);
    expect(me.onboardingCompleted).toBe(true);
    expect(me.academic).toMatchObject({ institutionShort: 'MU', departmentShort: 'CSE', programName: 'B.Sc. in CSE (Day)', semester: 5, batch: '56', section: 'A' });
    const plans = await coreDb.select().from(examPlans).where(eq(examPlans.studentId, s.user.id));
    expect(plans).toHaveLength(1);
  });

  it('onboarding is skippable (no body fields)', async () => {
    const app = await getApp();
    const s = await createUser();
    const r = await app.inject({ method: 'POST', url: '/api/v1/me/onboarding', headers: s.auth, payload: {} });
    expect(j(r).onboardingCompleted).toBe(true);
  });
});

describe('institution requests → admin', () => {
  it('student requests; admin sees similar matches, approves (creates institution) or merges (adds alias)', async () => {
    const app = await getApp();
    await seedCatalog();
    const s = await createUser();
    const admin = await createUser({ roles: ['admin'] });
    const r1 = await app.inject({ method: 'POST', url: '/api/v1/institution-requests', headers: s.auth, payload: { name: 'Leading University', type: 'private', city: 'সিলেট' } });
    expect(r1.statusCode).toBe(201);
    const r2 = await app.inject({ method: 'POST', url: '/api/v1/institution-requests', headers: s.auth, payload: { name: 'Metropolitan University Sylhet', type: 'private' } });
    const dup = await app.inject({ method: 'POST', url: '/api/v1/institution-requests', headers: s.auth, payload: { name: 'Leading University', type: 'private' } });
    expect(dup.statusCode).toBe(409);

    expect((await app.inject({ url: '/api/v1/admin/institution-requests', headers: s.auth })).statusCode).toBe(403);
    const q = j(await app.inject({ url: '/api/v1/admin/institution-requests', headers: admin.auth }));
    expect(q.items).toHaveLength(2);
    const metroReq = q.items.find((i: { id: string }) => i.id === j(r2).id);
    expect(metroReq.similar[0].shortName).toBe('MU');
    expect(metroReq.requestedBy.id).toBe(s.user.id);

    const appr = await app.inject({ method: 'PATCH', url: `/api/v1/admin/institution-requests/${j(r1).id}`, headers: admin.auth, payload: { action: 'approve', shortName: 'LU' } });
    expect(appr.statusCode).toBe(200);
    const [lu] = await contentDb.select().from(institutions).where(eq(institutions.slug, 'lu'));
    expect(lu?.status).toBe('approved');

    await app.inject({ method: 'PATCH', url: `/api/v1/admin/institution-requests/${j(r2).id}`, headers: admin.auth, payload: { action: 'merge', institutionId: metroReq.similar[0].id } });
    const [mu] = await contentDb.select().from(institutions).where(eq(institutions.slug, 'metropolitan-university'));
    expect(mu!.aliases).toContain('Metropolitan University Sylhet');
    const again = await app.inject({ method: 'PATCH', url: `/api/v1/admin/institution-requests/${j(r2).id}`, headers: admin.auth, payload: { action: 'reject' } });
    expect(again.statusCode).toBe(409);
    const logs = await coreDb.select().from(auditLogs);
    expect(logs.map((l) => l.action)).toEqual(expect.arrayContaining(['institution_request.approve', 'institution_request.merge']));
  });

  it('admin catalog CRUD + merge duplicates moves student profiles', async () => {
    const app = await getApp();
    const ids = await seedCatalog();
    const admin = await createUser({ roles: ['admin'] });
    const created = j(await app.inject({ method: 'POST', url: '/api/v1/admin/catalog/institutions', headers: admin.auth, payload: { name: 'Metro Uni (dup)', shortName: 'MUD', slug: 'metro-dup', type: 'private' } }));
    const s = await createUser();
    await app.inject({ method: 'PUT', url: '/api/v1/me/academic-profile', headers: s.auth, payload: { institutionId: created.id } });
    const m = await app.inject({ method: 'POST', url: `/api/v1/admin/catalog/institutions/${created.id}/merge`, headers: admin.auth, payload: { intoId: ids.inst['metropolitan-university'] } });
    expect(m.statusCode).toBe(200);
    const me = j(await app.inject({ url: '/api/v1/me', headers: s.auth }));
    expect(me.academic.institutionShort).toBe('MU');
    const dept = await app.inject({ method: 'POST', url: '/api/v1/admin/catalog/departments', headers: admin.auth, payload: { institutionId: ids.inst['metropolitan-university'], name: 'Computer Science again', shortName: 'CSE' } });
    expect(dept.statusCode).toBe(409);
    const subj = await app.inject({ method: 'POST', url: '/api/v1/admin/catalog/subjects', headers: admin.auth, payload: { name: 'Compiler Design', slug: 'compiler-design', aliases: ['Compiler'] } });
    expect(subj.statusCode).toBe(201);
    const map = await app.inject({ method: 'POST', url: '/api/v1/admin/catalog/institution-subjects', headers: admin.auth, payload: { institutionId: ids.inst['metropolitan-university'], departmentId: ids.dept['metropolitan-university:CSE'], semester: 8, subjectMasterId: j(subj).id, courseCode: 'CSE-4201' } });
    expect(map.statusCode).toBe(201);
  });
});
