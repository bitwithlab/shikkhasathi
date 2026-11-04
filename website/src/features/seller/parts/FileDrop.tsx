import { useRef, useState, type ReactNode } from 'react';
import { UploadCloud } from './icons';
import { cn } from '@/lib/cn';

/** Click-or-drop file target (a real button, so it is keyboard reachable). */
export function FileDrop({ accept, onFile, title, hint, disabled, className, icon, compact }: { accept: string; onFile: (f: File) => void; title: ReactNode; hint?: ReactNode; disabled?: boolean; className?: string; icon?: ReactNode; compact?: boolean }) {
  const ref = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  return (
    <>
      <button
        type="button"
        disabled={disabled}
        onClick={() => ref.current?.click()}
        onDragOver={(e) => {
          if (!e.dataTransfer.types.includes('Files')) return;
          e.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          const f = e.dataTransfer.files[0];
          if (!f) return;
          e.preventDefault();
          setOver(false);
          onFile(f);
        }}
        className={cn(
          'flex w-full items-center gap-3 rounded-control border border-dashed text-left transition-colors duration-150 disabled:opacity-50',
          compact ? 'min-h-12 px-3 py-2' : 'min-h-20 px-4 py-3',
          over ? 'border-primary bg-primary-soft' : 'border-border-strong bg-surface hover:border-primary hover:bg-primary-soft/50',
          className,
        )}
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-primary-soft text-primary">{icon ?? <UploadCloud className="size-5" strokeWidth={1.75} aria-hidden />}</span>
        <span className="min-w-0">
          <span className="block text-sm font-semibold text-text">{title}</span>
          {hint && <span className="block text-xs text-muted">{hint}</span>}
        </span>
      </button>
      <input
        ref={ref}
        type="file"
        accept={accept}
        className="hidden"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => {
          const f = e.target.files?.[0];
          e.target.value = '';
          if (f) onFile(f);
        }}
      />
    </>
  );
}
