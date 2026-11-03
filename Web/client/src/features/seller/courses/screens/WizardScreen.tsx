import '../i18n';
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SellerCourse } from '@shikkhasathi/shared';
import { Button, Card, EmptyState, ErrorState, Field, Illustration, Input, Skeleton } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useT } from '@/lib/i18n';
import { applyServer, fromApi, matchServer, toBody, updateLesson, type LessonDraft } from '../../lib/curriculum';
import { WizardContext, type Step, type Wizard } from '../wizard/context';
import { basicsBody, basicsFrom, pricingBody, pricingFrom, targetIssue, targetsBody, targetsFrom } from '../wizard/model';
import { overall, useRefState, useSaver } from '../wizard/useSaver';
import { AutosaveIndicator, StatusBanner, Stepper } from '../wizard/Chrome';
import { Preview } from '../wizard/Preview';
import { StepBasics } from '../wizard/StepBasics';
import { StepTarget } from '../wizard/StepTarget';
import { StepContent } from '../wizard/StepContent';
import { StepPricing } from '../wizard/StepPricing';
import { StepReview } from '../wizard/StepReview';
import { useUploads } from '../wizard/uploads';
import { ArrowLeft, ArrowRight, Counter, Lightbulb, StatusBadge, VerificationBanner, type DocInfo } from '../../parts';

// Mock tests & flashcards live on the same route (?tool=exam) so no router change is needed; own lazy chunk.
const MockAuthoring = lazy(() => import('./MockAuthoringScreen'));

export default function CourseWizardPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  if (id && params.get('tool') === 'exam')
    return (
      <Suspense fallback={<WizardSkeleton />}>
        <MockAuthoring key={id} courseId={id} />
      </Suspense>
    );
  return id ? <EditCourse key={id} id={id} /> : <NewCourse />;
}

// ---------------------------------------------------------------- /teach/courses/new

