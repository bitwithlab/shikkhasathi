import './i18n';
import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Bookmark as BookmarkIcon, Clock, NotebookPen, Search } from 'lucide-react';
import { Button, EmptyState, ErrorState, Input, Select, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { fmtTime } from './player/time';

type Tab = 'notes' | 'bookmarks';

export default function NotesPage() {
  const t = useT();
  const [sp, setSp] = useSearchParams();
  const tab: Tab = sp.get('tab') === 'bookmarks' ? 'bookmarks' : 'notes';
  const courseId = sp.get('course') ?? '';
  const [text, setText] = useState('');
  const q = useDeferredValue(text.trim());
  useEffect(() => {
    document.title = `${t('notes.title')} — ${t('brand.name')}`;
  }, [t]);
  const courses = useQuery({
    queryKey: ['my-courses'],
    queryFn: () => api(contracts.learning.myCourses, { query: { filter: 'all' } }),
    staleTime: 60_000,
  });
  const notes = useInfiniteQuery({
    queryKey: ['notes', { courseId, q }],
    queryFn: ({ pageParam }) =>
      api(contracts.learning.notes, {
        query: { limit: 30, cursor: pageParam, courseId: courseId || undefined, q: q || undefined },
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (l) => l.nextCursor ?? undefined,
    enabled: tab === 'notes',
  });
  const bms = useInfiniteQuery({
    queryKey: ['bookmarks', { courseId }],
    queryFn: ({ pageParam }) =>
      api(contracts.learning.bookmarks, {
        query: { limit: 30, cursor: pageParam, courseId: courseId || undefined },
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (l) => l.nextCursor ?? undefined,
    enabled: tab === 'bookmarks',
  });
  const active = tab === 'notes' ? notes : bms;
  const rows = useMemo(
    () =>
      tab === 'notes'
        ? (notes.data?.pages.flatMap((p) => p.items) ?? []).map((n) => ({
            id: n.id,
            courseId: n.courseId,
            lessonId: n.lessonId,
            lessonTitle: n.lessonTitle,
            courseTitle: n.courseTitle,
            ts: n.timestampSec,
            body: n.body as string | null,
            at: n.updatedAt,
          }))
        : (bms.data?.pages.flatMap((p) => p.items) ?? []).map((n) => ({
            id: n.id,
            courseId: n.courseId,
            lessonId: n.lessonId,
            lessonTitle: n.lessonTitle,
            courseTitle: n.courseTitle,
            ts: n.timestampSec,
            body: n.body,
            at: n.createdAt,
          })),
    [tab, notes.data, bms.data],
  );
  const set = (k: string, v: string) =>
    setSp(
      (p) => {
        const n = new URLSearchParams(p);
        if (v) n.set(k, v);
        else n.delete(k);
        return n;
      },
      { replace: true },
    );

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 lg:py-8">
      <header>
        <h1 className="text-2xl font-bold">{t('notes.title')}</h1>
        <p className="text-sm text-muted">{t('notes.subtitle')}</p>
      </header>
      <div role="tablist" className="mt-5 flex gap-1 border-b border-border">
        {(['notes', 'bookmarks'] as const).map((x) => (
          <button
            key={x}
            role="tab"
            type="button"
            aria-selected={tab === x}
            onClick={() => set('tab', x === 'notes' ? '' : x)}
            className={cn(
              '-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-semibold',
              tab === x ? 'border-primary text-text' : 'border-transparent text-muted hover:text-text',
            )}
          >
            {x === 'notes' ? (
              <NotebookPen className="size-4" aria-hidden />
            ) : (
              <BookmarkIcon className="size-4" aria-hidden />
            )}
            {t(`notes.tab.${x}`)}
          </button>
        ))}
      </div>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        {tab === 'notes' && (
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted"
              aria-hidden
            />
            <Input
              type="search"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={t('notes.search')}
              aria-label={t('notes.search')}
              className="pl-10"
            />
          </div>
        )}
        <Select
          value={courseId}
          onChange={(e) => set('course', e.target.value)}
          aria-label={t('notes.allCourses')}
          className="sm:w-64"
        >
          <option value="">{t('notes.allCourses')}</option>
          {courses.data?.items.map((c) => (
            <option key={c.courseId} value={c.courseId}>
              {c.title}
            </option>
          ))}
        </Select>
      </div>
      <div className="mt-5">
        {active.isLoading ? (
          <div className="flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-20 rounded-card" />
            ))}
          </div>
        ) : active.isError ? (
          <ErrorState onRetry={() => void active.refetch()} />
        ) : !rows.length ? (
          q && tab === 'notes' ? (
            <EmptyState compact title={t('notes.noMatch', { q })} />
          ) : (
            <EmptyState
              illustration={
                tab === 'notes' ? (
                  <NotebookPen className="size-10 text-muted" strokeWidth={1.5} aria-hidden />
                ) : (
                  <BookmarkIcon className="size-10 text-muted" strokeWidth={1.5} aria-hidden />
                )
              }
              title={tab === 'notes' ? t('notes.empty') : t('notes.emptyBm')}
              body={tab === 'notes' ? t('notes.emptyBody') : t('notes.emptyBmBody')}
              action={
                <Button asChild variant="secondary">
                  <Link to="/my-courses">{t('my.title')}</Link>
                </Button>
              }
            />
          )
        ) : (
          <ul className="flex flex-col gap-3">
            {rows.map((r) => (
              <li key={r.id} className="surface-card p-4">
                <p className="latin truncate text-xs font-semibold text-muted">{r.courseTitle}</p>
                <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
                  <Link
                    to={`/learn/${r.courseId}/${r.lessonId}${r.ts !== null ? `?t=${r.ts}&tab=notes` : '?tab=notes'}`}
                    className="latin font-semibold hover:underline"
                  >
                    {r.lessonTitle}
                  </Link>
                  {r.ts !== null && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-primary-soft px-2 py-0.5 text-xs font-semibold text-primary-fg tnum">
                      <Clock className="size-3" aria-hidden />
                      {localDigits(fmtTime(r.ts), t.locale)}
                    </span>
                  )}
                </div>
                {r.body && <p className="mt-2 whitespace-pre-wrap break-words text-[15px]">{r.body}</p>}
                <p className="mt-2 text-xs text-muted">{relativeTime(r.at, t.locale)}</p>
              </li>
            ))}
          </ul>
        )}
        {active.hasNextPage && (
          <div className="mt-4 flex justify-center">
            <Button
              variant="secondary"
              onClick={() => void active.fetchNextPage()}
              loading={active.isFetchingNextPage}
            >
              {t('notes.more')}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
