import { Languages, Monitor, Moon, Sun } from 'lucide-react';
import { useI18nStore, useT } from '@/lib/i18n';
import { useThemeStore, type ThemePref } from '@/lib/theme';
import { cn } from '@/lib/cn';
import { useAuth } from '@/lib/auth';
import { api, contracts } from '@/lib/api';

export function LangToggle({ className }: { className?: string }) {
  const t = useT();
  const setLocale = useI18nStore((s) => s.setLocale);
  const authed = useAuth((s) => s.status === 'authed');
  const next = t.locale === 'bn' ? 'en' : 'bn';
  return (
    <button
      type="button"
      onClick={() => {
        setLocale(next);
        if (authed) void api(contracts.auth.updateMe, { body: { locale: next } }).catch(() => undefined);
      }}
      className={cn('inline-flex h-10 items-center gap-1.5 rounded-control px-2.5 text-sm font-medium text-muted hover:bg-surface-2 hover:text-text', className)}
      aria-label={t('common.language')}
      lang={next}
    >
      <Languages className="size-4" strokeWidth={1.75} aria-hidden />
      <span>{t('common.switchLang')}</span>
    </button>
  );
}

const THEMES: { v: ThemePref; icon: typeof Sun; key: string }[] = [
  { v: 'light', icon: Sun, key: 'common.theme.light' },
  { v: 'dark', icon: Moon, key: 'common.theme.dark' },
  { v: 'system', icon: Monitor, key: 'common.theme.system' },
];

export function ThemeSwitcher({ className }: { className?: string }) {
  const t = useT();
  const { theme, setTheme } = useThemeStore();
  const authed = useAuth((s) => s.status === 'authed');
  return (
    <div role="radiogroup" aria-label={t('common.theme')} className={cn('inline-flex rounded-control border border-border bg-surface-2 p-0.5', className)}>
      {THEMES.map(({ v, icon: Icon, key }) => (
        <button
          key={v}
          type="button"
          role="radio"
          aria-checked={theme === v}
          title={t(key)}
          onClick={() => {
            setTheme(v);
            if (authed) void api(contracts.auth.updateMe, { body: { theme: v } }).catch(() => undefined);
          }}
          className={cn('inline-flex size-9 items-center justify-center rounded-[10px] text-muted transition-colors', theme === v && 'bg-surface text-text shadow-e1')}
        >
          <Icon className="size-4" strokeWidth={1.75} aria-hidden />
          <span className="sr-only">{t(key)}</span>
        </button>
      ))}
    </div>
  );
}
