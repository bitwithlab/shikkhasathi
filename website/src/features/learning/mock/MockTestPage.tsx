import '../exam/i18n';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Flag, Grid3x3, Timer } from 'lucide-react';
import type { Attempt } from '@/api';
import { Button, Dialog, ErrorState, Sheet, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { fmtTime } from '../player/time';

type Ans = Record<string, { optionId: string | null; flagged: boolean }>;

export default function MockTestPage() {
  const { id = '' } = useParams();
  const t = useT();
  // POST start returns the open attempt when one exists (resume) — never call twice per mount
  const q = useQuery({ queryKey: ['attempt-start', id], queryFn: () => api(contracts.exam.start, { params: { id } }), staleTime: Infinity, gcTime: 0, retry: false, refetchOnWindowFocus: false });
  useEffect(() => {
    if (q.data) document.title = `${q.data.title} — ${t('brand.name')}`;
  }, [q.data, t]);
  if (q.isLoading) return <div className="mx-auto max-w-3xl px-4 py-8"><Skeleton className="h-12 rounded-card" /><Skeleton className="mt-4 h-72 rounded-card" /></div>;
  if (q.isError || !q.data) return <div className="mx-auto max-w-lg px-4 py-16"><ErrorState body={q.error instanceof ApiError ? q.error.message : undefined} onRetry={() => void q.refetch()} /></div>;
  return <Runner a={q.data} />;
}

function Runner({ a }: { a: Attempt }) {
  const t = useT();
  const nav = useNavigate();
  const skew = useMemo(() => Date.parse(a.serverNow) - Date.now(), [a.serverNow]);
  const deadline = Date.parse(a.deadlineAt);
  const [now, setNow] = useState(() => Date.now() + skew);
  const [i, setI] = useState(() => {
    const firstOpen = a.questions.findIndex((q) => !a.answers.some((x) => x.questionId === q.id && x.optionId));
    return firstOpen < 0 ? 0 : firstOpen;
  });
  const [ans, setAns] = useState<Ans>(() => Object.fromEntries(a.answers.map((x) => [x.questionId, { optionId: x.optionId, flagged: x.flagged }])));
  const [pending, setPending] = useState(0);
  const [failed, setFailed] = useState<Set<string>>(new Set());
  const [nav2, setNav2] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const submitted = useRef(false);
  const ansRef = useRef(ans);
  ansRef.current = ans;

  useEffect(() => {
    if (a.answers.length) toast.success(t('mt.resumed'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = useCallback(
    async (questionId: string, v: { optionId: string | null; flagged: boolean }) => {
      setPending((n) => n + 1);
      try {
        await api(contracts.exam.answer, { params: { id: a.id }, body: { questionId, optionId: v.optionId, flagged: v.flagged } });
        setFailed((s) => { const n = new Set(s); n.delete(questionId); return n; });
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) return void submit.mutate();
        setFailed((s) => new Set(s).add(questionId));
      } finally {
        setPending((n) => n - 1);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [a.id],
  );
  const current = (qid: string) => ansRef.current[qid] ?? { optionId: null, flagged: false };

  const submit = useMutation({
    mutationFn: async () => {
      // flush anything that failed to save
      await Promise.all([...failed].map((qid) => save(qid, current(qid))));
      return api(contracts.exam.submit, { params: { id: a.id } });
    },
    onSuccess: (r) => nav(`/mock-test/${r.attemptId}/result`, { replace: true }),
    onError: (e) => {
      submitted.current = false;
      toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));
    },
  });
  const doSubmit = useCallback(() => {
    if (submitted.current) return;
    submitted.current = true;
    submit.mutate();
  }, [submit]);

  // timer + auto-submit
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now() + skew), 500);
    return () => window.clearInterval(id);
  }, [skew]);
  const left = Math.max(0, Math.round((deadline - now) / 1000));
  useEffect(() => {
    if (left === 0) doSubmit();
  }, [left, doSubmit]);
  // retry failed saves every 10 s
  useEffect(() => {
    if (!failed.size) return;
    const id = window.setTimeout(() => failed.forEach((qid) => void save(qid, current(qid))), 10_000);
    return () => window.clearTimeout(id);
  }, [failed, save]);

  const q = a.questions[i]!;
  const cur = ans[q.id];
  const answered = a.questions.filter((x) => ans[x.id]?.optionId).length;
  // compute the next value from the latest state and save exactly that (never a stale ref)
  const update = (patch: Partial<{ optionId: string | null; flagged: boolean }>) => {
    const next = { ...current(q.id), ...patch };
    ansRef.current = { ...ansRef.current, [q.id]: next };
    setAns(ansRef.current);
    void save(q.id, next);
  };
  const choose = (optionId: string | null) => update({ optionId });
  const flag = () => update({ flagged: !current(q.id).flagged });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (confirm || nav2) return;
      if (e.key === 'ArrowRight') setI((n) => Math.min(a.questions.length - 1, n + 1));
      else if (e.key === 'ArrowLeft') setI((n) => Math.max(0, n - 1));
      else if (/^[1-6]$/.test(e.key)) {
        const o = q.options[Number(e.key) - 1];
        if (o) choose(o.id);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const urgent = left <= 60;
  const warn = left <= 300;
  if (submit.isPending || (left === 0 && submitted.current))
    return (
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center" role="status">
        <span className="size-10 animate-spin rounded-full border-[3px] border-primary border-r-transparent motion-reduce:animate-none" aria-hidden />
        <p className="mt-5 font-semibold">{left === 0 ? t('mt.timeUp') : t('mt.saving')}</p>
      </div>
    );

  const navigator = (
    <div>
      <div className="mb-3 flex flex-wrap gap-3 text-xs text-muted">
        <span className="flex items-center gap-1.5"><span className="size-3 rounded bg-primary" />{t('mt.answered')}</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded border border-border-strong" />{t('mt.unanswered')}</span>
        <span className="flex items-center gap-1.5"><Flag className="size-3 text-warning" aria-hidden />{t('mt.flagged')}</span>
      </div>
      <ol className="grid grid-cols-6 gap-2 sm:grid-cols-8">
        {a.questions.map((x, n) => {
          const s = ans[x.id];
          return (
            <li key={x.id}>
              <button
                type="button"
                onClick={() => { setI(n); setNav2(false); }}
                aria-label={`${t('mt.q', { i: n + 1, n: a.questions.length })}${s?.optionId ? ` · ${t('mt.answered')}` : ''}${s?.flagged ? ` · ${t('mt.flagged')}` : ''}`}
                aria-current={n === i ? 'step' : undefined}
                className={cn('relative grid h-10 w-full place-items-center rounded-control text-sm font-semibold tnum transition-colors', s?.optionId ? 'bg-primary text-on-primary' : 'border border-border-strong hover:bg-surface-2', n === i && 'ring-2 ring-primary ring-offset-2 ring-offset-bg')}
              >
                {localDigits(n + 1, t.locale)}
                {s?.flagged && <Flag className="absolute -right-1 -top-1 size-3.5 fill-warning text-warning" aria-hidden />}
              </button>
            </li>
          );
        })}
      </ol>
    </div>
  );

  return (
    <div className="mx-auto max-w-5xl px-4 py-4 lg:py-6">
      {/* sticky test bar */}
      <div className="sticky top-14 z-20 -mx-4 flex items-center gap-3 border-b border-border bg-bg/95 px-4 py-2.5 backdrop-blur-sm lg:top-0">
        <p className="latin min-w-0 flex-1 truncate font-semibold">{a.title}</p>
        <span className="hidden text-xs text-muted sm:inline" aria-live="polite">{failed.size ? t('mt.offline') : pending ? t('mt.saving') : t('mt.saved')}</span>
        <span role="timer" aria-label={t('mt.timeLeft')} className={cn('flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-bold tnum', urgent ? 'bg-danger text-white' : warn ? 'bg-warning-soft text-warning-fg' : 'bg-surface-2')}>
          <Timer className="size-4" aria-hidden />
          {localDigits(fmtTime(left), t.locale)}
        </span>
        <Button size="sm" onClick={() => setConfirm(true)}>{t('mt.submit')}</Button>
      </div>

      <div className="mt-5 grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <section aria-labelledby="q-h" className="min-w-0">
          <div className="flex items-center gap-2 text-sm text-muted">
            <span id="q-h" className="font-semibold text-text tnum">{t('mt.q', { i: localDigits(i + 1, t.locale), n: localDigits(a.questions.length, t.locale) })}</span>
            {q.chapterTag && <span className="latin">· {q.chapterTag}</span>}
            <button type="button" onClick={flag} aria-pressed={!!cur?.flagged} className={cn('ml-auto flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium', cur?.flagged ? 'bg-warning-soft text-warning-fg' : 'hover:bg-surface-2')}>
              <Flag className={cn('size-4', cur?.flagged && 'fill-current')} aria-hidden />
              {cur?.flagged ? t('mt.flagged') : t('mt.flag')}
            </button>
          </div>
          <p className="mt-3 whitespace-pre-wrap text-lg font-semibold leading-relaxed">{q.body}</p>
          <div role="radiogroup" aria-labelledby="q-h" className="mt-5 flex flex-col gap-3">
            {q.options.map((o, n) => {
              const on = cur?.optionId === o.id;
              return (
                <button key={o.id} type="button" role="radio" aria-checked={on} onClick={() => choose(o.id)} className={cn('flex items-start gap-3 rounded-card border p-4 text-left transition-[border-color,background-color] duration-150', on ? 'border-2 border-primary bg-primary-soft/50' : 'border-border bg-surface hover:border-border-strong')}>
                  <span className={cn('grid size-7 shrink-0 place-items-center rounded-full border text-sm font-bold', on ? 'border-primary bg-primary text-on-primary' : 'border-border-strong text-muted')}>{String.fromCharCode(65 + n)}</span>
                  <span className="latin pt-0.5 text-[15px]">{o.body}</span>
                </button>
              );
            })}
          </div>
          {cur?.optionId && <button type="button" onClick={() => choose(null)} className="mt-3 text-sm text-muted underline hover:text-text">{t('mt.clear')}</button>}
          <div className="mt-8 flex items-center gap-2">
            <Button variant="secondary" disabled={i === 0} onClick={() => setI(i - 1)} icon={<ChevronLeft className="size-4" aria-hidden />}>{t('mt.prev')}</Button>
            <Button variant="ghost" className="lg:hidden" onClick={() => setNav2(true)} icon={<Grid3x3 className="size-4" aria-hidden />}>{localDigits(answered, t.locale)}/{localDigits(a.questions.length, t.locale)}</Button>
            <span className="flex-1" />
            {i < a.questions.length - 1 ? (
              <Button onClick={() => setI(i + 1)}>{t('mt.next')}<ChevronRight className="size-4" aria-hidden /></Button>
            ) : (
              <Button onClick={() => setConfirm(true)}>{t('mt.submit')}</Button>
            )}
          </div>
        </section>
        <aside className="hidden lg:block" aria-label={t('mt.navigator')}>
          <div className="sticky top-20 rounded-card border border-border bg-surface p-4">
            <h2 className="mb-3 text-sm font-semibold">{t('mt.navigator')} · <span className="tnum">{localDigits(answered, t.locale)}/{localDigits(a.questions.length, t.locale)}</span></h2>
            {navigator}
          </div>
        </aside>
      </div>

      <Sheet open={nav2} onOpenChange={setNav2} title={t('mt.navigator')}>{navigator}</Sheet>
      <Dialog
        open={confirm}
        onOpenChange={setConfirm}
        title={t('mt.submitTitle')}
        footer={<><Button variant="ghost" onClick={() => setConfirm(false)}>{t('mt.keep')}</Button><Button onClick={() => { setConfirm(false); doSubmit(); }}>{t('mt.submit')}</Button></>}
      >
        <p className="text-muted">{answered < a.questions.length ? t('mt.submitBody', { n: localDigits(a.questions.length - answered, t.locale) }) : t('mt.submitAll')}</p>
      </Dialog>
    </div>
  );
}
