import { useAuth } from '@/lib/auth';
import { useT } from '@/lib/i18n';

export default function HomePage() {
  const t = useT();
  const user = useAuth((s) => s.user);
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('home.greeting', { name: user?.name ?? '' })}</h1>
    </div>
  );
}
