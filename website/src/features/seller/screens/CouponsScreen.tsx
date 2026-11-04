import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { InferBody, InferResponse } from '@/api';
import { Badge, Button, Card, EmptyState, ErrorState, Field, Illustration, Input, ProgressBar, Select, Sheet, Skeleton, Switch, Textarea, toast, type Tone } from '@/components';
import { api, contracts } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDate, formatTaka, localDigits, useT } from '@/lib/i18n';
import { Check, Copy, normalizeDigits, Package, paisaToTaka, PencilLine, Plus, takaToPaisa, Ticket, type CourseItem } from '../parts';
import { dateToIso, dhakaDate, errorText, PageHead, pageClass } from '../ops/kit';

type Coupon = InferResponse<typeof contracts.sellerMoney.coupons>['items'][number];
type CouponBody = InferBody<typeof contracts.sellerMoney.createCoupon>;
type Status = 'live' | 'off' | 'expired' | 'scheduled' | 'usedUp';
const STATUS_TONE: Record<Status, Tone> = { live: 'success', off: 'neutral', expired: 'neutral', scheduled: 'primary', usedUp: 'warning' };

export function couponStatus(c: Coupon, now = Date.now()): Status {
  if (!c.active) return 'off';
  if (c.expiresAt && new Date(c.expiresAt).getTime() < now) return 'expired';
  if (c.maxUses != null && c.usedCount >= c.maxUses) return 'usedUp';
  if (c.startsAt && new Date(c.startsAt).getTime() > now) return 'scheduled';
  return 'live';
}

export default function SellerCouponsScreen() {
  const t = useT();
  const coupons = useQuery({ queryKey: ['seller', 'coupons'], queryFn: () => api(contracts.sellerMoney.coupons) });
  const courses = useQuery({ queryKey: ['seller', 'courses'], queryFn: () => api(contracts.sellerCourses.list) });
  const [editing, setEditing] = useState<Coupon | 'new' | null>(null);
  const courseItems = useMemo(() => (courses.data?.items ?? []).filter((c) => c.status !== 'archived'), [courses.data]);
  const titleOf = (id: string | null) => (id ? (courses.data?.items.find((c) => c.id === id)?.title ?? t('ops.cp.unknownCourse')) : t('ops.cp.allCourses'));

  return (
    <div className={pageClass}>
      <PageHead
        title={t('seller.nav.coupons')}
        subtitle={t('ops.cp.subtitle')}
        action={
          <Button icon={<Plus className="size-5" aria-hidden />} onClick={() => setEditing('new')}>
            {t('ops.cp.new')}
          </Button>
        }
      />

      <Card className="mt-6 overflow-hidden">
        <div className="hidden grid-cols-[minmax(0,1.1fr)_minmax(0,1.3fr)_150px_120px_100px] items-center gap-4 border-b border-border bg-surface-2 px-4 py-2.5 text-xs font-semibold text-muted lg:grid">
          <span>{t('ops.cp.col.code')}</span>
          <span>{t('ops.cp.col.course')}</span>
          <span>{t('ops.cp.col.usage')}</span>
          <span>{t('ops.cp.col.validity')}</span>
          <span className="sr-only">{t('ops.cp.col.actions')}</span>
        </div>
        {coupons.isLoading ? (
          <ul className="divide-y divide-border" aria-busy>
            {Array.from({ length: 3 }, (_, i) => (
              <li key={i} className="flex items-center gap-4 p-4">
                <Skeleton className="h-6 w-28" />
                <Skeleton className="h-4 flex-1" />
                <Skeleton className="h-6 w-11 rounded-full" />
              </li>
            ))}
          </ul>
        ) : coupons.isError ? (
          <ErrorState onRetry={() => void coupons.refetch()} />
        ) : !coupons.data?.items.length ? (
          <EmptyState
            illustration={<Illustration kind="empty" />}
            title={t('ops.cp.emptyTitle')}
            body={t('ops.cp.emptyBody')}
            action={
              <Button variant="soft" icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing('new')}>
                {t('ops.cp.emptyCta')}
              </Button>
            }
          />
        ) : (
          <ul className="divide-y divide-border">
            {coupons.data.items.map((c) => (
              <CouponRow key={c.id} c={c} courseTitle={titleOf(c.courseId)} onEdit={() => setEditing(c)} />
            ))}
          </ul>
        )}
      </Card>

      <BundleSection courses={courses.data?.items ?? []} loading={courses.isLoading} error={courses.isError} onRetry={() => void courses.refetch()} />

      <Sheet side="right" open={!!editing} onOpenChange={(o) => !o && setEditing(null)} title={editing === 'new' ? t('ops.cp.new') : t('ops.cp.edit')} description={t('ops.cp.formHint')}>
        {editing && <CouponForm key={editing === 'new' ? 'new' : editing.id} coupon={editing === 'new' ? null : editing} courses={courseItems} onDone={() => setEditing(null)} />}
      </Sheet>
    </div>
  );
}

