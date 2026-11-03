import { Button } from '@/components';
import { useT } from '@/lib/i18n';

/** "Google Classroom সংযুক্ত করুন" — starts the read-only OAuth flow (wired in Phase 3). */
export function ConnectClassroomButton({ onUnavailable }: { returnTo?: string; onUnavailable?: () => void; size?: 'md' | 'lg' }) {
  const t = useT();
  return (
    <Button size="lg" block variant="secondary" onClick={onUnavailable}>
      {t('gc.connect')}
    </Button>
  );
}
