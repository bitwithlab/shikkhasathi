import '../i18n';
import '../exam/i18n';
import { useEffect } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Timer, Trophy } from 'lucide-react';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export default function MockTestsPage() {
  const t = useT();
  const q = useQuery({ queryKey: ['mock-tests'], queryFn: () => api(contracts.exam.mockTests) });
  useEffect(() => {
    document.title = `${t('mk.title')} — ${t('brand.name')}`;
  }, [t]);
  const items = q.data?.items ?? [];
  const byCourse = items.reduce<Record<string, typeof items>>((a, m) => ((a[m.courseTitle] ??= []).push(m), a), {});
  return (
    <div className="mx-auto max-w-4xl px-4 py-6 lg:py-8">
      <h1 className="text-2xl font-bold">{t('mk.title')}</h1>
      <p className="text-sm text-muted">{t('mk.subtitle')}</p>
      <div className="mt-6">
        {q.isLoading ? (
          <div className="flex flex-col gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-20 rounded-card" />)}</div>
        ) : q.isError ? (
          <ErrorState onRetry={() => void q.refetch()} />
        ) : !items.length ? (
          <EmptyState illustration={<Timer className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} title={t('mk.empty')} body={t('mk.emptyBody')} action={<Button asChild variant="secondary"><Link to="/my-courses">{t('my.title')}</Link></Button>} />
        ) : (
          <div className="flex flex-col gap-6">
            {Object.entries(byCourse).map(([course, ms]) => (
              <section key={course}>
                <h2 className="latin mb-2 text-sm font-semibold text-muted">{course}</h2>
                <ul className="flex flex-col gap-3">
                  {ms.map((m) => (
                    <li key={m.id} className="surface-card flex flex-wrap items-center gap-4 p-4">
                      <span className={cn('grid size-12 shrink-0 place-items-center rounded-card text-sm font-bold tnum', m.bestScore === null ? 'bg-surface-2 text-muted' : m.bestScore >= 70 ? 'bg-success-soft text-success-fg' : m.bestScore >= 40 ? 'bg-warning-soft text-warning-fg' : 'bg-danger-soft text-danger-fg')} aria-label={m.bestScore === null ? undefined : t('mk.best', { n: m.bestScore })}>
                        {m.bestScore === null ? <Timer className="size-5" aria-hidden /> : `${localDigits(m.bestScore, t.locale)}%`}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="latin font-semibold">{m.title}</p>
                        <p className="text-sm text-muted">
                          {t('mk.questions', { n: localDigits(m.questionCount, t.locale) })} · {t('mk.minutes', { n: localDigits(Math.round(m.durationSec / 60), t.locale) })}
                          {m.attempts > 0 && ` · ${t('mk.attempts', { n: localDigits(m.attempts, t.locale) })}`}
                        </p>
                      </div>
                      <Button asChild variant={m.inProgressAttemptId || !m.attempts ? 'primary' : 'secondary'} icon={m.attempts ? <Trophy className="size-4" aria-hidden /> : undefined}>
                        <Link to={`/mock-test/${m.id}`}>{m.inProgressAttemptId ? t('mk.resume') : m.attempts ? t('mk.retry') : t('mk.start')}</Link>
                      </Button>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
