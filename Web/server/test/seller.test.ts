import { describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedCatalog } from '../src/database/seed/catalog';
import { contentDb, coreDb } from '../src/database/client';
import { courses, lessons } from '../src/database/content/schema';
import { auditLogs, notifications, sellerProfiles } from '../src/database/core/schema';
import { FakeEmailProvider } from '../src/services/email';
import { issueSession } from '../src/modules/auth/auth.service';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any

async function asSeller(verified: boolean) {
  const app = await getApp();
  const u = await createUser({ name: 'রাকিবুল হাসান', email: `t${Math.random()}@x.com` });
  const ids = await seedCatalog();
  const r = await app.inject({
    method: 'POST',
    url: '/api/v1/seller/apply',
    headers: u.auth,
    payload: { displayName: 'মো. রাকিবুল হাসান', slug: `rakib-${Math.floor(Math.random() * 1e6)}`, institutionId: ids.inst['metropolitan-university'], method: 'linkedin', evidence: { linkedinUrl: 'https://linkedin.com/in/rakib' }, payoutMethod: 'bkash', payoutAccount: '01711223344' },
  });
  expect(r.statusCode).toBe(200);
  expect(j(r).payoutAccountMasked).toBe('••••••3344');
  // roles now include seller → re-issue token
  const [row] = await coreDb.select().from((await import('../src/database/core/schema')).users).where(eq((await import('../src/database/core/schema')).users.id, u.user.id));
  const s = await issueSession(row!, { platform: 'android' });
  const auth = { authorization: `Bearer ${s.accessToken}` };
  if (verified) {
    const admin = await createUser({ roles: ['admin'] });
    const d = await app.inject({ method: 'PATCH', url: `/api/v1/admin/sellers/${u.user.id}/verification`, headers: admin.auth, payload: { decision: 'verified' } });
    expect(d.statusCode).toBe(200);
  }
  return { app, auth, userId: u.user.id, ids };
}

async function buildCourse(app: Awaited<ReturnType<typeof getApp>>, auth: Record<string, string>, ids: Awaited<ReturnType<typeof seedCatalog>>) {
  const c = j(await app.inject({ method: 'POST', url: '/api/v1/seller/courses', headers: auth, payload: { title: 'Data Structures — Midterm Crash Course' } }));
  await app.inject({ method: 'PATCH', url: `/api/v1/seller/courses/${c.id}`, headers: auth, payload: { shortDescription: 'BST, Heap, Graph — Midterm-এর সম্ভাব্য গুরুত্বপূর্ণ টপিক, আগের বছরের প্রশ্নসহ।', learnings: ['BST insert/delete', 'Heap sort', 'BFS/DFS'], price: 29900, compareAtPrice: 49900 } });
  await app.inject({ method: 'PUT', url: `/api/v1/seller/courses/${c.id}/targets`, headers: auth, payload: { targets: [{ institutionId: ids.inst['metropolitan-university'], departmentId: ids.dept['metropolitan-university:CSE'], semester: 4, subjectMasterId: ids.subj['data-structures'], courseCode: 'cse 2203', batches: ['56'], sections: ['A'] }] } });
  const v = j(await app.inject({ method: 'POST', url: '/api/v1/seller/uploads', headers: auth, payload: { kind: 'video', title: 'BST deletion' } }));
  const link = j(await app.inject({ method: 'POST', url: '/api/v1/seller/uploads', headers: auth, payload: { kind: 'video_link', title: 'Heap intro', url: 'https://youtu.be/abc', durationSec: 480 } }));
  const cur = await app.inject({
    method: 'PUT',
    url: `/api/v1/seller/courses/${c.id}/curriculum`,
    headers: auth,
    payload: {
      sections: [
        { title: 'Trees', chapters: [{ title: 'BST', lessons: [{ title: 'BST deletion (৩টি কেস)', type: 'video', videoAssetId: v.id, isPreview: true, isImportant: true }, { title: 'Notes', type: 'text', body: 'Inorder successor…' }] }] },
        { title: 'Heaps', chapters: [{ title: 'Heap', lessons: [{ title: 'Heap intro', type: 'video', videoAssetId: link.id }] }] },
      ],
    },
  });
  expect(cur.statusCode).toBe(200);
  return j(cur);
}

