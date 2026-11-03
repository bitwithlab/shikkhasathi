import { cn } from '@/lib/cn';

const PALETTE = ['#4F46E5', '#0EA5E9', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#14B8A6', '#F97316'];

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]!;
  const last = parts.length > 1 ? parts[parts.length - 1]! : '';
  return (Array.from(first)[0] ?? '') + (last ? (Array.from(last)[0] ?? '') : '');
}

/** Initials avatar (no stock photos, CLAUDE.md §7). */
export function Avatar({ name, src, size = 40, className }: { name: string; src?: string | null; size?: number; className?: string }) {
  const color = PALETTE[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % PALETTE.length];
  if (src) return <img src={src} alt="" width={size} height={size} className={cn('rounded-full object-cover', className)} style={{ width: size, height: size }} loading="lazy" />;
  return (
    <span
      aria-hidden
      className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white', className)}
      style={{ width: size, height: size, background: color, fontSize: Math.round(size * 0.38) }}
    >
      {initials(name).toUpperCase()}
    </span>
  );
}
