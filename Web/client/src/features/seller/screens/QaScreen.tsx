import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useInfiniteQuery, useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query';
import type { InferResponse } from '@shikkhasathi/shared';
import { Avatar, Badge, Button, Card, Chip, EmptyState, ErrorState, Illustration, Skeleton, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { cn } from '@/lib/cn';
import { relativeTime, useT } from '@/lib/i18n';
import { BadgeCheck, CheckCircle2, ExternalLink, formatDuration, SendHorizontal } from '../parts';
import { errorText, LoadMore, PageHead, pageClass } from '../ops/kit';

type Page = InferResponse<typeof contracts.sellerLearning.questions>;
type Q = Page['items'][number];
type Tab = 'open' | 'answered' | 'all';
const TABS: Tab[] = ['open', 'answered', 'all'];

export default function SellerQaScreen() {
  const t = useT();
  const [tab, setTab] = useState<Tab>('open');
  const list = useInfiniteQuery({
    queryKey: ['seller', 'questions', tab],
    queryFn: ({ pageParam }) => api(contracts.sellerLearning.questions, { query: { status: tab, cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className={pageClass}>
      <PageHead title={t('seller.nav.qa')} subtitle={t('ops.qa.subtitle')} />
      <div role="tablist" aria-label={t('ops.qa.filter')} className="-mx-4 mt-5 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-none">
        {TABS.map((x) => (
          <Chip key={x} role="tab" aria-selected={tab === x} selected={tab === x} onClick={() => setTab(x)} className="h-11">
            {t(`ops.qa.tab.${x}`)}
          </Chip>
        ))}
      </div>

      <div className="mt-4 max-w-3xl">
        {list.isLoading ? (
          <div className="flex flex-col gap-3" aria-busy>
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-40 rounded-card" />
            ))}
          </div>
        ) : list.isError ? (
          <Card>
            <ErrorState onRetry={() => void list.refetch()} />
          </Card>
        ) : items.length === 0 ? (
          <Card>
            <EmptyState illustration={<Illustration kind={tab === 'open' ? 'done' : 'empty'} />} title={t(`ops.qa.empty.${tab}`)} body={t(`ops.qa.emptyBody.${tab}`)} />
          </Card>
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {items.map((q) => (
                <QuestionCard key={q.id} q={q} tab={tab} />
              ))}
            </ul>
            {list.hasNextPage && (
              <Card className="mt-3 overflow-hidden">
                <LoadMore hasMore loading={list.isFetchingNextPage} onMore={() => void list.fetchNextPage()} label={t('ops.loadMore')} />
              </Card>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function QuestionCard({ q, tab }: { q: Q; tab: Tab }) {
  const t = useT();
  const qc = useQueryClient();
  const [reply, setReply] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const send = useMutation({
    mutationFn: (body: string) => api(contracts.learning.answer, { params: { id: q.id }, body: { body } }),
    onSuccess: (updated) => {
      setReply('');
      toast.success(t('ops.qa.sent'));
      // keep the card in place (now answered) so the teacher sees the reply; the open tab refreshes next visit
      qc.setQueryData<InfiniteData<Page>>(['seller', 'questions', tab], (d) => (d ? { ...d, pages: d.pages.map((p) => ({ ...p, items: p.items.map((x) => (x.id === q.id ? { ...x, ...updated, courseId: x.courseId, courseTitle: x.courseTitle } : x)) })) } : d));
      for (const other of TABS) if (other !== tab) void qc.invalidateQueries({ queryKey: ['seller', 'questions', other] });
    },
    onError: (e) => setErr(errorText(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const body = reply.trim();
    if (body.length < 2) return setErr(t('ops.qa.errShort'));
    setErr(null);
    send.mutate(body);
  };
  const lessonLink = `/learn/${q.courseId}/${q.lessonId}?tab=qa`;
  const answered = q.status === 'answered';

  return (
    <li className={cn('surface-card overflow-hidden', !answered && 'border-l-[3px] border-l-warning')}>
      <div className="p-4 sm:p-5">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
          <span className="latin font-medium text-text">{q.courseTitle}</span>
          {q.lessonTitle && (
            <>
              <span aria-hidden>·</span>
              <Link to={lessonLink} className="latin inline-flex min-h-6 items-center gap-1 text-primary-fg hover:underline">
                {q.lessonTitle}
                {q.timestampSec != null && q.timestampSec > 0 && <span className="tnum"> @ {formatDuration(q.timestampSec)}</span>}
                <ExternalLink className="size-3" aria-hidden />
              </Link>
            </>
          )}
        </div>
        <div className="mt-3 flex gap-3">
          <Avatar name={q.authorName} size={36} />
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-semibold">{q.authorName}</span>
              <span className="text-xs text-muted">{relativeTime(q.createdAt, t.locale)}</span>
              {answered ? (
                <Badge tone="success" icon={<CheckCircle2 className="size-3.5" aria-hidden />}>
                  {t('ops.qa.answered')}
                </Badge>
              ) : (
                <Badge tone="warning">{t('ops.qa.waiting')}</Badge>
              )}
            </p>
            <p className="latin mt-1 whitespace-pre-line text-[15px] leading-relaxed">{q.body}</p>
          </div>
        </div>

        {q.answers.length > 0 && (
          <ul className="mt-4 flex flex-col gap-3 border-l-2 border-border pl-4 sm:ml-12">
            {q.answers.map((a) => (
              <li key={a.id}>
                <p className="flex flex-wrap items-center gap-1.5 text-sm">
                  <span className="font-semibold">{a.authorName}</span>
                  {a.isSeller && (
                    <Badge tone="primary" icon={<BadgeCheck className="size-3.5" aria-hidden />}>
                      {t('ops.qa.teacher')}
                    </Badge>
                  )}
                  <span className="text-xs text-muted">{relativeTime(a.createdAt, t.locale)}</span>
                </p>
                <p className="latin mt-0.5 whitespace-pre-line text-sm leading-relaxed">{a.body}</p>
              </li>
            ))}
          </ul>
        )}
      </div>

      <form onSubmit={submit} className="flex flex-col gap-2 border-t border-border bg-surface-2/60 p-3 sm:flex-row sm:items-end sm:p-4">
        <label className="min-w-0 flex-1">
          <span className="sr-only">{t('ops.qa.replyTo', { name: q.authorName })}</span>
          <Textarea value={reply} onChange={(e) => setReply(e.target.value)} maxLength={5000} rows={2} className="min-h-12 bg-surface" placeholder={answered ? t('ops.qa.addMore') : t('ops.qa.replyPh')} aria-invalid={err ? true : undefined} />
          {err && (
            <span role="alert" className="mt-1 block text-[13px] text-danger-fg">
              {err}
            </span>
          )}
        </label>
        <Button type="submit" loading={send.isPending} icon={<SendHorizontal className="size-4" aria-hidden />}>
          {t('ops.qa.send')}
        </Button>
      </form>
    </li>
  );
}