describe('seller application & verification', () => {
  it('apply adds seller role (pending), admin verifies, seller notified + emailed, audit-logged', async () => {
    const { app, userId } = await asSeller(true);
    const [p] = await coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.userId, userId));
    expect(p!.verificationStatus).toBe('verified');
    expect(p!.payoutAccountEnc).not.toContain('01711223344');
    expect((await coreDb.select().from(notifications).where(eq(notifications.userId, userId))).some((n) => n.type === 'seller.application_result')).toBe(true);
    expect((await coreDb.select().from(auditLogs)).some((l) => l.action === 'seller.verified')).toBe(true);
    void app;
  });
  it('university email verification by code; personal Gmail rejected', async () => {
    const { app, auth } = await asSeller(false);
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/verify-email/request', headers: auth, payload: { email: 'me@gmail.com' } })).statusCode).toBe(400);
    await app.inject({ method: 'POST', url: '/api/v1/seller/verify-email/request', headers: auth, payload: { email: 'rakib@metrouni.edu.bd' } });
    const code = FakeEmailProvider.outbox.at(-1)!.text.match(/\b(\d{6})\b/)![1]!;
    const r = await app.inject({ method: 'POST', url: '/api/v1/seller/verify-email/confirm', headers: auth, payload: { email: 'rakib@metrouni.edu.bd', code } });
    expect(j(r).universityEmailVerified).toBe(true);
  });
  it('non-sellers cannot use Teacher Studio; students cannot see the admin queue', async () => {
    const app = await getApp();
    const s = await createUser();
    expect((await app.inject({ url: '/api/v1/seller/courses', headers: s.auth })).statusCode).toBe(403);
    expect((await app.inject({ url: '/api/v1/admin/sellers', headers: s.auth })).statusCode).toBe(403);
  });
});

