import './i18n';
import '@/features/auth/i18n';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Check, GitMerge, X } from 'lucide-react';
import { Badge, Button, Card, Chip, Dialog, EmptyState, ErrorState, Field, Input, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { relativeTime, useT } from '@/lib/i18n';

type Status = 'pending' | 'approved' | 'rejected' | 'merged';
type Req = Awaited<ReturnType<typeof api<typeof contracts.adminCatalog.institutionRequests>>>['items'][number];

export default function AdminInstitutionsPage() {
  const t = useT();
  const qc = useQueryClient();
  const [status, setStatus] = useState<Status>('pending');
  const [approving, setApproving] = useState<Req | null>(null);
  const q = useQuery({ queryKey: ['admin', 'inst-requests', status], queryFn: () => api(contracts.adminCatalog.institutionRequests, { query: { status, limit: 50 } }) });
  const decide = useMutation({
    mutationFn: (v: { id: string; body: Parameters<typeof api<typeof contracts.adminCatalog.decideInstitutionRequest>>[1] extends infer A ? (A extends { body?: infer B } ? B : never) : never }) =>
      api(contracts.adminCatalog.decideInstitutionRequest, { params: { id: v.id }, body: v.body }),
    onSuccess: () => {
      toast.success(t('common.saved'));
      setApproving(null);
      void qc.invalidateQueries({ queryKey: ['admin', 'inst-requests'] });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.nav.institutions')}</h1>
      <p className="mt-1 text-sm text-muted">{t('admin.inst.subtitle')}</p>
      <div className="mt-5 flex gap-2 overflow-x-auto scrollbar-none">
        {(['pending', 'approved', 'merged', 'rejected'] as const).map((s) => (
          <Chip key={s} selected={status === s} onClick={() => setStatus(s)}>
            {t(`admin.inst.status.${s}`)}
          </Chip>
        ))}
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {q.isLoading ? (
          [0, 1, 2].map((i) => <Skeleton key={i} className="h-28 rounded-card" />)
        ) : q.isError ? (
          <Card><ErrorState onRetry={() => void q.refetch()} /></Card>
        ) : !q.data?.items.length ? (
          <Card><EmptyState illustration={<Building2 className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('admin.inst.empty')} /></Card>
        ) : (
          q.data.items.map((r) => (
            <Card key={r.id} className="p-4 sm:p-5">
              <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                <div className="min-w-0">
                  <p className="latin text-lg font-semibold">{r.name}</p>
                  <p className="mt-0.5 text-sm text-muted">
                    {t(`instType.${r.type}`)}
                    {r.city && ` · ${r.city}`} · {r.requestedBy?.name ?? '—'} · {relativeTime(r.createdAt, t.locale)}
                  </p>
                  {r.note && <p className="mt-2 text-sm">{r.note}</p>}
                  {r.similar.length > 0 && (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <span className="text-xs font-semibold text-warning-fg">{t('admin.inst.similar')}</span>
                      {r.similar.map((s) => (
                        <Badge key={s.id} tone="warning">{s.shortName}</Badge>
                      ))}
                    </div>
                  )}
                </div>
                {status === 'pending' && (
                  <div className="flex shrink-0 flex-wrap gap-2">
                    {r.similar[0] && (
                      <Button variant="secondary" size="sm" icon={<GitMerge className="size-4" aria-hidden />} onClick={() => decide.mutate({ id: r.id, body: { action: 'merge', institutionId: r.similar[0]!.id } })}>
                        {t('admin.inst.mergeInto', { name: r.similar[0].shortName })}
                      </Button>
                    )}
                    <Button size="sm" icon={<Check className="size-4" aria-hidden />} onClick={() => setApproving(r)}>
                      {t('admin.inst.approve')}
                    </Button>
                    <Button variant="ghost" size="sm" icon={<X className="size-4" aria-hidden />} onClick={() => decide.mutate({ id: r.id, body: { action: 'reject' } })}>
                      {t('admin.inst.reject')}
                    </Button>
                  </div>
                )}
              </div>
            </Card>
          ))
        )}
      </div>
      {approving && <ApproveDialog req={approving} onClose={() => setApproving(null)} onSubmit={(b) => decide.mutate({ id: approving.id, body: { action: 'approve', ...b } })} pending={decide.isPending} />}
    </div>
  );
}

function ApproveDialog({ req, onClose, onSubmit, pending }: { req: Req; onClose: () => void; onSubmit: (b: { name: string; shortName: string; slug: string }) => void; pending: boolean }) {
  const t = useT();
  const [name, setName] = useState(req.name);
  const [shortName, setShort] = useState(req.name.split(/\s+/).map((w) => w[0]).join('').toUpperCase().slice(0, 6));
  const [slug, setSlug] = useState(req.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={t('admin.inst.approve')} footer={<Button loading={pending} onClick={() => onSubmit({ name, shortName, slug })}>{t('admin.inst.approve')}</Button>}>
      <div className="flex flex-col gap-4">
        <Field label={t('admin.inst.name')}>{(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
        <Field label={t('admin.inst.short')}>{(p) => <Input {...p} value={shortName} onChange={(e) => setShort(e.target.value)} className="latin" />}</Field>
        <Field label="Slug" hint={`shikkhasathi.com/u/${slug}`}>{(p) => <Input {...p} value={slug} onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} className="latin" />}</Field>
      </div>
    </Dialog>
  );
}
