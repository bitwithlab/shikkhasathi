import '../i18n';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Bell, BookOpen, CalendarDays, ClipboardList, PlayCircle, Sparkles, Timer } from 'lucide-react';
import type { Assignment, Dashboard } from '@/api';
import { Badge, Button, EmptyState, ErrorState, Illustration, ProgressBar, Skeleton, SkeletonLines, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatDate, localDigits, ordinal, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { AssignmentRow, AssignmentSheet, ConnectCard, CountdownCard, FeedRow, SectionCard, SyncStatus, WeekStrip } from '../classroom/parts';
import { CourseCard } from '@/components/CourseCard';

const CACHE_KEY = (uid: string) => `ss-dash:${uid}`;

/** Dashboard aggregate: cached copy renders instantly (localStorage), then refreshes in the background. */
function useDashboard() {
  const uid = useAuth((s) => s.user?.id ?? '');
  const cached = useMemo(() => {
    try {
      const raw = localStorage.getItem(CACHE_KEY(uid));
      return raw ? (JSON.parse(raw) as Dashboard) : undefined;
    } catch {
      return undefined;
    }
  }, [uid]);
  const q = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api(contracts.dashboard.get),
    initialData: cached,
    initialDataUpdatedAt: 0,
    staleTime: 20_000,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => (query.state.data?.header.classroom.status === 'syncing' ? 3000 : 120_000),
  });
  useEffect(() => {
    if (q.data && q.dataUpdatedAt > 0) {
      try {
        localStorage.setItem(CACHE_KEY(uid), JSON.stringify(q.data));
      } catch {
        /* quota */
      }
    }
  }, [q.data, q.dataUpdatedAt, uid]);
  return q;
}

function greetingKey() {
  const h = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Dhaka', hour: '2-digit', hour12: false }).format(new Date()));
  return h < 5 || h >= 21 ? 'home.night' : h < 12 ? 'home.morning' : h < 17 ? 'home.afternoon' : 'home.evening';
}

export default function HomePage() {
  const t = useT();
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const { data, isLoading, isError, refetch } = useDashboard();
  const [open, setOpen] = useState<Assignment | null>(null);

  // Return from the Google consent screen.
  useEffect(() => {
    const r = params.get('classroom');
    if (!r) return;
    if (r === 'connected') toast.success(t('gc.connectedToast'));
    else if (r === 'denied') toast.info(t('gc.deniedToast'));
    else if (r === 'workspace') toast.error(t('gc.blockedTitle'));
    else toast.error(t('gc.connectError'));
    void qc.invalidateQueries({ queryKey: ['dashboard'] });
    params.delete('classroom');
    setParams(params, { replace: true });
  }, [params, setParams, qc, t]);

  if (isLoading && !data) return <HomeSkeleton />;
  if (isError && !data) return <div className="mx-auto max-w-3xl px-4 py-10"><ErrorState onRetry={() => void refetch()} /></div>;
  if (!data) return null;

  const h = data.header;
  const gc = h.classroom;
  const connected = gc.connected && gc.status !== 'needs_reauth' && gc.status !== 'workspace_blocked';

  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-10 pt-5 lg:px-8 lg:pt-7">
      {/* 1. Header */}
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-muted">{t(greetingKey())}</p>
          <h1 className="mt-0.5 text-[26px] font-bold leading-tight">{t('home.greeting', { name: h.name.split(' ')[0] ?? h.name })}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {h.institutionShort ? (
              <Link to="/profile" className="latin inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-[13px] font-medium hover:border-primary">
                {[h.institutionShort, h.departmentShort, h.semester ? t('common.semesterN', { ord: ordinal(h.semester, t.locale) }) : null].filter(Boolean).join(' · ')}
              </Link>
            ) : (
              <Link to="/onboarding" className="text-sm font-semibold text-primary-fg hover:underline">{t('home.addAcademic')}</Link>
            )}
            {gc.connected && <SyncStatus status={gc} />}
          </div>
        </div>
        <Link to="/notifications" className="relative hidden size-11 items-center justify-center rounded-full border border-border bg-surface text-muted hover:text-text lg:inline-flex" aria-label={`${t('nav.notifications')} (${h.unreadNotifications})`}>
          <Bell className="size-5" strokeWidth={1.75} aria-hidden />
          {h.unreadNotifications > 0 && <span className="latin absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-danger px-1 text-center text-[11px] font-bold leading-5 text-white">{localDigits(h.unreadNotifications > 99 ? '99+' : h.unreadNotifications, t.locale)}</span>}
        </Link>
      </header>

      <div className="mt-6 grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <div className="flex min-w-0 flex-col gap-5">
          {connected ? (
            <>
              <DueSoon section={data.dueSoon} onOpen={setOpen} />
              <Feed section={data.feed} />
            </>
          ) : (
            <ConnectCard status={gc.connected ? gc : null} />
          )}
          {/* mobile: exam + continue + week follow in spec order */}
          <div className="flex flex-col gap-5 lg:hidden">
            <ExamWidget section={data.exam} />
            <Continue section={data.continueLearning} />
            <Week section={data.week} />
          </div>
          <Recommendations section={data.recommendations} />
        </div>
        <aside className="hidden flex-col gap-5 lg:flex">
          <ExamWidget section={data.exam} />
          <Continue section={data.continueLearning} />
          <Week section={data.week} />
        </aside>
      </div>
      <AssignmentSheet a={open} onClose={() => setOpen(null)} />
    </div>
  );
}

