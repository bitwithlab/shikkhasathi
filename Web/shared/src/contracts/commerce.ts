import { z } from 'zod';
import { CursorQuery, IdParams, endpoint, page } from './common';
import { COUPON_SCOPES, COUPON_TYPES, LEDGER_TYPES, ORDER_STATUSES, PAYMENT_METHODS, REFUND_STATUSES } from '../types';

export const OrderItem = z.object({ id: z.uuid(), courseId: z.uuid().nullable(), bundleId: z.uuid().nullable(), title: z.string(), price: z.number().int(), discount: z.number().int(), coverColor: z.string().nullable(), slug: z.string().nullable() });
export const Order = z.object({
  id: z.uuid(),
  status: z.enum(ORDER_STATUSES),
  items: z.array(OrderItem),
  subtotal: z.number().int(),
  discount: z.number().int(),
  total: z.number().int(),
  couponCode: z.string().nullable(),
  paymentMethod: z.string().nullable(),
  createdAt: z.string(),
  paidAt: z.string().nullable(),
  invoiceNumber: z.string().nullable(),
  refund: z.object({ status: z.enum(REFUND_STATUSES), amount: z.number().int(), reason: z.string() }).nullable(),
  refundEligible: z.boolean(),
  firstLessonPath: z.string().nullable(),
});
export type Order = z.infer<typeof Order>;

export const CreateOrderBody = z.object({
  courseId: z.uuid().optional(),
  bundleId: z.uuid().optional(),
  couponCode: z.string().trim().toUpperCase().max(40).optional(),
  /** client-generated (uuid) so double-clicks/retries return the same order */
  idempotencyKey: z.string().min(8).max(64),
});

export const PayBody = z.object({ method: z.enum(PAYMENT_METHODS), phone: z.string().max(20).optional() });
export const PayResponse = z.object({ redirectUrl: z.string(), gateway: z.string() });

export const CouponValidateBody = z.object({ code: z.string().trim().toUpperCase().min(3).max(40), courseId: z.uuid().optional(), bundleId: z.uuid().optional() });
export const CouponValidateResponse = z.object({ valid: z.boolean(), discount: z.number().int(), total: z.number().int(), message: z.string() });

export const commerce = {
  createOrder: endpoint({ method: 'POST', path: '/orders', summary: 'Create a pending order (server-priced, coupon applied); free courses enroll immediately', tag: 'orders', auth: 'user', body: CreateOrderBody, response: Order, status: 201 }),
  getOrder: endpoint({ method: 'GET', path: '/orders/:id', summary: 'Order status', tag: 'orders', auth: 'user', params: IdParams, response: Order }),
  pay: endpoint({ method: 'POST', path: '/orders/:id/pay', summary: 'Start payment at the gateway (bKash / Nagad / Rocket / card)', tag: 'orders', auth: 'user', params: IdParams, body: PayBody, response: PayResponse }),
  myOrders: endpoint({ method: 'GET', path: '/me/orders', summary: 'Payment history', tag: 'orders', auth: 'user', query: CursorQuery, response: page(Order) }),
  invoice: endpoint({ method: 'GET', path: '/orders/:id/invoice', summary: 'Signed invoice PDF URL', tag: 'orders', auth: 'user', params: IdParams, response: z.object({ url: z.string(), number: z.string() }) }),
  refundRequest: endpoint({ method: 'POST', path: '/orders/:id/refund-request', summary: 'Request a refund (≤ 48 h, < 20% watched)', tag: 'orders', auth: 'user', params: IdParams, body: z.object({ reason: z.string().trim().min(10).max(1000) }), response: Order }),
  validateCoupon: endpoint({ method: 'POST', path: '/coupons/validate', summary: 'Check a coupon for a course/bundle', tag: 'orders', auth: 'user', body: CouponValidateBody, response: CouponValidateResponse }),
  bundle: endpoint({ method: 'GET', path: '/bundles/:slug', summary: 'Bundle (Midterm + Final …)', tag: 'courses', auth: 'optional', params: z.object({ slug: z.string() }), response: z.object({ id: z.uuid(), slug: z.string(), title: z.string(), description: z.string().nullable(), price: z.number().int(), compareAtPrice: z.number().int().nullable(), courses: z.array(z.object({ id: z.uuid(), slug: z.string(), title: z.string(), price: z.number().int() })) }) }),
};

// ---------------------------------------------------------------- seller money

