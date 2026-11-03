import type { ReactNode } from 'react';
import { ErrorState, Skeleton } from '@/components';
import { useClassroomStatus } from './useClassroom';
import { ConnectCard } from './parts';

/** Renders children only when Classroom is connected & usable; otherwise the connect / reconnect card. */
export function ClassroomGate({ children, returnTo }: { children: ReactNode; returnTo: string }) {
  const s = useClassroomStatus();
  if (s.isLoading) return <Skeleton className="h-40 rounded-card" />;
  if (s.isError || !s.data) return <ErrorState onRetry={() => void s.refetch()} />;
  if (!s.data.connected || s.data.status === 'needs_reauth' || s.data.status === 'workspace_blocked') return <ConnectCard status={s.data.connected ? s.data : null} returnTo={returnTo} />;
  return <>{children}</>;
}
