import './i18n';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { EmptyState, ErrorState, Illustration, Skeleton } from '@/components';
import { CourseCard } from '@/components/CourseCard';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, ordinal, useT } from '@/lib/i18n';

export default function SubjectPage() {
  const t = useT();
  const { slug = '' } = useParams();
  const s = useQuery({ queryKey: ['subject', slug], queryFn: () => api(contracts.catalog.subject, { params: { slug } }) });
  const courses = useQuery({ queryKey: ['search', 'subject', s.data?.id], queryFn: () => api(contracts.discovery.search, { query: { subjectId: s.data!.id, limit: 30 } }), enabled: !!s.data });
  useEffect(() => {
    if (s.data) document.title = `${s.data.name} — ${t('brand.name')}`;
  }, [s.data, t]);
  if (s.isLoading) return <div className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8"><Skeleton className="h-32 rounded-card" /></div>;
  if (s.isError || !s.data) return <main className="mx-auto max-w-lg px-4 py-16">{s.error instanceof ApiError && s.error.status === 404 ? <EmptyState title={t('subj.notFound')} /> : <ErrorState onRetry={() => void s.refetch()} />}</main>;
  const d = s.data;
  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <nav aria-label={t('course.breadcrumb')} className="mb-4 flex items-center gap-1 text-[13px] text-muted">
        <Link to="/subjects" className="hover:text-text">{t('subj.title')}</Link><ChevronRight className="size-3.5" aria-hidden /><span className="latin">{d.name}</span>
      </nav>
      <h1 className="latin text-3xl font-bold">{d.name}</h1>
      <p className="mt-1 text-muted">{[d.nameBn, d.aliases.join(' · ')].filter(Boolean).join(' — ')}</p>
      {d.offerings.length > 0 && (
        <section className="mt-6" aria-labelledby="off-h">
          <h2 id="off-h" className="text-sm font-semibold text-muted">{t('subj.taughtAt')}</h2>
          <ul className="mt-2 flex flex-wrap gap-2">
            {d.offerings.map((o) => (
              <li key={`${o.institutionId}-${o.courseCode}`}>
                <Link to={`/explore?institutionId=${o.institutionId}&subjectId=${d.id}`} className="latin inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-[13px] hover:border-primary">
                  <strong>{o.institutionShort}</strong>{o.courseCode && <span className="text-muted">{o.courseCode}</span>}{o.semester && <span className="text-muted">· {ordinal(o.semester, t.locale)}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      <section className="mt-8" aria-labelledby="sc-h">
        <h2 id="sc-h" className="text-xl font-semibold">{t('subj.courses', { n: localDigits(courses.data?.total ?? d.courseCount, t.locale) })}</h2>
        <div className="mt-4">
          {courses.isLoading ? <Skeleton className="h-64 rounded-card" /> : courses.isError ? <ErrorState onRetry={() => void courses.refetch()} /> : !courses.data?.items.length ? (
            <EmptyState illustration={<Illustration kind="courses" />} title={t('uni.noCourses')} action={<Link to={`/requests?new=1&subject=${encodeURIComponent(d.name)}`} className="text-sm font-semibold text-primary-fg hover:underline">{t('explore.requestIt')}</Link>} />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{courses.data.items.map((c) => <li key={c.id}><CourseCard c={c} /></li>)}</ul>
          )}
        </div>
      </section>
    </main>
  );
}
