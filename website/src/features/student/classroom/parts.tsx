import '../i18n';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Bell, BookOpen, CalendarClock, CheckCircle2, ClipboardList, ExternalLink, FileText, Link2, Lock, Megaphone, RefreshCw, ShieldAlert, Star, Timer, Youtube } from 'lucide-react';
import type { Assignment, AssignmentStatus, ClassroomStatus, FeedItem, Material } from '@/api';
import { Badge, Button, ProgressRing, Sheet, toast, type Tone } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { dayDiff, formatDate, formatTime, localDigits, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { ConnectClassroomButton } from './ConnectClassroomButton';

export const STATUS_TONE: Record<AssignmentStatus, Tone> = { pending: 'warning', turned_in: 'success', graded: 'primary', missing: 'danger' };
const STATUS_KEY: Record<AssignmentStatus, string> = { pending: 'status.pending', turned_in: 'status.turnedIn', graded: 'status.graded', missing: 'status.missing' };

export function StatusChip({ status }: { status: AssignmentStatus }) {
  const t = useT();
  return <Badge tone={STATUS_TONE[status]}>{t(STATUS_KEY[status])}</Badge>;
}

/** "আজ রাত ১১:৫৯", "আগামীকাল ৩:০০ PM", "বৃহঃ, ৬ নভে." */
export function dueLabel(iso: string | null, locale: 'bn' | 'en') {
  if (!iso) return null;
  const dd = dayDiff(iso);
  const time = formatTime(iso, locale);
  if (dd === 0) return `${locale === 'bn' ? 'আজ' : 'Today'} ${time}`;
  if (dd === 1) return `${locale === 'bn' ? 'আগামীকাল' : 'Tomorrow'} ${time}`;
  if (dd === -1) return `${locale === 'bn' ? 'গতকাল' : 'Yesterday'} ${time}`;
  return `${formatDate(iso, locale, { weekday: 'short', day: 'numeric', month: 'short' })}, ${time}`;
}

/** Human "x ঘণ্টা বাকি" for urgency. */
function timeLeft(iso: string, locale: 'bn' | 'en', t: ReturnType<typeof useT>) {
  const ms = new Date(iso).getTime() - Date.now();
  if (ms <= 0) return null;
  const h = Math.floor(ms / 3_600_000);
  if (h < 1) return t('due.minutesLeft', { n: localDigits(Math.max(1, Math.floor(ms / 60_000)), locale) });
  if (h < 48) return t('due.hoursLeft', { n: localDigits(h, locale) });
  return t('due.daysLeft', { n: localDigits(Math.floor(h / 24), locale) });
}

export function urgency(a: Pick<Assignment, 'status' | 'dueAt'>): 'danger' | 'warning' | 'neutral' | 'success' {
  if (a.status === 'missing') return 'danger';
  if (a.status === 'turned_in' || a.status === 'graded') return 'success';
  if (!a.dueAt) return 'neutral';
  const h = (new Date(a.dueAt).getTime() - Date.now()) / 3_600_000;
  return h < 24 ? 'danger' : h < 72 ? 'warning' : 'neutral';
}

export function AssignmentRow({ a, onOpen, showCourse = true }: { a: Assignment; onOpen: (a: Assignment) => void; showCourse?: boolean }) {
  const t = useT();
  const u = urgency(a);
  const left = a.dueAt && a.status === 'pending' ? timeLeft(a.dueAt, t.locale, t) : null;
  return (
    <button
      type="button"
      onClick={() => onOpen(a)}
      className={cn(
        'group flex w-full items-center gap-3 rounded-control border bg-surface px-3 py-3 text-left transition-[border-color,box-shadow] duration-150 hover:border-border-strong hover:shadow-e1',
        u === 'danger' && a.status !== 'missing' ? 'border-danger/25' : 'border-border',
        a.status === 'missing' && 'border-danger/30 bg-danger-soft/40',
      )}
    >
      <span className={cn('h-10 w-1 shrink-0 rounded-full', u === 'danger' ? 'bg-danger' : u === 'warning' ? 'bg-warning' : u === 'success' ? 'bg-success' : 'bg-border-strong')} aria-hidden />
      <span className="min-w-0 flex-1">
        <span className="latin block truncate text-[15px] font-medium text-text">{a.title}</span>
        <span className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[13px] text-muted">
          {showCourse && (
            <span className="inline-flex min-w-0 items-center gap-1.5">
              <span className="size-2 shrink-0 rounded-full" style={{ background: a.courseColor }} aria-hidden />
              <span className="latin truncate">{a.courseName}</span>
            </span>
          )}
          {a.dueAt && (
            <span className={cn('inline-flex items-center gap-1 tnum', u === 'danger' && a.status === 'pending' && 'font-medium text-danger-fg')}>
              <CalendarClock className="size-3.5" aria-hidden />
              {dueLabel(a.dueAt, t.locale)}
            </span>
          )}
        </span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <StatusChip status={a.status} />
        {left && <span className={cn('text-[11.5px] font-medium tnum', u === 'danger' ? 'text-danger-fg' : 'text-muted')}>{left}</span>}
        {a.status === 'graded' && a.grade !== null && (
          <span className="latin text-[11.5px] font-semibold text-primary-fg tnum">
            {localDigits(a.grade, t.locale)}
            {a.maxPoints ? `/${localDigits(a.maxPoints, t.locale)}` : ''}
          </span>
        )}
      </span>
    </button>
  );
}

const MAT_ICON = { drive: FileText, link: Link2, youtube: Youtube, form: ClipboardList } as const;

export function MaterialChips({ materials }: { materials: Material[] }) {
  if (!materials.length) return null;
  return (
    <ul className="mt-2 flex flex-wrap gap-2">
      {materials.map((m, i) => {
        const Icon = MAT_ICON[m.kind];
        return (
          <li key={i}>
            <a href={m.url ?? undefined} target="_blank" rel="noreferrer noopener" className="latin inline-flex max-w-[260px] items-center gap-1.5 rounded-full border border-border bg-bg px-3 py-1 text-[13px] hover:border-primary hover:text-primary-fg">
              <Icon className="size-3.5 shrink-0" aria-hidden />
              <span className="truncate">{m.title}</span>
            </a>
          </li>
        );
      })}
    </ul>
  );
}

/** Detail sheet — read-only; action is a deep link to Classroom (never submit from here). */
export function AssignmentSheet({ a, onClose }: { a: Assignment | null; onClose: () => void }) {
  const t = useT();
  if (!a) return null;
  return (
    <Sheet
      open={!!a}
      onOpenChange={(o) => !o && onClose()}
      title={<span className="latin">{a.title}</span>}
      description={
        <span className="inline-flex items-center gap-1.5">
          <span className="size-2 rounded-full" style={{ background: a.courseColor }} aria-hidden />
          <span className="latin">{a.courseName}</span>
        </span>
      }
      footer={
        a.alternateLink ? (
          <Button asChild block size="lg">
            <a href={a.alternateLink} target="_blank" rel="noreferrer noopener">
              {t('gc.openInClassroom')} <ExternalLink className="size-4" aria-hidden />
            </a>
          </Button>
        ) : undefined
      }
    >
      <div className="flex flex-wrap items-center gap-2">
        <StatusChip status={a.status} />
        {a.late && <Badge tone="danger">{t('gc.late')}</Badge>}
        {a.status === 'graded' && a.grade !== null && (
          <Badge tone="primary">
            {t('gc.grade')}: {localDigits(a.grade, t.locale)}
            {a.maxPoints ? `/${localDigits(a.maxPoints, t.locale)}` : ''}
          </Badge>
        )}
      </div>
      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted">{t('gc.due')}</dt>
        <dd className="font-medium tnum">{a.dueAt ? dueLabel(a.dueAt, t.locale) : t('gc.noDue')}</dd>
        {a.maxPoints !== null && (
          <>
            <dt className="text-muted">{t('gc.points')}</dt>
            <dd className="latin tnum">{localDigits(a.maxPoints, t.locale)}</dd>
          </>
        )}
        {a.postedAt && (
          <>
            <dt className="text-muted">{t('gc.posted')}</dt>
            <dd>{relativeTime(a.postedAt, t.locale)}</dd>
          </>
        )}
      </dl>
      {a.description && <p className="mt-4 whitespace-pre-line text-[15px]">{a.description}</p>}
      <MaterialChips materials={a.materials} />
      <p className="mt-6 flex items-start gap-2 rounded-control bg-surface-2 px-3 py-2.5 text-[13px] text-muted">
        <Lock className="mt-0.5 size-4 shrink-0" aria-hidden /> {t('gc.readOnlyNote')}
      </p>
    </Sheet>
  );
}

const FEED_ICON = { assignment: ClipboardList, material: BookOpen, announcement: Megaphone, graded: Star } as const;

export function FeedRow({ f, onOpen }: { f: FeedItem; onOpen?: (f: FeedItem) => void }) {
  const t = useT();
  const Icon = FEED_ICON[f.kind];
  const body = (
    <>
      <span className="relative mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-control" style={{ background: `${f.courseColor}1a`, color: f.courseColor }}>
        <Icon className="size-[18px]" strokeWidth={1.75} aria-hidden />
        {!f.read && <span className="absolute -right-0.5 -top-0.5 size-2.5 rounded-full border-2 border-surface bg-primary" aria-label={t('gc.unread')} />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2 text-[12.5px] text-muted">
          <span className="font-semibold" style={{ color: f.courseColor }}>
            {t(`gc.kind.${f.kind}`)}
          </span>
          <span className="latin truncate">{f.courseName}</span>
          <span className="ml-auto shrink-0">{relativeTime(f.occurredAt, t.locale)}</span>
        </span>
        <span className={cn('latin mt-0.5 block text-[15px] leading-snug', f.read ? 'text-text' : 'font-semibold text-text')}>
          {f.kind === 'graded' && f.grade?.assigned !== null && f.grade ? `${f.title} — ${localDigits(f.grade.assigned!, t.locale)}${f.grade.max ? `/${localDigits(f.grade.max, t.locale)}` : ''}` : f.title}
        </span>
        {f.kind === 'material' && f.materials.length > 0 && <MaterialChips materials={f.materials.slice(0, 2)} />}
      </span>
    </>
  );
  return onOpen ? (
    <button type="button" onClick={() => onOpen(f)} className="flex w-full gap-3 rounded-control px-2 py-2.5 text-left hover:bg-surface-2">
      {body}
    </button>
  ) : (
    <div className="flex gap-3 px-2 py-2.5">{body}</div>
  );
}

export function CountdownCard({ exam }: { exam: { planId: string; subjectName: string; courseCode: string | null; examType: string; daysLeft: number; readiness: number; examDate: string } }) {
  const t = useT();
  return (
    <Link to={`/exam-mode/${exam.planId}`} className="gradient-brand group relative block overflow-hidden rounded-card p-5 text-white shadow-e2 transition-transform duration-200 hover:-translate-y-0.5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-white/90">
            <Timer className="size-4" aria-hidden /> {t(`examType.${exam.examType}`)}
            {exam.courseCode && <span className="latin">· {exam.courseCode}</span>}
          </p>
          <p className="latin mt-1 truncate text-xl font-bold">{exam.subjectName}</p>
          <p className="mt-3 text-[32px] font-bold leading-none tnum">
            {exam.daysLeft === 0 ? t('exam.today') : t('exam.daysLeft', { n: localDigits(exam.daysLeft, t.locale) })}
          </p>
          <p className="mt-1.5 text-sm text-white/85">{formatDate(exam.examDate, t.locale, { weekday: 'long', day: 'numeric', month: 'long' })}</p>
        </div>
        <ProgressRing value={exam.readiness} size={72} stroke={6} tone="onDark" label={t('exam.readiness', { p: localDigits(exam.readiness, t.locale) })} />
      </div>
      <span className="mt-4 inline-flex items-center gap-1.5 rounded-control bg-white/15 px-3 py-2 text-sm font-semibold group-hover:bg-white/25">{t('exam.open')} →</span>
    </Link>
  );
}

export function WeekStrip({ days, selected, onSelect }: { days: { date: string; deadlines: number; exams: number }[]; selected?: string | null; onSelect?: (d: string) => void }) {
  const t = useT();
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(new Date());
  return (
    <ol className="grid grid-cols-7 gap-1.5">
      {days.map((d) => {
        const date = new Date(`${d.date}T12:00:00+06:00`);
        const isToday = d.date === today;
        const sel = selected === d.date;
        const label = `${formatDate(date, t.locale, { weekday: 'long', day: 'numeric', month: 'long' })}: ${t('week.summary', { d: localDigits(d.deadlines, t.locale), e: localDigits(d.exams, t.locale) })}`;
        return (
          <li key={d.date}>
            <button
              type="button"
              onClick={() => onSelect?.(d.date)}
              aria-pressed={sel}
              aria-label={label}
              className={cn('flex h-[76px] w-full flex-col items-center justify-center gap-1 rounded-control border text-center transition-colors', sel ? 'border-primary bg-primary text-on-primary' : isToday ? 'border-primary/40 bg-primary-soft' : 'border-border bg-surface hover:border-border-strong')}
            >
              <span className={cn('text-[11.5px] font-medium', sel ? 'text-on-primary/85' : 'text-muted')}>{formatDate(date, t.locale, { weekday: 'short' })}</span>
              <span className="text-lg font-bold leading-none tnum">{localDigits(date.toLocaleDateString('en-GB', { timeZone: 'Asia/Dhaka', day: 'numeric' }), t.locale)}</span>
              <span className="flex h-2 items-center gap-1" aria-hidden>
                {d.deadlines > 0 && <span className={cn('size-1.5 rounded-full', sel ? 'bg-white' : 'bg-danger')} />}
                {d.deadlines > 1 && <span className={cn('size-1.5 rounded-full', sel ? 'bg-white' : 'bg-danger')} />}
                {d.exams > 0 && <span className={cn('size-1.5 rounded-full', sel ? 'bg-white' : 'bg-warning')} />}
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}

/** Sync status + refresh (manual sync is rate limited server-side to 1/min). */
export function SyncStatus({ status, compact }: { status: ClassroomStatus; compact?: boolean }) {
  const t = useT();
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () => api(contracts.classroom.sync),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['classroom'] });
      toast.success(t('gc.synced'));
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  const label = status.status === 'syncing' || m.isPending ? t('gc.syncing') : status.lastSyncAt ? t('gc.syncedAgo', { when: relativeTime(status.lastSyncAt, t.locale) }) : t('gc.notSynced');
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-[13px] text-muted', compact && 'text-xs')}>
      <CheckCircle2 className={cn('size-4', status.status === 'active' ? 'text-success' : 'text-warning')} aria-hidden />
      <span aria-live="polite">{label}</span>
      <button type="button" onClick={() => m.mutate()} disabled={m.isPending || status.status === 'syncing'} className="-m-1 inline-flex size-8 items-center justify-center rounded-full hover:bg-surface-2 hover:text-text disabled:opacity-50" aria-label={t('gc.refresh')}>
        <RefreshCw className={cn('size-4', (m.isPending || status.status === 'syncing') && 'animate-spin')} aria-hidden />
      </button>
    </span>
  );
}

/** Not-connected / needs-reauth / workspace-blocked card (calm, prominent). */
export function ConnectCard({ status, returnTo = '/home', className }: { status: ClassroomStatus | null; returnTo?: string; className?: string }) {
  const t = useT();
  const [why, setWhy] = useState(false);
  let title = t('gc.connectTitle');
  let body: ReactNode = t('gc.connectBody');
  let tone: 'primary' | 'warning' | 'danger' = 'primary';
  if (status?.status === 'needs_reauth') {
    title = t('gc.reauthTitle');
    body = t('gc.reauthBody');
    tone = 'warning';
  } else if (status?.status === 'workspace_blocked') {
    title = t('gc.blockedTitle');
    body = t('gc.blockedBody');
    tone = 'danger';
  }
  return (
    <section className={cn('surface-card overflow-hidden', className)} aria-labelledby="gc-connect-h">
      <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
        <span className={cn('flex size-14 shrink-0 items-center justify-center rounded-card', tone === 'primary' ? 'bg-primary-soft text-primary' : tone === 'warning' ? 'bg-warning-soft text-warning-fg' : 'bg-danger-soft text-danger-fg')}>
          {tone === 'primary' ? <Bell className="size-7" strokeWidth={1.75} aria-hidden /> : tone === 'warning' ? <AlertTriangle className="size-7" strokeWidth={1.75} aria-hidden /> : <ShieldAlert className="size-7" strokeWidth={1.75} aria-hidden />}
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="gc-connect-h" className="text-lg font-semibold">
            {title}
          </h2>
          <p className="mt-0.5 text-[15px] text-muted">{body}</p>
          {status?.status === 'workspace_blocked' && (
            <button type="button" onClick={() => setWhy((w) => !w)} className="mt-1 text-sm font-semibold text-primary-fg hover:underline" aria-expanded={why}>
              {t('gc.blockedWhy')}
            </button>
          )}
          {why && <p className="mt-2 rounded-control bg-surface-2 px-3 py-2 text-sm text-muted">{t('gc.blockedHelp')}</p>}
        </div>
        <div className="shrink-0 sm:w-64">
          <ConnectClassroomButton returnTo={returnTo} label={status?.status === 'needs_reauth' ? t('gc.reconnect') : undefined} />
        </div>
      </div>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-border bg-bg px-5 py-2.5 text-[12.5px] text-muted">
        <span className="inline-flex items-center gap-1.5">
          <Lock className="size-3.5" aria-hidden /> {t('gc.privacy')}
        </span>
        <span>{t('brand.disclaimerGoogle')}</span>
      </p>
    </section>
  );
}

export function SectionCard({ title, icon, action, children, className, id }: { title: ReactNode; icon?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; id?: string }) {
  return (
    <section className={cn('surface-card p-4 sm:p-5', className)} aria-labelledby={id}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 id={id} className="flex items-center gap-2 text-[17px] font-semibold">
          {icon}
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

