import './i18n';
import { useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, BookOpen, Link2, Star, Users } from 'lucide-react';
import { Avatar, Badge, Button, EmptyState, ErrorState, Skeleton, toast } from '@/components';
import { CourseCard } from '@/components/CourseCard';
import { api, ApiError, contracts } from '@/lib/api';
import { formatNumber, localDigits, useT } from '@/lib/i18n';

export default function StorefrontPage() {
  const t = useT();
  const { sellerSlug = '' } = useParams();
  const q = useQuery({ queryKey: ['storefront', sellerSlug], queryFn: () => api(contracts.discovery.storefront, { params: { slug: sellerSlug } }) });
  useEffect(() => {
    if (q.data) document.title = `${q.data.name} — ${t('brand.name')}`;
  }, [q.data, t]);
  if (q.isLoading) return <div className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8"><Skeleton className="h-56 rounded-card" /></div>;
  if (q.isError || !q.data) return <main className="mx-auto max-w-lg px-4 py-16">{q.error instanceof ApiError && q.error.status === 404 ? <EmptyState title={t('store.notFound')} /> : <ErrorState onRetry={() => void q.refetch()} />}</main>;
  const s = q.data;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`https://shikkhasathi.com/t/${s.slug}`);
      toast.success(t('course.linkCopied'));
    } catch {
      /* ignore */
    }
  };
  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <header className="surface-card overflow-hidden">
        <div className="h-28 bg-surface-2 sm:h-36" style={s.coverUrl ? { backgroundImage: `url(${s.coverUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined} aria-hidden />
        <div className="flex flex-col gap-4 px-5 pb-5 sm:flex-row sm:items-end">
          <Avatar name={s.name} size={88} className="-mt-11 border-4 border-surface" />
          <div className="min-w-0 flex-1">
            <h1 className="flex flex-wrap items-center gap-2 text-2xl font-bold">{s.name}{s.verified && <Badge tone="success" icon={<BadgeCheck className="size-3.5" aria-hidden />}>{t('course.verified')}</Badge>}</h1>
            {s.headline && <p className="latin text-muted">{s.headline}</p>}
            {s.institutionsTaught.length > 0 && <p className="latin mt-1 text-sm text-muted">{t('store.teaches', { list: s.institutionsTaught.join(', ') })}</p>}
          </div>
          <Button variant="secondary" size="sm" onClick={() => void copy()} icon={<Link2 className="size-4" aria-hidden />}>shikkhasathi.com/t/{s.slug}</Button>
        </div>
        <dl className="grid grid-cols-3 border-t border-border">
          {[
            [Users, t('store.students'), formatNumber(s.stats.students, t.locale)],
            [BookOpen, t('store.courses'), localDigits(s.stats.courses, t.locale)],
            [Star, t('store.rating'), s.stats.ratingCount ? `${localDigits(s.stats.ratingAvg.toFixed(1), t.locale)} (${localDigits(s.stats.ratingCount, t.locale)})` : '—'],
          ].map(([Icon, label, v], i) => {
            const I = Icon as typeof Users;
            return (
              <div key={i} className="border-r border-border px-4 py-3 last:border-r-0">
                <dt className="flex items-center gap-1.5 text-xs text-muted"><I className="size-3.5" aria-hidden />{label as string}</dt>
                <dd className="text-lg font-bold tnum">{v as string}</dd>
              </div>
            );
          })}
        </dl>
      </header>
      {s.bio && <p className="mt-6 max-w-3xl whitespace-pre-line text-[16px] leading-[1.8]">{s.bio}</p>}
      <section className="mt-8" aria-labelledby="sc-h">
        <h2 id="sc-h" className="text-xl font-semibold">{t('store.coursesBy', { name: s.name })}</h2>
        {s.courses.length === 0 ? <EmptyState compact title={t('store.noCourses')} /> : <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{s.courses.map((c) => <li key={c.id}><CourseCard c={c} /></li>)}</ul>}
      </section>
    </main>
  );
}
