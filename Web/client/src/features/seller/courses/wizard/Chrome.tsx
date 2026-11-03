import { Link } from 'react-router';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { countLessons } from '../../lib/curriculum';
import { useWizard, type Step } from './context';
import { pricingBody, targetIssue } from './model';
import type { SaveStatus } from './useSaver';
import { AlertTriangle, Check, CheckCircle2, CloudOff, ExternalLink, Hourglass, Loader2, MessageSquareWarning, RotateCw } from '../../parts';

export function AutosaveIndicator({ status, onRetry }: { status: SaveStatus; onRetry: () => void }) {
  const t = useT();
  if (status === 'error')
    return (
      <button type="button" onClick={onRetry} className="inline-flex h-10 items-center gap-1.5 rounded-full border border-danger/30 bg-danger-soft px-3 text-[13px] font-semibold text-danger-fg" aria-live="polite">
        <CloudOff className="size-4" aria-hidden /> {t('wiz.save.error')}
        <RotateCw className="size-3.5" aria-hidden />
      </button>
    );
  const busy = status === 'saving' || status === 'pending';
  return (
    <p className="inline-flex h-10 items-center gap-1.5 text-[13px] text-muted" aria-live="polite" role="status">
      {busy ? <Loader2 className="size-4 animate-spin motion-reduce:animate-none" aria-hidden /> : <CheckCircle2 className="size-4 text-success" aria-hidden />}
      {busy ? t('wiz.save.saving') : status === 'saved' ? t('wiz.save.saved') : t('wiz.save.idle')}
    </p>
  );
}

const STEPS: { n: Step; key: string }[] = [
  { n: 1, key: 'wiz.step.basics' },
  { n: 2, key: 'wiz.step.target' },
  { n: 3, key: 'wiz.step.content' },
  { n: 4, key: 'wiz.step.pricing' },
  { n: 5, key: 'wiz.step.review' },
];

/** Heuristic "looks done" per step, for the stepper ticks (the server checklist is the real gate). */
function useDone(): Record<Step, boolean> {
  const w = useWizard();
  return {
    1: w.basics.title.trim().length >= 10 && w.basics.shortDescription.trim().length >= 30 && w.basics.learnings.filter((x) => x.trim()).length >= 3,
    2: w.targets.length > 0 && w.targets.every((x) => !targetIssue(x)),
    3: countLessons(w.tree) >= 3,
    4: !!pricingBody(w.pricing).body && !pricingBody(w.pricing).errors.price,
    5: w.server.status === 'in_review' || w.server.status === 'published',
  };
}

export function Stepper({ step }: { step: Step }) {
  const t = useT();
  const w = useWizard();
  const done = useDone();
  return (
    <nav aria-label={t('wiz.steps')} className="mt-4">
      <ol className="grid grid-cols-5 gap-1.5">
        {STEPS.map((s) => {
          const current = s.n === step;
          return (
            <li key={s.n} className="min-w-0">
              <button
                type="button"
                onClick={() => w.goto(s.n)}
                aria-current={current ? 'step' : undefined}
                className={cn(
                  'flex h-12 w-full items-center justify-center gap-2.5 rounded-control border px-1 text-left text-sm transition-colors duration-150 sm:justify-start sm:px-3',
                  current ? 'border-primary bg-primary-soft font-semibold text-primary-fg' : 'border-border bg-surface text-muted hover:border-border-strong hover:text-text',
                )}
              >
                <span className={cn('latin flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-bold', done[s.n] ? 'bg-success text-white' : current ? 'bg-primary text-on-primary' : 'bg-surface-2 text-muted')} aria-hidden>
                  {done[s.n] ? <Check className="size-3.5" strokeWidth={3} /> : localDigits(s.n, t.locale)}
                </span>
                <span className="sr-only truncate sm:not-sr-only">{t(s.key)}</span>
                {done[s.n] && <span className="sr-only">{t('wiz.stepDone')}</span>}
              </button>
            </li>
          );
        })}
      </ol>
      <p className="mt-2 text-sm font-semibold text-primary-fg sm:hidden" aria-hidden>
        {t('wiz.stepOf', { n: localDigits(step, t.locale), total: localDigits(5, t.locale), step: t(STEPS[step - 1]!.key) })}
      </p>
    </nav>
  );
}

/** Review state at the top of every step. */
export function StatusBanner() {
  const t = useT();
  const { server } = useWizard();
  const s = server.status;
  if (s === 'draft') return null;
  const box = 'mt-4 flex gap-3 rounded-card border p-4 text-sm';
  if (s === 'in_review')
    return (
      <div role="status" className={cn(box, 'border-primary/25 bg-primary-soft text-primary-fg')}>
        <Hourglass className="mt-0.5 size-5 shrink-0" aria-hidden />
        <div>
          <p className="font-semibold">{t('wiz.banner.inReview')}</p>
          <p className="opacity-90">{t('wiz.banner.inReviewBody')}</p>
        </div>
      </div>
    );
  if (s === 'changes_requested' || s === 'rejected')
    return (
      <div role="status" className={cn(box, s === 'rejected' ? 'border-danger/30 bg-danger-soft text-danger-fg' : 'border-warning/30 bg-warning-soft text-warning-fg')}>
        <MessageSquareWarning className="mt-0.5 size-5 shrink-0" aria-hidden />
        <div className="min-w-0">
          <p className="font-semibold">{t(s === 'rejected' ? 'wiz.banner.rejected' : 'wiz.banner.changes')}</p>
          {server.reviewNote && <p className="mt-1 whitespace-pre-line">{server.reviewNote}</p>}
          <p className="mt-1 opacity-90">{t('wiz.banner.changesBody')}</p>
        </div>
      </div>
    );
  return (
    <div role="status" className={cn(box, s === 'published' ? 'border-success/30 bg-success-soft text-success-fg' : 'border-border bg-surface-2 text-text')}>
      {s === 'published' ? <CheckCircle2 className="mt-0.5 size-5 shrink-0" aria-hidden /> : <AlertTriangle className="mt-0.5 size-5 shrink-0" aria-hidden />}
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{t(`wiz.banner.${s}`)}</p>
        <p className="opacity-90">{server.pendingRevision ? t('wiz.banner.revisionPending') : t('wiz.banner.liveEdits')}</p>
      </div>
      {s !== 'archived' && (
        <Link to={`/c/${server.slug}`} className="inline-flex h-10 shrink-0 items-center gap-1.5 self-start rounded-control border border-current/25 bg-surface px-3 text-sm font-semibold text-text hover:bg-surface-2">
          {t('wiz.banner.viewPublic')} <ExternalLink className="size-4" aria-hidden />
        </Link>
      )}
    </div>
  );
}
