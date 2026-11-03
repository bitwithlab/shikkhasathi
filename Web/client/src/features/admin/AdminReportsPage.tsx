import './i18n';
import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { REPORT_STATUSES, type Report } from '@shikkhasathi/shared';
import { Eye, EyeOff, ExternalLink, Flag, Search, Users, XCircle } from 'lucide-react';
import { Badge, Button, Card, Dialog, Field, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, relativeTime, useT } from '@/lib/i18n';
import { AdminHeader, CardSkeletons, CardState, LoadMore, StatusTabs, errMsg } from './shared';

type Status = (typeof REPORT_STATUSES)[number];
type Action = 'hide' | 'restore' | 'dismiss' | 'reviewing';
const TARGET_TONE = { course: 'primary', review: 'neutral', question: 'neutral', answer: 'neutral', user: 'warning' } as const;
const REASONS = new Set(['spam', 'abuse', 'copyright', 'misleading', 'guarantee_claim', 'other']);

export default function AdminReportsPage() {
  const t = useT();
  const [status, setStatus] = useState<Status>('open');
  const [acting, setActing] = useState<{ r: Report; action: Action } | null>(null);
  const list = useInfiniteQuery({
    queryKey: ['admin', 'reports', status],
    queryFn: ({ pageParam }) => api(contracts.adminOps.reports, { query: { status, cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.reports')} subtitle={t('admin.reports.subtitle')} />
      <div className="mt-5">
        <StatusTabs value={status} options={REPORT_STATUSES} onChange={setStatus} label={t('admin.common.status')} labelKey={(s) => `admin.reportStatus.${s}`} />
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {list.isLoading ? (
          <CardSkeletons h="h-36" />
        ) : list.isError || !items.length ? (
          <CardState error={list.isError} onRetry={() => void list.refetch()} emptyIcon={<Flag className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={t(`admin.reports.empty.${status}`)} />
        ) : (
          <>
            {items.map((r) => <ReportCard key={r.id} r={r} onAct={(action) => setActing({ r, action })} />)}
            {list.hasNextPage && <Card><LoadMore shown={items.length} hasMore loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} /></Card>}
          </>
        )}
      </div>
      {acting && <ActDialog {...acting} onClose={() => setActing(null)} />}
    </div>
  );
}

function ReportCard({ r, onAct }: { r: Report; onAct: (a: Action) => void }) {
  const t = useT();
  const closed = r.status === 'resolved' || r.status === 'dismissed';
  return (
    <Card className="p-4 sm:p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-start">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={TARGET_TONE[r.targetType]}>{t(`admin.reports.target.${r.targetType}`)}</Badge>
            <Badge tone={r.reason === 'copyright' || r.reason === 'abuse' ? 'danger' : 'warning'}>{REASONS.has(r.reason) ? t(`admin.reports.reason.${r.reason}`) : r.reason}</Badge>
            {r.sameTargetCount > 1 && <Badge tone="danger" icon={<Users className="size-3.5" aria-hidden />}>{t('admin.reports.same', { n: localDigits(r.sameTargetCount, t.locale) })}</Badge>}
            {r.targetHidden && <Badge icon={<EyeOff className="size-3.5" aria-hidden />}>{t('admin.reports.hidden')}</Badge>}
          </div>
          <div className="mt-3 rounded-control border border-border bg-surface-2 p-3">
            <p className="line-clamp-3 text-sm">{r.targetPreview}</p>
            {r.targetLink && (
              <a href={r.targetLink} target="_blank" rel="noreferrer noopener" className="mt-1.5 inline-flex items-center gap-1 text-sm font-medium text-primary-fg hover:underline">
                {t('admin.reports.open')} <ExternalLink className="size-3.5" aria-hidden />
              </a>
            )}
          </div>
          {r.details && <p className="mt-3 text-sm"><span className="text-muted">{t('admin.reports.details')}:</span> {r.details}</p>}
          <p className="mt-2 text-xs text-muted">{t('admin.reports.by', { name: r.reporterName, when: relativeTime(r.createdAt, t.locale) })}</p>
          {r.resolution && <p className="mt-1 text-sm"><span className="text-muted">{t('admin.reports.resolution')}:</span> {r.resolution}</p>}
        </div>
        <div className="flex shrink-0 flex-wrap gap-2 md:w-44 md:flex-col">
          {r.targetHidden ? (
            <Button size="sm" variant="secondary" icon={<Eye className="size-4" aria-hidden />} onClick={() => onAct('restore')}>{t('admin.reports.a.restore')}</Button>
          ) : (
            <Button size="sm" variant="danger" icon={<EyeOff className="size-4" aria-hidden />} onClick={() => onAct('hide')}>{t('admin.reports.a.hide')}</Button>
          )}
          {!closed && <Button size="sm" variant="ghost" icon={<XCircle className="size-4" aria-hidden />} onClick={() => onAct('dismiss')}>{t('admin.reports.a.dismiss')}</Button>}
          {r.status === 'open' && <Button size="sm" variant="ghost" icon={<Search className="size-4" aria-hidden />} onClick={() => onAct('reviewing')}>{t('admin.reports.a.reviewing')}</Button>}
        </div>
      </div>
    </Card>
  );
}

function ActDialog({ r, action, onClose }: { r: Report; action: Action; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const m = useMutation({
    mutationFn: () => api(contracts.adminOps.resolveReport, { params: { id: r.id }, body: { action, resolution: note.trim() || undefined } }),
    onSuccess: () => {
      toast.success(t('common.saved'));
      void qc.invalidateQueries({ queryKey: ['admin', 'reports'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'analytics'] });
      onClose();
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={t(`admin.reports.confirm.${action}`)}
      description={t(`admin.reports.target.${r.targetType}`)}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant={action === 'hide' ? 'danger' : 'primary'} loading={m.isPending} onClick={() => m.mutate()}>{t(`admin.reports.a.${action}`)}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="line-clamp-4 rounded-control bg-surface-2 p-3 text-sm">{r.targetPreview}</p>
        {action !== 'reviewing' && r.sameTargetCount > 1 && <p className="text-sm text-muted">{t('admin.reports.allSame', { n: localDigits(r.sameTargetCount, t.locale) })}</p>}
        <Field label={`${t('admin.reports.resolution')} (${t('common.optional')})`}>{(p) => <Textarea {...p} value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} placeholder={t('admin.reports.resolutionPh')} />}</Field>
      </div>
    </Dialog>
  );
}
