import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { InferResponse } from '@shikkhasathi/shared';
import { Badge, Button, Card, EmptyState, ErrorState, Field, Illustration, Input, Skeleton, toast, type Tone } from '@/components';
import { api, contracts } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDate, formatTaka, useT } from '@/lib/i18n';
import { ArrowRight, Clock, Hourglass, Landmark, paisaToTaka, Smartphone, takaToPaisa, Wallet } from '../parts';
import { errorText, LoadMore, PageHead, pageClass } from '../ops/kit';

type Ledger = InferResponse<typeof contracts.sellerMoney.ledger>;
type Entry = Ledger['items'][number];
type Balance = Ledger['balance'];
type Payout = InferResponse<typeof contracts.sellerOps.payouts>['items'][number];

const TYPE_TONE: Record<Entry['type'], Tone> = { sale: 'success', commission: 'neutral', refund: 'danger', payout: 'primary', adjustment: 'warning' };
const PAYOUT_TONE: Record<Payout['status'], Tone> = { requested: 'warning', approved: 'primary', paid: 'success', rejected: 'danger' };

export default function SellerEarningsScreen() {
  const t = useT();
  const ledger = useInfiniteQuery({
    queryKey: ['seller', 'ledger'],
    queryFn: ({ pageParam }) => api(contracts.sellerMoney.ledger, { query: { cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const payouts = useQuery({ queryKey: ['seller', 'payouts'], queryFn: () => api(contracts.sellerOps.payouts) });
  const profile = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  const balance = ledger.data?.pages[0]?.balance;
  const entries = ledger.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className={pageClass}>
      <PageHead title={t('seller.nav.earnings')} subtitle={t('ops.earn.subtitle')} />

      {ledger.isLoading ? (
        <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <Skeleton className="h-64 rounded-card" />
          <Skeleton className="h-64 rounded-card" />
        </div>
      ) : ledger.isError || !balance ? (
        <Card className="mt-6">
          <ErrorState onRetry={() => void ledger.refetch()} />
        </Card>
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] lg:items-stretch">
          <PayoutPanel balance={balance} payouts={payouts.data?.items} payoutsLoading={payouts.isLoading} method={profile.data?.profile?.payoutMethod ?? null} account={profile.data?.profile?.payoutAccountMasked ?? null} profileLoaded={!!profile.data} />
          <BalanceList balance={balance} />
        </div>
      )}

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <section aria-labelledby="ledger-h">
          <h2 id="ledger-h" className="mb-3 text-lg font-semibold">
            {t('ops.earn.ledger')}
          </h2>
          <Card className="overflow-hidden">
            {ledger.isLoading ? (
              <RowsSkeleton />
            ) : ledger.isError ? (
              <ErrorState compact onRetry={() => void ledger.refetch()} />
            ) : entries.length === 0 ? (
              <EmptyState compact illustration={<Illustration kind="empty" />} title={t('ops.earn.emptyTitle')} body={t('ops.earn.emptyBody')} />
            ) : (
              <>
                <ul className="divide-y divide-border">
                  {entries.map((e) => (
                    <LedgerRow key={e.id} e={e} />
                  ))}
                </ul>
                <LoadMore hasMore={!!ledger.hasNextPage} loading={ledger.isFetchingNextPage} onMore={() => void ledger.fetchNextPage()} label={t('ops.loadMore')} />
              </>
            )}
          </Card>
        </section>

        <section aria-labelledby="payouts-h">
          <h2 id="payouts-h" className="mb-3 text-lg font-semibold">
            {t('ops.earn.history')}
          </h2>
          <Card className="overflow-hidden">
            {payouts.isLoading ? (
              <RowsSkeleton n={3} />
            ) : payouts.isError ? (
              <ErrorState compact onRetry={() => void payouts.refetch()} />
            ) : !payouts.data?.items.length ? (
              <p className="p-5 text-sm text-muted">{t('ops.earn.noPayouts')}</p>
            ) : (
              <ul className="divide-y divide-border">
                {payouts.data.items.map((p) => (
                  <PayoutRow key={p.id} p={p} />
                ))}
              </ul>
            )}
          </Card>
        </section>
      </div>
    </div>
  );
}

function PayoutPanel({ balance, payouts, payoutsLoading, method, account, profileLoaded }: { balance: Balance; payouts?: Payout[]; payoutsLoading: boolean; method: string | null; account: string | null; profileLoaded: boolean }) {
  const t = useT();
  const qc = useQueryClient();
  const [amount, setAmount] = useState(() => paisaToTaka(balance.available));
  const [err, setErr] = useState<string | null>(null);
  const open = payouts?.find((p) => p.status === 'requested' || p.status === 'approved');
  const enough = balance.available >= balance.minPayout;
  const request = useMutation({
    mutationFn: (paisa: number) => api(contracts.sellerOps.requestPayout, { body: { amount: paisa } }),
    onSuccess: () => {
      toast.success(t('ops.earn.requested'));
      setErr(null);
      void qc.invalidateQueries({ queryKey: ['seller', 'payouts'] });
      void qc.invalidateQueries({ queryKey: ['seller', 'ledger'] });
    },
    onError: (e) => setErr(errorText(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const p = takaToPaisa(amount);
    if (p == null || Number.isNaN(p) || p <= 0) return setErr(t('ops.earn.errAmount'));
    if (p < balance.minPayout) return setErr(t('ops.earn.errMin', { min: formatTaka(balance.minPayout, t.locale) }));
    if (p > balance.available) return setErr(t('ops.earn.errMax', { max: formatTaka(balance.available, t.locale) }));
    setErr(null);
    request.mutate(p);
  };
  const MethodIcon = method === 'bank' ? Landmark : Smartphone;

  return (
    <section aria-labelledby="avail-h" className="surface-card flex flex-col p-5 sm:p-6">
      <p id="avail-h" className="flex items-center gap-2 text-sm font-medium text-muted">
        <Wallet className="size-4" strokeWidth={1.75} aria-hidden /> {t('ops.earn.available')}
      </p>
      <p className="latin mt-1 text-[40px] font-bold leading-none tnum">{formatTaka(balance.available, t.locale)}</p>
      <p className="mt-2 text-sm text-muted">{t('ops.earn.minNote', { min: formatTaka(balance.minPayout, t.locale) })}</p>

      <div className="mt-5 border-t border-border pt-5">
        {!profileLoaded || payoutsLoading ? (
          <Skeleton className="h-12 w-full" />
        ) : !method || !account ? (
          <div className="rounded-control border border-warning/30 bg-warning-soft p-4 text-sm text-warning-fg">
            <p className="font-semibold">{t('ops.earn.noMethod')}</p>
            <p className="mt-0.5 opacity-90">{t('ops.earn.noMethodBody')}</p>
            <Link to="/teach/verification" className="mt-3 inline-flex h-11 items-center gap-1.5 rounded-control border border-current/30 bg-surface px-4 font-semibold text-text hover:bg-surface-2">
              {t('ops.earn.addMethod')} <ArrowRight className="size-4" aria-hidden />
            </Link>
          </div>
        ) : open ? (
          <div className="flex items-start gap-3 rounded-control bg-primary-soft p-4 text-sm text-primary-fg" role="status">
            <Hourglass className="mt-0.5 size-5 shrink-0" strokeWidth={1.75} aria-hidden />
            <div>
              <p className="font-semibold">{t('ops.earn.openTitle', { amount: formatTaka(open.amount, t.locale) })}</p>
              <p className="mt-0.5 opacity-90">{t(`ops.earn.open.${open.status}`)}</p>
            </div>
          </div>
        ) : (
          <form onSubmit={submit} noValidate className="flex flex-col gap-3">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
              <Field className="flex-1" label={t('ops.earn.amount')} error={err ?? undefined} hint={enough ? undefined : t('ops.earn.notEnough', { gap: formatTaka(balance.minPayout - balance.available, t.locale) })}>
                {(p) => (
                  <div className="relative">
                    <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted">৳</span>
                    <Input {...p} inputMode="decimal" className="latin pl-8 tnum" value={amount} disabled={!enough} onChange={(e) => setAmount(e.target.value)} />
                  </div>
                )}
              </Field>
              <Button type="submit" size="lg" className="sm:mt-[26px]" disabled={!enough} loading={request.isPending}>
                {t('ops.earn.request')}
              </Button>
            </div>
            <p className="flex items-center gap-1.5 text-[13px] text-muted">
              <MethodIcon className="size-4" strokeWidth={1.75} aria-hidden />
              {t('ops.earn.sendTo', { method: t(`seller.payout.${method}`), account })}
            </p>
          </form>
        )}
      </div>
    </section>
  );
}

function BalanceList({ balance }: { balance: Balance }) {
  const t = useT();
  const rows: { k: string; v: number; note?: string; tone?: string }[] = [
    { k: 'held', v: balance.held, note: t('ops.earn.heldNote'), tone: 'text-warning-fg' },
    { k: 'pending', v: balance.pendingPayouts },
    { k: 'paidOut', v: balance.paidOut },
    { k: 'lifetime', v: balance.lifetime },
  ];
  return (
    <dl className="surface-card divide-y divide-border">
      {rows.map((r) => (
        <div key={r.k} className="flex items-baseline justify-between gap-4 px-5 py-4">
          <dt className="min-w-0">
            <span className="block text-sm font-medium">{t(`ops.earn.${r.k}`)}</span>
            {r.note && <span className="block text-xs text-muted">{r.note}</span>}
          </dt>
          <dd className={cn('latin shrink-0 text-lg font-semibold tnum', r.tone)}>{formatTaka(r.v, t.locale)}</dd>
        </div>
      ))}
    </dl>
  );
}

function LedgerRow({ e }: { e: Entry }) {
  const t = useT();
  const held = new Date(e.availableAt).getTime() > Date.now();
  const positive = e.amount > 0;
  return (
    <li className="flex items-start gap-3 px-4 py-3.5">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={TYPE_TONE[e.type]}>{t(`ops.ledger.${e.type}`)}</Badge>
          <span className="text-xs text-muted">{formatDate(e.createdAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
        </div>
        {e.description && <p className="latin mt-1 line-clamp-2 text-sm">{e.description}</p>}
        {held && (
          <p className="mt-1 flex items-center gap-1 text-xs text-warning-fg">
            <Clock className="size-3.5" aria-hidden /> {t('ops.earn.availableOn', { date: formatDate(e.availableAt, t.locale) })}
          </p>
        )}
      </div>
      <div className="shrink-0 text-right">
        <p className={cn('latin font-semibold tnum', positive ? 'text-success-fg' : 'text-text')}>
          <span className="sr-only">{positive ? t('ops.credit') : t('ops.debit')} </span>
          {positive ? '+' : '−'}
          {formatTaka(Math.abs(e.amount), t.locale)}
        </p>
        <p className="latin text-xs text-muted tnum">{t('ops.earn.after', { amount: formatTaka(e.balanceAfter, t.locale) })}</p>
      </div>
    </li>
  );
}

function PayoutRow({ p }: { p: Payout }) {
  const t = useT();
  return (
    <li className="px-4 py-3.5">
      <div className="flex items-center justify-between gap-3">
        <span className="latin font-semibold tnum">{formatTaka(p.amount, t.locale)}</span>
        <Badge tone={PAYOUT_TONE[p.status]}>{t(`ops.payout.${p.status}`)}</Badge>
      </div>
      <p className="latin mt-1 text-xs text-muted">
        {t(`seller.payout.${p.method}`)} {p.accountMasked ?? ''} · {formatDate(p.createdAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' })}
      </p>
      {p.reference && <p className="latin mt-1 text-xs text-muted">{t('ops.payout.ref', { ref: p.reference })}</p>}
      {p.note && <p className="mt-1 text-xs text-muted">{p.note}</p>}
    </li>
  );
}

function RowsSkeleton({ n = 5 }: { n?: number }) {
  return (
    <ul className="divide-y divide-border" aria-busy>
      {Array.from({ length: n }, (_, i) => (
        <li key={i} className="flex items-center gap-3 p-4">
          <div className="flex-1">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-2 h-3 w-2/3" />
          </div>
          <Skeleton className="h-5 w-16" />
        </li>
      ))}
    </ul>
  );
}
