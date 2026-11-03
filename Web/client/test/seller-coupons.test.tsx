import '@/features/seller/ops/i18n';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { t } from '@/lib/i18n';
import CouponsScreen, { couponBody, couponStatus } from '@/features/seller/screens/CouponsScreen';
import { course, json, mockApi, renderAt, uuid } from './seller-ops-helpers';

const coupon = (o: Record<string, unknown> = {}) => ({ id: uuid(40), code: 'MIDTERM20', scope: 'course', courseId: uuid(1), type: 'percent', value: 20, maxUses: 100, usedCount: 37, perUserLimit: 1, minAmount: 0, startsAt: null, expiresAt: null, active: true, ...o });
const draft = (o: Record<string, unknown> = {}) => ({ code: 'exam20', courseId: '', type: 'percent' as const, value: '20', maxUses: '', perUserLimit: '1', minAmount: '', startsAt: '', expiresAt: '', active: true, ...o });

// Radix Switch inside a <form> measures itself
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
} as unknown as typeof ResizeObserver;

afterEach(() => vi.restoreAllMocks());

describe('coupon helpers', () => {
  it('builds the API body: uppercase code, taka → paisa, Dhaka dates, null scope = all courses', () => {
    const r = couponBody(draft({ type: 'fixed', value: '৫০', minAmount: '199', maxUses: '100', startsAt: '2026-11-10', expiresAt: '2026-11-20' }), t);
    expect(r.body).toEqual({ code: 'EXAM20', courseId: null, type: 'fixed', value: 5000, maxUses: 100, perUserLimit: 1, minAmount: 19900, startsAt: '2026-11-10T00:00:00+06:00', expiresAt: '2026-11-20T23:59:59+06:00', active: true });
  });
  it('rejects bad input like the server does', () => {
    expect(couponBody(draft({ value: '95' }), t).errors.value).toBe('১ থেকে ৯০-এর মধ্যে শতাংশ দিন');
    expect(couponBody(draft({ code: 'a b' }), t).errors.code).toBeTruthy();
    expect(couponBody(draft({ startsAt: '2026-11-20', expiresAt: '2026-11-10' }), t).errors.expiresAt).toBe('শেষ তারিখ শুরুর পরে হতে হবে');
    expect(couponBody(draft({ maxUses: '0' }), t).errors.maxUses).toBeTruthy();
  });
  it('derives status', () => {
    const now = Date.parse('2026-11-03T00:00:00Z');
    expect(couponStatus(coupon() as never, now)).toBe('live');
    expect(couponStatus(coupon({ active: false }) as never, now)).toBe('off');
    expect(couponStatus(coupon({ expiresAt: '2026-11-01T00:00:00Z' }) as never, now)).toBe('expired');
    expect(couponStatus(coupon({ usedCount: 100 }) as never, now)).toBe('usedUp');
    expect(couponStatus(coupon({ startsAt: '2026-12-01T00:00:00Z' }) as never, now)).toBe('scheduled');
  });
});

describe('Seller coupons page', () => {
  function api(items: unknown[] = [coupon()], courses = [course(), course({ id: uuid(2), slug: 'oop', title: 'OOP Java Final', price: 19900 })]) {
    return mockApi((url, method, body) => {
      if (url.endsWith('/seller/courses')) return json({ items: courses });
      if (url.endsWith('/seller/coupons') && method === 'POST') return json(coupon({ id: uuid(41), ...(body as object) }), 201);
      if (url.endsWith('/seller/coupons')) return json({ items });
      if (url.includes('/seller/coupons/') && method === 'PATCH') return json(coupon({ ...(body as object) }));
      if (url.endsWith('/seller/bundles')) return json({ id: uuid(50), slug: 'midterm-final-ab12c' }, 201);
      return undefined;
    });
  }

  it('lists coupons with usage and toggles active via PATCH', async () => {
    const calls = api();
    const u = userEvent.setup();
    renderAt('/teach/coupons', '/teach/coupons', <CouponsScreen />);
    expect(await screen.findByText('MIDTERM20')).toBeInTheDocument();
    expect(screen.getByText('২০% ছাড়')).toBeInTheDocument();
    expect(await screen.findByText('Data Structures Midterm', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByText('চালু')).toBeInTheDocument();
    await u.click(screen.getByRole('switch', { name: 'MIDTERM20 চালু/বন্ধ' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ active: false }));
    expect(await screen.findByText('বন্ধ')).toBeInTheDocument();
  });

  it('creates a coupon from the sheet', async () => {
    const calls = api([]);
    const u = userEvent.setup();
    renderAt('/teach/coupons', '/teach/coupons', <CouponsScreen />);
    await u.click(await screen.findByRole('button', { name: 'প্রথম কুপন বানান' }));
    const dialog = await screen.findByRole('dialog');
    await u.type(within(dialog).getByLabelText('কুপন কোড'), 'final15');
    await u.click(within(dialog).getByRole('radio', { name: 'নির্দিষ্ট টাকা (৳)' }));
    await u.type(within(dialog).getByLabelText('ছাড় (৳)'), '75');
    await u.click(within(dialog).getByRole('button', { name: 'কুপন তৈরি করুন' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ code: 'FINAL15', type: 'fixed', value: 7500, courseId: null, perUserLimit: 1 }));
  });

  it('bundles two published courses, shows savings and the checkout link', async () => {
    const calls = api([]);
    const u = userEvent.setup();
    renderAt('/teach/coupons', '/teach/coupons', <CouponsScreen />);
    const section = (await screen.findByRole('heading', { name: 'বান্ডেল অফার' })).closest('section')!;
    await u.click(await within(section).findByRole('checkbox', { name: /Data Structures Midterm/ }));
    await u.click(within(section).getByRole('checkbox', { name: /OOP Java Final/ }));
    await u.type(within(section).getByLabelText('বান্ডেলের নাম'), 'Midterm + Final Combo');
    await u.type(within(section).getByLabelText('বান্ডেলের দাম (৳)'), '600');
    expect(within(section).getByText('২টি কোর্স আলাদা কিনলে').nextSibling).toHaveTextContent('৳৪৯৮');
    // 600 ≥ 498 → blocked client-side
    await u.click(within(section).getByRole('button', { name: 'বান্ডেল তৈরি করুন' }));
    expect(within(section).getByRole('alert')).toHaveTextContent('দাম ৳৪৯৮-এর কম হতে হবে');
    await u.clear(within(section).getByLabelText('বান্ডেলের দাম (৳)'));
    await u.type(within(section).getByLabelText('বান্ডেলের দাম (৳)'), '399');
    expect(within(section).getByText(/৳৯৯ \(২০%\)/)).toBeInTheDocument();
    await u.click(within(section).getByRole('button', { name: 'বান্ডেল তৈরি করুন' }));
    expect(await within(section).findByRole('link', { name: '/checkout/bundle?slug=midterm-final-ab12c' })).toHaveAttribute('href', '/checkout/bundle?slug=midterm-final-ab12c');
    expect(calls.find((c) => c.url.endsWith('/seller/bundles'))?.body).toEqual({ title: 'Midterm + Final Combo', courseIds: [uuid(1), uuid(2)], price: 39900 });
  });
});
