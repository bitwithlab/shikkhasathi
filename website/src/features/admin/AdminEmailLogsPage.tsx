import './i18n';
import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { AlertCircle, Mail, Search } from 'lucide-react';
import { Badge, Card, Input, type Tone } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatTime, localDigits, relativeTime, useT } from '@/lib/i18n';
import { AdminHeader, ListBody, LoadMore, StatusTabs, useDebounced } from './shared';

const STATUSES = ['all', 'queued', 'sent', 'failed'] as const;
type S = (typeof STATUSES)[number];
const TONE: Record<string, Tone> = { queued: 'warning', sent: 'success', failed: 'danger' };

export default function AdminEmailLogsPage() {
  const t = useT();
  const [status, setStatus] = useState<S>('all');
  const [search, setSearch] = useState('');
  const q = useDebounced(search.trim());
  const list = useInfiniteQuery({
    queryKey: ['admin', 'email-logs', status, q],
    queryFn: ({ pageParam }) => api(contracts.adminOps.emailLogs, { query: { status: status === 'all' ? undefined : status, q: q || undefined, cursor: pageParam, limit: 50 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.emailLogs')} subtitle={t('admin.email.subtitle')} />
      <div className="mt-5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <StatusTabs value={status} options={STATUSES} onChange={setStatus} label={t('admin.common.status')} labelKey={(s) => `admin.email.s.${s}`} />
        <div className="relative md:w-80">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <Input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('admin.email.searchPh')} aria-label={t('common.search')} className="pl-10" />
        </div>
      </div>
      <Card className="mt-4 overflow-hidden">
        <ListBody loading={list.isLoading} error={list.isError} empty={!items.length} onRetry={() => void list.refetch()} emptyIcon={<Mail className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={q || status !== 'all' ? t('admin.common.noMatch') : t('admin.email.empty')} rows={8}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('admin.email.to')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.email.template')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.common.status')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.email.provider')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.email.error')}</th>
                  <th className="px-4 py-2.5 font-medium">{t('admin.email.when')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((e) => (
                  <tr key={e.id} className="border-t border-border align-top hover:bg-surface-2">
                    <td className="max-w-[240px] px-4 py-3">
                      <p className="latin truncate font-medium">{e.toEmail}</p>
                      {e.subject && <p className="truncate text-xs text-muted">{e.subject}</p>}
                    </td>
                    <td className="px-3 py-3"><code className="latin rounded bg-surface-2 px-1.5 py-0.5 font-mono text-xs">{e.template}</code></td>
                    <td className="px-3 py-3">
                      <Badge tone={TONE[e.status] ?? 'neutral'}>{STATUSES.includes(e.status as S) ? t(`admin.email.s.${e.status}`) : e.status}</Badge>
                      {e.attempts > 1 && <p className="mt-1 text-xs text-muted">{t('admin.email.attempts', { n: localDigits(e.attempts, t.locale) })}</p>}
                    </td>
                    <td className="latin px-3 py-3 text-muted">{e.provider ?? '—'}</td>
                    <td className="max-w-[280px] px-3 py-3">
                      {e.error ? <p className="latin flex items-start gap-1.5 break-words text-xs text-danger-fg"><AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden />{e.error}</p> : <span className="text-muted">—</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-muted">
                      <time dateTime={e.createdAt} title={`${formatDate(e.createdAt, t.locale, { day: 'numeric', month: 'short', year: 'numeric' })} ${formatTime(e.createdAt, t.locale)}`}>{relativeTime(e.createdAt, t.locale)}</time>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <LoadMore shown={items.length} hasMore={!!list.hasNextPage} loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} />
        </ListBody>
      </Card>
    </div>
  );
}
