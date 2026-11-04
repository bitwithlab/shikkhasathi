import { useState } from 'react';
import { Button, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useT } from '@/lib/i18n';

function GoogleClassroomMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <rect x="2" y="4" width="20" height="16" rx="2.5" fill="#0F9D58" />
      <rect x="4.5" y="6.5" width="15" height="11" rx="1" fill="#57BB8A" />
      <circle cx="12" cy="11" r="1.9" fill="#fff" />
      <path d="M8.6 15.4c.5-1.4 1.8-2.3 3.4-2.3s2.9.9 3.4 2.3" fill="#fff" />
      <path d="M14 20h5v-1.3h-5z" fill="#fff" />
    </svg>
  );
}

/** "Google Classroom সংযুক্ত করুন" — fetches the consent URL (read-only scopes) and redirects to Google. */
export function ConnectClassroomButton({ returnTo = '/home', label, onUnavailable, size = 'lg' }: { returnTo?: string; label?: string; onUnavailable?: () => void; size?: 'md' | 'lg' }) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const start = async () => {
    setBusy(true);
    try {
      const { url } = await api(contracts.classroom.connectUrl, { query: { returnTo } });
      window.location.assign(url);
    } catch (e) {
      setBusy(false);
      toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));
      if (e instanceof ApiError && e.code === 'INTEGRATION_DISABLED') onUnavailable?.();
    }
  };
  return (
    <Button size={size} block onClick={() => void start()} loading={busy} icon={busy ? undefined : <GoogleClassroomMark />}>
      {label ?? t('gc.connect')}
    </Button>
  );
}
