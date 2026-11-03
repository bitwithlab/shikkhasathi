import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, EmptyState, ErrorState, Illustration, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatNumber, formatTaka, localDigits, relativeTime, useT } from '@/lib/i18n';
import { ArrowRight, BookOpenCheck, ExternalLink, kpis, PencilLine, Plus, Star, StatusBadge, Globe2, type CourseItem, Users, VerificationBanner, Wallet } from '../parts';

export default function SellerDashboardPage() {
  const t = useT();
  const user = useAuth((s) => s.user);
  const profile = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  const courses = useQuery({ queryKey: ['seller', 'courses'], queryFn: () => api(contracts.sellerCourses.list) });
  const p = profile.data?.profile ?? null;
  const items = courses.data?.items ?? [];
  const k = kpis(items);

  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-12 pt-5 lg:px-8 lg:pt-7">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm text-muted">{t('nav.teacherStudio')}</p>
          <h1 className="mt-0.5 text-[26px] font-bold leading-tight">{t('seller.dash.hello', { name: firstName(p?.displayName ?? user?.name ?? '') })}</h1>
          {p && (
            <Link to={`/t/${p.slug}`} className="latin mt-2 inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-[13px] font-medium text-muted hover:border-primary hover:text-text">
              <Globe2 className="size-3.5" aria-hidden /> shikkhasathi.com/t/{p.slug}
            </Link>
          )}
        </div>
        <Button asChild size="lg" icon={<Plus className="size-5" aria-hidden />}>
          <Link to="/teach/courses/new">{t('seller.newCourse')}</Link>
        </Button>
      </header>

      {profile.data && <VerificationBanner profile={p} className="mt-5" />}

      {/* KPI band — one surface with dividers, not four floating cards */}
      <section aria-label={t('seller.dash.kpis')} className="mt-6 grid grid-cols-2 gap-px overflow-hidden rounded-card border border-border bg-border shadow-e1 lg:grid-cols-4">
        {courses.isLoading ? (
          Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="bg-surface p-4 sm:p-5">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="mt-3 h-8 w-16" />
            </div>
          ))
        ) : courses.isError ? (
          <div className="col-span-2 bg-surface lg:col-span-4">
            <ErrorState compact onRetry={() => void courses.refetch()} />
          </div>
        ) : (
          <>
            <Kpi icon={BookOpenCheck} label={t('seller.kpi.published')} value={localDigits(k.published, t.locale)} sub={t('seller.kpi.publishedSub', { n: localDigits(items.length, t.locale) })} />
            <Kpi icon={Users} label={t('seller.kpi.students')} value={formatNumber(k.students, t.locale)} sub={t('seller.kpi.studentsSub')} />
            <Kpi icon={Star} label={t('seller.kpi.rating')} value={k.ratingCount ? localDigits(k.rating.toFixed(1), t.locale) : '—'} sub={k.ratingCount ? t('seller.kpi.ratingSub', { n: localDigits(k.ratingCount, t.locale) }) : t('seller.kpi.noRatings')} />
            <Kpi icon={PencilLine} label={t('seller.kpi.drafts')} value={localDigits(k.drafts, t.locale)} sub={t('seller.kpi.draftsSub')} />
          </>
        )}
      </section>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
        <section aria-labelledby="my-courses">
          <div className="mb-3 flex items-end justify-between gap-3">
            <h2 id="my-courses" className="text-lg font-semibold">
              {t('seller.nav.courses')}
            </h2>
            {items.length > 0 && (
              <Link to="/teach/courses" className="inline-flex h-10 items-center gap-1 rounded-control px-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
                {t('common.seeAll')} <ArrowRight className="size-4" aria-hidden />
              </Link>
            )}
          </div>
          <Card className="overflow-hidden">
            {courses.isLoading ? (
              <ul className="divide-y divide-border">
                {Array.from({ length: 4 }, (_, i) => (
                  <li key={i} className="flex items-center gap-3 p-4">
                    <Skeleton className="size-12 rounded-control" />
                    <div className="flex-1">
                      <Skeleton className="h-4 w-2/3" />
                      <Skeleton className="mt-2 h-3 w-1/3" />
                    </div>
                  </li>
                ))}
              </ul>
            ) : courses.isError ? (
              <ErrorState onRetry={() => void courses.refetch()} />
            ) : items.length === 0 ? (
              <EmptyState
                illustration={<Illustration kind="courses" />}
                title={t('seller.dash.emptyTitle')}
                body={t('seller.dash.emptyBody')}
                action={
                  <Button asChild variant="soft" icon={<Plus className="size-4" aria-hidden />}>
                    <Link to="/teach/courses/new">{t('seller.dash.emptyCta')}</Link>
                  </Button>
                }
              />
            ) : (
              <ul className="divide-y divide-border">
                {items.slice(0, 6).map((c) => (
                  <CourseRow key={c.id} c={c} />
                ))}
              </ul>
            )}
          </Card>
        </section>

        <aside className="flex flex-col gap-4">
          <BalanceCard />
          <section className="surface-card p-5" aria-labelledby="tips">
            <h2 id="tips" className="font-semibold">
              {t('seller.dash.tipsTitle')}
            </h2>
            <ol className="mt-3 flex flex-col gap-3 text-sm">
              {['tip1', 'tip2', 'tip3'].map((k, i) => (
                <li key={k} className="flex gap-3">
                  <span className="latin flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-soft text-xs font-bold text-primary-fg">{localDigits(i + 1, t.locale)}</span>
                  <span className="text-muted">{t(`seller.dash.${k}`)}</span>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </div>
  );
}

function Kpi({ icon: Icon, label, value, sub }: { icon: typeof Star; label: string; value: string; sub: string }) {
  return (
    <div className="min-w-0 bg-surface p-4 sm:p-5">
      <p className="flex items-center gap-2 text-sm text-muted">
        <Icon className="size-4 shrink-0" strokeWidth={1.75} aria-hidden />
        <span className="truncate">{label}</span>
      </p>
      <p className="latin mt-1.5 text-[28px] font-bold leading-none tnum">{value}</p>
      <p className="mt-1.5 truncate text-xs text-muted">{sub}</p>
    </div>
  );
}

function CourseRow({ c }: { c: CourseItem }) {
  const t = useT();
  const live = c.status === 'published' || c.status === 'unlisted';
  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-2 p-4 sm:flex-nowrap">
      <span className="latin flex size-12 shrink-0 items-center justify-center rounded-control text-[11px] font-bold leading-tight text-white" style={{ background: c.coverColor ?? '#4F46E5' }} aria-hidden>
        {(c.courseCode ?? '').slice(0, 9) || c.title.slice(0, 2)}
      </span>
      <div className="min-w-0 flex-1 basis-48">
        <Link to={`/teach/courses/${c.id}/edit`} className="latin line-clamp-1 font-semibold hover:text-primary-fg">
          {c.title}
        </Link>
        <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
          <StatusBadge status={c.status} />
          <span className="tnum">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
          <span>{t('seller.row.students', { n: formatNumber(c.enrollmentCount, t.locale) })}</span>
          <span>{t('seller.row.updated', { when: relativeTime(c.updatedAt, t.locale) })}</span>
        </p>
      </div>
      <div className="ml-auto flex shrink-0 gap-1">
        {live && (
          <Button asChild variant="ghost" aria-label={t('seller.row.viewPublic')} icon={<ExternalLink className="size-4" aria-hidden />}>
            <Link to={`/c/${c.slug}`} />
          </Button>
        )}
        <Button asChild variant="secondary" icon={<PencilLine className="size-4" aria-hidden />}>
          <Link to={`/teach/courses/${c.id}/edit`}>{t('common.edit')}</Link>
        </Button>
      </div>
    </li>
  );
}