export const Coupon = z.object({ id: z.uuid(), code: z.string(), scope: z.enum(COUPON_SCOPES), courseId: z.uuid().nullable(), type: z.enum(COUPON_TYPES), value: z.number().int(), maxUses: z.number().int().nullable(), usedCount: z.number().int(), perUserLimit: z.number().int(), minAmount: z.number().int(), startsAt: z.string().nullable(), expiresAt: z.string().nullable(), active: z.boolean() });
export const CouponUpsert = z.object({
  code: z.string().trim().toUpperCase().regex(/^[A-Z0-9_-]{3,30}$/, 'ইংরেজি অক্ষর, সংখ্যা, - বা _ (৩–৩০)'),
  courseId: z.uuid().nullable().optional(),
  type: z.enum(COUPON_TYPES),
  value: z.number().int().positive(),
  maxUses: z.number().int().positive().nullable().optional(),
  perUserLimit: z.number().int().positive().default(1),
  minAmount: z.number().int().min(0).default(0),
  startsAt: z.iso.datetime({ offset: true }).nullable().optional(),
  expiresAt: z.iso.datetime({ offset: true }).nullable().optional(),
  active: z.boolean().default(true),
});

export const LedgerEntry = z.object({ id: z.uuid(), type: z.enum(LEDGER_TYPES), amount: z.number().int(), description: z.string().nullable(), availableAt: z.string(), balanceAfter: z.number().int(), createdAt: z.string() });
export const Balance = z.object({ available: z.number().int(), held: z.number().int(), lifetime: z.number().int(), paidOut: z.number().int(), pendingPayouts: z.number().int(), minPayout: z.number().int() });

export const sellerMoney = {
  coupons: endpoint({ method: 'GET', path: '/seller/coupons', summary: 'My coupons', tag: 'seller', auth: 'seller', response: z.object({ items: z.array(Coupon) }) }),
  createCoupon: endpoint({ method: 'POST', path: '/seller/coupons', summary: 'Create coupon (seller- or course-scoped)', tag: 'seller', auth: 'verified_seller', body: CouponUpsert, response: Coupon, status: 201 }),
  updateCoupon: endpoint({ method: 'PATCH', path: '/seller/coupons/:id', summary: 'Update / deactivate coupon', tag: 'seller', auth: 'seller', params: IdParams, body: CouponUpsert.partial(), response: Coupon }),
  ledger: endpoint({ method: 'GET', path: '/seller/ledger', summary: 'Earnings ledger + balance', tag: 'seller', auth: 'seller', query: CursorQuery, response: page(LedgerEntry).extend({ balance: Balance }) }),
  createBundle: endpoint({ method: 'POST', path: '/seller/bundles', summary: 'Bundle own published courses', tag: 'seller', auth: 'verified_seller', body: z.object({ title: z.string().trim().min(5).max(160), description: z.string().max(1000).optional(), courseIds: z.array(z.uuid()).min(2).max(10), price: z.number().int().min(0) }), response: z.object({ id: z.uuid(), slug: z.string() }), status: 201 }),
};

// ---------------------------------------------------------------- admin

export const AdminOrder = Order.extend({ studentName: z.string(), studentPhone: z.string().nullable(), gateway: z.string().nullable(), gatewayTrxId: z.string().nullable() });
export const AdminRefund = z.object({ id: z.uuid(), orderId: z.uuid(), studentName: z.string(), amount: z.number().int(), reason: z.string(), status: z.enum(REFUND_STATUSES), createdAt: z.string(), watchedPercent: z.number().int(), hoursSincePurchase: z.number().int(), items: z.array(z.string()), note: z.string().nullable() });

export const adminCommerce = {
  orders: endpoint({ method: 'GET', path: '/admin/orders', summary: 'Orders (filter by status, search)', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(ORDER_STATUSES).optional(), q: z.string().max(80).optional() }), response: page(AdminOrder) }),
  refunds: endpoint({ method: 'GET', path: '/admin/refunds', summary: 'Refund requests', tag: 'admin', auth: 'admin', query: CursorQuery.extend({ status: z.enum(REFUND_STATUSES).default('requested') }), response: page(AdminRefund) }),
  decideRefund: endpoint({ method: 'PATCH', path: '/admin/refunds/:id', summary: 'Approve / reject refund (gateway refund or manual), ledger reversal, audit', tag: 'admin', auth: 'admin', params: IdParams, body: z.object({ decision: z.enum(['approve', 'reject']), note: z.string().max(500).optional(), manualReference: z.string().max(100).optional() }), response: AdminRefund }),
};
