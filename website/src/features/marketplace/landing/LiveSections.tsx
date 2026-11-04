import './i18n';
import { useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, BadgeCheck } from 'lucide-react';
import { Avatar, Chip, Skeleton } from '@/components';
import { CourseCard } from '@/components/CourseCard';
import { api, contracts } from '@/lib/api';
import { formatNumber, localDigits, useT } from '@/lib/i18n';

const TABS = [
  { slug: '', key: 'landing.live.all' },
  { slug: 'metropolitan-university', key: 'MU' },
  { slug: 'north-south-university', key: 'NSU' },
  { slug: 'brac-university', key: 'BRACU' },
];

/** "এই সপ্তাহের Midterm Crash Courses" — horizontal carousel with university tabs. */
export function LandingCourses() {
  const t = useT();
  const [tab, setTab] = useState('');
  const insts = useQuery({ queryKey: ['institutions', ''], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), staleTime: 600_000 });
  const instId = tab ? insts.data?.items.find((i) => i.slug === tab)?.id : undefined;
  const q = useQuery({ queryKey: ['featured', instId ?? ''], queryFn: () => api(contracts.discovery.featured, { query: { institutionId: instId } }), enabled: !tab || !!instId, staleTime: 300_000 });
  return (
    <section className="mx-auto max-w-[1200px] px-4 py-16 lg:px-8" aria-labelledby="lc-h">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="lc-h" className="text-[28px] font-bold">{t('landing.live.title')}</h2>
          <p className="mt-1 text-muted">{t('landing.live.body')}</p>
        </div>
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0" role="tablist">
          {TABS.map((x) => <Chip key={x.slug} role="tab" aria-selected={tab === x.slug} selected={tab === x.slug} onClick={() => setTab(x.slug)}><span className="latin">{x.key.startsWith('landing') ? t(x.key) : x.key}</span></Chip>)}
        </div>
      </div>
      <ul className="-mx-4 mt-6 flex snap-x gap-4 overflow-x-auto px-4 pb-2 scrollbar-none lg:mx-0 lg:grid lg:grid-cols-4 lg:overflow-visible lg:px-0">
        {q.isLoading || !q.data
          ? Array.from({ length: 4 }, (_, i) => <li key={i} className="w-[78%] shrink-0 sm:w-[45%] lg:w-auto"><Skeleton className="aspect-[4/5] rounded-card" /></li>)
          : q.data.courses.slice(0, 8).map((c) => (
              <li key={c.id} className="w-[78%] shrink-0 snap-start sm:w-[45%] lg:w-auto"><CourseCard c={c} /></li>
            ))}
      </ul>
      <Link to="/explore" className="mt-6 inline-flex items-center gap-1.5 font-semibold text-primary-fg hover:underline">{t('landing.live.all2')} <ArrowRight className="size-4" aria-hidden /></Link>
    </section>
  );
}

export function LandingTeachers() {
  const t = useT();
  const q = useQuery({ queryKey: ['featured', ''], queryFn: () => api(contracts.discovery.featured, { query: {} }), staleTime: 300_000 });
  if (q.isError || (q.data && q.data.teachers.length === 0)) return null;
  return (
    <section className="border-y border-border bg-surface" aria-labelledby="lt-h">
      <div className="mx-auto max-w-[1200px] px-4 py-16 lg:px-8">
        <h2 id="lt-h" className="text-[28px] font-bold">{t('landing.teachers.title')}</h2>
        <p className="mt-1 text-muted">{t('landing.teachers.body')}</p>
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {(q.data?.teachers ?? Array.from({ length: 4 }, () => null)).map((x, i) =>
            x ? (
              <li key={x.slug}>
                <Link to={`/t/${x.slug}`} className="flex h-full flex-col rounded-card border border-border bg-bg p-4 hover:border-border-strong">
                  <div className="flex items-center gap-3">
                    <Avatar name={x.name} size={48} />
                    <div className="min-w-0">
                      <p className="flex items-center gap-1 font-semibold">{x.name}<BadgeCheck className="size-4 shrink-0 text-success" aria-label={t('course.verified')} /></p>
                      <p className="latin truncate text-[13px] text-muted">{x.headline}</p>
                    </div>
                  </div>
                  <p className="latin mt-3 text-[13px] text-muted">{x.institutionName}</p>
                  <dl className="mt-3 grid grid-cols-3 gap-2 border-t border-border pt-3 text-center">
                    <div><dt className="text-[11px] text-muted">{t('store.rating')}</dt><dd className="font-semibold tnum">{localDigits(x.ratingAvg.toFixed(1), t.locale)}</dd></div>
                    <div><dt className="text-[11px] text-muted">{t('store.students')}</dt><dd className="font-semibold tnum">{formatNumber(x.studentCount, t.locale)}</dd></div>
                    <div><dt className="text-[11px] text-muted">{t('store.courses')}</dt><dd className="font-semibold tnum">{localDigits(x.courseCount, t.locale)}</dd></div>
                  </dl>
                </Link>
              </li>
            ) : (
              <li key={i}><Skeleton className="h-44 rounded-card" /></li>
            ),
          )}
        </ul>
      </div>
    </section>
  );
}
