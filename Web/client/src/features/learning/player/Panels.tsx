import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Clock,
  ExternalLink,
  FileText,
  MessageCircleQuestion,
  NotebookPen,
  Pencil,
  Star,
  Trash2,
} from 'lucide-react';
import type { PlayerOutline } from '@shikkhasathi/shared';
import { Avatar, Badge, Button, EmptyState, ErrorState, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { fmtTime } from './time';

const errMsg = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

function TimeChip({ sec, onSeek }: { sec: number; onSeek?: (s: number) => void }) {
  const t = useT();
  const label = localDigits(fmtTime(sec), t.locale);
  return onSeek ? (
    <button
      type="button"
      onClick={() => onSeek(sec)}
      className="inline-flex items-center gap-1 rounded-full bg-primary-soft px-2 py-0.5 text-xs font-semibold text-primary-fg tnum hover:brightness-95"
    >
      <Clock className="size-3" aria-hidden />
      {label}
    </button>
  ) : (
    <span className="inline-flex items-center gap-1 text-xs text-muted tnum">
      <Clock className="size-3" aria-hidden />
      {label}
    </span>
  );
}

// ---------------------------------------------------------------- notes

export function NotesPanel({
  lessonId,
  currentTime,
  onSeek,
}: {
  lessonId: string;
  currentTime: () => number | null;
  onSeek: (s: number) => void;
}) {
  const t = useT();
  const qc = useQueryClient();
  const [body, setBody] = useState('');
  const [stamp, setStamp] = useState(true);
  const [editing, setEditing] = useState<{ id: string; body: string } | null>(null);
  const key = ['notes', { lessonId }];
  const q = useQuery({
    queryKey: key,
    queryFn: () => api(contracts.learning.notes, { query: { lessonId, limit: 100 } }),
  });
  const now = currentTime();
  const add = useMutation({
    mutationFn: () =>
      api(contracts.learning.createNote, {
        body: { lessonId, body, timestampSec: stamp && now !== null ? Math.floor(now) : null },
      }),
    onSuccess: () => {
      setBody('');
      void qc.invalidateQueries({ queryKey: ['notes'] });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const save = useMutation({
    mutationFn: (n: { id: string; body: string }) =>
      api(contracts.learning.updateNote, { params: { id: n.id }, body: { body: n.body } }),
    onSuccess: () => {
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ['notes'] });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const del = useMutation({
    mutationFn: (id: string) => api(contracts.learning.deleteNote, { params: { id } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['notes'] }),
  });
  return (
    <div>
      <form
        className="rounded-card border border-border bg-surface p-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (body.trim()) add.mutate();
        }}
      >
        <label htmlFor="note-body" className="sr-only">
          {t('nt.add')}
        </label>
        <Textarea
          id="note-body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('nt.add')}
          rows={2}
          className="min-h-0 resize-y border-0 px-1 focus:ring-0"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
              e.preventDefault();
              if (body.trim()) add.mutate();
            }
          }}
        />
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          {now !== null ? (
            <label className="flex cursor-pointer items-center gap-2 text-sm text-muted">
              <input
                type="checkbox"
                checked={stamp}
                onChange={(e) => setStamp(e.target.checked)}
                className="size-4 accent-[var(--color-primary)]"
              />
              {t('nt.attach', { time: localDigits(fmtTime(now), t.locale) })}
            </label>
          ) : (
            <span />
          )}
          <Button type="submit" size="sm" loading={add.isPending} disabled={!body.trim()}>
            {t('nt.save')}
          </Button>
        </div>
      </form>
      <div className="mt-4">
        {q.isLoading ? (
          <div className="flex flex-col gap-2">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-16 rounded-card" />
            ))}
          </div>
        ) : q.isError ? (
          <ErrorState compact onRetry={() => void q.refetch()} />
        ) : !q.data?.items.length ? (
          <EmptyState
            compact
            illustration={<NotebookPen className="size-8 text-muted" strokeWidth={1.5} aria-hidden />}
            title={t('nt.empty')}
            body={t('nt.emptyBody')}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {[...q.data.items]
              .sort((a, b) => (a.timestampSec ?? 1e9) - (b.timestampSec ?? 1e9))
              .map((n) => (
                <li key={n.id} className="group rounded-card border border-border bg-surface p-3">
                  <div className="flex items-start gap-3">
                    {n.timestampSec !== null && <TimeChip sec={n.timestampSec} onSeek={onSeek} />}
                    {editing?.id === n.id ? (
                      <div className="min-w-0 flex-1">
                        <Textarea
                          value={editing.body}
                          onChange={(e) => setEditing({ id: n.id, body: e.target.value })}
                          rows={2}
                          aria-label={t('nt.edit')}
                        />
                        <div className="mt-2 flex gap-2">
                          <Button
                            size="sm"
                            loading={save.isPending}
                            onClick={() => save.mutate(editing)}
                            disabled={!editing.body.trim()}
                          >
                            {t('nt.save')}
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>
                            {t('common.cancel')}
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <p className="min-w-0 flex-1 whitespace-pre-wrap break-words text-[15px]">{n.body}</p>
                    )}
                    {editing?.id !== n.id && (
                      <div className="flex shrink-0 gap-0.5 opacity-100 sm:opacity-0 sm:transition-opacity sm:group-focus-within:opacity-100 sm:group-hover:opacity-100">
                        <button
                          type="button"
                          className="grid size-8 place-items-center rounded-full text-muted hover:bg-surface-2 hover:text-text"
                          onClick={() => setEditing({ id: n.id, body: n.body })}
                          aria-label={t('nt.edit')}
                        >
                          <Pencil className="size-4" aria-hidden />
                        </button>
                        <button
                          type="button"
                          className="grid size-8 place-items-center rounded-full text-muted hover:bg-danger-soft hover:text-danger-fg"
                          onClick={() => del.mutate(n.id)}
                          aria-label={t('nt.delete')}
                        >
                          <Trash2 className="size-4" aria-hidden />
                        </button>
                      </div>
                    )}
                  </div>
                </li>
              ))}
          </ul>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Q&A

export function QaPanel({
  lessonId,
  isOwner,
  currentTime,
  onSeek,
}: {
  lessonId: string;
  isOwner: boolean;
  currentTime: () => number | null;
  onSeek: (s: number) => void;
}) {
  const t = useT();
  const qc = useQueryClient();
  const [body, setBody] = useState('');
  const [reply, setReply] = useState<Record<string, string>>({});
  const key = ['questions', lessonId];
  const q = useInfiniteQuery({
    queryKey: key,
    queryFn: ({ pageParam }) =>
      api(contracts.learning.questions, {
        params: { id: lessonId },
        query: { limit: 20, cursor: pageParam },
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (l) => l.nextCursor ?? undefined,
  });
  const ask = useMutation({
    mutationFn: () => {
      const now = currentTime();
      return api(contracts.learning.ask, {
        params: { id: lessonId },
        body: { body, timestampSec: now !== null ? Math.floor(now) : null },
      });
    },
    onSuccess: () => {
      setBody('');
      toast.success(t('qa.sent'));
      void qc.invalidateQueries({ queryKey: key });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const answer = useMutation({
    mutationFn: (id: string) =>
      api(contracts.learning.answer, { params: { id }, body: { body: reply[id] ?? '' } }),
    onSuccess: (_r, id) => {
      setReply((r) => ({ ...r, [id]: '' }));
      void qc.invalidateQueries({ queryKey: key });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div>
      {!isOwner && (
        <form
          className="rounded-card border border-border bg-surface p-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (body.trim().length >= 5) ask.mutate();
          }}
        >
          <label htmlFor="qa-body" className="mb-1 block text-sm font-semibold">
            {t('pl.doubt')}
          </label>
          <Textarea
            id="qa-body"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder={t('qa.placeholder')}
            rows={2}
          />
          <div className="mt-2 flex justify-end">
            <Button type="submit" size="sm" loading={ask.isPending} disabled={body.trim().length < 5}>
              {t('qa.ask')}
            </Button>
          </div>
        </form>
      )}
      <div className="mt-4">
        {q.isLoading ? (
          <div className="flex flex-col gap-2">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-24 rounded-card" />
            ))}
          </div>
        ) : q.isError ? (
          <ErrorState compact onRetry={() => void q.refetch()} />
        ) : !items.length ? (
          <EmptyState
            compact
            illustration={
              <MessageCircleQuestion className="size-8 text-muted" strokeWidth={1.5} aria-hidden />
            }
            title={t('qa.empty')}
            body={isOwner ? undefined : t('qa.emptyBody')}
          />
        ) : (
          <ul className="flex flex-col gap-3">
            {items.map((x) => (
              <li key={x.id} className="rounded-card border border-border bg-surface p-4">
                <div className="flex items-start gap-3">
                  <Avatar name={x.authorName} size={32} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                      <span className="font-semibold">{x.mine ? t('qa.you') : x.authorName}</span>
                      <span className="text-muted">{relativeTime(x.createdAt, t.locale)}</span>
                      {x.timestampSec !== null && <TimeChip sec={x.timestampSec} onSeek={onSeek} />}
                      <Badge tone={x.status === 'answered' ? 'success' : 'neutral'} className="ml-auto">
                        {x.status === 'answered' ? t('qa.answered') : t('qa.waiting')}
                      </Badge>
                    </div>
                    <p className="mt-1.5 whitespace-pre-wrap break-words">{x.body}</p>
                    {x.answers.length > 0 && (
                      <ul className="mt-3 flex flex-col gap-2 border-l-2 border-border pl-3">
                        {x.answers.map((a) => (
                          <li
                            key={a.id}
                            className={cn('rounded-control p-2.5', a.isSeller && 'bg-primary-soft/60')}
                          >
                            <p className="flex flex-wrap items-center gap-2 text-sm">
                              <span className="font-semibold">{a.authorName}</span>
                              {a.isSeller && <Badge tone="primary">{t('qa.teacher')}</Badge>}
                              <span className="text-muted">{relativeTime(a.createdAt, t.locale)}</span>
                            </p>
                            <p className="mt-1 whitespace-pre-wrap break-words text-[15px]">{a.body}</p>
                          </li>
                        ))}
                      </ul>
                    )}
                    <form
                      className="mt-3 flex gap-2"
                      onSubmit={(e) => {
                        e.preventDefault();
                        if ((reply[x.id] ?? '').trim().length >= 2) answer.mutate(x.id);
                      }}
                    >
                      <label htmlFor={`r-${x.id}`} className="sr-only">
                        {t('qa.answer')}
                      </label>
                      <input
                        id={`r-${x.id}`}
                        value={reply[x.id] ?? ''}
                        onChange={(e) => setReply((r) => ({ ...r, [x.id]: e.target.value }))}
                        placeholder={t('qa.answer')}
                        className="h-10 min-w-0 flex-1 rounded-control border border-border bg-surface px-3 text-sm focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15"
                      />
                      <Button
                        type="submit"
                        size="sm"
                        variant={isOwner ? 'primary' : 'secondary'}
                        loading={answer.isPending && answer.variables === x.id}
                        disabled={(reply[x.id] ?? '').trim().length < 2}
                      >
                        {t('qa.reply')}
                      </Button>
                    </form>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        {q.hasNextPage && (
          <div className="mt-3 flex justify-center">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => void q.fetchNextPage()}
              loading={q.isFetchingNextPage}
            >
              {t('notes.more')}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- resources & review

export function ResourcesPanel({ resources }: { resources: PlayerOutline['resources'] }) {
  const t = useT();
  const open = useMutation({
    mutationFn: (id: string) => api(contracts.learning.document, { params: { id } }),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  if (!resources.length)
    return (
      <EmptyState
        compact
        illustration={<FileText className="size-8 text-muted" strokeWidth={1.5} aria-hidden />}
        title={t('pl.resourcesEmpty')}
      />
    );
  return (
    <ul className="divide-y divide-border rounded-card border border-border bg-surface">
      {resources.map((r) => (
        <li key={r.id}>
          <button
            type="button"
            onClick={() => open.mutate(r.id)}
            disabled={open.isPending && open.variables === r.id}
            className="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-surface-2 disabled:opacity-60"
          >
            <span className="grid size-10 shrink-0 place-items-center rounded-control bg-surface-2 text-muted">
              <FileText className="size-5" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span className="latin block truncate font-medium">{r.title}</span>
              <span className="block text-xs text-muted">
                {t(`pl.res.${r.kind}`)}
                {r.year ? ` · ${localDigits(r.year, t.locale)}` : ''}
              </span>
            </span>
            <ExternalLink className="size-4 shrink-0 text-muted" aria-hidden />
          </button>
        </li>
      ))}
    </ul>
  );
}

export function ReviewCard({ courseId, mine }: { courseId: string; mine: PlayerOutline['myReview'] }) {
  const t = useT();
  const qc = useQueryClient();
  const [rating, setRating] = useState(mine?.rating ?? 0);
  const [hover, setHover] = useState(0);
  const [body, setBody] = useState(mine?.body ?? '');
  const m = useMutation({
    mutationFn: () =>
      api(contracts.learning.review, {
        params: { courseId },
        body: { rating, body: body.trim() || undefined },
      }),
    onSuccess: () => {
      toast.success(t('rv.thanks'));
      void qc.invalidateQueries({ queryKey: ['outline', courseId] });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  return (
    <section className="rounded-card border border-border bg-surface p-4" aria-labelledby="rv-title">
      <h3 id="rv-title" className="font-semibold">
        {mine ? t('rv.yours') : t('rv.title')}
      </h3>
      {!mine && <p className="mt-0.5 text-sm text-muted">{t('rv.body')}</p>}
      <div
        className="mt-3 flex gap-1"
        role="radiogroup"
        aria-label={t('rv.title')}
        onMouseLeave={() => setHover(0)}
      >
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={rating === n}
            aria-label={t('rv.star', { n: localDigits(n, t.locale) })}
            onMouseEnter={() => setHover(n)}
            onClick={() => setRating(n)}
            className="grid size-10 place-items-center rounded-full hover:bg-warning-soft"
          >
            <Star
              className={cn(
                'size-6',
                (hover || rating) >= n ? 'fill-warning text-warning' : 'text-border-strong',
              )}
              aria-hidden
            />
          </button>
        ))}
      </div>
      <Textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={t('rv.placeholder')}
        rows={2}
        className="mt-2"
        aria-label={t('rv.placeholder')}
      />
      <Button className="mt-3" size="sm" disabled={!rating} loading={m.isPending} onClick={() => m.mutate()}>
        {mine ? t('rv.update') : t('rv.submit')}
      </Button>
    </section>
  );
}
