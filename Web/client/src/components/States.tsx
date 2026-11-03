import type { ReactNode } from 'react';
import { RefreshCw, WifiOff } from 'lucide-react';
import { Button } from './Button';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export function Skeleton({ className }: { className?: string }) {
  return (
    <div aria-hidden className={cn('relative overflow-hidden rounded-[10px] bg-surface-2', className)}>
      <div className="absolute inset-0 -translate-x-full animate-[ss-shimmer_1.4s_infinite] bg-gradient-to-r from-transparent via-white/40 to-transparent dark:via-white/5" />
    </div>
  );
}

export function SkeletonLines({ lines = 3 }: { lines?: number }) {
  return (
    <div className="flex flex-col gap-2.5" aria-hidden>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} className={cn('h-3.5', i === lines - 1 ? 'w-3/5' : 'w-full')} />
      ))}
    </div>
  );
}

/** Empty state: small illustration, one line of purpose, one next action. */
export function EmptyState({ illustration, title, body, action, className, compact }: { illustration?: ReactNode; title: ReactNode; body?: ReactNode; action?: ReactNode; className?: string; compact?: boolean }) {
  return (
    <div className={cn('flex flex-col items-center text-center', compact ? 'gap-2 py-6' : 'gap-3 py-10', className)}>
      {illustration ?? <Illustration kind="empty" />}
      <div className="max-w-sm">
        <p className="font-semibold text-text">{title}</p>
        {body && <p className="mt-1 text-sm text-muted">{body}</p>}
      </div>
      {action}
    </div>
  );
}

export function ErrorState({ title, body, onRetry, className, compact }: { title?: ReactNode; body?: ReactNode; onRetry?: () => void; className?: string; compact?: boolean }) {
  const t = useT();
  const offline = typeof navigator !== 'undefined' && !navigator.onLine;
  return (
    <div role="alert" className={cn('flex flex-col items-center text-center', compact ? 'gap-2 py-5' : 'gap-3 py-10', className)}>
      {offline ? <WifiOff className="size-8 text-muted" strokeWidth={1.75} aria-hidden /> : <Illustration kind="error" />}
      <div className="max-w-sm">
        <p className="font-semibold text-text">{title ?? (offline ? t('state.offline') : t('state.errorTitle'))}</p>
        {!offline && <p className="mt-1 text-sm text-muted">{body ?? t('state.errorBody')}</p>}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry} icon={<RefreshCw className="size-4" aria-hidden />}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  );
}

/** Flat, minimal illustrations in brand tones (inline SVG, no image requests). */
export function Illustration({ kind, className }: { kind: 'empty' | 'error' | 'search' | 'classroom' | 'done' | 'courses'; className?: string }) {
  const c = cn('h-20 w-24', className);
  switch (kind) {
    case 'error':
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <rect x="14" y="14" width="68" height="52" rx="10" fill="var(--ss-surface-2)" stroke="var(--ss-border)" />
          <path d="M30 50c6-8 30-8 36 0" stroke="var(--ss-muted)" strokeWidth="3" fill="none" strokeLinecap="round" />
          <circle cx="36" cy="34" r="3.5" fill="var(--ss-muted)" />
          <circle cx="60" cy="34" r="3.5" fill="var(--ss-muted)" />
          <path d="M70 10l6-6M78 18l8-2" stroke="var(--ss-warning)" strokeWidth="3" strokeLinecap="round" />
        </svg>
      );
    case 'search':
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <rect x="10" y="18" width="52" height="44" rx="8" fill="var(--ss-primary-soft)" />
          <rect x="18" y="28" width="30" height="5" rx="2.5" fill="var(--ss-primary)" opacity=".5" />
          <rect x="18" y="38" width="22" height="5" rx="2.5" fill="var(--ss-primary)" opacity=".3" />
          <circle cx="62" cy="44" r="16" fill="var(--ss-surface)" stroke="var(--ss-primary)" strokeWidth="4" />
          <path d="M74 56l12 12" stroke="var(--ss-primary)" strokeWidth="5" strokeLinecap="round" />
          <path d="M56 44h12" stroke="var(--ss-warning)" strokeWidth="3" strokeLinecap="round" />
        </svg>
      );
    case 'classroom':
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <rect x="8" y="12" width="80" height="50" rx="10" fill="var(--ss-primary-soft)" />
          <rect x="18" y="22" width="26" height="30" rx="5" fill="var(--ss-surface)" stroke="var(--ss-border)" />
          <rect x="50" y="22" width="28" height="6" rx="3" fill="var(--ss-primary)" />
          <rect x="50" y="34" width="20" height="5" rx="2.5" fill="var(--ss-primary)" opacity=".4" />
          <rect x="50" y="44" width="24" height="5" rx="2.5" fill="var(--ss-primary)" opacity=".25" />
          <circle cx="31" cy="34" r="6" fill="var(--ss-success)" />
          <path d="M28 34l2.4 2.4L35 31.6" stroke="#fff" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <rect x="34" y="66" width="28" height="4" rx="2" fill="var(--ss-border)" />
        </svg>
      );
    case 'done':
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <circle cx="48" cy="40" r="28" fill="var(--ss-success-soft)" />
          <circle cx="48" cy="40" r="18" fill="var(--ss-success)" />
          <path d="M40 40l5.5 5.5L57 34" stroke="#fff" strokeWidth="4" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="16" cy="18" r="3" fill="var(--ss-warning)" />
          <rect x="76" y="12" width="6" height="6" rx="1.5" fill="var(--ss-primary)" transform="rotate(20 79 15)" />
        </svg>
      );
    case 'courses':
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <rect x="10" y="20" width="44" height="34" rx="7" fill="var(--ss-primary-soft)" />
          <rect x="30" y="28" width="52" height="38" rx="8" fill="var(--ss-surface)" stroke="var(--ss-border)" />
          <path d="M52 40l10 6-10 6z" fill="var(--ss-primary)" />
          <path d="M74 14l2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5-3.6-3.5 5-.7z" fill="var(--ss-warning)" />
        </svg>
      );
    default:
      return (
        <svg viewBox="0 0 96 80" className={c} aria-hidden>
          <rect x="18" y="16" width="60" height="48" rx="10" fill="var(--ss-surface-2)" stroke="var(--ss-border)" />
          <rect x="28" y="28" width="28" height="5" rx="2.5" fill="var(--ss-border-strong)" />
          <rect x="28" y="38" width="40" height="5" rx="2.5" fill="var(--ss-border)" />
          <rect x="28" y="48" width="20" height="5" rx="2.5" fill="var(--ss-border)" />
          <circle cx="74" cy="20" r="9" fill="var(--ss-primary-soft)" stroke="var(--ss-primary)" strokeWidth="2" />
          <path d="M74 16v5M74 24h.01" stroke="var(--ss-primary)" strokeWidth="2.4" strokeLinecap="round" />
        </svg>
      );
  }
}
