import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import EarningsScreen from '@/features/seller/screens/EarningsScreen';
import { json, mockApi, renderAt, uuid } from './seller-ops-helpers';

const balance = { available: 120000, held: 30000, lifetime: 450000, paidOut: 300000, pendingPayouts: 0, minPayout: 50000 };
const now = Date.now();
const ledger = {
  items: [
    { id: uuid(11), type: 'sale', amount: 29900, description: 'Data Structures Midterm', availableAt: new Date(now + 5 * 86400e3).toISOString(), balanceAfter: 150000, createdAt: new Date(now).toISOString() },
    { id: uuid(12), type: 'commission', amount: -5980, description: 'Platform commission 20%', availableAt: new Date(now - 86400e3).toISOString(), balanceAfter: 120100, createdAt: new Date(now).toISOString() },
  ],
  nextCursor: null,
  balance,
};
const profile = (o: Record<string, unknown> = {}) => ({ profile: { userId: uuid(9), slug: 'rakib', displayName: 'রাকিব', verificationStatus: 'verified', payoutMethod: 'bkash', payoutAccountMasked: '01•••••678', ...o } });

afterEach(() => vi.restoreAllMocks());

function api(o: { payouts?: unknown[]; profile?: unknown; pay?: (body: unknown) => Response } = {}) {
  return mockApi((url, method, body) => {
    if (url.includes('/seller/ledger')) return json(ledger);
    if (url.endsWith('/seller/profile')) return json(o.profile ?? profile());
    if (url.endsWith('/seller/payouts') && method === 'POST') return o.pay?.(body) ?? json({ id: uuid(20), amount: (body as { amount: number }).amount, method: 'bkash', accountMasked: '01•••••678', status: 'requested', reference: null, note: null, createdAt: new Date().toISOString(), processedAt: null }, 201);
    if (url.endsWith('/seller/payouts')) return json({ items: o.payouts ?? [] });
    return undefined;
  });
}

describe('Seller earnings', () => {
  it('shows balances and a signed, typed ledger with the hold date', async () => {
    api();
    renderAt('/teach/earnings', '/teach/earnings', <EarningsScreen />);
    expect(await screen.findByText('৳১,২০০')).toBeInTheDocument();
    expect(screen.getByText('হোল্ডে আছে').closest('div')!.parentElement).toHaveTextContent('৳৩০০');
    expect(screen.getByText('বিক্রি')).toBeInTheDocument();
    expect(screen.getByText('কমিশন')).toBeInTheDocument();
    expect(screen.getByText(/\+৳২৯৯/)).toBeInTheDocument();
    expect(screen.getByText(/−৳৫৯\.৮/)).toBeInTheDocument();
    expect(screen.getAllByText(/থেকে তোলা যাবে/)).toHaveLength(1);
    expect(screen.getByText(/পাঠানো হবে: বিকাশ 01•••••678/)).toBeInTheDocument();
  });

  it('validates the amount and requests the payout in paisa', async () => {
    const calls = api();
    const u = userEvent.setup();
    renderAt('/teach/earnings', '/teach/earnings', <EarningsScreen />);
    const input = await screen.findByLabelText('কত টাকা তুলবেন');
    expect(input).toHaveValue('1200');
    await u.clear(input);
    await u.type(input, '৩০০');
    await u.click(screen.getByRole('button', { name: 'পেআউট অনুরোধ করুন' }));
    expect(screen.getByRole('alert')).toHaveTextContent('সর্বনিম্ন ৳৫০০ তোলা যায়।');
    await u.clear(input);
    await u.type(input, '5000');
    await u.click(screen.getByRole('button', { name: 'পেআউট অনুরোধ করুন' }));
    expect(screen.getByRole('alert')).toHaveTextContent('এখন সর্বোচ্চ ৳১,২০০ তোলা যাবে।');
    await u.clear(input);
    await u.type(input, '750.50');
    await u.click(screen.getByRole('button', { name: 'পেআউট অনুরোধ করুন' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ amount: 75050 }));
  });

  it('shows the server message on conflict and the open request instead of the form', async () => {
    api({ pay: () => json({ error: { code: 'CONFLICT', message: 'একটি পেআউট অনুরোধ আগে থেকেই প্রক্রিয়াধীন আছে।' } }, 409) });
    const u = userEvent.setup();
    renderAt('/teach/earnings', '/teach/earnings', <EarningsScreen />);
    await u.click(await screen.findByRole('button', { name: 'পেআউট অনুরোধ করুন' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('একটি পেআউট অনুরোধ আগে থেকেই প্রক্রিয়াধীন আছে।');
    cleanup();
    vi.restoreAllMocks();

    api({ payouts: [{ id: uuid(30), amount: 80000, method: 'bkash', accountMasked: '01•••••678', status: 'approved', reference: null, note: null, createdAt: new Date().toISOString(), processedAt: null }] });
    renderAt('/teach/earnings', '/teach/earnings', <EarningsScreen />);
    expect(await screen.findByText('৳৮০০ পেআউট প্রক্রিয়াধীন')).toBeInTheDocument();
    const history = screen.getByRole('heading', { name: 'পেআউটের ইতিহাস' }).parentElement!;
    expect(within(history).getByText('অনুমোদিত')).toBeInTheDocument();
  });

  it('links to verification when payout details are missing', async () => {
    api({ profile: profile({ payoutMethod: null, payoutAccountMasked: null }) });
    renderAt('/teach/earnings', '/teach/earnings', <EarningsScreen />);
    expect(await screen.findByRole('link', { name: /মাধ্যম যোগ করুন/ })).toHaveAttribute('href', '/teach/verification');
    expect(screen.queryByRole('button', { name: 'পেআউট অনুরোধ করুন' })).toBeNull();
  });
});
