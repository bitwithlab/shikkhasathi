import './i18n';
import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PAYOUT_STATUSES } from '@shikkhasathi/shared';
import { AlertTriangle, BadgeCheck, Banknote, Check, ExternalLink, Wallet, X } from 'lucide-react';
import { Avatar, Badge, Button, Card, Dialog, Field, Input, Textarea, toast, type Tone } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatTaka, relativeTime, useT } from '@/lib/i18n';
import { AdminHeader, CardSkeletons, CardState, LoadMore, StatusTabs, errMsg } from './shared';

type Status = (typeof PAYOUT_STATUSES)[number];
type Decision = 'approve' | 'reject' | 'paid';
type Item = Awaited<ReturnType<typeof api<typeof contracts.adminOps.payouts>>>['items'][number];
const TONE: Record<Status, Tone> = { requested: 'warning', approved: 'primary', paid: 'success', rejected: 'neutral' };

export default function AdminPayoutsPage() {
  const t = useT();
  const [status, setStatus] = useState<Status>('requested');
  const [deciding, setDeciding] = useState<{ item: Item; decision: Decision } | null>(null);
  const list = useInfiniteQuery({
    queryKey: ['admin', 'payouts', status],
    queryFn: ({ pageParam }) => api(contracts.adminOps.payouts, { query: { status, cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  const total = items.reduce((s, p) => s + p.amount, 0);
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.payouts')} subtitle={t('admin.payouts.subtitle')} />
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        <StatusTabs value={status} options={PAYOUT_STATUSES} onChange={setStatus} label={t('admin.common.status')} labelKey={(s) => `admin.payoutStatus.${s}`} />
        {items.length > 0 && <p className="text-sm text-muted">{t('admin.payouts.sum', { a: formatTaka(total, t.locale) })}</p>}
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {list.isLoading ? (
          <CardSkeletons h="h-32" />
        ) : list.isError || !items.length ? (
          <CardState error={list.isError} onRetry={() => void list.refetch()} emptyIcon={<Wallet className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={t(`admin.payouts.empty.${status}`)} />
        ) : (
          <>
            {items.map((p) => <PayoutCard key={p.id} p={p} onDecide={(decision) => setDeciding({ item: p, decision })} />)}
            {list.hasNextPage && <Card><LoadMore shown={items.length} hasMore loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} /></Card>}
          </>
        )}
      </div>
      {deciding && <DecideDialog {...deciding} onClose={() => setDeciding(null)} />}
    </div>
  );
}

function PayoutCard({ p, onDecide }: { p: Item; onDecide: (d: Decision) => void }) {
  const t = useT();
  const short = p.status === 'requested' || p.status === 'approved' ? p.amount > p.available : false;
  return (
    <Card className="p-4 sm:p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-center">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <Avatar name={p.sellerName} size={44} />
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 font-semibold">
              {p.sellerName}
              {p.sellerSlug && <a href={`/t/${p.sellerSlug}`} target="_blank" rel="noreferrer noopener" aria-label={t('admin.payouts.storefront')} className="text-muted hover:text-primary-fg"><ExternalLink className="size-3.5" aria-hidden /></a>}
              <Badge tone={TONE[p.status]}>{t(`admin.payoutStatus.${p.status}`)}</Badge>
            </p>
            <p className="latin mt-0.5 text-sm text-muted">{p.method} · {p.accountMasked ?? '—'}</p>
            <p className="mt-0.5 text-xs text-muted">{t('admin.payouts.requested', { when: relativeTime(p.createdAt, t.locale) })}{p.processedAt && ` · ${t('admin.payouts.processed', { when: formatDate(p.processedAt, t.locale) })}`}</p>
            {p.reference && <p className="latin mt-1 text-sm">{t('admin.payouts.ref')}: <span className="select-all font-mono">{p.reference}</span></p>}
            {p.note && <p className="mt-1 text-sm text-muted">{p.note}</p>}
          </div>
        </div>
        <dl className="grid shrink-0 grid-cols-2 gap-4 text-right md:w-64">
          <div>
            <dt className="text-xs text-muted">{t('admin.payouts.amount')}</dt>
            <dd className="text-xl font-bold tnum">{formatTaka(p.amount, t.locale)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">{t('admin.payouts.available')}</dt>
            <dd className={short ? 'font-semibold text-danger-fg tnum' : 'font-semibold tnum'}>{formatTaka(p.available, t.locale)}</dd>
          </div>
        </dl>
        {(p.status === 'requested' || p.status === 'approved') && (
          <div className="flex shrink-0 flex-wrap gap-2 md:w-40 md:flex-col">
            {p.status === 'requested' && <Button size="sm" variant="secondary" icon={<Check className="size-4" aria-hidden />} onClick={() => onDecide('approve')}>{t('admin.payouts.approve')}</Button>}
            <Button size="sm" icon={<Banknote className="size-4" aria-hidden />} onClick={() => onDecide('paid')}>{t('admin.payouts.markPaid')}</Button>
            <Button size="sm" variant="ghost" icon={<X className="size-4" aria-hidden />} onClick={() => onDecide('reject')}>{t('admin.common.reject')}</Button>
          </div>
        )}
      </div>
      {short && (
        <p className="mt-3 flex items-center gap-2 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger-fg">
          <AlertTriangle className="size-4 shrink-0" aria-hidden /> {t('admin.payouts.short')}
        </p>
      )}
    </Card>
  );
}

function DecideDialog({ item, decision, onClose }: { item: Item; decision: Decision; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const [ref, setRef] = useState('');
  const m = useMutation({
    mutationFn: () => api(contracts.adminOps.decidePayout, { params: { id: item.id }, body: { decision, reference: ref.trim() || undefined, note: note.trim() || undefined } }),
    onSuccess: () => {
      toast.success(t(`admin.payouts.done.${decision}`));
      void qc.invalidateQueries({ queryKey: ['admin', 'payouts'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'analytics'] });
      onClose();
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const valid = decision === 'paid' ? ref.trim().length >= 4 : decision === 'reject' ? note.trim().length >= 5 : true;
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t(`admin.payouts.title.${decision}`, { a: formatTaka(item.amount, t.locale) })}
      description={<span className="latin">{item.sellerName} · {item.method} {item.accountMasked}</span>}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant={decision === 'reject' ? 'danger' : 'primary'} loading={m.isPending} disabled={!valid} onClick={() => m.mutate()} icon={decision === 'approve' ? <BadgeCheck className="size-4" aria-hidden /> : undefined}>{t('common.confirm')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="rounded-control bg-surface-2 p-3 text-sm">{t(`admin.payouts.body.${decision}`)}</p>
        {decision === 'paid' && <Field label={t('admin.payouts.refLabel')} hint={t('admin.payouts.refHint')}>{(p) => <Input {...p} value={ref} maxLength={100} onChange={(e) => setRef(e.target.value)} className="latin font-mono" placeholder="BKA7X9Q2LM" autoFocus />}</Field>}
        <Field label={decision === 'reject' ? t('admin.payouts.rejectNote') : `${t('admin.common.note')} (${t('common.optional')})`} hint={decision === 'reject' ? t('admin.payouts.rejectHint') : undefined}>{(p) => <Textarea {...p} value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />}</Field>
      </div>
    </Dialog>
  );
}
