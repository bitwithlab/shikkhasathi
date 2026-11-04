import './i18n';
import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { ChevronRight, ScrollText } from 'lucide-react';
import { Card, Input, Select } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatTime, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { AdminHeader, ListBody, LoadMore, useDebounced } from './shared';

type Entry = Awaited<ReturnType<typeof api<typeof contracts.adminOps.audit>>>['items'][number];

const ENTITIES = ['courses', 'payments', 'refunds', 'payouts', 'users', 'seller_profiles', 'institutions', 'institution_requests', 'departments', 'programs', 'subject_masters', 'institution_subjects', 'coupons', 'bundles', 'mock_tests', 'integration_settings', 'broadcast', 'course', 'review', 'question', 'answer', 'user'] as const;

export default function AdminAuditPage() {
  const t = useT();
  const [entity, setEntity] = useState('');
  const [action, setAction] = useState('');
  const a = useDebounced(action.trim());
  const list = useInfiniteQuery({
    queryKey: ['admin', 'audit', entity, a],
    queryFn: ({ pageParam }) => api(contracts.adminOps.audit, { query: { entity: entity || undefined, action: a || undefined, cursor: pageParam, limit: 50 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.audit')} subtitle={t('admin.audit.subtitle')} />
      <div className="mt-5 grid gap-3 sm:grid-cols-[240px_minmax(0,1fr)] lg:grid-cols-[240px_320px]">
        <Select value={entity} onChange={(e) => setEntity(e.target.value)} aria-label={t('admin.audit.entity')} className="latin">
          <option value="">{t('admin.audit.allEntities')}</option>
          {ENTITIES.map((e) => <option key={e} value={e}>{e}</option>)}
        </Select>
        <Input type="search" value={action} onChange={(e) => setAction(e.target.value)} placeholder={t('admin.audit.actionPh')} aria-label={t('admin.audit.action')} className="latin" />
      </div>
      <Card className="mt-4 overflow-hidden">
        <ListBody loading={list.isLoading} error={list.isError} empty={!items.length} onRetry={() => void list.refetch()} emptyIcon={<ScrollText className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={entity || a ? t('admin.common.noMatch') : t('admin.audit.empty')} rows={8}>
          <ul className="divide-y divide-border">
            {items.map((e) => <Row key={e.id} e={e} />)}
          </ul>
          <LoadMore shown={items.length} hasMore={!!list.hasNextPage} loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} />
        </ListBody>
      </Card>
    </div>
  );
}

const hasData = (v: unknown) => v !== null && v !== undefined && !(typeof v === 'object' && Object.keys(v as object).length === 0);

function Row({ e }: { e: Entry }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const expandable = hasData(e.before) || hasData(e.after);
  const id = `audit-${e.id}`;
  const head = (
    <>
      <ChevronRight className={cn('mt-0.5 size-4 shrink-0 text-muted transition-transform duration-150', open && 'rotate-90', !expandable && 'invisible')} aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <code className="latin rounded bg-primary-soft px-1.5 py-0.5 font-mono text-xs font-semibold text-primary-fg">{e.action}</code>
          <span className="latin text-xs text-muted">{e.entity}{e.entityId && <> · <span className="font-mono">{e.entityId.slice(-12)}</span></>}</span>
        </p>
        <p className="mt-0.5 text-sm">{e.actorName ?? <span className="text-muted">{t('admin.audit.system')}</span>}{e.ip && <span className="latin text-xs text-muted"> · {e.ip}</span>}</p>
      </div>
      <time dateTime={e.createdAt} title={`${formatDate(e.createdAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' })} ${formatTime(e.createdAt, t.locale)}`} className="shrink-0 text-xs text-muted">{relativeTime(e.createdAt, t.locale)}</time>
    </>
  );
  return (
    <li>
      {expandable ? (
        <button type="button" aria-expanded={open} aria-controls={id} onClick={() => setOpen((o) => !o)} className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-2">{head}</button>
      ) : (
        <div className="flex items-start gap-3 px-4 py-3">{head}</div>
      )}
      {open && expandable && (
        <div id={id} className={cn('grid gap-3 px-4 pb-4 pl-11', hasData(e.before) && hasData(e.after) && 'md:grid-cols-2')}>
          {hasData(e.before) && <Json label={t('admin.audit.before')} v={e.before} />}
          {hasData(e.after) && <Json label={t('admin.audit.after')} v={e.after} />}
        </div>
      )}
    </li>
  );
}

function Json({ label, v }: { label: string; v: unknown }) {
  return (
    <div className="min-w-0">
      <p className="mb-1 text-xs font-medium text-muted">{label}</p>
      <pre className="latin max-h-80 overflow-auto rounded-control border border-border bg-surface-2 p-3 font-mono text-xs leading-relaxed">{hasData(v) ? JSON.stringify(v, null, 2) : '—'}</pre>
    </div>
  );
}
