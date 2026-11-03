import './i18n';
import { useEffect, useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { Button, Card, Chip, EmptyState, ErrorState, Skeleton } from '@/components';
import { ApiError } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';

/** Page title + one-line purpose + optional right-side actions. */
export function AdminHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-2xl font-bold">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function StatusTabs<S extends string>({ value, options, onChange, label, labelKey }: { value: S; options: readonly S[]; onChange: (s: S) => void; label: string; labelKey: (s: S) => string }) {
  const t = useT();
  return (
    <div role="group" aria-label={label} className="-mx-4 flex gap-2 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0">
      {options.map((s) => (
        <Chip key={s} selected={value === s} onClick={() => onChange(s)}>{t(labelKey(s))}</Chip>
      ))}
    </div>
  );
}

/** Loading → error → empty → content, inside one card. */
export function ListBody({ loading, error, empty, onRetry, emptyTitle, emptyBody, emptyIcon, rows = 3, children }: { loading: boolean; error: boolean; empty: boolean; onRetry: () => void; emptyTitle: ReactNode; emptyBody?: ReactNode; emptyIcon: ReactNode; rows?: number; children: ReactNode }) {
  if (loading) return <div className="flex flex-col gap-2 p-4" aria-busy="true">{Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>;
  if (error) return <ErrorState onRetry={onRetry} />;
  if (empty) return <EmptyState illustration={emptyIcon} title={emptyTitle} body={emptyBody} />;
  return <>{children}</>;
}

export function LoadMore({ hasMore, loading, onClick, shown }: { hasMore: boolean; loading: boolean; onClick: () => void; shown: number }) {
  const t = useT();
  return (
    <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-3 text-sm text-muted">
      <span>{t('admin.common.shown', { n: localDigits(shown, t.locale) })}</span>
      {hasMore && <Button variant="secondary" size="sm" loading={loading} onClick={onClick} icon={<ChevronDown className="size-4" aria-hidden />}>{t('admin.common.loadMore')}</Button>}
    </div>
  );
}

export function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

export const errMsg = (e: unknown, fallback: string) => (e instanceof ApiError && e.message ? e.message : fallback);

export const shortId = (id: string) => id.slice(-8).toUpperCase();

export function CardSkeletons({ n = 2, h = 'h-40' }: { n?: number; h?: string }) {
  return <>{Array.from({ length: n }, (_, i) => <Skeleton key={i} className={`${h} rounded-card`} />)}</>;
}

export function CardState({ error, onRetry, emptyTitle, emptyBody, emptyIcon }: { error: boolean; onRetry: () => void; emptyTitle: ReactNode; emptyBody?: ReactNode; emptyIcon: ReactNode }) {
  return <Card>{error ? <ErrorState onRetry={onRetry} /> : <EmptyState illustration={emptyIcon} title={emptyTitle} body={emptyBody} />}</Card>;
}
