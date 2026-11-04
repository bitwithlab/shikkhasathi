// Shared bits for the Teacher Studio operations screens (earnings, coupons, students, Q&A, reviews,
// analytics). Kept in one module so those lazy screens share a single small chunk.
import './i18n';
import type { ReactNode } from 'react';
import { Button } from '@/components';
import { ApiError } from '@/lib/api';
import { cn } from '@/lib/cn';
import { localDigits, t as translateNow, type Locale } from '@/lib/i18n';
import { Star } from '../parts';

/** Server messages are already friendly Bangla; anything else falls back to the generic copy. */
export function errorText(e: unknown) {
  return e instanceof ApiError && e.status > 0 && e.status < 500 ? e.message : translateNow('state.errorBody');
}

export function PageHead({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-bold">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-sm text-muted">{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}

export const pageClass = 'mx-auto max-w-[1200px] px-4 pb-12 pt-6 lg:px-8';

/** "আরও দেখুন" for cursor lists. */
export function LoadMore({ hasMore, loading, onMore, label }: { hasMore: boolean; loading: boolean; onMore: () => void; label: string }) {
  if (!hasMore) return null;
  return (
    <div className="flex justify-center border-t border-border p-3">
      <Button variant="ghost" loading={loading} onClick={onMore}>
        {label}
      </Button>
    </div>
  );
}

/** Read-only star row with an accessible label. */
export function Stars({ value, locale, size = 'size-4', className }: { value: number; locale: Locale; size?: string; className?: string }) {
  const v = Math.round(value);
  return (
    <span role="img" aria-label={translateNow('ops.stars', { n: localDigits(v, locale) })} className={cn('inline-flex items-center gap-0.5', className)}>
      {Array.from({ length: 5 }, (_, i) => (
        <Star key={i} className={cn(size, i < v ? 'fill-warning text-warning' : 'text-border-strong')} strokeWidth={1.75} aria-hidden />
      ))}
    </span>
  );
}

/** Short Dhaka-date ISO offset helpers for `<input type="date">`. */
export const dhakaDate = (iso: string) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(iso));
export const dateToIso = (d: string, endOfDay = false) => (d ? `${d}T${endOfDay ? '23:59:59' : '00:00:00'}+06:00` : null);