/** "মো. রাকিবুল হাসান" → "রাকিবুল" (skip honorific prefixes like মো. / Dr.). */
export function firstName(full: string) {
  const parts = full.trim().split(/\s+/).filter(Boolean);
  return parts.find((w) => !w.endsWith('.')) ?? parts[0] ?? '';
}

function BalanceCard() {
  const t = useT();
  const q = useQuery({ queryKey: ['seller', 'ledger', 'summary'], queryFn: () => api(contracts.sellerMoney.ledger, { query: { limit: 1 } }) });
  const b = q.data?.balance;
  return (
    <section className="surface-card p-5" aria-labelledby="earn-h">
      <div className="flex items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-control bg-success-soft text-success-fg">
          <Wallet className="size-5" strokeWidth={1.75} aria-hidden />
        </span>
        <h2 id="earn-h" className="font-semibold">{t('seller.dash.balance')}</h2>
      </div>
      {q.isLoading ? (
        <Skeleton className="mt-4 h-16 rounded-control" />
      ) : q.isError || !b ? (
        <ErrorState compact onRetry={() => void q.refetch()} />
      ) : (
        <>
          <p className="mt-4 text-sm text-muted">{t('seller.dash.available')}</p>
          <p className="text-3xl font-bold tnum">{formatTaka(b.available, t.locale)}</p>
          <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
            <div className="rounded-control bg-surface-2 px-3 py-2"><dt className="text-xs text-muted">{t('seller.dash.held')}</dt><dd className="font-semibold tnum">{formatTaka(b.held, t.locale)}</dd></div>
            <div className="rounded-control bg-surface-2 px-3 py-2"><dt className="text-xs text-muted">{t('seller.dash.pendingPayout')}</dt><dd className="font-semibold tnum">{formatTaka(b.pendingPayouts, t.locale)}</dd></div>
          </dl>
          <Button asChild variant="secondary" size="sm" className="mt-4" block><Link to="/teach/earnings">{t('seller.dash.toEarnings')}<ArrowRight className="size-4" aria-hidden /></Link></Button>
        </>
      )}
    </section>
  );
}
