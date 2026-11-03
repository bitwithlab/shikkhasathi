import '../exam/i18n';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, CircleSlash, RotateCcw, XCircle } from 'lucide-react';
import { Badge, Button, Chip, EmptyState, ErrorState, ProgressRing, Skeleton } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { fmtTime } from '../player/time';

export default function MockResultPage() {
  const { id = '' } = useParams();
  const t = useT();
  const q = useQuery({ queryKey: ['mock-result', id], queryFn: () => api(contracts.exam.result, { params: { id } }) });
  const [onlyWrong, setOnlyWrong] = useState(true);
  useEffect(() => {
    document.title = `${t('rs.title')} — ${t('brand.name')}`;
  }, [t]);
  if (q.isLoading) return <div className="mx-auto max-w-3xl px-4 py-8"><Skeleton className="h-56 rounded-card" /><Skeleton className="mt-5 h-40 rounded-card" /></div>;
  if (q.isError || !q.data) return <div className="mx-auto max-w-lg px-4 py-16"><ErrorState body={q.error instanceof ApiError ? q.error.message : undefined} onRetry={() => void q.refetch()} /></div>;
  const r = q.data;
  const tone = r.score >= 70 ? 'success' : r.score >= 40 ? 'warning' : 'primary';
  const msg = r.score >= 70 ? t('rs.great') : r.score >= 40 ? t('rs.ok') : t('rs.low');
  const review = r.review.filter((x) => !onlyWrong || x.chosenOptionId !== x.correctOptionId);
  return (
    <div className="mx-auto max-w-3xl px-4 py-6 lg:py-8">
      <section className="surface-card p-5 sm:p-6" aria-labelledby="rs-h">
        <p className="latin text-sm text-muted">{r.title}</p>
        <div className="mt-3 flex flex-wrap items-center gap-6">
          <ProgressRing value={r.score} size={120} stroke={10} tone={tone} label={`${t('rs.score')} ${r.score}%`} />
          <div className="min-w-0 flex-1">
            <h1 id="rs-h" className="text-xl font-bold">{msg}</h1>
            <p className="mt-1 text-sm text-muted">{t('rs.time', { t: localDigits(fmtTime(r.timeUsedSec), t.locale) })} · {t('rs.best', { n: localDigits(r.bestScore, t.locale), a: localDigits(r.attempts, t.locale) })}</p>
            {r.autoSubmitted && <Badge tone="warning" className="mt-2">{t('rs.auto')}</Badge>}
            <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
              {[['correct', r.correct, 'text-success-fg', CheckCircle2], ['wrong', r.wrong, 'text-danger-fg', XCircle], ['skipped', r.skipped, 'text-muted', CircleSlash]].map(([k, n, cls, Icon]) => {
                const I = Icon as typeof CheckCircle2;
                return (
                  <div key={k as string} className="rounded-control bg-surface-2 py-2">
                    <dt className="flex items-center justify-center gap-1 text-xs text-muted"><I className={cn('size-3.5', cls as string)} aria-hidden />{t(`rs.${k as string}`)}</dt>
                    <dd className="text-lg font-bold tnum">{localDigits(n as number, t.locale)}</dd>
                  </div>
                );
              })}
            </dl>
          </div>
        </div>
        <div className="mt-5 flex flex-wrap gap-2">
          <Button asChild icon={<RotateCcw className="size-4" aria-hidden />}><Link to={`/mock-test/${r.testId}`}>{t('rs.retry')}</Link></Button>
          <Button asChild variant="ghost"><Link to="/mock-tests">{t('rs.back')}</Link></Button>
        </div>
      </section>

      {r.chapters.length > 0 && (
        <section className="surface-card mt-5 p-5" aria-labelledby="ch-h">
          <h2 id="ch-h" className="text-[17px] font-semibold">{t('rs.chapters')}</h2>
          <ul className="mt-4 flex flex-col gap-3">
            {[...r.chapters].sort((a, b) => a.correct / a.total - b.correct / b.total).map((c) => {
              const pct = Math.round((c.correct / c.total) * 100);
              return (
                <li key={c.tag}>
                  <div className="flex items-center justify-between gap-2 text-sm">
                    <span className="latin font-medium">{c.tag}{pct < 50 && <Badge tone="danger" className="ml-2">{t('rs.weak')}</Badge>}</span>
                    <span className="text-muted tnum">{localDigits(c.correct, t.locale)}/{localDigits(c.total, t.locale)}</span>
                  </div>
                  <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-surface-2" role="img" aria-label={`${c.tag} ${pct}%`}>
                    <div className={cn('h-full rounded-full', pct >= 70 ? 'bg-success' : pct >= 40 ? 'bg-warning' : 'bg-danger')} style={{ width: `${Math.max(pct, 3)}%` }} />
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      <section className="mt-5" aria-labelledby="rv-h">
        <div className="flex flex-wrap items-center gap-2">
          <h2 id="rv-h" className="mr-auto text-[17px] font-semibold">{t('rs.review')}</h2>
          <Chip selected={onlyWrong} onClick={() => setOnlyWrong(true)}>{t('rs.onlyWrong')}</Chip>
          <Chip selected={!onlyWrong} onClick={() => setOnlyWrong(false)}>{t('rs.all')}</Chip>
        </div>
        {!review.length ? (
          <EmptyState compact title={t('rs.great')} />
        ) : (
          <ol className="mt-3 flex flex-col gap-3">
            {review.map((x) => {
              const n = r.review.indexOf(x) + 1;
              const ok = x.chosenOptionId === x.correctOptionId;
              return (
                <li key={x.questionId} className="surface-card p-4">
                  <p className="flex items-start gap-2 font-semibold">
                    <span className="tnum text-muted">{localDigits(n, t.locale)}.</span>
                    <span className="whitespace-pre-wrap">{x.body}</span>
                  </p>
                  <ul className="mt-3 flex flex-col gap-2">
                    {x.options.map((o) => {
                      const right = o.id === x.correctOptionId;
                      const mine = o.id === x.chosenOptionId;
                      return (
                        <li key={o.id} className={cn('latin flex items-start gap-2 rounded-control border px-3 py-2 text-[15px]', right ? 'border-success/40 bg-success-soft' : mine ? 'border-danger/40 bg-danger-soft' : 'border-border')}>
                          {right ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success-fg" aria-label={t('rs.right')} /> : mine ? <XCircle className="mt-0.5 size-4 shrink-0 text-danger-fg" aria-label={t('rs.yours')} /> : <span className="size-4 shrink-0" />}
                          <span className="min-w-0 flex-1">{o.body}</span>
                          {mine && !right && <span className="shrink-0 text-xs font-semibold text-danger-fg">{t('rs.yours')}</span>}
                        </li>
                      );
                    })}
                  </ul>
                  {!x.chosenOptionId && <p className="mt-2 text-sm text-muted">{t('rs.noAnswer')}</p>}
                  {x.explanation && (!ok || !onlyWrong) && (
                    <p className="mt-3 rounded-control bg-surface-2 px-3 py-2 text-sm"><span className="font-semibold">{t('rs.explain')}: </span>{x.explanation}</p>
                  )}
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </div>
  );
}
