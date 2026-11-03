import { Link } from 'react-router';
import { Logo } from '@/components';
import { localDigits, useT } from '@/lib/i18n';

export function Footer() {
  const t = useT();
  const cols = [
    { title: t('nav.public.explore'), links: [['/explore', t('nav.public.explore')], ['/universities', t('nav.public.universities')], ['/subjects', t('nav.public.subjects')], ['/requests', t('nav.requests')]] },
    { title: t('brand.name'), links: [['/about', t('footer.about')], ['/contact', t('footer.contact')], ['/teach', t('footer.teach')]] },
    { title: t('footer.terms'), links: [['/privacy', t('footer.privacy')], ['/terms', t('footer.terms')], ['/refund', t('footer.refund')]] },
  ];
  return (
    <footer className="mt-16 border-t border-border bg-surface pb-24 md:pb-0">
      <div className="mx-auto grid max-w-[1200px] gap-10 px-4 py-12 md:grid-cols-[1.4fr_repeat(3,1fr)] lg:px-8">
        <div className="max-w-xs">
          <Logo />
          <p className="mt-3 text-sm text-muted">{t('brand.tagline')}</p>
          <p className="mt-4 text-xs text-muted">{t('footer.payments')}</p>
          <div className="mt-2 flex flex-wrap gap-2" aria-label={t('footer.payments')}>
            {[
              ['bKash', '#E2136E'],
              ['Nagad', '#EC1C24'],
              ['Rocket', '#8C3494'],
              ['VISA', '#1A1F71'],
            ].map(([n, c]) => (
              <span key={n} className="latin rounded-md border border-border bg-bg px-2 py-0.5 text-[11px] font-bold" style={{ color: c }}>
                {n}
              </span>
            ))}
          </div>
        </div>
        {cols.map((c) => (
          <div key={c.title}>
            <p className="text-sm font-semibold">{c.title}</p>
            <ul className="mt-3 flex flex-col gap-2">
              {c.links.map(([to, label]) => (
                <li key={to}>
                  <Link to={to!} className="text-sm text-muted hover:text-text">
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-border">
        <div className="mx-auto flex max-w-[1200px] flex-col gap-1 px-4 py-4 text-xs text-muted md:flex-row md:justify-between lg:px-8">
          <span>{t('footer.rights', { year: localDigits(new Date().getFullYear(), t.locale) })}</span>
          <span>
            {t('brand.disclaimerUni')} · {t('brand.disclaimerGoogle')}
          </span>
        </div>
      </div>
    </footer>
  );
}
