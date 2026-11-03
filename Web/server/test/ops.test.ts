import { describe, expect, it } from 'vitest';
import { and, eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedAll } from '../src/database/seed';
import { contentDb, coreDb } from '../src/database/client';
import { auditLogs, enrollments, ledgerEntries, notifications, reviews, sellerProfiles, users } from '../src/database/core/schema';
import { courses } from '../src/database/content/schema';
import { issueSession } from '../src/modules/auth/auth.service';
import { ledgerAppend } from '../src/modules/orders/orders.service';
import { FakeEmailProvider } from '../src/services/email/fake';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any
async function as(email: string) {
  const [u] = await coreDb.select().from(users).where(eq(users.email, email));
  return { id: u!.id, auth: { authorization: `Bearer ${(await issueSession(u!, { platform: 'android' })).accessToken}` } };
}

describe('payouts', () => {
  it('seller requests ≤ available (≥ min); admin approves → marks paid → ledger debit, notification + email; one open at a time', async () => {
    const app = await getApp();
    await seedAll();
    const rakib = await as('rakib@demo.shikkhasathi.com');
    const admin = await as('admin@shikkhasathi.com');
    // released earnings: ৳1,000
    await ledgerAppend(rakib.id, { type: 'sale', amount: 100_000, description: 'seed sale', availableAt: new Date(Date.now() - 1000), dedupeKey: 'test:sale:1' });
    const bal = j(await app.inject({ url: '/api/v1/seller/ledger', headers: rakib.auth })).balance;
    expect(bal.available).toBeGreaterThanOrEqual(100_000);
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/payouts', headers: rakib.auth, payload: { amount: 100 } })).statusCode).toBe(400); // below minimum
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/payouts', headers: rakib.auth, payload: { amount: bal.available + 100 } })).statusCode).toBe(400);
    const p = await app.inject({ method: 'POST', url: '/api/v1/seller/payouts', headers: rakib.auth, payload: { amount: 60_000 } });
    expect(p.statusCode).toBe(201);
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/payouts', headers: rakib.auth, payload: { amount: 60_000 } })).statusCode).toBe(409);
    // pending payout is held out of available
    expect(j(await app.inject({ url: '/api/v1/seller/ledger', headers: rakib.auth })).balance.available).toBe(bal.available - 60_000);
    expect((await app.inject({ url: '/api/v1/admin/payouts', headers: rakib.auth })).statusCode).toBe(403);
    const q = j(await app.inject({ url: '/api/v1/admin/payouts', headers: admin.auth }));
    expect(q.items[0]).toMatchObject({ id: j(p).id, sellerName: 'মো. রাকিবুল হাসান', status: 'requested' });
    expect(j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/payouts/${j(p).id}`, headers: admin.auth, payload: { decision: 'approve' } })).status).toBe('approved');
    expect((await app.inject({ method: 'PATCH', url: `/api/v1/admin/payouts/${j(p).id}`, headers: admin.auth, payload: { decision: 'paid' } })).statusCode).toBe(400); // reference required
    const paid = j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/payouts/${j(p).id}`, headers: admin.auth, payload: { decision: 'paid', reference: 'BKX9AB12CD' } }));
    expect(paid.status).toBe('paid');
    const debit = await coreDb.select().from(ledgerEntries).where(and(eq(ledgerEntries.sellerId, rakib.id), eq(ledgerEntries.type, 'payout')));
    expect(debit).toHaveLength(1);
    expect(debit[0]!.amount).toBe(-60_000);
    const after = j(await app.inject({ url: '/api/v1/seller/ledger', headers: rakib.auth })).balance;
    expect(after).toMatchObject({ available: bal.available - 60_000, pendingPayouts: 0, paidOut: 60_000 });
    expect((await coreDb.select().from(notifications).where(and(eq(notifications.userId, rakib.id), eq(notifications.type, 'seller.payout_paid')))).length).toBe(1);
    expect(FakeEmailProvider.outbox.some((m) => m.subject.includes('পেআউট'))).toBe(true);
    expect((await app.inject({ method: 'PATCH', url: `/api/v1/admin/payouts/${j(p).id}`, headers: admin.auth, payload: { decision: 'reject' } })).statusCode).toBe(409);
    expect(j(await app.inject({ url: '/api/v1/seller/payouts', headers: rakib.auth })).items[0].status).toBe('paid');
  });
});

