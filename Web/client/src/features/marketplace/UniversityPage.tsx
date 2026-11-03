import './i18n';
import { useEffect, useMemo } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, GraduationCap, MapPin, Users } from 'lucide-react';
import { Chip, EmptyState, ErrorState, Illustration, Skeleton } from '@/components';
import { CourseCard } from '@/components/CourseCard';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, ordinal, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export default function UniversityPage() {
  const t = useT();
  const { slug = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const d = useQuery({ queryKey: ['institution', slug], queryFn: () => api(contracts.catalog.institution, { params: { slug } }) });
  const deptId = params.get('dept') ?? d.data?.departments.find((x) => x.subjectCount > 0)?.id ?? d.data?.departments[0]?.id ?? '';
  const sem = params.get('sem') ? Number(params.get('sem')) : undefined;
  const courses = useQuery({ queryKey: ['search', 'uni', d.data?.id, deptId, sem], queryFn: () => api(contracts.discovery.search, { query: { institutionId: d.data!.id, departmentId: deptId || undefined, semester: sem, limit: 24 } }), enabled: !!d.data });
  useEffect(() => {
    if (d.data) document.title = `${d.data.name} — ${t('brand.name')}`;
  }, [d.data, t]);
  const set = (k: string, v: string | null) => {
    const n = new URLSearchParams(params);
    if (v) n.set(k, v);
    else n.delete(k);
    if (k === 'dept') n.delete('sem');
    setParams(n, { replace: true });
  };
  const subjects = useMemo(() => (d.data?.subjects ?? []).filter((s) => (!deptId || s.departmentId === deptId) && (!sem || s.semester === sem)), [d.data, deptId, sem]);
  if (d.isLoading) return <div className="mx-auto max-w-[1200px] px-4 py-8 lg:px-8"><Skeleton className="h-48 rounded-card" /><Skeleton className="mt-6 h-64 rounded-card" /></div>;
  if (d.isError || !d.data) return <main className="mx-auto max-w-lg px-4 py-16">{d.error instanceof ApiError && d.error.status === 404 ? <EmptyState title={t('uni.notFound')} /> : <ErrorState onRetry={() => void d.refetch()} />}</main>;
  const u = d.data;
  const dept = u.departments.find((x) => x.id === deptId);
  const maxSem = Math.max(8, ...(dept?.programs.map((p) => p.semesters) ?? [8]));
  return (
    <main id="main" className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <nav aria-label={t('course.breadcrumb')} className="mb-4 flex items-center gap-1 text-[13px] text-muted">
        <Link to="/universities" className="hover:text-text">{t('uni.title')}</Link><ChevronRight className="size-3.5" aria-hidden /><span className="latin">{u.shortName}</span>
      </nav>
      <header className="surface-card overflow-hidden">
        <div className="h-20 bg-primary-soft" aria-hidden />
        <div className="-mt-10 flex flex-col gap-4 px-5 pb-5 sm:flex-row sm:items-end">
          <span className="latin flex size-20 shrink-0 items-center justify-center rounded-card border-4 border-surface bg-primary text-xl font-bold text-on-primary shadow-e2">{u.shortName.slice(0, 5)}</span>
          <div className="min-w-0 flex-1">
            <h1 className="text-[26px] font-bold leading-snug">{t.locale === 'bn' ? (u.nameBn ?? u.name) : u.name}</h1>
            <p className="latin text-sm text-muted">{u.name}{u.city && <span className="inline-flex items-center gap-1"> · <MapPin className="size-3.5" aria-hidden />{u.city}</span>}</p>
          </div>
        </div>
        <dl className="grid grid-cols-3 border-t border-border">
          {[
            [GraduationCap, t('uni.statCourses'), u.courseCount],
            [Users, t('uni.statTeachers'), u.teacherCount],
            [GraduationCap, t('uni.statDepts'), u.departments.length],
          ].map(([Icon, label, n], i) => {
            const I = Icon as typeof Users;
            return (
              <div key={i} className="flex flex-col gap-0.5 border-r border-border px-4 py-3 last:border-r-0">
                <dt className="flex items-center gap-1.5 text-xs text-muted"><I className="size-3.5" aria-hidden />{label as string}</dt>
                <dd className="text-xl font-bold tnum">{localDigits(n as number, t.locale)}</dd>
              </div>
            );
          })}
        </dl>
      </header>

      <section className="mt-8" aria-labelledby="dept-h">
        <h2 id="dept-h" className="text-xl font-semibold">{t('uni.pickDept')}</h2>
        <div role="tablist" className="-mx-4 mt-3 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:px-0">
          {u.departments.map((x) => (
            <Chip key={x.id} role="tab" aria-selected={x.id === deptId} selected={x.id === deptId} onClick={() => set('dept', x.id)}>
              <span className="latin">{x.shortName}</span>
              {x.subjectCount > 0 && <span className={cn('latin rounded-full px-1.5 text-[11px]', x.id === deptId ? 'bg-white/20' : 'bg-surface-2')}>{localDigits(x.subjectCount, t.locale)}</span>}
            </Chip>
          ))}
        </div>
        {dept && <p className="mt-3 text-sm text-muted">{t.locale === 'bn' ? (dept.nameBn ?? dept.name) : dept.name} · <span className="latin">{dept.programs.map((p) => p.name).join(', ')}</span></p>}
        <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label={t('explore.f.semester')}>
          <Chip selected={!sem} onClick={() => set('sem', null)}>{t('uni.allSem')}</Chip>
          {Array.from({ length: maxSem }, (_, i) => i + 1).map((n) => <Chip key={n} selected={sem === n} onClick={() => set('sem', String(n))}>{t('common.semesterN', { ord: ordinal(n, t.locale) })}</Chip>)}
        </div>
      </section>

      <section className="mt-8" aria-labelledby="subj-h">
        <h2 id="subj-h" className="text-xl font-semibold">{sem ? t('uni.subjectsSem', { ord: ordinal(sem, t.locale) }) : t('uni.subjects')}</h2>
        {subjects.length === 0 ? (
          <EmptyState compact title={t('uni.noSubjects')} />
        ) : (
          <ul className="-mx-4 mt-3 flex gap-3 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:grid sm:grid-cols-3 sm:overflow-visible sm:px-0 lg:grid-cols-5">
            {subjects.map((s) => (
              <li key={s.id} className="w-56 shrink-0 sm:w-auto">
                <Link to={`/explore?institutionId=${u.id}&courseCode=${encodeURIComponent(s.courseCode ?? '')}${s.courseCode ? '' : `&subjectId=${s.subjectId}`}`} className="surface-card flex h-full flex-col p-3.5 hover:border-primary">
                  {s.courseCode && <span className="latin text-xs font-semibold text-primary-fg">{s.courseCode}</span>}
                  <span className="latin mt-1 font-medium leading-snug">{s.subjectName}</span>
                  <span className="mt-auto pt-2 text-[13px] text-muted">{s.courseCount ? t('uni.courses', { n: localDigits(s.courseCount, t.locale) }) : t('uni.noCourseYet')}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-8" aria-labelledby="uc-h">
        <h2 id="uc-h" className="text-xl font-semibold">{t('uni.coursesFor', { u: u.shortName })}</h2>
        <div className="mt-4">
          {courses.isLoading ? (
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2].map((i) => <li key={i}><Skeleton className="aspect-[4/5] rounded-card" /></li>)}</ul>
          ) : courses.isError ? (
            <ErrorState onRetry={() => void courses.refetch()} />
          ) : !courses.data?.items.length ? (
            <EmptyState illustration={<Illustration kind="courses" />} title={t('uni.noCourses')} action={<Link to={`/requests?new=1&institutionId=${u.id}`} className="text-sm font-semibold text-primary-fg hover:underline">{t('explore.requestIt')}</Link>} />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{courses.data.items.map((c) => <li key={c.id}><CourseCard c={c} /></li>)}</ul>
          )}
        </div>
      </section>
      <p className="mt-10 text-xs text-muted">{t('brand.disclaimerUni')}</p>
    </main>
  );
}
