import { describe, expect, it } from 'vitest';
import { formatDuration, normalizeDigits, paisaToTaka, parseDuration, percentOff, takaToPaisa } from '@/features/seller/lib/money';
import { isSlug, slugify } from '@/features/seller/lib/slug';
import { kpis } from '@/features/seller/lib/stats';
import { pricingBody, targetIssue, targetsBody, emptyTarget, basicsBody } from '@/features/seller/courses/wizard/model';

describe('paisa ↔ taka', () => {
  it('parses taka (ASCII, Bangla digits, commas, decimals) into integer paisa', () => {
    expect(takaToPaisa('299')).toBe(29900);
    expect(takaToPaisa('২৯৯')).toBe(29900);
    expect(takaToPaisa('1,299.5')).toBe(129950);
    expect(takaToPaisa('৳ 49.99')).toBe(4999);
    expect(takaToPaisa('')).toBeNull();
    expect(takaToPaisa('12a')).toBeNaN();
    expect(takaToPaisa('-5')).toBeNaN();
    expect(takaToPaisa('1.234')).toBeNaN();
  });
  it('formats paisa as an editable taka value', () => {
    expect(paisaToTaka(29900)).toBe('299');
    expect(paisaToTaka(29950)).toBe('299.50');
    expect(paisaToTaka(5)).toBe('0.05');
    expect(paisaToTaka(null)).toBe('');
  });
  it('round-trips', () => {
    for (const p of [0, 5000, 29900, 49999, 5_000_000]) expect(takaToPaisa(paisaToTaka(p))).toBe(p);
  });
  it('percent off only when compare-at is higher', () => {
    expect(percentOff(29900, 49900)).toBe(40);
    expect(percentOff(29900, 29900)).toBe(0);
    expect(percentOff(29900, null)).toBe(0);
  });
  it('durations', () => {
    expect(formatDuration(754)).toBe('12:34');
    expect(formatDuration(3725)).toBe('1:02:05');
    expect(parseDuration('12:30')).toBe(750);
    expect(parseDuration('১:০২:০৫')).toBe(3725);
    expect(parseDuration('45')).toBe(2700);
    expect(parseDuration('')).toBe(0);
    expect(parseDuration('ab')).toBeNull();
    expect(normalizeDigits('০১৯')).toBe('019');
  });
});

describe('slug', () => {
  it('slugifies display names and validates the shared rule', () => {
    expect(slugify('Rakib Hasan')).toBe('rakib-hasan');
    expect(slugify('  Dr. Farhana  Islam!! ')).toBe('dr-farhana-islam');
    expect(isSlug('rakib-hasan')).toBe(true);
    expect(isSlug('ab')).toBe(false);
    expect(isSlug('-rakib')).toBe(false);
    expect(isSlug('Rakib')).toBe(false);
  });
});

describe('pricing draft → PATCH body', () => {
  const base = { free: false, price: '299', compare: '499', accessType: 'lifetime' as const, accessDays: '', accessUntil: '' };
  it('converts to paisa', () => {
    expect(pricingBody(base).body).toEqual({ price: 29900, compareAtPrice: 49900, accessType: 'lifetime', accessDays: null, accessUntil: null });
  });
  it('free wins over typed prices', () => {
    expect(pricingBody({ ...base, free: true }).body).toMatchObject({ price: 0, compareAtPrice: null });
  });
  it('blocks compare-at ≤ price and bad numbers, but only warns under ৳50', () => {
    expect(pricingBody({ ...base, compare: '199' }).body).toBeNull();
    expect(pricingBody({ ...base, price: 'abc' }).errors.price).toBe('wiz.price.err.invalid');
    const cheap = pricingBody({ ...base, price: '30', compare: '' });
    expect(cheap.errors.price).toBe('wiz.price.err.min');
    expect(cheap.body?.price).toBe(3000);
  });
  it('access: days and until-date (Asia/Dhaka end of day)', () => {
    expect(pricingBody({ ...base, accessType: 'days', accessDays: '120' }).body?.accessDays).toBe(120);
    expect(pricingBody({ ...base, accessType: 'days', accessDays: '0' }).body).toBeNull();
    expect(pricingBody({ ...base, accessType: 'until_exam', accessUntil: '2026-12-20' }).body?.accessUntil).toBe('2026-12-20T23:59:59+06:00');
  });
});

describe('targets & basics', () => {
  it('flags incomplete targets and normalises course codes', () => {
    const t = emptyTarget();
    expect(targetIssue(t)).toBe('institution');
    expect(targetIssue({ ...t, isGeneral: true })).toBe('subject');
    expect(targetIssue({ ...t, isGeneral: true, courseCode: 'cse 2203' })).toBeNull();
    expect(targetsBody([{ ...t, isGeneral: true, institutionId: 'x', courseCode: 'cse 2203' }]).targets[0]).toMatchObject({ institutionId: null, courseCode: 'CSE-2203' });
  });
  it('drops empty list rows and incomplete FAQ, skips a too-short title', () => {
    const b = basicsBody({ title: 'DS', shortDescription: ' x ', description: '', learnings: ['A', ' ', ''], notFor: [''], faq: [{ q: 'Q?', a: '' }, { q: 'Q2', a: 'A2' }], coverUrl: null, coverColor: '#4F46E5', language: 'bn', examType: 'midterm', courseType: 'crash' });
    expect(b).not.toHaveProperty('title');
    expect(b.learnings).toEqual(['A']);
    expect(b.notFor).toEqual([]);
    expect(b.faq).toEqual([{ q: 'Q2', a: 'A2' }]);
  });
});

describe('dashboard KPIs', () => {
  it('sums students, weights rating by review count, counts drafts', () => {
    const row = { id: 'x', slug: 's', title: 't', price: 29900, lessonCount: 3, updatedAt: '', coverColor: null, courseCode: null };
    const k = kpis([
      { ...row, status: 'published', enrollmentCount: 120, ratingAvg: 4.5, ratingCount: 30 },
      { ...row, status: 'published', enrollmentCount: 30, ratingAvg: 4.0, ratingCount: 10 },
      { ...row, status: 'draft', enrollmentCount: 0, ratingAvg: 0, ratingCount: 0 },
      { ...row, status: 'changes_requested', enrollmentCount: 0, ratingAvg: 0, ratingCount: 0 },
    ]);
    expect(k).toMatchObject({ published: 2, students: 150, ratingCount: 40, drafts: 2 });
    expect(k.rating).toBeCloseTo(4.375);
  });
});

describe('dashboard greeting', () => {
  it('skips honorific prefixes', async () => {
    const { firstName } = await import('@/features/seller/screens/DashboardScreen');
    expect(firstName('মো. রাকিবুল হাসান')).toBe('রাকিবুল');
    expect(firstName('Dr. Farhana Islam')).toBe('Farhana');
    expect(firstName('Sadman')).toBe('Sadman');
  });
});
