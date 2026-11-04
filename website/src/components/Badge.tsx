import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger';
const tones: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-muted border-border',
  primary: 'bg-primary-soft text-primary-fg border-transparent',
  success: 'bg-success-soft text-success-fg border-transparent',
  warning: 'bg-warning-soft text-warning-fg border-transparent',
  danger: 'bg-danger-soft text-danger-fg border-transparent',
};

export function Badge({ tone = 'neutral', icon, className, children, wrap, ...rest }: HTMLAttributes<HTMLSpanElement> & { tone?: Tone; icon?: ReactNode; wrap?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold leading-5', wrap ? 'whitespace-normal' : 'whitespace-nowrap', tones[tone], className)} {...rest}>
      {icon}
      {children}
    </span>
  );
}

/** Selectable filter chip (pill). */
export function Chip({ selected, className, ...rest }: React.ButtonHTMLAttributes<HTMLButtonElement> & { selected?: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={rest.role ? undefined : selected}
      className={cn(
        'inline-flex h-9 items-center gap-1.5 rounded-full border px-3.5 text-sm font-medium whitespace-nowrap transition-colors duration-150',
        selected ? 'border-primary bg-primary text-on-primary' : 'border-border bg-surface text-text hover:border-border-strong hover:bg-surface-2',
        className,
      )}
      {...rest}
    />
  );
}
