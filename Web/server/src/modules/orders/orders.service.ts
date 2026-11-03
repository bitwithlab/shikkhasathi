import { and, count, desc, eq, gt, ilike, inArray, isNull, lt, or, sql, type SQL } from 'drizzle-orm';
import { uuidv7 } from 'uuidv7';
import type { Order } from '@shikkhasathi/shared';
import { contentDb, coreDb } from '../../database/client';
import { couponRedemptions, coupons, enrollments, invoices, ledgerEntries, lessonProgress, orderItems, orders, payments, refunds, sellerProfiles, users } from '../../database/core/schema';
import { bundleItems, bundles, courses, lessons } from '../../database/content/schema';
import { AppError, badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { defineJob, enqueue } from '../../common/jobs';
import { audit } from '../../common/audit';
import { decodeCursor, toPage } from '../../common/pagination';
import { logger } from '../../common/logger';
import { activeGateway, gatewayByName, type ValidateResult } from '../../services/payments';
import { storage } from '../../services/storage';
import { renderInvoicePdf } from '../../services/invoice/pdf';
import { notify } from '../notifications/notifications.service';
import { getBusinessNumber } from '../settings/settings.service';
import { invalidateDashboard } from '../dashboard/dashboard.service';

type OrderRow = typeof orders.$inferSelect;
type CourseRow = typeof courses.$inferSelect;
const HOUR = 3_600_000;

// ---------------------------------------------------------------- pricing & coupons

interface Line {
  courseId: string | null;
  bundleId: string | null;
  sellerId: string;
  title: string;
  price: number;
  courseIds: string[];
}

async function linesFor(b: { courseId?: string; bundleId?: string }): Promise<Line[]> {
  if (b.courseId) {
    const [c] = await contentDb.select().from(courses).where(and(eq(courses.id, b.courseId), isNull(courses.deletedAt)));
    if (!c || c.status !== 'published') throw notFound('কোর্সটি এখন কেনা যাচ্ছে না।');
    return [{ courseId: c.id, bundleId: null, sellerId: c.sellerId, title: c.title, price: c.price, courseIds: [c.id] }];
  }
  if (b.bundleId) {
    const [bd] = await contentDb.select().from(bundles).where(eq(bundles.id, b.bundleId));
    if (!bd || bd.status !== 'published') throw notFound('বান্ডেলটি পাওয়া যায়নি।');
    const items = await contentDb.select({ courseId: bundleItems.courseId }).from(bundleItems).where(eq(bundleItems.bundleId, bd.id));
    return [{ courseId: null, bundleId: bd.id, sellerId: bd.sellerId, title: bd.title, price: bd.price, courseIds: items.map((i) => i.courseId) }];
  }
  throw badRequest('কোর্স বা বান্ডেল দিন।');
}

type CouponRow = typeof coupons.$inferSelect;

/** Returns discount (paisa) or throws a friendly Bangla message. */
async function applyCoupon(code: string, userId: string, lines: Line[]): Promise<{ coupon: CouponRow; discount: number }> {
  const [c] = await coreDb.select().from(coupons).where(eq(coupons.code, code.toUpperCase()));
  const now = new Date();
  if (!c || !c.active) throw badRequest('কুপনটি সঠিক নয়।');
  if (c.startsAt && c.startsAt > now) throw badRequest('কুপনটি এখনো চালু হয়নি।');
  if (c.expiresAt && c.expiresAt < now) throw badRequest('কুপনের মেয়াদ শেষ।');
  if (c.maxUses !== null && c.usedCount >= c.maxUses) throw badRequest('কুপনটি সব ব্যবহার হয়ে গেছে।');
  const [used] = await coreDb.select({ n: count() }).from(couponRedemptions).where(and(eq(couponRedemptions.couponId, c.id), eq(couponRedemptions.userId, userId)));
  if ((used?.n ?? 0) >= c.perUserLimit) throw badRequest('আপনি এই কুপন আগেই ব্যবহার করেছেন।');
  const eligible = lines.filter((l) => c.scope === 'platform' || (c.scope === 'seller' && l.sellerId === c.sellerId) || (c.scope === 'course' && l.courseId === c.courseId));
  if (!eligible.length) throw badRequest('এই কোর্সে কুপনটি প্রযোজ্য নয়।');
  const base = eligible.reduce((a, l) => a + l.price, 0);
  if (base < c.minAmount) throw badRequest(`কুপনটি কমপক্ষে ৳${c.minAmount / 100}-এর কেনাকাটায় প্রযোজ্য।`);
  const discount = c.type === 'percent' ? Math.round((base * Math.min(100, c.value)) / 100) : Math.min(base, c.value);
  return { coupon: c, discount };
}

export async function validateCoupon(userId: string, b: { code: string; courseId?: string; bundleId?: string }) {
  const lines = await linesFor(b);
  const subtotal = lines.reduce((a, l) => a + l.price, 0);
  try {
    const { discount } = await applyCoupon(b.code, userId, lines);
    return { valid: true, discount, total: subtotal - discount, message: `কুপন প্রযোজ্য — ৳${discount / 100} ছাড়` };
  } catch (e) {
    return { valid: false, discount: 0, total: subtotal, message: e instanceof AppError ? e.message : 'কুপনটি সঠিক নয়।' };
  }
}

// ---------------------------------------------------------------- read model

async function toOrder(o: OrderRow): Promise<Order> {
  const [items, inv, ref, coupon] = await Promise.all([
    coreDb.select().from(orderItems).where(eq(orderItems.orderId, o.id)),
    coreDb.select({ number: invoices.number }).from(invoices).where(eq(invoices.orderId, o.id)),
    coreDb.select().from(refunds).where(eq(refunds.orderId, o.id)),
    o.couponId ? coreDb.select({ code: coupons.code }).from(coupons).where(eq(coupons.id, o.couponId)) : [],
  ]);
  const ids = items.map((i) => i.courseId).filter((v): v is string => !!v);
  const cs = ids.length ? await contentDb.select({ id: courses.id, slug: courses.slug, coverColor: courses.coverColor }).from(courses).where(inArray(courses.id, ids)) : [];
  const first = items[0]?.courseId ? (await contentDb.select({ id: lessons.id }).from(lessons).where(eq(lessons.courseId, items[0].courseId)).orderBy(lessons.position).limit(1))[0] : undefined;
  return {
    id: o.id,
    status: o.status as Order['status'],
    items: items.map((i) => ({ id: i.id, courseId: i.courseId, bundleId: i.bundleId, title: i.title, price: i.price, discount: i.discount, coverColor: cs.find((c) => c.id === i.courseId)?.coverColor ?? null, slug: cs.find((c) => c.id === i.courseId)?.slug ?? null })),
    subtotal: o.subtotal,
    discount: o.discount,
    total: o.total,
    couponCode: coupon[0]?.code ?? null,
    paymentMethod: o.paymentMethod,
    createdAt: o.createdAt.toISOString(),
    paidAt: o.paidAt?.toISOString() ?? null,
    invoiceNumber: inv[0]?.number ?? null,
    refund: ref[0] ? { status: ref[0].status as 'requested', amount: ref[0].amount, reason: ref[0].reason } : null,
    refundEligible: o.status === 'paid' && o.total > 0 && !ref[0] && !!o.paidAt && (await refundCheck(o)).ok,
    firstLessonPath: items[0]?.courseId && first ? `/learn/${items[0].courseId}/${first.id}` : null,
  };
}

async function ownOrder(userId: string, id: string) {
  const [o] = await coreDb.select().from(orders).where(eq(orders.id, id));
  if (!o) throw notFound();
  if (o.studentId !== userId) throw forbidden();
  return o;
}

export const getOrder = async (userId: string, id: string) => toOrder(await ownOrder(userId, id));

export async function myOrders(userId: string, q: { cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(orders.studentId, userId), sql`${orders.status} <> 'pending' or ${orders.createdAt} > now() - interval '1 day'`];
  if (cur) conds.push(or(lt(orders.createdAt, new Date(String(cur[0]))), and(eq(orders.createdAt, new Date(String(cur[0]))), lt(orders.id, cur[1])))!);
  const rows = await coreDb.select().from(orders).where(and(...conds)).orderBy(desc(orders.createdAt), desc(orders.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await Promise.all(pg.items.map(toOrder)), nextCursor: pg.nextCursor };
}

// ---------------------------------------------------------------- create & pay

export async function createOrder(userId: string, b: { courseId?: string; bundleId?: string; couponCode?: string; idempotencyKey: string }) {
  const [existing] = await coreDb.select().from(orders).where(and(eq(orders.studentId, userId), eq(orders.idempotencyKey, b.idempotencyKey)));
  if (existing) return toOrder(existing);
  const lines = await linesFor(b);
  const allCourses = lines.flatMap((l) => l.courseIds);
  const owned = await coreDb.select({ courseId: enrollments.courseId }).from(enrollments).where(and(eq(enrollments.studentId, userId), inArray(enrollments.courseId, allCourses), eq(enrollments.status, 'active')));
  if (b.courseId && owned.length) throw conflict('এই কোর্সটি আপনার কাছে আগেই আছে।');
  if (b.bundleId && owned.length === allCourses.length) throw conflict('বান্ডেলের সব কোর্স আপনার কাছে আগেই আছে।');
  const sellerOfCourse = lines.flatMap((l) => l.courseIds.map(() => l.sellerId));
  if (sellerOfCourse.includes(userId)) throw badRequest('নিজের কোর্স কেনা যায় না।');
  const subtotal = lines.reduce((a, l) => a + l.price, 0);
  const applied = b.couponCode ? await applyCoupon(b.couponCode, userId, lines) : null;
  const discount = applied?.discount ?? 0;
  const total = Math.max(0, subtotal - discount);
  const [o] = await coreDb
    .insert(orders)
    .values({ studentId: userId, status: 'pending', subtotal, discount, total, couponId: applied?.coupon.id ?? null, idempotencyKey: b.idempotencyKey })
    .onConflictDoNothing({ target: [orders.studentId, orders.idempotencyKey] })
    .returning();
  if (!o) return toOrder((await coreDb.select().from(orders).where(and(eq(orders.studentId, userId), eq(orders.idempotencyKey, b.idempotencyKey))))[0]!);
  // allocate discount proportionally per line (for the seller ledger)
  let left = discount;
  for (const [i, l] of lines.entries()) {
    const share = i === lines.length - 1 ? left : Math.round((discount * l.price) / Math.max(1, subtotal));
    left -= share;
    await coreDb.insert(orderItems).values({ orderId: o.id, courseId: l.courseId, bundleId: l.bundleId, sellerId: l.sellerId, title: l.title, price: l.price, discount: share });
  }
  if (total === 0) {
    await coreDb.update(orders).set({ status: 'paid', paidAt: new Date(), paymentMethod: 'free' }).where(eq(orders.id, o.id));
    await fulfil({ orderId: o.id });
  }
  return toOrder((await coreDb.select().from(orders).where(eq(orders.id, o.id)))[0]!);
}

export async function pay(userId: string, orderId: string, method: 'bkash' | 'nagad' | 'rocket' | 'card') {
  const o = await ownOrder(userId, orderId);
  if (o.status !== 'pending' && o.status !== 'failed') throw conflict('এই অর্ডারের পেমেন্ট আগেই হয়েছে।');
  if (o.total === 0) throw badRequest('এই অর্ডারে পেমেন্ট লাগবে না।');
  const [u] = await coreDb.select({ name: users.name, email: users.email, phone: users.phone }).from(users).where(eq(users.id, userId));
  const items = await coreDb.select({ title: orderItems.title }).from(orderItems).where(eq(orderItems.orderId, orderId));
  const gw = await activeGateway();
  const trxId = `SS${Date.now().toString(36).toUpperCase()}${uuidv7().slice(-6).toUpperCase()}`;
  const r = await gw.init({ orderId, trxId, amount: o.total, method, customer: { name: u?.name ?? 'Student', email: u?.email ?? null, phone: u?.phone ?? null }, productName: items.map((i) => i.title).join(', ') });
  await coreDb.insert(payments).values({ orderId, gateway: gw.name, gatewayTrxId: trxId, gatewaySessionId: r.sessionId, method, amount: o.total, status: 'initiated' });
  await coreDb.update(orders).set({ status: 'pending', paymentMethod: method }).where(eq(orders.id, orderId));
  return { redirectUrl: r.redirectUrl, gateway: gw.name };
}

/**
 * Idempotent payment confirmation — used by the browser callback, the IPN webhook and reconciliation.
 * Server-side validation result decides; the unique (gateway, trx_id) row is the idempotency key.
 */
export async function applyValidation(gateway: string, v: ValidateResult) {
  const [p] = await coreDb.select().from(payments).where(and(eq(payments.gateway, gateway), eq(payments.gatewayTrxId, v.trxId)));
  if (!p) {
    logger.warn({ gateway, trxId: v.trxId }, 'payment for unknown trx');
    return null;
  }
  if (p.status === 'succeeded') return p.orderId; // already processed
  if (v.status === 'pending') return p.orderId;
  if (v.ok && v.amount !== p.amount) {
    logger.error({ trxId: v.trxId, expected: p.amount, got: v.amount }, 'payment amount mismatch');
    await coreDb.update(payments).set({ status: 'failed', rawPayload: v.raw as object }).where(eq(payments.id, p.id));
    await coreDb.update(orders).set({ status: 'failed' }).where(and(eq(orders.id, p.orderId), eq(orders.status, 'pending')));
    return p.orderId;
  }
  if (!v.ok) {
    await coreDb.update(payments).set({ status: 'failed', rawPayload: v.raw as object, validatedAt: new Date() }).where(and(eq(payments.id, p.id), sql`${payments.status} <> 'succeeded'`));
    await coreDb.update(orders).set({ status: 'failed' }).where(and(eq(orders.id, p.orderId), eq(orders.status, 'pending')));
    return p.orderId;
  }
  // succeeded: flip payment + order atomically, once
  const flipped = await coreDb.transaction(async (tx) => {
    const [up] = await tx.update(payments).set({ status: 'succeeded', rawPayload: { ...(v.raw as object), gatewayRef: v.gatewayRef }, validatedAt: new Date() }).where(and(eq(payments.id, p.id), sql`${payments.status} <> 'succeeded'`)).returning({ id: payments.id });
    if (!up) return false;
    await tx.update(orders).set({ status: 'paid', paidAt: new Date() }).where(and(eq(orders.id, p.orderId), inArray(orders.status, ['pending', 'failed'])));
    return true;
  });
  if (flipped) await audit({ actorId: null, action: 'payment.succeeded', entity: 'payments', entityId: p.id, after: { orderId: p.orderId, gateway, trxId: v.trxId, amount: v.amount } });
  await enqueue('payments.fulfil', { orderId: p.orderId }, { jobId: `fulfil-${p.orderId}` });
  return p.orderId;
}

export async function handleCallback(gateway: string, params: Record<string, string>) {
  try {
    const gw = await gatewayByName(gateway);
    const v = await gw.validate(params);
    const orderId = await applyValidation(gateway, v);
    if (!orderId) return '/checkout/result?status=failed';
    const [o] = await coreDb.select({ status: orders.status }).from(orders).where(eq(orders.id, orderId));
    const st = o?.status === 'paid' ? 'paid' : params.status === 'cancel' ? 'cancelled' : v.status === 'pending' ? 'pending' : 'failed';
    return `/checkout/result?order=${orderId}&status=${st}`;
  } catch (err) {
    logger.error({ err: (err as Error).message, gateway }, 'payment callback failed');
    return '/checkout/result?status=failed';
  }
}

export async function handleIpn(gateway: string, params: Record<string, string>) {
  const gw = await gatewayByName(gateway);
  await applyValidation(gateway, await gw.validate(params));
}

/** payments.reconcile (hourly): re-validate payments stuck in "initiated" for > 10 minutes. */
export async function reconcile() {
  const stuck = await coreDb.select().from(payments).where(and(eq(payments.status, 'initiated'), lt(payments.createdAt, new Date(Date.now() - 10 * 60_000)), gt(payments.createdAt, new Date(Date.now() - 3 * 24 * HOUR)))).limit(200);
  let fixed = 0;
  for (const p of stuck) {
    try {
      const gw = await gatewayByName(p.gateway);
      const v = await gw.query(p.gatewayTrxId, p.gatewaySessionId);
      if (v.status !== 'pending') {
        await applyValidation(p.gateway, { ...v, trxId: p.gatewayTrxId });
        fixed++;
      } else if (p.createdAt < new Date(Date.now() - 24 * HOUR)) {
        await coreDb.update(payments).set({ status: 'failed' }).where(eq(payments.id, p.id));
      }
    } catch (err) {
      logger.warn({ err: (err as Error).message, trx: p.gatewayTrxId }, 'reconcile failed');
    }
  }
  // fulfil any paid orders whose fulfilment didn't complete (cross-DB outbox)
  const unfulfilled = await coreDb.select({ id: orders.id }).from(orders).where(and(eq(orders.status, 'paid'), isNull(orders.fulfilledAt))).limit(200);
  for (const o of unfulfilled) await enqueue('payments.fulfil', { orderId: o.id }, { jobId: `fulfil-${o.id}` });
  return fixed;
}
defineJob('payments.reconcile', () => reconcile());

// ---------------------------------------------------------------- fulfilment (enroll → ledger → invoice → email)

async function ledgerAppend(sellerId: string, e: { type: string; amount: number; orderId?: string; orderItemId?: string; payoutId?: string; refundId?: string; description: string; availableAt: Date; dedupeKey: string }) {
  await coreDb.transaction(async (tx) => {
    // serialize per seller so balance_after is consistent
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${sellerId}))`);
    const [dup] = await tx.select({ id: ledgerEntries.id }).from(ledgerEntries).where(eq(ledgerEntries.dedupeKey, e.dedupeKey));
    if (dup) return;
    const [last] = await tx.select({ b: ledgerEntries.balanceAfter }).from(ledgerEntries).where(eq(ledgerEntries.sellerId, sellerId)).orderBy(desc(ledgerEntries.createdAt), desc(ledgerEntries.id)).limit(1);
    await tx.insert(ledgerEntries).values({ sellerId, type: e.type, amount: e.amount, orderId: e.orderId ?? null, orderItemId: e.orderItemId ?? null, payoutId: e.payoutId ?? null, refundId: e.refundId ?? null, description: e.description, availableAt: e.availableAt, balanceAfter: (last?.b ?? 0) + e.amount, dedupeKey: e.dedupeKey });
  });
}
export { ledgerAppend };

async function nextInvoiceNumber() {
  const year = new Date().getFullYear();
  const [r] = await coreDb.select({ n: count() }).from(invoices).where(ilike(invoices.number, `SS-${year}-%`));
  return `SS-${year}-${String((r?.n ?? 0) + 1).padStart(6, '0')}`;
}

/** Idempotent: safe to run any number of times (unique keys on enrollments, ledger dedupe, invoice per order). */
export async function fulfil({ orderId }: { orderId: string }) {
  const [o] = await coreDb.select().from(orders).where(eq(orders.id, orderId));
  if (!o || o.status !== 'paid' || o.fulfilledAt) return;
  const items = await coreDb.select().from(orderItems).where(eq(orderItems.orderId, orderId));
  const holdDays = await getBusinessNumber('ledger_hold_days', 7);
  const defaultCommission = await getBusinessNumber('commission_percent', 20);
  const courseRows: CourseRow[] = [];
  for (const it of items) {
    const courseIds = it.courseId ? [it.courseId] : (await contentDb.select({ courseId: bundleItems.courseId }).from(bundleItems).where(eq(bundleItems.bundleId, it.bundleId!))).map((b) => b.courseId);
    const cs = await contentDb.select().from(courses).where(inArray(courses.id, courseIds));
    courseRows.push(...cs);
    for (const c of cs) {
      const accessUntil = c.accessType === 'days' && c.accessDays ? new Date(Date.now() + c.accessDays * 86_400_000) : c.accessType === 'until_exam' ? c.accessUntil : null;
      const [ins] = await coreDb.insert(enrollments).values({ studentId: o.studentId, courseId: c.id, orderId, bundleId: it.bundleId, accessUntil, status: 'active' }).onConflictDoNothing().returning({ id: enrollments.id });
      if (ins) {
        await contentDb.update(courses).set({ enrollmentCount: sql`${courses.enrollmentCount} + 1`, enrollments30d: sql`${courses.enrollments30d} + 1` }).where(eq(courses.id, c.id));
        await notify({ userId: c.sellerId, type: 'seller.new_enrollment', ref: `${orderId}:${c.id}`, title: 'নতুন শিক্ষার্থী এনরোল করেছেন', body: c.title, link: '/teach/students' });
      }
    }
    const net = it.price - it.discount;
    if (net > 0) {
      const [sp] = await coreDb.select({ rate: sellerProfiles.commissionRate }).from(sellerProfiles).where(eq(sellerProfiles.userId, it.sellerId));
      const rate = sp?.rate ?? defaultCommission;
      const availableAt = new Date(Date.now() + holdDays * 86_400_000);
      await ledgerAppend(it.sellerId, { type: 'sale', amount: net, orderId, orderItemId: it.id, description: it.title, availableAt, dedupeKey: `sale:${it.id}` });
      await ledgerAppend(it.sellerId, { type: 'commission', amount: -Math.round((net * rate) / 100), orderId, orderItemId: it.id, description: `${rate}% platform commission`, availableAt, dedupeKey: `commission:${it.id}` });
    }
  }
  if (o.couponId) {
    const [r] = await coreDb.insert(couponRedemptions).values({ couponId: o.couponId, userId: o.studentId, orderId }).onConflictDoNothing().returning({ id: couponRedemptions.id });
    if (r) await coreDb.update(coupons).set({ usedCount: sql`${coupons.usedCount} + 1` }).where(eq(coupons.id, o.couponId));
  }
  if (o.total > 0) await generateInvoice(o, items);
  await coreDb.update(orders).set({ fulfilledAt: new Date() }).where(and(eq(orders.id, orderId), isNull(orders.fulfilledAt)));
  await invalidateDashboard(o.studentId);
  if (o.total > 0) {
    const [inv] = await coreDb.select().from(invoices).where(eq(invoices.orderId, orderId));
    await notify({
      userId: o.studentId,
      type: 'order.paid',
      ref: orderId,
      title: 'পেমেন্ট সফল — কোর্স আনলক হয়েছে',
      body: items.map((i) => i.title).join(', '),
      link: '/my-courses',
      email: { template: 'order_paid', data: { items: items.map((i) => i.title).join(', '), total: o.total, invoiceNumber: inv?.number ?? '', invoiceUrl: inv?.storageKey ? await storage().signedDownloadUrl('invoices', inv.storageKey, 7 * 86_400, `${inv.number}.pdf`) : '' } },
    });
  }
}
defineJob<{ orderId: string }>('payments.fulfil', fulfil);

async function generateInvoice(o: OrderRow, items: (typeof orderItems.$inferSelect)[]) {
  const [exists] = await coreDb.select().from(invoices).where(eq(invoices.orderId, o.id));
  if (exists?.storageKey) return exists;
  const [u] = await coreDb.select().from(users).where(eq(users.id, o.studentId));
  const [p] = await coreDb.select().from(payments).where(and(eq(payments.orderId, o.id), eq(payments.status, 'succeeded')));
  const vatPct = await getBusinessNumber('vat_percent', 0);
  const vat = vatPct ? Math.round((o.total * vatPct) / (100 + vatPct)) : 0; // VAT-inclusive prices
  let inv = exists;
  for (let attempt = 0; !inv && attempt < 5; attempt++) {
    const number = await nextInvoiceNumber();
    [inv] = await coreDb
      .insert(invoices)
      .values({ orderId: o.id, userId: o.studentId, number, billingName: u?.name ?? 'Student', billingPhone: u?.phone ?? null, billingEmail: u?.email ?? null, itemsJson: items.map((i) => ({ title: i.title, price: i.price, discount: i.discount })), subtotal: o.subtotal, discount: o.discount, vat, total: o.total })
      .onConflictDoNothing()
      .returning();
    if (!inv) [inv] = await coreDb.select().from(invoices).where(eq(invoices.orderId, o.id));
  }
  if (!inv) throw new Error('invoice number allocation failed');
  const pdf = await renderInvoicePdf({ number: inv.number, date: o.paidAt ?? new Date(), billingName: inv.billingName, billingPhone: inv.billingPhone, billingEmail: inv.billingEmail, orderId: o.id, items: items.map((i) => ({ title: i.title, price: i.price, discount: i.discount })), subtotal: o.subtotal, discount: o.discount, vat, total: o.total, paymentMethod: o.paymentMethod, gatewayRef: (p?.rawPayload as { gatewayRef?: string } | null)?.gatewayRef ?? p?.gatewayTrxId ?? null });
  const key = `${o.studentId}/${inv.number}.pdf`;
  await storage().put('invoices', key, pdf, 'application/pdf');
  await coreDb.update(invoices).set({ storageKey: key, emailedAt: new Date() }).where(eq(invoices.id, inv.id));
  return inv;
}

export async function invoiceUrl(userId: string, orderId: string, isAdmin = false) {
  const [o] = await coreDb.select().from(orders).where(eq(orders.id, orderId));
  if (!o) throw notFound();
  if (o.studentId !== userId && !isAdmin) throw forbidden();
  const [inv] = await coreDb.select().from(invoices).where(eq(invoices.orderId, orderId));
  if (!inv?.storageKey) throw notFound('ইনভয়েস এখনো তৈরি হয়নি।');
  return { url: await storage().signedDownloadUrl('invoices', inv.storageKey, 600, `${inv.number}.pdf`), number: inv.number };
}

// ---------------------------------------------------------------- refunds

async function watchedPercent(studentId: string, courseIds: string[]) {
  if (!courseIds.length) return 0;
  const [tot] = await contentDb.select({ s: sql<number>`coalesce(sum(${courses.totalDurationSec}),0)::int` }).from(courses).where(inArray(courses.id, courseIds));
  const [w] = await coreDb.select({ s: sql<number>`coalesce(sum(least(${lessonProgress.watchedSeconds}, 100000)),0)::int` }).from(lessonProgress).where(and(eq(lessonProgress.studentId, studentId), inArray(lessonProgress.courseId, courseIds)));
  return tot?.s ? Math.round(((w?.s ?? 0) / tot.s) * 100) : 0;
}

async function orderCourseIds(orderId: string) {
  return (await coreDb.select({ courseId: enrollments.courseId }).from(enrollments).where(eq(enrollments.orderId, orderId))).map((e) => e.courseId);
}

async function refundCheck(o: OrderRow) {
  const windowH = await getBusinessNumber('refund_window_hours', 48);
  const maxPct = await getBusinessNumber('refund_max_watch_percent', 20);
  const hours = o.paidAt ? (Date.now() - o.paidAt.getTime()) / HOUR : Infinity;
  if (hours > windowH) return { ok: false, reason: `কেনার ${windowH} ঘণ্টা পেরিয়ে গেছে।`, pct: 0 };
  const pct = await watchedPercent(o.studentId, await orderCourseIds(o.id));
  if (pct >= maxPct) return { ok: false, reason: `কোর্সের ${maxPct}%-এর বেশি দেখা হয়ে গেছে।`, pct };
  return { ok: true, reason: '', pct };
}

export async function requestRefund(userId: string, orderId: string, reason: string) {
  const o = await ownOrder(userId, orderId);
  if (o.status !== 'paid' || o.total === 0) throw badRequest('এই অর্ডারে রিফান্ড প্রযোজ্য নয়।');
  const chk = await refundCheck(o);
  if (!chk.ok) throw badRequest(`রিফান্ড দেওয়া যাচ্ছে না — ${chk.reason}`);
  const [r] = await coreDb.insert(refunds).values({ orderId, requestedBy: userId, reason, amount: o.total }).onConflictDoNothing().returning();
  if (!r) throw conflict('এই অর্ডারের রিফান্ড অনুরোধ আগেই করা হয়েছে।');
  await audit({ actorId: userId, action: 'refund.request', entity: 'refunds', entityId: r.id, after: { orderId, amount: o.total } });
  return toOrder(o);
}

async function adminRefundItem(r: typeof refunds.$inferSelect) {
  const [[o], [u]] = await Promise.all([coreDb.select().from(orders).where(eq(orders.id, r.orderId)), coreDb.select({ name: users.name }).from(users).where(eq(users.id, r.requestedBy))]);
  const items = await coreDb.select({ title: orderItems.title }).from(orderItems).where(eq(orderItems.orderId, r.orderId));
  return {
    id: r.id,
    orderId: r.orderId,
    studentName: u?.name ?? '',
    amount: r.amount,
    reason: r.reason,
    status: r.status as 'requested',
    createdAt: r.createdAt.toISOString(),
    watchedPercent: o ? await watchedPercent(o.studentId, await orderCourseIds(o.id)) : 0,
    hoursSincePurchase: o?.paidAt ? Math.round((r.createdAt.getTime() - o.paidAt.getTime()) / HOUR) : 0,
    items: items.map((i) => i.title),
    note: r.note,
  };
}

export async function adminRefunds(q: { status: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(refunds.status, q.status), isNull(refunds.deletedAt)];
  if (cur) conds.push(lt(refunds.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(refunds).where(and(...conds)).orderBy(desc(refunds.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return { items: await Promise.all(pg.items.map(adminRefundItem)), nextCursor: pg.nextCursor };
}

export async function decideRefund(actorId: string, id: string, d: { decision: 'approve' | 'reject'; note?: string; manualReference?: string }) {
  const [r] = await coreDb.select().from(refunds).where(eq(refunds.id, id));
  if (!r) throw notFound();
  if (r.status !== 'requested') throw conflict('এই রিফান্ডের সিদ্ধান্ত আগেই হয়েছে।');
  const [o] = await coreDb.select().from(orders).where(eq(orders.id, r.orderId));
  if (!o) throw notFound();
  if (d.decision === 'reject') {
    await coreDb.update(refunds).set({ status: 'rejected', processedBy: actorId, processedAt: new Date(), note: d.note ?? null }).where(eq(refunds.id, id));
    await audit({ actorId, action: 'refund.reject', entity: 'refunds', entityId: id, after: { note: d.note } });
    await notify({ userId: o.studentId, type: 'order.refunded', ref: `rejected:${id}`, title: 'রিফান্ড অনুরোধ গ্রহণ হয়নি', body: d.note ?? '', link: '/profile#orders', email: { template: 'refund', data: { status: 'rejected', orderId: o.id, note: d.note ?? '' } } });
    return adminRefundItem((await coreDb.select().from(refunds).where(eq(refunds.id, id)))[0]!);
  }
  // approve: gateway refund where supported, else manual with reference
  const [p] = await coreDb.select().from(payments).where(and(eq(payments.orderId, o.id), eq(payments.status, 'succeeded')));
  let gatewayRefundId: string | null = d.manualReference ?? null;
  if (p && !d.manualReference) {
    const gw = await gatewayByName(p.gateway);
    const res = await gw.refund({ trxId: p.gatewayTrxId, gatewayRef: (p.rawPayload as { gatewayRef?: string } | null)?.gatewayRef ?? null, amount: r.amount, reason: r.reason });
    if (!res.supported) throw badRequest('এই গেটওয়েতে স্বয়ংক্রিয় রিফান্ড নেই — ম্যানুয়ালি টাকা ফেরত দিয়ে রেফারেন্স লিখুন।');
    gatewayRefundId = res.refundId;
  }
  await coreDb.update(refunds).set({ status: 'processed', processedBy: actorId, processedAt: new Date(), gatewayRefundId, note: d.note ?? null }).where(eq(refunds.id, id));
  await coreDb.update(orders).set({ status: 'refunded' }).where(eq(orders.id, o.id));
  await coreDb.update(payments).set({ status: 'refunded' }).where(and(eq(payments.orderId, o.id), eq(payments.status, 'succeeded')));
  const enr = await coreDb.update(enrollments).set({ status: 'refunded' }).where(eq(enrollments.orderId, o.id)).returning({ courseId: enrollments.courseId });
  if (enr.length) await contentDb.update(courses).set({ enrollmentCount: sql`greatest(${courses.enrollmentCount} - 1, 0)` }).where(inArray(courses.id, enr.map((e) => e.courseId)));
  // ledger reversal: undo sale and commission per item
  const entries = await coreDb.select().from(ledgerEntries).where(and(eq(ledgerEntries.orderId, o.id), inArray(ledgerEntries.type, ['sale', 'commission'])));
  for (const e of entries) await ledgerAppend(e.sellerId, { type: 'refund', amount: -e.amount, orderId: o.id, orderItemId: e.orderItemId ?? undefined, refundId: id, description: `Refund reversal (${e.type})`, availableAt: new Date(), dedupeKey: `refund:${id}:${e.id}` });
  await audit({ actorId, action: 'refund.approve', entity: 'refunds', entityId: id, before: { status: 'requested' }, after: { status: 'processed', amount: r.amount, gatewayRefundId } });
  await invalidateDashboard(o.studentId);
  await notify({ userId: o.studentId, type: 'order.refunded', ref: id, title: 'রিফান্ড অনুমোদিত হয়েছে', body: `৳${r.amount / 100} ফেরত যাচ্ছে (৩–৭ কার্যদিবস)`, link: '/profile#orders', email: { template: 'refund', data: { status: 'approved', amount: r.amount, orderId: o.id } } });
  return adminRefundItem((await coreDb.select().from(refunds).where(eq(refunds.id, id)))[0]!);
}

// ---------------------------------------------------------------- admin orders

export async function adminOrders(q: { status?: string; q?: string; cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [];
  if (q.status) conds.push(eq(orders.status, q.status));
  if (cur) conds.push(lt(orders.createdAt, new Date(String(cur[0]))));
  if (q.q) {
    const ids = (await coreDb.select({ id: users.id }).from(users).where(or(ilike(users.name, `%${q.q}%`), ilike(users.phone, `%${q.q}%`), ilike(users.email, `%${q.q}%`))).limit(50)).map((u) => u.id);
    const byTrx = (await coreDb.select({ id: payments.orderId }).from(payments).where(ilike(payments.gatewayTrxId, `%${q.q}%`)).limit(50)).map((p) => p.id);
    conds.push(or(ids.length ? inArray(orders.studentId, ids) : sql`false`, byTrx.length ? inArray(orders.id, byTrx) : sql`false`)!);
  }
  const rows = await coreDb.select().from(orders).where(conds.length ? and(...conds) : undefined).orderBy(desc(orders.createdAt)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  const studentIds = [...new Set(pg.items.map((o) => o.studentId))];
  const [people, pays] = await Promise.all([
    studentIds.length ? coreDb.select({ id: users.id, name: users.name, phone: users.phone }).from(users).where(inArray(users.id, studentIds)) : [],
    pg.items.length ? coreDb.select().from(payments).where(inArray(payments.orderId, pg.items.map((o) => o.id))).orderBy(desc(payments.createdAt)) : [],
  ]);
  return {
    items: await Promise.all(
      pg.items.map(async (o) => {
        const pay = pays.find((p) => p.orderId === o.id);
        const u = people.find((p) => p.id === o.studentId);
        return { ...(await toOrder(o)), studentName: u?.name ?? '', studentPhone: u?.phone ?? null, gateway: pay?.gateway ?? null, gatewayTrxId: pay?.gatewayTrxId ?? null };
      }),
    ),
    nextCursor: pg.nextCursor,
  };
}

