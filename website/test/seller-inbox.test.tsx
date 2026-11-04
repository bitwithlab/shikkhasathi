import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QaScreen from '@/features/seller/screens/QaScreen';
import ReviewsScreen from '@/features/seller/screens/ReviewsScreen';
import StudentsScreen from '@/features/seller/screens/StudentsScreen';
import AnalyticsScreen from '@/features/seller/screens/AnalyticsScreen';
import { niceMax } from '@/features/seller/ops/BarChart';
import { course, json, mockApi, renderAt, uuid } from './seller-ops-helpers';

afterEach(() => vi.restoreAllMocks());
const iso = new Date().toISOString();

describe('Seller Q&A inbox', () => {
  const q = { id: uuid(60), lessonId: uuid(61), lessonTitle: 'AVL Tree Rotation', body: 'AVL double rotation কখন লাগে?', timestampSec: 125, authorName: 'সাদমান সা.', mine: false, status: 'open', createdAt: iso, answers: [], courseId: uuid(1), courseTitle: 'Data Structures Midterm' };

  it('filters by status, links to the lesson Q&A tab and replies inline', async () => {
    const calls = mockApi((url, method, body) => {
      if (url.includes('/seller/questions')) return json({ items: [q], nextCursor: null });
      if (url.endsWith(`/questions/${q.id}/answers`) && method === 'POST') return json({ ...q, status: 'answered', answers: [{ id: uuid(62), body: (body as { body: string }).body, authorName: 'রাকিব হাসান', isSeller: true, createdAt: iso }] }, 201);
      return undefined;
    });
    const u = userEvent.setup();
    renderAt('/teach/qa', '/teach/qa', <QaScreen />);
    expect(await screen.findByText('AVL double rotation কখন লাগে?')).toBeInTheDocument();
    expect(calls[0]!.url).toContain('status=open');
    expect(screen.getByRole('link', { name: /AVL Tree Rotation/ })).toHaveAttribute('href', `/learn/${uuid(1)}/${uuid(61)}?tab=qa`);
    expect(screen.getByText(/@ 2:05/)).toBeInTheDocument();
    await u.type(screen.getByLabelText('সাদমান সা.-কে উত্তর দিন'), 'Left-Right বা Right-Left imbalance হলে।');
    await u.click(screen.getByRole('button', { name: 'উত্তর পাঠান' }));
    expect(await screen.findByText('Left-Right বা Right-Left imbalance হলে।')).toBeInTheDocument();
    expect(screen.getByText('শিক্ষক')).toBeInTheDocument();
    expect(screen.getByText('উত্তর দেওয়া হয়েছে')).toBeInTheDocument();
    await u.click(screen.getByRole('tab', { name: 'সব' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('status=all'))).toBe(true));
  });
});

describe('Seller reviews', () => {
  const r = (o: Record<string, unknown> = {}) => ({ id: uuid(70), rating: 4, body: 'খুব ভালো বুঝিয়েছেন, তবে আরও প্র্যাকটিস প্রশ্ন দরকার।', authorName: 'নুসরাত জা.', createdAt: iso, sellerReply: null, sellerRepliedAt: null, verified: true, courseId: uuid(1), courseTitle: 'Data Structures Midterm', ...o });

  it('shows stars and existing replies; replies once and then hides the button', async () => {
    const calls = mockApi((url, method, body) => {
      if (url.includes('/seller/reviews?') || url.endsWith('/seller/reviews')) return json({ items: [r(), r({ id: uuid(71), sellerReply: 'ধন্যবাদ!', sellerRepliedAt: iso, authorName: 'তানভীর আ.' })], nextCursor: null });
      if (url.endsWith('/reply') && method === 'PUT') return json({ ...r(), sellerReply: (body as { reply: string }).reply, sellerRepliedAt: iso });
      return undefined;
    });
    const u = userEvent.setup();
    renderAt('/teach/reviews', '/teach/reviews', <ReviewsScreen />);
    expect(await screen.findAllByRole('img', { name: '৫-এর মধ্যে ৪ স্টার' })).toHaveLength(2);
    expect(screen.getByText('১টি রিভিউ আপনার উত্তরের অপেক্ষায়')).toBeInTheDocument();
    expect(screen.getByText('ধন্যবাদ!')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'উত্তর দিন' })).toHaveLength(1);
    await u.click(screen.getByRole('button', { name: 'উত্তর দিন' }));
    await u.type(screen.getByLabelText('নুসরাত জা.-এর রিভিউতে উত্তর'), 'ধন্যবাদ, নতুন প্র্যাকটিস সেট যোগ করছি।');
    await u.click(screen.getByRole('button', { name: 'উত্তর প্রকাশ করুন' }));
    expect(await screen.findByText('ধন্যবাদ, নতুন প্র্যাকটিস সেট যোগ করছি।')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'উত্তর দিন' })).toBeNull();
    expect(calls.find((c) => c.method === 'PUT')?.url).toContain(`/seller/reviews/${uuid(70)}/reply`);
  });
});

