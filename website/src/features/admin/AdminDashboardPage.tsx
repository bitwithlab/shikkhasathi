import './i18n';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import type { AdminAnalytics } from '@/api';
import { ArrowRight, BadgeCheck, Building2, CheckCircle2, Flag, HeartPulse, Inbox, Settings, Trophy, Undo2, Wallet } from 'lucide-react';
import { Avatar, Card, Chip, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatNumber, formatTaka, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { AdminHeader } from './shared';

const PERIODS = [7, 30, 90] as const;
type Period = (typeof PERIODS)[number];
type Metric = 'gmv' | 'orders' | 'signups';

const QUEUES = [
  { key: 'pendingSellers', to: '/admin/sellers', icon: BadgeCheck },
  { key: 'pendingCourses', to: '/admin/courses/review', icon: Inbox },
  { key: 'pendingInstitutions', to: '/admin/institutions', icon: Building2 },
  { key: 'openReports', to: '/admin/reports', icon: Flag },
  { key: 'refundRequests', to: '/admin/refunds', icon: Undo2 },
  { key: 'payoutRequests', to: '/admin/payouts', icon: Wallet },
] as const;

export default function AdminDashboardPage() {
  const t = useT();
  const [days, setDays] = useState<Period>(30);
  const q = useQuery({ queryKey: ['admin', 'analytics', days], queryFn: () => api(contracts.adminOps.analytics, { query: { days } }), placeholderData: (p) => p });
  const d = q.data;
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader
        title={t('admin.nav.dashboard')}
        subtitle={t('admin.dash.subtitle')}
        actions={
          <div role="group" aria-label={t('admin.dash.period')} className="flex gap-2">
            {PERIODS.map((p) => (
              <Chip key={p} selected={days === p} onClick={() => setDays(p)}>{t('admin.dash.days', { n: localDigits(p, t.locale) })}</Chip>
            ))}
          </div>
        }
      />

      {q.isError && !d ? (
        <Card className="mt-6"><ErrorState onRetry={() => void q.refetch()} /></Card>
      ) : (
        // DOM order = mobile order (KPIs → work queue → chart → top lists); on desktop the queue sits in the right column.
        <div className={cn('mt-6 grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_340px]', q.isFetching && d && 'opacity-70 transition-opacity')}>
          <div className="min-w-0 lg:col-start-1 lg:row-start-1"><Kpis d={d} /></div>
          <div className="flex min-w-0 flex-col gap-5 lg:col-start-2 lg:row-span-2 lg:row-start-1">
            <QueueCard d={d} />
            <nav aria-label={t('admin.dash.system')} className="grid grid-cols-2 gap-3">
              <SystemLink to="/admin/settings" icon={<Settings className="size-4" aria-hidden />} label={t('admin.nav.settings')} />
              <SystemLink to="/admin/health" icon={<HeartPulse className="size-4" aria-hidden />} label={t('admin.health.title')} />
            </nav>
          </div>
          <div className="min-w-0 lg:col-start-1 lg:row-start-2"><SeriesCard d={d} /></div>
          <div className="min-w-0 lg:col-start-1"><TopCourses d={d} /></div>
          <div className="min-w-0 lg:col-start-2"><TopSellers d={d} /></div>
        </div>
      )}
    </div>
  );
}

