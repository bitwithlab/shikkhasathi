import { useEffect, useRef, useState } from 'react';
import { Button, Dialog, Field, Input } from '@/components';
import { usePublicConfig } from '@/lib/config';
import { useT } from '@/lib/i18n';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (o: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: string }) => void;
          renderButton: (el: HTMLElement, o: Record<string, unknown>) => void;
        };
      };
    };
  }
}

let gisLoading: Promise<void> | null = null;
function loadGis() {
  gisLoading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://accounts.google.com/gsi/client';
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('gis'));
    document.head.appendChild(s);
  });
  return gisLoading;
}

function GoogleG() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.4h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.7z" />
      <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-2.9l-3.9-3c-1.1.7-2.5 1.2-4.1 1.2-3.1 0-5.8-2.1-6.7-5H1.3v3.1A12 12 0 0 0 12 24z" />
      <path fill="#FBBC05" d="M5.3 14.3a7.2 7.2 0 0 1 0-4.6V6.6h-4a12 12 0 0 0 0 10.8z" />
      <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.3 6.6l4 3.1c.9-2.9 3.6-4.9 6.7-4.9z" />
    </svg>
  );
}

/**
 * Google sign-in. With a configured client id: official GIS button (ID token → POST /auth/google).
 * In local dev without one: a dev dialog that issues a `fake:` token accepted only outside production.
 */
export function GoogleButton({ onCredential, disabled }: { onCredential: (idToken: string) => void; disabled?: boolean }) {
  const t = useT();
  const cfg = usePublicConfig();
  const ref = useRef<HTMLDivElement>(null);
  const [devOpen, setDevOpen] = useState(false);
  const [devEmail, setDevEmail] = useState('');
  const clientId = cfg.data?.googleClientId;

  useEffect(() => {
    if (!clientId || !ref.current) return;
    let cancelled = false;
    loadGis()
      .then(() => {
        if (cancelled || !window.google || !ref.current) return;
        window.google.accounts.id.initialize({ client_id: clientId, callback: (r) => onCredential(r.credential) });
        window.google.accounts.id.renderButton(ref.current, { theme: 'outline', size: 'large', shape: 'rectangular', width: ref.current.offsetWidth || 320, text: 'continue_with', locale: t.locale });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [clientId, onCredential, t.locale]);

  if (clientId) return <div ref={ref} className="flex h-11 w-full justify-center" />;
  if (!import.meta.env.DEV) return null;
  return (
    <>
      <Button variant="secondary" block size="lg" disabled={disabled} onClick={() => setDevOpen(true)} icon={<GoogleG />}>
        {t('auth.withGoogle')}
      </Button>
      <Dialog
        open={devOpen}
        onOpenChange={setDevOpen}
        title="Google (dev)"
        description="Google client id সেট করা নেই — ডেভ মোডে একটি fake টোকেন দিয়ে লগইন হবে।"
        footer={
          <Button
            disabled={!/^\S+@\S+\.\S+$/.test(devEmail)}
            onClick={() => {
              setDevOpen(false);
              onCredential(`fake:${devEmail}:${devEmail.split('@')[0]}`);
            }}
          >
            {t('common.next')}
          </Button>
        }
      >
        <Field label={t('auth.email')}>{(p) => <Input {...p} type="email" value={devEmail} onChange={(e) => setDevEmail(e.target.value)} placeholder="you@gmail.com" />}</Field>
      </Dialog>
    </>
  );
}
