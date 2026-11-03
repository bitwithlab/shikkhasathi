import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CheckoutPage from '@/features/marketplace/checkout/CheckoutPage';
import CheckoutResultPage from '@/features/marketplace/checkout/CheckoutResultPage';

const CID = '0192f0c0-0000-7000-8000-000000000001';
const OID = '0192f0c0-0000-7000-8000-0000000000aa';
const course = {
  id: CID, slug: 'ds', title: 'Data Structures Midterm', courseCode: 'CSE-2203', price: 34900, compareAtPrice: 54900, coverColor: '#4F46E5', accessType: 'lifetime', accessDays: null,
  seller: { name: 'রাকিব হাসান' }, viewer: { enrolled: false, wishlisted: false, firstLessonId: null, resumeLessonId: null, isOwner: false },
};
const order = (o: Record<string, unknown> = {}) => ({ id: OID, status: 'pending', items: [{ id: OID, courseId: CID, bundleId: null, title: course.title, price: 34900, discount: 0, coverColor: null, slug: 'ds' }], subtotal: 34900, discount: 0, total: 34900, couponCode: null, paymentMethod: null, createdAt: new Date().toISOString(), paidAt: null, invoiceNumber: null, refund: null, refundEligible: false, firstLessonPath: null, ...o });
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'content-type': 'application/json' } });

function wrap(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/checkout/result" element={<CheckoutResultPage />} />
          <Route path="/checkout/:courseId" element={<CheckoutPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('Checkout', () => {
  it('applies a coupon and pays with the server total, redirecting to the gateway', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    const calls: { url: string; body: unknown }[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      const u = String(url);
      calls.push({ url: u, body: init?.body ? JSON.parse(String(init.body)) : null });
      if (u.includes('/courses/')) return json(course);
      if (u.endsWith('/coupons/validate')) return json({ valid: true, discount: 6980, total: 27920, message: 'কুপন প্রযোজ্য — ৳69.8 ছাড়' });
      if (u.endsWith('/pay')) return json({ redirectUrl: 'https://sandbox.example/pay', gateway: 'fake' });
      if (u.endsWith('/orders')) return json(order({ total: 27920, discount: 6980, couponCode: 'EXAM20' }), 201);
      return json({}, 404);
    });
    wrap(`/checkout/${CID}`);
    expect(await screen.findByText('Data Structures Midterm')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /বিকাশ/ })).toHaveAttribute('aria-checked', 'true');
    await userEvent.type(screen.getByLabelText('কুপন কোড'), 'exam20');
    await userEvent.click(screen.getByRole('button', { name: 'প্রয়োগ করুন' }));
    expect(await screen.findByRole('button', { name: /৳২৭৯\.২ পরিশোধ করুন/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: /নগদ/ }));
    await userEvent.type(screen.getByLabelText('নগদ নম্বর'), '01712345678');
    await userEvent.click(screen.getByRole('button', { name: /পরিশোধ করুন/ }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://sandbox.example/pay'));
    const create = calls.find((c) => c.url.endsWith('/orders'))!.body as Record<string, string>;
    expect(create).toMatchObject({ courseId: CID, couponCode: 'EXAM20' });
    expect(create.idempotencyKey!.length).toBeGreaterThan(8);
    expect(calls.find((c) => c.url.endsWith('/pay'))!.body).toEqual({ method: 'nagad', phone: '01712345678' });
    expect(await screen.findByText('পেমেন্ট পেজে নিয়ে যাওয়া হচ্ছে…')).toBeInTheDocument();
  });

  it('owned course shows start learning instead of the form', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ ...course, viewer: { ...course.viewer, enrolled: true, firstLessonId: CID } }));
    wrap(`/checkout/${CID}`);
    expect(await screen.findByText('এই কোর্সটি আপনার কাছে আগেই আছে')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'শেখা শুরু করুন' })).toHaveAttribute('href', `/learn/${CID}/${CID}`);
  });

  it('result: paid order shows unlock + receipt; failed shows reassurance and retry', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(order({ status: 'paid', paidAt: new Date().toISOString(), invoiceNumber: 'SS-2026-000001', firstLessonPath: '/learn/a/b' })));
    const { unmount } = wrap(`/checkout/result?order=${OID}&status=paid`);
    expect(await screen.findByText('কোর্স আনলক হয়েছে')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'শেখা শুরু করুন' })).toHaveAttribute('href', '/learn/a/b');
    expect(screen.getByRole('button', { name: /রসিদ ডাউনলোড/ })).toBeEnabled();
    unmount();
    vi.restoreAllMocks();
    // URL claims paid, but the server says failed → server wins
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(order({ status: 'failed' })));
    wrap(`/checkout/result?order=${OID}&status=paid`);
    expect(await screen.findByText('পেমেন্ট সম্পন্ন হয়নি')).toBeInTheDocument();
    expect(screen.getByText(/আপনার টাকা কাটা হয়নি/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /আবার চেষ্টা করুন/ })).toHaveAttribute('href', `/checkout/${CID}`);
  });
});
