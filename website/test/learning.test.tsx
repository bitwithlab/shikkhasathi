import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MyCoursesPage from '@/features/learning/MyCoursesPage';
import PlayerPage from '@/features/learning/player/PlayerPage';
import { fmtTime } from '@/features/learning/player/time';

const CID = '0192f0c0-0000-7000-8000-000000000001';
const L1 = '0192f0c0-0000-7000-8000-0000000000a1';
const L2 = '0192f0c0-0000-7000-8000-0000000000a2';
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'content-type': 'application/json' } });
const wrap = (path: string, el: React.ReactNode, route: string) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={route} element={el} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

const myCourse = (o: Record<string, unknown>) => ({ courseId: CID, slug: 'ds', title: 'Data Structures Midterm', courseCode: 'CSE-2203', institutionShort: 'MU', examType: 'midterm', sellerName: 'রাকিব', coverColor: null, progressPercent: 40, completedLessons: 4, totalLessons: 10, resumeLessonId: L2, resumeLessonTitle: 'BST deletion', lastAccessedAt: new Date().toISOString(), completedAt: null, accessUntil: null, expiringSoon: false, expired: false, ...o });
const lesson = (id: string, o: Record<string, unknown> = {}) => ({ id, title: `Lesson ${id.slice(-2)}`, type: 'text', durationSec: 0, isPreview: false, isImportant: false, completed: false, lastPositionSec: 0, locked: false, ...o });
const outline = (o: Record<string, unknown> = {}) => ({
  course: { id: CID, slug: 'ds', title: 'Data Structures Midterm', courseCode: 'CSE-2203', sellerId: CID, sellerName: 'রাকিব', sellerSlug: 'rakib' },
  enrolled: true, isOwner: false, accessUntil: null, progressPercent: 0, completedLessons: 0, totalLessons: 2, resumeLessonId: L1,
  sections: [{ id: CID, title: 'Trees', chapters: [{ id: CID, title: 'BST', lessons: [lesson(L1, { isImportant: true }), lesson(L2)] }] }],
  resources: [], myReview: null, canReview: false, ...o,
});
const detail = (o: Record<string, unknown> = {}) => ({ id: L1, courseId: CID, title: 'Lesson a1', type: 'text', durationSec: 0, isImportant: true, body: 'BST-এ ডিলিট করার তিনটি কেস', document: null, video: null, completed: false, lastPositionSec: 0, prevLessonId: null, nextLessonId: L2, ...o });

afterEach(() => vi.restoreAllMocks());

describe('learning', () => {
  it('fmtTime', () => {
    expect(fmtTime(75)).toBe('1:15');
    expect(fmtTime(3725)).toBe('1:02:05');
    expect(fmtTime(-3)).toBe('0:00');
  });

  it('My Courses: filters by chip, resume link, expiring + completed badges', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ items: [myCourse({}), myCourse({ courseId: L1, slug: 'algo', title: 'Algorithms Final', completedAt: new Date().toISOString(), progressPercent: 100 }), myCourse({ courseId: L2, slug: 'os', title: 'OS Quiz', expiringSoon: true, accessUntil: new Date(Date.now() + 3 * 86_400_000).toISOString() })] }));
    wrap('/my-courses', <MyCoursesPage />, '/my-courses');
    expect(await screen.findByText('Data Structures Midterm')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'চালিয়ে যান' })[0]).toHaveAttribute('href', `/learn/${CID}/${L2}`);
    expect(screen.getByRole('link', { name: 'আবার দেখুন' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /^শেষ/ }));
    expect(screen.queryByText('Data Structures Midterm')).not.toBeInTheDocument();
    expect(screen.getByText('Algorithms Final')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /মেয়াদ শেষ হচ্ছে/ }));
    expect(screen.getByText('OS Quiz')).toBeInTheDocument();
  });

  it('My Courses empty state points to Explore', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ items: [] }));
    wrap('/my-courses', <MyCoursesPage />, '/my-courses');
    expect(await screen.findByText('এখনো কোনো কোর্স নেই')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'কোর্স খুঁজুন' })).toHaveAttribute('href', '/explore');
  });

  it('player: text lesson, mark complete updates progress, next lesson, curriculum', async () => {
    const calls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      const u = String(url);
      calls.push(`${init?.method ?? 'GET'} ${u}`);
      if (u.endsWith(`/learn/${CID}`)) return json(outline());
      if (u.endsWith(`/lessons/${L1}/complete`)) return json({ completed: true, progressPercent: 50, streamActive: true });
      if (u.endsWith(`/lessons/${L1}`)) return json(detail());
      return json({}, 404);
    });
    wrap(`/learn/${CID}/${L1}`, <PlayerPage />, '/learn/:courseId/:lessonId');
    expect(await screen.findByText('BST-এ ডিলিট করার তিনটি কেস')).toBeInTheDocument();
    expect(screen.getByText('সম্ভাব্য গুরুত্বপূর্ণ')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'আগের লেসন' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'সম্পন্ন হিসেবে চিহ্নিত করুন' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'সম্পন্ন' })).toHaveAttribute('aria-pressed', 'true'));
    expect(calls).toContain(`POST /api/v1/lessons/${L1}/complete`);
    expect(screen.getAllByText('৫০% শেষ').length).toBeGreaterThan(0);
  });

  it('player: locked lesson for non-buyers shows the buy CTA', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const u = String(url);
      if (u.endsWith(`/learn/${CID}`)) return json(outline({ enrolled: false, sections: [{ id: CID, title: 'Trees', chapters: [{ id: CID, title: 'BST', lessons: [lesson(L1, { isPreview: true }), lesson(L2, { locked: true })] }] }] }));
      return json({ error: { code: 'FORBIDDEN', message: 'এই লেসন দেখতে কোর্সটি কিনুন।' } }, 403);
    });
    wrap(`/learn/${CID}/${L2}`, <PlayerPage />, '/learn/:courseId/:lessonId');
    expect(await screen.findByText('এই লেসন দেখতে কোর্সটি কিনুন।')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'পুরো কোর্স কিনুন' })[0]).toHaveAttribute('href', '/c/ds');
  });
});
