import { EmptyState, Illustration } from './States';
import { useT } from '@/lib/i18n';

/** Temporary page for a route whose phase isn't built yet (tracked in docs/SCREEN_MAP.md). */
export function PagePlaceholder({ titleKey }: { titleKey: string }) {
  const t = useT();
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8">
      <h1 className="text-2xl font-bold">{t(titleKey)}</h1>
      <EmptyState illustration={<Illustration kind="courses" className="h-28 w-32" />} title={t('state.comingSoon')} body={t('state.comingSoonBody')} />
    </div>
  );
}
