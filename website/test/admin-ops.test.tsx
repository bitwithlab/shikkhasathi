import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import AdminDashboardPage from '@/features/admin/AdminDashboardPage';
import AdminOrdersPage from '@/features/admin/AdminOrdersPage';
import AdminRefundsPage from '@/features/admin/AdminRefundsPage';
import AdminPayoutsPage from '@/features/admin/AdminPayoutsPage';
import AdminUsersPage from '@/features/admin/AdminUsersPage';
import AdminReportsPage from '@/features/admin/AdminReportsPage';
import AdminBroadcastsPage from '@/features/admin/AdminBroadcastsPage';
import AdminEmailLogsPage from '@/features/admin/AdminEmailLogsPage';
import AdminAuditPage from '@/features/admin/AdminAuditPage';
import { useAuth } from '@/lib/auth';

const U = (n: number) => `0192f0c0-0000-7000-8000-${String(n).padStart(12, '0')}`;
const now = new Date().toISOString();
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'content-type': 'application/json' } });
type Call = { url: string; method: string; body: unknown };

function mockFetch(handler: (u: string, method: string, body: unknown) => Response | undefined) {
  const calls: Call[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
    const u = String(url);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    calls.push({ url: u, method, body });
    return handler(u, method, body) ?? json({ error: { code: 'NOT_FOUND', message: 'নেই' } }, 404);
  });
  return calls;
}

function wrap(el: ReactElement) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{el}</MemoryRouter>
    </QueryClientProvider>,
  );
}

// Radix Switch measures itself; jsdom has no ResizeObserver.
globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;

afterEach(() => vi.restoreAllMocks());

describe('Admin dashboard', () => {
  it('shows KPIs, queue tiles linking to their pages, and refetches per period', async () => {
    const series = Array.from({ length: 30 }, (_, i) => ({ date: `2026-10-${String(i + 1).padStart(2, '0')}`, gmv: i === 29 ? 104700 : 0, orders: i === 29 ? 3 : 0, signups: 1 }));
    const calls = mockFetch((u) =>
      u.includes('/admin/analytics')
        ? json({
            totals: { gmv: 250000, platformRevenue: 20940, orders: 3, refunds: 1, refundAmount: 34900, newStudents: 14, newSellers: 6, enrollments: 2, classroomConnected: 1, activeStudents7d: 10 },
            queues: { pendingSellers: 1, pendingCourses: 2, pendingInstitutions: 0, openReports: 0, refundRequests: 3, payoutRequests: 0 },
            series,
            topCourses: [{ id: U(1), title: 'Data Structures (CSE225)', sellerName: 'রাকিব হাসান', sales: 3, revenue: 104700 }],
            topSellers: [{ id: U(2), name: 'রাকিব হাসান', revenue: 104700, students: 3 }],
          })
        : undefined,
    );
    wrap(<AdminDashboardPage />);
    expect(await screen.findByText('৳২,৫০০')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /কোর্স রিভিউ/ })).toHaveAttribute('href', '/admin/courses/review');
    expect(screen.getByRole('link', { name: /রিফান্ডের অনুরোধ/ })).toHaveAttribute('href', '/admin/refunds');
    expect(screen.getByRole('link', { name: /সেটিংস/ })).toHaveAttribute('href', '/admin/settings');
    expect(screen.getByText('Data Structures (CSE225)')).toBeInTheDocument();
    expect(screen.getByText('মোট ৬টি')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '৭ দিন' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('days=7'))).toBe(true));
  });

  it('shows an error state with retry', async () => {
    mockFetch(() => json({ error: { code: 'INTERNAL', message: 'সমস্যা' } }, 500));
    wrap(<AdminDashboardPage />);
    expect(await screen.findByRole('button', { name: /আবার চেষ্টা/ })).toBeInTheDocument();
  });
});

const order = { id: U(10), status: 'paid', items: [{ id: U(11), courseId: U(1), bundleId: null, title: 'Data Structures (CSE225)', price: 34900, discount: 0, coverColor: '#4F46E5', slug: 'ds' }], subtotal: 34900, discount: 0, total: 34900, couponCode: null, paymentMethod: 'bkash', createdAt: now, paidAt: now, invoiceNumber: 'SS-2026-000001', refund: null, refundEligible: true, firstLessonPath: null, studentName: 'নুসরাত জাহান', studentPhone: '+8801711000002', gateway: 'fake', gatewayTrxId: 'SSMW12J7YB2A8A73' };

