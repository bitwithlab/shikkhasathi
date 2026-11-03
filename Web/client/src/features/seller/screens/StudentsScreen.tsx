import { useEffect, useState } from 'react';
import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
import type { InferResponse } from '@shikkhasathi/shared';
import { Avatar, Badge, Card, EmptyState, ErrorState, Illustration, Input, ProgressBar, Select, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatNumber, localDigits, relativeTime, useT } from '@/lib/i18n';
import { CheckCircle2, Search } from '../parts';
import { LoadMore, PageHead, pageClass } from '../ops/kit';

type Student = InferResponse<typeof contracts.sellerOps.students>['items'][number];

export default function SellerStudentsScreen() {
  const t = useT();
  const [courseId, setCourseId] = useState('');
  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  useEffect(() => {
    const h = setTimeout(() => setQ(text.trim()), 300);
    return () => clearTimeout(h);
  }, [text]);
  const courses = useQuery({ queryKey: ['seller', 'courses'], queryFn: () => api(contracts.sellerCourses.list) });
  const list = useInfiniteQuery({
    queryKey: ['seller', 'students', courseId, q],
    queryFn: ({ pageParam }) => api(contracts.sellerOps.students, { query: { cursor: pageParam, limit: 30, courseId: courseId || undefined, q: q || undefined } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
    placeholderData: keepPreviousData,
  });
  const rows = list.data?.pages.flatMap((p) => p.items) ?? [];
  const total = list.data?.pages[0]?.total ?? 0;
  const filtered = !!courseId || !!q;

  return (
    <div className={pageClass}>
      <PageHead title={t('seller.nav.students')} subtitle={t('ops.st.subtitle')} />

      <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center">
        <label className="relative min-w-0 flex-1">
          <span className="sr-only">{t('ops.st.search')}</span>
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-[18px] -translate-y-1/2 text-muted" aria-hidden />
          <Input type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder={t('ops.st.search')} className="pl-10" />
        </label>
        <label className="sm:w-80">
          <span className="sr-only">{t('ops.st.course')}</span>
          <Select value={courseId} onChange={(e) => setCourseId(e.target.value)} className="latin">
            <option value="">{t('ops.st.allCourses')}</option>
            {(courses.data?.items ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </Select>
        </label>
      </div>

      <p className="mt-4 text-sm text-muted" aria-live="polite">
        {list.data ? t(filtered ? 'ops.st.countFiltered' : 'ops.st.count', { n: formatNumber(total, t.locale) }) : ' '}
      </p>

      <Card className="mt-2 overflow-hidden">
        <div className="hidden grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_180px_120px] items-center gap-4 border-b border-border bg-surface-2 px-4 py-2.5 text-xs font-semibold text-muted lg:grid">
          <span>{t('ops.st.col.student')}</span>
          <span>{t('ops.st.col.course')}</span>
          <span>{t('ops.st.col.progress')}</span>
          <span className="text-right">{t('ops.st.col.active')}</span>
        </div>
        {list.isLoading ? (
          <ul className="divide-y divide-border" aria-busy>
            {Array.from({ length: 6 }, (_, i) => (
              <li key={i} className="flex items-center gap-3 p-4">
                <Skeleton className="size-10 rounded-full" />
                <div className="flex-1">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="mt-2 h-3 w-1/2" />
                </div>
                <Skeleton className="hidden h-2 w-40 sm:block" />
              </li>
            ))}
          </ul>
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : rows.length === 0 ? (
          filtered ? (
            <EmptyState compact illustration={<Illustration kind="search" />} title={t('ops.st.noMatch')} body={t('ops.st.noMatchBody')} />
          ) : (
            <EmptyState illustration={<Illustration kind="empty" />} title={t('ops.st.emptyTitle')} body={t('ops.st.emptyBody')} />
          )
        ) : (
          <>
            <ul className="divide-y divide-border">
              {rows.map((s) => (
                <Row key={s.enrollmentId} s={s} showCourse={!courseId} />
              ))}
            </ul>
            <LoadMore hasMore={!!list.hasNextPage} loading={list.isFetchingNextPage} onMore={() => void list.fetchNextPage()} label={t('ops.loadMore')} />
          </>
        )}
      </Card>
      <p className="mt-3 text-xs text-muted">{t('ops.st.privacy')}</p>
    </div>
  );
}

function Row({ s, showCourse }: { s: Student; showCourse: boolean }) {
  const t = useT();
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 p-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_180px_120px]">
      <div className="flex min-w-0 items-center gap-3">
        <Avatar name={s.studentName} size={40} />
        <div className="min-w-0">
          <p className="truncate font-semibold">{s.studentName}</p>
          <p className="truncate text-xs text-muted">
            {s.institutionShort ? <span className="latin">{s.institutionShort} · </span> : null}
            {t('ops.st.enrolled', { date: formatDate(s.enrolledAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' }) })}
          </p>
        </div>
      </div>
      <p className="text-right text-xs text-muted lg:order-last">{s.lastAccessedAt ? relativeTime(s.lastAccessedAt, t.locale) : t('ops.st.notStarted')}</p>
      <p className={showCourse ? 'latin col-span-2 line-clamp-1 text-sm lg:col-span-1' : 'hidden lg:block'}>{showCourse ? s.courseTitle : ''}</p>
      <div className="col-span-2 flex items-center gap-3 lg:col-span-1">
        {s.completed ? (
          <Badge tone="success" icon={<CheckCircle2 className="size-3.5" aria-hidden />}>
            {t('ops.st.completed')}
          </Badge>
        ) : (
          <>
            <ProgressBar value={s.progressPercent} className="flex-1" label={t('ops.st.progressOf', { name: s.studentName })} />
            <span className="latin w-10 shrink-0 text-right text-sm font-semibold tnum">{localDigits(s.progressPercent, t.locale)}%</span>
          </>
        )}
      </div>
    </li>
  );
}
