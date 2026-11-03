import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import { endpoints } from '@shikkhasathi/shared/endpoints';

/** Unread notification count for the bell + sidebar badge (polls every 60 s). */
export function useUnreadCount() {
  const authed = useAuth((s) => s.status === 'authed');
  const ep = (endpoints as unknown as Record<string, Record<string, { path: string }>>).notifications?.unreadCount as Parameters<typeof api>[0] | undefined;
  const q = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api(ep!) as Promise<{ count: number }>,
    enabled: authed && !!ep,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
  return q.data?.count ?? 0;
}
