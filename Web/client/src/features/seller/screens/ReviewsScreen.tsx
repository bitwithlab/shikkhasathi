import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useInfiniteQuery, useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query';
import type { InferResponse } from '@shikkhasathi/shared';
import { Avatar, Badge, Button, Card, EmptyState, ErrorState, Illustration, Skeleton, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, localDigits, relativeTime, useT } from '@/lib/i18n';
import { BadgeCheck, MessageSquare } from '../parts';
import { errorText, LoadMore, PageHead, pageClass, Stars } from '../ops/kit';

type Page = InferResponse<typeof contracts.sellerLearning.reviews>;
type R = Page['items'][number];

export default function SellerReviewsScreen() {
  const t = useT();
  const list = useInfiniteQuery({
    queryKey: ['seller', 'reviews'],
    queryFn: ({ pageParam }) => api(contracts.sellerLearning.reviews, { query: { cursor: pageParam, limit: 20 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  const waiting = items.filter((r) => !r.sellerReply).length;

  return (
    <div className={pageClass}>
      <PageHead title={t('seller.nav.reviews')} subtitle={t('ops.rv.subtitle')} />
      {items.length > 0 && (
        <p className="mt-4 text-sm text-muted" aria-live="polite">
          {waiting ? t('ops.rv.waiting', { n: localDigits(waiting, t.locale) }) : t('ops.rv.allReplied')}
        </p>
      )}
      <div className="mt-3 max-w-3xl">
        {list.isLoading ? (
          <div className="flex flex-col gap-3" aria-busy>
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-36 rounded-card" />
            ))}
          </div>
        ) : list.isError ? (
          <Card>
            <ErrorState onRetry={() => void list.refetch()} />
          </Card>
        ) : items.length === 0 ? (
          <Card>
            <EmptyState
              illustration={<Illustration kind="empty" />}
              title={t('ops.rv.emptyTitle')}
              body={t('ops.rv.emptyBody')}
              action={
                <Button asChild variant="secondary">
                  <Link to="/teach/courses">{t('seller.nav.courses')}</Link>
                </Button>
              }
            />
          </Card>
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {items.map((r) => (
                <ReviewCard key={r.id} r={r} />
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

function ReviewCard({ r }: { r: R }) {
  const t = useT();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const reply = useMutation({
    mutationFn: (body: string) => api(contracts.sellerLearning.replyReview, { params: { id: r.id }, body: { reply: body } }),
    onSuccess: (u) => {
      qc.setQueryData<InfiniteData<Page>>(['seller', 'reviews'], (d) => (d ? { ...d, pages: d.pages.map((p) => ({ ...p, items: p.items.map((x) => (x.id === r.id ? { ...x, sellerReply: u.sellerReply, sellerRepliedAt: u.sellerRepliedAt } : x)) })) } : d));
      setOpen(false);
      toast.success(t('ops.rv.sent'));
    },
    onError: (e) => setErr(errorText(e)),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const b = text.trim();
    if (b.length < 2) return setErr(t('ops.qa.errShort'));
    setErr(null);
    reply.mutate(b);
  };

  return (
    <li className="surface-card p-4 sm:p-5">
      <p className="latin text-xs font-medium text-muted">{r.courseTitle}</p>
      <div className="mt-3 flex gap-3">
        <Avatar name={r.authorName} size={36} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="text-sm font-semibold">{r.authorName}</span>
            {r.verified && (
              <Badge tone="success" icon={<BadgeCheck className="size-3.5" aria-hidden />}>
                {t('ops.rv.verified')}
              </Badge>
            )}
            <span className="text-xs text-muted">{relativeTime(r.createdAt, t.locale)}</span>
          </div>
          <Stars value={r.rating} locale={t.locale} className="mt-1" />
          {r.body ? <p className="latin mt-1.5 whitespace-pre-line text-[15px] leading-relaxed">{r.body}</p> : <p className="mt-1.5 text-sm italic text-muted">{t('ops.rv.noText')}</p>}
        </div>
      </div>

      {r.sellerReply ? (
        <div className="mt-4 rounded-control bg-surface-2 p-3 sm:ml-12">
          <p className="flex flex-wrap items-center gap-2 text-xs font-semibold text-muted">
            <MessageSquare className="size-3.5" aria-hidden /> {t('ops.rv.yourReply')}
            {r.sellerRepliedAt && <span className="font-normal">· {formatDate(r.sellerRepliedAt, t.locale)}</span>}
          </p>
          <p className="latin mt-1 whitespace-pre-line text-sm leading-relaxed">{r.sellerReply}</p>
        </div>
      ) : open ? (
        <form onSubmit={submit} className="mt-4 flex flex-col gap-2 sm:ml-12">
          <label>
            <span className="sr-only">{t('ops.rv.replyTo', { name: r.authorName })}</span>
            <Textarea autoFocus value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} rows={3} placeholder={t('ops.rv.replyPh')} aria-invalid={err ? true : undefined} />
          </label>
          {err && (
            <p role="alert" className="text-[13px] text-danger-fg">
              {err}
            </p>
          )}
          <p className="text-xs text-muted">{t('ops.rv.once')}</p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" loading={reply.isPending}>
              {t('ops.rv.publish')}
            </Button>
          </div>
        </form>
      ) : (
        <div className="mt-3 sm:ml-12">
          <Button variant="soft" icon={<MessageSquare className="size-4" aria-hidden />} onClick={() => setOpen(true)}>
            {t('ops.rv.reply')}
          </Button>
        </div>
      )}
    </li>
  );
}