describe('Admin orders', () => {
  it('searches, opens the drawer with trx id and downloads the invoice', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    const calls = mockFetch((u) => {
      if (u.includes('/invoice')) return json({ url: 'https://files.example/inv.pdf', number: 'SS-2026-000001' });
      if (u.includes('/admin/orders')) return json({ items: [order], nextCursor: null });
      return undefined;
    });
    wrap(<AdminOrdersPage />);
    await userEvent.click(await screen.findByRole('button', { name: `#${U(10).slice(-8).toUpperCase()}` }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByText('SSMW12J7YB2A8A73')).toBeInTheDocument();
    await userEvent.click(within(dlg).getByRole('button', { name: /ইনভয়েস SS-2026-000001/ }));
    await waitFor(() => expect(open).toHaveBeenCalledWith('https://files.example/inv.pdf', '_blank', 'noopener'));
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await userEvent.type(screen.getByPlaceholderText(/TrxID/), 'নুসরাত');
    await waitFor(() => expect(calls.some((c) => c.url.includes('q=') && decodeURIComponent(c.url).includes('নুসরাত'))).toBe(true));
  });
});

describe('Admin refunds', () => {
  it('shows eligibility context and requires a note to reject', async () => {
    const r = { id: U(20), orderId: U(10), studentName: 'তানভীর আহমেদ', amount: 34900, reason: 'ভুল কোর্স কিনে ফেলেছি, আমার সেকশনের নয়', status: 'requested', createdAt: now, watchedPercent: 35, hoursSincePurchase: 5, items: ['Data Structures (CSE225)'], note: null };
    const calls = mockFetch((u, m) => {
      if (u.includes(`/admin/refunds/${U(20)}`) && m === 'PATCH') return json({ ...r, status: 'rejected' });
      if (u.includes('/admin/refunds')) return json({ items: [r], nextCursor: null });
      return undefined;
    });
    wrap(<AdminRefundsPage />);
    expect(await screen.findByText('৩৫%')).toBeInTheDocument();
    expect(screen.getByText('নীতির বাইরে')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'নাকচ করুন' }));
    const dlg = await screen.findByRole('dialog');
    const confirm = within(dlg).getByRole('button', { name: 'নাকচ করুন' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dlg).getByLabelText(/কারণ/), '২০%-এর বেশি দেখা হয়েছে');
    await userEvent.click(confirm);
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ decision: 'reject', note: '২০%-এর বেশি দেখা হয়েছে' }));
  });
});

describe('Admin payouts', () => {
  it('mark paid needs a reference and sends it', async () => {
    const p = { id: U(30), amount: 500000, method: 'bkash', accountMasked: '01•••••678', status: 'requested', reference: null, note: null, createdAt: now, processedAt: null, sellerId: U(2), sellerName: 'রাকিব হাসান', sellerSlug: 'rakib', available: 620000 };
    const calls = mockFetch((u, m) => {
      if (m === 'PATCH') return json({ ...p, status: 'paid', reference: 'BKA7X9Q2LM' });
      if (u.includes('/admin/payouts')) return json({ items: [p], nextCursor: null });
      return undefined;
    });
    wrap(<AdminPayoutsPage />);
    expect(await screen.findByText('৳৬,২০০')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'পাঠানো হয়েছে' }));
    const dlg = await screen.findByRole('dialog');
    const ok = within(dlg).getByRole('button', { name: 'নিশ্চিত করুন' });
    expect(ok).toBeDisabled();
    await userEvent.type(within(dlg).getByLabelText(/রেফারেন্স/), 'BKA7X9Q2LM');
    await userEvent.click(ok);
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ decision: 'paid', reference: 'BKA7X9Q2LM' }));
  });
});

describe('Admin users', () => {
  it('blocks self-demotion/suspension and toggles roles for others', async () => {
    useAuth.setState({ status: 'authed', user: { id: U(1), roles: ['admin'] } as never });
    const users = [
      { id: U(1), name: 'অ্যাডমিন', email: 'admin@shikkhasathi.com', phone: null, roles: ['student', 'admin'], status: 'active', createdAt: now, lastActiveAt: now, enrollments: 0, sellerStatus: null },
      { id: U(3), name: 'সাদিয়া ইসলাম', email: 'sadia@example.com', phone: null, roles: ['student'], status: 'active', createdAt: now, lastActiveAt: null, enrollments: 2, sellerStatus: null },
    ];
    const calls = mockFetch((u, m) => {
      if (m === 'PATCH') return json({ ...users[1], roles: ['student', 'seller'] });
      if (u.includes('/admin/users')) return json({ items: users, nextCursor: null });
      return undefined;
    });
    wrap(<AdminUsersPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'অ্যাডমিন ম্যানেজ করুন' }));
    let dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByRole('switch', { name: 'অ্যাডমিন' })).toBeDisabled();
    expect(within(dlg).getByRole('button', { name: /স্থগিত করুন/ })).toBeDisabled();
    await userEvent.click(within(dlg).getByRole('button', { name: /বন্ধ|Close/ }));
    await userEvent.click(screen.getByRole('button', { name: 'সাদিয়া ইসলাম ম্যানেজ করুন' }));
    dlg = await screen.findByRole('dialog');
    await userEvent.click(within(dlg).getByRole('switch', { name: 'শিক্ষক' }));
    await userEvent.click(within(dlg).getByRole('button', { name: 'ভূমিকা সেভ করুন' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')).toMatchObject({ url: expect.stringContaining(U(3)), body: { roles: ['student', 'seller'] } }));
  });
});

