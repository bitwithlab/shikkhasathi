import { bigint, index, integer, jsonb, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';
import { users } from './identity';

const money = (name: string) => bigint(name, { mode: 'number' });

export const coupons = pgTable(
  'coupons',
  {
    id: id(),
    code: text('code').notNull(),
    scope: text('scope').notNull(), // platform | seller | course
    sellerId: uuid('seller_id'),
    courseId: uuid('course_id'), // CONTENT courses.id (no cross-db FK)
    type: text('type').notNull(), // percent | fixed
    value: integer('value').notNull(), // percent 1–100, or paisa
    maxUses: integer('max_uses'),
    usedCount: integer('used_count').notNull().default(0),
    perUserLimit: integer('per_user_limit').notNull().default(1),
    minAmount: money('min_amount').notNull().default(0),
    startsAt: ts('starts_at'),
    expiresAt: ts('expires_at'),
    active: integer('active').notNull().default(1),
    createdBy: uuid('created_by'),
    ...timestamps,
  },
  (t) => [uniqueIndex('coupons_code_uq').on(t.code), index('coupons_seller_idx').on(t.sellerId)],
);

export const orders = pgTable(
  'orders',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id),
    status: text('status').notNull().default('pending'),
    subtotal: money('subtotal').notNull(),
    discount: money('discount').notNull().default(0),
    total: money('total').notNull(),
    couponId: uuid('coupon_id').references(() => coupons.id),
    currency: text('currency').notNull().default('BDT'),
    paymentMethod: text('payment_method'),
    paidAt: ts('paid_at'),
    /** Cross-DB fulfilment marker: enrollments + ledger + invoice done (outbox). */
    fulfilledAt: ts('fulfilled_at'),
    idempotencyKey: text('idempotency_key'),
    ...timestamps,
  },
  (t) => [
    index('orders_student_idx').on(t.studentId, t.createdAt),
    index('orders_status_idx').on(t.status),
    uniqueIndex('orders_idem_uq').on(t.studentId, t.idempotencyKey),
  ],
);

export const orderItems = pgTable(
  'order_items',
  {
    id: id(),
    orderId: uuid('order_id')
      .notNull()
      .references(() => orders.id, { onDelete: 'cascade' }),
    courseId: uuid('course_id'),
    bundleId: uuid('bundle_id'),
    sellerId: uuid('seller_id').notNull(),
    title: text('title').notNull(),
    price: money('price').notNull(),
    /** Discount allocated to this line (for per-seller ledger). */
    discount: money('discount').notNull().default(0),
    ...timestamps,
  },
  (t) => [index('order_items_order_idx').on(t.orderId)],
);

export const payments = pgTable(
  'payments',
  {
    id: id(),
    orderId: uuid('order_id')
      .notNull()
      .references(() => orders.id),
    gateway: text('gateway').notNull(),
    gatewayTrxId: text('gateway_trx_id').notNull(),
    gatewaySessionId: text('gateway_session_id'),
    method: text('method'),
    amount: money('amount').notNull(),
    status: text('status').notNull().default('initiated'),
    rawPayload: jsonb('raw_payload'),
    validatedAt: ts('validated_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('payments_gateway_trx_uq').on(t.gateway, t.gatewayTrxId), index('payments_order_idx').on(t.orderId)],
);

export const invoices = pgTable(
  'invoices',
  {
    id: id(),
    orderId: uuid('order_id')
      .notNull()
      .references(() => orders.id),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    number: text('number').notNull(),
    billingName: text('billing_name').notNull(),
    billingPhone: text('billing_phone'),
    billingEmail: text('billing_email'),
    itemsJson: jsonb('items_json').notNull(),
    subtotal: money('subtotal').notNull(),
    discount: money('discount').notNull().default(0),
    vat: money('vat').notNull().default(0),
    total: money('total').notNull(),
    storageKey: text('storage_key'),
    emailedAt: ts('emailed_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('invoices_number_uq').on(t.number), uniqueIndex('invoices_order_uq').on(t.orderId)],
);

export const couponRedemptions = pgTable(
  'coupon_redemptions',
  {
    id: id(),
    couponId: uuid('coupon_id')
      .notNull()
      .references(() => coupons.id),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    orderId: uuid('order_id')
      .notNull()
      .references(() => orders.id),
    ...timestamps,
  },
  (t) => [uniqueIndex('coupon_redemptions_order_uq').on(t.couponId, t.orderId), index('coupon_redemptions_user_idx').on(t.couponId, t.userId)],
);

export const enrollments = pgTable(
  'enrollments',
  {
    id: id(),
    studentId: uuid('student_id')
      .notNull()
      .references(() => users.id),
    courseId: uuid('course_id').notNull(),
    orderId: uuid('order_id').references(() => orders.id),
    bundleId: uuid('bundle_id'),
    accessUntil: ts('access_until'),
    status: text('status').notNull().default('active'),
    progressPercent: integer('progress_percent').notNull().default(0),
    lastLessonId: uuid('last_lesson_id'),
    lastAccessedAt: ts('last_accessed_at'),
    completedAt: ts('completed_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('enrollments_student_course_uq').on(t.studentId, t.courseId), index('enrollments_course_idx').on(t.courseId)],
);

export const refunds = pgTable(
  'refunds',
  {
    id: id(),
    orderId: uuid('order_id')
      .notNull()
      .references(() => orders.id),
    requestedBy: uuid('requested_by')
      .notNull()
      .references(() => users.id),
    reason: text('reason').notNull(),
    status: text('status').notNull().default('requested'),
    amount: money('amount').notNull(),
    processedBy: uuid('processed_by'),
    processedAt: ts('processed_at'),
    gatewayRefundId: text('gateway_refund_id'),
    note: text('note'),
    deletedAt: ts('deleted_at'),
    ...timestamps,
  },
  (t) => [index('refunds_status_idx').on(t.status), uniqueIndex('refunds_open_order_uq').on(t.orderId)],
);

export const payouts = pgTable(
  'payouts',
  {
    id: id(),
    sellerId: uuid('seller_id')
      .notNull()
      .references(() => users.id),
    amount: money('amount').notNull(),
    method: text('method').notNull(),
    accountMasked: text('account_masked'),
    status: text('status').notNull().default('requested'),
    reference: text('reference'),
    processedBy: uuid('processed_by'),
    processedAt: ts('processed_at'),
    note: text('note'),
    deletedAt: ts('deleted_at'),
    ...timestamps,
  },
  (t) => [index('payouts_seller_idx').on(t.sellerId, t.createdAt), index('payouts_status_idx').on(t.status)],
);

export const ledgerEntries = pgTable(
  'ledger_entries',
  {
    id: id(),
    sellerId: uuid('seller_id')
      .notNull()
      .references(() => users.id),
    type: text('type').notNull(), // sale | commission | refund | payout | adjustment
    amount: money('amount').notNull(), // +/- paisa
    orderId: uuid('order_id'),
    orderItemId: uuid('order_item_id'),
    payoutId: uuid('payout_id'),
    refundId: uuid('refund_id'),
    description: text('description'),
    /** Idempotency: `sale:<itemId>`, `commission:<itemId>`, `refund:<refundId>:<itemId>`, `payout:<payoutId>`. */
    dedupeKey: text('dedupe_key').notNull(),
    availableAt: ts('available_at').notNull(),
    balanceAfter: money('balance_after').notNull(),
    ...timestamps,
  },
  (t) => [
    index('ledger_seller_created_idx').on(t.sellerId, t.createdAt),
    uniqueIndex('ledger_dedupe_uq').on(t.dedupeKey),
  ],
);