function CouponRow({ c, courseTitle, onEdit }: { c: Coupon; courseTitle: string; onEdit: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const status = couponStatus(c);
  const toggle = useMutation({
    mutationFn: (active: boolean) => api(contracts.sellerMoney.updateCoupon, { params: { id: c.id }, body: { active } }),
    onSuccess: (u) => {
      qc.setQueryData<{ items: Coupon[] }>(['seller', 'coupons'], (d) => (d ? { items: d.items.map((x) => (x.id === u.id ? u : x)) } : d));
      toast.success(u.active ? t('ops.cp.turnedOn', { code: u.code }) : t('ops.cp.turnedOff', { code: u.code }));
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const off = c.type === 'percent' ? t('ops.cp.percentOff', { n: localDigits(c.value, t.locale) }) : t('ops.cp.fixedOff', { amount: formatTaka(c.value, t.locale) });
  const usedPct = c.maxUses ? (c.usedCount / c.maxUses) * 100 : 0;
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-3 p-4 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1.3fr)_150px_120px_100px]">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-2">
          <span className="latin rounded-control border border-dashed border-border-strong bg-surface-2 px-2 py-0.5 font-mono text-[15px] font-bold tracking-wide">{c.code}</span>
          <Badge tone={STATUS_TONE[status]}>{t(`ops.cp.status.${status}`)}</Badge>
        </p>
        <p className="mt-1.5 text-sm font-semibold text-primary-fg">{off}</p>
      </div>
      <div className="row-start-1 col-start-2 flex items-center gap-1 lg:col-start-5">
        <Switch checked={c.active} disabled={toggle.isPending} onCheckedChange={(v) => toggle.mutate(v)} label={t('ops.cp.activeLabel', { code: c.code })} />
        <Button variant="ghost" aria-label={t('ops.cp.editLabel', { code: c.code })} icon={<PencilLine className="size-4" aria-hidden />} onClick={onEdit} />
      </div>
      <div className="col-span-2 min-w-0 text-sm lg:col-span-1 lg:col-start-2 lg:row-start-1">
        <p className="latin line-clamp-1">{courseTitle}</p>
        <p className="mt-0.5 text-xs text-muted">
          {c.minAmount > 0 ? t('ops.cp.minAmount', { amount: formatTaka(c.minAmount, t.locale) }) : t('ops.cp.noMin')} · {t('ops.cp.perUser', { n: localDigits(c.perUserLimit, t.locale) })}
        </p>
      </div>
      <div className="min-w-0 text-xs text-muted lg:col-start-3 lg:row-start-1">
        <p className="tnum">
          <span className="font-semibold text-text">{localDigits(c.usedCount, t.locale)}</span>
          {c.maxUses ? ` / ${localDigits(c.maxUses, t.locale)}` : ''} {t('ops.cp.used')}
          {!c.maxUses && ` · ${t('ops.cp.unlimited')}`}
        </p>
        {c.maxUses ? <ProgressBar className="mt-1.5 h-1.5" value={usedPct} tone="primary" label={t('ops.cp.col.usage')} /> : null}
      </div>
      <p className="text-xs text-muted lg:col-start-4 lg:row-start-1">
        {c.expiresAt ? t('ops.cp.until', { date: formatDate(c.expiresAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' }) }) : t('ops.cp.noExpiry')}
        {c.startsAt && status === 'scheduled' && <span className="block">{t('ops.cp.from', { date: formatDate(c.startsAt, t.locale) })}</span>}
      </p>
    </li>
  );
}

// ---------------------------------------------------------------- create / edit

interface Draft {
  code: string;
  courseId: string;
  type: 'percent' | 'fixed';
  value: string;
  maxUses: string;
  perUserLimit: string;
  minAmount: string;
  startsAt: string;
  expiresAt: string;
  active: boolean;
}
type Errors = Partial<Record<keyof Draft, string>>;

const fromCoupon = (c: Coupon | null): Draft => ({
  code: c?.code ?? '',
  courseId: c?.courseId ?? '',
  type: c?.type ?? 'percent',
  value: c ? (c.type === 'percent' ? String(c.value) : paisaToTaka(c.value)) : '',
  maxUses: c?.maxUses ? String(c.maxUses) : '',
  perUserLimit: String(c?.perUserLimit ?? 1),
  minAmount: c?.minAmount ? paisaToTaka(c.minAmount) : '',
  startsAt: c?.startsAt ? dhakaDate(c.startsAt) : '',
  expiresAt: c?.expiresAt ? dhakaDate(c.expiresAt) : '',
  active: c?.active ?? true,
});

const int = (s: string) => {
  const n = normalizeDigits(s).trim();
  return /^\d+$/.test(n) ? Number(n) : Number.NaN;
};

/** Validate the form and build the API body (money in paisa, dates as Dhaka-offset ISO). */
export function couponBody(d: Draft, t: (k: string, v?: Record<string, string | number>) => string): { body?: CouponBody; errors: Errors } {
  const errors: Errors = {};
  const code = d.code.trim().toUpperCase();
  if (!/^[A-Z0-9_-]{3,30}$/.test(code)) errors.code = t('ops.cp.err.code');
  let value = Number.NaN;
  if (d.type === 'percent') {
    value = int(d.value);
    if (!(value >= 1 && value <= 90)) errors.value = t('ops.cp.err.percent');
  } else {
    const p = takaToPaisa(d.value);
    value = p ?? Number.NaN;
    if (!(value > 0)) errors.value = t('ops.cp.err.fixed');
  }
  const maxUses = d.maxUses.trim() ? int(d.maxUses) : null;
  if (maxUses !== null && !(maxUses > 0)) errors.maxUses = t('ops.cp.err.positive');
  const perUserLimit = int(d.perUserLimit);
  if (!(perUserLimit > 0)) errors.perUserLimit = t('ops.cp.err.positive');
  const min = d.minAmount.trim() ? takaToPaisa(d.minAmount) : 0;
  if (min === null || Number.isNaN(min)) errors.minAmount = t('ops.cp.err.amount');
  if (d.startsAt && d.expiresAt && d.expiresAt < d.startsAt) errors.expiresAt = t('ops.cp.err.dates');
  if (Object.keys(errors).length) return { errors };
  return {
    errors,
    body: { code, courseId: d.courseId || null, type: d.type, value, maxUses, perUserLimit, minAmount: min ?? 0, startsAt: dateToIso(d.startsAt), expiresAt: dateToIso(d.expiresAt, true), active: d.active },
  };
}

function CouponForm({ coupon, courses, onDone }: { coupon: Coupon | null; courses: CourseItem[]; onDone: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const [d, setD] = useState<Draft>(() => fromCoupon(coupon));
  const [errors, setErrors] = useState<Errors>({});
  const [serverErr, setServerErr] = useState<string | null>(null);
  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setD((p) => ({ ...p, [k]: v }));
  const save = useMutation({
    mutationFn: (body: CouponBody) => (coupon ? api(contracts.sellerMoney.updateCoupon, { params: { id: coupon.id }, body }) : api(contracts.sellerMoney.createCoupon, { body })),
    onSuccess: (c) => {
      toast.success(coupon ? t('ops.cp.saved', { code: c.code }) : t('ops.cp.created', { code: c.code }));
      void qc.invalidateQueries({ queryKey: ['seller', 'coupons'] });
      onDone();
    },
    onError: (e) => setServerErr(errorText(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const r = couponBody(d, t);
    setErrors(r.errors);
    setServerErr(null);
    if (r.body) save.mutate(r.body);
  };
  const today = dhakaDate(new Date().toISOString());

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4 pt-2">
      <Field label={t('ops.cp.f.code')} hint={t('ops.cp.f.codeHint')} error={errors.code}>
        {(p) => <Input {...p} value={d.code} maxLength={30} autoCapitalize="characters" spellCheck={false} className="latin font-mono uppercase tracking-wide" placeholder="MIDTERM20" onChange={(e) => set('code', e.target.value.toUpperCase().replace(/\s/g, ''))} />}
      </Field>
      <Field label={t('ops.cp.f.course')}>
        {(p) => (
          <Select {...p} value={d.courseId} onChange={(e) => set('courseId', e.target.value)} className="latin">
            <option value="">{t('ops.cp.allCourses')}</option>
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
                {c.status !== 'published' ? ` (${t(`seller.status.${c.status}`)})` : ''}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">{t('ops.cp.f.type')}</legend>
        <div role="radiogroup" aria-label={t('ops.cp.f.type')} className="grid grid-cols-2 gap-2">
          {(['percent', 'fixed'] as const).map((ty) => (
            <button
              key={ty}
              type="button"
              role="radio"
              aria-checked={d.type === ty}
              onClick={() => set('type', ty)}
              className={cn('h-12 rounded-control border text-sm font-semibold transition-colors', d.type === ty ? 'border-primary bg-primary-soft text-primary-fg ring-1 ring-primary' : 'border-border bg-surface hover:border-border-strong')}
            >
              {t(`ops.cp.type.${ty}`)}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="grid grid-cols-2 gap-3">
        <Field label={d.type === 'percent' ? t('ops.cp.f.percent') : t('ops.cp.f.fixed')} error={errors.value}>
          {(p) => <Input {...p} inputMode="decimal" className="latin tnum" value={d.value} placeholder={d.type === 'percent' ? '20' : '50'} onChange={(e) => set('value', e.target.value)} />}
        </Field>
        <Field label={t('ops.cp.f.minAmount')} hint={t('common.optional')} error={errors.minAmount}>
          {(p) => <Input {...p} inputMode="decimal" className="latin tnum" value={d.minAmount} placeholder="0" onChange={(e) => set('minAmount', e.target.value)} />}
        </Field>
        <Field label={t('ops.cp.f.maxUses')} hint={t('ops.cp.f.maxUsesHint')} error={errors.maxUses}>
          {(p) => <Input {...p} inputMode="numeric" className="latin tnum" value={d.maxUses} placeholder="100" onChange={(e) => set('maxUses', e.target.value)} />}
        </Field>
        <Field label={t('ops.cp.f.perUser')} error={errors.perUserLimit}>
          {(p) => <Input {...p} inputMode="numeric" className="latin tnum" value={d.perUserLimit} onChange={(e) => set('perUserLimit', e.target.value)} />}
        </Field>
        <Field label={t('ops.cp.f.starts')} hint={t('common.optional')}>
          {(p) => <Input {...p} type="date" className="latin" min={coupon ? undefined : today} value={d.startsAt} onChange={(e) => set('startsAt', e.target.value)} />}
        </Field>
        <Field label={t('ops.cp.f.expires')} hint={t('common.optional')} error={errors.expiresAt}>
          {(p) => <Input {...p} type="date" className="latin" min={d.startsAt || today} value={d.expiresAt} onChange={(e) => set('expiresAt', e.target.value)} />}
        </Field>
      </div>
      <label className="flex min-h-12 items-center justify-between gap-3 rounded-control border border-border px-4">
        <span className="text-sm font-medium">{t('ops.cp.f.active')}</span>
        <Switch checked={d.active} onCheckedChange={(v) => set('active', v)} label={t('ops.cp.f.active')} />
      </label>
      {serverErr && (
        <p role="alert" className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger-fg">
          {serverErr}
        </p>
      )}
      <div className="flex justify-end gap-2 border-t border-border pt-4">
        <Button variant="secondary" onClick={onDone}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" loading={save.isPending}>
          {coupon ? t('common.save') : t('ops.cp.create')}
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- bundles

function BundleSection({ courses, loading, error, onRetry }: { courses: CourseItem[]; loading: boolean; error: boolean; onRetry: () => void }) {
  const t = useT();
  const published = courses.filter((c) => c.status === 'published');
  const [picked, setPicked] = useState<string[]>([]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [errors, setErrors] = useState<{ courses?: string; title?: string; price?: string }>({});
  const qc = useQueryClient();
  const mine = useQuery({ queryKey: ['seller', 'bundles'], queryFn: () => api(contracts.sellerMoney.bundles) });
  const made = (mine.data?.items ?? []).map((b) => ({ slug: b.slug, title: b.title, price: b.price, sum: b.compareAtPrice ?? b.price }));
  const [serverErr, setServerErr] = useState<string | null>(null);
  const sum = published.filter((c) => picked.includes(c.id)).reduce((a, c) => a + c.price, 0);
  const pricePaisa = takaToPaisa(price);
  const saving = pricePaisa != null && !Number.isNaN(pricePaisa) && pricePaisa < sum ? sum - pricePaisa : 0;
  const create = useMutation({
    mutationFn: () => api(contracts.sellerMoney.createBundle, { body: { title: title.trim(), description: description.trim() || undefined, courseIds: picked, price: pricePaisa ?? 0 } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['seller', 'bundles'] });
      toast.success(t('ops.bd.created'));
      setPicked([]);
      setTitle('');
      setDescription('');
      setPrice('');
    },
    onError: (e) => setServerErr(errorText(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const er: typeof errors = {};
    if (picked.length < 2) er.courses = t('ops.bd.err.courses');
    if (title.trim().length < 5) er.title = t('ops.bd.err.title');
    if (pricePaisa == null || Number.isNaN(pricePaisa)) er.price = t('ops.cp.err.amount');
    else if (pricePaisa >= sum) er.price = t('ops.bd.err.price', { sum: formatTaka(sum, t.locale) });
    setErrors(er);
    setServerErr(null);
    if (!Object.keys(er).length) create.mutate();
  };

  return (
    <section aria-labelledby="bundle-h" className="mt-10">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-control bg-primary-soft text-primary-fg">
          <Package className="size-5" strokeWidth={1.75} aria-hidden />
        </span>
        <div>
          <h2 id="bundle-h" className="text-lg font-semibold">
            {t('ops.bd.title')}
          </h2>
          <p className="mt-0.5 max-w-2xl text-sm text-muted">{t('ops.bd.body')}</p>
        </div>
      </div>

      {made.length > 0 && (
        <ul className="mt-4 flex flex-col gap-2">
          {made.map((b) => (
            <MadeBundle key={b.slug} b={b} />
          ))}
        </ul>
      )}

      <Card className="mt-4 p-4 sm:p-6">
        {loading ? (
          <Skeleton className="h-40 w-full" />
        ) : error ? (
          <ErrorState compact onRetry={onRetry} />
        ) : published.length < 2 ? (
          <EmptyState
            compact
            illustration={<Illustration kind="courses" />}
            title={t('ops.bd.needTwo')}
            body={t('ops.bd.needTwoBody')}
            action={
              <Button asChild variant="secondary">
                <Link to="/teach/courses">{t('seller.nav.courses')}</Link>
              </Button>
            }
          />
        ) : (
          <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
            <fieldset>
              <legend className="mb-2 text-sm font-medium">{t('ops.bd.pick')}</legend>
              <ul className="flex flex-col gap-2" aria-describedby={errors.courses ? 'bd-courses-err' : undefined}>
                {published.map((c) => {
                  const on = picked.includes(c.id);
                  return (
                    <li key={c.id}>
                      <label className={cn('flex min-h-14 cursor-pointer items-center gap-3 rounded-control border px-3 py-2 transition-colors', on ? 'border-primary bg-primary-soft/60' : 'border-border hover:border-border-strong')}>
                        <input type="checkbox" className="size-5 shrink-0 accent-[var(--ss-primary)]" checked={on} onChange={() => setPicked((p) => (on ? p.filter((x) => x !== c.id) : [...p, c.id]))} />
                        <span className="latin min-w-0 flex-1 text-sm font-medium leading-snug">{c.title}</span>
                        <span className="latin shrink-0 text-sm tnum text-muted">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
                      </label>
                    </li>
                  );
                })}
              </ul>
              {errors.courses && (
                <p id="bd-courses-err" role="alert" className="mt-1.5 text-[13px] text-danger-fg">
                  {errors.courses}
                </p>
              )}
            </fieldset>
            <div className="flex flex-col gap-4">
              <Field label={t('ops.bd.name')} error={errors.title}>
                {(p) => <Input {...p} value={title} maxLength={160} className="latin" placeholder="Midterm + Final Combo (CSE-2203)" onChange={(e) => setTitle(e.target.value)} />}
              </Field>
              <Field label={t('ops.bd.desc')} hint={t('common.optional')}>
                {(p) => <Textarea {...p} value={description} maxLength={1000} rows={2} onChange={(e) => setDescription(e.target.value)} />}
              </Field>
              <Field label={t('ops.bd.price')} error={errors.price}>
                {(p) => (
                  <div className="relative">
                    <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted">৳</span>
                    <Input {...p} inputMode="decimal" className="latin pl-8 tnum" value={price} onChange={(e) => setPrice(e.target.value)} />
                  </div>
                )}
              </Field>
              <dl className="rounded-control bg-surface-2 px-4 py-3 text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-muted">{t('ops.bd.sum', { n: localDigits(picked.length, t.locale) })}</dt>
                  <dd className="latin tnum">{formatTaka(sum, t.locale)}</dd>
                </div>
                <div className="mt-1 flex justify-between gap-3 font-semibold">
                  <dt>{t('ops.bd.saving')}</dt>
                  <dd className={cn('latin tnum', saving ? 'text-success-fg' : 'text-muted')}>{saving ? `${formatTaka(saving, t.locale)} (${localDigits(Math.round((saving / sum) * 100), t.locale)}%)` : '—'}</dd>
                </div>
              </dl>
              {serverErr && (
                <p role="alert" className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger-fg">
                  {serverErr}
                </p>
              )}
              <Button type="submit" loading={create.isPending} icon={<Package className="size-4" aria-hidden />}>
                {t('ops.bd.create')}
              </Button>
            </div>
          </form>
        )}
      </Card>
    </section>
  );
}

function MadeBundle({ b }: { b: { slug: string; title: string; price: number; sum: number } }) {
  const t = useT();
  const [copied, setCopied] = useState(false);
  const path = `/checkout/bundle?slug=${encodeURIComponent(b.slug)}`;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}${path}`);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(t('ops.bd.copyFail'));
    }
  };
  return (
    <li className="flex flex-col gap-3 rounded-card border border-success/30 bg-success-soft p-4 sm:flex-row sm:items-center">
      <Ticket className="hidden size-5 shrink-0 text-success-fg sm:block" strokeWidth={1.75} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="latin font-semibold">{b.title}</p>
        <p className="text-sm text-muted">
          <span className="latin tnum font-semibold text-text">{formatTaka(b.price, t.locale)}</span> <s className="latin tnum">{formatTaka(b.sum, t.locale)}</s> · <Link to={path} className="latin break-all text-primary-fg underline-offset-2 hover:underline">{path}</Link>
        </p>
      </div>
      <Button variant="secondary" onClick={() => void copy()} icon={copied ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}>
        {copied ? t('ops.bd.copied') : t('ops.bd.copy')}
      </Button>
    </li>
  );
}
