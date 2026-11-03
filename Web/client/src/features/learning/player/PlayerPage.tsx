import '../i18n';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Bookmark,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  FileText,
  ListVideo,
  Lock,
  MonitorSmartphone,
  PlayCircle,
  Star,
  Type,
} from 'lucide-react';
import type { LessonDetail, Playback, PlayerOutline } from '@shikkhasathi/shared';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Illustration,
  ProgressRing,
  Sheet,
  Skeleton,
  toast,
} from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { EmbedPlayer, VideoPlayer, fmtTime, type VideoPlayerHandle } from './VideoPlayer';
import { NotesPanel, QaPanel, ResourcesPanel, ReviewCard } from './Panels';

type Tab = 'overview' | 'notes' | 'qa' | 'resources';

/** Stable per browser — used for the 3-device playback limit. */
function deviceId() {
  try {
    let id = localStorage.getItem('ss.device');
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem('ss.device', id);
    }
    return id;
  } catch {
    return 'web-ephemeral-device';
  }
}

export default function PlayerPage() {
  const { courseId = '', lessonId = '' } = useParams();
  const t = useT();
  const outline = useQuery({
    queryKey: ['outline', courseId],
    queryFn: () => api(contracts.learning.outline, { params: { courseId } }),
  });
  const lesson = useQuery({
    queryKey: ['lesson', lessonId],
    queryFn: () => api(contracts.learning.lesson, { params: { id: lessonId } }),
    retry: (n, e) => !(e instanceof ApiError && e.status < 500) && n < 2,
  });
  useEffect(() => {
    if (lesson.data && outline.data)
      document.title = `${lesson.data.title} · ${outline.data.course.title} — ${t('brand.name')}`;
  }, [lesson.data, outline.data, t]);

  if (outline.isLoading) return <PlayerSkeleton />;
  if (outline.isError || !outline.data)
    return (
      <Frame>
        <div className="mx-auto max-w-lg px-4 py-16">
          {outline.error instanceof ApiError && outline.error.status === 404 ? (
            <EmptyState
              illustration={<Illustration kind="search" className="h-28 w-32" />}
              title={t('pl.notFound')}
              action={
                <Button asChild>
                  <Link to="/my-courses">{t('pl.back')}</Link>
                </Button>
              }
            />
          ) : (
            <ErrorState onRetry={() => void outline.refetch()} />
          )}
        </div>
      </Frame>
    );
  return <Player o={outline.data} lesson={lesson} lessonId={lessonId} />;
}

function Frame({ children, header }: { children: React.ReactNode; header?: React.ReactNode }) {
  const t = useT();
  return (
    <div className="min-h-dvh bg-bg">
      <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-surface/95 px-3 backdrop-blur-sm sm:px-4">
        <Link
          to="/my-courses"
          className="grid size-10 shrink-0 place-items-center rounded-full hover:bg-surface-2"
          aria-label={t('pl.back')}
        >
          <ArrowLeft className="size-5" aria-hidden />
        </Link>
        {header}
      </header>
      <main id="main">{children}</main>
    </div>
  );
}

