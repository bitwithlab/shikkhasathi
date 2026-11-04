import { NavLink } from 'react-router';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';
import { localDigits, useT } from '@/lib/i18n';

export interface NavItem {
  to: string;
  labelKey: string;
  icon: LucideIcon;
  end?: boolean;
  badge?: number;
}

export function SideNav({ items, collapsed, onNavigate, label }: { items: NavItem[]; collapsed?: boolean; onNavigate?: () => void; label: string }) {
  const t = useT();
  return (
    <nav aria-label={label} className="flex flex-col gap-0.5">
      {items.map((it) => (
        <NavLink
          key={it.to}
          to={it.to}
          end={it.end}
          onClick={onNavigate}
          title={collapsed ? t(it.labelKey) : undefined}
          className={({ isActive }) =>
            cn(
              'group relative flex h-11 items-center gap-3 rounded-control px-3 text-[15px] font-medium transition-colors duration-150',
              isActive ? 'bg-primary-soft text-primary-fg' : 'text-muted hover:bg-surface-2 hover:text-text',
              collapsed && 'justify-center px-0',
            )
          }
        >
          {({ isActive }) => (
            <>
              {isActive && <span className="absolute inset-y-2 left-0 w-[3px] rounded-full bg-primary" aria-hidden />}
              <it.icon className="size-5 shrink-0" strokeWidth={1.75} aria-hidden />
              <span className={cn('truncate', collapsed && 'sr-only')}>{t(it.labelKey)}</span>
              {!!it.badge && (
                <span className={cn('latin ml-auto min-w-5 rounded-full bg-danger px-1.5 text-center text-[11px] font-bold leading-5 text-white', collapsed && 'absolute right-1.5 top-1.5 ml-0 min-w-4 px-1 text-[10px] leading-4')}>
                  {localDigits(it.badge > 99 ? '99+' : it.badge, t.locale)}
                </span>
              )}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}