describe('admin users + moderation', () => {
  it('search, suspend (sessions end, token rejected), reactivate, cannot suspend self; roles change audited', async () => {
    const app = await getApp();
    await seedAll();
    const admin = await as('admin@shikkhasathi.com');
    const s = await createUser({ name: 'Rafiq Islam', email: 'rafiq@example.com' });
    const found = j(await app.inject({ url: '/api/v1/admin/users?q=rafiq', headers: admin.auth }));
    expect(found.items).toHaveLength(1);
    expect(j(await app.inject({ url: '/api/v1/admin/users?role=seller', headers: admin.auth })).items.every((u: { roles: string[] }) => u.roles.includes('seller'))).toBe(true);
    const sus = j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/users/${s.user.id}`, headers: admin.auth, payload: { status: 'suspended', reason: 'spam' } }));
    expect(sus.status).toBe('suspended');
    expect((await app.inject({ url: '/api/v1/me/courses', headers: s.auth })).statusCode).toBe(403);
    expect((await app.inject({ method: 'PATCH', url: `/api/v1/admin/users/${admin.id}`, headers: admin.auth, payload: { status: 'suspended' } })).statusCode).toBe(403);
    await app.inject({ method: 'PATCH', url: `/api/v1/admin/users/${s.user.id}`, headers: admin.auth, payload: { status: 'active' } });
    expect((await app.inject({ url: '/api/v1/me/courses', headers: s.auth })).statusCode).not.toBe(403);
    const r = j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/users/${s.user.id}`, headers: admin.auth, payload: { roles: ['seller'] } }));
    expect(r.roles.sort()).toEqual(['seller', 'student']);
    expect((await coreDb.select().from(auditLogs).where(eq(auditLogs.action, 'user.suspend'))).length).toBe(1);
  });

  it('report a review → admin hides it (rating recomputed), all reports on the target resolve; restore works', async () => {
    const app = await getApp();
    const seeded = await seedAll();
    const admin = await as('admin@shikkhasathi.com');
    const ds = seeded.courses['data-structures-midterm-crash-cse-2203']!;
    const [course] = await contentDb.select().from(courses).where(eq(courses.id, ds));
    const writer = await createUser({ name: 'Troll' });
    await coreDb.insert(enrollments).values({ studentId: writer.user.id, courseId: ds, progressPercent: 50 });
    const rv = j(await app.inject({ method: 'PUT', url: `/api/v1/courses/${ds}/review`, headers: writer.auth, payload: { rating: 1, body: 'এই কোর্স ১০০% পরীক্ষায় আসবে, আমার টেলিগ্রামে যোগ দিন' } }));
    const [c1] = await contentDb.select().from(courses).where(eq(courses.id, ds));
    // aggregates are recomputed from visible review rows (seeded demo totals are replaced on the first real review)
    const visible = await coreDb.select().from(reviews).where(and(eq(reviews.courseId, ds), eq(reviews.status, 'visible')));
    expect(c1!.ratingCount).toBe(visible.length);
    void course;
    const a = await createUser({ name: 'Reporter A' });
    const b = await createUser({ name: 'Reporter B' });
    const r1 = j(await app.inject({ method: 'POST', url: '/api/v1/reports', headers: a.auth, payload: { targetType: 'review', targetId: rv.id, reason: 'spam' } }));
    expect(r1.duplicate).toBe(false);
    expect(j(await app.inject({ method: 'POST', url: '/api/v1/reports', headers: a.auth, payload: { targetType: 'review', targetId: rv.id, reason: 'spam' } })).duplicate).toBe(true);
    await app.inject({ method: 'POST', url: '/api/v1/reports', headers: b.auth, payload: { targetType: 'review', targetId: rv.id, reason: 'guarantee_claim' } });
    expect((await app.inject({ method: 'POST', url: '/api/v1/reports', headers: a.auth, payload: { targetType: 'review', targetId: a.user.id, reason: 'spam' } })).statusCode).toBe(404);
    const q = j(await app.inject({ url: '/api/v1/admin/reports', headers: admin.auth }));
    expect(q.items).toHaveLength(2);
    expect(q.items[0]).toMatchObject({ targetType: 'review', sameTargetCount: 2, targetHidden: false });
    const h = j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/reports/${r1.id}`, headers: admin.auth, payload: { action: 'hide', resolution: 'guarantee claim + spam link' } }));
    expect(h).toMatchObject({ status: 'resolved', targetHidden: true });
    expect(j(await app.inject({ url: '/api/v1/admin/reports', headers: admin.auth })).items).toHaveLength(0);
    const [row] = await coreDb.select().from(reviews).where(eq(reviews.id, rv.id));
    expect(row!.status).toBe('hidden');
    const [c2] = await contentDb.select().from(courses).where(eq(courses.id, ds));
    expect(c2!.ratingCount).toBe(c1!.ratingCount - 1);
    expect(j(await app.inject({ url: `/api/v1/courses/${ds}/reviews?limit=50` })).items.find((x: { id: string }) => x.id === rv.id)).toBeUndefined();
    const res = j(await app.inject({ method: 'PATCH', url: `/api/v1/admin/reports/${r1.id}`, headers: admin.auth, payload: { action: 'restore' } }));
    expect(res.targetHidden).toBe(false);
  });
});

describe('broadcasts, logs, analytics, seller insights', () => {
  it('broadcast to sellers: preview count, in-app notifications, email optional, history', async () => {
    const app = await getApp();
    await seedAll();
    const admin = await as('admin@shikkhasathi.com');
    const pv = j(await app.inject({ method: 'POST', url: '/api/v1/admin/broadcasts/preview', headers: admin.auth, payload: { audience: 'sellers' } }));
    expect(pv.recipients).toBeGreaterThan(0);
    expect((await app.inject({ method: 'POST', url: '/api/v1/admin/broadcasts', headers: admin.auth, payload: { title: 'নতুন ফিচার', body: 'মক টেস্ট এখন কোর্সে যোগ করা যায়।', link: 'https://evil.example', audience: 'sellers' } })).statusCode).toBe(422);
    const bc = j(await app.inject({ method: 'POST', url: '/api/v1/admin/broadcasts', headers: admin.auth, payload: { title: 'নতুন ফিচার', body: 'মক টেস্ট এখন কোর্সে যোগ করা যায়।', link: '/teach', audience: 'sellers', email: true } }));
    expect(bc.recipients).toBe(pv.recipients);
    const sellerIds = (await coreDb.select({ id: sellerProfiles.userId }).from(sellerProfiles)).map((x) => x.id);
    const ns = await coreDb.select().from(notifications).where(eq(notifications.type, 'admin.broadcast'));
    expect(ns.length).toBe(pv.recipients);
    expect(ns.every((n) => sellerIds.includes(n.userId))).toBe(true);
    expect(j(await app.inject({ url: '/api/v1/admin/broadcasts', headers: admin.auth })).items[0]).toMatchObject({ title: 'নতুন ফিচার', recipients: pv.recipients });
    const logs = j(await app.inject({ url: '/api/v1/admin/email-logs?q=admin_broadcast', headers: admin.auth }));
    expect(logs.items.length).toBeGreaterThan(0);
    const au = j(await app.inject({ url: '/api/v1/admin/audit?action=broadcast', headers: admin.auth }));
    expect(au.items[0].action).toBe('broadcast.send');
  });

  it('audit log redacts secrets; analytics + seller students/analytics return real numbers', async () => {
    const app = await getApp();
    await seedAll();
    const admin = await as('admin@shikkhasathi.com');
    await coreDb.insert(auditLogs).values({ actorId: admin.id, action: 'settings.update', entity: 'integration_settings', entityId: 'brevo', afterJson: { api_key: 'xkeysib-123', sender_name: 'ShikkhaSathi' } });
    const au = j(await app.inject({ url: '/api/v1/admin/audit?entity=integration_settings', headers: admin.auth }));
    expect(au.items[0].after).toEqual({ api_key: '••••', sender_name: 'ShikkhaSathi' });
    const an = j(await app.inject({ url: '/api/v1/admin/analytics?days=30', headers: admin.auth }));
    expect(an.series).toHaveLength(30);
    expect(an.queues.pendingSellers).toBeGreaterThanOrEqual(1);
    expect(an.totals.enrollments).toBeGreaterThan(0); // seeded demo enrollments
    const rakib = await as('rakib@demo.shikkhasathi.com');
    const st = j(await app.inject({ url: '/api/v1/seller/students', headers: rakib.auth }));
    expect(st.total).toBeGreaterThan(0);
    expect(st.items[0].studentName).toMatch(/\.$/); // short name only
    expect(JSON.stringify(st)).not.toContain('@');
    const sa = j(await app.inject({ url: '/api/v1/seller/analytics?days=30', headers: rakib.auth }));
    expect(sa.series).toHaveLength(30);
    expect(sa.courses.length).toBeGreaterThan(0);
    expect(sa.totals.students).toBeGreaterThan(0);
    expect((await app.inject({ url: '/api/v1/admin/analytics', headers: rakib.auth })).statusCode).toBe(403);
  });
});