function Player({
  o,
  lesson,
  lessonId,
}: {
  o: PlayerOutline;
  lesson: ReturnType<typeof useQuery<LessonDetail>>;
  lessonId: string;
}) {
  const t = useT();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [sp, setSp] = useSearchParams();
  const tab = (sp.get('tab') as Tab) || 'overview';
  const setTab = (x: Tab) =>
    setSp(
      (p) => {
        const n = new URLSearchParams(p);
        n.set('tab', x);
        n.delete('t');
        return n;
      },
      { replace: true },
    );
  const [sheet, setSheet] = useState(false);
  const [sidebar, setSidebar] = useState(true);
  const handle = useRef<VideoPlayerHandle | null>(null);
  const fullAccess = o.enrolled || o.isOwner;
  const flat = useMemo(() => o.sections.flatMap((s) => s.chapters.flatMap((c) => c.lessons)), [o]);
  const current = flat.find((l) => l.id === lessonId);
  const seekTo = sp.get('t');

  const setProgress = useCallback(
    (p: { progressPercent: number; completed: boolean }) => {
      qc.setQueryData<PlayerOutline>(['outline', o.course.id], (old) =>
        old
          ? {
              ...old,
              progressPercent: p.progressPercent,
              sections: old.sections.map((s) => ({
                ...s,
                chapters: s.chapters.map((c) => ({
                  ...c,
                  lessons: c.lessons.map((l) => (l.id === lessonId ? { ...l, completed: p.completed } : l)),
                })),
              })),
              completedLessons: old.sections
                .flatMap((s) => s.chapters.flatMap((c) => c.lessons))
                .filter(
                  (l) =>
                    (l.id === lessonId ? p.completed : l.completed) &&
                    ['video', 'pdf', 'text'].includes(l.type),
                ).length,
            }
          : old,
      );
      qc.setQueryData<LessonDetail>(['lesson', lessonId], (old) =>
        old ? { ...old, completed: p.completed } : old,
      );
      void qc.invalidateQueries({ queryKey: ['my-courses'] });
    },
    [qc, o.course.id, lessonId],
  );

  const complete = useMutation({
    mutationFn: (v: boolean) =>
      api(contracts.learning.complete, { params: { id: lessonId }, body: { completed: v } }),
    onSuccess: setProgress,
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  const goto = (id: string | null | undefined) => {
    if (!id) return;
    setSheet(false);
    nav(`/learn/${o.course.id}/${id}${tab !== 'overview' ? `?tab=${tab}` : ''}`);
  };

  const L = lesson.data;
  const header = (
    <>
      <div className="min-w-0 flex-1">
        <p className="latin truncate text-sm font-semibold leading-tight sm:text-[15px]">{o.course.title}</p>
        {o.course.courseCode && (
          <p className="latin truncate text-xs text-muted">
            {o.course.courseCode} · {o.course.sellerName}
          </p>
        )}
      </div>
      {fullAccess && (
        <div className="hidden items-center gap-2 sm:flex">
          <ProgressRing
            value={o.progressPercent}
            size={36}
            stroke={4}
            showValue={false}
            label={t('pl.progress', { n: localDigits(o.progressPercent, t.locale) })}
          />
          <span className="text-sm text-muted tnum">
            {t('pl.progress', { n: localDigits(o.progressPercent, t.locale) })}
          </span>
        </div>
      )}
      <span className="lg:hidden">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setSheet(true)}
          icon={<ListVideo className="size-5" aria-hidden />}
          aria-label={t('pl.curriculum')}
        >
          <span className="hidden sm:inline">{t('pl.curriculum')}</span>
        </Button>
      </span>
      <span className="hidden lg:block">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setSidebar((s) => !s)}
          aria-pressed={sidebar}
          icon={<ListVideo className="size-5" aria-hidden />}
        >
          {t('pl.curriculum')}
        </Button>
      </span>
    </>
  );

  return (
    <Frame header={header}>
      <div
        className={cn(
          'mx-auto grid max-w-[1440px] lg:gap-6 lg:px-6 lg:py-5',
          sidebar ? 'lg:grid-cols-[minmax(0,1fr)_360px]' : 'lg:grid-cols-1',
        )}
      >
        <div className="min-w-0">
          {/* media */}
          <div className="sticky top-14 z-20 bg-black lg:static lg:bg-transparent">
            {lesson.isLoading ? (
              <Skeleton className="aspect-video w-full rounded-none sm:rounded-card" />
            ) : lesson.isError || !L ? (
              <LockedOrError err={lesson.error} o={o} onRetry={() => void lesson.refetch()} />
            ) : (
              <LessonMedia
                key={L.id}
                L={L}
                seekTo={seekTo ? Number(seekTo) : null}
                handle={handle}
                onProgress={setProgress}
                onEnded={() => L.nextLessonId && toast.success(t('pl.next'))}
              />
            )}
          </div>

          <div className="px-4 pb-24 pt-4 lg:px-0">
            {L && (
              <>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h1 className="latin text-lg font-bold leading-snug sm:text-xl">{L.title}</h1>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                      {L.isImportant && (
                        <Badge tone="warning" icon={<Star className="size-3.5 fill-current" aria-hidden />}>
                          {t('pl.important')}
                        </Badge>
                      )}
                      {L.durationSec > 0 && (
                        <span>
                          {t('pl.minutes', {
                            n: localDigits(Math.max(1, Math.round(L.durationSec / 60)), t.locale),
                          })}
                        </span>
                      )}
                      {current?.isPreview && !fullAccess && <Badge tone="primary">{t('pl.preview')}</Badge>}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {o.enrolled && (
                      <Button
                        variant={L.completed ? 'soft' : 'secondary'}
                        size="sm"
                        loading={complete.isPending}
                        onClick={() => complete.mutate(!L.completed)}
                        aria-pressed={L.completed}
                        icon={
                          L.completed ? (
                            <CircleCheck className="size-4" aria-hidden />
                          ) : (
                            <Check className="size-4" aria-hidden />
                          )
                        }
                      >
                        {L.completed ? t('pl.done') : t('pl.markDone')}
                      </Button>
                    )}
                    {o.enrolled && L.type === 'video' && <BookmarkBtn lessonId={L.id} handle={handle} />}
                  </div>
                </div>
                <div className="mt-4 flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={!L.prevLessonId}
                    onClick={() => goto(L.prevLessonId)}
                    icon={<ChevronLeft className="size-4" aria-hidden />}
                  >
                    {t('pl.prev')}
                  </Button>
                  <span className="flex-1" />
                  <Button size="sm" disabled={!L.nextLessonId} onClick={() => goto(L.nextLessonId)}>
                    {t('pl.next')}
                    <ChevronRight className="size-4" aria-hidden />
                  </Button>
                </div>
              </>
            )}

            {!fullAccess && <BuyBanner o={o} />}

            {fullAccess && L && (
              <>
                <div
                  role="tablist"
                  aria-label={t('pl.curriculum')}
                  className="mt-6 flex gap-1 overflow-x-auto border-b border-border scrollbar-none"
                >
                  {(['overview', 'notes', 'qa', 'resources'] as const).map((x) => (
                    <button
                      key={x}
                      role="tab"
                      type="button"
                      id={`tab-${x}`}
                      aria-selected={tab === x}
                      aria-controls={`panel-${x}`}
                      onClick={() => setTab(x)}
                      className={cn(
                        '-mb-px whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors',
                        tab === x
                          ? 'border-primary text-text'
                          : 'border-transparent text-muted hover:text-text',
                      )}
                    >
                      {t(`pl.tab.${x}`)}
                    </button>
                  ))}
                </div>
                <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="pt-4">
                  {tab === 'overview' && (
                    <div className="flex flex-col gap-4">
                      {L.type !== 'text' && L.body && (
                        <p className="whitespace-pre-wrap text-[15px] leading-relaxed">{L.body}</p>
                      )}
                      {o.enrolled && (
                        <div className="rounded-card border border-border bg-surface p-4">
                          <div className="flex items-center gap-3">
                            <ProgressRing value={o.progressPercent} size={52} />
                            <div>
                              <p className="font-semibold">
                                {t('pl.progress', { n: localDigits(o.progressPercent, t.locale) })}
                              </p>
                              <p className="text-sm text-muted">
                                {t('my.lessons', {
                                  done: localDigits(o.completedLessons, t.locale),
                                  total: localDigits(o.totalLessons, t.locale),
                                })}
                              </p>
                            </div>
                          </div>
                        </div>
                      )}
                      {o.canReview && <ReviewCard courseId={o.course.id} mine={o.myReview} />}
                      <p className="hidden text-xs text-muted lg:block">{t('vp.shortcuts')}</p>
                    </div>
                  )}
                  {tab === 'notes' && (
                    <NotesPanel
                      lessonId={L.id}
                      currentTime={() =>
                        L.type === 'video' && handle.current ? handle.current.time() : null
                      }
                      onSeek={(s) => handle.current?.seek(s)}
                    />
                  )}
                  {tab === 'qa' && (
                    <QaPanel
                      lessonId={L.id}
                      isOwner={o.isOwner}
                      currentTime={() =>
                        L.type === 'video' && handle.current ? handle.current.time() : null
                      }
                      onSeek={(s) => handle.current?.seek(s)}
                    />
                  )}
                  {tab === 'resources' && <ResourcesPanel resources={o.resources} />}
                </div>
              </>
            )}
          </div>
        </div>

        {sidebar && (
          <aside className="hidden lg:block" aria-label={t('pl.curriculum')}>
            <div className="sticky top-[76px] max-h-[calc(100dvh-96px)] overflow-y-auto rounded-card border border-border bg-surface">
              <Curriculum o={o} current={lessonId} onPick={goto} />
            </div>
          </aside>
        )}
      </div>

      <Sheet open={sheet} onOpenChange={setSheet} title={t('pl.curriculum')} className="max-h-[85dvh]">
        <Curriculum o={o} current={lessonId} onPick={goto} />
      </Sheet>
    </Frame>
  );
}

