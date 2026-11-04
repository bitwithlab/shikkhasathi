import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';

export const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'content-type': 'application/json' } });
export const uuid = (n: number) => `0192f0c0-0000-7000-8000-${String(n).padStart(12, '0')}`;

export type Call = { method: string; url: string; body: unknown };
type Handler = (url: string, method: string, body: unknown) => Response | undefined;

/** Route fetch by URL; records every call. Unhandled → 404 envelope. */
export function mockApi(handler: Handler) {
  const calls: Call[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    calls.push({ method, url, body });
    return handler(url, method, body) ?? json({ error: { code: 'NOT_FOUND', message: 'nf' } }, 404);
  });
  return calls;
}

export function renderAt(path: string, routePath: string, el: ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={routePath} element={el} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export const course = (o: Record<string, unknown> = {}) => ({ id: uuid(1), slug: 'ds', title: 'Data Structures Midterm', status: 'published', price: 29900, lessonCount: 10, enrollmentCount: 12, ratingAvg: 4.8, ratingCount: 10, updatedAt: new Date().toISOString(), coverColor: null, courseCode: 'CSE-2203', ...o });
