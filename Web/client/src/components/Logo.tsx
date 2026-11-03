import { cn } from '@/lib/cn';
import { useT } from '@/lib/i18n';

/** ShikkhaSathi mark: graduation cap over an open book (from the brand logo), drawn as SVG. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={cn('size-9 shrink-0', className)} aria-hidden>
      <defs>
        <linearGradient id="ss-cap" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#4F46E5" />
          <stop offset="1" stopColor="#7C3AED" />
        </linearGradient>
      </defs>
      <path d="M20 5 4 12l16 7 16-7z" fill="url(#ss-cap)" />
      <path d="M11 15.2v5.3c0 1.6 4 3.5 9 3.5s9-1.9 9-3.5v-5.3L20 19z" fill="#2E1F6B" />
      <circle cx="20" cy="20.6" r="2.4" fill="#fff" />
      <path d="M8.5 13.4v6.8" stroke="#F59E0B" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M4.5 25c5-1.6 10.5-.8 15.5 3.4 5-4.2 10.5-5 15.5-3.4l-1.6 8.6c-4.6-1.2-9.4-.4-13.9 2.6-4.5-3-9.3-3.8-13.9-2.6z" fill="#6366F1" />
      <path d="M20 28.4v7.8" stroke="#fff" strokeWidth="1.2" opacity=".7" />
      <path d="M33 5.5 34.6 3M35.6 8.4l2.6-1.2" stroke="#F59E0B" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

export function Logo({ className, compact }: { className?: string; compact?: boolean }) {
  const t = useT();
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark />
      {!compact && (
        <span className="flex flex-col leading-none">
          <span className="text-[19px] font-bold tracking-tight text-text">{t('brand.name')}</span>
          {t.locale === 'bn' && <span className="latin mt-0.5 hidden text-[10.5px] font-medium tracking-[0.18em] text-muted uppercase min-[400px]:block">ShikkhaSathi</span>}
        </span>
      )}
    </span>
  );
}
