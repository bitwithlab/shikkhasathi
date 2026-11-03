import './i18n';
import { Link } from 'react-router';
import { HeartPulse, Settings } from 'lucide-react';
import { Card } from '@/components';
import { useT } from '@/lib/i18n';

export default function AdminDashboardPage() {
  const t = useT();
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.nav.dashboard')}</h1>
      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Card interactive className="p-5">
          <Link to="/admin/settings" className="flex items-center gap-3 font-semibold">
            <Settings className="size-5 text-primary" aria-hidden /> {t('admin.settings.title')}
          </Link>
        </Card>
        <Card interactive className="p-5">
          <Link to="/admin/health" className="flex items-center gap-3 font-semibold">
            <HeartPulse className="size-5 text-primary" aria-hidden /> {t('admin.health.title')}
          </Link>
        </Card>
      </div>
    </div>
  );
}
