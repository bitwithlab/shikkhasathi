import '../i18n';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import type { ASSIGNMENT_STATUSES } from '@shikkhasathi/shared/types';
import { Chip, EmptyState, ErrorState, Select, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { ClassroomGate } from './ClassroomGate';
import { AssignmentRow, AssignmentSheet } from './parts';

type Filter = 'upcoming' | 'all' | (typeof ASSIGNMENT_STATUSES)[number];
const FILTERS: Filter[] = ['upcoming', 'pending', 'missing', 'turned_in', 'graded', 'all'];

export default function AssignmentsPage() {
  const t = useT();
  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <h1 className="text-2xl font-bold">{t('nav.assignments')}</h1>
      <p className="mb-5 text-sm text-muted">{t('gc.assignmentsSubtitle')}</p>
      <ClassroomGate returnTo="/classroom/assignments">
        <List />
      </ClassroomGate>
    </div>
  );
}

function List() {
  const t = useT();
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') as Filter) ?? 'upcoming';
  const courseId = params.get('course') ?? '';
  const openId = params.get('id');
  const set = (k: string, v: string | null) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    setParams(next, { replace: true });
  };
  const courses = useQuery({ queryKey: ['classroom', 'courses'], queryFn: () => api(contracts.classroom.courses) });
  const q = useQuery({ queryKey: ['classroom', 'assignments', status, courseId], queryFn: () => api(contracts.classroom.assignments, { query: { status, courseId: courseId || undefined } }) });
  // deep link ?id= (from notifications) even if filtered out
  const direct = useQuery({ queryKey: ['classroom', 'assignment', openId], queryFn: () => api(contracts.classroom.assignment, { params: { id: openId! } }), enabled: !!openId });
  const items = useMemo(() => {
    const list = q.data?.items ?? [];
    // upcoming: soonest first; others: most recent due first
    return status === 'upcoming' ? list : [...list].sort((a, b) => (b.dueAt ?? '').localeCompare(a.dueAt ?? ''));
  }, [q.data, status]);

  return (
    <>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="-mx-4 flex flex-1 gap-2 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0" role="group" aria-label={t('gc.filterStatus')}>
          {FILTERS.map((f) => (
            <Chip key={f} selected={status === f} onClick={() => set('status', f === 'upcoming' ? null : f)}>
              {t(`gc.filter.${f}`)}
            </Chip>
          ))}
        </div>
        <label htmlFor="course-f" className="sr-only">{t('gc.filterClass')}</label>
        <Select id="course-f" value={courseId} onChange={(e) => set('course', e.target.value || null)} className="h-10 sm:w-56">
          <option value="">{t('gc.allClasses')}</option>
          {courses.data?.items.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
      </div>
      <div className="mt-4">
        {q.isLoading ? (
          <div className="flex flex-col gap-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[68px]" />)}</div>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState illustration={<ClipboardList className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('gc.noAssignments')} body={status === 'upcoming' ? t('home.allDoneBody') : undefined} />
        ) : (
          <>
            <p className="mb-2 text-sm text-muted">{t('gc.countN', { n: localDigits(items.length, t.locale) })}</p>
            <ul className="flex flex-col gap-2">
              {items.map((a) => (
                <li key={a.id}><AssignmentRow a={a} onOpen={(x) => set('id', x.id)} /></li>
              ))}
            </ul>
          </>
        )}
      </div>
      <AssignmentSheet a={openId ? (items.find((a) => a.id === openId) ?? direct.data ?? null) : null} onClose={() => set('id', null)} />
    </>
  );
}
