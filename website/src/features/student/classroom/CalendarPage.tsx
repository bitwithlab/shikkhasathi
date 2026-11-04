import '../i18n';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, ClipboardList, Timer } from 'lucide-react';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatTime, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { StatusChip } from './parts';

const dhakaDay = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);

/** Month grid (Sat-first, as in Bangladesh) with dots for deadlines (red) and exams (amber). */
export default function CalendarPage() {
  const t = useT();
  const [params, setParams] = useSearchParams();
  const today = dhakaDay(new Date());
  const selected = params.get('day') ?? today;
  const [month, setMonth] = useState(() => selected.slice(0, 7)); // YYYY-MM
  const [y, m] = month.split('-').map(Number) as [number, number];
  const first = new Date(`${month}-01T00:00:00+06:00`);
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 1) % 7; // 0 = Saturday
  const from = new Date(first.getTime() - lead * 86_400_000);
  const to = new Date(first.getTime() + (daysInMonth + 14) * 86_400_000);
  const q = useQuery({ queryKey: ['classroom', 'calendar', month], queryFn: () => api(contracts.classroom.calendar, { query: { from: from.toISOString(), to: to.toISOString() } }) });
  const events = q.data?.items;
  const byDay = useMemo(() => {
    const map = new Map<string, NonNullable<typeof events>>();
    for (const e of events ?? []) {
      const k = dhakaDay(new Date(e.at));
      map.set(k, [...(map.get(k) ?? []), e]);
    }
    return map;
  }, [events]);
  const cells = Array.from({ length: Math.ceil((lead + daysInMonth) / 7) * 7 }, (_, i) => dhakaDay(new Date(from.getTime() + i * 86_400_000 + 6 * 3_600_000)));
  const shift = (n: number) => {
    const d = new Date(Date.UTC(y, m - 1 + n, 1));
    setMonth(`${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`);
  };
  const weekdays = Array.from({ length: 7 }, (_, i) => formatDate(new Date(Date.UTC(2026, 0, 3 + i, 6)), t.locale, { weekday: 'short' })); // 3 Jan 2026 = Saturday
  const dayEvents = byDay.get(selected) ?? [];

  return (
    <div className="mx-auto max-w-[1100px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('nav.calendar')}</h1>
      <div className="mt-5 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <section className="surface-card p-4" aria-label={t('nav.calendar')}>
          <div className="mb-3 flex items-center justify-between">
            <Button variant="ghost" size="sm" onClick={() => shift(-1)} aria-label={t('cal.prev')} icon={<ChevronLeft className="size-5" aria-hidden />} />
            <h2 className="text-lg font-semibold">{formatDate(first, t.locale, { month: 'long', year: 'numeric' })}</h2>
            <Button variant="ghost" size="sm" onClick={() => shift(1)} aria-label={t('cal.next')} icon={<ChevronRight className="size-5" aria-hidden />} />
          </div>
          {q.isError ? (
            <ErrorState compact onRetry={() => void q.refetch()} />
          ) : (
            <div role="grid" className="grid grid-cols-7 gap-1">
              {weekdays.map((w) => <div key={w} role="columnheader" className="py-1 text-center text-xs font-medium text-muted">{w}</div>)}
              {cells.map((d) => {
                const ev = byDay.get(d) ?? [];
                const inMonth = d.startsWith(month);
                const deadlines = ev.filter((e) => e.kind === 'assignment').length;
                const exams = ev.filter((e) => e.kind === 'exam').length;
                return (
                  <button
                    key={d}
                    role="gridcell"
                    type="button"
                    aria-selected={d === selected}
                    aria-label={`${formatDate(`${d}T12:00:00+06:00`, t.locale, { day: 'numeric', month: 'long' })}: ${t('week.summary', { d: localDigits(deadlines, t.locale), e: localDigits(exams, t.locale) })}`}
                    onClick={() => setParams({ day: d }, { replace: true })}
                    className={cn('flex aspect-square min-h-11 flex-col items-center justify-center gap-1 rounded-control text-sm transition-colors', d === selected ? 'bg-primary text-on-primary' : d === today ? 'bg-primary-soft font-semibold text-primary-fg' : 'hover:bg-surface-2', !inMonth && d !== selected && 'text-muted/50')}
                  >
                    <span className="tnum">{localDigits(Number(d.slice(8)), t.locale)}</span>
                    <span className="flex h-1.5 gap-0.5" aria-hidden>
                      {q.isLoading ? null : (
                        <>
                          {deadlines > 0 && <span className={cn('size-1.5 rounded-full', d === selected ? 'bg-white' : 'bg-danger')} />}
                          {deadlines > 1 && <span className={cn('size-1.5 rounded-full', d === selected ? 'bg-white' : 'bg-danger')} />}
                          {exams > 0 && <span className={cn('size-1.5 rounded-full', d === selected ? 'bg-white' : 'bg-warning')} />}
                        </>
                      )}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>
        <section aria-labelledby="day-h">
          <h2 id="day-h" className="mb-3 font-semibold">{formatDate(`${selected}T12:00:00+06:00`, t.locale, { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
          {q.isLoading ? (
            <Skeleton className="h-24 rounded-card" />
          ) : dayEvents.length === 0 ? (
            <EmptyState compact title={t('cal.free')} />
          ) : (
            <ul className="flex flex-col gap-2">
              {dayEvents.map((e) => (
                <li key={`${e.kind}-${e.id}`}>
                  <Link to={e.link} className="flex items-center gap-3 rounded-control border border-border bg-surface p-3 hover:border-border-strong">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-control" style={{ background: `${e.color}1a`, color: e.color }}>
                      {e.kind === 'exam' ? <Timer className="size-[18px]" aria-hidden /> : <ClipboardList className="size-[18px]" aria-hidden />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="latin block truncate text-sm font-medium">{e.title}</span>
                      <span className="latin block truncate text-xs text-muted">{formatTime(e.at, t.locale)}{e.courseName ? ` · ${e.courseName}` : ''}</span>
                    </span>
                    {e.status && <StatusChip status={e.status} />}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
