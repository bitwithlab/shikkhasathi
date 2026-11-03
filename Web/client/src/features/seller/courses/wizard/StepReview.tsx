import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SellerCourse } from '@shikkhasathi/shared';
import { Button, ErrorState, Illustration, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useWizard, type Step } from './context';
import { ArrowRight, CheckCircle2, ExternalLink, SendHorizontal, ShieldX, XCircle } from '../../parts';

/** Where each checklist item is fixed. */
const FIX: Record<string, Step | 'verify'> = { verified: 'verify', title: 1, description: 1, learnings: 1, noGuarantee: 1, targets: 2, lessons: 3, preview: 3, media: 3, price: 4 };
type Item = { key: string; ok: boolean; message: string };

export function StepReview() {
  const t = useT();
  const qc = useQueryClient();
  const w = useWizard();
  const [ready, setReady] = useState(false);
  const [declared, setDeclared] = useState(false);
  const [failed, setFailed] = useState<Item[] | null>(null);
  const [forbidden, setForbidden] = useState(false);

  // make sure the checklist sees the latest autosaved state
  useEffect(() => {
    void w.flushAll().finally(() => setReady(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const q = useQuery({ queryKey: ['seller', 'checklist', w.id], queryFn: () => api(contracts.sellerCourses.checklist, { params: { id: w.id } }), enabled: ready, staleTime: 0 });

  const submit = useMutation({
    mutationFn: async () => {
      await w.flushAll();
      return api(contracts.sellerCourses.submit, { params: { id: w.id }, body: { declaration: true } });
    },
    onSuccess: (c: SellerCourse) => {
      w.accept(c);
      void qc.invalidateQueries({ queryKey: ['seller', 'courses'] });
      void q.refetch();
      setFailed(null);
      toast.success(t('wiz.review.sent'));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 403) setForbidden(true);
      else if (e instanceof ApiError && e.status === 422 && Array.isArray(e.details)) setFailed(e.details as Item[]);
      else toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));
    },
  });

  const items = q.data?.items ?? [];
  const okCount = items.filter((i) => i.ok).length;
  const status = w.server.status;
  const canSend = status === 'draft' || status === 'changes_requested' || status === 'rejected';

  if (!ready || q.isLoading)
    return (
      <div className="surface-card flex flex-col gap-3 p-6" aria-busy>
        <Skeleton className="h-6 w-48" />
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
    );
  if (q.isError)
    return (
      <div className="surface-card">
        <ErrorState onRetry={() => void q.refetch()} />
      </div>
    );

  return (
    <div className="flex flex-col gap-5">
      <section className="surface-card overflow-hidden" aria-labelledby="cl-title">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-4 sm:p-5">
          <div>
            <h3 id="cl-title" className="font-semibold">
              {t('wiz.review.title')}
            </h3>
            <p className="text-sm text-muted">{t('wiz.review.body')}</p>
          </div>
          <p className="latin text-sm font-semibold tnum" aria-live="polite">
            {t('wiz.review.count', { ok: localDigits(okCount, t.locale), total: localDigits(items.length, t.locale) })}
          </p>
        </div>
        <ul className="divide-y divide-border">
          {items.map((i) => {
            const fix = FIX[i.key];
            return (
              <li key={i.key} className="flex items-center gap-3 px-4 py-3 sm:px-5">
                {i.ok ? <CheckCircle2 className="size-5 shrink-0 text-success" aria-label={t('wiz.review.ok')} /> : <XCircle className="size-5 shrink-0 text-danger" aria-label={t('wiz.review.notOk')} />}
                <span className={cn('min-w-0 flex-1 text-sm', i.ok ? 'text-muted' : 'font-medium text-text')}>{i.message}</span>
                {!i.ok && fix && (
                  fix === 'verify' ? (
                    <Link to="/teach/verification" className="inline-flex h-10 shrink-0 items-center gap-1 rounded-control px-2.5 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
                      {t('wiz.review.fix')} <ArrowRight className="size-4" aria-hidden />
                    </Link>
                  ) : (
                    <button type="button" onClick={() => w.goto(fix)} className="inline-flex h-10 shrink-0 items-center gap-1 rounded-control px-2.5 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
                      {t('wiz.review.fix')} <ArrowRight className="size-4" aria-hidden />
                    </button>
                  )
                )}
              </li>
            );
          })}
        </ul>
      </section>

      {status === 'published' || status === 'unlisted' ? (
        <section className="surface-card flex flex-col items-center gap-3 p-6 text-center">
          <Illustration kind="done" />
          <p className="font-semibold">{t('wiz.review.liveTitle')}</p>
          <p className="max-w-md text-sm text-muted">{w.server.pendingRevision ? t('wiz.banner.revisionPending') : t('wiz.banner.liveEdits')}</p>
          <Button asChild variant="secondary" icon={<ExternalLink className="size-4" aria-hidden />}>
            <Link to={`/c/${w.server.slug}`}>{t('wiz.banner.viewPublic')}</Link>
          </Button>
        </section>
      ) : status === 'in_review' ? (
        <section className="surface-card flex flex-col items-center gap-3 p-6 text-center">
          <Illustration kind="done" />
          <p className="font-semibold">{t('wiz.banner.inReview')}</p>
          <p className="max-w-md text-sm text-muted">{t('wiz.banner.inReviewBody')}</p>
        </section>
      ) : (
        <section className="surface-card p-4 sm:p-6" aria-labelledby="sub-title">
          <h3 id="sub-title" className="font-semibold">
            {t('wiz.review.submitTitle')}
          </h3>
          <label className="mt-4 flex cursor-pointer gap-3 rounded-control border border-border p-4">
            <input type="checkbox" checked={declared} onChange={(e) => setDeclared(e.target.checked)} className="mt-1 size-5 shrink-0 accent-[var(--ss-primary)]" />
            <span className="text-sm leading-relaxed">{t('wiz.review.declaration')}</span>
          </label>
          {(forbidden || !w.verified) && (
            <div role="alert" className="mt-4 flex flex-col gap-2 rounded-control border border-warning/30 bg-warning-soft p-4 text-sm text-warning-fg sm:flex-row sm:items-center">
              <ShieldX className="size-5 shrink-0" aria-hidden />
              <p className="min-w-0 flex-1">{t('wiz.review.notVerified')}</p>
              <Link to="/teach/verification" className="inline-flex h-10 shrink-0 items-center gap-1 self-start rounded-control bg-surface px-3 font-semibold text-text hover:bg-surface-2 sm:self-center">
                {t('seller.nav.verification')} <ArrowRight className="size-4" aria-hidden />
              </Link>
            </div>
          )}
          {failed && failed.length > 0 && (
            <div role="alert" className="mt-4 rounded-control border border-danger/30 bg-danger-soft p-4 text-sm text-danger-fg">
              <p className="font-semibold">{t('wiz.review.failed')}</p>
              <ul className="mt-1 list-disc pl-5">
                {failed.map((f) => (
                  <li key={f.key}>{f.message}</li>
                ))}
              </ul>
            </div>
          )}
          <div className="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-muted">{t('wiz.review.after')}</p>
            <Button size="lg" disabled={!declared || !canSend} loading={submit.isPending} onClick={() => submit.mutate()} icon={<SendHorizontal className="size-4" aria-hidden />}>
              {t('wiz.review.submit')}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}
