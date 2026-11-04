import './i18n';
import '@/features/admin/i18n';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowBigUp, Hammer, Megaphone, Plus } from 'lucide-react';
import { Badge, Button, Card, Chip, EmptyState, ErrorState, Field, Input, Select, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth, hasRole } from '@/lib/auth';
import { localDigits, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

type Status = 'all' | 'open' | 'teacher_working' | 'published';
const STATUS_TONE = { open: 'primary', teacher_working: 'warning', published: 'success', closed: 'neutral' } as const;

export default function RequestsPage() {
  const t = useT();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const [params] = useSearchParams();
  const [status, setStatus] = useState<Status>('all');
  const [formOpen, setFormOpen] = useState(params.get('new') === '1');
  const q = useInfiniteQuery({
    queryKey: ['requests', status],
    queryFn: ({ pageParam }) => api(contracts.requests.list, { query: { status, limit: 20, cursor: pageParam } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (l) => l.nextCursor ?? undefined,
  });
  const vote = useMutation({ mutationFn: (id: string) => api(contracts.requests.vote, { params: { id } }), onSuccess: () => void qc.invalidateQueries({ queryKey: ['requests'] }), onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')) });
  const claim = useMutation({ mutationFn: (id: string) => api(contracts.requests.claim, { params: { id }, body: {} }), onSuccess: () => { toast.success(t('req.claimed')); void qc.invalidateQueries({ queryKey: ['requests'] }); }, onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')) });
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  const isSeller = hasRole(user, 'seller');
  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">{t('nav.requests')}</h1>
          <p className="text-sm text-muted">{t('req.subtitle')}</p>
        </div>
        {!formOpen && <Button onClick={() => setFormOpen(true)} icon={<Plus className="size-4" aria-hidden />}>{t('req.new')}</Button>}
      </header>
      {formOpen && <RequestForm onDone={() => setFormOpen(false)} prefill={{ subject: params.get('subject') ?? '', institutionId: params.get('institutionId') ?? '', examType: params.get('examType') ?? 'midterm' }} />}
      <div className="mt-6 flex gap-2 overflow-x-auto scrollbar-none">
        {(['all', 'open', 'teacher_working', 'published'] as const).map((s) => <Chip key={s} selected={status === s} onClick={() => setStatus(s)}>{t(`req.status.${s}`)}</Chip>)}
      </div>
      <div className="mt-4">
        {q.isLoading ? (
          <div className="flex flex-col gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-24 rounded-card" />)}</div>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : !items.length ? (
          <EmptyState illustration={<Megaphone className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('req.empty')} body={t('req.emptyBody')} action={<Button onClick={() => setFormOpen(true)}>{t('req.new')}</Button>} />
        ) : (
          <ul className="flex flex-col gap-3">
            {items.map((r) => (
              <li key={r.id}>
                <Card className="flex gap-4 p-4">
                  <button type="button" onClick={() => vote.mutate(r.id)} aria-pressed={r.voted} aria-label={t('req.voteA', { n: r.voteCount })} className={cn('flex w-14 shrink-0 flex-col items-center justify-center rounded-control border py-2 transition-colors', r.voted ? 'border-primary bg-primary-soft text-primary-fg' : 'border-border hover:border-border-strong')}>
                    <ArrowBigUp className={cn('size-6', r.voted && 'fill-current')} aria-hidden />
                    <span className="text-sm font-bold tnum">{localDigits(r.voteCount, t.locale)}</span>
                  </button>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone={STATUS_TONE[r.status]}>{t(`req.status.${r.status}`)}</Badge>
                      <Badge>{t(`examType.${r.examType}`)}</Badge>
                      {r.institutionShort && <Badge className="latin">{r.institutionShort}</Badge>}
                    </div>
                    <p className="latin mt-1.5 font-semibold">{r.courseCode ? `${r.courseCode} · ` : ''}{r.subjectName}</p>
                    {r.note && <p className="mt-0.5 text-sm text-muted">{r.note}</p>}
                    <p className="mt-1.5 text-xs text-muted">
                      {relativeTime(r.createdAt, t.locale)}
                      {r.claimedByName && ` · ${t('req.claimedBy', { name: r.claimedByName })}`}
                    </p>
                    {r.linkedCourseSlug && <Link to={`/c/${r.linkedCourseSlug}`} className="mt-2 inline-block text-sm font-semibold text-primary-fg hover:underline">{t('req.viewCourse')}</Link>}
                  </div>
                  {isSeller && r.status === 'open' && <Button variant="secondary" size="sm" className="shrink-0 self-center" loading={claim.isPending && claim.variables === r.id} onClick={() => claim.mutate(r.id)} icon={<Hammer className="size-4" aria-hidden />}>{t('req.claim')}</Button>}
                </Card>
              </li>
            ))}
          </ul>
        )}
        {q.hasNextPage && <div className="mt-4 flex justify-center"><Button variant="secondary" onClick={() => void q.fetchNextPage()} loading={q.isFetchingNextPage}>{t('notif.more')}</Button></div>}
      </div>
    </div>
  );
}

function RequestForm({ onDone, prefill }: { onDone: () => void; prefill: { subject: string; institutionId: string; examType: string } }) {
  const t = useT();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const [v, setV] = useState({ institutionId: prefill.institutionId || user?.academic.institutionId || '', subjectId: '', subjectName: prefill.subject, courseCode: '', examType: prefill.examType as 'midterm', note: '' });
  const insts = useQuery({ queryKey: ['institutions', ''], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), staleTime: 600_000 });
  const subs = useQuery({ queryKey: ['subjects', v.institutionId], queryFn: () => api(contracts.catalog.subjects, { query: v.institutionId ? { institutionId: v.institutionId } : {} }), staleTime: 600_000 });
  const m = useMutation({
    mutationFn: () => api(contracts.requests.create, { body: { institutionId: v.institutionId || null, subjectId: v.subjectId || null, subjectName: v.subjectName, courseCode: v.courseCode || null, examType: v.examType, note: v.note || undefined } }),
    onSuccess: (r) => {
      toast.success(r.duplicate ? t('req.duplicate') : t('req.created'));
      void qc.invalidateQueries({ queryKey: ['requests'] });
      onDone();
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Card className="mt-5 p-5">
      <h2 className="text-lg font-semibold">{t('req.new')}</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Field label={t('explore.f.university')}>
          {(p) => (
            <Select {...p} value={v.institutionId} onChange={(e) => setV({ ...v, institutionId: e.target.value, subjectId: '' })}>
              <option value="">{t('req.anyUni')}</option>
              {insts.data?.items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
            </Select>
          )}
        </Field>
        <Field label={t('explore.f.subject')}>
          {(p) => (
            <Select {...p} value={v.subjectId} onChange={(e) => { const s = subs.data?.items.find((x) => x.id === e.target.value); setV({ ...v, subjectId: e.target.value, subjectName: s?.name ?? v.subjectName, courseCode: (s && 'courseCode' in s ? s.courseCode : null) ?? v.courseCode }); }}>
              <option value="">{t('req.otherSubject')}</option>
              {subs.data?.items.map((s) => <option key={s.id} value={s.id}>{'courseCode' in s && s.courseCode ? `${s.courseCode} · ` : ''}{s.name}</option>)}
            </Select>
          )}
        </Field>
        {!v.subjectId && <Field label={t('req.subjectName')}>{(p) => <Input {...p} value={v.subjectName} onChange={(e) => setV({ ...v, subjectName: e.target.value })} placeholder="Operating Systems" />}</Field>}
        <Field label={t('admin.cat.code')}>{(p) => <Input {...p} value={v.courseCode} onChange={(e) => setV({ ...v, courseCode: e.target.value })} placeholder="CSE-3201" className="latin" />}</Field>
        <Field label={t('explore.f.examType')}>
          {(p) => (
            <Select {...p} value={v.examType} onChange={(e) => setV({ ...v, examType: e.target.value as 'midterm' })}>
              {(['midterm', 'final', 'quiz', 'lab'] as const).map((x) => <option key={x} value={x}>{t(`examType.${x}`)}</option>)}
            </Select>
          )}
        </Field>
        <Field label={t('req.note')} className="sm:col-span-2">{(p) => <Textarea {...p} value={v.note} onChange={(e) => setV({ ...v, note: e.target.value })} placeholder={t('req.notePh')} />}</Field>
      </div>
      <div className="mt-4 flex gap-2">
        <Button onClick={() => m.mutate()} loading={m.isPending} disabled={v.subjectName.trim().length < 2}>{t('req.submit')}</Button>
        <Button variant="ghost" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </Card>
  );
}
