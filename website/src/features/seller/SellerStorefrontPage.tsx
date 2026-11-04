import { lazy, Suspense } from 'react';
import { Skeleton } from '@/components';

// Thin route module: the screen loads behind this boundary so its many shared chunks are listed in a
// seller chunk's preload map instead of the main entry's (keeps the 150 KB landing budget).
const Screen = lazy(() => import('./screens/StorefrontScreen'));

export default function Page() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8" aria-busy><Skeleton className="h-8 w-56" /><Skeleton className="mt-6 h-72 w-full rounded-card" /></div>}>
      <Screen />
    </Suspense>
  );
}
