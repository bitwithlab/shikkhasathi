import { create } from 'zustand';
import { CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { cn } from '@/lib/cn';

type Kind = 'success' | 'error' | 'info';
interface ToastItem {
  id: number;
  kind: Kind;
  message: string;
}
const useToasts = create<{ items: ToastItem[]; push: (k: Kind, m: string) => void; dismiss: (id: number) => void }>((set) => ({
  items: [],
  push: (kind, message) => {
    const id = Date.now() + Math.random();
    set((s) => ({ items: [...s.items.slice(-2), { id, kind, message }] }));
    setTimeout(() => set((s) => ({ items: s.items.filter((i) => i.id !== id) })), kind === 'error' ? 6000 : 3500);
  },
  dismiss: (id) => set((s) => ({ items: s.items.filter((i) => i.id !== id) })),
}));

export const toast = {
  success: (m: string) => useToasts.getState().push('success', m),
  error: (m: string) => useToasts.getState().push('error', m),
  info: (m: string) => useToasts.getState().push('info', m),
};

const icons = { success: CheckCircle2, error: XCircle, info: Info };

export function Toaster() {
  const { items, dismiss } = useToasts();
  return (
    <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-20 z-[100] flex flex-col items-center gap-2 px-4 lg:bottom-6">
      {items.map((t) => {
        const Icon = icons[t.kind];
        return (
          <div key={t.id} role={t.kind === 'error' ? 'alert' : 'status'} className="animate-fade-up pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-control border border-border bg-surface px-4 py-3 shadow-e3">
            <Icon className={cn('mt-0.5 size-5 shrink-0', t.kind === 'success' ? 'text-success' : t.kind === 'error' ? 'text-danger' : 'text-primary')} strokeWidth={1.75} aria-hidden />
            <p className="flex-1 text-sm text-text">{t.message}</p>
            <button type="button" onClick={() => dismiss(t.id)} className="-m-1 rounded p-1 text-muted hover:text-text" aria-label="close">
              <X className="size-4" aria-hidden />
            </button>
          </div>
        );
      })}
    </div>
  );
}
