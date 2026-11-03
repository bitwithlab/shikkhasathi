import { describe, expect, it } from 'vitest';
import { and, eq, gt } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { seedAll } from '../src/database/seed';
import { contentDb, coreDb } from '../src/database/client';
import { auditLogs, coupons, enrollments, invoices, ledgerEntries, lessonProgress, notifications, orders, payments, users } from '../src/database/core/schema';
import { courses } from '../src/database/content/schema';
import { FakeGateway } from '../src/services/payments';
import { FakeEmailProvider } from '../src/services/email/fake';
import { storage } from '../src/services/storage';
import { issueSession } from '../src/modules/auth/auth.service';
import { reconcile } from '../src/modules/orders/orders.service';

const j = (r: { json: () => unknown }) => r.json() as any; // eslint-disable-line @typescript-eslint/no-explicit-any
const form = (o: Record<string, string | number>) => new URLSearchParams(Object.entries(o).map(([k, v]): [string, string] => [k, String(v)])).toString();

async function setup() {
  const app = await getApp();
  await seedAll();
  const [course] = await contentDb.select().from(courses).where(and(eq(courses.status, 'published'), gt(courses.price, 0))).limit(1);
  const student = await createUser({ name: 'Nadia Rahman', email: `nadia${Date.now()}@example.com` });
  return { app, course: course!, student };
}

async function login(email: string) {
  const [u] = await coreDb.select().from(users).where(eq(users.email, email));
  const s = await issueSession(u!, { platform: 'android' });
  return { id: u!.id, auth: { authorization: `Bearer ${s.accessToken}` } };
}

async function payFake(app: Awaited<ReturnType<typeof getApp>>, auth: Record<string, string>, orderId: string, outcome: 'success' | 'fail' = 'success', amountOverride?: number) {
  const p = j(await app.inject({ method: 'POST', url: `/api/v1/orders/${orderId}/pay`, headers: auth, payload: { method: 'bkash' } }));
  expect(p.gateway).toBe('fake');
  const page = await app.inject({ url: new URL(p.redirectUrl).pathname });
  expect(page.statusCode).toBe(200);
  expect(page.body).toContain('ডেভ পেমেন্ট গেটওয়ে');
  const [row] = await coreDb.select().from(payments).where(eq(payments.orderId, orderId)).orderBy(payments.createdAt);
  const body = { tran_id: row!.gatewayTrxId, amount: amountOverride ?? row!.amount, outcome, sig: FakeGateway.sign(row!.gatewayTrxId, outcome) };
  const cb = await app.inject({ method: 'POST', url: `/api/v1/payments/callback/fake?status=${outcome}`, headers: { 'content-type': 'application/x-www-form-urlencoded' }, payload: form(body) });
  return { cb, body, trxId: row!.gatewayTrxId };
}