function NewCourse() {
  const t = useT();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [title, setTitle] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const profile = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  const create = useMutation({
    mutationFn: () => api(contracts.sellerCourses.create, { body: { title: title.trim() } }),
    onSuccess: (c) => {
      qc.setQueryData(['seller', 'course', c.id], c);
      void qc.invalidateQueries({ queryKey: ['seller', 'courses'] });
      nav(`/teach/courses/${c.id}/edit`, { replace: true });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-12 pt-6 lg:px-8">
      <Link to="/teach/courses" className="inline-flex h-10 items-center gap-1.5 rounded-control pr-2 text-sm font-medium text-muted hover:text-text">
        <ArrowLeft className="size-4" aria-hidden /> {t('seller.nav.courses')}
      </Link>
      <div className="mt-4 grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px]">
        <form
          className="surface-card p-5 sm:p-8"
          onSubmit={(e) => {
            e.preventDefault();
            if (title.trim().length < 5) return setErr(t('wiz.new.err'));
            setErr(null);
            create.mutate();
          }}
        >
          <p className="text-sm font-semibold text-primary-fg">{t('wiz.new.kicker')}</p>
          <h1 className="mt-1 text-[26px] font-bold leading-snug">{t('wiz.new.title')}</h1>
          <p className="mt-1 max-w-xl text-[15px] text-muted">{t('wiz.new.body')}</p>
          <Field className="mt-6" label={t('wiz.basics.title')} error={err ?? undefined} hint={t('wiz.basics.titleHint')} trailing={<Counter n={title.length} max={160} />}>
            {(p) => <Input {...p} autoFocus value={title} maxLength={160} className="latin h-14 text-lg" placeholder="Data Structures — Midterm Crash Course (CSE-2203)" onChange={(e) => setTitle(e.target.value)} />}
          </Field>
          <div className="mt-6 flex justify-end">
            <Button type="submit" size="lg" loading={create.isPending}>
              {t('wiz.new.cta')} <ArrowRight className="size-5" aria-hidden />
            </Button>
          </div>
          {profile.data && <VerificationBanner profile={profile.data.profile} className="mt-6" />}
        </form>
        <aside className="flex flex-col gap-3 text-sm">
          <p className="flex items-center gap-2 font-semibold">
            <Lightbulb className="size-[18px] text-warning" aria-hidden /> {t('wiz.new.tipsTitle')}
          </p>
          {['tip1', 'tip2', 'tip3'].map((k) => (
            <p key={k} className="border-l-2 border-border-strong pl-3 text-muted">
              {t(`wiz.new.${k}`)}
            </p>
          ))}
        </aside>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- /teach/courses/:id/edit

function EditCourse({ id }: { id: string }) {
  const t = useT();
  const q = useQuery({ queryKey: ['seller', 'course', id], queryFn: () => api(contracts.sellerCourses.get, { params: { id } }), staleTime: Infinity, refetchOnWindowFocus: false });
  const [initial, setInitial] = useState<SellerCourse | null>(null);
  useEffect(() => {
    if (q.data && !initial) setInitial(q.data);
  }, [q.data, initial]);

  if (q.isError && !initial)
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        {q.error instanceof ApiError && (q.error.status === 404 || q.error.status === 403) ? (
          <EmptyState
            illustration={<Illustration kind="search" />}
            title={t('wiz.notFound')}
            action={
              <Button asChild variant="soft">
                <Link to="/teach/courses">{t('seller.nav.courses')}</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState onRetry={() => void q.refetch()} />
        )}
      </div>
    );
  if (!initial) return <WizardSkeleton />;
  return <Editor initial={initial} />;
}

function WizardSkeleton() {
  return (
    <div className="mx-auto max-w-[1280px] px-4 pb-12 pt-6 lg:px-8" aria-busy>
      <Skeleton className="h-6 w-40" />
      <Skeleton className="mt-3 h-9 w-2/3" />
      <Skeleton className="mt-5 h-14 w-full rounded-card" />
      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex flex-col gap-5">
          <Skeleton className="h-72 rounded-card" />
          <Skeleton className="h-48 rounded-card" />
        </div>
        <Skeleton className="hidden h-96 rounded-card lg:block" />
      </div>
    </div>
  );
}

const STEP_KEYS: Record<Step, string> = { 1: 'wiz.step.basics', 2: 'wiz.step.target', 3: 'wiz.step.content', 4: 'wiz.step.pricing', 5: 'wiz.step.review' };

function Editor({ initial }: { initial: SellerCourse }) {
  const t = useT();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const [params, setParams] = useSearchParams();
  const step = (Math.min(5, Math.max(1, Number(params.get('step')) || 1)) as Step);
  const id = initial.id;
  const profile = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  const verified = (profile.data?.profile?.verificationStatus ?? user?.seller?.verificationStatus) === 'verified';

  const [server, setServer] = useState(initial);
  const [basics, basicsRef, setBasicsRaw] = useRefState(() => basicsFrom(initial));
  const [pricing, pricingRef, setPricingRaw] = useRefState(() => pricingFrom(initial));
  const [targets, targetsRef, setTargetsRaw] = useRefState(() => targetsFrom(initial));
  const [tree, treeRef, setTreeRaw] = useRefState(() => fromApi(initial.sections));
  const [documents, setDocuments] = useState<DocInfo[]>(initial.documents);

  const serverRef = useRef(initial);
  const accept = useCallback(
    (c: SellerCourse) => {
      serverRef.current = c;
      setServer(c);
      qc.setQueryData(['seller', 'course', id], c);
    },
    [qc, id],
  );

  // One PATCH carries basics AND pricing: the shared `update` body is CourseBasics.partial(), and zod
  // still applies `.default()`s inside `.partial()`, so a partial PATCH would reset omitted fields
  // (shortDescription, learnings, faq, accessType…) on the server. Always send the full set.
  const courseSaver = useSaver(async () => {
    const { body: pricingPatch } = pricingBody(pricingRef.current);
    const cur = serverRef.current;
    const pricingSafe = pricingPatch ?? { accessType: cur.accessType, accessDays: cur.accessDays ?? null, accessUntil: cur.accessUntil ?? null };
    accept(await api(contracts.sellerCourses.update, { params: { id }, body: { ...basicsBody(basicsRef.current), ...pricingSafe } }));
  });
  const basicsSaver = courseSaver;
  const pricingSaver = courseSaver;
  const targetsSaver = useSaver(async () => {
    const ts = targetsRef.current;
    if (!ts.length || ts.some((x) => targetIssue(x))) return;
    accept(await api(contracts.sellerCourses.targets, { params: { id }, body: targetsBody(ts) }));
  });
  const curriculumSaver = useSaver(async () => {
    const sent = treeRef.current;
    const res = await api(contracts.sellerCourses.curriculum, { params: { id }, body: toBody(sent, t('wiz.untitled')) });
    setTreeRaw((cur) => applyServer(cur, matchServer(sent, res.sections)));
    accept(res);
  });
  const savers = { basics: basicsSaver, pricing: pricingSaver, targets: targetsSaver, curriculum: curriculumSaver };
  const distinctSavers = [courseSaver, targetsSaver, curriculumSaver];

  const wrap = <T,>(set: (n: T | ((p: T) => T)) => void, saver: { schedule: () => void }) => (n: T | ((p: T) => T)) => {
    set(n);
    saver.schedule();
  };
  /* eslint-disable react-hooks/exhaustive-deps -- setters and savers are stable */
  const setBasics = useCallback(wrap(setBasicsRaw, basicsSaver), []);
  const setPricing = useCallback(wrap(setPricingRaw, pricingSaver), []);
  const setTargets = useCallback(wrap(setTargetsRaw, targetsSaver), []);
  const setTree = useCallback(wrap(setTreeRaw, curriculumSaver), []);
  const patchLesson = useCallback((key: string, patch: Partial<LessonDraft>) => setTree((cur) => updateLesson(cur, key, patch)), []);
  const flushAll = useCallback(async () => {
    await Promise.all([courseSaver.flush(), targetsSaver.flush(), curriculumSaver.flush()]);
    void qc.invalidateQueries({ queryKey: ['seller', 'courses'] });
  }, []);
  /* eslint-enable react-hooks/exhaustive-deps */

  const goto = useCallback(
    (s: Step) => {
      void flushAll();
      setParams((p) => {
        p.set('step', String(s));
        return p;
      });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    [flushAll, setParams],
  );

  // Save before leaving; warn while uploads or saves are pending.
  const uploading = useUploads((s) => Object.values(s.items).some((u) => !u.error));
  const status = overall(distinctSavers);
  useEffect(() => {
    if (!uploading && status !== 'pending' && status !== 'saving') return;
    const h = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', h);
    return () => window.removeEventListener('beforeunload', h);
  }, [uploading, status]);
  useEffect(() => () => void flushAll(), [flushAll]);

  const w: Wizard = useMemo(
    () => ({ id, server, accept, basics, setBasics, pricing, setPricing, targets, setTargets, tree, setTree, patchLesson, documents, addDocument: (d) => setDocuments((x) => [...x, d]), savers, flushAll, goto, verified }),
    // savers' status objects change identity every render; include them so the indicator stays live
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [id, server, accept, basics, pricing, targets, tree, documents, verified, basicsSaver.status, pricingSaver.status, targetsSaver.status, curriculumSaver.status, basicsSaver.error, targetsSaver.error, curriculumSaver.error, pricingSaver.error],
  );

  return (
    <WizardContext.Provider value={w}>
      <div className="mx-auto max-w-[1280px] px-4 pb-16 pt-5 lg:px-8">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Link to="/teach/courses" className="inline-flex h-10 items-center gap-1.5 rounded-control pr-2 text-sm font-medium text-muted hover:text-text">
            <ArrowLeft className="size-4" aria-hidden /> {t('seller.nav.courses')}
          </Link>
          <AutosaveIndicator status={status} onRetry={() => distinctSavers.forEach((s) => s.status === 'error' && s.retry())} />
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
          <h1 className="latin line-clamp-2 min-w-0 text-[22px] font-bold leading-snug sm:text-2xl">{basics.title || t('wiz.untitled')}</h1>
          <StatusBadge status={server.status} />
          {server.pendingRevision && <span className="rounded-full border border-dashed border-warning px-2.5 py-0.5 text-xs font-semibold text-warning-fg">{t('wiz.pendingRevision')}</span>}
        </div>

        <Stepper step={step} />
        <StatusBanner />

        <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <div className="min-w-0">
            <details className="surface-card mb-5 lg:hidden">
              <summary className="flex min-h-12 cursor-pointer items-center px-4 text-sm font-semibold">{t('wiz.preview.toggle')}</summary>
              <div className="border-t border-border p-4">
                <Preview />
              </div>
            </details>
            <div key={step} className="animate-fade-up">
              <h2 className="sr-only">{t(STEP_KEYS[step])}</h2>
              {step === 1 && <StepBasics />}
              {step === 2 && <StepTarget />}
              {step === 3 && <StepContent />}
              {step === 4 && <StepPricing />}
              {step === 5 && <StepReview />}
            </div>
            {step < 5 && (
              <div className="mt-6 flex items-center justify-between gap-3 border-t border-border pt-5">
                {step > 1 ? (
                  <Button variant="secondary" onClick={() => goto((step - 1) as Step)} icon={<ArrowLeft className="size-4" aria-hidden />}>
                    {t(STEP_KEYS[(step - 1) as Step])}
                  </Button>
                ) : (
                  <span />
                )}
                <Button onClick={() => goto((step + 1) as Step)}>
                  {t('wiz.nextStep', { step: t(STEP_KEYS[(step + 1) as Step]) })} <ArrowRight className="size-4" aria-hidden />
                </Button>
              </div>
            )}
          </div>
          <aside className="sticky top-6 hidden lg:block" aria-label={t('wiz.preview.title')}>
            <Card className="p-4">
              <Preview />
            </Card>
          </aside>
        </div>
      </div>
    </WizardContext.Provider>
  );
}
