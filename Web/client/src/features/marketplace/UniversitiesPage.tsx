import './i18n';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { MapPin, Search } from 'lucide-react';
import { INSTITUTION_TYPES } from '@shikkhasathi/shared/types';
import { Chip, EmptyState, ErrorState, Input, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';

export default function UniversitiesPage() {
  const t = useT();
  const [q, setQ] = useState('');
  const [d, setD] = useState('');
  const [type, setType] = useState<(typeof INSTITUTION_TYPES)[number] | undefined>();
  useEffect(() => {
    const h = setTimeout(() => setD(q.trim()), 200);
    return () => clearTimeout(h);
  }, [q]);
  const list = useQuery({ queryKey: ['institutions', d, type], queryFn: () => api(contracts.catalog.institutions, { query: { q: d || undefined, type, limit: 100 } }), placeholderData: (p) => p });
  const items = [...(list.data?.items ?? [])].sort((a, b) => b.courseCount - a.courseCount || a.name.localeCompare(b.name));
  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8">
      <h1 className="text-3xl font-bold">{t('uni.title')}</h1>
      <p className="mt-1 text-muted">{t('uni.subtitle')}</p>
      <div className="mt-6 flex flex-col gap-3 md:flex-row md:items-center">
        <div className="relative md:w-96">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-muted" aria-hidden />
          <label htmlFor="uq" className="sr-only">{t('uni.search')}</label>
          <Input id="uq" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('uni.search')} className="pl-11" />
        </div>
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 scrollbar-none md:mx-0 md:px-0">
          <Chip selected={!type} onClick={() => setType(undefined)}>{t('uni.all')}</Chip>
          {INSTITUTION_TYPES.map((x) => <Chip key={x} selected={type === x} onClick={() => setType(x)}>{t(`instType.${x}`)}</Chip>)}
        </div>
      </div>
      <div className="mt-6">
        {list.isLoading ? (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <li key={i}><Skeleton className="h-32 rounded-card" /></li>)}</ul>
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : !items.length ? (
          <EmptyState title={t('uni.none')} action={<Link to="/onboarding" className="text-sm font-semibold text-primary-fg hover:underline">{t('onb.uni.notListed')}</Link>} />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 [&>*]:min-w-0">
            {items.map((i) => (
              <li key={i.id}>
                <Link to={`/u/${i.slug}`} className="surface-card flex h-full gap-4 p-4 transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-e2">
                  <span className="latin flex size-14 shrink-0 items-center justify-center rounded-control bg-primary-soft text-[15px] font-bold text-primary-fg">{i.shortName.split(/\s+/)[0]!.slice(0, 5)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold leading-snug">{t.locale === 'bn' ? (i.nameBn ?? i.name) : i.name}</span>
                    {t.locale === 'bn' && <span className="latin block truncate text-[13px] text-muted">{i.name}</span>}
                    <span className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-muted">
                      {i.city && <span className="inline-flex items-center gap-1"><MapPin className="size-3.5" aria-hidden />{i.city}</span>}
                      <span>{t('uni.courses', { n: localDigits(i.courseCount, t.locale) })}</span>
                      <span>{t('uni.teachers', { n: localDigits(i.teacherCount, t.locale) })}</span>
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="mt-10 text-xs text-muted">{t('brand.disclaimerUni')}</p>
    </main>
  );
}
