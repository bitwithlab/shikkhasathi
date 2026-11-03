import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth, hasRole, homePath } from '@/lib/auth';
import { Skeleton } from '@/components';
import { ForbiddenPage } from './pages';

function BootSkeleton() {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4 p-6" aria-busy>
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-40 w-full" />
      <div className="grid gap-4 md:grid-cols-2">
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
      </div>
    </div>
  );
}

export function RequireAuth({ children, role, allowOnboarding }: { children: ReactNode; role?: 'seller' | 'admin'; allowOnboarding?: boolean }) {
  const { status, user } = useAuth();
  const loc = useLocation();
  if (status === 'loading') return <BootSkeleton />;
  if (status === 'anon') return <Navigate to={`/auth?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />;
  if (role && !hasRole(user, role)) return <ForbiddenPage />;
  if (!allowOnboarding && !role && user && !user.onboardingCompleted && !hasRole(user, 'admin')) return <Navigate to="/onboarding" replace />;
  return <>{children}</>;
}

/** Logged-in users visiting `/` or `/auth` go to their home. */
export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const loc = useLocation();
  if (status === 'authed') {
    const next = new URLSearchParams(loc.search).get('next');
    return <Navigate to={next && next.startsWith('/') && !next.startsWith('//') ? next : homePath(user)} replace />;
  }
  return <>{children}</>;
}
