import { lazy, Suspense, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { Menu, Search } from 'lucide-react';
import { Button } from '@/components/Button';
import { Logo } from '@/components/Logo';

// Radix dialog only loads when the mobile menu is opened.
const Sheet = lazy(() => import('@/components/Overlay').then((m) => ({ default: m.Sheet })));
import { useT } from '@/lib/i18n';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/cn';
import { LangToggle } from './Prefs';
import { Footer } from './Footer';

const LINKS = [
  { to: '/explore', key: 'nav.public.explore' },
  { to: '/universities', key: 'nav.public.universities' },
  { to: '/subjects', key: 'nav.public.subjects' },
  { to: '/explore?examType=midterm', key: 'nav.public.examPrep' },
];

export function PublicShell() {
  const loc = useLocation();
  const t = useT();
  const [open, setOpen] = useState(false);
  const authed = useAuth((s) => s.status === 'authed');
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-control focus:bg-surface focus:px-4 focus:py-2 focus:shadow-e3"
      >
        {t('nav.skipToContent')}
      </a>
      <header className="sticky top-0 z-40 border-b border-border bg-bg/90 backdrop-blur-sm supports-[backdrop-filter]:bg-bg/75">
        <div className="mx-auto flex h-16 max-w-[1200px] items-center gap-3 px-4 md:gap-6 lg:px-8">
          <Link to="/" aria-label={t('brand.name')}>
            <Logo />
          </Link>
          <nav aria-label="primary" className="hidden items-center gap-1 md:flex">
            {LINKS.map((l) => (
              <NavLink
                key={l.key}
                to={l.to}
                className={({ isActive }) =>
                  cn(
                    'rounded-control px-3 py-2 text-[15px] font-medium hover:bg-surface-2',
                    isActive ? 'text-primary-fg' : 'text-muted hover:text-text',
                  )
                }
              >
                {t(l.key)}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1.5">
            <span className="hidden sm:block">
              <LangToggle />
            </span>
            {authed ? (
              <Button asChild size="sm">
                <Link to="/home">{t('nav.home')}</Link>
              </Button>
            ) : (
              <>
                <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
                  <Link to="/auth">{t('nav.public.login')}</Link>
                </Button>
                <Button asChild size="sm">
                  <Link to="/auth?mode=register">{t('nav.public.start')}</Link>
                </Button>
              </>
            )}
            <Button
              variant="ghost"
              size="sm"
              className="md:hidden"
              aria-label={t('common.menu')}
              onClick={() => setOpen(true)}
              icon={<Menu className="size-5" aria-hidden />}
            />
          </div>
        </div>
      </header>
      {open && (
        <Suspense fallback={null}>
          <Sheet open={open} onOpenChange={setOpen} title={t('brand.name')} side="left">
            <nav className="flex flex-col gap-1">
              {LINKS.map((l) => (
                <Link
                  key={l.key}
                  to={l.to}
                  onClick={() => setOpen(false)}
                  className="flex h-12 items-center rounded-control px-3 text-base font-medium hover:bg-surface-2"
                >
                  {t(l.key)}
                </Link>
              ))}
              <Link
                to="/auth"
                onClick={() => setOpen(false)}
                className="flex h-12 items-center rounded-control px-3 text-base font-medium hover:bg-surface-2"
              >
                {t('nav.public.login')}
              </Link>
              <LangToggle className="mt-2 self-start" />
            </nav>
          </Sheet>
        </Suspense>
      )}
      <div className="flex-1">
        <Outlet />
      </div>
      <Footer />
      {/* phone CTA only on the landing — course pages have their own buy bar */}
      {loc.pathname === '/' && (
        <div
          className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/95 p-3 pb-[max(12px,env(safe-area-inset-bottom))] md:hidden"
          data-public-cta
        >
          <Button asChild block icon={<Search className="size-4" aria-hidden />}>
            <Link to="/explore">{t('nav.public.explore')}</Link>
          </Button>
        </div>
      )}
    </div>
  );
}