describe('course wizard → review → publish', () => {
  it('builds a course, unverified seller cannot submit, verified can; admin publishes', async () => {
    const unverified = await asSeller(false);
    const c0 = await buildCourse(unverified.app, unverified.auth, unverified.ids);
    expect((await unverified.app.inject({ method: 'POST', url: `/api/v1/seller/courses/${c0.id}/submit`, headers: unverified.auth, payload: { declaration: true } })).statusCode).toBe(403);

    const { app, auth, ids } = await asSeller(true);
    const c = await buildCourse(app, auth, ids);
    expect(c.lessonCount).toBe(3);
    expect(c.totalDurationSec).toBe(600 + 480);
    expect(c.targets[0]).toMatchObject({ courseCode: 'CSE-2203', institutionShort: 'MU', departmentShort: 'CSE', subjectName: 'Data Structures' });
    const cl = j(await app.inject({ url: `/api/v1/seller/courses/${c.id}/checklist`, headers: auth }));
    expect(cl.canSubmit).toBe(true);
    const sub = await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${c.id}/submit`, headers: auth, payload: { declaration: true } });
    expect(j(sub).status).toBe('in_review');

    const admin = await createUser({ roles: ['admin'] });
    const q = j(await app.inject({ url: '/api/v1/admin/courses/review', headers: admin.auth }));
    expect(q.items[0]).toMatchObject({ id: c.id, kind: 'new' });
    expect((await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'publish', checklist: { quality: true, noGuaranteeClaims: false, targeting: true, copyright: true } } })).statusCode).toBe(400);
    const pub = await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'publish', checklist: { quality: true, noGuaranteeClaims: true, targeting: true, copyright: true } } });
    expect(j(pub).status).toBe('published');
    const [row] = await contentDb.select().from(courses).where(eq(courses.id, c.id));
    expect(row!.publishedAt).toBeTruthy();
    expect(row!.sellerVerified).toBe(true);
  });

  it('checklist blocks guarantee claims and thin courses', async () => {
    const { app, auth } = await asSeller(true);
    const c = j(await app.inject({ method: 'POST', url: '/api/v1/seller/courses', headers: auth, payload: { title: 'DS ১০০% কমন সাজেশন' } }));
    const cl = j(await app.inject({ url: `/api/v1/seller/courses/${c.id}/checklist`, headers: auth }));
    expect(cl.canSubmit).toBe(false);
    expect(cl.items.find((i: { key: string }) => i.key === 'noGuarantee').ok).toBe(false);
    const sub = await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${c.id}/submit`, headers: auth, payload: { declaration: true } });
    expect(sub.statusCode).toBe(422);
  });

  it('changes requested → seller edits → back to draft; editing published title/price creates a revision, content edits go live', async () => {
    const { app, auth, ids } = await asSeller(true);
    const admin = await createUser({ roles: ['admin'] });
    const c = await buildCourse(app, auth, ids);
    await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${c.id}/submit`, headers: auth, payload: { declaration: true } });
    expect((await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'changes_requested', checklist: { quality: false, noGuaranteeClaims: true, targeting: true, copyright: true } } })).statusCode).toBe(400); // note required
    const cr = await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'changes_requested', note: 'প্রিভিউ ভিডিওর অডিও ঠিক করুন', checklist: { quality: false, noGuaranteeClaims: true, targeting: true, copyright: true } } });
    expect(j(cr).status).toBe('changes_requested');
    const ed = j(await app.inject({ method: 'PATCH', url: `/api/v1/seller/courses/${c.id}`, headers: auth, payload: { description: 'নতুন অডিও' } }));
    expect(ed.status).toBe('draft');
    await app.inject({ method: 'POST', url: `/api/v1/seller/courses/${c.id}/submit`, headers: auth, payload: { declaration: true } });
    await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'publish', checklist: { quality: true, noGuaranteeClaims: true, targeting: true, copyright: true } } });

    const rev = j(await app.inject({ method: 'PATCH', url: `/api/v1/seller/courses/${c.id}`, headers: auth, payload: { title: 'Data Structures — Final Crash Course', price: 39900, description: 'আপডেট' } }));
    expect(rev.pendingRevision).toBe(true);
    expect(rev.title).toBe('Data Structures — Final Crash Course'); // seller sees the draft revision
    const [live] = await contentDb.select().from(courses).where(eq(courses.id, c.id));
    expect(live!.title).toBe('Data Structures — Midterm Crash Course'); // public sees live
    expect(live!.description).toBe('আপডেট'); // content edits go live
    expect(live!.needsSpotReview).toBe(true);
    const q = j(await app.inject({ url: '/api/v1/admin/courses/review', headers: admin.auth }));
    expect(q.items.find((i: { id: string }) => i.id === c.id).kind).toBe('revision');
    const detail = j(await app.inject({ url: `/api/v1/admin/courses/${c.id}`, headers: admin.auth }));
    expect(detail.revision).toMatchObject({ title: 'Data Structures — Final Crash Course', price: 39900 });
    await app.inject({ method: 'POST', url: `/api/v1/admin/courses/${c.id}/decision`, headers: admin.auth, payload: { decision: 'publish', checklist: { quality: true, noGuaranteeClaims: true, targeting: true, copyright: true } } });
    const [after] = await contentDb.select().from(courses).where(eq(courses.id, c.id));
    expect(after!.title).toBe('Data Structures — Final Crash Course');
    expect(after!.price).toBe(39900);
  });

  it('curriculum replace keeps lesson ids and deletes removed ones; other sellers cannot touch it', async () => {
    const { app, auth, ids } = await asSeller(true);
    const c = await buildCourse(app, auth, ids);
    const first = c.sections[0].chapters[0].lessons[0];
    const body = { sections: [{ id: c.sections[0].id, title: 'Trees (updated)', chapters: [{ id: c.sections[0].chapters[0].id, title: 'BST', lessons: [{ ...first, title: 'BST deletion — updated', video: undefined, document: undefined }] }] }] };
    const r = j(await app.inject({ method: 'PUT', url: `/api/v1/seller/courses/${c.id}/curriculum`, headers: auth, payload: body }));
    expect(r.sections).toHaveLength(1);
    expect(r.sections[0].chapters[0].lessons[0].id).toBe(first.id);
    expect(await contentDb.select().from(lessons).where(eq(lessons.courseId, c.id))).toHaveLength(1);
    const other = await asSeller(true);
    expect((await app.inject({ url: `/api/v1/seller/courses/${c.id}`, headers: other.auth })).statusCode).toBe(403);
  });

  it('document upload: signed URL → PUT → complete; cover upload returns a public URL', async () => {
    const { app, auth, ids } = await asSeller(true);
    const c = await buildCourse(app, auth, ids);
    const up = j(await app.inject({ method: 'POST', url: '/api/v1/seller/uploads', headers: auth, payload: { kind: 'document', courseId: c.id, docKind: 'previous_questions', title: 'DS Midterm 2024', year: 2024, mime: 'application/pdf', size: 12 } }));
    expect(up.method).toBe('PUT');
    expect((await app.inject({ method: 'POST', url: `/api/v1/seller/uploads/${up.id}/complete`, headers: auth })).statusCode).toBe(400); // not uploaded yet
    const u = new URL(up.uploadUrl);
    const put = await app.inject({ method: 'PUT', url: `${u.pathname}${u.search}`, headers: { 'content-type': 'application/pdf' }, payload: Buffer.from('%PDF-1.4 test') });
    expect(put.statusCode).toBe(200);
    expect((await app.inject({ method: 'POST', url: `/api/v1/seller/uploads/${up.id}/complete`, headers: auth })).statusCode).toBe(200);
    const cv = j(await app.inject({ method: 'POST', url: '/api/v1/seller/uploads', headers: auth, payload: { kind: 'cover', mime: 'image/png', size: 100 } }));
    expect(cv.publicUrl).toContain('/files/public/course-covers/');
    const tampered = await app.inject({ method: 'PUT', url: '/api/v1/files/upload?token=bad', headers: { 'content-type': 'application/pdf' }, payload: Buffer.from('x') });
    expect(tampered.statusCode).toBe(401);
  });
});
