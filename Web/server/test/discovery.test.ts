import { describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp } from './helpers';
import { seedAll } from '../src/database/seed';
import { coreDb } from '../src/database/client';
import { enrollments, notifications, users } from '../src/database/core/schema';
import { issueSession } from '../src/modules/auth/auth.service';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any

async function login(email: string) {
  const [u] = await coreDb.select().from(users).where(eq(users.email, email));
  const s = await issueSession(u!, { platform: 'android' });
  return { id: u!.id, auth: { authorization: `Bearer ${s.accessToken}` } };
}

describe('search & ranking', () => {
  it('student at MU CSE sem 4: "DS" ranks own-syllabus CSE-2203 first with a match badge; others show mismatch', async () => {
    const app = await getApp();
    await seedAll();
    const s = await login('admin@shikkhasathi.com'); // seeded as MU CSE sem 5
    await coreDb.update((await import('../src/database/core/schema')).studentProfiles).set({ semester: 4 }).where(eq((await import('../src/database/core/schema')).studentProfiles.userId, s.id));
    const r = j(await app.inject({ url: '/api/v1/search?q=DS', headers: s.auth }));
    expect(r.engine).toBe('postgres');
    expect(r.interpreted[0]).toEqual({ term: 'ds', meaning: 'Data Structures' });
    expect(r.items[0].courseCode).toBe('CSE-2203');
    expect(r.items[0].compatibility).toBe('match');
    expect(r.items.some((c: { courseCode: string; compatibility: string }) => c.courseCode === 'CSE225' && c.compatibility === 'mismatch')).toBe(true);
    const bn = j(await app.inject({ url: `/api/v1/search?q=${encodeURIComponent('ডাটা স্ট্রাকচার')}`, headers: s.auth }));
    expect(bn.items.map((c: { id: string }) => c.id)).toEqual(r.items.map((c: { id: string }) => c.id));
  });

  it('course code in any format, filters, sort, pagination, zero results', async () => {
    const app = await getApp();
    await seedAll();
    for (const q of ['cse2203', 'CSE 2203', 'cse-2203']) expect(j(await app.inject({ url: `/api/v1/search?q=${encodeURIComponent(q)}` })).items[0].courseCode).toBe('CSE-2203');
    const free = j(await app.inject({ url: '/api/v1/search?price=free' }));
    expect(free.items.every((c: { price: number }) => c.price === 0)).toBe(true);
    expect(free.total).toBe(1);
    const finals = j(await app.inject({ url: '/api/v1/search?examType=final&sort=price_asc' }));
    expect(finals.items.every((c: { examType: string }) => c.examType === 'final')).toBe(true);
    const prices = finals.items.map((c: { price: number }) => c.price);
    expect(prices).toEqual([...prices].sort((a: number, b: number) => a - b));
    const p1 = j(await app.inject({ url: '/api/v1/search?limit=5' }));
    expect(p1.items).toHaveLength(5);
    const p2 = j(await app.inject({ url: `/api/v1/search?limit=5&cursor=${p1.nextCursor}` }));
    expect(p2.items[0].id).not.toBe(p1.items[0].id);
    expect(p1.total).toBe(12); // 13 seeded − 1 draft of the pending teacher
    const none = j(await app.inject({ url: '/api/v1/search?q=quantum%20chromodynamics' }));
    expect(none.items).toHaveLength(0);
    const nsu = j(await app.inject({ url: '/api/v1/search?q=nsu' }));
    expect(nsu.items.length).toBeGreaterThan(0);
  });

  it('autocomplete groups institutions, subjects, codes, teachers and courses', async () => {
    const app = await getApp();
    await seedAll();
    const r = j(await app.inject({ url: '/api/v1/search/suggest?q=data' }));
    const types = new Set(r.items.map((i: { type: string }) => i.type));
    expect(types.has('subject')).toBe(true);
    expect(types.has('course')).toBe(true);
    const code = j(await app.inject({ url: '/api/v1/search/suggest?q=CSE22' }));
    expect(code.items.some((i: { type: string; label: string }) => i.type === 'code' && i.label === 'CSE-2203')).toBe(true);
    const mu = j(await app.inject({ url: '/api/v1/search/suggest?q=metro' }));
    expect(mu.items[0]).toMatchObject({ type: 'institution', href: '/u/metropolitan-university' });
  });
});

