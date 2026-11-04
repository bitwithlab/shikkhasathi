import { useState } from 'react';
import { Link } from 'react-router';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, ReceiptText, RotateCcw } from 'lucide-react';
import type { Order } from '@/api';
import { Badge, Button, Dialog, EmptyState, ErrorState, Field, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { formatDate, formatTaka, useT } from '@/lib/i18n';

const TONE = { pending: 'warning', paid: 'success', failed: 'danger', refunded: 'neutral', partially_refunded: 'neutral', cancelled: 'neutral' } as const;
const REFUND_TONE = { requested: 'warning', approved: 'warning', processed: 'success', rejected: 'danger' } as const;

/** Payment history with receipts and refund requests (spec §6.7: ≤ 48 h, < 20 % watched). */
export function OrdersList() {
  const t = useT();
  const qc = useQueryClient();
  const [refundFor, setRefundFor] = useState<Order | null>(null);
  const [reason, setReason] = useState('');
  const q = useInfiniteQuery({
    queryKey: ['my-orders'],
    queryFn: ({ pageParam }) => api(contracts.commerce.myOrders, { query: { limit: 10, cursor: pageParam } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (l) => l.nextCursor ?? undefined,
  });
  const receipt = useMutation({
    mutationFn: (id: string) => api(contracts.commerce.invoice, { params: { id } }),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  const refund = useMutation({
    mutationFn: () => api(contracts.commerce.refundRequest, { params: { id: refundFor!.id }, body: { reason: reason.trim() } }),
    onSuccess: () => {
      toast.success(t('prof.ord.sent'));
      setRefundFor(null);
      setReason('');
      void qc.invalidateQueries({ queryKey: ['my-orders'] });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  if (q.isLoading) return <div className="flex flex-col gap-2">{[0, 1].map((i) => <Skeleton key={i} className="h-16 rounded-control" />)}</div>;
  if (q.isError) return <ErrorState compact onRetry={() => void q.refetch()} />;
  if (!items.length) return <EmptyState compact illustration={<ReceiptText className="size-8 text-muted" strokeWidth={1.5} aria-hidden />} title={t('prof.ord.empty')} action={<Button asChild size="sm" variant="secondary"><Link to="/explore">{t('nav.explore')}</Link></Button>} />;
  return (
    <>
      <ul className="divide-y divide-border rounded-control border border-border">
        {items.map((o) => (
          <li key={o.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 p-3">
            <div className="min-w-0 flex-1">
              <p className="latin truncate font-medium">{o.items.map((i) => i.title).join(', ')}</p>
              <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-muted">
                <span>{formatDate(o.paidAt ?? o.createdAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
                {o.invoiceNumber && <span className="latin">{o.invoiceNumber}</span>}
                <Badge tone={TONE[o.status]}>{t(`prof.ord.status.${o.status}`)}</Badge>
                {o.refund && <Badge tone={REFUND_TONE[o.refund.status]}>{t(`prof.ord.refund.${o.refund.status}`)}</Badge>}
              </p>
            </div>
            <span className="shrink-0 font-semibold tnum">{o.total ? formatTaka(o.total, t.locale) : t('prof.ord.free')}</span>
            <div className="flex w-full justify-end gap-2 sm:w-auto">
              {o.invoiceNumber && <Button size="sm" variant="ghost" loading={receipt.isPending && receipt.variables === o.id} onClick={() => receipt.mutate(o.id)} icon={<Download className="size-4" aria-hidden />}>{t('prof.ord.receipt')}</Button>}
              {o.refundEligible && <Button size="sm" variant="secondary" onClick={() => setRefundFor(o)} icon={<RotateCcw className="size-4" aria-hidden />}>{t('prof.ord.refund')}</Button>}
              {(o.status === 'pending' || o.status === 'failed') && o.items[0]?.courseId && <Button asChild size="sm"><Link to={`/checkout/${o.items[0].courseId}`}>{t('prof.ord.pay')}</Link></Button>}
            </div>
          </li>
        ))}
      </ul>
      {q.hasNextPage && <div className="mt-3 flex justify-center"><Button size="sm" variant="secondary" loading={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>{t('prof.ord.more')}</Button></div>}
      <Dialog
        open={!!refundFor}
        onOpenChange={(o) => !o && setRefundFor(null)}
        title={t('prof.ord.refundTitle')}
        description={t('prof.ord.refundBody')}
        footer={<><Button variant="ghost" onClick={() => setRefundFor(null)}>{t('common.cancel')}</Button><Button loading={refund.isPending} disabled={reason.trim().length < 10} onClick={() => refund.mutate()}>{t('prof.ord.send')}</Button></>}
      >
        <p className="latin mb-3 text-sm font-medium">{refundFor?.items.map((i) => i.title).join(', ')}</p>
        <Field label={t('prof.ord.reason')}>{(p) => <Textarea {...p} value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t('prof.ord.reasonPh')} rows={3} />}</Field>
      </Dialog>
    </>
  );
}
