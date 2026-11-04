import '../i18n';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, ArrowRight, BarChart3, BellRing, CalendarDays, Check, CheckCircle2, Code2, DraftingCompass, FileCode2, Gavel, GraduationCap, Languages, LineChart, Lock, Minus, Monitor, Pencil, Plus, PlusCircle, Search, ShieldCheck, Timer, Zap, type LucideIcon,
} from 'lucide-react';
import type { EXAM_TYPES } from '@/types';
import { Badge, Button, Dialog, EmptyState, ErrorState, Field, Input, Logo, Select, Skeleton, Switch, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { localDigits, ordinal, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { ClassroomConnectStep } from './ClassroomConnectStep';

const ICONS: Record<string, LucideIcon> = { code: Code2, zap: Zap, 'file-code': FileCode2, 'bar-chart': BarChart3, languages: Languages, gavel: Gavel, 'drafting-compass': DraftingCompass, 'line-chart': LineChart, monitor: Monitor };
const STORE = 'ss-onboarding';
const STEPS = 5;

interface Draft {
  step: number;
  institutionId: string | null;
  institutionLabel: string | null;
  departmentId: string | null;
  departmentShort: string | null;
  programId: string | null;
  programName: string | null;
  semester: number | null;
  batch: string;
  section: string | null;
  exam: { enabled: boolean; subjectId: string | null; subjectName: string; courseCode: string | null; examType: string; examDate: string };
}

const emptyDraft = (): Draft => ({
  step: 1,
  institutionId: null,
  institutionLabel: null,
  departmentId: null,
  departmentShort: null,
  programId: null,
  programName: null,
  semester: null,
  batch: '',
  section: null,
  exam: { enabled: true, subjectId: null, subjectName: '', courseCode: null, examType: 'midterm', examDate: '' },
});

function loadDraft(): Draft {
  try {
    const raw = sessionStorage.getItem(STORE);
    if (raw) return { ...emptyDraft(), ...(JSON.parse(raw) as Draft) };
  } catch {
    /* ignore */
  }
  return emptyDraft();
}

export default function OnboardingPage() {
  const t = useT();
  const nav = useNavigate();
  const setUser = useAuth((s) => s.setUser);
  const [d, setD] = useState<Draft>(loadDraft);
  const [done, setDone] = useState(false);
  const patch = (p: Partial<Draft>) => setD((x) => ({ ...x, ...p }));

  useEffect(() => {
    try {
      sessionStorage.setItem(STORE, JSON.stringify(d));
    } catch {
      /* ignore */
    }
  }, [d]);

  const finish = useMutation({
    mutationFn: (skipAll: boolean) =>
      api(contracts.catalog.completeOnboarding, {
        body: skipAll
          ? {}
          : {
              academic: d.institutionId ? { institutionId: d.institutionId, departmentId: d.departmentId, programId: d.programId, semester: d.semester, batch: d.batch || null, section: d.section } : undefined,
              nextExam:
                d.exam.enabled && d.exam.subjectName && d.exam.examDate
                  ? { subjectId: d.exam.subjectId, subjectName: d.exam.subjectName, courseCode: d.exam.courseCode, examType: d.exam.examType as (typeof EXAM_TYPES)[number], examDate: new Date(`${d.exam.examDate}T09:00:00+06:00`).toISOString() }
                  : null,
            },
      }),
    onSuccess: (me, skipAll) => {
      setUser(me);
      try {
        sessionStorage.removeItem(STORE);
      } catch {
        /* ignore */
      }
      if (skipAll) nav('/home', { replace: true });
      else setDone(true);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  const canNext = d.step === 1 ? !!d.institutionId : d.step === 2 ? !!d.departmentId : d.step === 3 ? !!d.semester : true;
  const next = () => (d.step < STEPS ? patch({ step: d.step + 1 }) : finish.mutate(false));
  const back = () => (d.step > 1 ? patch({ step: d.step - 1 }) : nav(-1));
  const later = () => (d.step < STEPS ? patch({ step: d.step + 1 }) : finish.mutate(false));

  if (done) return <Success draft={d} />;

  const pct = Math.round(((d.step - 1) / STEPS) * 100);
  const contextChip = [d.institutionLabel, d.departmentShort, d.programName].filter(Boolean).join(' · ');

  return (
    <div className="flex min-h-dvh flex-col bg-bg">
      <header className="sticky top-0 z-20 border-b border-border bg-surface/95">
        <div className="mx-auto flex h-14 max-w-2xl items-center justify-between px-2 sm:px-4">
          <Button variant="ghost" size="sm" onClick={back} aria-label={t('common.back')} icon={<ArrowLeft className="size-5 text-primary" aria-hidden />} />
          <Logo />
          <Button variant="ghost" size="sm" onClick={() => finish.mutate(true)} disabled={finish.isPending}>
            {t('common.skip')}
          </Button>
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-2xl flex-1 px-4 pb-40 pt-4">
        <div className="surface-card p-4">
          <div className="flex items-center justify-between text-sm">
            <span className="font-semibold text-primary-fg">{t('onb.stepOf', { n: localDigits(d.step, t.locale), total: localDigits(STEPS, t.locale) })}</span>
            <span className="text-muted">{t('onb.percent', { p: localDigits(pct, t.locale) })}</span>
          </div>
          <div className="mt-3 grid grid-cols-5 gap-1.5" aria-hidden>
            {Array.from({ length: STEPS }, (_, i) => (
              <span key={i} className={cn('h-1.5 rounded-full transition-colors duration-200', i + 1 < d.step ? 'bg-success' : i + 1 === d.step ? 'bg-primary' : 'bg-surface-2')} />
            ))}
          </div>
          <ol className="mt-2 hidden grid-cols-5 gap-1.5 text-[11.5px] text-muted sm:grid">
            {['onb.s1', 'onb.s2', 'onb.s3', 'onb.s4', 'onb.s5'].map((k, i) => (
              <li key={k} className={cn(i + 1 === d.step && 'font-semibold text-primary-fg', i + 1 < d.step && 'text-success-fg')}>
                {t(k)}
              </li>
            ))}
          </ol>
        </div>

        {contextChip && d.step > 1 && (
          <button type="button" onClick={() => patch({ step: 1 })} className="latin mt-4 inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3.5 py-1.5 text-sm font-medium hover:border-primary">
            <GraduationCap className="size-4 text-primary" aria-hidden /> {contextChip}
            <Pencil className="size-3.5 text-muted" aria-hidden />
          </button>
        )}

        <div className="mt-5 animate-fade-up" key={d.step}>
          {d.step === 1 && <StepUniversity d={d} patch={patch} />}
          {d.step === 2 && <StepDepartment d={d} patch={patch} />}
          {d.step === 3 && <StepSemester d={d} patch={patch} />}
          {d.step === 4 && <ClassroomConnectStep onLater={later} />}
          {d.step === 5 && <StepExam d={d} patch={patch} />}
        </div>
      </main>

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-surface/95 px-4 pb-[max(12px,env(safe-area-inset-bottom))] pt-3">
        <div className="mx-auto flex max-w-2xl flex-col items-center gap-1">
          <Button size="lg" block onClick={next} disabled={!canNext} loading={finish.isPending} icon={undefined}>
            {d.step === STEPS ? t('onb.finish') : t('common.next')} <ArrowRight className="size-5" aria-hidden />
          </Button>
          <button type="button" onClick={later} className="h-10 px-4 text-sm font-medium text-muted hover:text-text">
            {t('common.later')}
          </button>
        </div>
      </div>
    </div>
  );
}

function StepHeading({ title, body, icon }: { title: string; body: string; icon?: ReactNode }) {
  return (
    <div className="mb-5 flex items-start justify-between gap-4">
      <div>
        <h1 className="text-[26px] font-bold leading-snug">{title}</h1>
        <p className="mt-1 text-[15px] text-muted">{body}</p>
      </div>
      {icon && <div className="hidden size-16 shrink-0 items-center justify-center rounded-card bg-primary-soft text-primary sm:flex">{icon}</div>}
    </div>
  );
}

function initials(short: string) {
  const words = short.split(/\s+/).filter(Boolean);
  const raw = words.length > 1 ? words.map((w) => w[0]).join('') : short;
  return raw.replace(/[^A-Za-z]/g, '').slice(0, 4).toUpperCase() || short.slice(0, 2);
}

function SelectRow({ selected, onClick, children, leading }: { selected: boolean; onClick: () => void; children: ReactNode; leading: ReactNode }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={cn('flex w-full items-center gap-4 rounded-card border bg-surface p-3.5 text-left transition-[border-color,box-shadow] duration-150', selected ? 'border-primary shadow-e2 ring-1 ring-primary' : 'border-border hover:border-border-strong')}
    >
      {leading}
      <span className="min-w-0 flex-1">{children}</span>
      <span className={cn('flex size-6 shrink-0 items-center justify-center rounded-full border-2', selected ? 'border-success bg-success text-white' : 'border-border-strong')} aria-hidden>
        {selected && <Check className="size-3.5" strokeWidth={3} />}
      </span>
    </button>
  );
}

function StepUniversity({ d, patch }: { d: Draft; patch: (p: Partial<Draft>) => void }) {
  const t = useT();
  const [q, setQ] = useState('');
  const [reqOpen, setReqOpen] = useState(false);
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebounced(q.trim()), 200);
    return () => clearTimeout(id);
  }, [q]);
  const list = useQuery({ queryKey: ['institutions', debounced], queryFn: () => api(contracts.catalog.institutions, { query: { q: debounced || undefined, limit: 50 } }), placeholderData: (p) => p });

  return (
    <section>
      <div className="mb-5 flex flex-col items-center text-center">
        <span className="relative flex size-16 items-center justify-center rounded-card border border-border bg-primary-soft text-primary">
          <GraduationCap className="size-8" strokeWidth={1.75} aria-hidden />
          <CheckCircle2 className="absolute -right-1.5 -top-1.5 size-5 rounded-full bg-surface text-success" aria-hidden />
        </span>
        <h1 className="mt-4 text-[26px] font-bold leading-snug">{t('onb.uni.title')}</h1>
        <p className="mt-1 max-w-md text-[15px] text-muted">{t('onb.uni.body')}</p>
      </div>
      <label htmlFor="uni-q" className="sr-only">
        {t('onb.uni.search')}
      </label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-muted" aria-hidden />
        <Input id="uni-q" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('onb.uni.search')} className="pl-11" />
      </div>
      <div role="radiogroup" aria-label={t('onb.uni.title')} className="mt-4 flex flex-col gap-2.5">
        {list.isLoading ? (
          Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-[76px] rounded-card" />)
        ) : list.isError ? (
          <ErrorState compact onRetry={() => void list.refetch()} />
        ) : list.data?.items.length === 0 ? (
          <EmptyState compact title={t('onb.uni.none', { q })} action={<Button variant="soft" onClick={() => setReqOpen(true)}>{t('onb.uni.request')}</Button>} />
        ) : (
          list.data?.items.map((i) => (
            <SelectRow
              key={i.id}
              selected={d.institutionId === i.id}
              onClick={() => patch({ institutionId: i.id, institutionLabel: i.shortName, departmentId: null, departmentShort: null, programId: null, programName: null })}
              leading={<span className="latin flex size-12 shrink-0 items-center justify-center rounded-control bg-primary-soft text-[15px] font-bold text-primary-fg">{initials(i.shortName)}</span>}
            >
              <span className="block truncate font-semibold">{t.locale === 'bn' ? (i.nameBn ?? i.name) : i.name}</span>
              <span className="mt-0.5 flex items-center gap-2 text-sm text-muted">
                <span className="latin truncate">{t.locale === 'bn' ? i.name : i.shortName}</span>
                {i.city && <Badge tone={d.institutionId === i.id ? 'success' : 'neutral'}>{i.city}</Badge>}
              </span>
            </SelectRow>
          ))
        )}
      </div>
      <button type="button" onClick={() => setReqOpen(true)} className="mx-auto mt-5 flex items-center gap-2 rounded-control px-3 py-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
        <PlusCircle className="size-[18px]" aria-hidden /> {t('onb.uni.notListed')}
      </button>
      <RequestInstitutionDialog open={reqOpen} onOpenChange={setReqOpen} initialName={q} />
    </section>
  );
}

