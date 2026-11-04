import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { SellerProfile } from '@/api';
import { Badge, Button, Card, ErrorState, Skeleton, toast, type Tone } from '@/components';
import { api, contracts, refreshSession } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatDate, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { ApplicationForm, BadgeCheck, ChevronUp, Hourglass, ShieldX, ShieldQuestion } from '../parts';

const STATUS: Record<SellerProfile['verificationStatus'], { tone: Tone; icon: typeof BadgeCheck; box: string }> = {
  none: { tone: 'neutral', icon: ShieldQuestion, box: 'bg-surface-2 text-muted' },
  pending: { tone: 'warning', icon: Hourglass, box: 'bg-warning-soft text-warning-fg' },
  verified: { tone: 'success', icon: BadgeCheck, box: 'bg-success-soft text-success-fg' },
  rejected: { tone: 'danger', icon: ShieldX, box: 'bg-danger-soft text-danger-fg' },
};

export default function SellerVerificationPage() {
  const t = useT();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const q = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  const p = q.data?.profile ?? null;
  const [editOpen, setEditOpen] = useState<boolean | null>(null);

  if (q.isLoading)
    return (
      <div className="mx-auto max-w-[960px] px-4 py-6 lg:px-8">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="mt-6 h-40 rounded-card" />
        <Skeleton className="mt-6 h-96 rounded-card" />
      </div>
    );
  if (q.isError)
    return (
      <div className="mx-auto max-w-[960px] px-4 py-10">
        <ErrorState onRetry={() => void q.refetch()} />
      </div>
    );

  const status = p?.verificationStatus ?? 'none';
  const meta = STATUS[status];
  // Verified teachers rarely need the form; everyone else sees it open.
  const open = editOpen ?? status !== 'verified';

  return (
    <div className="mx-auto max-w-[960px] px-4 pb-12 pt-6 lg:px-8">
      <header className="mb-6">
        <h1 className="text-2xl font-bold">{t('seller.ver.title')}</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted">{t('seller.ver.subtitle')}</p>
      </header>

      <Card className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start sm:p-6">
        <span className={cn('flex size-14 shrink-0 items-center justify-center rounded-card', meta.box)}>
          <meta.icon className="size-7" strokeWidth={1.75} aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-semibold">{t(`seller.ver.${status}.title`)}</h2>
            <Badge tone={meta.tone}>{t(`seller.ver.status.${status}`)}</Badge>
          </div>
          <p className="mt-1 text-sm text-muted">{t(`seller.ver.${status}.body`)}</p>
          {status === 'rejected' && p?.verificationNote && (
            <blockquote className="mt-3 rounded-control border-l-4 border-danger bg-danger-soft px-4 py-3 text-sm text-danger-fg">
              <span className="block text-xs font-semibold uppercase tracking-wide">{t('seller.ver.note')}</span>
              {p.verificationNote}
            </blockquote>
          )}
          {p && (
            <dl className="mt-4 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-xs text-muted">{t('seller.ver.method')}</dt>
                <dd className="font-medium">{p.verificationMethod ? t(`seller.method.${p.verificationMethod}`) : '—'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">{t('seller.ver.payout')}</dt>
                <dd className="latin font-medium tnum">{p.payoutMethod ? `${t(`seller.payout.${p.payoutMethod}`)} · ${p.payoutAccountMasked ?? ''}` : '—'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">{status === 'verified' ? t('seller.ver.verifiedAt') : t('seller.ver.institution')}</dt>
                <dd className="latin font-medium">{status === 'verified' && p.verifiedAt ? formatDate(p.verifiedAt, t.locale, { day: 'numeric', month: 'long', year: 'numeric' }) : (p.institutionName ?? '—')}</dd>
              </div>
            </dl>
          )}
        </div>
      </Card>

      <section className="mt-8" aria-labelledby="ver-form">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="ver-form" className="text-lg font-semibold">
              {p ? t('seller.ver.update') : t('seller.ver.apply')}
            </h2>
            <p className="text-sm text-muted">{status === 'verified' ? t('seller.ver.updateVerified') : t('seller.ver.updateBody')}</p>
          </div>
          {status === 'verified' && (
            <Button variant="secondary" size="sm" aria-expanded={open} onClick={() => setEditOpen(!open)} icon={<ChevronUp className={cn('rotate-180 size-4 transition-transform', open && 'rotate-0')} aria-hidden />}>
              {open ? t('seller.ver.hideForm') : t('seller.ver.showForm')}
            </Button>
          )}
        </div>
        {open && (
          <ApplicationForm
            key={p?.verificationStatus ?? 'none'}
            initial={p}
            defaultName={user?.name ?? ''} defaultEmail={user?.email}
            submitLabel={p ? t('seller.ver.save') : t('seller.apply.submit')}
            onDone={async ({ profile: np, pendingCode }) => {
              let latest = np;
              if (pendingCode) latest = await api(contracts.sellerApply.emailConfirm, { body: pendingCode }).catch(() => np);
              qc.setQueryData(['seller', 'profile'], { profile: latest });
              await refreshSession();
              toast.success(t('seller.ver.saved'));
              if (status === 'verified') setEditOpen(false);
            }}
          />
        )}
      </section>
    </div>
  );
}
