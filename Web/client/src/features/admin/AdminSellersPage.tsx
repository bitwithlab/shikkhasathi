import './i18n';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, ExternalLink, FileCheck2, Mail, X } from 'lucide-react';
import { Avatar, Badge, Button, Card, Chip, Dialog, EmptyState, ErrorState, Field, Input, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { relativeTime, useT } from '@/lib/i18n';

type Status = 'pending' | 'verified' | 'rejected';
type Item = Awaited<ReturnType<typeof api<typeof contracts.adminSellers.list>>>['items'][number];

export default function AdminSellersPage() {
  const t = useT();
  const qc = useQueryClient();
  const [status, setStatus] = useState<Status>('pending');
  const [deciding, setDeciding] = useState<{ item: Item; decision: 'verified' | 'rejected' } | null>(null);
  const q = useQuery({ queryKey: ['admin', 'sellers', status], queryFn: () => api(contracts.adminSellers.list, { query: { status, limit: 50 } }) });
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.nav.sellers')}</h1>
      <p className="mt-1 text-sm text-muted">{t('admin.sellers.subtitle')}</p>
      <div className="mt-5 flex gap-2">
        {(['pending', 'verified', 'rejected'] as const).map((s) => (
          <Chip key={s} selected={status === s} onClick={() => setStatus(s)}>{t(`admin.sellers.status.${s}`)}</Chip>
        ))}
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {q.isLoading ? (
          [0, 1].map((i) => <Skeleton key={i} className="h-40 rounded-card" />)
        ) : q.isError ? (
          <Card><ErrorState onRetry={() => void q.refetch()} /></Card>
        ) : !q.data?.items.length ? (
          <Card><EmptyState illustration={<FileCheck2 className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('admin.sellers.empty')} /></Card>
        ) : (
          q.data.items.map((s) => {
            const ev = (s.evidence ?? {}) as Record<string, string | boolean | null>;
            return (
              <Card key={s.userId} className="p-4 sm:p-5">
                <div className="flex flex-col gap-4 md:flex-row md:items-start">
                  <Avatar name={s.displayName} size={48} />
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 text-lg font-semibold">
                      {s.displayName}
                      {s.verificationStatus === 'verified' && <BadgeCheck className="size-5 text-success" aria-label={t('course.verified')} />}
                    </p>
                    <p className="text-sm text-muted">{[s.headline, s.institutionName].filter(Boolean).join(' · ')}</p>
                    <p className="latin mt-1 text-sm text-muted">{[s.email, s.phone].filter(Boolean).join(' · ')} · shikkhasathi.com/t/{s.slug}</p>
                    <dl className="mt-3 grid gap-2 rounded-control bg-surface-2 p-3 text-sm sm:grid-cols-2">
                      <div>
                        <dt className="text-xs text-muted">{t('admin.sellers.method')}</dt>
                        <dd className="font-medium">{s.verificationMethod ? t(`admin.sellers.m.${s.verificationMethod}`) : '—'}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted">{t('admin.sellers.evidence')}</dt>
                        <dd className="latin min-w-0 break-words">
                          {s.verificationMethod === 'university_email' && (
                            <span className="inline-flex items-center gap-1.5">
                              <Mail className="size-4" aria-hidden /> {String(ev.universityEmail ?? '—')} {s.universityEmailVerified ? <Badge tone="success">{t('admin.sellers.emailOk')}</Badge> : <Badge tone="warning">{t('admin.sellers.emailNo')}</Badge>}
                            </span>
                          )}
                          {s.verificationMethod === 'linkedin' && ev.linkedinUrl && <a className="inline-flex items-center gap-1 text-primary-fg hover:underline" href={String(ev.linkedinUrl)} target="_blank" rel="noreferrer noopener">LinkedIn <ExternalLink className="size-3.5" aria-hidden /></a>}
                          {s.verificationMethod === 'id_card' && (s.idCardUrl ? <a className="inline-flex items-center gap-1 text-primary-fg hover:underline" href={s.idCardUrl} target="_blank" rel="noreferrer noopener">{t('admin.sellers.viewId')} <ExternalLink className="size-3.5" aria-hidden /></a> : '—')}
                          {s.verificationMethod === 'reference' && `${String(ev.referenceName ?? '')} · ${String(ev.referenceContact ?? '')}`}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted">{t('admin.sellers.payout')}</dt>
                        <dd className="latin">{s.payoutMethod ?? '—'} {s.payoutAccountMasked}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted">{t('admin.sellers.applied')}</dt>
                        <dd>{relativeTime(s.appliedAt, t.locale)} · {t('admin.sellers.courses', { n: s.courseCount })}</dd>
                      </div>
                    </dl>
                    {s.bio && <p className="mt-3 text-sm">{s.bio}</p>}
                    {s.verificationNote && <p className="mt-2 text-sm text-danger-fg">{s.verificationNote}</p>}
                  </div>
                  <div className="flex shrink-0 gap-2 md:flex-col">
                    {s.verificationStatus !== 'verified' && <Button size="sm" icon={<BadgeCheck className="size-4" aria-hidden />} onClick={() => setDeciding({ item: s, decision: 'verified' })}>{t('admin.sellers.verify')}</Button>}
                    {s.verificationStatus !== 'rejected' && <Button size="sm" variant="ghost" icon={<X className="size-4" aria-hidden />} onClick={() => setDeciding({ item: s, decision: 'rejected' })}>{t('admin.inst.reject')}</Button>}
                  </div>
                </div>
              </Card>
            );
          })
        )}
      </div>
      {deciding && <DecideDialog {...deciding} onClose={() => setDeciding(null)} onDone={() => { setDeciding(null); void qc.invalidateQueries({ queryKey: ['admin', 'sellers'] }); }} />}
    </div>
  );
}

function DecideDialog({ item, decision, onClose, onDone }: { item: Item; decision: 'verified' | 'rejected'; onClose: () => void; onDone: () => void }) {
  const t = useT();
  const [note, setNote] = useState('');
  const [commission, setCommission] = useState(item.commissionRate !== null ? String(item.commissionRate) : '');
  const m = useMutation({
    mutationFn: () => api(contracts.adminSellers.decide, { params: { id: item.userId }, body: { decision, note: note || undefined, commissionRate: commission ? Number(commission) : undefined } }),
    onSuccess: () => { toast.success(t('common.saved')); onDone(); },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={decision === 'verified' ? t('admin.sellers.verify') : t('admin.inst.reject')} description={item.displayName} footer={<Button variant={decision === 'verified' ? 'primary' : 'danger'} loading={m.isPending} disabled={decision === 'rejected' && note.trim().length < 5} onClick={() => m.mutate()}>{t('common.confirm')}</Button>}>
      <div className="flex flex-col gap-4">
        {decision === 'verified' && <Field label={t('admin.sellers.commission')} hint={t('admin.sellers.commissionHint')}>{(p) => <Input {...p} inputMode="numeric" value={commission} onChange={(e) => setCommission(e.target.value.replace(/\D/g, '').slice(0, 2))} placeholder="20" className="latin" />}</Field>}
        <Field label={t('admin.sellers.note')} hint={decision === 'rejected' ? t('admin.sellers.noteRequired') : undefined}>{(p) => <Textarea {...p} value={note} onChange={(e) => setNote(e.target.value)} />}</Field>
      </div>
    </Dialog>
  );
}
