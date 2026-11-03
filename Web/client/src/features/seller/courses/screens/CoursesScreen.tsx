import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CourseStatus } from '@shikkhasathi/shared/types';
import { Button, Card, Chip, Dialog, EmptyState, ErrorState, Illustration, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { formatNumber, formatTaka, localDigits, relativeTime, useT } from '@/lib/i18n';
import { Archive, ExternalLink, ListChecks, EyeOff, MessageSquareWarning, PencilLine, Plus, RotateCw, Star, StatusBadge, Trash2, type CourseItem } from '../../parts';

type Filter = 'all' | 'draft' | 'in_review' | 'changes_requested' | 'published' | 'unlisted' | 'archived';
const FILTERS: Filter[] = ['all', 'draft', 'in_review', 'changes_requested', 'published', 'unlisted', 'archived'];
/** "Needs changes" also holds rejected courses. */
const inFilter = (f: Filter, s: CourseStatus) => f === 'all' || s === f || (f === 'changes_requested' && s === 'rejected');

type Pending = { kind: 'delete' | 'archive' | 'unlisted' | 'published'; course: CourseItem } | null;

export default function SellerCoursesPage() {
  const t = useT();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['seller', 'courses'], queryFn: () => api(contracts.sellerCourses.list) });
  const [filter, setFilter] = useState<Filter>('all');
  const [pending, setPending] = useState<Pending>(null);
  const items = useMemo(() => q.data?.items ?? [], [q.data]);
  const counts = useMemo(() => Object.fromEntries(FILTERS.map((f) => [f, items.filter((c) => inFilter(f, c.status)).length])) as Record<Filter, number>, [items]);
  const shown = items.filter((c) => inFilter(filter, c.status));

  const act = useMutation({
    mutationFn: async (p: NonNullable<Pending>) => {
      if (p.kind === 'delete') return api(contracts.sellerCourses.remove, { params: { id: p.course.id } });
      return api(contracts.sellerCourses.archive, { params: { id: p.course.id }, body: { status: p.kind === 'archive' ? 'archived' : p.kind } });
    },
    onSuccess: (_r, p) => {
      toast.success(t(`seller.courses.done.${p.kind}`));
      setPending(null);
      void qc.invalidateQueries({ queryKey: ['seller', 'courses'] });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-12 pt-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{t('seller.nav.courses')}</h1>
          <p className="mt-1 text-sm text-muted">{t('seller.courses.subtitle')}</p>
        </div>
        <Button asChild icon={<Plus className="size-5" aria-hidden />}>
          <Link to="/teach/courses/new">{t('seller.newCourse')}</Link>
        </Button>
      </header>

      <div role="toolbar" aria-label={t('seller.courses.filter')} className="-mx-4 mt-5 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-none">
        {FILTERS.filter((f) => f === 'all' || counts[f] > 0 || f === 'draft' || f === 'published').map((f) => (
          <Chip key={f} selected={filter === f} onClick={() => setFilter(f)} className="h-11">
            {f === 'all' ? t('seller.courses.all') : t(`seller.status.${f}`)}
            <span className="latin text-xs opacity-75 tnum">{localDigits(counts[f], t.locale)}</span>
          </Chip>
        ))}
      </div>

      <Card className="mt-4 overflow-hidden">
        <div className="hidden grid-cols-[minmax(0,1fr)_120px_90px_90px_100px_auto] items-center gap-4 border-b border-border bg-surface-2 px-4 py-2.5 text-xs font-semibold text-muted lg:grid">
          <span>{t('seller.courses.col.course')}</span>
          <span>{t('seller.courses.col.status')}</span>
          <span className="text-right">{t('seller.courses.col.price')}</span>
          <span className="text-right">{t('seller.courses.col.students')}</span>
          <span className="text-right">{t('seller.courses.col.rating')}</span>
          <span className="sr-only">{t('seller.courses.col.actions')}</span>
        </div>
        {q.isLoading ? (
          <ul className="divide-y divide-border">
            {Array.from({ length: 5 }, (_, i) => (
              <li key={i} className="flex items-center gap-3 p-4">
                <Skeleton className="size-12 rounded-control" />
                <div className="flex-1">
                  <Skeleton className="h-4 w-1/2" />
                  <Skeleton className="mt-2 h-3 w-1/4" />
                </div>
                <Skeleton className="hidden h-9 w-28 sm:block" />
              </li>
            ))}
          </ul>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            illustration={<Illustration kind="courses" />}
            title={t('seller.dash.emptyTitle')}
            body={t('seller.dash.emptyBody')}
            action={
              <Button asChild variant="soft" icon={<Plus className="size-4" aria-hidden />}>
                <Link to="/teach/courses/new">{t('seller.dash.emptyCta')}</Link>
              </Button>
            }
          />
        ) : shown.length === 0 ? (
          <EmptyState compact illustration={<Illustration kind="search" />} title={t('seller.courses.noneInFilter')} action={<Button variant="secondary" onClick={() => setFilter('all')}>{t('seller.courses.showAll')}</Button>} />
        ) : (
          <ul className="divide-y divide-border">
            {shown.map((c) => (
              <Row key={c.id} c={c} onAction={(kind) => setPending({ kind, course: c })} />
            ))}
          </ul>
        )}
      </Card>

      <Dialog
        open={!!pending}
        onOpenChange={(o) => !o && setPending(null)}
        title={pending ? t(`seller.courses.confirm.${pending.kind}`) : ''}
        description={pending ? t(`seller.courses.confirm.${pending.kind}.body`, { title: pending.course.title }) : undefined}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPending(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant={pending?.kind === 'delete' ? 'danger' : 'primary'} loading={act.isPending} onClick={() => pending && act.mutate(pending)}>
              {pending ? t(`seller.courses.action.${pending.kind}`) : ''}
            </Button>
          </>
        }
      >
        {null}
      </Dialog>
    </div>
  );
}

function Row({ c, onAction }: { c: CourseItem; onAction: (k: NonNullable<Pending>['kind']) => void }) {
  const t = useT();
  const live = c.status === 'published' || c.status === 'unlisted';
  const editable = c.status === 'draft' || c.status === 'changes_requested' || c.status === 'rejected';
  const iconBtn = 'flex size-11 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text';
  return (
    <li className="grid grid-cols-1 items-center gap-x-4 gap-y-2 p-4 lg:grid-cols-[minmax(0,1fr)_120px_90px_90px_100px_auto]">
      <div className="flex min-w-0 items-center gap-3">
        <span className="latin flex size-12 shrink-0 items-center justify-center rounded-control px-1 text-center text-[10.5px] font-bold leading-tight text-white" style={{ background: c.coverColor ?? '#4F46E5' }} aria-hidden>
          {(c.courseCode ?? '').slice(0, 9) || c.title.slice(0, 2)}
        </span>
        <div className="min-w-0">
          <Link to={`/teach/courses/${c.id}/edit`} className="latin line-clamp-2 font-semibold leading-snug hover:text-primary-fg">
            {c.title}
          </Link>
          <p className="mt-0.5 text-xs text-muted">
            {t('seller.row.lessons', { n: localDigits(c.lessonCount, t.locale) })} · {t('seller.row.updated', { when: relativeTime(c.updatedAt, t.locale) })}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted lg:hidden">
            <StatusBadge status={c.status} />
            <span className="tnum font-semibold text-text">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
            <span>{t('seller.row.students', { n: formatNumber(c.enrollmentCount, t.locale) })}</span>
          </div>
        </div>
      </div>
      <span className="hidden lg:block">
        <StatusBadge status={c.status} />
      </span>
      <span className="hidden text-right text-sm font-semibold tnum lg:block">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
      <span className="hidden text-right text-sm tnum lg:block">{formatNumber(c.enrollmentCount, t.locale)}</span>
      <span className="hidden items-center justify-end gap-1 text-sm lg:flex">
        <Star className="size-4 fill-warning text-warning" aria-hidden />
        <span className="latin tnum">{c.ratingCount ? localDigits(c.ratingAvg.toFixed(1), t.locale) : '—'}</span>
      </span>
      <div className="-mb-1 flex flex-wrap items-center gap-0.5 pl-[60px] lg:mb-0 lg:justify-end lg:pl-0">
        {live && (
          <Link to={`/c/${c.slug}`} className={iconBtn} aria-label={t('seller.row.viewPublic')} title={t('seller.row.viewPublic')}>
            <ExternalLink className="size-[18px]" aria-hidden />
          </Link>
        )}
        <Link to={`/teach/courses/${c.id}/edit?tool=exam`} className={iconBtn} aria-label={t('seller.courses.action.exam')} title={t('seller.courses.action.exam')}>
          <ListChecks className="size-[18px]" aria-hidden />
        </Link>
        {c.status === 'published' && (
          <button type="button" className={iconBtn} onClick={() => onAction('unlisted')} aria-label={t('seller.courses.action.unlisted')} title={t('seller.courses.action.unlisted')}>
            <EyeOff className="size-[18px]" aria-hidden />
          </button>
        )}
        {live && (
          <button type="button" className={iconBtn} onClick={() => onAction('archive')} aria-label={t('seller.courses.action.archive')} title={t('seller.courses.action.archive')}>
            <Archive className="size-[18px]" aria-hidden />
          </button>
        )}
        {(c.status === 'unlisted' || c.status === 'archived') && (
          <button type="button" className={iconBtn} onClick={() => onAction('published')} aria-label={t('seller.courses.action.published')} title={t('seller.courses.action.published')}>
            <RotateCw className="size-[18px]" aria-hidden />
          </button>
        )}
        {editable && (
          <button type="button" className={`${iconBtn} hover:bg-danger-soft hover:text-danger-fg`} onClick={() => onAction('delete')} aria-label={t('seller.courses.action.delete')} title={t('seller.courses.action.delete')}>
            <Trash2 className="size-[18px]" aria-hidden />
          </button>
        )}
        <Link to={`/teach/courses/${c.id}/edit`} className="order-first ml-0 mr-1 inline-flex h-11 items-center gap-1.5 rounded-control border border-border px-3.5 text-sm font-semibold hover:border-border-strong hover:bg-surface-2 lg:order-last lg:ml-1 lg:mr-0">
          <PencilLine className="size-4" aria-hidden /> {t('common.edit')}
        </Link>
      </div>
      {(c.status === 'changes_requested' || c.status === 'rejected') && <ReviewNote id={c.id} />}
    </li>
  );
}

/** The list endpoint has no review note; fetch it only for courses that need changes. */
function ReviewNote({ id }: { id: string }) {
  const t = useT();
  const q = useQuery({ queryKey: ['seller', 'course', id], queryFn: () => api(contracts.sellerCourses.get, { params: { id } }), staleTime: 60_000 });
  if (!q.data?.reviewNote) return null;
  return (
    <div className="col-span-full flex gap-2.5 rounded-control border border-warning/30 bg-warning-soft px-3.5 py-2.5 text-sm text-warning-fg">
      <MessageSquareWarning className="mt-0.5 size-4 shrink-0" aria-hidden />
      <p>
        <span className="font-semibold">{t('seller.courses.reviewerSays')} </span>
        {q.data.reviewNote}
      </p>
    </div>
  );
}
