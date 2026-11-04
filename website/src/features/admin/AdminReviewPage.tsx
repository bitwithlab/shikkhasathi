import './i18n';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, CheckCircle2, ExternalLink, FileText, Inbox, PlayCircle, Star, XCircle } from 'lucide-react';
import { Badge, Button, Card, Chip, EmptyState, ErrorState, Sheet, Skeleton, Switch, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { formatTaka, localDigits, relativeTime, useT } from '@/lib/i18n';

type Status = 'in_review' | 'changes_requested' | 'rejected' | 'published';
const KIND_TONE = { new: 'primary', revision: 'warning', spot_check: 'neutral' } as const;

export default function AdminReviewPage() {
  const t = useT();
  const [status, setStatus] = useState<Status>('in_review');
  const [open, setOpen] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['admin', 'review', status], queryFn: () => api(contracts.adminReview.queue, { query: { status, limit: 50 } }) });
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.nav.review')}</h1>
      <p className="mt-1 text-sm text-muted">{t('admin.review.subtitle')}</p>
      <div className="mt-5 flex gap-2 overflow-x-auto scrollbar-none">
        {(['in_review', 'changes_requested', 'rejected', 'published'] as const).map((s) => (
          <Chip key={s} selected={status === s} onClick={() => setStatus(s)}>{t(`courseStatus.${s}`)}</Chip>
        ))}
      </div>
      <Card className="mt-5 overflow-hidden">
        {q.isLoading ? (
          <div className="flex flex-col gap-2 p-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : !q.data?.items.length ? (
          <EmptyState illustration={<Inbox className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('admin.review.empty')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('admin.review.course')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.review.teacher')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.review.target')}</th>
                  <th className="px-3 py-2.5 text-right font-medium">{t('admin.review.price')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.review.kind')}</th>
                  <th className="px-4 py-2.5 font-medium">{t('admin.review.submitted')}</th>
                </tr>
              </thead>
              <tbody>
                {q.data.items.map((c) => (
                  <tr key={c.id} className="border-t border-border hover:bg-surface-2">
                    <td className="px-4 py-3">
                      <button type="button" onClick={() => setOpen(c.id)} className="latin text-left font-medium hover:text-primary-fg">{c.title}</button>
                      <p className="text-xs text-muted">{t('admin.review.lessons', { n: localDigits(c.lessonCount, t.locale) })}</p>
                    </td>
                    <td className="px-3 py-3">
                      <span className="inline-flex items-center gap-1">{c.sellerName}{c.sellerVerified && <BadgeCheck className="size-4 text-success" aria-label={t('course.verified')} />}</span>
                    </td>
                    <td className="latin px-3 py-3 text-muted">{c.targets.join(', ')}</td>
                    <td className="px-3 py-3 text-right tnum">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</td>
                    <td className="px-3 py-3"><Badge tone={KIND_TONE[c.kind]}>{t(`admin.review.k.${c.kind}`)}</Badge></td>
                    <td className="px-4 py-3 text-muted">{c.submittedAt ? relativeTime(c.submittedAt, t.locale) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {open && <ReviewDrawer id={open} onClose={() => setOpen(null)} />}
    </div>
  );
}

const CHECKS = ['quality', 'noGuaranteeClaims', 'targeting', 'copyright'] as const;

function ReviewDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const d = useQuery({ queryKey: ['admin', 'review', 'detail', id], queryFn: () => api(contracts.adminReview.detail, { params: { id } }) });
  const [checks, setChecks] = useState<Record<(typeof CHECKS)[number], boolean>>({ quality: false, noGuaranteeClaims: false, targeting: false, copyright: false });
  const [note, setNote] = useState('');
  const m = useMutation({
    mutationFn: (decision: 'publish' | 'changes_requested' | 'reject') => api(contracts.adminReview.decide, { params: { id }, body: { decision, note: note || undefined, checklist: checks } }),
    onSuccess: () => {
      toast.success(t('common.saved'));
      void qc.invalidateQueries({ queryKey: ['admin', 'review'] });
      onClose();
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  const c = d.data;
  const all = CHECKS.every((k) => checks[k]);
  return (
    <Sheet
      open
      side="right"
      onOpenChange={(o) => !o && onClose()}
      title={<span className="latin">{c?.title ?? '…'}</span>}
      description={c ? `${c.sellerName} · ${t(`courseStatus.${c.status}`)}` : undefined}
      footer={
        c && (
          <div className="flex w-full flex-wrap gap-2">
            <Button className="flex-1" disabled={!all} loading={m.isPending} onClick={() => m.mutate('publish')} icon={<CheckCircle2 className="size-4" aria-hidden />}>{t('admin.review.publish')}</Button>
            <Button variant="secondary" disabled={note.trim().length < 5} onClick={() => m.mutate('changes_requested')}>{t('admin.review.changes')}</Button>
            <Button variant="ghost" disabled={note.trim().length < 5} onClick={() => m.mutate('reject')} icon={<XCircle className="size-4" aria-hidden />}>{t('admin.inst.reject')}</Button>
          </div>
        )
      }
    >
      {d.isLoading ? (
        <Skeleton className="h-96" />
      ) : d.isError || !c ? (
        <ErrorState onRetry={() => void d.refetch()} />
      ) : (
        <div className="flex flex-col gap-5">
          <a href={`/c/${c.slug}`} target="_blank" rel="noreferrer noopener" className="inline-flex w-fit items-center gap-1 text-sm font-semibold text-primary-fg hover:underline">{t('admin.review.publicPage')} <ExternalLink className="size-4" aria-hidden /></a>
          {c.revision && (
            <section className="rounded-card border border-warning/40 bg-warning-soft/60 p-3">
              <h3 className="text-sm font-semibold text-warning-fg">{t('admin.review.revision')}</h3>
              <ul className="mt-2 flex flex-col gap-1 text-sm">
                {Object.entries(c.revision).map(([k, v]) => (
                  <li key={k} className="latin">
                    <span className="text-muted">{k}:</span>{' '}
                    {k === 'price' || k === 'compareAtPrice' ? (
                      <><span className="line-through">{formatTaka(Number((c as Record<string, unknown>)[k] ?? 0), t.locale)}</span> → <strong>{formatTaka(Number(v ?? 0), t.locale)}</strong></>
                    ) : k === 'targets' ? (
                      `${(v as unknown[]).length} target(s)`
                    ) : (
                      <><span className="line-through">{String((c as Record<string, unknown>)[k] ?? '')}</span> → <strong>{String(v)}</strong></>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <section>
            <h3 className="font-semibold">{t('admin.review.summary')}</h3>
            <p className="mt-1 text-sm">{c.shortDescription}</p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <Badge tone="primary">{t(`examType.${c.examType}`)}</Badge>
              <Badge>{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</Badge>
              {c.targets.map((tg) => <Badge key={tg.id} className="latin">{tg.isGeneral ? 'General' : [tg.institutionShort, tg.departmentShort, tg.courseCode, tg.semester ? `S${tg.semester}` : null].filter(Boolean).join(' · ')}</Badge>)}
            </div>
            {c.learnings.length > 0 && <ul className="mt-3 list-disc pl-5 text-sm">{c.learnings.map((l) => <li key={l}>{l}</li>)}</ul>}
          </section>
          <section>
            <h3 className="font-semibold">{t('admin.review.curriculum')}</h3>
            <ol className="mt-2 flex flex-col gap-2 text-sm">
              {c.sections.map((s) => (
                <li key={s.id} className="rounded-control border border-border p-2.5">
                  <p className="latin font-medium">{s.title}</p>
                  {s.chapters.map((ch) => (
                    <div key={ch.id} className="mt-1.5 pl-2">
                      <p className="latin text-xs font-semibold text-muted">{ch.title}</p>
                      <ul>
                        {ch.lessons.map((l) => (
                          <li key={l.id} className="latin flex items-center gap-1.5 py-0.5">
                            {l.type === 'video' ? <PlayCircle className="size-3.5 text-muted" aria-hidden /> : <FileText className="size-3.5 text-muted" aria-hidden />}
                            {l.title}
                            {l.isPreview && <Badge tone="primary">{t('admin.review.preview')}</Badge>}
                            {l.isImportant && <Star className="size-3.5 fill-warning text-warning" aria-label={t('admin.review.important')} />}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </li>
              ))}
            </ol>
          </section>
          <section aria-labelledby="chk-h">
            <h3 id="chk-h" className="font-semibold">{t('admin.review.checklist')}</h3>
            <ul className="mt-2 flex flex-col divide-y divide-border rounded-control border border-border">
              {CHECKS.map((k) => (
                <li key={k} className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
                  <span>{t(`admin.review.c.${k}`)}</span>
                  <Switch label={t(`admin.review.c.${k}`)} checked={checks[k]} onCheckedChange={(v) => setChecks((s) => ({ ...s, [k]: v }))} />
                </li>
              ))}
            </ul>
          </section>
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            {t('admin.review.note')}
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('admin.review.notePh')} />
            <span className="text-xs font-normal text-muted">{t('admin.review.noteHint')}</span>
          </label>
        </div>
      )}
    </Sheet>
  );
}
