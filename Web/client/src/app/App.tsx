import { useEffect } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import { Provider as Tooltip } from '@radix-ui/react-tooltip';
import { queryClient } from '@/lib/query';
import { useAuth } from '@/lib/auth';
import { Toaster } from '@/components';
import { router } from './router';

export function App() {
  const bootstrap = useAuth((s) => s.bootstrap);
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);
  return (
    <QueryClientProvider client={queryClient}>
      <Tooltip>
        <RouterProvider router={router} />
      </Tooltip>
      <Toaster />
    </QueryClientProvider>
  );
}
