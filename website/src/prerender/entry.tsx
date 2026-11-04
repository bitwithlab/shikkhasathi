import { renderToString } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { PublicShell } from '@/app/shells/PublicShell';
import LandingPage from '@/features/marketplace/LandingPage';

/**
 * Build-time render of the landing page (shell + hero + static sections) so first paint doesn't wait for JS.
 * Data sections render their skeletons; the client (createRoot) replaces this markup on boot.
 */
export function renderLanding() {
  const router = createMemoryRouter([{ path: '/', element: <PublicShell />, children: [{ index: true, element: <LandingPage /> }] }], { initialEntries: ['/'] });
  const qc = new QueryClient({ defaultOptions: { queries: { enabled: false } } });
  return renderToString(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}
