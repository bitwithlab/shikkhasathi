import './i18n';
import { lazy, Suspense, useMemo, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { ChevronDown, Megaphone, RotateCcw, SlidersHorizontal, X } from 'lucide-react';
import { COURSE_TYPES } from '@shikkhasathi/shared/types';
import { Button, EmptyState, ErrorState, Illustration, Select, Skeleton } from '@/components';
import { CourseCard } from '@/components/CourseCard';
import { api, contracts } from '@/lib/api';
import { localDigits, ordinal, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { SearchBox } from './SearchBox';

const Sheet = lazy(() => import('@/components/Overlay').then((m) => ({ default: m.Sheet })));
const FILTER_KEYS = ['institutionId', 'departmentId', 'semester', 'subjectId', 'courseCode', 'examType', 'courseType', 'price', 'language', 'rating', 'duration'] as const;
const SORTS = ['best', 'newest', 'rating', 'price_asc', 'price_desc', 'popular'] as const;

export default function ExplorePage() {
  const t = useT();
  const [params, setParams] = useSearchParams();
  const [sheet, setSheet] = useState(false);
  const q = params.get('q') ?? '';
  const sort = (params.get('sort') as (typeof SORTS)[number]) ?? 'best';
  const filters = useMemo(() => Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? undefined]).filter(([, v]) => v)) as Record<(typeof FILTER_KEYS)[number], string | undefined>, [params]);
  const set = (k: string, v: string | null | undefined) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k === 'institutionId') next.delete('departmentId');
    setParams(next, { replace: true });
  };
  const clearAll = () => setParams(q ? { q } : {}, { replace: true });

  const query = {
    q: q || undefined,
    ...filters,
    semester: filters.semester ? Number(filters.semester) : undefined,
    rating: filters.rating ? Number(filters.rating) : undefined,
    sort,
    limit: 24,
  } as Parameters<typeof api<typeof contracts.discovery.search>>[1] extends { query?: infer Q } ? Q : never;

  const res = useInfiniteQuery({
    queryKey: ['search', q, filters, sort],
    queryFn: ({ pageParam }) => api(contracts.discovery.search, { query: { ...query, cursor: pageParam } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
  const first = res.data?.pages[0];
  const items = res.data?.pages.flatMap((p) => p.items) ?? [];
  const activeCount = Object.keys(filters).length;

  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <div className="lg:grid lg:grid-cols-[272px_minmax(0,1fr)] lg:gap-8">
        <aside className="hidden lg:block" aria-label={t('explore.filters')}>
          <div className="surface-card sticky top-20 max-h-[calc(100dvh-6rem)] overflow-y-auto p-4">
            <Filters filters={filters} set={set} clearAll={clearAll} />
          </div>
        </aside>
        <div className="min-w-0">
          <SearchBox initial={q} onSearch={(v) => set('q', v)} />
          {first?.interpreted.length ? (
            <p className="mt-2 text-sm text-muted">
              {first.interpreted.map((i) => t('explore.interpreted', { term: i.term.toUpperCase(), meaning: i.meaning })).join(' · ')}
            </p>
          ) : null}
          <ActiveChips filters={filters} set={set} clearAll={clearAll} />
          <div className="mt-4 flex items-center justify-between gap-3">
            <p className="text-sm text-muted" aria-live="polite">
              {res.isLoading ? t('common.loading') : t('explore.count', { n: localDigits(first?.total ?? 0, t.locale) })}
            </p>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setSheet(true)} icon={<SlidersHorizontal className="size-4" aria-hidden />}>
                {t('explore.filters')}
                {activeCount > 0 && <span className="latin ml-0.5 rounded-full bg-primary px-1.5 text-xs text-on-primary">{localDigits(activeCount, t.locale)}</span>}
              </Button>
              <label htmlFor="sort" className="sr-only">{t('explore.sort')}</label>
              <Select id="sort" value={sort} onChange={(e) => set('sort', e.target.value === 'best' ? null : e.target.value)} className="h-9 w-auto min-w-36 text-sm">
                {SORTS.map((s) => <option key={s} value={s}>{t(`explore.sort.${s}`)}</option>)}
              </Select>
            </div>
          </div>
          <div className="mt-4">
            {res.isLoading ? (
              <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <li key={i}><Skeleton className="aspect-[4/5] rounded-card" /></li>)}</ul>
            ) : res.isError ? (
              <ErrorState onRetry={() => void res.refetch()} />
            ) : items.length === 0 ? (
              <ZeroResults q={q} filters={filters} />
            ) : (
              <>
                <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                  {items.map((c) => <li key={c.id}><CourseCard c={c} /></li>)}
                </ul>
                {res.hasNextPage && (
                  <div className="mt-6 flex justify-center">
                    <Button variant="secondary" onClick={() => void res.fetchNextPage()} loading={res.isFetchingNextPage}>{t('notif.more')}</Button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
      {sheet && (
        <Suspense fallback={null}>
          <Sheet open={sheet} onOpenChange={setSheet} title={t('explore.filters')} footer={<Button block onClick={() => setSheet(false)}>{t('explore.showN', { n: localDigits(first?.total ?? 0, t.locale) })}</Button>}>
            <Filters filters={filters} set={set} clearAll={clearAll} />
          </Sheet>
        </Suspense>
      )}
    </main>
  );
}

function Group({ title, children, defaultOpen = true }: { title: string; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <fieldset className="border-b border-border py-3 last:border-0">
      <legend className="sr-only">{title}</legend>
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} className="flex w-full items-center justify-between text-left text-[15px] font-semibold">
        {title}
        <ChevronDown className={cn('size-4 text-muted transition-transform', open && 'rotate-180')} aria-hidden />
      </button>
      {open && <div className="mt-2.5">{children}</div>}
    </fieldset>
  );
}

function Pill({ on, onClick, children }: { on: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" aria-pressed={on} onClick={onClick} className={cn('h-9 rounded-full border px-3 text-[13px] font-medium transition-colors', on ? 'border-primary bg-primary text-on-primary' : 'border-border hover:border-border-strong')}>
      {children}
    </button>
  );
}

function Filters({ filters, set, clearAll }: { filters: Record<string, string | undefined>; set: (k: string, v: string | null) => void; clearAll: () => void }) {
  const t = useT();
  const insts = useQuery({ queryKey: ['institutions', ''], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), staleTime: 600_000 });
  const depts = useQuery({ queryKey: ['departments', filters.institutionId], queryFn: () => api(contracts.catalog.departments, { query: { institutionId: filters.institutionId! } }), enabled: !!filters.institutionId });
  const subjects = useQuery({ queryKey: ['subjects', filters.institutionId ?? '', filters.departmentId ?? '', filters.semester ?? ''], queryFn: () => api(contracts.catalog.subjects, { query: filters.institutionId ? { institutionId: filters.institutionId, departmentId: filters.departmentId, semester: filters.semester ? Number(filters.semester) : undefined } : {} }), staleTime: 600_000 });
  const toggle = (k: string, v: string) => set(k, filters[k] === v ? null : v);
  const withCourses = (insts.data?.items ?? []).filter((i) => i.courseCount > 0 || i.id === filters.institutionId);
  return (
    <div>
      <div className="flex items-center justify-between pb-1">
        <h2 className="flex items-center gap-2 text-base font-semibold"><SlidersHorizontal className="size-4" aria-hidden /> {t('explore.filters')}</h2>
        <button type="button" onClick={clearAll} className="inline-flex items-center gap-1 text-sm font-medium text-primary-fg hover:underline"><RotateCcw className="size-3.5" aria-hidden /> {t('explore.reset')}</button>
      </div>
      <Group title={t('explore.f.university')}>
        {insts.isLoading ? <Skeleton className="h-24" /> : (
          <ul className="flex flex-col gap-0.5">
            {withCourses.map((i) => (
              <li key={i.id}>
                <label className="flex cursor-pointer items-center gap-2.5 rounded-control px-1 py-1.5 text-sm hover:bg-surface-2">
                  <input type="radio" name="inst" className="size-4 accent-[var(--ss-primary)]" checked={filters.institutionId === i.id} onChange={() => toggle('institutionId', i.id)} onClick={() => filters.institutionId === i.id && set('institutionId', null)} />
                  <span className="latin min-w-0 flex-1 truncate">{i.name}</span>
                  <span className="latin rounded-full bg-surface-2 px-1.5 text-xs text-muted tnum">{localDigits(i.courseCount, t.locale)}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </Group>
      {filters.institutionId && depts.data && (
        <Group title={t('explore.f.department')}>
          <div className="flex flex-wrap gap-2">{depts.data.items.map((d) => <Pill key={d.id} on={filters.departmentId === d.id} onClick={() => toggle('departmentId', d.id)}><span className="latin">{d.shortName}</span></Pill>)}</div>
        </Group>
      )}
      <Group title={t('explore.f.semester')}>
        <div className="grid grid-cols-4 gap-1.5">{Array.from({ length: 8 }, (_, i) => String(i + 1)).map((s) => <Pill key={s} on={filters.semester === s} onClick={() => toggle('semester', s)}>{ordinal(Number(s), t.locale)}</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.subject')} defaultOpen={false}>
        <Select aria-label={t('explore.f.subject')} value={filters.subjectId ?? ''} onChange={(e) => set('subjectId', e.target.value || null)} className="h-10 text-sm">
          <option value="">{t('explore.any')}</option>
          {subjects.data?.items.map((s) => <option key={s.id} value={s.id}>{'courseCode' in s && s.courseCode ? `${s.courseCode} · ` : ''}{s.name}</option>)}
        </Select>
      </Group>
      <Group title={t('explore.f.examType')}>
        <div className="flex flex-wrap gap-2">{(['midterm', 'final', 'quiz', 'lab'] as const).map((e) => <Pill key={e} on={filters.examType === e} onClick={() => toggle('examType', e)}>{t(`examType.${e}`)}</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.courseType')} defaultOpen={false}>
        <div className="flex flex-wrap gap-2">{COURSE_TYPES.map((c) => <Pill key={c} on={filters.courseType === c} onClick={() => toggle('courseType', c)}>{t(`courseType.${c}`)}</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.price')}>
        <div className="flex flex-wrap gap-2">{(['free', 'paid'] as const).map((p) => <Pill key={p} on={filters.price === p} onClick={() => toggle('price', p)}>{t(`explore.price.${p}`)}</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.rating')} defaultOpen={false}>
        <div className="flex flex-wrap gap-2">{['4.5', '4', '3.5'].map((r) => <Pill key={r} on={filters.rating === r} onClick={() => toggle('rating', r)}>{localDigits(r, t.locale)}+ ★</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.duration')} defaultOpen={false}>
        <div className="flex flex-wrap gap-2">{(['short', 'medium', 'long'] as const).map((d) => <Pill key={d} on={filters.duration === d} onClick={() => toggle('duration', d)}>{t(`explore.dur.${d}`)}</Pill>)}</div>
      </Group>
      <Group title={t('explore.f.language')} defaultOpen={false}>
        <div className="flex flex-wrap gap-2">{(['bn', 'en', 'mixed'] as const).map((l) => <Pill key={l} on={filters.language === l} onClick={() => toggle('language', l)}>{t(`explore.lang.${l}`)}</Pill>)}</div>
      </Group>
    </div>
  );
}

function ActiveChips({ filters, set, clearAll }: { filters: Record<string, string | undefined>; set: (k: string, v: string | null) => void; clearAll: () => void }) {
  const t = useT();
  const insts = useQuery({ queryKey: ['institutions', ''], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), staleTime: 600_000, enabled: !!filters.institutionId });
  const entries = Object.entries(filters).filter(([, v]) => v);
  if (!entries.length) return null;
  const label = (k: string, v: string) => {
    if (k === 'institutionId') return insts.data?.items.find((i) => i.id === v)?.shortName ?? '…';
    if (k === 'semester') return t('common.semesterN', { ord: ordinal(Number(v), t.locale) });
    if (k === 'examType') return t(`examType.${v}`);
    if (k === 'courseType') return t(`courseType.${v}`);
    if (k === 'price') return t(`explore.price.${v}`);
    if (k === 'rating') return `${localDigits(v, t.locale)}+ ★`;
    if (k === 'duration') return t(`explore.dur.${v}`);
    if (k === 'language') return t(`explore.lang.${v}`);
    if (k === 'courseCode') return v;
    return t(`explore.f.${k === 'departmentId' ? 'department' : 'subject'}`);
  };
  return (
    <div className="mt-3 flex flex-wrap items-center gap-2">
      {entries.map(([k, v]) => (
        <button key={k} type="button" onClick={() => set(k, null)} className="latin inline-flex h-8 items-center gap-1.5 rounded-full bg-primary-soft px-3 text-[13px] font-medium text-primary-fg hover:brightness-95" aria-label={t('explore.removeFilter', { f: label(k, v!) })}>
          {label(k, v!)} <X className="size-3.5" aria-hidden />
        </button>
      ))}
      <button type="button" onClick={clearAll} className="text-[13px] font-semibold text-muted hover:text-text">{t('explore.clearAll')}</button>
    </div>
  );
}

function ZeroResults({ q, filters }: { q: string; filters: Record<string, string | undefined> }) {
  const t = useT();
  const pre = new URLSearchParams({ new: '1', ...(q ? { subject: q } : {}), ...(filters.institutionId ? { institutionId: filters.institutionId } : {}), ...(filters.examType ? { examType: filters.examType } : {}) });
  return (
    <EmptyState
      illustration={<Illustration kind="search" className="h-32 w-40" />}
      title={<span className="text-lg">{q ? t('explore.zeroFor', { q }) : t('explore.zero')}</span>}
      body={t('explore.zeroBody')}
      action={
        <div className="flex flex-wrap justify-center gap-2">
          <Button asChild icon={<Megaphone className="size-4" aria-hidden />}><Link to={`/requests?${pre}`}>{t('explore.requestIt')}</Link></Button>
          <Button asChild variant="secondary"><Link to="/explore">{t('explore.clearAll')}</Link></Button>
        </div>
      }
    />
  );
}
