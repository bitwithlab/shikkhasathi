import { Link } from 'react-router';
import { Field, Input, Switch } from '@/components';
import { formatTaka, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useWizard } from './context';
import { pricingBody, type Pricing } from './model';
import { Block } from './StepBasics';
import { CalendarClock, ChoiceCard, Gift, InfinityIcon, percentOff, Tag, takaToPaisa, Timer } from '../../parts';

export function StepPricing() {
  const t = useT();
  const { pricing: p, setPricing, server } = useWizard();
  const set = <K extends keyof Pricing>(k: K, v: Pricing[K]) => setPricing((x) => ({ ...x, [k]: v }));
  const { errors } = pricingBody(p);
  const price = takaToPaisa(p.price);
  const compare = takaToPaisa(p.compare);
  const valid = (n: number | null): n is number => n != null && !Number.isNaN(n);
  const off = valid(price) && valid(compare) ? percentOff(price, compare) : 0;
  const err = (k: keyof typeof errors) => (errors[k] ? t(errors[k]!) : undefined);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(new Date());

  return (
    <div className="flex flex-col gap-5">
      {server.status === 'published' && <p className="rounded-control border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning-fg">{t('wiz.price.publishedNote')}</p>}
      <Block id="p-price" title={t('wiz.price.title')} body={t('wiz.price.body')}>
        <label className="mb-5 flex min-h-14 cursor-pointer items-center gap-3 rounded-control border border-border px-3 py-2">
          <Gift className="size-5 shrink-0 text-success" strokeWidth={1.75} aria-hidden />
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold">{t('wiz.price.free')}</span>
            <span className="block text-xs text-muted">{t('wiz.price.freeHint')}</span>
          </span>
          <Switch checked={p.free} onCheckedChange={(v) => set('free', v)} label={t('wiz.price.free')} />
        </label>
        <div className={cn('grid gap-4 sm:grid-cols-2', p.free && 'pointer-events-none opacity-50')} aria-disabled={p.free || undefined}>
          <Field label={t('wiz.price.price')} error={err('price')} hint={t('wiz.price.priceHint')}>
            {(f) => (
              <div className="relative">
                <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-lg font-semibold text-muted">৳</span>
                <Input {...f} disabled={p.free} inputMode="decimal" className="latin pl-9 text-lg font-semibold tnum" value={p.price} placeholder="299" onChange={(e) => set('price', e.target.value)} />
              </div>
            )}
          </Field>
          <Field label={t('wiz.price.compare')} error={err('compare')} hint={t('wiz.price.compareHint')}>
            {(f) => (
              <div className="relative">
                <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-lg font-semibold text-muted">৳</span>
                <Input {...f} disabled={p.free} inputMode="decimal" className="latin pl-9 text-lg tnum" value={p.compare} placeholder="499" onChange={(e) => set('compare', e.target.value)} />
              </div>
            )}
          </Field>
        </div>
        <div className="mt-5 flex flex-wrap items-center gap-3 rounded-control bg-surface-2 px-4 py-3" aria-live="polite">
          <Tag className="size-4 text-muted" aria-hidden />
          <span className="text-sm text-muted">{t('wiz.price.studentsSee')}</span>
          <span className="flex items-baseline gap-2">
            {!p.free && off > 0 && <span className="text-sm text-muted line-through tnum">{formatTaka(compare!, t.locale)}</span>}
            <span className="text-xl font-bold tnum">{p.free || price === 0 ? t('course.free') : valid(price) ? formatTaka(price, t.locale) : '—'}</span>
            {!p.free && off > 0 && <span className="rounded-full bg-success-soft px-2 py-0.5 text-xs font-bold text-success-fg">{t('wiz.price.off', { p: localDigits(off, t.locale) })}</span>}
          </span>
        </div>
      </Block>

      <Block id="p-access" title={t('wiz.price.access')} body={t('wiz.price.accessBody')}>
        <div role="radiogroup" aria-label={t('wiz.price.access')} className="grid gap-2 md:grid-cols-3">
          <ChoiceCard selected={p.accessType === 'lifetime'} onClick={() => set('accessType', 'lifetime')} icon={<InfinityIcon className="size-5" aria-hidden />} title={t('wiz.price.lifetime')} body={t('wiz.price.lifetimeHint')} />
          <ChoiceCard selected={p.accessType === 'days'} onClick={() => set('accessType', 'days')} icon={<Timer className="size-5" aria-hidden />} title={t('wiz.price.days')} body={t('wiz.price.daysHint')} />
          <ChoiceCard selected={p.accessType === 'until_exam'} onClick={() => set('accessType', 'until_exam')} icon={<CalendarClock className="size-5" aria-hidden />} title={t('wiz.price.until')} body={t('wiz.price.untilHint')} />
        </div>
        {p.accessType === 'days' && (
          <Field className="mt-4 max-w-xs" label={t('wiz.price.daysN')} error={err('accessDays')}>
            {(f) => <Input {...f} inputMode="numeric" className="latin tnum" value={p.accessDays} placeholder="120" onChange={(e) => set('accessDays', e.target.value.replace(/\D/g, '').slice(0, 4))} />}
          </Field>
        )}
        {p.accessType === 'until_exam' && (
          <Field className="mt-4 max-w-xs" label={t('wiz.price.untilDate')} error={err('accessUntil')}>
            {(f) => <Input {...f} type="date" min={today} className="latin" value={p.accessUntil} onChange={(e) => set('accessUntil', e.target.value)} />}
          </Field>
        )}
      </Block>

      <p className="flex flex-col gap-2 rounded-card border border-dashed border-border-strong px-4 py-3 text-sm text-muted sm:flex-row sm:items-center sm:justify-between">
        <span>{t('wiz.price.couponsSoon')}</span>
        <Link to="/teach/coupons" className="inline-flex h-11 shrink-0 items-center gap-1.5 self-start rounded-control px-2 font-semibold text-primary-fg hover:bg-primary-soft sm:self-auto">
          <Tag className="size-4" aria-hidden /> {t('wiz.price.couponsLink')}
        </Link>
      </p>
    </div>
  );
}
