import { Link, Navigate, useNavigate } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, ErrorState, Logo, Skeleton, toast } from '@/components';
import { api, ApiError, contracts, refreshSession } from '@/lib/api';
import { hasRole, useAuth } from '@/lib/auth';
import { localDigits, useT } from '@/lib/i18n';
import { ApplicationForm, ArrowLeft, BadgeCheck, FilePenLine, Rocket, ShieldCheck, type ApplyResult } from '../parts';

export default function SellerApplyPage() {
  const t = useT();
  const nav = useNavigate();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const profile = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });

  if (hasRole(user, 'seller')) return <Navigate to="/teach" replace />;

  const done = async ({ profile: p, pendingCode }: ApplyResult) => {
    qc.setQueryData(['seller', 'profile'], { profile: p });
    if (pendingCode) {
      await api(contracts.sellerApply.emailConfirm, { body: pendingCode })
        .then((np) => qc.setQueryData(['seller', 'profile'], { profile: np }))
        .catch((e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')));
    }
    await refreshSession();
    toast.success(t('seller.apply.success'));
    nav('/teach', { replace: true });
  };

  const steps = [
    { icon: FilePenLine, title: t('seller.apply.how1'), body: t('seller.apply.how1Body') },
    { icon: ShieldCheck, title: t('seller.apply.how2'), body: t('seller.apply.how2Body') },
    { icon: Rocket, title: t('seller.apply.how3'), body: t('seller.apply.how3Body') },
  ];

  return (
    <div className="min-h-dvh bg-bg">
      <header className="sticky top-0 z-20 border-b border-border bg-surface/95">
        <div className="mx-auto flex h-14 max-w-[1200px] items-center justify-between gap-3 px-2 sm:px-4 lg:px-8">
          <Button asChild variant="ghost" size="sm" icon={<ArrowLeft className="size-5 text-primary" aria-hidden />}>
            <Link to="/home">{t('seller.apply.backHome')}</Link>
          </Button>
          <Logo />
          <span className="w-20" aria-hidden />
        </div>
      </header>
      <main id="main" className="mx-auto grid max-w-[1200px] gap-6 px-4 pb-16 pt-6 lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-10 lg:px-8 lg:pt-10">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <p className="text-sm font-semibold text-primary-fg">{t('nav.teacherStudio')}</p>
          <h1 className="mt-1 text-[28px] font-bold leading-snug">{t('seller.apply.title')}</h1>
          <p className="mt-2 text-[15px] text-muted">{t('seller.apply.lead')}</p>
          <ol className="mt-6 flex flex-col gap-4">
            {steps.map((s, i) => (
              <li key={s.title} className="flex gap-3">
                <span className="relative flex size-10 shrink-0 items-center justify-center rounded-full border border-border bg-surface text-primary">
                  <s.icon className="size-5" strokeWidth={1.75} aria-hidden />
                  <span className="latin absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-on-primary">{localDigits(i + 1, t.locale)}</span>
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold">{s.title}</span>
                  <span className="block text-sm text-muted">{s.body}</span>
                </span>
              </li>
            ))}
          </ol>
          <div className="mt-6 flex gap-3 rounded-card border border-warning/30 bg-warning-soft p-4 text-sm text-warning-fg">
            <BadgeCheck className="mt-0.5 size-5 shrink-0" aria-hidden />
            <p>{t('seller.apply.draftNote')}</p>
          </div>
        </aside>
        <section aria-label={t('seller.apply.title')}>
          {profile.isLoading ? (
            <div className="flex flex-col gap-5">
              <Skeleton className="h-80 rounded-card" />
              <Skeleton className="h-56 rounded-card" />
            </div>
          ) : profile.isError ? (
            <Card>
              <ErrorState onRetry={() => void profile.refetch()} />
            </Card>
          ) : (
            <ApplicationForm initial={profile.data?.profile ?? null} defaultName={user?.name ?? ''} defaultEmail={user?.email} submitLabel={t('seller.apply.submit')} onDone={done} />
          )}
        </section>
      </main>
    </div>
  );
}