function SystemLink({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  return (
    <Link to={to} className="flex min-h-11 items-center gap-2 rounded-control border border-border bg-surface px-3 py-2 text-sm font-medium text-text transition-colors hover:border-border-strong hover:bg-surface-2">
      <span className="text-muted">{icon}</span>
      {label}
    </Link>
  );
}

function Kpis({ d }: { d?: AdminAnalytics }) {
  const t = useT();
  if (!d) return <Skeleton className="h-44 rounded-card" />;
  const n = (v: number) => formatNumber(v, t.locale);
  const small = [
    { k: 'orders', v: n(d.totals.orders) },
    { k: 'enrollments', v: n(d.totals.enrollments) },
    { k: 'newStudents', v: n(d.totals.newStudents) },
    { k: 'newSellers', v: n(d.totals.newSellers) },
    { k: 'activeStudents7d', v: n(d.totals.activeStudents7d) },
    { k: 'classroomConnected', v: n(d.totals.classroomConnected) },
  ];
  return (
    <Card className="overflow-hidden">
      <div className="flex flex-col gap-4 border-b border-border p-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted">{t('admin.dash.k.gmv')}</p>
          <p className="mt-0.5 text-4xl font-bold tnum">{formatTaka(d.totals.gmv, t.locale)}</p>
        </div>
        <dl className="flex gap-6 text-sm">
          <div>
            <dt className="text-muted">{t('admin.dash.k.platformRevenue')}</dt>
            <dd className="text-lg font-semibold tnum">{formatTaka(d.totals.platformRevenue, t.locale)}</dd>
          </div>
          <div>
            <dt className="text-muted">{t('admin.dash.k.refunds')}</dt>
            <dd className="text-lg font-semibold tnum">
              {n(d.totals.refunds)} <span className="text-sm font-normal text-muted">· {formatTaka(d.totals.refundAmount, t.locale)}</span>
            </dd>
          </div>
        </dl>
      </div>
      <dl className="grid grid-cols-2 gap-px bg-border sm:grid-cols-3">
        {small.map((s) => (
          <div key={s.k} className="bg-surface px-5 py-3.5">
            <dt className="text-xs text-muted">{t(`admin.dash.k.${s.k}`)}</dt>
            <dd className="text-xl font-semibold tnum">{s.v}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

function QueueCard({ d }: { d?: AdminAnalytics }) {
  const t = useT();
  const total = d ? QUEUES.reduce((s, q) => s + d.queues[q.key], 0) : 0;
  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between px-4 pb-2 pt-4">
        <h2 className="font-semibold">{t('admin.dash.queues')}</h2>
        {d && <span className="text-sm text-muted">{total ? t('admin.dash.queueTotal', { n: localDigits(total, t.locale) }) : ''}</span>}
      </div>
      {!d ? (
        <div className="flex flex-col gap-2 p-4 pt-1">{QUEUES.map((q) => <Skeleton key={q.key} className="h-11" />)}</div>
      ) : (
        <>
          {total === 0 && (
            <p className="mx-4 mb-2 flex items-center gap-2 rounded-control bg-success-soft px-3 py-2 text-sm text-success-fg">
              <CheckCircle2 className="size-4" aria-hidden /> {t('admin.dash.allClear')}
            </p>
          )}
          <ul className="flex flex-col pb-2">
            {QUEUES.map(({ key, to, icon: Icon }) => {
              const v = d.queues[key];
              return (
                <li key={key}>
                  <Link to={to} className="group flex min-h-11 items-center gap-3 px-4 py-2 transition-colors hover:bg-surface-2">
                    <Icon className={cn('size-4 shrink-0', v ? 'text-warning-fg' : 'text-muted')} aria-hidden />
                    <span className={cn('flex-1 text-sm', !v && 'text-muted')}>{t(`admin.dash.q.${key}`)}</span>
                    <span className={cn('min-w-8 rounded-full px-2 py-0.5 text-center text-sm font-semibold tnum', v ? 'bg-warning-soft text-warning-fg' : 'text-muted')}>{localDigits(v, t.locale)}</span>
                    <ArrowRight className="size-4 text-muted opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100" aria-hidden />
                  </Link>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Card>
  );
}

function SeriesCard({ d }: { d?: AdminAnalytics }) {
  const t = useT();
  const [metric, setMetric] = useState<Metric>('gmv');
  const fmt = (v: number) => (metric === 'gmv' ? formatTaka(v, t.locale) : formatNumber(v, t.locale));
  const total = d?.series.reduce((s, p) => s + p[metric], 0) ?? 0;
  return (
    <Card className="p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="font-semibold">{t(`admin.dash.m.${metric}`)}</h2>
          <p className="text-sm text-muted tnum">{d ? t('admin.dash.periodTotal', { v: fmt(total) }) : ' '}</p>
        </div>
        <div role="group" aria-label={t('admin.dash.metric')} className="flex gap-1 rounded-control bg-surface-2 p-1">
          {(['gmv', 'orders', 'signups'] as const).map((m) => (
            <button key={m} type="button" aria-pressed={metric === m} onClick={() => setMetric(m)} className={cn('h-8 rounded-[9px] px-3 text-sm font-medium transition-colors', metric === m ? 'bg-surface text-text shadow-e1' : 'text-muted hover:text-text')}>
              {t(`admin.dash.m.${m}`)}
            </button>
          ))}
        </div>
      </div>
      {d ? <BarChart series={d.series} metric={metric} fmt={fmt} /> : <Skeleton className="mt-4 h-48" />}
    </Card>
  );
}

/** 1/2/2.5/5 × 10ⁿ ceiling, so axis ticks read as round numbers. */
function niceCeil(v: number) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const m = v / p;
  return ([1, 2, 2.5, 5, 10].find((s) => m <= s) ?? 10) * p;
}

/** Single-series bar chart (HTML/CSS bars, crisp at any width). Hover a day to read it; a visually hidden table mirrors the data. */
function BarChart({ series, metric, fmt }: { series: AdminAnalytics['series']; metric: Metric; fmt: (v: number) => string }) {
  const t = useT();
  const [hover, setHover] = useState<number | null>(null);
  const max = useMemo(() => {
    const raw = Math.max(0, ...series.map((p) => p[metric]));
    return metric === 'gmv' ? niceCeil(raw / 100) * 100 : Math.max(2, niceCeil(raw));
  }, [series, metric]);
  const n = series.length;
  const labelEvery = n <= 7 ? 1 : n <= 31 ? 7 : 15;
  const allZero = series.every((p) => p[metric] === 0);
  const h = hover !== null ? series[hover] : null;
  return (
    <div className="relative mt-4">
      <div className="pointer-events-none absolute right-0 top-0 z-10 text-right text-sm" aria-live="polite">
        {h && (
          <div className="rounded-control border border-border bg-surface px-3 py-1.5 shadow-e2">
            <p className="text-xs text-muted">{formatDate(h.date, t.locale, { day: 'numeric', month: 'short', weekday: 'short' })}</p>
            <p className="font-semibold tnum">{fmt(h[metric])}</p>
          </div>
        )}
      </div>
      <div className="relative h-44 sm:h-52" aria-hidden onMouseLeave={() => setHover(null)}>
        {[1, 0.5].map((tk) => (
          <div key={tk} className="absolute inset-x-0 border-t border-dashed border-border" style={{ top: `${(1 - tk) * 100}%` }}>
            <span className="absolute left-0 top-0.5 text-[11px] leading-none text-muted tnum">{fmt(max * tk)}</span>
          </div>
        ))}
        <div className="absolute inset-0 flex items-end gap-px border-b border-border sm:gap-0.5">
          {series.map((p, i) => {
            const v = p[metric];
            return (
              <div key={p.date} className={cn('flex h-full flex-1 items-end justify-center rounded-t-[3px] transition-colors', hover === i && 'bg-surface-2')} onMouseEnter={() => setHover(i)}>
                {v > 0 && <div className={cn('w-full max-w-[18px] rounded-t-[4px] bg-primary transition-opacity', hover !== null && hover !== i && 'opacity-55')} style={{ height: `${Math.max(2, (v / max) * 100)}%` }} />}
              </div>
            );
          })}
        </div>
        {allZero && <p className="absolute inset-x-0 top-1/3 text-center text-sm text-muted">{t('admin.dash.noData')}</p>}
      </div>
      <div className="relative mt-1.5 h-4 text-[11px] text-muted" aria-hidden>
        {series.map((p, i) =>
          i % labelEvery === 0 || i === n - 1 ? (
            <span key={p.date} className={cn('absolute whitespace-nowrap', i === n - 1 ? '-translate-x-full' : i === 0 ? '' : '-translate-x-1/2', i !== n - 1 && n - 1 - i < labelEvery / 2 && 'hidden')} style={{ left: i === n - 1 ? '100%' : `${((i + 0.5) / n) * 100}%` }}>
              {formatDate(p.date, t.locale)}
            </span>
          ) : null,
        )}
      </div>
      <table className="sr-only">
        <caption>{t(`admin.dash.m.${metric}`)}</caption>
        <tbody>
          {series.map((p) => (
            <tr key={p.date}><th scope="row">{formatDate(p.date, t.locale)}</th><td>{fmt(p[metric])}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TopCourses({ d }: { d?: AdminAnalytics }) {
  const t = useT();
  return (
    <Card className="overflow-hidden">
      <h2 className="flex items-center gap-2 px-5 pb-2 pt-4 font-semibold"><Trophy className="size-4 text-warning" aria-hidden />{t('admin.dash.topCourses')}</h2>
      {!d ? (
        <div className="flex flex-col gap-2 p-4 pt-1">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-10" />)}</div>
      ) : !d.topCourses.length ? (
        <p className="px-5 pb-5 text-sm text-muted">{t('admin.dash.noSales')}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted">
              <tr className="border-b border-border">
                <th className="w-8 px-5 py-2 font-medium">#</th>
                <th className="px-2 py-2 font-medium">{t('admin.review.course')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('admin.dash.sales')}</th>
                <th className="px-5 py-2 text-right font-medium">{t('admin.dash.revenue')}</th>
              </tr>
            </thead>
            <tbody>
              {d.topCourses.map((c, i) => (
                <tr key={c.id} className="border-b border-border last:border-0">
                  <td className="px-5 py-2.5 text-muted tnum">{localDigits(i + 1, t.locale)}</td>
                  <td className="px-2 py-2.5">
                    <p className="latin font-medium">{c.title}</p>
                    <p className="text-xs text-muted">{c.sellerName}</p>
                  </td>
                  <td className="px-3 py-2.5 text-right tnum">{formatNumber(c.sales, t.locale)}</td>
                  <td className="px-5 py-2.5 text-right font-semibold tnum">{formatTaka(c.revenue, t.locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

function TopSellers({ d }: { d?: AdminAnalytics }) {
  const t = useT();
  const max = d ? Math.max(1, ...d.topSellers.map((s) => s.revenue)) : 1;
  return (
    <Card className="p-5">
      <h2 className="font-semibold">{t('admin.dash.topSellers')}</h2>
      {!d ? (
        <div className="mt-3 flex flex-col gap-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-10" />)}</div>
      ) : !d.topSellers.length ? (
        <p className="mt-2 text-sm text-muted">{t('admin.dash.noSales')}</p>
      ) : (
        <ol className="mt-3 flex flex-col gap-3">
          {d.topSellers.map((s) => (
            <li key={s.id} className="flex items-center gap-3">
              <Avatar name={s.name} size={32} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2 text-sm">
                  <span className="truncate font-medium">{s.name}</span>
                  <span className="shrink-0 font-semibold tnum">{formatTaka(s.revenue, t.locale)}</span>
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-2" aria-hidden>
                    <span className="block h-full rounded-full bg-success" style={{ width: `${(s.revenue / max) * 100}%` }} />
                  </span>
                  <span className="text-xs text-muted">{t('admin.dash.students', { n: formatNumber(s.students, t.locale) })}</span>
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
