import '../i18n';
import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { InferBody, InferResponse } from '@/api';
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Field, Illustration, Input, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { cn } from '@/lib/cn';
import { localDigits, t as tNow, useT } from '@/lib/i18n';
import { ArrowLeft, ChevronDown, ChevronUp, Layers, ListChecks, normalizeDigits, PencilLine, Plus, Timer, Trash2, Users, X } from '../../parts';

type SellerMock = InferResponse<typeof contracts.sellerExam.mockTests>['items'][number];
type MockBody = InferBody<typeof contracts.sellerExam.createMock>;

let seq = 0;
const k = () => `k${++seq}`;

export interface OptDraft {
  key: string;
  body: string;
  isCorrect: boolean;
}
export interface QDraft {
  key: string;
  body: string;
  explanation: string;
  chapterTag: string;
  options: OptDraft[];
}
export interface MockDraft {
  id: string | null;
  title: string;
  description: string;
  durationMin: string;
  questions: QDraft[];
}
export interface MockErrors {
  title?: string;
  duration?: string;
  questions?: string;
  q: Record<string, string>;
}

const emptyOption = (): OptDraft => ({ key: k(), body: '', isCorrect: false });
export const emptyQuestion = (): QDraft => ({ key: k(), body: '', explanation: '', chapterTag: '', options: [emptyOption(), emptyOption(), emptyOption(), emptyOption()] });

export function draftFrom(m: SellerMock | null): MockDraft {
  if (!m) return { id: null, title: '', description: '', durationMin: '30', questions: [emptyQuestion()] };
  return {
    id: m.id,
    title: m.title,
    description: m.description ?? '',
    durationMin: String(Math.round(m.durationSec / 60)),
    questions: m.questions.map((q) => ({ key: k(), body: q.body, explanation: q.explanation ?? '', chapterTag: q.chapterTag ?? '', options: q.options.map((o) => ({ key: k(), body: o.body, isCorrect: o.isCorrect })) })),
  };
}

/** Client-side mirror of the server rules (MCQ, exactly one correct, 2–6 options, 5–240 min). */
export function mockBody(d: MockDraft): { body?: MockBody; errors: MockErrors } {
  const errors: MockErrors = { q: {} };
  if (d.title.trim().length < 3) errors.title = tNow('mock.err.title');
  const min = Number(normalizeDigits(d.durationMin).trim());
  if (!Number.isInteger(min) || min < 5 || min > 240) errors.duration = tNow('mock.err.duration');
  if (!d.questions.length) errors.questions = tNow('mock.err.noQuestions');
  if (d.questions.length > 150) errors.questions = tNow('mock.err.tooMany');
  d.questions.forEach((q) => {
    const filled = q.options.filter((o) => o.body.trim());
    if (q.body.trim().length < 3) errors.q[q.key] = tNow('mock.err.qBody');
    else if (filled.length < 2) errors.q[q.key] = tNow('mock.err.options');
    else if (q.options.some((o) => !o.body.trim() && o.isCorrect)) errors.q[q.key] = tNow('mock.err.emptyCorrect');
    else if (filled.filter((o) => o.isCorrect).length !== 1) errors.q[q.key] = tNow('mock.err.oneCorrect');
  });
  if (errors.title || errors.duration || errors.questions || Object.keys(errors.q).length) return { errors };
  return {
    errors,
    body: {
      title: d.title.trim(),
      description: d.description.trim() || null,
      durationSec: min * 60,
      questions: d.questions.map((q) => ({
        body: q.body.trim(),
        explanation: q.explanation.trim() || null,
        chapterTag: q.chapterTag.trim() || null,
        // blank option rows are dropped rather than sent
        options: q.options.filter((o) => o.body.trim()).map((o) => ({ body: o.body.trim(), isCorrect: o.isCorrect })),
      })),
    },
  };
}

const errMsg = (e: unknown) => (e instanceof ApiError && e.status > 0 && e.status < 500 ? e.message : tNow('state.errorBody'));

// ---------------------------------------------------------------- screen

