import './i18n';
import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, CircleCheck, PlayCircle } from 'lucide-react';
import type { MyCourse } from '@shikkhasathi/shared';
import {
  Badge,
  Button,
  Chip,
  EmptyState,
  ErrorState,
  Illustration,
  ProgressRing,
  Skeleton,
} from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, localDigits, relativeTime, useT } from '@/lib/i18n';

type Filter = 'all' | 'in_progress' | 'completed' | 'expiring';
const FILTERS: Filter[] = ['all', 'in_progress', 'completed', 'expiring'];

export default function MyCoursesPage() {
  const t = useT();
  const [sp, setSp] = useSearchParams();
  const filter = (FILTERS.includes(sp.get('f') as Filter) ? sp.get('f') : 'all') as Filter;
  // one request; filter on the client so chips are instant and counts are exact
  const q = useQuery({
    queryKey: ['my-courses'],
    queryFn: () => api(contracts.learning.myCourses, { query: { filter: 'all' } }),
  });
  useEffect(() => {
    document.title = `${t('my.title')} — ${t('brand.name')}`;
  }, [t]);
  const all = q.data?.items ?? [];
  const pick = (f: Filter) =>
    all.filter((i) =>
      f === 'in_progress'
        ? !i.completedAt && !i.expired
        : f === 'completed'
          ? !!i.completedAt
          : f === 'expiring'
            ? i.expiringSoon
            : true,
    );
  const items = pick(filter);
  return (
    <div className="mx-auto max-w-6xl px-4 py-6 lg:py-8">
      <header>
        <h1 className="text-2xl font-bold">{t('my.title')}</h1>
        <p className="text-sm text-muted">{t('my.subtitle')}</p>
      </header>
      {all.length > 0 && (
        <div
          className="mt-5 flex gap-2 overflow-x-auto scrollbar-none"
          role="group"
          aria-label={t('my.title')}
        >
          {FILTERS.map((f) => {
            const n = pick(f).length;
            if (f === 'expiring' && !n) return null;
            return (
              <Chip
                key={f}
                selected={filter === f}
                onClick={() => setSp(f === 'all' ? {} : { f }, { replace: true })}
              >
                {t(`my.f.${f}`)} <span className="tnum opacity-70">{localDigits(n, t.locale)}</span>
              </Chip>
            );
          })}
        </div>
      )}
      <div className="mt-5">
        {q.isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-56 rounded-card" />
            ))}
          </div>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : !all.length ? (
          <EmptyState
            illustration={<Illustration kind="courses" className="h-28 w-32" />}
            title={t('my.empty')}
            body={t('my.emptyBody')}
            action={
              <Button asChild>
                <Link to="/explore">{t('my.explore')}</Link>
              </Button>
            }
          />
        ) : !items.length ? (
          <EmptyState compact title={t('my.emptyFilter')} />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 [&>*]:min-w-0">
            {items.map((c) => (
              <li key={c.courseId}>
                <MyCourseCard c={c} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function MyCourseCard({ c }: { c: MyCourse }) {
  const t = useT();
  const color = c.coverColor ?? '#4F46E5';
  const done = !!c.completedAt;
  const started = c.progressPercent > 0 || !!c.lastAccessedAt;
  const to = c.resumeLessonId ? `/learn/${c.courseId}/${c.resumeLessonId}` : `/c/${c.slug}`;
  return (
    <article className="surface-card flex h-full flex-col overflow-hidden">
      <Link
        to={to}
        className="relative block h-24 shrink-0 px-4 pt-3 text-white"
        style={{ background: `linear-gradient(135deg, ${color}, ${color}cc)` }}
        tabIndex={-1}
        aria-hidden
      >
        <div className="flex items-start justify-between gap-2">
          {c.institutionShort && (
            <span className="latin rounded-full bg-white/95 px-2 py-0.5 text-[11px] font-bold text-slate-900">
              {c.institutionShort}
            </span>
          )}
          <span className="ml-auto rounded-full bg-slate-900/70 px-2 py-0.5 text-[11px] font-semibold">
            {t(`examType.${c.examType}`)}
          </span>
        </div>
        {c.courseCode && (
          <span className="latin absolute bottom-2 left-4 text-xs font-bold text-white/90">
            {c.courseCode}
          </span>
        )}
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-start gap-3">
          <div className="min-w-0 flex-1">
            <h2 className="latin line-clamp-2 font-semibold leading-snug">
              <Link to={to} className="hover:underline">
                {c.title}
              </Link>
            </h2>
            <p className="mt-0.5 truncate text-xs text-muted">{c.sellerName}</p>
          </div>
          <ProgressRing
            value={c.progressPercent}
            size={48}
            label={t('pl.progress', { n: localDigits(c.progressPercent, t.locale) })}
          />
        </div>
        <div className="mt-3 flex flex-wrap gap-1.5">
          {done && (
            <Badge tone="success" icon={<CircleCheck className="size-3.5" aria-hidden />}>
              {t('my.done')}
            </Badge>
          )}
          {c.expired ? (
            <Badge tone="danger">{t('my.expired')}</Badge>
          ) : (
            c.expiringSoon &&
            c.accessUntil && (
              <Badge tone="warning" icon={<AlertTriangle className="size-3.5" aria-hidden />}>
                {t('my.expiresIn', { when: formatDate(c.accessUntil, t.locale) })}
              </Badge>
            )
          )}
        </div>
        <p className="mt-3 text-sm text-muted tnum">
          {t('my.lessons', {
            done: localDigits(c.completedLessons, t.locale),
            total: localDigits(c.totalLessons, t.locale),
          })}
          {c.lastAccessedAt && ` · ${t('my.lastSeen', { when: relativeTime(c.lastAccessedAt, t.locale) })}`}
        </p>
        {c.resumeLessonTitle && !done && started && (
          <p className="latin mt-1 line-clamp-1 text-sm">{t('my.next', { title: c.resumeLessonTitle })}</p>
        )}
        <div className="mt-auto pt-4">
          <Button
            asChild
            block
            variant={done || c.expired ? 'secondary' : 'primary'}
            icon={<PlayCircle className="size-4" aria-hidden />}
          >
            <Link to={c.expired ? `/c/${c.slug}` : to}>
              {done ? t('my.review') : started ? t('my.resume') : t('my.start')}
            </Link>
          </Button>
        </div>
      </div>
    </article>
  );
}