describe('orders & checkout', () => {
  it('creates a server-priced order idempotently and blocks buying own/owned courses', async () => {
    const { app, course, student } = await setup();
    const key = 'idem-key-0001';
    const a = await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: key } });
    expect(a.statusCode).toBe(201);
    const b = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: key } }));
    expect(b.id).toBe(j(a).id);
    expect(j(a).total).toBe(course.price);
    expect(j(a).status).toBe('pending');
    expect(await coreDb.select().from(orders).where(eq(orders.studentId, student.user.id))).toHaveLength(1);
    // seller can't buy own course
    const seller = await coreDb.select().from(users).where(eq(users.id, course.sellerId));
    const s = await issueSession(seller[0]!, { platform: 'android' });
    const own = await app.inject({ method: 'POST', url: '/api/v1/orders', headers: { authorization: `Bearer ${s.accessToken}` }, payload: { courseId: course.id, idempotencyKey: 'idem-own-01' } });
    expect(own.statusCode).toBe(400);
    // guests can't
    expect((await app.inject({ method: 'POST', url: '/api/v1/orders', payload: { courseId: course.id, idempotencyKey: 'idem-guest' } })).statusCode).toBe(401);
    // other students can't read it
    const other = await createUser({ name: 'Other Student' });
    expect((await app.inject({ url: `/api/v1/orders/${j(a).id}`, headers: other.auth })).statusCode).toBe(403);
  });

  it('fake bKash success → one enrollment, ledger, invoice PDF, email, notifications even with duplicate callbacks/IPN', async () => {
    const { app, course, student } = await setup();
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-pay-0001' } }));
    const { cb, body } = await payFake(app, student.auth, o.id);
    expect(cb.statusCode).toBe(303);
    expect(cb.headers.location).toBe(`/checkout/result?order=${o.id}&status=paid`);
    // replay browser callback and IPN
    await app.inject({ method: 'POST', url: '/api/v1/payments/callback/fake?status=success', headers: { 'content-type': 'application/x-www-form-urlencoded' }, payload: form(body) });
    const ipn = await app.inject({ method: 'POST', url: '/api/v1/webhooks/payments/fake', headers: { 'content-type': 'application/x-www-form-urlencoded' }, payload: form(body) });
    expect(ipn.statusCode).toBe(200);

    const order = j(await app.inject({ url: `/api/v1/orders/${o.id}`, headers: student.auth }));
    expect(order.status).toBe('paid');
    expect(order.invoiceNumber).toMatch(/^SS-\d{4}-\d{6}$/);
    expect(order.refundEligible).toBe(true);
    expect(order.firstLessonPath).toMatch(/^\/learn\//);
    const enr = await coreDb.select().from(enrollments).where(and(eq(enrollments.studentId, student.user.id), eq(enrollments.courseId, course.id)));
    expect(enr).toHaveLength(1);
    const led = await coreDb.select().from(ledgerEntries).where(eq(ledgerEntries.orderId, o.id));
    expect(led.map((l) => l.type).sort()).toEqual(['commission', 'sale']);
    expect(led.find((l) => l.type === 'sale')!.amount).toBe(course.price);
    expect(led.find((l) => l.type === 'commission')!.amount).toBeLessThan(0);
    expect(led.every((l) => l.availableAt > new Date())).toBe(true);
    const [inv] = await coreDb.select().from(invoices).where(eq(invoices.orderId, o.id));
    const pdf = await storage().get('invoices', inv!.storageKey!);
    expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
    const url = j(await app.inject({ url: `/api/v1/orders/${o.id}/invoice`, headers: student.auth }));
    expect(url.number).toBe(inv!.number);
    expect(FakeEmailProvider.outbox.filter((m) => m.to === student.user.email)).toHaveLength(1);
    const ns = await coreDb.select().from(notifications).where(eq(notifications.userId, student.user.id));
    expect(ns.filter((n) => n.type === 'order.paid')).toHaveLength(1);
    expect((await coreDb.select().from(notifications).where(and(eq(notifications.userId, course.sellerId), eq(notifications.type, 'seller.new_enrollment')))).length).toBe(1);
    const [after] = await contentDb.select().from(courses).where(eq(courses.id, course.id));
    expect(after!.enrollmentCount).toBe(course.enrollmentCount + 1);
    // can't buy again; can't pay again
    expect((await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-pay-0002' } })).statusCode).toBe(409);
    expect((await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/pay`, headers: student.auth, payload: { method: 'bkash' } })).statusCode).toBe(409);
    expect((await coreDb.select().from(auditLogs).where(eq(auditLogs.action, 'payment.succeeded'))).length).toBe(1);
  });

  it('rejects tampered signature and amount mismatch; failure path marks order failed', async () => {
    const { app, course, student } = await setup();
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-bad-0001' } }));
    // forged success without valid sig
    await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/pay`, headers: student.auth, payload: { method: 'nagad' } });
    const [p] = await coreDb.select().from(payments).where(eq(payments.orderId, o.id));
    const forged = await app.inject({ method: 'POST', url: '/api/v1/payments/callback/fake?status=success', headers: { 'content-type': 'application/x-www-form-urlencoded' }, payload: form({ tran_id: p!.gatewayTrxId, amount: p!.amount, outcome: 'success', sig: 'nope' }) });
    expect(forged.headers.location).toContain('status=failed');
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(0);
    // retry with wrong amount (validly signed)
    const { cb } = await payFake(app, student.auth, o.id, 'success', 100);
    expect(cb.headers.location).toContain('status=failed');
    expect(j(await app.inject({ url: `/api/v1/orders/${o.id}`, headers: student.auth })).status).toBe('failed');
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(0);
    // explicit failure
    const o2 = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-bad-0002' } }));
    const f = await payFake(app, student.auth, o2.id, 'fail');
    expect(f.cb.headers.location).toBe(`/checkout/result?order=${o2.id}&status=failed`);
    // retry after failure succeeds
    const ok = await payFake(app, student.auth, o2.id, 'success');
    expect(ok.cb.headers.location).toContain('status=paid');
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(1);
  });

  it('coupons: percent/fixed math, scope, per-user limit, expiry; redemption counted once', async () => {
    const { app, course, student } = await setup();
    await coreDb.insert(coupons).values([
      { code: 'EXAM20', scope: 'platform', type: 'percent', value: 20 },
      { code: 'OLD10', scope: 'platform', type: 'percent', value: 10, expiresAt: new Date(Date.now() - 1000) },
      { code: 'BIG', scope: 'platform', type: 'fixed', value: 100, minAmount: 10_000_000 },
      { code: 'OTHERSELLER', scope: 'seller', sellerId: student.user.id, type: 'fixed', value: 5000 },
    ]);
    const v = j(await app.inject({ method: 'POST', url: '/api/v1/coupons/validate', headers: student.auth, payload: { code: 'exam20', courseId: course.id } }));
    expect(v).toMatchObject({ valid: true, discount: Math.round(course.price * 0.2), total: course.price - Math.round(course.price * 0.2) });
    for (const code of ['OLD10', 'BIG', 'OTHERSELLER', 'NOPE']) expect(j(await app.inject({ method: 'POST', url: '/api/v1/coupons/validate', headers: student.auth, payload: { code, courseId: course.id } })).valid).toBe(false);
    expect((await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, couponCode: 'OLD10', idempotencyKey: 'idem-cpn-0000' } })).statusCode).toBe(400);
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, couponCode: 'EXAM20', idempotencyKey: 'idem-cpn-0001' } }));
    expect(o.couponCode).toBe('EXAM20');
    expect(o.total).toBe(course.price - Math.round(course.price * 0.2));
    await payFake(app, student.auth, o.id);
    const [c] = await coreDb.select().from(coupons).where(eq(coupons.code, 'EXAM20'));
    expect(c!.usedCount).toBe(1);
    const sale = (await coreDb.select().from(ledgerEntries).where(and(eq(ledgerEntries.orderId, o.id), eq(ledgerEntries.type, 'sale'))))[0];
    expect(sale!.amount).toBe(o.total); // seller is credited the discounted amount
    expect(j(await app.inject({ method: 'POST', url: '/api/v1/coupons/validate', headers: student.auth, payload: { code: 'EXAM20', courseId: course.id } })).message).toContain('আগেই');
  });

  it('free course enrolls immediately without payment', async () => {
    const { app, student } = await setup();
    const [free] = await contentDb.select().from(courses).where(and(eq(courses.status, 'published'), eq(courses.price, 0))).limit(1);
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: free!.id, idempotencyKey: 'idem-free-001' } }));
    expect(o.status).toBe('paid');
    expect(o.total).toBe(0);
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(1);
    expect(await coreDb.select().from(invoices).where(eq(invoices.orderId, o.id))).toHaveLength(0);
  });

  it('refund: eligibility rules, admin approval reverses ledger and revokes enrollment', async () => {
    const { app, course, student } = await setup();
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-ref-0001' } }));
    await payFake(app, student.auth, o.id);
    const short = await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/refund-request`, headers: student.auth, payload: { reason: 'short' } });
    expect(short.statusCode).toBe(422);
    const r = await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/refund-request`, headers: student.auth, payload: { reason: 'ভুল কোর্স কিনে ফেলেছি, আমার সেকশনের সিলেবাস আলাদা।' } });
    expect(r.statusCode).toBe(200);
    expect(j(r).refund.status).toBe('requested');
    expect((await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/refund-request`, headers: student.auth, payload: { reason: 'আবার অনুরোধ করছি দয়া করে' } })).statusCode).toBe(409);
    // student can't see admin queue
    expect((await app.inject({ url: '/api/v1/admin/refunds', headers: student.auth })).statusCode).toBe(403);
    const admin = await login('admin@shikkhasathi.com');
    const q = j(await app.inject({ url: '/api/v1/admin/refunds', headers: admin.auth }));
    expect(q.items).toHaveLength(1);
    expect(q.items[0].watchedPercent).toBe(0);
    const d = await app.inject({ method: 'PATCH', url: `/api/v1/admin/refunds/${q.items[0].id}`, headers: admin.auth, payload: { decision: 'approve', note: 'নীতিমালা অনুযায়ী' } });
    expect(d.statusCode).toBe(200);
    expect(j(d).status).toBe('processed');
    const [enr] = await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id));
    expect(enr!.status).toBe('refunded');
    const led = await coreDb.select().from(ledgerEntries).where(eq(ledgerEntries.orderId, o.id));
    expect(led.reduce((a, l) => a + l.amount, 0)).toBe(0);
    expect(j(await app.inject({ url: `/api/v1/orders/${o.id}`, headers: student.auth })).status).toBe('refunded');
    expect((await app.inject({ method: 'PATCH', url: `/api/v1/admin/refunds/${q.items[0].id}`, headers: admin.auth, payload: { decision: 'reject' } })).statusCode).toBe(409);
    expect((await coreDb.select().from(auditLogs).where(eq(auditLogs.action, 'refund.approve'))).length).toBe(1);
    // admin order list + search by trx
    const ao = j(await app.inject({ url: '/api/v1/admin/orders?q=Nadia', headers: admin.auth }));
    expect(ao.items[0].studentName).toBe('Nadia Rahman');
    expect(ao.items[0].gateway).toBe('fake');
  });

  it('refund blocked after watching ≥ 20%', async () => {
    const { app, course, student } = await setup();
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-ref-0002' } }));
    await payFake(app, student.auth, o.id);
    const [c] = await contentDb.select().from(courses).where(eq(courses.id, course.id));
    const { lessons } = await import('../src/database/content/schema');
    const [l] = await contentDb.select().from(lessons).where(eq(lessons.courseId, course.id)).limit(1);
    await coreDb.insert(lessonProgress).values({ studentId: student.user.id, courseId: course.id, lessonId: l!.id, watchedSeconds: Math.ceil(c!.totalDurationSec * 0.5) + 1 });
    const r = await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/refund-request`, headers: student.auth, payload: { reason: 'কোর্সটা আমার প্রত্যাশামতো হয়নি।' } });
    expect(r.statusCode).toBe(400);
    expect(j(r).error.message).toContain('20%');
  });

  it('reconcile picks up a paid-at-gateway payment whose callback never arrived', async () => {
    const { app, course, student } = await setup();
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { courseId: course.id, idempotencyKey: 'idem-rec-0001' } }));
    await app.inject({ method: 'POST', url: `/api/v1/orders/${o.id}/pay`, headers: student.auth, payload: { method: 'bkash' } });
    const [p] = await coreDb.select().from(payments).where(eq(payments.orderId, o.id));
    FakeGateway.results.set(p!.gatewayTrxId, { outcome: 'success', amount: p!.amount });
    await coreDb.update(payments).set({ createdAt: new Date(Date.now() - 20 * 60_000) }).where(eq(payments.id, p!.id));
    expect(await reconcile()).toBe(1);
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(1);
  });

  it('seller coupons, bundles and ledger with balance', async () => {
    const { app, student } = await setup();
    const rakib = await login('rakib@demo.shikkhasathi.com');
    const mine = await contentDb.select().from(courses).where(and(eq(courses.sellerId, rakib.id), eq(courses.status, 'published')));
    expect(mine.length).toBeGreaterThanOrEqual(2);
    const cp = await app.inject({ method: 'POST', url: '/api/v1/seller/coupons', headers: rakib.auth, payload: { code: 'rakib50', type: 'fixed', value: 5000, courseId: mine[0]!.id } });
    expect(cp.statusCode).toBe(201);
    expect(j(cp)).toMatchObject({ code: 'RAKIB50', scope: 'course' });
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/coupons', headers: rakib.auth, payload: { code: 'RAKIB50', type: 'fixed', value: 10 } })).statusCode).toBe(409);
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/coupons', headers: student.auth, payload: { code: 'HACK', type: 'fixed', value: 10 } })).statusCode).toBe(403);
    const off = j(await app.inject({ method: 'PATCH', url: `/api/v1/seller/coupons/${j(cp).id}`, headers: rakib.auth, payload: { active: false } }));
    expect(off.active).toBe(false);
    const sum = mine[0]!.price + mine[1]!.price;
    expect((await app.inject({ method: 'POST', url: '/api/v1/seller/bundles', headers: rakib.auth, payload: { title: 'Midterm + Final combo', courseIds: [mine[0]!.id, mine[1]!.id], price: sum } })).statusCode).toBe(400);
    const b = j(await app.inject({ method: 'POST', url: '/api/v1/seller/bundles', headers: rakib.auth, payload: { title: 'Midterm + Final combo', courseIds: [mine[0]!.id, mine[1]!.id], price: sum - 10000 } }));
    const bd = j(await app.inject({ url: `/api/v1/bundles/${b.slug}` }));
    expect(bd.courses).toHaveLength(2);
    expect(bd.compareAtPrice).toBe(sum);
    // buy bundle → both enrolled, ledger split per bundle line
    const o = j(await app.inject({ method: 'POST', url: '/api/v1/orders', headers: student.auth, payload: { bundleId: b.id, idempotencyKey: 'idem-bdl-0001' } }));
    expect(o.total).toBe(sum - 10000);
    await payFake(app, student.auth, o.id);
    expect(await coreDb.select().from(enrollments).where(eq(enrollments.studentId, student.user.id))).toHaveLength(2);
    const l = j(await app.inject({ url: '/api/v1/seller/ledger', headers: rakib.auth }));
    expect(l.items.length).toBeGreaterThanOrEqual(2);
    expect(l.balance.held).toBeGreaterThan(0);
    expect(l.items[0].balanceAfter).toBe(l.balance.held + l.balance.available + l.balance.pendingPayouts);
  });
});
