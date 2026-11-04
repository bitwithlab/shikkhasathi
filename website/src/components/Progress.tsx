import { cn } from '@/lib/cn';
import { localDigits, useT } from '@/lib/i18n';

export function ProgressRing({ value, size = 48, stroke = 5, label, tone = 'success', className, showValue = true }: { value: number; size?: number; stroke?: number; label?: string; tone?: 'success' | 'primary' | 'warning' | 'onDark'; className?: string; showValue?: boolean }) {
  const t = useT();
  const v = Math.max(0, Math.min(100, Math.round(value)));
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const color = tone === 'primary' ? 'var(--ss-primary)' : tone === 'warning' ? 'var(--ss-warning)' : tone === 'onDark' ? '#fff' : 'var(--ss-success)';
  const track = tone === 'onDark' ? 'rgba(255,255,255,.25)' : 'var(--ss-surface-2)';
  return (
    <span className={cn('relative inline-flex shrink-0 items-center justify-center', className)} style={{ width: size, height: size }} role="img" aria-label={label ?? `${v}%`}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} stroke={track} strokeWidth={stroke} fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke={color} strokeWidth={stroke} fill="none" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c - (v / 100) * c} className="transition-[stroke-dashoffset] duration-500 ease-ss" />
      </svg>
      {showValue && <span className={cn('latin absolute font-semibold tnum', size >= 64 ? 'text-base' : 'text-[11px]', tone === 'onDark' && 'text-white')}>{localDigits(v, t.locale)}%</span>}
    </span>
  );
}

export function ProgressBar({ value, className, tone = 'success', label }: { value: number; className?: string; tone?: 'success' | 'primary'; label?: string }) {
  const v = Math.max(0, Math.min(100, value));
  return (
    <div className={cn('h-2 w-full overflow-hidden rounded-full bg-surface-2', className)} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(v)} aria-label={label}>
      <div className={cn('h-full rounded-full transition-[width] duration-500 ease-ss', tone === 'primary' ? 'bg-primary' : 'bg-success')} style={{ width: `${v}%` }} />
    </div>
  );
}
