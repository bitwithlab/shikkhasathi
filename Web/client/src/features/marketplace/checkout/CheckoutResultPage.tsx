import './i18n';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Download, LifeBuoy, RefreshCw, X } from 'lucide-react';
import { Button, Card, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import type { Order } from '@shikkhasathi/shared';
import { formatDate, formatTaka, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

const MAX_POLLS = 15; // ~30 s

/** Gateway return page. The URL status is only a hint — the order fetched from the API is the truth. */
export default function CheckoutResultPage() {
  const [sp] = useSearchParams();
  const orderId = sp.get('order');
  const hint = sp.get('status');
  const t = useT();
  const qc = useQueryClient();
  const polls = useRef(0);
  const [gaveUp, setGaveUp] = useState(false);
  const q = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => api(contracts.commerce.getOrder, { params: { id: orderId! } }),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const o = query.state.data;
      const waiting = !o || o.status === 'pending' || (o.status === 'paid' && !o.invoiceNumber && o.total > 0);
      if (!waiting) return false;
      polls.current += 1;
      if (polls.current > MAX_POLLS) {
        setGaveUp(true);
        return false;
      }
      return 2000;
    },
  });
  useEffect(() => {
    document.title = `${t('co.title')} — ${t('brand.name')}`;
  }, [t]);
  useEffect(() => {
    if (q.data?.status === 'paid') {
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['course'] });
      void qc.invalidateQueries({ queryKey: ['my-courses'] });
    }
  }, [q.data?.status, qc]);

  const o = q.data;
  const status = !orderId ? 'failed' : o ? (o.status === 'paid' ? 'paid' : o.status === 'pending' ? (hint === 'cancelled' ? 'cancelled' : hint === 'failed' ? 'failed' : 'pending') : o.status === 'failed' ? (hint === 'cancelled' ? 'cancelled' : 'failed') : o.status) : q.isError ? 'failed' : 'loading';
  const retryTo = o?.items[0]?.courseId ? `/checkout/${o.items[0].courseId}` : '/my-courses';

  return (
    <div className="mx-auto max-w-lg px-4 py-10 sm:py-16" aria-live="polite">
      {status === 'loading' ? (
        <Card className="flex flex-col items-center p-8"><Skeleton className="size-16 rounded-full" /><Skeleton className="mt-5 h-6 w-48" /><Skeleton className="mt-2 h-4 w-64" /></Card>
      ) : status === 'pending' ? (
        <Card className="flex flex-col items-center p-8 text-center" role="status">
          <span className="size-14 animate-spin rounded-full border-4 border-primary border-r-transparent motion-reduce:animate-none" aria-hidden />
          <h1 className="mt-5 text-lg font-semibold">{t('res.verifying')}</h1>
          <p className="mt-1 text-sm text-muted">{gaveUp ? t('res.stillPending') : t('res.verifyingBody')}</p>
          {gaveUp && <Button className="mt-5" variant="secondary" asChild><Link to="/my-courses">{t('res.myCourses')}</Link></Button>}
        </Card>
      ) : status === 'paid' && o ? (
        <Success order={o} />
      ) : (
        <Card className="flex flex-col items-center p-8 text-center">
          <span className="grid size-16 place-items-center rounded-full bg-danger-soft text-danger-fg" aria-hidden><X className="size-8" strokeWidth={2.5} /></span>
          <h1 className="mt-5 text-xl font-bold">{status === 'cancelled' ? t('res.cancelled') : t('res.failed')}</h1>
          <p className="mt-2 text-muted">{t('res.failedBody')}</p>
          <div className="mt-6 flex w-full flex-col gap-2 sm:flex-row sm:justify-center">
            <Button asChild icon={<RefreshCw className="size-4" aria-hidden />}><Link to={retryTo}>{t('res.retry')}</Link></Button>
            <Button asChild variant="ghost" icon={<LifeBuoy className="size-4" aria-hidden />}><Link to="/contact">{t('res.contact')}</Link></Button>
          </div>
          <p className="mt-6 text-xs text-muted">{t('res.help')}</p>
        </Card>
      )}
    </div>
  );
}

function Success({ order }: { order: Order }) {
  const t = useT();
  const receipt = useMutation({
    mutationFn: () => api(contracts.commerce.invoice, { params: { id: order.id } }),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Card className="overflow-hidden">
      <div className="flex flex-col items-center px-6 pb-6 pt-8 text-center">
        <span className={cn('grid size-16 place-items-center rounded-full bg-success text-white shadow-e2', 'motion-safe:animate-[ss-pop_320ms_cubic-bezier(.2,.9,.3,1.3)_both]')} aria-hidden>
          <Check className="size-8" strokeWidth={3} />
        </span>
        <h1 className="mt-5 text-2xl font-bold">{t('res.success')}</h1>
        <p className="mt-1.5 text-muted">{t('res.successBody')}</p>
        <Button asChild size="lg" className="mt-6 w-full sm:w-auto"><Link to={order.firstLessonPath ?? '/my-courses'}>{t('co.start')}</Link></Button>
      </div>
      <div className="border-t border-border bg-surface-2 px-6 py-4">
        <ul className="flex flex-col gap-1.5">
          {order.items.map((i) => (
            <li key={i.id} className="flex justify-between gap-3 text-sm">
              <span className="latin min-w-0 truncate">{i.title}</span>
              <span className="shrink-0 tnum">{formatTaka(i.price - i.discount, t.locale)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-3 flex items-center justify-between border-t border-border pt-3 text-sm">
          <span className="text-muted">
            {order.invoiceNumber ? t('res.invoice', { n: order.invoiceNumber }) : t('res.paid')}
            {order.paidAt && ` · ${formatDate(order.paidAt, t.locale)}`}
          </span>
          <span className="font-bold tnum">{formatTaka(order.total, t.locale)}</span>
        </div>
        {order.total > 0 && (
          <Button variant="secondary" size="sm" className="mt-4" loading={receipt.isPending} disabled={!order.invoiceNumber} onClick={() => receipt.mutate()} icon={<Download className="size-4" aria-hidden />}>
            {t('res.receipt')}
          </Button>
        )}
      </div>
    </Card>
  );
}

