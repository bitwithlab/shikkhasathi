import '../i18n';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CalendarClock, ChevronRight, ClipboardList, DoorOpen } from 'lucide-react';
import { EmptyState, ErrorState, Illustration, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { ClassroomGate } from './ClassroomGate';
import { SyncStatus, dueLabel } from './parts';
import { useClassroomStatus } from './useClassroom';

export default function ClassroomPage() {
  const t = useT();
  const status = useClassroomStatus();
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <header className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">{t('nav.classroom')}</h1>
          <p className="text-sm text-muted">{t('gc.pageSubtitle')}</p>
        </div>
        {status.data?.connected && status.data.status === 'active' && <SyncStatus status={status.data} />}
      </header>
      <ClassroomGate returnTo="/classroom">
        <ClassList />
      </ClassroomGate>
      <p className="mt-8 text-xs text-muted">{t('brand.disclaimerGoogle')}</p>
    </div>
  );
}

function ClassList() {
  const t = useT();
  const q = useQuery({ queryKey: ['classroom', 'courses'], queryFn: () => api(contracts.classroom.courses) });
  if (q.isLoading) return <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-40 rounded-card" />)}</div>;
  if (q.isError) return <ErrorState onRetry={() => void q.refetch()} />;
  if (!q.data?.items.length) return <EmptyState illustration={<Illustration kind="classroom" />} title={t('gc.noClasses')} body={t('gc.noClassesBody')} />;
  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {q.data.items.map((c) => (
        <li key={c.id}>
          <Link to={`/classroom/${encodeURIComponent(c.id)}`} className="group surface-card flex h-full flex-col overflow-hidden transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-e2">
            <div className="relative px-4 pb-3 pt-4" style={{ background: `${c.color}14`, borderBottom: `3px solid ${c.color}` }}>
              <p className="latin line-clamp-2 pr-8 text-[17px] font-semibold leading-snug" style={{ color: c.color }}>
                {c.name}
              </p>
              <p className="latin mt-1 text-[13px] text-muted">{[c.section, c.room].filter(Boolean).join(' · ')}</p>
              {c.unreadCount > 0 && <span className="latin absolute right-3 top-3 min-w-6 rounded-full bg-primary px-1.5 text-center text-xs font-bold leading-6 text-white" aria-label={t('gc.unreadN', { n: c.unreadCount })}>{localDigits(c.unreadCount, t.locale)}</span>}
            </div>
            <div className="flex flex-1 flex-col gap-2 p-4 text-sm">
              {c.teacherNames.length > 0 && (
                <p className="flex items-center gap-2 text-muted">
                  <DoorOpen className="size-4 shrink-0" aria-hidden />
                  <span className="truncate">{c.teacherNames.join(', ')}</span>
                </p>
              )}
              <p className="flex items-center gap-2">
                <ClipboardList className="size-4 shrink-0 text-warning" aria-hidden />
                {c.pendingCount ? t('gc.pendingN', { n: localDigits(c.pendingCount, t.locale) }) : t('gc.noPending')}
              </p>
              {c.nextDueAt && (
                <p className="flex items-center gap-2 text-muted">
                  <CalendarClock className="size-4 shrink-0" aria-hidden /> {t('gc.nextDue', { when: dueLabel(c.nextDueAt, t.locale) ?? '' })}
                </p>
              )}
              <span className="mt-auto inline-flex items-center gap-1 pt-2 text-[13px] font-semibold text-primary-fg">
                {t('gc.openClass')} <ChevronRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />
              </span>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