function RequestInstitutionDialog({ open, onOpenChange, initialName }: { open: boolean; onOpenChange: (o: boolean) => void; initialName: string }) {
  const t = useT();
  const [name, setName] = useState(initialName);
  const [type, setType] = useState<'public' | 'private' | 'college' | 'polytechnic'>('private');
  const [city, setCity] = useState('');
  useEffect(() => {
    if (open) setName(initialName);
  }, [open, initialName]);
  const m = useMutation({
    mutationFn: () => api(contracts.catalog.requestInstitution, { body: { name, type, city: city || undefined } }),
    onSuccess: () => {
      toast.success(t('onb.uni.requested'));
      onOpenChange(false);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('onb.uni.request')}
      description={t('onb.uni.requestBody')}
      footer={
        <Button onClick={() => m.mutate()} loading={m.isPending} disabled={name.trim().length < 3}>
          {t('onb.uni.send')}
        </Button>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label={t('onb.uni.fullName')}>{(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
        <Field label={t('onb.uni.type')}>
          {(p) => (
            <Select {...p} value={type} onChange={(e) => setType(e.target.value as typeof type)}>
              {(['public', 'private', 'college', 'polytechnic'] as const).map((v) => (
                <option key={v} value={v}>
                  {t(`instType.${v}`)}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label={t('onb.uni.city')}>{(p) => <Input {...p} value={city} onChange={(e) => setCity(e.target.value)} placeholder="সিলেট" />}</Field>
      </div>
    </Dialog>
  );
}

function StepDepartment({ d, patch }: { d: Draft; patch: (p: Partial<Draft>) => void }) {
  const t = useT();
  const depts = useQuery({ queryKey: ['departments', d.institutionId], queryFn: () => api(contracts.catalog.departments, { query: { institutionId: d.institutionId! } }), enabled: !!d.institutionId });
  const progs = useQuery({ queryKey: ['programs', d.departmentId], queryFn: () => api(contracts.catalog.programs, { query: { departmentId: d.departmentId! } }), enabled: !!d.departmentId });
  if (!d.institutionId) return <EmptyState title={t('onb.pickUniFirst')} action={<Button onClick={() => patch({ step: 1 })}>{t('onb.s1')}</Button>} />;
  return (
    <section>
      <StepHeading title={t('onb.dept.title')} body={t('onb.dept.body')} />
      <div className="mb-3 flex items-end justify-between">
        <h2 className="font-semibold">{t('onb.dept.label')}</h2>
        {depts.data && <span className="text-sm font-medium text-primary-fg">{t('onb.dept.count', { n: localDigits(depts.data.items.length, t.locale) })}</span>}
      </div>
      <div role="radiogroup" aria-label={t('onb.dept.label')} className="flex flex-col gap-2.5">
        {depts.isLoading
          ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-[68px] rounded-card" />)
          : depts.isError
            ? <ErrorState compact onRetry={() => void depts.refetch()} />
            : depts.data?.items.map((x) => {
                const Icon = ICONS[x.icon ?? ''] ?? GraduationCap;
                const sel = d.departmentId === x.id;
                return (
                  <SelectRow key={x.id} selected={sel} onClick={() => patch({ departmentId: x.id, departmentShort: x.shortName, programId: null, programName: null })} leading={<span className={cn('flex size-11 shrink-0 items-center justify-center rounded-control', sel ? 'bg-primary text-on-primary' : 'bg-primary-soft text-primary')}><Icon className="size-5" strokeWidth={1.75} aria-hidden /></span>}>
                    <span className={cn('block font-semibold', sel && 'text-primary-fg')}>
                      {t.locale === 'bn' ? (x.nameBn ?? x.name) : x.name} <span className="latin">({x.shortName})</span>
                    </span>
                  </SelectRow>
                );
              })}
      </div>
      {d.departmentId && (
        <>
          <h2 className="mb-3 mt-8 font-semibold">{t('onb.prog.label')}</h2>
          <div role="radiogroup" aria-label={t('onb.prog.label')} className="flex flex-col gap-2.5">
            {progs.isLoading
              ? <Skeleton className="h-14 rounded-card" />
              : progs.data?.items.map((p) => (
                  <SelectRow key={p.id} selected={d.programId === p.id} onClick={() => patch({ programId: p.id, programName: p.name })} leading={<GraduationCap className="size-5 shrink-0 text-muted" strokeWidth={1.75} aria-hidden />}>
                    <span className="latin block font-medium">{p.name}</span>
                  </SelectRow>
                ))}
          </div>
        </>
      )}
    </section>
  );
}

function StepSemester({ d, patch }: { d: Draft; patch: (p: Partial<Draft>) => void }) {
  const t = useT();
  const progs = useQuery({ queryKey: ['programs', d.departmentId], queryFn: () => api(contracts.catalog.programs, { query: { departmentId: d.departmentId! } }), enabled: !!d.departmentId });
  const total = progs.data?.items.find((p) => p.id === d.programId)?.semesters ?? 8;
  const batchNum = Number(d.batch) || 0;
  const quick = batchNum ? [batchNum - 2, batchNum - 1, batchNum, batchNum + 1, batchNum + 2].filter((n) => n > 0) : [54, 55, 56, 57, 58];
  const [customSection, setCustomSection] = useState('');
  return (
    <section className="flex flex-col gap-5">
      <StepHeading title={t('onb.sem.title')} body={t('onb.sem.body')} icon={<CalendarDays className="size-8" strokeWidth={1.75} aria-hidden />} />
      <fieldset className="surface-card p-4">
        <legend className="sr-only">{t('onb.sem.current')}</legend>
        <p className="mb-3 flex items-center gap-2 font-semibold">
          <CalendarDays className="size-[18px] text-primary" aria-hidden /> {t('onb.sem.current')}
        </p>
        <div role="radiogroup" aria-label={t('onb.sem.current')} className="grid grid-cols-4 gap-2">
          {Array.from({ length: total }, (_, i) => i + 1).map((n) => {
            const sel = d.semester === n;
            return (
              <button key={n} type="button" role="radio" aria-checked={sel} onClick={() => patch({ semester: n })} className={cn('relative flex h-16 flex-col items-center justify-center rounded-control border transition-colors', sel ? 'border-primary bg-primary-soft text-primary-fg ring-1 ring-primary' : 'border-border hover:border-border-strong')}>
                <span className="latin text-[17px] font-semibold">{ordinal(n, 'en')}</span>
                <span className="text-xs text-muted">{ordinal(n, 'bn')}</span>
                {sel && <Check className="absolute -right-1.5 -top-1.5 size-5 rounded-full bg-primary p-0.5 text-white" strokeWidth={3} aria-hidden />}
              </button>
            );
          })}
        </div>
      </fieldset>
      <fieldset className="surface-card p-4">
        <legend className="sr-only">{t('onb.batch.label')}</legend>
        <p className="mb-3 font-semibold">{t('onb.batch.label')}</p>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="lg" aria-label={t('onb.batch.minus')} onClick={() => patch({ batch: String(Math.max(1, batchNum - 1)) })} icon={<Minus className="size-5" aria-hidden />} />
          <label htmlFor="batch" className="sr-only">
            {t('onb.batch.label')}
          </label>
          <Input id="batch" inputMode="numeric" value={d.batch} onChange={(e) => patch({ batch: e.target.value.replace(/[^\dA-Za-z-]/g, '').slice(0, 8) })} placeholder="56" className="latin tnum h-14 flex-1 text-center text-2xl font-bold text-primary-fg" />
          <Button variant="secondary" size="lg" aria-label={t('onb.batch.plus')} onClick={() => patch({ batch: String(batchNum + 1) })} icon={<Plus className="size-5" aria-hidden />} />
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="text-sm text-muted">{t('onb.batch.quick')}</span>
          {quick.map((n) => (
            <button key={n} type="button" onClick={() => patch({ batch: String(n) })} aria-pressed={d.batch === String(n)} className={cn('latin h-9 rounded-full border px-3.5 text-sm font-medium', d.batch === String(n) ? 'border-primary bg-primary text-on-primary' : 'border-border hover:border-border-strong')}>
              {ordinal(n, 'en')}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset className="surface-card p-4">
        <legend className="sr-only">{t('onb.section.label')}</legend>
        <p className="mb-3 font-semibold">{t('onb.section.label')}</p>
        <div role="radiogroup" aria-label={t('onb.section.label')} className="grid grid-cols-3 gap-2">
          {['A', 'B', 'C'].map((s) => {
            const sel = d.section === s;
            return (
              <button key={s} type="button" role="radio" aria-checked={sel} onClick={() => patch({ section: s })} className={cn('latin h-14 rounded-control border text-[17px] font-semibold', sel ? 'border-primary bg-primary-soft text-primary-fg ring-1 ring-primary' : 'border-border hover:border-border-strong')}>
                Section {s}
              </button>
            );
          })}
        </div>
        <div className="mt-3 flex gap-2">
          <label htmlFor="sec-other" className="sr-only">
            {t('onb.section.other')}
          </label>
          <Input id="sec-other" value={customSection} onChange={(e) => setCustomSection(e.target.value.slice(0, 10))} placeholder={t('onb.section.other')} className="h-11" />
          <Button variant="secondary" disabled={!customSection.trim()} onClick={() => patch({ section: customSection.trim() })}>
            {t('common.save')}
          </Button>
        </div>
      </fieldset>
      {d.semester && d.departmentShort && (
        <p className="flex items-start gap-3 rounded-card border border-success/30 bg-success-soft p-4 text-sm font-medium text-success-fg">
          <ShieldCheck className="mt-0.5 size-5 shrink-0" aria-hidden />
          {t('onb.sem.matched', { dept: d.departmentShort, batch: d.batch ? ordinal(Number(d.batch) || 0, t.locale) : '' })}
        </p>
      )}
      <p className="flex items-start gap-2 text-[13px] text-muted">
        <Lock className="mt-0.5 size-4 shrink-0" aria-hidden /> {t('onb.sem.privacy')}
      </p>
    </section>
  );
}

function StepExam({ d, patch }: { d: Draft; patch: (p: Partial<Draft>) => void }) {
  const t = useT();
  const subjects = useQuery({
    queryKey: ['subjects', d.institutionId, d.departmentId, d.semester],
    queryFn: () => api(contracts.catalog.subjects, { query: d.institutionId ? { institutionId: d.institutionId, departmentId: d.departmentId ?? undefined, semester: d.semester ?? undefined } : {} }),
  });
  const ex = d.exam;
  const setEx = (p: Partial<Draft['exam']>) => patch({ exam: { ...ex, ...p } });
  const today = new Date().toISOString().slice(0, 10);
  const options = useMemo(() => subjects.data?.items ?? [], [subjects.data]);
  return (
    <section className="flex flex-col gap-5">
      <StepHeading title={t('onb.exam.title')} body={t('onb.exam.body')} icon={<Timer className="size-8" strokeWidth={1.75} aria-hidden />} />
      <div className="surface-card flex items-center justify-between gap-4 p-4">
        <div>
          <p className="font-semibold">{t('onb.exam.toggle')}</p>
          <p className="text-sm text-muted">{t('onb.exam.toggleBody')}</p>
        </div>
        <Switch checked={ex.enabled} onCheckedChange={(v) => setEx({ enabled: v })} label={t('onb.exam.toggle')} />
      </div>
      {ex.enabled && (
        <div className="surface-card flex flex-col gap-4 p-4">
          <Field label={t('onb.exam.subject')}>
            {(p) =>
              subjects.isLoading ? (
                <Skeleton className="h-12" />
              ) : (
                <Select
                  {...p}
                  value={ex.subjectId ?? ''}
                  onChange={(e) => {
                    const s = options.find((o) => o.id === e.target.value);
                    setEx({ subjectId: s?.id ?? null, subjectName: s?.name ?? '', courseCode: (s && 'courseCode' in s ? s.courseCode : null) ?? null });
                  }}
                >
                  <option value="">{t('onb.exam.pickSubject')}</option>
                  {options.map((s) => (
                    <option key={s.id} value={s.id}>
                      {'courseCode' in s && s.courseCode ? `${s.courseCode} · ` : ''}
                      {s.name}
                    </option>
                  ))}
                </Select>
              )
            }
          </Field>
          <div>
            <p className="mb-2 text-sm font-medium">{t('onb.exam.type')}</p>
            <div className="flex flex-wrap gap-2">
              {(['midterm', 'final', 'quiz', 'lab'] as const).map((k) => (
                <button key={k} type="button" aria-pressed={ex.examType === k} onClick={() => setEx({ examType: k })} className={cn('h-10 rounded-full border px-4 text-sm font-semibold', ex.examType === k ? 'border-primary bg-primary text-on-primary' : 'border-border hover:border-border-strong')}>
                  {t(`examType.${k}`)}
                </button>
              ))}
            </div>
          </div>
          <Field label={t('onb.exam.date')}>{(p) => <Input {...p} type="date" min={today} value={ex.examDate} onChange={(e) => setEx({ examDate: e.target.value })} className="latin" />}</Field>
        </div>
      )}
    </section>
  );
}

function Success({ draft }: { draft: Draft }) {
  const t = useT();
  const nav = useNavigate();
  const user = useAuth((s) => s.user);
  const days = draft.exam.enabled && draft.exam.examDate ? Math.max(0, Math.ceil((new Date(`${draft.exam.examDate}T09:00:00+06:00`).getTime() - Date.now()) / 86_400_000)) : null;
  const a = user?.academic;
  return (
    <main id="main" className="relative mx-auto flex min-h-dvh max-w-xl flex-col items-center overflow-hidden px-4 py-10 text-center">
      <Confetti />
      <span className="relative flex size-36 items-center justify-center rounded-full bg-primary-soft">
        <span className="flex size-24 items-center justify-center rounded-full bg-surface text-primary shadow-e1">
          <GraduationCap className="size-12" strokeWidth={1.75} aria-hidden />
        </span>
        <CheckCircle2 className="absolute bottom-3 right-3 size-10 rounded-full bg-surface text-success" aria-hidden />
      </span>
      <Badge tone="primary" wrap className="mt-6 px-4 py-1 text-sm">
        {t('onb.done.badge')}
      </Badge>
      <h1 className="mt-4 text-[26px] font-bold">{t('onb.done.title')}</h1>
      {a?.institutionName && (
        <p className="mt-2 text-muted">
          <span className="latin font-semibold text-text">{a.institutionName}</span>
          {a.departmentShort && ` · ${a.departmentShort}`}
          {a.semester && ` · ${t('common.semesterN', { ord: ordinal(a.semester, t.locale) })}`}
        </p>
      )}
      <ul className="mt-6 flex w-full flex-col gap-2.5 text-left">
        <li className="surface-card flex items-start gap-3 p-4">
          <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-success" aria-hidden />
          <span>
            <span className="block font-semibold">{t('onb.done.dashboard')}</span>
            <span className="text-sm text-muted">{t('onb.done.dashboardBody')}</span>
          </span>
        </li>
        <li className="surface-card flex items-start gap-3 p-4">
          <BellRing className="mt-0.5 size-6 shrink-0 text-primary" aria-hidden />
          <span>
            <span className="block font-semibold">{t('onb.done.courses')}</span>
            <span className="text-sm text-muted">{t('onb.done.coursesBody')}</span>
          </span>
        </li>
        {days !== null && (
          <li className="flex items-center justify-between gap-3 rounded-card border border-warning/30 bg-warning-soft p-4">
            <span className="flex items-center gap-2 font-semibold text-warning-fg">
              <Timer className="size-5" aria-hidden /> {t(`examType.${draft.exam.examType}`)} · {draft.exam.courseCode ?? draft.exam.subjectName}
            </span>
            <span className="rounded-full bg-surface px-3 py-1 text-sm font-bold text-warning-fg">{t('onb.done.daysLeft', { n: localDigits(days, t.locale) })}</span>
          </li>
        )}
      </ul>
      <Button size="lg" block className="mt-8" onClick={() => nav('/home', { replace: true })}>
        {t('onb.done.cta')} <ArrowRight className="size-5" aria-hidden />
      </Button>
      <p className="mt-6 flex items-center gap-2 text-sm text-muted">
        <ShieldCheck className="size-4 text-success" aria-hidden /> {t('onb.done.support')}
      </p>
    </main>
  );
}

/** One celebratory moment (CLAUDE.md §7): a few CSS confetti pieces, disabled for reduced motion. */
function Confetti() {
  const pieces = useMemo(() => Array.from({ length: 18 }, (_, i) => ({ left: (i * 53) % 100, delay: (i % 6) * 0.12, color: ['#4F46E5', '#10B981', '#F59E0B', '#EC4899', '#0EA5E9'][i % 5], rot: (i * 37) % 360 })), []);
  return (
    <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-72 motion-reduce:hidden">
      {pieces.map((p, i) => (
        <span key={i} className="absolute top-0 block h-2.5 w-1.5 rounded-sm" style={{ left: `${p.left}%`, background: p.color, transform: `rotate(${p.rot}deg)`, animation: `ss-confetti 1.8s ${p.delay}s ease-out both` }} />
      ))}
    </div>
  );
}
