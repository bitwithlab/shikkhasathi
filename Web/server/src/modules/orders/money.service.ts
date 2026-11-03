import { and, desc, eq, inArray, isNull, lt, sql, type SQL } from 'drizzle-orm';
import { contentDb, coreDb } from '../../database/client';
import { coupons, ledgerEntries, payouts } from '../../database/core/schema';
import { bundleItems, bundles, courses } from '../../database/content/schema';
import { badRequest, conflict, forbidden, notFound } from '../../common/errors';
import { audit } from '../../common/audit';
import { decodeCursor, toPage } from '../../common/pagination';
import { getBusinessNumber } from '../settings/settings.service';

type CouponRow = typeof coupons.$inferSelect;
const toCoupon = (c: CouponRow) => ({
  id: c.id,
  code: c.code,
  scope: c.scope as 'seller',
  courseId: c.courseId,
  type: c.type as 'percent',
  value: c.value,
  maxUses: c.maxUses,
  usedCount: c.usedCount,
  perUserLimit: c.perUserLimit,
  minAmount: c.minAmount,
  startsAt: c.startsAt?.toISOString() ?? null,
  expiresAt: c.expiresAt?.toISOString() ?? null,
  active: !!c.active,
});

interface CouponInput {
  code?: string;
  courseId?: string | null;
  type?: 'percent' | 'fixed';
  value?: number;
  maxUses?: number | null;
  perUserLimit?: number;
  minAmount?: number;
  startsAt?: string | null;
  expiresAt?: string | null;
  active?: boolean;
}

async function checkCouponInput(sellerId: string, d: CouponInput) {
  if (d.type === 'percent' && d.value !== undefined && (d.value < 1 || d.value > 90)) throw badRequest('শতাংশ ছাড় ১–৯০ এর মধ্যে দিন।');
  if (d.courseId) {
    const [c] = await contentDb.select({ sellerId: courses.sellerId }).from(courses).where(eq(courses.id, d.courseId));
    if (!c || c.sellerId !== sellerId) throw forbidden('শুধু নিজের কোর্সে কুপন দেওয়া যায়।');
  }
  if (d.startsAt && d.expiresAt && new Date(d.expiresAt) <= new Date(d.startsAt)) throw badRequest('মেয়াদ শেষের তারিখ শুরুর পরে হতে হবে।');
}

export async function listCoupons(sellerId: string) {
  const rows = await coreDb.select().from(coupons).where(eq(coupons.sellerId, sellerId)).orderBy(desc(coupons.createdAt));
  return { items: rows.map(toCoupon) };
}

export async function createCoupon(sellerId: string, d: CouponInput & { code: string; type: 'percent' | 'fixed'; value: number }) {
  await checkCouponInput(sellerId, d);
  const [row] = await coreDb
    .insert(coupons)
    .values({ code: d.code, scope: d.courseId ? 'course' : 'seller', sellerId, courseId: d.courseId ?? null, type: d.type, value: d.value, maxUses: d.maxUses ?? null, perUserLimit: d.perUserLimit ?? 1, minAmount: d.minAmount ?? 0, startsAt: d.startsAt ? new Date(d.startsAt) : null, expiresAt: d.expiresAt ? new Date(d.expiresAt) : null, active: d.active === false ? 0 : 1, createdBy: sellerId })
    .onConflictDoNothing()
    .returning();
  if (!row) throw conflict('এই কোডের কুপন আগে থেকেই আছে — অন্য কোড দিন।');
  await audit({ actorId: sellerId, action: 'coupon.create', entity: 'coupons', entityId: row.id, after: toCoupon(row) });
  return toCoupon(row);
}

export async function updateCoupon(sellerId: string, id: string, d: CouponInput) {
  const [c] = await coreDb.select().from(coupons).where(eq(coupons.id, id));
  if (!c) throw notFound();
  if (c.sellerId !== sellerId) throw forbidden();
  await checkCouponInput(sellerId, { ...d, type: d.type ?? (c.type as 'percent'), value: d.value ?? c.value });
  const patch: Partial<typeof coupons.$inferInsert> = {};
  if (d.code !== undefined) patch.code = d.code;
  if (d.courseId !== undefined) {
    patch.courseId = d.courseId;
    patch.scope = d.courseId ? 'course' : 'seller';
  }
  if (d.type !== undefined) patch.type = d.type;
  if (d.value !== undefined) patch.value = d.value;
  if (d.maxUses !== undefined) patch.maxUses = d.maxUses;
  if (d.perUserLimit !== undefined) patch.perUserLimit = d.perUserLimit;
  if (d.minAmount !== undefined) patch.minAmount = d.minAmount;
  if (d.startsAt !== undefined) patch.startsAt = d.startsAt ? new Date(d.startsAt) : null;
  if (d.expiresAt !== undefined) patch.expiresAt = d.expiresAt ? new Date(d.expiresAt) : null;
  if (d.active !== undefined) patch.active = d.active ? 1 : 0;
  const [row] = await coreDb.update(coupons).set(patch).where(eq(coupons.id, id)).returning();
  await audit({ actorId: sellerId, action: 'coupon.update', entity: 'coupons', entityId: id, before: toCoupon(c), after: toCoupon(row!) });
  return toCoupon(row!);
}

// ---------------------------------------------------------------- ledger & balance