export default function MockAuthoringScreen({ courseId }: { courseId: string }) {
  const t = useT();
  const course = useQuery({ queryKey: ['seller', 'course', courseId], queryFn: () => api(contracts.sellerCourses.get, { params: { id: courseId } }), staleTime: 60_000 });
  const [tab, setTab] = useState<'mock' | 'cards'>('mock');
  const chapters = useMemo(() => [...new Set((course.data?.sections ?? []).flatMap((s) => s.chapters.map((c) => c.title)).filter(Boolean))], [course.data]);

  return (
    <div className="mx-auto max-w-[1100px] px-4 pb-16 pt-5 lg:px-8">
      <Link to={`/teach/courses/${courseId}/edit?step=3`} className="inline-flex h-10 items-center gap-1.5 rounded-control pr-2 text-sm font-medium text-muted hover:text-text">
        <ArrowLeft className="size-4" aria-hidden /> {t('mock.back')}
      </Link>
      <p className="latin mt-1 line-clamp-1 text-sm text-muted">{course.data?.title ?? ' '}</p>
      <h1 className="mt-0.5 text-[24px] font-bold leading-snug">{t('mock.title')}</h1>
      <p className="mt-1 max-w-2xl text-sm text-muted">{t('mock.subtitle')}</p>

      <div role="tablist" aria-label={t('mock.title')} className="mt-5 flex gap-1 border-b border-border">
        {(['mock', 'cards'] as const).map((x) => (
          <button
            key={x}
            type="button"
            role="tab"
            aria-selected={tab === x}
            onClick={() => setTab(x)}
            className={cn('-mb-px inline-flex h-12 items-center gap-2 border-b-2 px-3 text-sm font-semibold transition-colors', tab === x ? 'border-primary text-primary-fg' : 'border-transparent text-muted hover:text-text')}
          >
            {x === 'mock' ? <ListChecks className="size-4" aria-hidden /> : <Layers className="size-4" aria-hidden />}
            {t(`mock.tab.${x}`)}
          </button>
        ))}
      </div>

      <div className="mt-6" role="tabpanel">
        {tab === 'mock' ? <MockTests courseId={courseId} chapters={chapters} /> : <Flashcards courseId={courseId} />}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- mock tests

function MockTests({ courseId, chapters }: { courseId: string; chapters: string[] }) {
  const t = useT();
  const qc = useQueryClient();
  const key = ['seller', 'mocks', courseId];
  const list = useQuery({ queryKey: key, queryFn: () => api(contracts.sellerExam.mockTests, { params: { courseId } }) });
  const [editing, setEditing] = useState<SellerMock | 'new' | null>(null);
  const [deleting, setDeleting] = useState<SellerMock | null>(null);
  const del = useMutation({
    mutationFn: (id: string) => api(contracts.sellerExam.deleteMock, { params: { id } }),
    onSuccess: () => {
      toast.success(t('mock.deleted'));
      setDeleting(null);
      void qc.invalidateQueries({ queryKey: key });
    },
    onError: (e) => toast.error(errMsg(e)),
  });

  if (editing)
    return (
      <MockEditor
        courseId={courseId}
        chapters={chapters}
        mock={editing === 'new' ? null : editing}
        onDone={(saved) => {
          if (saved) void qc.invalidateQueries({ queryKey: key });
          setEditing(null);
          window.scrollTo({ top: 0 });
        }}
      />
    );

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">{t('mock.list')}</h2>
        <Button icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing('new')}>
          {t('mock.new')}
        </Button>
      </div>
      <Card className="mt-3 overflow-hidden">
        {list.isLoading ? (
          <ul className="divide-y divide-border" aria-busy>
            {Array.from({ length: 2 }, (_, i) => (
              <li key={i} className="p-4">
                <Skeleton className="h-5 w-1/2" />
                <Skeleton className="mt-2 h-3 w-1/3" />
              </li>
            ))}
          </ul>
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : !list.data?.items.length ? (
          <EmptyState
            illustration={<Illustration kind="empty" />}
            title={t('mock.emptyTitle')}
            body={t('mock.emptyBody')}
            action={
              <Button variant="soft" icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing('new')}>
                {t('mock.emptyCta')}
              </Button>
            }
          />
        ) : (
          <ul className="divide-y divide-border">
            {list.data.items.map((m) => (
              <li key={m.id} className="flex flex-wrap items-center gap-3 p-4">
                <div className="min-w-0 flex-1 basis-56">
                  <p className="latin font-semibold">{m.title}</p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                    <span className="inline-flex items-center gap-1">
                      <ListChecks className="size-3.5" aria-hidden /> {t('mock.qCount', { n: localDigits(m.questions.length, t.locale) })}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <Timer className="size-3.5" aria-hidden /> {t('mock.minutes', { n: localDigits(Math.round(m.durationSec / 60), t.locale) })}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <Users className="size-3.5" aria-hidden /> {t('mock.attempts', { n: localDigits(m.attempts, t.locale) })}
                    </span>
                  </p>
                </div>
                <div className="ml-auto flex gap-1">
                  <Button variant="secondary" icon={<PencilLine className="size-4" aria-hidden />} onClick={() => setEditing(m)}>
                    {t('common.edit')}
                  </Button>
                  <Button variant="ghost" aria-label={t('mock.deleteLabel', { title: m.title })} icon={<Trash2 className="size-4" aria-hidden />} onClick={() => setDeleting(m)} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Dialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={t('mock.deleteTitle')}
        description={deleting ? t(deleting.attempts ? 'mock.deleteBodyAttempts' : 'mock.deleteBody', { title: deleting.title, n: localDigits(deleting.attempts, t.locale) }) : undefined}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" loading={del.isPending} onClick={() => deleting && del.mutate(deleting.id)}>
              {t('common.delete')}
            </Button>
          </>
        }
      >
        {null}
      </Dialog>
    </>
  );
}

function MockEditor({ courseId, chapters, mock, onDone }: { courseId: string; chapters: string[]; mock: SellerMock | null; onDone: (saved: boolean) => void }) {
  const t = useT();
  const [initial] = useState(() => draftFrom(mock));
  const [d, setD] = useState<MockDraft>(initial);
  const [errors, setErrors] = useState<MockErrors>({ q: {} });
  const [serverErr, setServerErr] = useState<string | null>(null);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const dirty = JSON.stringify(d) !== JSON.stringify(initial);
  const save = useMutation({
    mutationFn: (body: MockBody) => (mock ? api(contracts.sellerExam.updateMock, { params: { id: mock.id }, body }) : api(contracts.sellerExam.createMock, { params: { courseId }, body })),
    onSuccess: () => {
      toast.success(mock ? t('mock.saved') : t('mock.created'));
      onDone(true);
    },
    onError: (e) => setServerErr(errMsg(e)),
  });
  const setQ = (key: string, patch: Partial<QDraft>) => setD((p) => ({ ...p, questions: p.questions.map((q) => (q.key === key ? { ...q, ...patch } : q)) }));
  const move = (i: number, dir: -1 | 1) =>
    setD((p) => {
      const qs = [...p.questions];
      const j = i + dir;
      if (j < 0 || j >= qs.length) return p;
      [qs[i], qs[j]] = [qs[j]!, qs[i]!];
      return { ...p, questions: qs };
    });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const r = mockBody(d);
    setErrors(r.errors);
    setServerErr(null);
    if (r.body) return save.mutate(r.body);
    // jump to the first problem
    const first = d.questions.find((q) => r.errors.q[q.key]);
    const el = document.getElementById(r.errors.title || r.errors.duration ? 'mock-meta' : first ? `q-${first.key}` : 'mock-meta');
    el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
  };
  const errCount = Object.keys(errors.q).length;
  const listId = `chapters-${courseId}`;

  return (
    <form onSubmit={submit} noValidate>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">{mock ? t('mock.editTitle') : t('mock.new')}</h2>
        <Button variant="ghost" icon={<X className="size-4" aria-hidden />} onClick={() => (dirty ? setConfirmLeave(true) : onDone(false))}>
          {t('common.cancel')}
        </Button>
      </div>
      {mock && mock.attempts > 0 && (
        <p className="mt-3 rounded-control border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning-fg" role="note">
          {t('mock.attemptsWarn', { n: localDigits(mock.attempts, t.locale) })}
        </p>
      )}

      <Card id="mock-meta" className="mt-4 grid gap-4 p-4 sm:grid-cols-[minmax(0,1fr)_160px] sm:p-5">
        <Field label={t('mock.f.title')} error={errors.title}>
          {(p) => <Input {...p} value={d.title} maxLength={160} className="latin" placeholder="Midterm Mock — Linked List ও Stack" onChange={(e) => setD({ ...d, title: e.target.value })} />}
        </Field>
        <Field label={t('mock.f.duration')} error={errors.duration} hint={t('mock.f.durationHint')}>
          {(p) => <Input {...p} inputMode="numeric" value={d.durationMin} className="latin tnum" onChange={(e) => setD({ ...d, durationMin: e.target.value })} />}
        </Field>
        <Field className="sm:col-span-2" label={t('mock.f.desc')} hint={t('common.optional')}>
          {(p) => <Textarea {...p} rows={2} maxLength={1000} value={d.description} onChange={(e) => setD({ ...d, description: e.target.value })} />}
        </Field>
      </Card>

      <datalist id={listId}>
        {chapters.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>

      <ol className="mt-5 flex flex-col gap-4">
        {d.questions.map((q, i) => (
          <QuestionEditor
            key={q.key}
            q={q}
            index={i}
            total={d.questions.length}
            error={errors.q[q.key]}
            listId={listId}
            onChange={(patch) => setQ(q.key, patch)}
            onMove={(dir) => move(i, dir)}
            onRemove={() => setD((p) => ({ ...p, questions: p.questions.filter((x) => x.key !== q.key) }))}
          />
        ))}
      </ol>
      {errors.questions && (
        <p role="alert" className="mt-3 text-sm text-danger-fg">
          {errors.questions}
        </p>
      )}
      <Button variant="secondary" className="mt-4 w-full border-dashed" disabled={d.questions.length >= 150} icon={<Plus className="size-4" aria-hidden />} onClick={() => setD((p) => ({ ...p, questions: [...p.questions, emptyQuestion()] }))}>
        {t('mock.addQuestion')}
      </Button>

      <div className="sticky bottom-0 z-10 -mx-4 mt-6 flex flex-wrap items-center justify-end gap-3 border-t border-border bg-surface px-4 py-3 shadow-e2 lg:mx-0 lg:rounded-card lg:border lg:px-5">
        <p className="mr-auto text-sm text-muted" aria-live="polite">
          {serverErr ? <span className="text-danger-fg">{serverErr}</span> : errCount ? <span className="text-danger-fg">{t('mock.errCount', { n: localDigits(errCount, t.locale) })}</span> : t('mock.qCount', { n: localDigits(d.questions.length, t.locale) })}
        </p>
        <Button type="submit" loading={save.isPending}>
          {mock ? t('mock.save') : t('mock.create')}
        </Button>
      </div>

      <Dialog
        open={confirmLeave}
        onOpenChange={setConfirmLeave}
        title={t('mock.leaveTitle')}
        description={t('mock.leaveBody')}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmLeave(false)}>
              {t('mock.keepEditing')}
            </Button>
            <Button variant="danger" onClick={() => onDone(false)}>
              {t('mock.discard')}
            </Button>
          </>
        }
      >
        {null}
      </Dialog>
    </form>
  );
}

function QuestionEditor({ q, index, total, error, listId, onChange, onMove, onRemove }: { q: QDraft; index: number; total: number; error?: string; listId: string; onChange: (p: Partial<QDraft>) => void; onMove: (dir: -1 | 1) => void; onRemove: () => void }) {
  const t = useT();
  const n = localDigits(index + 1, t.locale);
  const setOpt = (key: string, patch: Partial<OptDraft>) => onChange({ options: q.options.map((o) => (o.key === key ? { ...o, ...patch } : patch.isCorrect ? { ...o, isCorrect: false } : o)) });
  const iconBtn = 'flex size-11 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text disabled:opacity-40 disabled:hover:bg-transparent';
  return (
    <li id={`q-${q.key}`} className={cn('surface-card scroll-mt-24 p-4 sm:p-5', error && 'border-danger/60')}>
      <div className="flex items-center gap-2">
        <Badge tone="primary">{t('mock.qN', { n })}</Badge>
        <span className="flex-1" />
        <button type="button" className={iconBtn} disabled={index === 0} onClick={() => onMove(-1)} aria-label={t('mock.moveUp', { n })}>
          <ChevronUp className="size-5" aria-hidden />
        </button>
        <button type="button" className={iconBtn} disabled={index === total - 1} onClick={() => onMove(1)} aria-label={t('mock.moveDown', { n })}>
          <ChevronDown className="size-5" aria-hidden />
        </button>
        <button type="button" className={cn(iconBtn, 'hover:text-danger-fg')} disabled={total === 1} onClick={onRemove} aria-label={t('mock.removeQ', { n })}>
          <Trash2 className="size-[18px]" aria-hidden />
        </button>
      </div>
      <Field className="mt-3" label={t('mock.f.question')}>
        {(p) => <Textarea {...p} rows={2} maxLength={3000} value={q.body} className="latin" placeholder={t('mock.f.questionPh')} onChange={(e) => onChange({ body: e.target.value })} />}
      </Field>

      <fieldset className="mt-4">
        <legend className="mb-2 text-sm font-medium">{t('mock.f.options')}</legend>
        <ul className="flex flex-col gap-2">
          {q.options.map((o, j) => {
            const letter = String.fromCharCode(65 + j);
            return (
              <li key={o.key} className={cn('flex items-center gap-2 rounded-control border pl-3 pr-1 transition-colors', o.isCorrect ? 'border-success bg-success-soft' : 'border-border')}>
                <label className="flex shrink-0 cursor-pointer items-center gap-2 py-2">
                  <input type="radio" name={`correct-${q.key}`} className="size-5 accent-[var(--ss-success)]" checked={o.isCorrect} onChange={() => setOpt(o.key, { isCorrect: true })} aria-label={t('mock.markCorrect', { letter, n })} />
                  <span className="latin w-4 text-sm font-semibold text-muted">{letter}</span>
                </label>
                <input
                  value={o.body}
                  maxLength={1000}
                  onChange={(e) => setOpt(o.key, { body: e.target.value })}
                  placeholder={t('mock.f.optionPh', { letter })}
                  aria-label={t('mock.optionLabel', { letter, n })}
                  className="latin h-11 min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted/70"
                />
                {o.isCorrect && <span className="hidden shrink-0 text-xs font-semibold text-success-fg sm:inline">{t('mock.correct')}</span>}
                <button type="button" className={iconBtn} disabled={q.options.length <= 2} onClick={() => onChange({ options: q.options.filter((x) => x.key !== o.key) })} aria-label={t('mock.removeOption', { letter, n })}>
                  <X className="size-4" aria-hidden />
                </button>
              </li>
            );
          })}
        </ul>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-muted">{t('mock.correctHint')}</p>
          {q.options.length < 6 && (
            <Button variant="ghost" size="sm" className="h-11" icon={<Plus className="size-4" aria-hidden />} onClick={() => onChange({ options: [...q.options, emptyOption()] })}>
              {t('mock.addOption')}
            </Button>
          )}
        </div>
      </fieldset>

      <div className="mt-4 grid gap-4 sm:grid-cols-[220px_minmax(0,1fr)]">
        <Field label={t('mock.f.chapter')} hint={t('common.optional')}>
          {(p) => <Input {...p} list={listId} maxLength={80} value={q.chapterTag} className="latin" placeholder="Linked List" onChange={(e) => onChange({ chapterTag: e.target.value })} />}
        </Field>
        <Field label={t('mock.f.explanation')} hint={t('mock.f.explanationHint')}>
          {(p) => <Textarea {...p} rows={2} maxLength={3000} value={q.explanation} className="latin" onChange={(e) => onChange({ explanation: e.target.value })} />}
        </Field>
      </div>
      {error && (
        <p role="alert" className="mt-3 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger-fg">
          {error}
        </p>
      )}
    </li>
  );
}

// ---------------------------------------------------------------- flashcards

interface Card2 {
  key: string;
  front: string;
  back: string;
}

function Flashcards({ courseId }: { courseId: string }) {
  const qc = useQueryClient();
  const key = ['seller', 'flashcards', courseId];
  const q = useQuery({ queryKey: key, queryFn: () => api(contracts.sellerExam.flashcards, { params: { courseId } }) });
  if (q.isLoading) return <Skeleton className="h-64 rounded-card" />;
  if (q.isError || !q.data)
    return (
      <Card>
        <ErrorState onRetry={() => void q.refetch()} />
      </Card>
    );
  return <FlashcardEditor key={q.dataUpdatedAt} courseId={courseId} initial={q.data.items} onSaved={(items) => qc.setQueryData(key, { items })} />;
}

function FlashcardEditor({ courseId, initial, onSaved }: { courseId: string; initial: { front: string; back: string }[]; onSaved: (items: InferResponse<typeof contracts.sellerExam.setFlashcards>['items']) => void }) {
  const t = useT();
  const [start] = useState<Card2[]>(() => initial.map((c) => ({ key: k(), front: c.front, back: c.back })));
  const [cards, setCards] = useState<Card2[]>(start);
  const [bad, setBad] = useState<Set<string>>(new Set());
  const dirty = JSON.stringify(cards.map(({ front, back }) => [front, back])) !== JSON.stringify(start.map(({ front, back }) => [front, back]));
  const save = useMutation({
    mutationFn: (body: { front: string; back: string }[]) => api(contracts.sellerExam.setFlashcards, { params: { courseId }, body: { cards: body } }),
    onSuccess: (r) => {
      toast.success(t('cards.saved', { n: localDigits(r.items.length, t.locale) }));
      onSaved(r.items);
    },
    onError: (e) => toast.error(errMsg(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const kept = cards.filter((c) => c.front.trim() || c.back.trim());
    const half = new Set(kept.filter((c) => !c.front.trim() || !c.back.trim()).map((c) => c.key));
    setBad(half);
    if (half.size) return;
    save.mutate(kept.map((c) => ({ front: c.front.trim(), back: c.back.trim() })));
  };
  const set = (key: string, patch: Partial<Card2>) => setCards((cs) => cs.map((c) => (c.key === key ? { ...c, ...patch } : c)));

  return (
    <form onSubmit={submit} noValidate>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">{t('cards.title')}</h2>
          <p className="mt-0.5 text-sm text-muted">{t('cards.body')}</p>
        </div>
        <Badge>{t('cards.count', { n: localDigits(cards.length, t.locale) })}</Badge>
      </div>
      {cards.length === 0 ? (
        <Card className="mt-4">
          <EmptyState
            compact
            illustration={<Illustration kind="empty" />}
            title={t('cards.emptyTitle')}
            body={t('cards.emptyBody')}
            action={
              <Button variant="soft" icon={<Plus className="size-4" aria-hidden />} onClick={() => setCards([{ key: k(), front: '', back: '' }])}>
                {t('cards.add')}
              </Button>
            }
          />
        </Card>
      ) : (
        <ol className="mt-4 flex flex-col gap-3">
          {cards.map((c, i) => {
            const n = localDigits(i + 1, t.locale);
            return (
              <li key={c.key} className={cn('surface-card grid gap-3 p-3 sm:grid-cols-[32px_minmax(0,1fr)_minmax(0,1.4fr)_44px] sm:items-start sm:p-4', bad.has(c.key) && 'border-danger/60')}>
                <span className="latin hidden pt-3 text-sm font-semibold text-muted sm:block">{n}</span>
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-medium text-muted">{t('cards.front', { n })}</span>
                  <Textarea rows={2} maxLength={500} value={c.front} className="latin min-h-12" placeholder="Stack কোন নীতিতে চলে?" onChange={(e) => set(c.key, { front: e.target.value })} />
                </label>
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-medium text-muted">{t('cards.back', { n })}</span>
                  <Textarea rows={2} maxLength={2000} value={c.back} className="latin min-h-12" placeholder="LIFO — Last In, First Out" onChange={(e) => set(c.key, { back: e.target.value })} />
                </label>
                <button type="button" onClick={() => setCards((cs) => cs.filter((x) => x.key !== c.key))} aria-label={t('cards.remove', { n })} className="flex size-11 items-center justify-center self-end rounded-control text-muted hover:bg-surface-2 hover:text-danger-fg sm:mt-5 sm:self-start">
                  <Trash2 className="size-[18px]" aria-hidden />
                </button>
                {bad.has(c.key) && (
                  <p role="alert" className="text-sm text-danger-fg sm:col-span-4 sm:col-start-2">
                    {t('cards.err')}
                  </p>
                )}
              </li>
            );
          })}
        </ol>
      )}
      {cards.length > 0 && (
        <Button variant="secondary" className="mt-3 w-full border-dashed" disabled={cards.length >= 300} icon={<Plus className="size-4" aria-hidden />} onClick={() => setCards((cs) => [...cs, { key: k(), front: '', back: '' }])}>
          {t('cards.add')}
        </Button>
      )}
      <div className="sticky bottom-0 z-10 -mx-4 mt-6 flex items-center justify-end gap-3 border-t border-border bg-surface px-4 py-3 shadow-e2 lg:mx-0 lg:rounded-card lg:border lg:px-5">
        <p className="mr-auto text-sm text-muted">{dirty ? t('cards.unsaved') : t('cards.upToDate')}</p>
        <Button type="submit" disabled={!dirty} loading={save.isPending}>
          {t('cards.save')}
        </Button>
      </div>
    </form>
  );
}