describe('Admin reports', () => {
  it('hides reported content with a resolution note', async () => {
    const r = { id: U(40), targetType: 'review', targetId: U(41), targetPreview: 'এই টিচার একদম বাজে, টাকা নষ্ট', targetLink: '/c/ds#reviews', targetHidden: false, reason: 'abuse', details: null, reporterName: 'নুসরাত', status: 'open', resolution: null, createdAt: now, sameTargetCount: 3 };
    const calls = mockFetch((u, m) => {
      if (m === 'PATCH') return json({ ...r, status: 'resolved', targetHidden: true });
      if (u.includes('/admin/reports')) return json({ items: [r], nextCursor: null });
      return undefined;
    });
    wrap(<AdminReportsPage />);
    expect(await screen.findByText('৩টি রিপোর্ট')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /নতুন ট্যাবে/ })).toHaveAttribute('target', '_blank');
    await userEvent.click(screen.getByRole('button', { name: 'লুকান' }));
    const dlg = await screen.findByRole('dialog');
    await userEvent.type(within(dlg).getByLabelText(/সিদ্ধান্তের নোট/), 'গালি ছিল');
    await userEvent.click(within(dlg).getByRole('button', { name: 'লুকান' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ action: 'hide', resolution: 'গালি ছিল' }));
  });
});

describe('Admin broadcasts', () => {
  it('previews recipients, confirms and sends', async () => {
    const calls = mockFetch((u, m) => {
      if (u.endsWith('/admin/broadcasts/preview')) return json({ recipients: 42 });
      if (u.endsWith('/admin/broadcasts') && m === 'POST') return json({ id: 'bc-1', title: 'মিডটার্ম ছাড়', body: 'সব কোর্সে ২০% ছাড়', audience: 'students', recipients: 42, email: false, createdAt: now, actorName: 'অ্যাডমিন' }, 201);
      if (u.endsWith('/admin/broadcasts')) return json({ items: [] });
      return undefined;
    });
    wrap(<AdminBroadcastsPage />);
    expect(await screen.findByText('৪২ জন পাবেন')).toBeInTheDocument();
    expect(screen.getByText('এখনো কোনো ঘোষণা পাঠানো হয়নি')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'শিক্ষার্থী' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/preview') && (c.body as { audience: string }).audience === 'students')).toBe(true));
    await userEvent.type(screen.getByLabelText('শিরোনাম'), 'মিডটার্ম ছাড়');
    await userEvent.type(screen.getByLabelText('বার্তা'), 'সব কোর্সে ২০% ছাড়');
    await userEvent.type(screen.getByLabelText(/লিংক/), 'https://evil.example');
    await userEvent.click(screen.getByRole('button', { name: 'দেখে পাঠান' }));
    expect(await screen.findByText('সাইটের ভেতরের লিংক দিন, যেমন /explore')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/লিংক/));
    await userEvent.type(screen.getByLabelText(/লিংক/), '/explore');
    await userEvent.click(screen.getByRole('button', { name: 'দেখে পাঠান' }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByText('৪২ জনকে পাঠাবেন?')).toBeInTheDocument();
    await userEvent.click(within(dlg).getByRole('button', { name: 'এখনই পাঠান' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'POST' && c.url.endsWith('/admin/broadcasts'))?.body).toEqual({ title: 'মিডটার্ম ছাড়', body: 'সব কোর্সে ২০% ছাড়', link: '/explore', audience: 'students', institutionId: null, email: false }));
  });
});

describe('Admin logs', () => {
  it('email logs show the error and filter by status', async () => {
    const calls = mockFetch((u) =>
      u.includes('/admin/email-logs') ? json({ items: [{ id: U(50), toEmail: 'rafi@example.com', template: 'order_paid', subject: 'অর্ডার নিশ্চিত', status: 'failed', provider: 'brevo', error: 'Sender not verified', attempts: 3, createdAt: now, sentAt: null }], nextCursor: null }) : undefined,
    );
    wrap(<AdminEmailLogsPage />);
    expect(await screen.findByText('Sender not verified')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'ব্যর্থ' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('status=failed'))).toBe(true));
  });

  it('audit rows expand to pretty before/after JSON', async () => {
    mockFetch((u) =>
      u.includes('/admin/audit') ? json({ items: [{ id: U(60), actorName: 'অ্যাডমিন', action: 'refund.approve', entity: 'refunds', entityId: U(20), before: { status: 'requested' }, after: { status: 'processed' }, ip: null, createdAt: now }], nextCursor: null }) : undefined,
    );
    wrap(<AdminAuditPage />);
    const row = await screen.findByRole('button', { name: /refund\.approve/ });
    expect(row).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(row);
    expect(row).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/"status": "processed"/)).toBeInTheDocument();
  });
});
