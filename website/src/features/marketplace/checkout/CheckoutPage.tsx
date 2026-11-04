import '../i18n';
import './i18n';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { BadgeCheck, Check, CreditCard, Lock, RotateCcw, ShieldCheck, Tag, X } from 'lucide-react';
import { Badge, Button, Card, ErrorState, Field, Illustration, Input, Skeleton, EmptyState, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatTaka, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

type Method = 'bkash' | 'nagad' | 'rocket' | 'card';
/** Brand marks are plain colored monograms (no third-party logos bundled). */
const METHODS: { id: Method; mark: string; color: string }[] = [
  { id: 'bkash', mark: 'b', color: '#E2136E' },
  { id: 'nagad', mark: 'N', color: '#EC1C24' },
  { id: 'rocket', mark: 'R', color: '#8C3494' },
  { id: 'card', mark: '', color: '#334155' },
];

interface Item {
  kind: 'course' | 'bundle';
  id: string;
  title: string;
  sub: string | null;
  code: string | null;
  price: number;
  compareAtPrice: number | null;
  coverColor: string;
  access: string;
  enrolled: boolean;
  learnPath: string | null;
}

/** Stable per item+coupon so double-clicks and back/forward reuse the same pending order. */
function idempotencyKey(itemId: string, coupon: string | null) {
  const k = `ss.co.${itemId}.${coupon ?? ''}`;
  try {
    const v = sessionStorage.getItem(k);
    if (v) return v;
    const n = crypto.randomUUID();
    sessionStorage.setItem(k, n);
    return n;
  } catch {
    return crypto.randomUUID();
  }
}
function forgetKey(itemId: string, coupon: string | null) {
  try {
    sessionStorage.removeItem(`ss.co.${itemId}.${coupon ?? ''}`);
  } catch {
    /* storage unavailable */
  }
}

export default function CheckoutPage() {
  const { courseId = '' } = useParams();
  const [sp] = useSearchParams();
  const bundleSlug = courseId === 'bundle' ? sp.get('slug') : null;
  const t = useT();
  const course = useQuery({ queryKey: ['course', courseId], queryFn: () => api(contracts.discovery.course, { params: { slug: courseId } }), enabled: !bundleSlug });
  const bundle = useQuery({ queryKey: ['bundle', bundleSlug], queryFn: () => api(contracts.commerce.bundle, { params: { slug: bundleSlug! } }), enabled: !!bundleSlug });
  useEffect(() => {
    document.title = `${t('co.title')} — ${t('brand.name')}`;
  }, [t]);

  const item: Item | null = useMemo(() => {
    if (bundleSlug && bundle.data) {
      const b = bundle.data;
      return { kind: 'bundle', id: b.id, title: b.title, sub: t('co.courses', { n: localDigits(b.courses.length, t.locale) }), code: null, price: b.price, compareAtPrice: b.compareAtPrice, coverColor: '#4F46E5', access: t('co.access.lifetime'), enrolled: false, learnPath: null };
    }
    if (course.data) {
      const c = course.data;
      const access = c.accessType === 'days' && c.accessDays ? t('co.access.days', { n: localDigits(c.accessDays, t.locale) }) : c.accessType === 'until_exam' ? t('co.access.until_exam') : t('co.access.lifetime');
      const lesson = c.viewer.resumeLessonId ?? c.viewer.firstLessonId;
      return { kind: 'course', id: c.id, title: c.title, sub: t('co.by', { name: c.seller.name }), code: c.courseCode, price: c.price, compareAtPrice: c.compareAtPrice, coverColor: c.coverColor ?? '#4F46E5', access, enrolled: c.viewer.enrolled, learnPath: lesson ? `/learn/${c.id}/${lesson}` : '/my-courses' };
    }
    return null;
  }, [bundleSlug, bundle.data, course.data, t]);

  const q = bundleSlug ? bundle : course;
  if (q.isLoading) return <CheckoutSkeleton />;
  if (q.isError || !item)
    return (
      <div className="mx-auto max-w-lg px-4 py-16">
        {q.error instanceof ApiError && q.error.status === 404 ? <EmptyState illustration={<Illustration kind="search" className="h-28 w-32" />} title={t('course.notFound')} action={<Button asChild><Link to="/explore">{t('nav.explore')}</Link></Button>} /> : <ErrorState onRetry={() => void q.refetch()} />}
      </div>
    );
  if (item.enrolled)
    return (
      <div className="mx-auto max-w-lg px-4 py-16">
        <EmptyState illustration={<Illustration kind="done" className="h-28 w-32" />} title={t('co.owned')} body={item.title} action={<Button asChild><Link to={item.learnPath ?? '/my-courses'}>{t('co.start')}</Link></Button>} />
      </div>
    );
  return <CheckoutForm item={item} />;
}

function CheckoutForm({ item }: { item: Item }) {
  const t = useT();
  const nav = useNavigate();
  const user = useAuth((s) => s.user);
  const [method, setMethod] = useState<Method>('bkash');
  const [phone, setPhone] = useState(user?.phone?.replace(/^\+?88/, '') ?? '');
  const [code, setCode] = useState('');
  const [coupon, setCoupon] = useState<{ code: string; discount: number; total: number; message: string } | null>(null);
  const [redirecting, setRedirecting] = useState(false);
  const target = item.kind === 'course' ? { courseId: item.id } : { bundleId: item.id };
  const total = coupon ? coupon.total : item.price;

  const check = useMutation({
    mutationFn: (c: string) => api(contracts.commerce.validateCoupon, { body: { code: c, ...target } }),
    onSuccess: (r, c) => {
      if (r.valid) {
        setCoupon({ code: c.toUpperCase(), discount: r.discount, total: r.total, message: r.message });
        toast.success(r.message);
      } else toast.error(r.message);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  const checkout = useMutation({
    mutationFn: async () => {
      const order = await api(contracts.commerce.createOrder, { body: { ...target, couponCode: coupon?.code, idempotencyKey: idempotencyKey(item.id, coupon?.code ?? null) } });
      if (order.status === 'paid') return { done: order.id };
      if (order.status !== 'pending' && order.status !== 'failed') return { done: order.id };
      const p = await api(contracts.commerce.pay, { params: { id: order.id }, body: { method, phone: method === 'card' ? undefined : phone || undefined } });
      return { redirect: p.redirectUrl };
    },
    onSuccess: (r) => {
      forgetKey(item.id, coupon?.code ?? null);
      if ('done' in r) return nav(`/checkout/result?order=${r.done}&status=paid`, { replace: true });
      setRedirecting(true);
      window.location.assign(r.redirect);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  const wallet = method !== 'card';
  const phoneOk = !wallet || /^01[3-9]\d{8}$/.test(phone.replace(/\D/g, '')) || phone === '';
  const off = item.compareAtPrice && item.compareAtPrice > item.price ? item.compareAtPrice : null;

  if (redirecting) return <Redirecting />;

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 lg:py-10">
      <ol className="mb-6 flex items-center gap-3 text-sm" aria-label={t('co.title')}>
        <li className="flex items-center gap-2 text-muted">
          <span className="grid size-6 place-items-center rounded-full bg-success text-white" aria-hidden><Check className="size-3.5" strokeWidth={3} /></span>
          {t('co.step1')}
        </li>
        <li aria-hidden className="h-px w-8 bg-border" />
        <li className="flex items-center gap-2 font-semibold" aria-current="step">
          <span className="grid size-6 place-items-center rounded-full bg-primary text-xs font-bold text-on-primary tnum" aria-hidden>{localDigits(2, t.locale)}</span>
          {t('co.step2')}
        </li>
      </ol>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] [&>*]:min-w-0">
        {/* Payment */}
        <section aria-labelledby="co-method" className="order-2 lg:order-1">
          <h1 id="co-method" className="text-xl font-bold">{t('co.method')}</h1>
          <div role="radiogroup" aria-labelledby="co-method" className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {METHODS.map((m) => (
              <button
                key={m.id}
                type="button"
                role="radio"
                aria-checked={method === m.id}
                onClick={() => setMethod(m.id)}
                className={cn(
                  'relative flex min-h-24 flex-col items-start justify-between gap-3 rounded-card border bg-surface p-3 text-left transition-[border-color,box-shadow] duration-150',
                  method === m.id ? 'border-2 shadow-e1' : 'border-border hover:border-border-strong',
                )}
                style={method === m.id ? { borderColor: m.color } : undefined}
              >
                <span className="grid size-9 place-items-center rounded-control text-base font-extrabold text-white latin" style={{ background: m.color }} aria-hidden>
                  {m.id === 'card' ? <CreditCard className="size-5" /> : m.mark}
                </span>
                <span>
                  <span className="block font-semibold leading-tight">{t(`co.m.${m.id}`)}</span>
                  <span className="block text-xs text-muted">{m.id === 'card' ? t('co.m.cardSub') : t('co.m.walletSub')}</span>
                </span>
                {method === m.id && <span className="absolute right-2.5 top-2.5 size-2.5 rounded-full" style={{ background: m.color }} aria-hidden />}
              </button>
            ))}
          </div>

          {wallet && (
            <div className="mt-5 max-w-sm">
              <Field label={t('co.phone', { method: t(`co.m.${method}`) })} hint={t('co.phoneHint')} error={!phoneOk ? t('co.phoneInvalid') : undefined}>
                {(p) => <Input {...p} inputMode="numeric" autoComplete="tel-national" value={phone} onChange={(e) => setPhone(e.target.value.replace(/[^\d]/g, '').slice(0, 11))} placeholder="01XXXXXXXXX" className="latin tnum" />}
              </Field>
            </div>
          )}

          <div className="mt-6">
            <PayButton total={total} loading={checkout.isPending} disabled={!phoneOk} onClick={() => checkout.mutate()} />
          </div>

          <ul className="mt-6 flex flex-col gap-3 text-sm text-muted">
            <li className="flex gap-2.5"><ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />{t('co.secure')}</li>
            <li className="flex gap-2.5"><RotateCcw className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />{t('co.refund')}</li>
          </ul>
        </section>

        {/* Summary */}
        <aside aria-labelledby="co-summary" className="order-1 lg:order-2">
          <Card className="p-4 lg:sticky lg:top-20">
            <h2 id="co-summary" className="text-sm font-semibold text-muted">{t('co.summary')}</h2>
            <div className="mt-3 flex gap-3">
              <div className="grid aspect-video w-28 shrink-0 place-items-end overflow-hidden rounded-control p-1.5 text-white" style={{ background: `linear-gradient(135deg, ${item.coverColor}, ${item.coverColor}cc)` }} aria-hidden>
                {item.code && <span className="latin text-[11px] font-bold">{item.code}</span>}
              </div>
              <div className="min-w-0">
                <p className="latin line-clamp-2 font-semibold leading-snug">{item.title}</p>
                {item.sub && <p className="mt-0.5 flex items-center gap-1 text-xs text-muted">{item.kind === 'course' && <BadgeCheck className="size-3.5 text-primary" aria-hidden />}{item.sub}</p>}
                <Badge className="mt-1.5">{t('co.access', { access: item.access })}</Badge>
              </div>
            </div>

            <div className="mt-4 border-t border-border pt-4">
              {coupon ? (
                <div className="flex items-center justify-between gap-2 rounded-control bg-success-soft px-3 py-2 text-sm text-success-fg">
                  <span className="flex min-w-0 items-center gap-2"><Tag className="size-4 shrink-0" aria-hidden /><span className="latin font-semibold">{coupon.code}</span><span className="truncate">{coupon.message}</span></span>
                  <button type="button" onClick={() => setCoupon(null)} className="grid size-8 shrink-0 place-items-center rounded-full hover:bg-black/5" aria-label={t('co.remove')}><X className="size-4" aria-hidden /></button>
                </div>
              ) : (
                <form
                  className="flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (code.trim().length >= 3) check.mutate(code.trim());
                  }}
                >
                  <label htmlFor="co-coupon" className="sr-only">{t('co.coupon')}</label>
                  <Input id="co-coupon" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder={t('co.couponPh')} className="latin min-w-0 flex-1 uppercase" autoComplete="off" />
                  <Button type="submit" variant="secondary" size="lg" loading={check.isPending} disabled={code.trim().length < 3}>{t('co.apply')}</Button>
                </form>
              )}
            </div>

            <dl className="mt-4 flex flex-col gap-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-muted">{t('co.subtotal')}</dt>
                <dd className="tnum">{off && <s className="mr-2 text-muted">{formatTaka(off, t.locale)}</s>}{formatTaka(item.price, t.locale)}</dd>
              </div>
              {coupon && (
                <div className="flex justify-between text-success-fg">
                  <dt>{t('co.discount')}</dt>
                  <dd className="tnum">−{formatTaka(coupon.discount, t.locale)}</dd>
                </div>
              )}
              <div className="mt-1 flex items-baseline justify-between border-t border-border pt-3">
                <dt className="font-semibold">{t('co.total')}</dt>
                <dd className="text-2xl font-bold tnum">{formatTaka(total, t.locale)}</dd>
              </div>
            </dl>
            {item.kind === 'bundle' && off && <p className="mt-1 text-right text-xs text-muted">{t('co.bundleSave', { amount: formatTaka(off, t.locale) })}</p>}
          </Card>
        </aside>
      </div>

    </div>
  );
}

function PayButton({ total, loading, disabled, onClick }: { total: number; loading: boolean; disabled: boolean; onClick: () => void }) {
  const t = useT();
  return (
    <div>
      <Button size="lg" block loading={loading} disabled={disabled} onClick={onClick} icon={<Lock className="size-4" aria-hidden />}>
        {total === 0 ? t('co.getFree') : t('co.pay', { amount: formatTaka(total, t.locale) })}
      </Button>
      <p className="mt-2 text-center text-xs text-muted">
        {t('co.terms').split(/(শর্তাবলি|রিফান্ড নীতি|terms|refund policy)/).map((part, i) =>
          /^(শর্তাবলি|terms)$/.test(part) ? <Link key={i} to="/terms" className="underline hover:text-text">{part}</Link> : /^(রিফান্ড নীতি|refund policy)$/.test(part) ? <Link key={i} to="/refund" className="underline hover:text-text">{part}</Link> : part,
        )}
      </p>
    </div>
  );
}

function Redirecting() {
  const t = useT();
  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center" role="status" aria-live="polite">
      <span className="size-10 animate-spin rounded-full border-[3px] border-primary border-r-transparent motion-reduce:animate-none" aria-hidden />
      <h1 className="mt-6 text-lg font-semibold">{t('co.redirecting')}</h1>
      <p className="mt-1 text-sm text-muted">{t('co.redirectingBody')}</p>
    </div>
  );
}

function CheckoutSkeleton() {
  return (
    <div className="mx-auto grid max-w-5xl gap-6 px-4 py-10 lg:grid-cols-[minmax(0,1fr)_380px]">
      <div className="order-2 flex flex-col gap-4 lg:order-1">
        <Skeleton className="h-7 w-56" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24 rounded-card" />)}</div>
        <Skeleton className="h-11 w-72" />
      </div>
      <Skeleton className="order-1 h-72 rounded-card lg:order-2" />
    </div>
  );
}

