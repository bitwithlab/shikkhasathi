import { Link } from 'react-router';
import { ArrowRight, Hourglass, ShieldX, ShieldQuestion } from './icons';
import type { SellerProfile } from '@/api';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

/** Calm banner for teachers who are not verified yet: drafts are fine, submitting waits for verification. */
export function VerificationBanner({ profile, className }: { profile: SellerProfile | null; className?: string }) {
  const t = useT();
  const s = profile?.verificationStatus ?? 'none';
  if (s === 'verified') return null;
  const Icon = s === 'pending' ? Hourglass : s === 'rejected' ? ShieldX : ShieldQuestion;
  return (
    <div role="status" className={cn('flex flex-col gap-3 rounded-card border p-4 sm:flex-row sm:items-center', s === 'rejected' ? 'border-danger/30 bg-danger-soft text-danger-fg' : 'border-warning/30 bg-warning-soft text-warning-fg', className)}>
      <Icon className="size-6 shrink-0" strokeWidth={1.75} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{t(`seller.banner.${s}.title`)}</p>
        <p className="text-sm opacity-90">{s === 'rejected' && profile?.verificationNote ? profile.verificationNote : t(`seller.banner.${s}.body`)}</p>
      </div>
      <Link to="/teach/verification" className="inline-flex h-11 shrink-0 items-center gap-1.5 self-start rounded-control border border-current/30 bg-surface px-4 text-sm font-semibold text-text hover:bg-surface-2 sm:self-center">
        {t(`seller.banner.${s}.cta`)} <ArrowRight className="size-4" aria-hidden />
      </Link>
    </div>
  );
}
