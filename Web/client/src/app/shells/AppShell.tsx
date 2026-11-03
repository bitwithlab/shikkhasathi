import { useState, type ReactNode } from 'react';
import { Link, Outlet, useLocation } from 'react-router';
import { Bell, ChevronsLeft, ChevronsRight, LogOut, Menu } from 'lucide-react';
import { Avatar, Button, Logo, Sheet } from '@/components';
import { localDigits, useT } from '@/lib/i18n';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/cn';
import { SideNav, type NavItem } from './SideNav';
import { LangToggle, ThemeSwitcher } from './Prefs';

interface Props {
  nav: NavItem[];
  navLabel: string;
  /** Role switch links at the bottom (Teacher Studio / Admin / Student view). */
  switches?: { to: string; label: string; icon: NavItem['icon'] }[];
  bottomBar?: NavItem[];
  unread?: number;
  badgeLabel?: ReactNode;
}

function readCollapsed() {
  try {
    return localStorage.getItem('ss-sidebar') === 'collapsed';
  } catch {
    return false;
  }
}

/** Shared layout for student / seller / admin: collapsible sidebar (desktop), off-canvas (mobile), optional bottom bar. */
export function AppShell({ nav, navLabel, switches = [], bottomBar, unread = 0, badgeLabel }: Props) {
  const t = useT();
  const { user, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [open, setOpen] = useState(false);
  const loc = useLocation();

  const toggle = () => {
    setCollapsed((c) => {
      try {
        localStorage.setItem('ss-sidebar', c ? 'expanded' : 'collapsed');
      } catch {
        /* ignore */
      }
      return !c;
    });
  };

  const footer = (inSheet: boolean) => (
    <div className="flex flex-col gap-2 border-t border-border pt-3">
      {switches.map((s) => (
        <Link key={s.to} to={s.to} onClick={() => setOpen(false)} className={cn('flex h-11 items-center gap-3 rounded-control border border-dashed border-border-strong px-3 text-sm font-semibold text-primary-fg hover:bg-primary-soft', collapsed && !inSheet && 'justify-center px-0')} title={s.label}>
          <s.icon className="size-5" strokeWidth={1.75} aria-hidden />
          <span className={cn(collapsed && !inSheet && 'sr-only')}>{s.label}</span>
        </Link>
      ))}
      {(!collapsed || inSheet) && (
        <div className="flex items-center justify-between gap-2">
          <ThemeSwitcher />
          <LangToggle />
        </div>
      )}
      <button type="button" onClick={() => void logout()} className={cn('flex h-10 items-center gap-3 rounded-control px-3 text-sm text-muted hover:bg-danger-soft hover:text-danger-fg', collapsed && !inSheet && 'justify-center px-0')} title={t('common.logout')}>
        <LogOut className="size-4" strokeWidth={1.75} aria-hidden />
        <span className={cn(collapsed && !inSheet && 'sr-only')}>{t('common.logout')}</span>
      </button>
    </div>
  );

  const userCard = (inSheet: boolean) =>
    user && (
      <Link to="/profile" onClick={() => setOpen(false)} className={cn('flex items-center gap-3 rounded-card border border-border bg-bg p-2.5 hover:border-border-strong', collapsed && !inSheet && 'justify-center border-transparent bg-transparent p-0')}>
        <Avatar name={user.name} src={user.avatarUrl} size={collapsed && !inSheet ? 36 : 40} />
        {(!collapsed || inSheet) && (
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold">{user.name}</span>
            <span className="block truncate text-xs text-muted">{badgeLabel}</span>
          </span>
        )}
      </Link>
    );

  return (
    <div className="flex min-h-dvh">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-control focus:bg-surface focus:px-4 focus:py-2 focus:shadow-e3">
        {t('nav.skipToContent')}
      </a>
      <aside className={cn('sticky top-0 hidden h-dvh shrink-0 flex-col gap-4 border-r border-border bg-surface px-3 py-4 transition-[width] duration-200 ease-ss lg:flex', collapsed ? 'w-[76px]' : 'w-[264px]')}>
        <div className={cn('flex items-center justify-between', collapsed && 'flex-col gap-3')}>
          <Link to="/home" aria-label={t('brand.name')} className="px-1">
            <Logo compact={collapsed} />
          </Link>
          <button type="button" onClick={toggle} className="rounded-control p-2 text-muted hover:bg-surface-2 hover:text-text" aria-label={collapsed ? t('nav.expand') : t('nav.collapse')}>
            {collapsed ? <ChevronsRight className="size-4" aria-hidden /> : <ChevronsLeft className="size-4" aria-hidden />}
          </button>
        </div>
        {userCard(false)}
        <div className="-mx-1 flex-1 overflow-y-auto px-1 scrollbar-none">
          <SideNav items={nav} collapsed={collapsed} label={navLabel} />
        </div>
        {footer(false)}
      </aside>

      <Sheet open={open} onOpenChange={setOpen} title={t('brand.name')} side="left">
        <div className="flex flex-col gap-4">
          {userCard(true)}
          <SideNav items={nav} label={navLabel} onNavigate={() => setOpen(false)} />
          {footer(true)}
        </div>
      </Sheet>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-border bg-surface/95 px-2 lg:hidden">
          <Button variant="ghost" size="sm" aria-label={t('common.menu')} onClick={() => setOpen(true)} icon={<Menu className="size-5" aria-hidden />} />
          <Link to="/home" aria-label={t('brand.name')}>
            <Logo />
          </Link>
          <Link to="/notifications" className="relative ml-auto inline-flex size-11 items-center justify-center rounded-control text-muted hover:bg-surface-2" aria-label={`${t('nav.notifications')}${unread ? ` (${unread})` : ''}`}>
            <Bell className="size-5" strokeWidth={1.75} aria-hidden />
            {unread > 0 && <span className="latin absolute right-1.5 top-1.5 min-w-4 rounded-full bg-danger px-1 text-center text-[10px] font-bold leading-4 text-white">{localDigits(unread > 9 ? '9+' : unread, t.locale)}</span>}
          </Link>
        </header>
        <main id="main" key={loc.pathname.split('/')[1]} className={cn('flex-1', bottomBar && 'pb-20 lg:pb-0')}>
          <Outlet />
        </main>
        {bottomBar && (
          <nav aria-label="bottom" className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 border-t border-border bg-surface/95 pb-[env(safe-area-inset-bottom)] lg:hidden">
            {bottomBar.map((it) => {
              const active = it.end ? loc.pathname === it.to : loc.pathname.startsWith(it.to);
              return (
                <Link key={it.to} to={it.to} aria-current={active ? 'page' : undefined} className={cn('flex h-16 flex-col items-center justify-center gap-0.5 text-[11.5px] font-medium', active ? 'text-primary-fg' : 'text-muted')}>
                  <it.icon className="size-[22px]" strokeWidth={active ? 2.1 : 1.75} aria-hidden />
                  <span className="max-w-full truncate px-1">{t(it.labelKey)}</span>
                </Link>
              );
            })}
          </nav>
        )}
      </div>
    </div>
  );
}
