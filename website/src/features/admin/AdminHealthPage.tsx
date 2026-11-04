import './i18n';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, Database, Server, XCircle } from 'lucide-react';
import { Badge, Card, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatNumber, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export default function AdminHealthPage() {
  const t = useT();
  const q = useQuery({ queryKey: ['admin', 'health'], queryFn: () => api(contracts.health.system), refetchInterval: 15_000 });
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.health.title')}</h1>
      <p className="mt-1 text-sm text-muted">{t('admin.settings.dbNote')}</p>
      {q.isLoading ? (
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-36" />
          ))}
        </div>
      ) : q.isError || !q.data ? (
        <Card className="mt-6">
          <ErrorState onRetry={() => void q.refetch()} />
        </Card>
      ) : (
        <>
          <div className="mt-6 grid gap-4 md:grid-cols-3">
            {q.data.databases.map((d) => (
              <Card key={d.name} className="p-5">
                <div className="flex items-center justify-between">
                  <p className="flex items-center gap-2 font-semibold">
                    <Database className="size-[18px] text-primary" strokeWidth={1.75} aria-hidden />
                    {t(`admin.health.db.${d.name}`)}
                  </p>
                  <Status ok={d.connected} />
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-y-2 text-sm">
                  <dt className="text-muted">{t('admin.health.latency')}</dt>
                  <dd className="latin tnum text-right">{d.latencyMs === null ? '—' : `${localDigits(d.latencyMs, t.locale)} ms`}</dd>
                  <dt className="text-muted">{t('admin.health.migration')}</dt>
                  <dd className="latin truncate text-right text-xs" title={d.migration ?? ''}>{d.migration ?? '—'}</dd>
                  <dt className="text-muted">{t('admin.health.rls')}</dt>
                  <dd className="text-right">{d.rlsOk === null ? '—' : <Status ok={d.rlsOk} small labels={['admin.health.rlsOn', 'admin.health.rlsOff']} />}</dd>
                </dl>
              </Card>
            ))}
            <Card className="p-5">
              <div className="flex items-center justify-between">
                <p className="flex items-center gap-2 font-semibold">
                  <Server className="size-[18px] text-primary" strokeWidth={1.75} aria-hidden />
                  {t('admin.health.redis')}
                </p>
                <Status ok={q.data.redis.connected} />
              </div>
              <dl className="mt-4 grid grid-cols-2 text-sm">
                <dt className="text-muted">{t('admin.health.latency')}</dt>
                <dd className="latin tnum text-right">{q.data.redis.latencyMs === null ? '—' : `${localDigits(q.data.redis.latencyMs, t.locale)} ms`}</dd>
              </dl>
              <div className="mt-4 flex flex-wrap gap-1.5">
                {q.data.integrations.map((i) => (
                  <Badge key={i.provider} tone={i.mode === 'configured' ? 'success' : i.mode === 'env' ? 'primary' : i.mode === 'fake' ? 'warning' : 'danger'}>
                    {i.provider}: {t(`admin.settings.mode.${i.mode}`)}
                  </Badge>
                ))}
              </div>
            </Card>
          </div>
          <Card className="mt-6 overflow-hidden">
            <h2 className="border-b border-border px-5 py-3 font-semibold">{t('admin.health.queues')}</h2>
            {q.data.queues.length === 0 ? (
              <p className="px-5 py-6 text-sm text-muted">{t('admin.health.inlineJobs')}</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-surface-2 text-left text-xs text-muted">
                    <tr>
                      <th className="px-5 py-2 font-medium">Queue</th>
                      {(['waiting', 'active', 'failed', 'delayed'] as const).map((k) => (
                        <th key={k} className="px-3 py-2 text-right font-medium">
                          {t(`admin.health.queue.${k}`)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {q.data.queues.map((r) => (
                      <tr key={r.name} className="border-t border-border">
                        <td className="latin px-5 py-2 font-medium">{r.name}</td>
                        {(['waiting', 'active', 'failed', 'delayed'] as const).map((k) => (
                          <td key={k} className={cn('latin tnum px-3 py-2 text-right', k === 'failed' && r.failed > 0 && 'font-semibold text-danger-fg')}>
                            {formatNumber(r[k], t.locale)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
}

function Status({ ok, small, labels = ['admin.health.connected', 'admin.health.disconnected'] }: { ok: boolean; small?: boolean; labels?: [string, string] }) {
  const t = useT();
  return (
    <span className={cn('inline-flex items-center gap-1 font-medium', small ? 'text-xs' : 'text-sm', ok ? 'text-success-fg' : 'text-danger-fg')}>
      {ok ? <CheckCircle2 className="size-4" aria-hidden /> : <XCircle className="size-4" aria-hidden />}
      {t(ok ? labels[0] : labels[1])}
    </span>
  );
}
