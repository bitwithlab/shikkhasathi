import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export function Card({ className, interactive, ...rest }: HTMLAttributes<HTMLDivElement> & { interactive?: boolean }) {
  return (
    <div
      className={cn(
        'surface-card',
        interactive && 'transition-[transform,box-shadow,border-color] duration-200 ease-ss hover:-translate-y-0.5 hover:shadow-e2 hover:border-border-strong',
        className,
      )}
      {...rest}
    />
  );
}

export function SectionHeader({ title, action, subtitle, id }: { title: React.ReactNode; action?: React.ReactNode; subtitle?: React.ReactNode; id?: string }) {
  return (
    <div className="mb-3 flex items-end justify-between gap-3">
      <div className="min-w-0">
        <h2 id={id} className="text-lg font-semibold text-text">
          {title}
        </h2>
        {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
