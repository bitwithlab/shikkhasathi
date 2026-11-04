import './i18n';
import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Clock, PlayCircle, Undo2, X } from 'lucide-react';
import { Avatar, Badge, Button, Card, Dialog, Field, Input, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatTaka, localDigits, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { AdminHeader, CardSkeletons, CardState, LoadMore, StatusTabs, errMsg, shortId } from './shared';

const TABS = ['requested', 'processed', 'rejected'] as const;
type Tab = (typeof TABS)[number];
type Item = Awaited<ReturnType<typeof api<typeof contracts.adminCommerce.refunds>>>['items'][number];

/** Policy (spec §8): within 48 h of purchase and under 20% watched. */
const MAX_HOURS = 48;
const MAX_WATCHED = 20;

export default function AdminRefundsPage() {
  const t = useT();
  const [status, setStatus] = useState<Tab>('requested');
  const [deciding, setDeciding] = useState<{ item: Item; decision: 'approve' | 'reject' } | null>(null);
  const list = useInfiniteQuery({
    queryKey: ['admin', 'refunds', status],
    queryFn: ({ pageParam }) => api(contracts.adminCommerce.refunds, { query: { status, cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.refunds')} subtitle={t('admin.refunds.subtitle')} />
      <div className="mt-5">
        <StatusTabs value={status} options={TABS} onChange={setStatus} label={t('admin.common.status')} labelKey={(s) => `admin.refundStatus.${s}`} />
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {list.isLoading ? (
          <CardSkeletons />
        ) : list.isError || !items.length ? (
          <CardState error={list.isError} onRetry={() => void list.refetch()} emptyIcon={<Undo2 className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={t(`admin.refunds.empty.${status}`)} />
        ) : (
          <>
            {items.map((r) => <RefundCard key={r.id} r={r} onDecide={(decision) => setDeciding({ item: r, decision })} />)}
            {list.hasNextPage && <Card><LoadMore shown={items.length} hasMore loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} /></Card>}
          </>
        )}
      </div>
      {deciding && <DecideDialog {...deciding} onClose={() => setDeciding(null)} />}
    </div>
  );
}

function RefundCard({ r, onDecide }: { r: Item; onDecide: (d: 'approve' | 'reject') => void }) {
  const t = useT();
  const lateHours = r.hoursSincePurchase > MAX_HOURS;
  const watchedHigh = r.watchedPercent >= MAX_WATCHED;
  const eligible = !lateHours && !watchedHigh;
  return (
    <Card className="p-4 sm:p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-start">
        <Avatar name={r.studentName} size={44} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
            <p className="text-lg font-semibold">{r.studentName}</p>
            <p className="text-xl font-bold tnum">{formatTaka(r.amount, t.locale)}</p>
          </div>
          <p className="latin text-sm text-muted">{r.items.join(' · ')} · #{shortId(r.orderId)}</p>
          <blockquote className="mt-3 border-l-2 border-border-strong pl-3 text-sm">{r.reason}</blockquote>
          <div className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
            <Meter icon={<Clock className="size-4" aria-hidden />} warn={lateHours} label={t('admin.refunds.since')} value={t('admin.refunds.hours', { n: localDigits(r.hoursSincePurchase, t.locale) })} pct={Math.min(100, (r.hoursSincePurchase / MAX_HOURS) * 100)} limit={t('admin.refunds.hoursLimit', { n: localDigits(MAX_HOURS, t.locale) })} />
            <Meter icon={<PlayCircle className="size-4" aria-hidden />} warn={watchedHigh} label={t('admin.refunds.watched')} value={`${localDigits(r.watchedPercent, t.locale)}%`} pct={Math.min(100, (r.watchedPercent / MAX_WATCHED) * 100)} limit={t('admin.refunds.watchedLimit', { n: localDigits(MAX_WATCHED, t.locale) })} />
            <div className="flex items-center sm:justify-end">
              {eligible ? <Badge tone="success" icon={<Check className="size-3.5" aria-hidden />}>{t('admin.refunds.eligible')}</Badge> : <Badge tone="warning" icon={<AlertTriangle className="size-3.5" aria-hidden />}>{t('admin.refunds.outside')}</Badge>}
            </div>
          </div>
          <p className="mt-3 text-xs text-muted">{t('admin.refunds.requested', { when: relativeTime(r.createdAt, t.locale) })}</p>
          {r.note && <p className="mt-1 text-sm"><span className="text-muted">{t('admin.common.note')}:</span> {r.note}</p>}
        </div>
        {r.status === 'requested' && (
          <div className="flex shrink-0 gap-2 md:flex-col">
            <Button size="sm" icon={<Check className="size-4" aria-hidden />} onClick={() => onDecide('approve')}>{t('admin.refunds.approve')}</Button>
            <Button size="sm" variant="ghost" icon={<X className="size-4" aria-hidden />} onClick={() => onDecide('reject')}>{t('admin.common.reject')}</Button>
          </div>
        )}
        {r.status !== 'requested' && <Badge tone={r.status === 'rejected' ? 'neutral' : 'success'} className="self-start">{t(`admin.refundStatus.${r.status}`)}</Badge>}
      </div>
    </Card>
  );
}

function Meter({ icon, label, value, pct, warn, limit }: { icon: React.ReactNode; label: string; value: string; pct: number; warn: boolean; limit: string }) {
  return (
    <div className="rounded-control bg-surface-2 px-3 py-2">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="flex items-center gap-1.5 text-muted">{icon}{label}</span>
        <span className={cn('font-semibold tnum', warn && 'text-warning-fg')}>{value}</span>
      </div>
      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-border" aria-hidden>
        <div className={cn('h-full rounded-full', warn ? 'bg-warning' : 'bg-success')} style={{ width: `${pct}%` }} />
      </div>
      <p className="mt-1 text-xs text-muted">{limit}</p>
    </div>
  );
}

function DecideDialog({ item, decision, onClose }: { item: Item; decision: 'approve' | 'reject'; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const [ref, setRef] = useState('');
  const m = useMutation({
    mutationFn: () => api(contracts.adminCommerce.decideRefund, { params: { id: item.id }, body: { decision, note: note.trim() || undefined, manualReference: ref.trim() || undefined } }),
    onSuccess: () => {
      toast.success(decision === 'approve' ? t('admin.refunds.approved') : t('admin.refunds.rejected'));
      void qc.invalidateQueries({ queryKey: ['admin', 'refunds'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'analytics'] });
      onClose();
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const approve = decision === 'approve';
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={approve ? t('admin.refunds.approveTitle', { a: formatTaka(item.amount, t.locale) }) : t('admin.refunds.rejectTitle')}
      description={`${item.studentName} · ${item.items.join(', ')}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant={approve ? 'primary' : 'danger'} loading={m.isPending} disabled={!approve && note.trim().length < 5} onClick={() => m.mutate()}>{approve ? t('admin.refunds.approve') : t('admin.common.reject')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {approve && <p className="rounded-control bg-surface-2 p-3 text-sm">{t('admin.refunds.approveBody')}</p>}
        {approve && <Field label={t('admin.refunds.manualRef')} hint={t('admin.refunds.manualRefHint')}>{(p) => <Input {...p} value={ref} maxLength={100} onChange={(e) => setRef(e.target.value)} className="latin" placeholder="8N7A6B5C4D" />}</Field>}
        <Field label={approve ? `${t('admin.common.note')} (${t('common.optional')})` : t('admin.refunds.rejectNote')} hint={approve ? undefined : t('admin.refunds.rejectHint')}>{(p) => <Textarea {...p} value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />}</Field>
      </div>
    </Dialog>
  );
}
