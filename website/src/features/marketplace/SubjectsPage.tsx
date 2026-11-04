import './i18n';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { EmptyState, ErrorState, Input, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';

export default function SubjectsPage() {
  const t = useT();
  const [q, setQ] = useState('');
  const list = useQuery({ queryKey: ['subjects', 'all'], queryFn: () => api(contracts.catalog.subjects, { query: {} }), staleTime: 600_000 });
  const groups = useMemo(() => {
    const items = (list.data?.items ?? []).filter((s) => !q || [s.name, s.nameBn ?? '', ...s.aliases].some((x) => x.toLowerCase().includes(q.toLowerCase())));
    const by = new Map<string, typeof items>();
    for (const s of items) by.set(s.category, [...(by.get(s.category) ?? []), s]);
    return [...by.entries()].sort((a, b) => b[1].length - a[1].length);
  }, [list.data, q]);
  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8">
      <h1 className="text-3xl font-bold">{t('subj.title')}</h1>
      <p className="mt-1 text-muted">{t('subj.subtitle')}</p>
      <div className="relative mt-6 max-w-md">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-muted" aria-hidden />
        <label htmlFor="sq" className="sr-only">{t('subj.search')}</label>
        <Input id="sq" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('subj.search')} className="pl-11" />
      </div>
      <div className="mt-8 flex flex-col gap-8">
        {list.isLoading ? <Skeleton className="h-64 rounded-card" /> : list.isError ? <ErrorState onRetry={() => void list.refetch()} /> : groups.length === 0 ? <EmptyState title={t('subj.none')} /> : groups.map(([cat, items]) => (
          <section key={cat} aria-labelledby={`cat-${cat}`}>
            <h2 id={`cat-${cat}`} className="text-sm font-semibold uppercase tracking-wide text-muted">{t(`subj.cat.${cat}`)}</h2>
            <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {items.map((s) => (
                <li key={s.id}>
                  <Link to={`/s/${s.slug}`} className="flex items-center justify-between gap-3 rounded-control border border-border bg-surface px-4 py-3 hover:border-primary">
                    <span className="min-w-0">
                      <span className="latin block truncate font-medium">{s.name}</span>
                      <span className="block truncate text-[13px] text-muted">{[s.nameBn, s.aliases.slice(0, 2).join(', ')].filter(Boolean).join(' · ')}</span>
                    </span>
                    <span className="shrink-0 text-[13px] text-muted">{t('uni.courses', { n: localDigits(s.courseCount, t.locale) })}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </main>
  );
}
