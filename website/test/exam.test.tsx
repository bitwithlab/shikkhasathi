import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockTestPage from '@/features/learning/mock/MockTestPage';
import ExamModePage from '@/features/learning/exam/ExamModePage';

const U = (n: number) => `0192f0c0-0000-7000-8000-${String(n).padStart(12, '0')}`;
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'content-type': 'application/json' } });
const wrap = (path: string, route: string, el: React.ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={route} element={el} />
          <Route path="/mock-test/:id/result" element={<p>RESULT PAGE</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

afterEach(() => vi.restoreAllMocks());

const attempt = {
  id: U(9), testId: U(1), courseId: U(2), title: 'Midterm Mock', durationSec: 900, startedAt: new Date().toISOString(), deadlineAt: new Date(Date.now() + 900_000).toISOString(), serverNow: new Date().toISOString(), submitted: false,
  questions: [
    { id: U(11), body: 'Stack কোন নীতিতে চলে?', chapterTag: 'Stack', options: [{ id: U(111), body: 'LIFO' }, { id: U(112), body: 'FIFO' }] },
    { id: U(12), body: 'Queue কোন নীতিতে চলে?', chapterTag: 'Queue', options: [{ id: U(121), body: 'FIFO' }, { id: U(122), body: 'LIFO' }] },
  ],
  answers: [],
};

describe('mock test runner', () => {
  it('autosaves the chosen option (not a stale value), flags, navigates, submits with confirmation', async () => {
    const bodies: unknown[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      const u = String(url);
      if (u.endsWith('/attempts')) return json(attempt, 201);
      if (u.endsWith('/answers')) {
        bodies.push(JSON.parse(String(init!.body)));
        return json({ ok: true });
      }
      if (u.endsWith('/submit')) return json({ attemptId: U(9) });
      return json({}, 404);
    });
    wrap(`/mock-test/${U(1)}`, '/mock-test/:id', <MockTestPage />);
    expect(await screen.findByText('Stack কোন নীতিতে চলে?')).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent(/১৪:৫|১৫:০০/);
    await userEvent.click(screen.getByRole('radio', { name: /LIFO/ }));
    await waitFor(() => expect(bodies[0]).toEqual({ questionId: U(11), optionId: U(111), flagged: false }));
    await userEvent.click(screen.getByRole('button', { name: 'রিভিউর জন্য চিহ্নিত' }));
    await waitFor(() => expect(bodies[1]).toEqual({ questionId: U(11), optionId: U(111), flagged: true }));
    await userEvent.click(screen.getByRole('button', { name: /পরের/ }));
    expect(screen.getByText('Queue কোন নীতিতে চলে?')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'জমা দিন' })[0]!);
    expect(await screen.findByText('১টি প্রশ্নের উত্তর বাকি আছে।')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'জমা দিন' }).at(-1)!);
    expect(await screen.findByText('RESULT PAGE')).toBeInTheDocument();
  });
});

describe('exam mode', () => {
  it('no plans → shows the create form', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => (String(url).includes('/exam-plans') ? json({ items: [] }) : json({ items: [] })));
    wrap('/exam-mode', '/exam-mode', <ExamModePage />);
    expect(await screen.findByText('সামনে কোন পরীক্ষা আছে?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'প্ল্যান তৈরি করুন' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('বিষয় বা কোর্স কোড'), 'Data Structures');
    expect(screen.getByRole('button', { name: 'প্ল্যান তৈরি করুন' })).toBeEnabled();
    expect(screen.getByText(/বাকি আছে/)).toBeInTheDocument();
  });
});