function LessonMedia({
  L,
  seekTo,
  handle,
  onProgress,
  onEnded,
}: {
  L: LessonDetail;
  seekTo: number | null;
  handle: React.MutableRefObject<VideoPlayerHandle | null>;
  onProgress: (p: { progressPercent: number; completed: boolean }) => void;
  onEnded: () => void;
}) {
  const t = useT();
  const [takeover, setTakeover] = useState(false);
  const [nonce, setNonce] = useState(0);
  const pb = useQuery({
    queryKey: ['playback', L.id, nonce],
    queryFn: () => api(contracts.learning.playback, { params: { id: L.id }, body: { deviceId: deviceId() } }),
    enabled: L.type === 'video' && !!L.video,
    staleTime: 90 * 60_000,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const token = pb.data?.streamToken;
  const completedRef = useRef(L.completed);
  const onHeartbeat = useCallback(
    async (positionSec: number, deltaSec: number) => {
      try {
        const r = await api(contracts.learning.progress, {
          params: { id: L.id },
          body: { positionSec, deltaSec, streamToken: token },
        });
        if (r.completed !== completedRef.current || deltaSec > 0) {
          completedRef.current = r.completed;
          onProgress(r);
        }
        return r.streamActive;
      } catch {
        return true; // offline blips never stop playback
      }
    },
    [L.id, token, onProgress],
  );

  if (L.type === 'pdf') return <PdfLesson L={L} />;
  if (L.type === 'text') return <TextLesson L={L} />;
  if (L.type !== 'video')
    return <MediaMessage icon={<FileText className="size-8" aria-hidden />} text={t('pl.quizSoon')} />;
  if (!L.video || (L.video.kind === 'hls' && L.video.status !== 'ready'))
    return <MediaMessage icon={<PlayCircle className="size-8" aria-hidden />} text={t('pl.processing')} />;
  if (pb.isLoading) return <Skeleton className="aspect-video w-full rounded-none sm:rounded-card" />;
  if (pb.isError || !pb.data)
    return (
      <MediaMessage
        icon={<MonitorSmartphone className="size-8" aria-hidden />}
        text={pb.error instanceof ApiError ? pb.error.message : t('vp.error')}
        action={
          <Button size="sm" variant="secondary" onClick={() => void pb.refetch()}>
            {t('vp.retry')}
          </Button>
        }
      />
    );
  const data: Playback = seekTo !== null ? { ...pb.data, startAtSec: seekTo } : pb.data;
  if (takeover)
    return (
      <MediaMessage
        icon={<MonitorSmartphone className="size-8" aria-hidden />}
        text={t('vp.elsewhere')}
        sub={t('vp.elsewhereBody')}
        action={
          <Button
            size="sm"
            onClick={() => {
              setTakeover(false);
              setNonce((n) => n + 1);
            }}
          >
            {t('vp.playHere')}
          </Button>
        }
      />
    );
  return data.kind === 'hls' ? (
    <VideoPlayer
      pb={data}
      handleRef={handle}
      onHeartbeat={onHeartbeat}
      onEnded={onEnded}
      onTakeover={() => setTakeover(true)}
    />
  ) : (
    <EmbedPlayer pb={data} />
  );
}

function MediaMessage({
  icon,
  text,
  sub,
  action,
}: {
  icon: React.ReactNode;
  text: string;
  sub?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="grid aspect-video w-full place-items-center bg-slate-900 p-6 text-center text-white sm:rounded-card">
      <div className="flex max-w-sm flex-col items-center">
        <span className="text-white/70">{icon}</span>
        <p className="mt-3 font-semibold">{text}</p>
        {sub && <p className="mt-1 text-sm text-white/70">{sub}</p>}
        {action && <div className="mt-4">{action}</div>}
      </div>
    </div>
  );
}

function PdfLesson({ L }: { L: LessonDetail }) {
  const t = useT();
  if (!L.document)
    return <MediaMessage icon={<FileText className="size-8" aria-hidden />} text={t('vp.error')} />;
  return (
    <div className="bg-surface-2 sm:rounded-card sm:border sm:border-border">
      <iframe
        src={`${L.document.url}#view=FitH`}
        title={L.document.title}
        className="h-[70dvh] w-full sm:rounded-t-card"
      />
      <div className="flex items-center justify-between gap-3 border-t border-border px-3 py-2 text-sm">
        <span className="latin min-w-0 truncate">
          {L.document.title}
          {L.document.pages ? ` · ${t('pl.pdfPages', { n: localDigits(L.document.pages, t.locale) })}` : ''}
        </span>
        <a
          href={L.document.url}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 font-semibold text-primary-fg hover:underline"
        >
          {t('pl.pdfOpen')}
        </a>
      </div>
    </div>
  );
}

function TextLesson({ L }: { L: LessonDetail }) {
  return (
    <article className="border-b border-border bg-surface px-5 py-6 sm:rounded-card sm:border">
      <div className="mx-auto max-w-prose whitespace-pre-wrap text-[16px] leading-[1.8]">{L.body}</div>
    </article>
  );
}

function BookmarkBtn({
  lessonId,
  handle,
}: {
  lessonId: string;
  handle: React.MutableRefObject<VideoPlayerHandle | null>;
}) {
  const t = useT();
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () =>
      api(contracts.learning.createBookmark, {
        body: { lessonId, timestampSec: Math.floor(handle.current?.time() ?? 0) },
      }),
    onSuccess: (b) => {
      toast.success(`${t('nt.bookmarked')} · ${localDigits(fmtTime(b.timestampSec ?? 0), t.locale)}`);
      void qc.invalidateQueries({ queryKey: ['bookmarks'] });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Button
      variant="secondary"
      size="sm"
      loading={m.isPending}
      onClick={() => m.mutate()}
      icon={<Bookmark className="size-4" aria-hidden />}
      aria-label={t('nt.bookmark')}
    >
      <span className="hidden sm:inline">{t('nt.bookmark')}</span>
    </Button>
  );
}

function BuyBanner({ o }: { o: PlayerOutline }) {
  const t = useT();
  const expired = !!o.accessUntil && new Date(o.accessUntil) < new Date();
  return (
    <div className="mt-6 flex flex-col gap-3 rounded-card border border-primary/30 bg-primary-soft/50 p-4 sm:flex-row sm:items-center">
      <Lock className="size-6 shrink-0 text-primary-fg" aria-hidden />
      <p className="flex-1 text-sm">{expired ? t('pl.expired') : t('pl.buyBody')}</p>
      <Button asChild size="sm">
        <Link to={`/c/${o.course.slug}`}>{t('pl.buy')}</Link>
      </Button>
    </div>
  );
}

function LockedOrError({ err, o, onRetry }: { err: unknown; o: PlayerOutline; onRetry: () => void }) {
  const t = useT();
  if (err instanceof ApiError && err.status === 403)
    return (
      <MediaMessage
        icon={<Lock className="size-8" aria-hidden />}
        text={err.message}
        action={
          <Button asChild size="sm">
            <Link to={`/c/${o.course.slug}`}>{t('pl.buy')}</Link>
          </Button>
        }
      />
    );
  if (err instanceof ApiError && err.status === 404)
    return <MediaMessage icon={<FileText className="size-8" aria-hidden />} text={t('pl.notFound')} />;
  return (
    <MediaMessage
      icon={<PlayCircle className="size-8" aria-hidden />}
      text={t('vp.error')}
      action={
        <Button size="sm" variant="secondary" onClick={onRetry}>
          {t('vp.retry')}
        </Button>
      }
    />
  );
}

const TYPE_ICON = { video: PlayCircle, pdf: FileText, text: Type, quiz: FileText, mock: FileText } as const;

function Curriculum({
  o,
  current,
  onPick,
}: {
  o: PlayerOutline;
  current: string;
  onPick: (id: string) => void;
}) {
  const t = useT();
  const activeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    activeRef.current?.scrollIntoView?.({ block: 'nearest' });
  }, [current]);
  return (
    <nav aria-label={t('pl.curriculum')}>
      {(o.enrolled || o.isOwner) && (
        <div className="border-b border-border p-4">
          <div className="flex items-center justify-between text-sm">
            <span className="font-semibold">
              {t('pl.progress', { n: localDigits(o.progressPercent, t.locale) })}
            </span>
            <span className="text-muted tnum">
              {t('my.lessons', {
                done: localDigits(o.completedLessons, t.locale),
                total: localDigits(o.totalLessons, t.locale),
              })}
            </span>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-2">
            <div
              className="h-full rounded-full bg-success transition-[width] duration-300"
              style={{ width: `${o.progressPercent}%` }}
            />
          </div>
        </div>
      )}
      {o.sections.map((s) => (
        <section key={s.id} className="border-b border-border last:border-0">
          <h3 className="latin bg-surface-2/60 px-4 py-2.5 text-sm font-semibold">{s.title}</h3>
          {s.chapters.map((c) => (
            <div key={c.id}>
              {s.chapters.length > 1 && (
                <p className="latin px-4 pb-1 pt-3 text-xs font-semibold uppercase tracking-wide text-muted">
                  {c.title}
                </p>
              )}
              <ul>
                {c.lessons.map((l) => {
                  const Icon = TYPE_ICON[l.type];
                  const active = l.id === current;
                  return (
                    <li key={l.id}>
                      <button
                        ref={active ? activeRef : undefined}
                        type="button"
                        disabled={l.locked}
                        onClick={() => onPick(l.id)}
                        aria-current={active ? 'true' : undefined}
                        className={cn(
                          'flex w-full items-start gap-3 px-4 py-2.5 text-left text-sm transition-colors',
                          active ? 'bg-primary-soft/70' : 'hover:bg-surface-2',
                          l.locked && 'cursor-not-allowed opacity-60',
                        )}
                      >
                        <span className="mt-0.5 shrink-0">
                          {l.locked ? (
                            <Lock className="size-[18px] text-muted" aria-label={t('pl.locked')} />
                          ) : l.completed ? (
                            <CircleCheck className="size-[18px] text-success" aria-label={t('pl.done')} />
                          ) : (
                            <Icon
                              className={cn('size-[18px]', active ? 'text-primary' : 'text-muted')}
                              aria-hidden
                            />
                          )}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className={cn('latin block leading-snug', active && 'font-semibold')}>
                            {l.title}
                          </span>
                          <span className="mt-0.5 flex items-center gap-2 text-xs text-muted">
                            {l.durationSec > 0 && (
                              <span className="tnum">{localDigits(fmtTime(l.durationSec), t.locale)}</span>
                            )}
                            {l.isPreview && !o.enrolled && (
                              <span className="font-semibold text-primary-fg">{t('pl.preview')}</span>
                            )}
                          </span>
                        </span>
                        {l.isImportant && (
                          <Star
                            className="mt-0.5 size-4 shrink-0 fill-warning text-warning"
                            aria-label={t('pl.important')}
                          />
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </section>
      ))}
    </nav>
  );
}

function PlayerSkeleton() {
  return (
    <div className="min-h-dvh bg-bg">
      <div className="h-14 border-b border-border bg-surface" />
      <div className="mx-auto grid max-w-[1440px] gap-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:px-6 lg:py-5">
        <div>
          <Skeleton className="aspect-video w-full rounded-none sm:rounded-card" />
          <div className="p-4 lg:px-0">
            <Skeleton className="h-7 w-2/3" />
            <Skeleton className="mt-2 h-4 w-40" />
          </div>
        </div>
        <Skeleton className="hidden h-[70vh] rounded-card lg:block" />
      </div>
    </div>
  );
}
