import { Link } from 'react-router';
import { Button, EmptyState, Illustration } from '@/components';
import { useT } from '@/lib/i18n';

export function NotFoundPage() {
  const t = useT();
  return (
    <main id="main" className="mx-auto flex min-h-[60dvh] max-w-lg items-center justify-center px-4">
      <EmptyState
        illustration={<Illustration kind="search" className="h-28 w-32" />}
        title={<span className="text-xl">{t('state.notFoundTitle')}</span>}
        body={t('state.notFoundBody')}
        action={
          <Button asChild>
            <Link to="/">{t('state.goHome')}</Link>
          </Button>
        }
      />
    </main>
  );
}

export function ForbiddenPage() {
  const t = useT();
  return (
    <main id="main" className="mx-auto flex min-h-[60dvh] max-w-lg items-center justify-center px-4">
      <EmptyState
        illustration={<Illustration kind="error" className="h-28 w-32" />}
        title={t('state.forbiddenTitle')}
        action={
          <Button asChild variant="secondary">
            <Link to="/home">{t('state.goHome')}</Link>
          </Button>
        }
      />
    </main>
  );
}

export function RouteError() {
  const t = useT();
  return (
    <main id="main" className="mx-auto flex min-h-[60dvh] max-w-lg items-center justify-center px-4">
      <EmptyState
        illustration={<Illustration kind="error" className="h-28 w-32" />}
        title={t('state.errorTitle')}
        body={t('state.errorBody')}
        action={<Button onClick={() => window.location.reload()}>{t('common.retry')}</Button>}
      />
    </main>
  );
}
