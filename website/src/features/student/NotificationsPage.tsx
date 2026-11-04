import './i18n';
import { Link } from 'react-router';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Bell, BellRing, ClipboardList, GraduationCap, Megaphone, Receipt, Star, Timer } from 'lucide-react';
import type { NotificationItem } from '@/api';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { relativeTime, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

function iconFor(type: string) {
  if (type.startsWith('classroom.due') || type === 'classroom.overdue') return BellRing;
  if (type === 'classroom.graded') return Star;
  if (type === 'classroom.announcement' || type === 'admin.broadcast') return Megaphone;
  if (type.startsWith('classroom.')) return ClipboardList;
  if (type.startsWith('exam.')) return Timer;
  if (type.startsWith('order.')) return Receipt;
  if (type.startsWith('seller.')) return GraduationCap;
  return Bell;
}

export default function NotificationsPage() {
  const t = useT();
  const qc = useQueryClient();
  const q = useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    queryFn: ({ pageParam }) => api(contracts.notifications.list, { query: { limit: 30, cursor: pageParam } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
  const read = useMutation({
    mutationFn: (b: { ids?: string[]; all?: boolean }) => api(contracts.notifications.read, { body: b }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['notifications'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
  const items: NotificationItem[] = q.data?.pages.flatMap((p) => p.items) ?? [];
  const unread = items.filter((i) => !i.read).length;
  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <header className="mb-5 flex items-end justify-between gap-3">
        <h1 className="text-2xl font-bold">{t('nav.notifications')}</h1>
        {unread > 0 && (
          <Button variant="ghost" size="sm" onClick={() => read.mutate({ all: true })} loading={read.isPending}>
            {t('home.markAllRead')}
          </Button>
        )}
      </header>
      {q.isLoading ? (
        <div className="flex flex-col gap-2">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-[72px]" />)}</div>
      ) : q.isError ? (
        <ErrorState onRetry={() => void q.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState illustration={<Bell className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('notif.empty')} body={t('notif.emptyBody')} action={<Button asChild variant="secondary" size="sm"><Link to="/profile#notifications">{t('notif.settings')}</Link></Button>} />
      ) : (
        <ul className="surface-card divide-y divide-border overflow-hidden">
          {items.map((n) => {
            const Icon = iconFor(n.type);
            const urgent = n.type === 'classroom.due_2h' || n.type === 'classroom.overdue';
            const content = (
              <>
                <span className={cn('relative flex size-10 shrink-0 items-center justify-center rounded-full', urgent ? 'bg-danger-soft text-danger-fg' : 'bg-primary-soft text-primary-fg')}>
                  <Icon className="size-5" strokeWidth={1.75} aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className={cn('block text-[15px] leading-snug', !n.read && 'font-semibold')}>{n.title}</span>
                  <span className="latin mt-0.5 block text-sm text-muted">{n.body}</span>
                  <span className="mt-1 block text-xs text-muted">{relativeTime(n.createdAt, t.locale)}</span>
                </span>
                {!n.read && <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-primary" aria-label={t('gc.unread')} />}
              </>
            );
            return (
              <li key={n.id} className={cn(!n.read && 'bg-primary-soft/40')}>
                {n.link ? (
                  <Link to={n.link} onClick={() => !n.read && read.mutate({ ids: [n.id] })} className="flex gap-3 px-4 py-3.5 hover:bg-surface-2">
                    {content}
                  </Link>
                ) : (
                  <div className="flex gap-3 px-4 py-3.5">{content}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {q.hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button variant="secondary" onClick={() => void q.fetchNextPage()} loading={q.isFetchingNextPage}>{t('notif.more')}</Button>
        </div>
      )}
    </div>
  );
}
