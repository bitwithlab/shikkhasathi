import { useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Card, Chip, EmptyState, ErrorState, Illustration, ProgressBar, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDate, formatNumber, formatTaka, localDigits, useT } from '@/lib/i18n';
import { GraduationCap, MessageCircleQuestion, Star, StatusBadge, Undo2, Users } from '../parts';
import { BarChart } from '../ops/BarChart';
import { PageHead, pageClass } from '../ops/kit';
import type { CourseStatus } from '@/types';

const RANGES = [7, 30, 90] as const;
type Range = (typeof RANGES)[number];

/** Compact taka for axis labels: whole taka, thousands as "k" (৳১২k). */
function compactTaka(paisa: number, locale: 'bn' | 'en') {
  const taka = paisa / 100;
  const s = taka >= 1000 ? `${Number((taka / 1000).toFixed(taka >= 10_000 ? 1 : 2))}k` : String(Math.round(taka));
  return `৳${localDigits(s, locale)}`;
}

export default function SellerAnalyticsScreen() {
  const t = useT();
  const [days, setDays] = useState<Range>(30);
  const q = useQuery({ queryKey: ['seller', 'analytics', days], queryFn: () => api(contracts.sellerOps.analytics, { query: { days } }), placeholderData: keepPreviousData });
  const d = q.data;
  const label = (iso: string) => formatDate(iso, t.locale, { day: 'numeric', month: 'short' });

  return (
    <div className={pageClass}>
      <PageHead
        title={t('seller.nav.analytics')}
        subtitle={t('ops.an.subtitle')}
        action={
          <div role="radiogroup" aria-label={t('ops.an.range')} className="flex gap-2">
            {RANGES.map((r) => (
              <Chip key={r} role="radio" aria-checked={days === r} aria-pressed={undefined} selected={days === r} onClick={() => setDays(r)} className="h-11">
                {t('ops.an.days', { n: localDigits(r, t.locale) })}
              </Chip>
            ))}
          </div>
        }
      />

      {q.isLoading ? (
        <div aria-busy>
          <Skeleton className="mt-6 h-32 rounded-card" />
          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <Skeleton className="h-64 rounded-card" />
            <Skeleton className="h-64 rounded-card" />
          </div>
        </div>
      ) : q.isError || !d ? (
        <Card className="mt-6">
          <ErrorState onRetry={() => void q.refetch()} />
        </Card>
      ) : (
        <div className={cn('transition-opacity', q.isPlaceholderData && 'opacity-60')}>
          {/* headline band: revenue dominates, the rest are supporting numbers */}
          <section aria-label={t('ops.an.totals')} className="surface-card mt-6 grid gap-px overflow-hidden bg-border lg:grid-cols-[minmax(0,1.4fr)_repeat(4,minmax(0,1fr))]">
            <div className="bg-surface p-5">
              <p className="text-sm text-muted">{t('ops.an.revenue', { n: localDigits(days, t.locale) })}</p>
              <p className="latin mt-1 text-[36px] font-bold leading-none tnum">{formatTaka(d.totals.revenue, t.locale)}</p>
              <p className="mt-2 text-xs text-muted">{t('ops.an.revenueNote')}</p>
            </div>
            <div className="grid grid-cols-2 gap-px bg-border sm:grid-cols-4 lg:contents">
              <Stat icon={Users} label={t('ops.an.enrollments')} value={formatNumber(d.totals.enrollments, t.locale)} sub={t('ops.an.students', { n: formatNumber(d.totals.students, t.locale) })} />
              <Stat icon={GraduationCap} label={t('ops.an.col.completion')} value={`${localDigits(d.totals.avgCompletion, t.locale)}%`} sub={t('ops.an.completionSub')} />
              <Stat icon={Star} label={t('ops.an.rating')} value={d.totals.ratingCount ? localDigits(d.totals.ratingAvg.toFixed(1), t.locale) : '—'} sub={t('ops.an.ratingSub', { n: formatNumber(d.totals.ratingCount, t.locale) })} />
              <Stat icon={Undo2} label={t('ops.an.refunds')} value={formatNumber(d.totals.refunds, t.locale)} sub={t('ops.an.inDays', { n: localDigits(days, t.locale) })} />
            </div>
          </section>

          {d.totals.openQuestions > 0 && (
            <Link to="/teach/qa" className="mt-3 flex min-h-12 items-center gap-2 rounded-control border border-warning/30 bg-warning-soft px-4 text-sm font-medium text-warning-fg hover:brightness-[0.98]">
              <MessageCircleQuestion className="size-4 shrink-0" aria-hidden /> {t('ops.an.openQ', { n: localDigits(d.totals.openQuestions, t.locale) })}
            </Link>
          )}

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <Card className="p-4 sm:p-5">
              <h2 className="mb-4 font-semibold">{t('ops.an.revenueChart')}</h2>
              <BarChart title={t('ops.an.revenueChart')} tableLabels={[t('ops.an.date'), t('ops.an.revenueCol')]} data={d.series.map((s) => ({ label: label(s.date), value: s.revenue }))} format={(v) => formatTaka(v, t.locale)} axisFormat={(v) => compactTaka(v, t.locale)} />
            </Card>
            <Card className="p-4 sm:p-5">
              <h2 className="mb-4 font-semibold">{t('ops.an.enrollChart')}</h2>
              <BarChart title={t('ops.an.enrollChart')} tableLabels={[t('ops.an.date'), t('ops.an.enrollments')]} color="var(--ss-success)" data={d.series.map((s) => ({ label: label(s.date), value: s.enrollments }))} format={(v) => formatNumber(Math.round(v * 10) / 10, t.locale)} />
            </Card>
          </div>

          <section aria-labelledby="per-course" className="mt-8">
            <h2 id="per-course" className="mb-3 text-lg font-semibold">
              {t('ops.an.perCourse')}
            </h2>
            <Card className="overflow-hidden">
              {d.courses.length === 0 ? (
                <EmptyState compact illustration={<Illustration kind="courses" />} title={t('ops.an.noCourses')} />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[640px] text-sm">
                    <thead className="bg-surface-2 text-xs text-muted">
                      <tr>
                        <th scope="col" className="px-4 py-2.5 text-left font-semibold">{t('ops.an.col.course')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right font-semibold">{t('ops.an.col.students')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right font-semibold">{t('ops.an.revenueCol')}</th>
                        <th scope="col" className="w-44 px-4 py-2.5 text-left font-semibold">{t('ops.an.col.completion')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right font-semibold">{t('ops.an.rating')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {[...d.courses]
                        .sort((a, b) => b.revenue - a.revenue)
                        .map((c) => (
                          <tr key={c.id}>
                            <th scope="row" className="px-4 py-3 text-left font-normal">
                              <span className="latin line-clamp-2 font-semibold">{c.title}</span>
                              <span className="mt-1 inline-block">
                                <StatusBadge status={c.status as CourseStatus} />
                              </span>
                            </th>
                            <td className="latin px-4 py-3 text-right tnum">{formatNumber(c.enrollments, t.locale)}</td>
                            <td className="latin px-4 py-3 text-right font-semibold tnum">{formatTaka(c.revenue, t.locale)}</td>
                            <td className="px-4 py-3">
                              <span className="flex items-center gap-2">
                                <ProgressBar value={c.avgCompletion} className="flex-1" label={t('ops.an.col.completion')} />
                                <span className="latin w-9 text-right text-xs tnum">{localDigits(c.avgCompletion, t.locale)}%</span>
                              </span>
                            </td>
                            <td className="latin px-4 py-3 text-right tnum">
                              {c.ratingCount ? (
                                <>
                                  <Star className="mr-1 inline size-3.5 fill-warning text-warning" aria-hidden />
                                  {localDigits(c.ratingAvg.toFixed(1), t.locale)} <span className="text-xs text-muted">({formatNumber(c.ratingCount, t.locale)})</span>
                                </>
                              ) : (
                                '—'
                              )}
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </section>
        </div>
      )}
    </div>
  );
}

function Stat({ icon: Icon, label, value, sub }: { icon: typeof Star; label: string; value: string; sub: string }) {
  return (
    <div className="min-w-0 bg-surface p-4 sm:p-5">
      <p className="flex items-center gap-1.5 text-xs text-muted sm:text-sm">
        <Icon className="size-4 shrink-0" strokeWidth={1.75} aria-hidden />
        <span className="truncate">{label}</span>
      </p>
      <p className="latin mt-1.5 text-2xl font-bold leading-none tnum">{value}</p>
      <p className="mt-1.5 truncate text-xs text-muted">{sub}</p>
    </div>
  );
}
