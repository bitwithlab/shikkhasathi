import { useEffect } from 'react';
import { useT } from '@/lib/i18n';

export interface LegalDoc {
  title: { bn: string; en: string };
  updated: string;
  sections: { h: { bn: string; en: string }; p: { bn: string[]; en: string[] } }[];
}

/** Policy pages — must be live at shikkhasathi.com for Google OAuth verification. */
export function LegalPage({ doc }: { doc: LegalDoc }) {
  const t = useT();
  const l = t.locale;
  useEffect(() => {
    document.title = `${doc.title[l]} — ${t('brand.name')}`;
  }, [doc, l, t]);
  return (
    <main id="main" className="mx-auto max-w-3xl px-4 py-10 lg:py-14">
      <h1 className="text-3xl font-bold">{doc.title[l]}</h1>
      <p className="mt-2 text-sm text-muted">
        {l === 'bn' ? 'সর্বশেষ হালনাগাদ' : 'Last updated'}: {doc.updated}
      </p>
      <div className="mt-8 flex flex-col gap-8">
        {doc.sections.map((s, i) => (
          <section key={i}>
            <h2 className="text-xl font-semibold">{s.h[l]}</h2>
            {s.p[l].map((para, j) => (
              <p key={j} className="mt-3 text-[15.5px] leading-[1.8] text-text/90">
                {para}
              </p>
            ))}
          </section>
        ))}
      </div>
    </main>
  );
}