export async function balance(sellerId: string) {
  const now = new Date().toISOString();
  const [r] = await coreDb
    .select({
      available: sql<number>`coalesce(sum(${ledgerEntries.amount}) filter (where ${ledgerEntries.availableAt} <= ${now}::timestamptz), 0)::int`,
      held: sql<number>`coalesce(sum(${ledgerEntries.amount}) filter (where ${ledgerEntries.availableAt} > ${now}::timestamptz), 0)::int`,
      lifetime: sql<number>`coalesce(sum(${ledgerEntries.amount}) filter (where ${ledgerEntries.type} in ('sale','commission','refund','adjustment')), 0)::int`,
      paidOut: sql<number>`coalesce(-sum(${ledgerEntries.amount}) filter (where ${ledgerEntries.type} = 'payout'), 0)::int`,
    })
    .from(ledgerEntries)
    .where(eq(ledgerEntries.sellerId, sellerId));
  const [p] = await coreDb.select({ s: sql<number>`coalesce(sum(${payouts.amount}),0)::int` }).from(payouts).where(and(eq(payouts.sellerId, sellerId), inArray(payouts.status, ['requested', 'approved']), isNull(payouts.deletedAt)));
  const pending = p?.s ?? 0;
  return { available: Math.max(0, (r?.available ?? 0) - pending), held: r?.held ?? 0, lifetime: r?.lifetime ?? 0, paidOut: r?.paidOut ?? 0, pendingPayouts: pending, minPayout: (await getBusinessNumber('min_payout_bdt', 500)) * 100 };
}

export async function ledger(sellerId: string, q: { cursor?: string; limit: number }) {
  const cur = decodeCursor(q.cursor);
  const conds: SQL[] = [eq(ledgerEntries.sellerId, sellerId)];
  if (cur) conds.push(lt(ledgerEntries.createdAt, new Date(String(cur[0]))));
  const rows = await coreDb.select().from(ledgerEntries).where(and(...conds)).orderBy(desc(ledgerEntries.createdAt), desc(ledgerEntries.id)).limit(q.limit + 1);
  const pg = toPage(rows, q.limit, (r) => [r.createdAt.toISOString(), r.id]);
  return {
    items: pg.items.map((e) => ({ id: e.id, type: e.type as 'sale', amount: e.amount, description: e.description, availableAt: e.availableAt.toISOString(), balanceAfter: e.balanceAfter, createdAt: e.createdAt.toISOString() })),
    nextCursor: pg.nextCursor,
    balance: await balance(sellerId),
  };
}

// ---------------------------------------------------------------- bundles

const slugify = (s: string) =>
  s
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 50);

export async function createBundle(sellerId: string, d: { title: string; description?: string; courseIds: string[]; price: number }) {
  const ids = [...new Set(d.courseIds)];
  if (ids.length < 2) throw badRequest('বান্ডেলে অন্তত ২টি কোর্স দিন।');
  const cs = await contentDb.select().from(courses).where(and(inArray(courses.id, ids), isNull(courses.deletedAt)));
  if (cs.length !== ids.length || cs.some((c) => c.sellerId !== sellerId)) throw forbidden('শুধু নিজের কোর্স দিয়ে বান্ডেল বানানো যায়।');
  if (cs.some((c) => c.status !== 'published')) throw badRequest('বান্ডেলের সব কোর্স প্রকাশিত হতে হবে।');
  const compare = cs.reduce((a, c) => a + c.price, 0);
  if (d.price >= compare) throw badRequest('বান্ডেলের দাম আলাদা কোর্সের মোট দামের চেয়ে কম হতে হবে।');
  const slug = `${slugify(d.title) || 'bundle'}-${Math.random().toString(36).slice(2, 7)}`;
  const id = await contentDb.transaction(async (tx) => {
    const [b] = await tx.insert(bundles).values({ sellerId, title: d.title, slug, description: d.description ?? null, price: d.price, compareAtPrice: compare, status: 'published' }).returning({ id: bundles.id });
    await tx.insert(bundleItems).values(ids.map((courseId) => ({ bundleId: b!.id, courseId })));
    return b!.id;
  });
  await audit({ actorId: sellerId, action: 'bundle.create', entity: 'bundles', entityId: id, after: { courseIds: ids, price: d.price } });
  return { id, slug };
}

export async function listBundles(sellerId: string) {
  const bs = await contentDb.select().from(bundles).where(eq(bundles.sellerId, sellerId)).orderBy(desc(bundles.createdAt));
  const counts = bs.length ? await contentDb.select({ id: bundleItems.bundleId, n: sql<number>`count(*)::int` }).from(bundleItems).where(inArray(bundleItems.bundleId, bs.map((b) => b.id))).groupBy(bundleItems.bundleId) : [];
  return { items: bs.map((b) => ({ id: b.id, slug: b.slug, title: b.title, price: b.price, compareAtPrice: b.compareAtPrice, courseCount: counts.find((c) => c.id === b.id)?.n ?? 0 })) };
}

export async function getBundle(slug: string) {
  const [b] = await contentDb.select().from(bundles).where(and(eq(bundles.slug, slug), eq(bundles.status, 'published')));
  if (!b) throw notFound('বান্ডেলটি পাওয়া যায়নি।');
  const items = await contentDb.select({ id: courses.id, slug: courses.slug, title: courses.title, price: courses.price }).from(bundleItems).innerJoin(courses, eq(courses.id, bundleItems.courseId)).where(eq(bundleItems.bundleId, b.id));
  return { id: b.id, slug: b.slug, title: b.title, description: b.description, price: b.price, compareAtPrice: b.compareAtPrice, courses: items };
}