describe('Seller students', () => {
  it('shows short names, progress and completion; filters by course', async () => {
    const calls = mockApi((url) => {
      if (url.endsWith('/seller/courses')) return json({ items: [course()] });
      if (url.includes('/seller/students'))
        return json({
          items: [
            { enrollmentId: uuid(80), studentName: 'আরিফ হো.', institutionShort: 'MU', courseId: uuid(1), courseTitle: 'Data Structures Midterm', progressPercent: 42, enrolledAt: iso, lastAccessedAt: iso, completed: false },
            { enrollmentId: uuid(81), studentName: 'মিম আ.', institutionShort: null, courseId: uuid(1), courseTitle: 'Data Structures Midterm', progressPercent: 100, enrolledAt: iso, lastAccessedAt: null, completed: true },
          ],
          nextCursor: null,
          total: 2,
        });
      return undefined;
    });
    const u = userEvent.setup();
    renderAt('/teach/students', '/teach/students', <StudentsScreen />);
    expect(await screen.findByText('আরিফ হো.')).toBeInTheDocument();
    expect(screen.getByText('মোট ২ জন শিক্ষার্থী')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'আরিফ হো.-এর অগ্রগতি' })).toHaveAttribute('aria-valuenow', '42');
    expect(screen.getByText('সম্পন্ন')).toBeInTheDocument();
    expect(screen.queryByText(/@|01\d{9}/)).toBeNull();
    await u.selectOptions(screen.getByLabelText('কোর্স'), uuid(1));
    await waitFor(() => expect(calls.some((c) => c.url.includes(`courseId=${uuid(1)}`))).toBe(true));
  });
});

describe('Seller analytics', () => {
  it('renders totals, charts with a data table, per-course rows and switches range', async () => {
    const calls = mockApi((url) => {
      if (url.includes('/seller/analytics'))
        return json({
          totals: { revenue: 1196000, enrollments: 48, students: 1240, avgCompletion: 37, ratingAvg: 4.9, ratingCount: 340, refunds: 1, openQuestions: 3 },
          series: [
            { date: '2026-11-01', revenue: 59800, enrollments: 2 },
            { date: '2026-11-02', revenue: 0, enrollments: 0 },
            { date: '2026-11-03', revenue: 89700, enrollments: 3 },
          ],
          courses: [{ id: uuid(1), title: 'Data Structures Midterm', status: 'published', enrollments: 1240, revenue: 1196000, avgCompletion: 37, ratingAvg: 4.9, ratingCount: 340 }],
        });
      return undefined;
    });
    const u = userEvent.setup();
    renderAt('/teach/analytics', '/teach/analytics', <AnalyticsScreen />);
    expect(await screen.findByText('শেষ ৩০ দিনের আয়')).toBeInTheDocument();
    expect(screen.getAllByText('৳১১,৯৬০')).toHaveLength(2); // headline + course row
    expect(screen.getByRole('link', { name: /৩টি প্রশ্ন উত্তরের অপেক্ষায়/ })).toHaveAttribute('href', '/teach/qa');
    const table = screen.getByRole('table', { name: 'দৈনিক আয়' });
    expect(within(table).getAllByRole('row')).toHaveLength(4);
    expect(within(table).getByText('৳৮৯৭')).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: /Data Structures Midterm/ })).toBeInTheDocument();
    expect(calls[0]!.url).toContain('days=30');
    await u.click(screen.getByRole('radio', { name: '৯০ দিন' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('days=90'))).toBe(true));
  });

  it('niceMax rounds axis ceilings', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(3)).toBe(5);
    expect(niceMax(89700)).toBe(100000);
    expect(niceMax(21)).toBe(25);
  });
});
