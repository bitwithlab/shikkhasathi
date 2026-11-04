import * as D from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { useT } from '@/lib/i18n';

interface OverlayProps {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
}

/** Centered dialog on desktop. */
export function Dialog({ open, onOpenChange, title, description, children, footer, className }: OverlayProps) {
  const t = useT();
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-50 bg-slate-950/40 data-[state=open]:animate-fade-up" />
        <D.Content className={cn('fixed left-1/2 top-1/2 z-50 max-h-[90dvh] w-[calc(100vw-32px)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-card border border-border bg-surface p-6 shadow-e3 focus:outline-none', className)}>
          <div className="mb-4 flex items-start justify-between gap-4">
            <div>
              <D.Title className="text-lg font-semibold">{title}</D.Title>
              {description ? <D.Description className="mt-1 text-sm text-muted">{description}</D.Description> : <D.Description className="sr-only">{title}</D.Description>}
            </div>
            <D.Close className="-m-2 rounded-control p-2 text-muted hover:bg-surface-2 hover:text-text" aria-label={t('common.close')}>
              <X className="size-5" aria-hidden />
            </D.Close>
          </div>
          {children}
          {footer && <div className="mt-6 flex justify-end gap-2">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

/** Bottom sheet on mobile, right drawer on desktop (side="right"). */
export function Sheet({ open, onOpenChange, title, description, children, footer, className, side = 'bottom' }: OverlayProps & { side?: 'bottom' | 'right' | 'left' }) {
  const t = useT();
  const pos =
    side === 'right'
      ? 'inset-y-0 right-0 h-dvh w-full max-w-xl border-l rounded-none'
      : side === 'left'
        ? 'inset-y-0 left-0 h-dvh w-[86vw] max-w-xs border-r rounded-none'
        : 'inset-x-0 bottom-0 max-h-[88dvh] rounded-t-[20px] border-t lg:left-1/2 lg:bottom-auto lg:top-1/2 lg:max-w-lg lg:-translate-x-1/2 lg:-translate-y-1/2 lg:rounded-card lg:border';
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-50 bg-slate-950/40" />
        <D.Content className={cn('fixed z-50 flex flex-col border-border bg-surface shadow-e3 focus:outline-none animate-fade-up', pos, className)}>
          {side === 'bottom' && <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-border lg:hidden" aria-hidden />}
          <div className="flex items-start justify-between gap-4 px-5 pb-2 pt-4">
            <div className="min-w-0">
              <D.Title className="text-lg font-semibold">{title}</D.Title>
              {description ? <D.Description className="mt-0.5 text-sm text-muted">{description}</D.Description> : <D.Description className="sr-only">{title}</D.Description>}
            </div>
            <D.Close className="-m-2 rounded-control p-2 text-muted hover:bg-surface-2 hover:text-text" aria-label={t('common.close')}>
              <X className="size-5" aria-hidden />
            </D.Close>
          </div>
          <div className="flex-1 overflow-y-auto px-5 pb-5">{children}</div>
          {footer && <div className="flex gap-2 border-t border-border px-5 py-3 pb-[max(12px,env(safe-area-inset-bottom))]">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}
