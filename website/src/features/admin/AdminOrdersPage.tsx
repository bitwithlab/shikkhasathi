import './i18n';
import { useState } from 'react';
import { useInfiniteQuery, useMutation } from '@tanstack/react-query';
import { ORDER_STATUSES, type OrderStatus } from '@/api';
import { Download, ExternalLink, Receipt, Search } from 'lucide-react';
import { Badge, Button, Card, Input, Select, Sheet, toast, type Tone } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatTaka, formatTime, localDigits, useT } from '@/lib/i18n';
import { AdminHeader, ListBody, LoadMore, errMsg, shortId, useDebounced } from './shared';

type Item = Awaited<ReturnType<typeof api<typeof contracts.adminCommerce.orders>>>['items'][number];

const ORDER_TONE: Record<OrderStatus, Tone> = { pending: 'warning', paid: 'success', failed: 'danger', cancelled: 'neutral', refunded: 'neutral', partially_refunded: 'warning' };

export default function AdminOrdersPage() {
  const t = useT();
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [search, setSearch] = useState('');
  const q = useDebounced(search.trim());
  const [open, setOpen] = useState<Item | null>(null);
  const list = useInfiniteQuery({
    queryKey: ['admin', 'orders', status, q],
    queryFn: ({ pageParam }) => api(contracts.adminCommerce.orders, { query: { status: status || undefined, q: q || undefined, cursor: pageParam, limit: 30 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.orders')} subtitle={t('admin.orders.subtitle')} />
      <div className="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_220px]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <Input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('admin.orders.searchPh')} aria-label={t('common.search')} className="pl-10" />
        </div>
        <Select value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | '')} aria-label={t('admin.common.status')}>
          <option value="">{t('admin.common.allStatuses')}</option>
          {ORDER_STATUSES.map((s) => <option key={s} value={s}>{t(`admin.orderStatus.${s}`)}</option>)}
        </Select>
      </div>
      <Card className="mt-4 overflow-hidden">
        <ListBody loading={list.isLoading} error={list.isError} empty={!items.length} onRetry={() => void list.refetch()} emptyIcon={<Receipt className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={q || status ? t('admin.common.noMatch') : t('admin.orders.empty')} rows={6}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('admin.orders.order')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.orders.student')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.orders.items')}</th>
                  <th className="px-3 py-2.5 text-right font-medium">{t('admin.orders.total')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.orders.method')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.common.status')}</th>
                  <th className="px-4 py-2.5 font-medium">{t('admin.orders.date')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((o) => (
                  <tr key={o.id} className="border-t border-border hover:bg-surface-2">
                    <td className="px-4 py-3">
                      <button type="button" onClick={() => setOpen(o)} className="latin font-semibold text-primary-fg hover:underline">#{shortId(o.id)}</button>
                    </td>
                    <td className="px-3 py-3">
                      <p className="font-medium">{o.studentName}</p>
                      {o.studentPhone && <p className="latin text-xs text-muted">{o.studentPhone}</p>}
                    </td>
                    <td className="max-w-[260px] px-3 py-3">
                      <p className="latin truncate">{o.items[0]?.title ?? '—'}</p>
                      {o.items.length > 1 && <p className="text-xs text-muted">{t('admin.orders.more', { n: localDigits(o.items.length - 1, t.locale) })}</p>}
                    </td>
                    <td className="px-3 py-3 text-right font-semibold tnum">{formatTaka(o.total, t.locale)}</td>
                    <td className="latin px-3 py-3 text-muted">{o.paymentMethod ?? '—'}</td>
                    <td className="px-3 py-3"><Badge tone={ORDER_TONE[o.status]}>{t(`admin.orderStatus.${o.status}`)}</Badge></td>
                    <td className="px-4 py-3 text-muted">{formatDate(o.createdAt, t.locale)} · {formatTime(o.createdAt, t.locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <LoadMore shown={items.length} hasMore={!!list.hasNextPage} loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} />
        </ListBody>
      </Card>
      {open && <OrderDrawer o={open} onClose={() => setOpen(null)} />}
    </div>
  );
}

function OrderDrawer({ o, onClose }: { o: Item; onClose: () => void }) {
  const t = useT();
  const inv = useMutation({
    mutationFn: () => api(contracts.commerce.invoice, { params: { id: o.id } }),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const row = (label: string, value: React.ReactNode, latin = false) => (
    <div className="flex justify-between gap-4 py-2">
      <dt className="text-muted">{label}</dt>
      <dd className={latin ? 'latin text-right font-medium' : 'text-right font-medium'}>{value}</dd>
    </div>
  );
  return (
    <Sheet
      open
      side="right"
      onOpenChange={(v) => !v && onClose()}
      title={<span className="latin">#{shortId(o.id)}</span>}
      description={`${o.studentName} · ${formatDate(o.createdAt, t.locale, { day: 'numeric', month: 'long', year: 'numeric' })}`}
      footer={
        <Button variant="secondary" className="flex-1" disabled={!o.invoiceNumber} loading={inv.isPending} onClick={() => inv.mutate()} icon={<Download className="size-4" aria-hidden />}>
          {o.invoiceNumber ? t('admin.orders.invoice', { n: o.invoiceNumber }) : t('admin.orders.noInvoice')}
        </Button>
      }
    >
      <div className="flex flex-col gap-5">
        <div className="flex items-center gap-2">
          <Badge tone={ORDER_TONE[o.status]}>{t(`admin.orderStatus.${o.status}`)}</Badge>
          {o.refund && <Badge tone="warning">{t('admin.orders.refund', { s: t(`admin.refundStatus.${o.refund.status}`) })}</Badge>}
        </div>
        <section>
          <h3 className="text-sm font-semibold">{t('admin.orders.items')}</h3>
          <ul className="mt-2 divide-y divide-border rounded-control border border-border">
            {o.items.map((it) => (
              <li key={it.id} className="flex items-start gap-3 p-3">
                <span className="mt-1 size-3 shrink-0 rounded-full" style={{ background: it.coverColor ?? 'var(--ss-primary)' }} aria-hidden />
                <div className="min-w-0 flex-1">
                  <p className="latin text-sm font-medium">{it.title}</p>
                  {it.slug && <a href={`/c/${it.slug}`} target="_blank" rel="noreferrer noopener" className="inline-flex items-center gap-1 text-xs text-primary-fg hover:underline">{t('admin.review.publicPage')} <ExternalLink className="size-3" aria-hidden /></a>}
                </div>
                <div className="text-right text-sm tnum">
                  <p className="font-semibold">{formatTaka(it.price - it.discount, t.locale)}</p>
                  {it.discount > 0 && <p className="text-xs text-muted line-through">{formatTaka(it.price, t.locale)}</p>}
                </div>
              </li>
            ))}
          </ul>
        </section>
        <dl className="divide-y divide-border rounded-control bg-surface-2 px-3 text-sm tnum">
          {row(t('admin.orders.subtotal'), formatTaka(o.subtotal, t.locale))}
          {o.discount > 0 && row(o.couponCode ? t('admin.orders.coupon', { c: o.couponCode }) : t('admin.orders.discount'), `−${formatTaka(o.discount, t.locale)}`)}
          {row(t('admin.orders.total'), <span className="text-base">{formatTaka(o.total, t.locale)}</span>)}
        </dl>
        <section>
          <h3 className="text-sm font-semibold">{t('admin.orders.payment')}</h3>
          <dl className="mt-1 divide-y divide-border text-sm">
            {row(t('admin.orders.method'), o.paymentMethod ?? '—', true)}
            {row(t('admin.orders.gateway'), o.gateway ?? '—', true)}
            <div className="flex flex-col gap-1 py-2">
              <dt className="text-muted">{t('admin.orders.trx')}</dt>
              <dd className="latin select-all break-all rounded-control bg-surface-2 px-2.5 py-1.5 font-mono text-[13px]">{o.gatewayTrxId ?? '—'}</dd>
            </div>
            {row(t('admin.orders.paidAt'), o.paidAt ? `${formatDate(o.paidAt, t.locale)} · ${formatTime(o.paidAt, t.locale)}` : '—')}
            {row(t('admin.orders.student'), <>{o.studentName}{o.studentPhone && <span className="latin block text-xs font-normal text-muted">{o.studentPhone}</span>}</>)}
          </dl>
        </section>
        {o.refund && (
          <section className="rounded-control border border-warning/40 bg-warning-soft/50 p-3 text-sm">
            <p className="font-semibold">{t('admin.orders.refundReq', { a: formatTaka(o.refund.amount, t.locale) })}</p>
            <p className="mt-1">{o.refund.reason}</p>
          </section>
        )}
      </div>
    </Sheet>
  );
}