function WidgetError({ title }: { title: string }) {
  const t = useT();
  const qc = useQueryClient();
  return (
    <SectionCard title={title}>
      <ErrorState compact title={t('home.widgetError')} onRetry={() => void qc.invalidateQueries({ queryKey: ['dashboard'] })} />
    </SectionCard>
  );
}

const GROUP_TONE = { overdue: 'text-danger-fg', today: 'text-danger-fg', tomorrow: 'text-warning-fg', this_week: 'text-muted' } as const;

function DueSoon({ section, onOpen }: { section: Dashboard['dueSoon']; onOpen: (a: Assignment) => void }) {
  const t = useT();
  if (section.error) return <WidgetError title={t('home.dueSoon')} />;
  const groups = section.data?.groups ?? [];
  // max 6 rows across groups
  let budget = 6;
  const shown = groups.map((g) => {
    const items = g.items.slice(0, Math.max(0, budget));
    budget -= items.length;
    return { ...g, items };
  });
  return (
    <SectionCard
      id="due-h"
      title={t('home.dueSoon')}
      icon={<ClipboardList className="size-5 text-danger" strokeWidth={1.75} aria-hidden />}
      action={
        <Link to="/classroom/assignments" className="text-sm font-semibold text-primary-fg hover:underline">
          {t('common.seeAll')} {section.data && section.data.total > 6 ? `(${localDigits(section.data.total, t.locale)})` : ''}
        </Link>
      }
    >
      {groups.length === 0 ? (
        <EmptyState compact illustration={<Illustration kind="done" />} title={t('home.allDone')} body={t('home.allDoneBody')} />
      ) : (
        <div className="flex flex-col gap-4">
          {shown
            .filter((g) => g.items.length)
            .map((g) => (
              <div key={g.key}>
                <p className={cn('mb-1.5 text-[13px] font-semibold', GROUP_TONE[g.key])}>
                  {t(`due.${g.key === 'this_week' ? 'thisWeek' : g.key}`)} · {localDigits(g.items.length, t.locale)}
                </p>
                <ul className="flex flex-col gap-2">
                  {g.items.map((a) => (
                    <li key={a.id}>
                      <AssignmentRow a={a} onOpen={onOpen} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
        </div>
      )}
    </SectionCard>
  );
}

function Feed({ section }: { section: Dashboard['feed'] }) {
  const t = useT();
  const qc = useQueryClient();
  const nav = useNavigate();
  const readAll = useMutation({
    mutationFn: () => api(contracts.classroom.feedRead, { body: { all: true } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['dashboard'] }),
  });
  if (section.error) return <WidgetError title={t('home.feed')} />;
  const items = section.data?.items ?? [];
  const open = (id: string, courseId: string) => {
    void api(contracts.classroom.feedRead, { body: { ids: [id] } }).then(() => qc.invalidateQueries({ queryKey: ['dashboard'] }));
    nav(`/classroom/${encodeURIComponent(courseId)}`);
  };
  return (
    <SectionCard
      id="feed-h"
      title={
        <>
          {t('home.feed')}
          {section.data && section.data.unread > 0 && <Badge tone="primary">{t('home.newCount', { n: localDigits(section.data.unread, t.locale) })}</Badge>}
        </>
      }
      icon={<Sparkles className="size-5 text-primary" strokeWidth={1.75} aria-hidden />}
      action={
        section.data && section.data.unread > 0 ? (
          <button type="button" onClick={() => readAll.mutate()} className="text-sm font-semibold text-primary-fg hover:underline">
            {t('home.markAllRead')}
          </button>
        ) : undefined
      }
    >
      {items.length === 0 ? (
        <EmptyState compact title={t('home.feedEmpty')} />
      ) : (
        <ul className="-mx-2 flex flex-col divide-y divide-border">
          {items.map((f) => (
            <li key={f.id}>
              <FeedRow f={f} onOpen={() => open(f.id, f.courseId)} />
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

function ExamWidget({ section }: { section: Dashboard['exam'] }) {
  const t = useT();
  if (section.error) return null;
  if (!section.data)
    return (
      <section className="surface-card flex flex-wrap items-center gap-4 p-4">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-control bg-warning-soft text-warning-fg">
          <Timer className="size-5" aria-hidden />
        </span>
        <div className="min-w-[10rem] flex-1">
          <p className="font-semibold">{t('home.noExam')}</p>
          <p className="text-sm text-muted">{t('home.noExamBody')}</p>
        </div>
        <Button asChild variant="soft" size="sm">
          <Link to="/exam-mode">{t('home.addExam')}</Link>
        </Button>
      </section>
    );
  return <CountdownCard exam={section.data} />;
}

function Continue({ section }: { section: Dashboard['continueLearning'] }) {
  const t = useT();
  const c = section.data;
  if (section.error) return null;
  if (!c)
    return (
      <section className="surface-card p-4">
        <h2 className="flex items-center gap-2 text-[17px] font-semibold">
          <PlayCircle className="size-5 text-success" strokeWidth={1.75} aria-hidden /> {t('home.continue')}
        </h2>
        <EmptyState compact illustration={<Illustration kind="courses" />} title={t('home.noCourses')} action={<Button asChild variant="secondary" size="sm"><Link to="/explore">{t('nav.explore')}</Link></Button>} />
      </section>
    );
  return (
    <section className="surface-card p-4" aria-labelledby="cont-h">
      <h2 id="cont-h" className="flex items-center gap-2 text-[17px] font-semibold">
        <PlayCircle className="size-5 text-success" strokeWidth={1.75} aria-hidden /> {t('home.continue')}
      </h2>
      <div className="mt-3">
        {c.courseCode && <Badge tone="primary" className="latin">{c.courseCode}</Badge>}
        <p className="latin mt-2 font-semibold">{c.title}</p>
        {c.lastLessonTitle && <p className="mt-0.5 text-sm text-muted">{t('home.lastLesson', { title: c.lastLessonTitle })}</p>}
        <div className="mt-3 flex items-center justify-between text-[13px] text-muted">
          <span>{t('home.progress', { p: localDigits(c.progressPercent, t.locale) })}</span>
          <span className="tnum">{localDigits(c.completedLessons, t.locale)}/{localDigits(c.totalLessons, t.locale)}</span>
        </div>
        <ProgressBar value={c.progressPercent} className="mt-1.5" label={t('home.progress', { p: localDigits(c.progressPercent, t.locale) })} />
        <Button asChild block className="mt-4" icon={<PlayCircle className="size-4" aria-hidden />}>
          <Link to={c.lastLessonId ? `/learn/${c.courseId}/${c.lastLessonId}` : `/c/${c.courseSlug}`}>{t('home.resume')}</Link>
        </Button>
      </div>
    </section>
  );
}

function Week({ section }: { section: Dashboard['week'] }) {
  const t = useT();
  const [sel, setSel] = useState<string | null>(null);
  if (section.error || !section.data) return null;
  return (
    <section className="surface-card p-4" aria-labelledby="week-h">
      <div className="mb-3 flex items-center justify-between">
        <h2 id="week-h" className="flex items-center gap-2 text-[17px] font-semibold">
          <CalendarDays className="size-5 text-primary" strokeWidth={1.75} aria-hidden /> {t('home.week')}
        </h2>
        <Link to={sel ? `/classroom/calendar?day=${sel}` : '/classroom/calendar'} className="text-sm font-semibold text-primary-fg hover:underline">
          {t('nav.calendar')}
        </Link>
      </div>
      <WeekStrip days={section.data} selected={sel} onSelect={(d) => setSel((s) => (s === d ? null : d))} />
      <p className="mt-3 flex items-center gap-4 text-xs text-muted">
        <span className="inline-flex items-center gap-1.5"><span className="size-2 rounded-full bg-danger" />{t('home.legendDeadline')}</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-2 rounded-full bg-warning" />{t('home.legendExam')}</span>
      </p>
      {sel && (
        <Link to={`/classroom/calendar?day=${sel}`} className="mt-3 flex items-center justify-between rounded-control bg-surface-2 px-3 py-2 text-sm hover:bg-primary-soft">
          {formatDate(`${sel}T12:00:00+06:00`, t.locale, { weekday: 'long', day: 'numeric', month: 'long' })}
          <ArrowRight className="size-4" aria-hidden />
        </Link>
      )}
    </section>
  );
}

function Recommendations({ section }: { section: Dashboard['recommendations'] }) {
  const t = useT();
  const items = section.data ?? [];
  if (section.error || items.length === 0) return null;
  return (
    <section aria-labelledby="rec-h">
      <div className="mb-3 flex items-end justify-between">
        <h2 id="rec-h" className="flex items-center gap-2 text-[17px] font-semibold">
          <BookOpen className="size-5 text-primary" strokeWidth={1.75} aria-hidden /> {t('home.forYou')}
        </h2>
        <Link to="/explore" className="text-sm font-semibold text-primary-fg hover:underline">{t('common.seeAll')}</Link>
      </div>
      <ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:grid sm:grid-cols-2 sm:overflow-visible sm:px-0 xl:grid-cols-2">
        {items.slice(0, 8).map((c) => (
          <li key={c.id} className="w-[78%] shrink-0 snap-start sm:w-auto">
            <CourseCard c={c} compact />
          </li>
        ))}
      </ul>
    </section>
  );
}

function HomeSkeleton() {
  return (
    <div className="mx-auto max-w-[1200px] px-4 pt-6 lg:px-8" aria-busy>
      <Skeleton className="h-4 w-24" />
      <Skeleton className="mt-2 h-8 w-72" />
      <Skeleton className="mt-3 h-7 w-56 rounded-full" />
      <div className="mt-6 grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="flex flex-col gap-5">
          <div className="surface-card p-5">
            <Skeleton className="h-5 w-40" />
            <div className="mt-4 flex flex-col gap-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}</div>
          </div>
          <div className="surface-card p-5"><SkeletonLines lines={4} /></div>
        </div>
        <div className="hidden flex-col gap-5 lg:flex">
          <Skeleton className="h-48 rounded-card" />
          <Skeleton className="h-36 rounded-card" />
        </div>
      </div>
    </div>
  );
}
