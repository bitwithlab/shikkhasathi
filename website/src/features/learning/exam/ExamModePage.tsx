import './i18n';
import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpenCheck, CalendarClock, ChevronDown, CircleCheck, Circle, ClipboardList, FileText, Layers, Lock, Moon, Pencil, PlayCircle, Plus, Star, Timer, Trash2 } from 'lucide-react';
import type { ExamPlanDetail, PlanDay, PlanItem } from '@/api';
import { Badge, Button, Dialog, EmptyState, ErrorState, Illustration, ProgressRing, Sheet, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { formatDate, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { PlanForm } from './PlanForm';
import { FlashcardDeck } from './Flashcards';

const mins = (sec: number) => Math.max(1, Math.round(sec / 60));

export default function ExamModePage() {
  const { planId } = useParams();
  const t = useT();
  const nav = useNavigate();
  const plans = useQuery({ queryKey: ['exam-plans'], queryFn: () => api(contracts.exam.plans) });
  useEffect(() => {
    document.title = `${t('ex.title')} — ${t('brand.name')}`;
  }, [t]);
  if (!planId) {
    if (plans.isLoading) return <DetailSkeleton />;
    if (plans.isError) return <div className="p-6"><ErrorState onRetry={() => void plans.refetch()} /></div>;
    const next = plans.data?.items.find((p) => p.active && !p.past);
    if (next) return <Navigate to={`/exam-mode/${next.id}`} replace />;
    return (
      <div className="mx-auto max-w-xl px-4 py-8">
        <div className="flex flex-col items-center text-center">
          <span className="grid size-20 place-items-center rounded-full bg-primary-soft text-primary-fg shadow-e1"><Timer className="size-10" strokeWidth={1.5} aria-hidden /></span>
          <h1 className="mt-5 text-2xl font-bold">{t('ex.formTitle')}</h1>
          <p className="mt-1 max-w-sm text-muted">{t('ex.formBody')}</p>
        </div>
        <div className="mt-8"><PlanForm onDone={(p) => nav(`/exam-mode/${p.id}`)} /></div>
      </div>
    );
  }
  return <Detail planId={planId} />;
}

function Detail({ planId }: { planId: string }) {
  const t = useT();
  const nav = useNavigate();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['exam-plan', planId], queryFn: () => api(contracts.exam.get, { params: { id: planId } }) });
  const plans = useQuery({ queryKey: ['exam-plans'], queryFn: () => api(contracts.exam.plans) });
  const [edit, setEdit] = useState(false);
  const [add, setAdd] = useState(false);
  const [cards, setCards] = useState(false);
  const [del, setDel] = useState(false);
  const remove = useMutation({
    mutationFn: () => api(contracts.exam.remove, { params: { id: planId } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['exam-plans'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      nav('/exam-mode', { replace: true });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  if (q.isLoading) return <DetailSkeleton />;
  if (q.isError || !q.data)
    return (
      <div className="mx-auto max-w-lg px-4 py-16">
        {q.error instanceof ApiError && (q.error.status === 404 || q.error.status === 403) ? <EmptyState illustration={<Illustration kind="search" className="h-28 w-32" />} title={t('ex.empty')} action={<Button asChild><Link to="/exam-mode">{t('ex.title')}</Link></Button>} /> : <ErrorState onRetry={() => void q.refetch()} />}
      </div>
    );
  const d = q.data;
  const others = (plans.data?.items ?? []).filter((p) => p.id !== d.id && p.active && !p.past);
  return (
    <div className="mx-auto max-w-6xl px-4 py-6 lg:py-8">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <h1 className="mr-auto text-2xl font-bold">{t('ex.title')}</h1>
        {others.length > 0 && (
          <div className="flex gap-2 overflow-x-auto scrollbar-none">
            {others.map((p) => (
              <Link key={p.id} to={`/exam-mode/${p.id}`} className="latin whitespace-nowrap rounded-full border border-border bg-surface px-3 py-1.5 text-sm font-medium hover:border-border-strong">
                {p.courseCode ?? p.subjectName} · {localDigits(p.daysLeft, t.locale)}d
              </Link>
            ))}
          </div>
        )}
        <Button variant="secondary" size="sm" onClick={() => setAdd(true)} icon={<Plus className="size-4" aria-hidden />}>{t('ex.add')}</Button>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px] [&>*]:min-w-0">
        <div className="flex flex-col gap-5">
          <Countdown d={d} onEdit={() => setEdit(true)} onDelete={() => setDel(true)} />
          <Today d={d} />
          <Syllabus d={d} />
          <Important d={d} />
        </div>
        <aside className="flex flex-col gap-5">
          <Mocks d={d} />
          <FlashCard d={d} onOpen={() => setCards(true)} />
          <Revision d={d} />
          <Pq d={d} />
          <Upcoming days={d.days} />
          <p className="text-xs text-muted">{t('ex.disclaimer')}</p>
        </aside>
      </div>

      <Sheet open={edit} onOpenChange={setEdit} title={t('ex.edit')} side="right" className="sm:max-w-lg">
        <PlanForm plan={d} onDone={() => setEdit(false)} />
      </Sheet>
      <Sheet open={add} onOpenChange={setAdd} title={t('ex.add')} side="right" className="sm:max-w-lg">
        <PlanForm onDone={(p) => { setAdd(false); nav(`/exam-mode/${p.id}`); }} />
      </Sheet>
      <Sheet open={cards} onOpenChange={setCards} title={t('fc.title')} className="max-h-[92dvh]">
        <FlashcardDeck planId={d.id} />
      </Sheet>
      <Dialog open={del} onOpenChange={setDel} title={t('ex.deleteConfirm')} footer={<><Button variant="ghost" onClick={() => setDel(false)}>{t('common.cancel')}</Button><Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>{t('ex.delete')}</Button></>}>
        <p className="latin text-muted">{d.subjectName}</p>
      </Dialog>
    </div>
  );
}

function Countdown({ d, onEdit, onDelete }: { d: ExamPlanDetail; onEdit: () => void; onDelete: () => void }) {
  const t = useT();
  const label = d.past ? t('ex.done') : d.daysLeft === 0 ? t('ex.today') : d.daysLeft === 1 ? t('ex.tomorrow') : t('ex.daysLeft', { n: localDigits(d.daysLeft, t.locale) });
  const rb = d.readinessBreakdown;
  return (
    // gradient allowed here (CLAUDE.md §7: exam countdown card)
    <section className="relative overflow-hidden rounded-card bg-[linear-gradient(135deg,#4F46E5_0%,#6D28D9_55%,#9333EA_100%)] p-5 text-white shadow-e2 sm:p-6" aria-labelledby="cd-h">
      <div className="flex flex-wrap items-start gap-5">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2 text-sm text-white/85">
            <Badge className="border-white/25 bg-white/15 text-white">{t(`examType.${d.examType}`)}</Badge>
            {d.courseCode && <span className="latin font-semibold">{d.courseCode}</span>}
            <span className="ml-auto flex gap-1">
              <button type="button" onClick={onEdit} className="grid size-9 place-items-center rounded-full text-white/85 hover:bg-white/15" aria-label={t('ex.edit')}><Pencil className="size-4" aria-hidden /></button>
              <button type="button" onClick={onDelete} className="grid size-9 place-items-center rounded-full text-white/85 hover:bg-white/15" aria-label={t('ex.delete')}><Trash2 className="size-4" aria-hidden /></button>
            </span>
          </div>
          <h2 id="cd-h" className="latin mt-2 text-xl font-bold sm:text-2xl">{d.subjectName}</h2>
          <p className="mt-3 text-4xl font-extrabold tracking-tight tnum sm:text-5xl">{label}</p>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-white/85">
            <CalendarClock className="size-4" aria-hidden />
            {formatDate(d.examDate, t.locale, { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Asia/Dhaka' })} · {formatDate(d.examDate, t.locale, { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Dhaka' })}
          </p>
        </div>
        <div className="flex flex-col items-center">
          <ProgressRing value={d.readiness} size={104} stroke={9} tone="onDark" label={`${t('ex.readiness')} ${d.readiness}%`} />
          <span className="mt-1.5 text-sm font-semibold">{t('ex.readiness')}</span>
        </div>
      </div>
      <dl className="mt-5 grid grid-cols-3 gap-2 border-t border-white/20 pt-4 text-center text-sm">
        {(['lessons', 'mock', 'flashcards'] as const).map((k) => (
          <div key={k}>
            <dt className="text-white/75">{t(`ex.rb.${k}`)}</dt>
            <dd className="text-lg font-bold tnum">{rb[k] === null ? '—' : `${localDigits(rb[k]!, t.locale)}%`}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function ItemRow({ it }: { it: PlanItem }) {
  const t = useT();
  const done = it.done;
  const Icon = done ? CircleCheck : Circle;
  const body =
    it.kind === 'lesson'
      ? { to: `/learn/${it.courseId}/${it.lessonId}`, title: it.title, sub: `${it.chapterTitle} · ${t('mk.minutes', { n: localDigits(it.durationSec ? mins(it.durationSec) : 10, t.locale) })}`, star: it.isImportant }
      : it.kind === 'mock'
        ? { to: `/mock-test/${it.testId}`, title: it.title, sub: `${t('ex.mocks')} · ${t('ex.mockItem', { n: localDigits(it.questionCount, t.locale) })}`, star: false }
        : it.kind === 'flashcards'
          ? { to: null, title: t('ex.flashItem', { n: localDigits(it.count, t.locale) }), sub: t('ex.flash'), star: false }
          : { to: '/notes', title: t('ex.revisionDay'), sub: t('ex.revisionItem'), star: false };
  const inner = (
    <>
      <Icon className={cn('mt-0.5 size-5 shrink-0', done ? 'text-success' : 'text-border-strong')} aria-label={done ? t('ex.st.done') : t('ex.st.todo')} />
      <span className="min-w-0 flex-1">
        <span className={cn('latin block font-medium leading-snug', done && 'text-muted line-through decoration-1')}>{body.title}</span>
        <span className="latin mt-0.5 block truncate text-xs text-muted">{body.sub}</span>
      </span>
      {body.star && <Star className="mt-0.5 size-4 shrink-0 fill-warning text-warning" aria-label={t('pl.important')} />}
    </>
  );
  return body.to ? (
    <Link to={body.to} className="flex items-start gap-3 rounded-control px-2 py-2.5 transition-colors hover:bg-surface-2">{inner}</Link>
  ) : (
    <div className="flex items-start gap-3 px-2 py-2.5">{inner}</div>
  );
}

function Today({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  const td = d.today;
  const total = td?.items.length ?? 0;
  const done = td?.items.filter((i) => i.done).length ?? 0;
  const allDone = total > 0 && done === total;
  const [celebrate, setCelebrate] = useState(false);
  useEffect(() => {
    if (!allDone || !td) return;
    const k = `ss.planDone.${d.id}.${td.date}`;
    try {
      if (localStorage.getItem(k)) return;
      localStorage.setItem(k, '1');
    } catch {
      /* ignore */
    }
    setCelebrate(true);
  }, [allDone, td, d.id]);
  return (
    <section className="surface-card relative overflow-hidden p-4 sm:p-5" aria-labelledby="today-h">
      {celebrate && <Confetti />}
      <div className="flex items-center gap-3">
        <h2 id="today-h" className="flex items-center gap-2 text-[17px] font-semibold"><ClipboardList className="size-5 text-primary" aria-hidden />{t('ex.todayPlan')}</h2>
        {td && <span className="ml-auto text-sm text-muted tnum">{localDigits(done, t.locale)}/{localDigits(total, t.locale)} · {t('ex.todayMin', { n: localDigits(td.totalMinutes, t.locale) })}</span>}
      </div>
      {td && <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-2"><div className="h-full rounded-full bg-success transition-[width] duration-300" style={{ width: `${total ? (done / total) * 100 : 0}%` }} /></div>}
      {!d.courseIds.length ? (
        <EmptyState compact title={t('ex.noCoursesPlan')} action={<Button asChild size="sm" variant="secondary"><Link to="/explore">{t('nav.explore')}</Link></Button>} />
      ) : !td || !total ? (
        <EmptyState compact title={t('ex.noPlanToday')} />
      ) : (
        <>
          {td.isRevision && <Badge tone="warning" className="mt-3" icon={<Moon className="size-3.5" aria-hidden />}>{t('ex.revisionDay')}</Badge>}
          <ul className="mt-2">{td.items.map((it, i) => <li key={i}><ItemRow it={it} /></li>)}</ul>
          {allDone && <p className="mt-2 rounded-control bg-success-soft px-3 py-2 text-sm font-semibold text-success-fg">{t('ex.todayDone')}</p>}
        </>
      )}
    </section>
  );
}

function Syllabus({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  const [open, setOpen] = useState<string | null>(null);
  const syl = d.syllabus;
  const groups = useMemo(() => syl.reduce<Record<string, typeof syl>>((a, c) => ((a[c.sectionTitle] ??= []).push(c), a), {}), [syl]);
  if (!syl.length) return null;
  const tone = { todo: 'neutral', doing: 'warning', done: 'success' } as const;
  return (
    <section className="surface-card p-4 sm:p-5" aria-labelledby="syl-h">
      <h2 id="syl-h" className="flex items-center gap-2 text-[17px] font-semibold"><BookOpenCheck className="size-5 text-primary" aria-hidden />{t('ex.syllabus')}</h2>
      <div className="mt-3 flex flex-col gap-4">
        {Object.entries(groups).map(([sec, chs]) => (
          <div key={sec}>
            <button type="button" onClick={() => setOpen(open === sec ? null : sec)} className="latin flex w-full items-center gap-2 text-left text-sm font-semibold text-muted" aria-expanded={open !== sec}>
              <ChevronDown className={cn('size-4 transition-transform', open === sec && '-rotate-90')} aria-hidden />
              {sec}
            </button>
            {open !== sec && (
              <ul className="mt-2 divide-y divide-border rounded-control border border-border">
                {chs.map((c) => (
                  <li key={c.chapterId} className="flex items-center gap-3 px-3 py-2.5">
                    <span className="latin min-w-0 flex-1 truncate text-[15px]">{c.title}</span>
                    {c.important > 0 && <span className="flex items-center gap-0.5 text-xs text-warning-fg"><Star className="size-3.5 fill-warning text-warning" aria-hidden />{localDigits(c.important, t.locale)}</span>}
                    <span className="text-xs text-muted tnum">{localDigits(c.done, t.locale)}/{localDigits(c.total, t.locale)}</span>
                    <Badge tone={tone[c.status]}>{t(`ex.st.${c.status}`)}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}

function Important({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  if (!d.important.length && !d.shortLectures.length) return null;
  return (
    <section className="grid gap-5 sm:grid-cols-2 [&>*]:min-w-0">
      {d.important.length > 0 && (
        <div className="surface-card p-4">
          <h2 className="text-[15px] font-semibold">{t('ex.important')}</h2>
          <ul className="mt-2">
            {d.important.slice(0, 8).map((l) => (
              <li key={l.lessonId}><Link to={`/learn/${l.courseId}/${l.lessonId}`} className="flex items-center gap-2 rounded-control px-2 py-2 text-sm hover:bg-surface-2">{l.done ? <CircleCheck className="size-4 shrink-0 text-success" aria-hidden /> : <Star className="size-4 shrink-0 fill-warning text-warning" aria-hidden />}<span className="latin min-w-0 flex-1 truncate">{l.title}</span></Link></li>
            ))}
          </ul>
        </div>
      )}
      {d.shortLectures.length > 0 && (
        <div className="surface-card p-4">
          <h2 className="text-[15px] font-semibold">{t('ex.short')}</h2>
          <ul className="mt-2">
            {d.shortLectures.slice(0, 8).map((l) => (
              <li key={l.lessonId}><Link to={`/learn/${l.courseId}/${l.lessonId}`} className="flex items-center gap-2 rounded-control px-2 py-2 text-sm hover:bg-surface-2"><PlayCircle className={cn('size-4 shrink-0', l.done ? 'text-success' : 'text-muted')} aria-hidden /><span className="latin min-w-0 flex-1 truncate">{l.title}</span><span className="text-xs text-muted tnum">{localDigits(mins(l.durationSec), t.locale)}m</span></Link></li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function Mocks({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  if (!d.mockTests.length) return null;
  return (
    <section className="surface-card p-4" aria-labelledby="mk-h">
      <h2 id="mk-h" className="flex items-center gap-2 text-[15px] font-semibold"><Timer className="size-5 text-primary" aria-hidden />{t('ex.mocks')}</h2>
      <ul className="mt-3 flex flex-col gap-2">
        {d.mockTests.map((m) => (
          <li key={m.id} className="flex items-center gap-3 rounded-control border border-border p-3">
            <div className="min-w-0 flex-1">
              <p className="latin truncate font-medium">{m.title}</p>
              <p className="text-xs text-muted">{t('mk.questions', { n: localDigits(m.questionCount, t.locale) })} · {t('mk.minutes', { n: localDigits(mins(m.durationSec), t.locale) })}{m.bestScore !== null && ` · ${t('mk.best', { n: localDigits(m.bestScore, t.locale) })}`}</p>
            </div>
            <Button asChild size="sm" variant={m.inProgressAttemptId || !m.attempts ? 'primary' : 'secondary'}><Link to={`/mock-test/${m.id}`}>{m.inProgressAttemptId ? t('mk.resume') : m.attempts ? t('mk.retry') : t('mk.start')}</Link></Button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function FlashCard({ d, onOpen }: { d: ExamPlanDetail; onOpen: () => void }) {
  const t = useT();
  if (!d.flashcards.total) return null;
  const pct = Math.round((d.flashcards.known / d.flashcards.total) * 100);
  return (
    <section className="surface-card flex items-center gap-4 p-4" aria-labelledby="fc-h">
      <ProgressRing value={pct} size={52} tone="primary" label={t('ex.flashProgress', { known: d.flashcards.known, total: d.flashcards.total })} />
      <div className="min-w-0 flex-1">
        <h2 id="fc-h" className="flex items-center gap-2 text-[15px] font-semibold"><Layers className="size-4 text-primary" aria-hidden />{t('ex.flash')}</h2>
        <p className="text-sm text-muted tnum">{t('ex.flashProgress', { known: localDigits(d.flashcards.known, t.locale), total: localDigits(d.flashcards.total, t.locale) })}</p>
      </div>
      <Button size="sm" onClick={onOpen}>{t('ex.flashStart')}</Button>
    </section>
  );
}

function Revision({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  const r = d.revisionPack;
  return (
    <section className={cn('rounded-card border p-4', d.revisionUnlocked ? 'border-warning/40 bg-warning-soft/60' : 'border-dashed border-border bg-surface')} aria-labelledby="rv-h">
      <h2 id="rv-h" className="flex items-center gap-2 text-[15px] font-semibold">{d.revisionUnlocked ? <Moon className="size-4 text-warning-fg" aria-hidden /> : <Lock className="size-4 text-muted" aria-hidden />}{t('ex.revision')}</h2>
      {d.revisionUnlocked ? (
        <>
          <p className="mt-1 text-sm">{t('ex.revisionBody', { imp: localDigits(r.importantLessons, t.locale), notes: localDigits(r.notes, t.locale), bm: localDigits(r.bookmarks, t.locale), wrong: localDigits(r.wrongAnswers, t.locale) })}</p>
          <Button asChild size="sm" variant="secondary" className="mt-3"><Link to="/notes">{t('ex.openNotes')}</Link></Button>
        </>
      ) : (
        <p className="mt-1 text-sm text-muted">{t('ex.revisionLocked')}</p>
      )}
    </section>
  );
}

function Pq({ d }: { d: ExamPlanDetail }) {
  const t = useT();
  const open = useMutation({ mutationFn: (id: string) => api(contracts.learning.document, { params: { id } }), onSuccess: (r) => window.open(r.url, '_blank', 'noopener'), onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')) });
  if (!d.previousQuestions.length) return null;
  return (
    <section className="surface-card p-4" aria-labelledby="pq-h">
      <h2 id="pq-h" className="flex items-center gap-2 text-[15px] font-semibold"><FileText className="size-4 text-primary" aria-hidden />{t('ex.pq')}</h2>
      <div className="mt-3 flex flex-wrap gap-2">
        {d.previousQuestions.map((p) => (
          <button key={p.id} type="button" onClick={() => open.mutate(p.id)} className="latin rounded-full border border-border bg-surface px-3 py-1.5 text-sm font-semibold tnum hover:border-border-strong" title={p.title}>{p.year ? localDigits(p.year, t.locale) : p.title}</button>
        ))}
      </div>
    </section>
  );
}

function Upcoming({ days }: { days: PlanDay[] }) {
  const t = useT();
  const rest = days.slice(1);
  if (!rest.length) return null;
  return (
    <section className="surface-card p-4" aria-labelledby="up-h">
      <h2 id="up-h" className="text-[15px] font-semibold">{t('ex.upcoming')}</h2>
      <ol className="mt-2">
        {rest.map((d) => {
          const n = d.items.filter((i) => i.kind === 'lesson').length;
          return (
            <li key={d.date} className="flex items-center gap-3 border-b border-border py-2 text-sm last:border-0">
              <span className="w-20 shrink-0 font-medium">{formatDate(`${d.date}T06:00:00Z`, t.locale, { weekday: 'short', day: 'numeric' })}</span>
              <span className="min-w-0 flex-1 truncate text-muted">{d.isRevision ? t('ex.revisionDay') : t('ex.lessonsN', { n: localDigits(n, t.locale) })}</span>
              {!d.isRevision && <span className="text-xs text-muted tnum">{localDigits(d.totalMinutes, t.locale)}m</span>}
            </li>
          );
        })}
      </ol>
    </section>
  );
}

function Confetti() {
  const pieces = useMemo(() => Array.from({ length: 18 }, (_, i) => ({ left: (i * 53) % 100, delay: (i % 6) * 0.12, color: ['#4F46E5', '#10B981', '#F59E0B', '#EC4899', '#0EA5E9'][i % 5], rot: (i * 37) % 360 })), []);
  return (
    <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-72 motion-reduce:hidden">
      {pieces.map((p, i) => <span key={i} className="absolute top-0 block h-2.5 w-1.5 rounded-sm" style={{ left: `${p.left}%`, background: p.color, transform: `rotate(${p.rot}deg)`, animation: `ss-confetti 1.8s ${p.delay}s ease-out both` }} />)}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="mx-auto grid max-w-6xl gap-5 px-4 py-8 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div className="flex flex-col gap-5"><Skeleton className="h-60 rounded-card" /><Skeleton className="h-72 rounded-card" /></div>
      <div className="flex flex-col gap-5"><Skeleton className="h-40 rounded-card" /><Skeleton className="h-24 rounded-card" /></div>
    </div>
  );
}