describe('course details, storefront, recommendations, wishlist', () => {
  it('detail: breadcrumb, seller card, curriculum without media, resources, viewer flags; drafts hidden', async () => {
    const app = await getApp();
    await seedAll();
    const d = j(await app.inject({ url: '/api/v1/courses/data-structures-midterm-crash-cse-2203' }));
    expect(d.breadcrumb.map((b: { label: string }) => b.label)).toEqual(['Metropolitan University', 'CSE', '৪র্থ সেমিস্টার', 'Data Structures']);
    expect(d.seller).toMatchObject({ slug: 'rakibul-hasan', verified: true });
    expect(d.sections[0].chapters[0].lessons[0]).toMatchObject({ isPreview: true });
    expect(JSON.stringify(d)).not.toMatch(/videoAssetId|providerVideoId|storageKey/);
    expect(d.resources.previousQuestionYears).toEqual([2024, 2023, 2022]);
    expect(d.viewer.enrolled).toBe(false);
    expect((await app.inject({ url: '/api/v1/courses/dld-aiub-eee-2103-midterm' })).statusCode).toBe(404);
  });
  it('storefront + featured', async () => {
    const app = await getApp();
    await seedAll();
    const t = j(await app.inject({ url: '/api/v1/teachers/rakibul-hasan' }));
    expect(t.courses.length).toBe(3);
    expect(t.verified).toBe(true);
    const f = j(await app.inject({ url: '/api/v1/featured' }));
    expect(f.courses.length).toBe(8);
    expect(f.teachers.length).toBeGreaterThan(0);
    expect(f.stats.courses).toBe(12);
  });
  it('recommendations exclude enrolled courses and feed the dashboard; wishlist round-trip', async () => {
    const app = await getApp();
    const seeded = await seedAll();
    const s = await login('sadman@demo.shikkhasathi.com');
    const dsId = seeded.courses['data-structures-midterm-crash-cse-2203']!;
    const before = j(await app.inject({ url: '/api/v1/recommendations', headers: s.auth }));
    expect(before.items.map((c: { id: string }) => c.id)).toContain(dsId);
    await coreDb.insert(enrollments).values({ studentId: s.id, courseId: dsId });
    const after = j(await app.inject({ url: '/api/v1/recommendations', headers: s.auth }));
    expect(after.items.map((c: { id: string }) => c.id)).not.toContain(dsId);
    const dash = j(await app.inject({ url: '/api/v1/dashboard?fresh=1', headers: s.auth }));
    expect(dash.recommendations.data.length).toBeGreaterThan(0);
    await app.inject({ method: 'POST', url: `/api/v1/wishlist/${dsId}`, headers: s.auth });
    expect(j(await app.inject({ url: '/api/v1/me/wishlist', headers: s.auth })).items[0].id).toBe(dsId);
    expect(j(await app.inject({ url: '/api/v1/courses/data-structures-midterm-crash-cse-2203', headers: s.auth })).viewer).toMatchObject({ enrolled: true, wishlisted: true });
  });
});

describe('course request board', () => {
  it('create dedupes into a vote, toggles votes, teacher claims, publish notifies voters', async () => {
    const app = await getApp();
    const seeded = await seedAll();
    const a = await login('sadman@demo.shikkhasathi.com');
    const b = await login('nusrat@demo.shikkhasathi.com');
    const body = { institutionId: seeded.catalog.inst['metropolitan-university'], subjectId: seeded.catalog.subj['operating-systems'], subjectName: 'Operating Systems', courseCode: 'CSE-3201', examType: 'midterm' };
    const r1 = j(await app.inject({ method: 'POST', url: '/api/v1/course-requests', headers: a.auth, payload: body }));
    expect(r1.duplicate).toBe(false);
    const r2 = j(await app.inject({ method: 'POST', url: '/api/v1/course-requests', headers: b.auth, payload: { ...body, note: 'Final-এর আগেও লাগবে' } }));
    expect(r2.duplicate).toBe(true);
    expect(r2.request.voteCount).toBe(2);
    const un = j(await app.inject({ method: 'POST', url: `/api/v1/course-requests/${r1.request.id}/vote`, headers: b.auth }));
    expect(un).toMatchObject({ voteCount: 1, voted: false });
    await app.inject({ method: 'POST', url: `/api/v1/course-requests/${r1.request.id}/vote`, headers: b.auth });

    const t = await login('rakib@demo.shikkhasathi.com');
    expect((await app.inject({ method: 'POST', url: `/api/v1/course-requests/${r1.request.id}/claim`, headers: a.auth, payload: {} })).statusCode).toBe(403);
    const claimed = j(await app.inject({ method: 'POST', url: `/api/v1/course-requests/${r1.request.id}/claim`, headers: t.auth, payload: { courseId: seeded.courses['computer-networks-free-intro'] } }));
    expect(claimed.status).toBe('published'); // linked course is already published
    expect(claimed.claimedByName).toBe('মো. রাকিবুল হাসান');
    const notes = await coreDb.select().from(notifications).where(eq(notifications.type, 'request.published'));
    expect(new Set(notes.map((n) => n.userId))).toEqual(new Set([a.id, b.id]));
    const list = j(await app.inject({ url: '/api/v1/course-requests', headers: a.auth }));
    expect(list.items[0]).toMatchObject({ mine: true, voted: true, linkedCourseSlug: 'computer-networks-free-intro' });
  });
});
