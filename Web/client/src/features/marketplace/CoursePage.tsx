import './i18n';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, BadgeCheck, BookOpen, Check, ChevronDown, ChevronRight, Clock, FileText, Heart, Layers, Lock, PlayCircle, Share2, ShieldCheck, Star, Timer, Users } from 'lucide-react';
import type { CourseDetail } from '@shikkhasathi/shared';
import { Avatar, Badge, Button, EmptyState, ErrorState, Illustration, Skeleton, toast } from '@/components';
import { CompatibilityBadge } from '@/components/CourseCard';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatDate, formatNumber, formatTaka, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export function duration(sec: number, locale: 'bn' | 'en') {
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  if (locale === 'bn') return `${h ? `${localDigits(h, 'bn')}ঘ ` : ''}${localDigits(m, 'bn')}মি`;
  return `${h ? `${h}h ` : ''}${m}m`;
}

export default function CoursePage() {
  const { slug = '' } = useParams();
  const t = useT();
  const q = useQuery({ queryKey: ['course', slug], queryFn: () => api(contracts.discovery.course, { params: { slug } }) });
  useEffect(() => {
    if (q.data) document.title = `${q.data.title} — ${t('brand.name')}`;
  }, [q.data, t]);
  if (q.isLoading) return <CourseSkeleton />;
  if (q.isError || !q.data)
    return (
      <main id="main" className="mx-auto max-w-lg px-4 py-16">
        {q.error instanceof ApiError && q.error.status === 404 ? (
          <EmptyState illustration={<Illustration kind="search" className="h-28 w-32" />} title={t('course.notFound')} action={<Button asChild><Link to="/explore">{t('nav.explore')}</Link></Button>} />
        ) : (
          <ErrorState onRetry={() => void q.refetch()} />
        )}
      </main>
    );
  return <CourseView c={q.data} />;
}

function CourseView({ c }: { c: CourseDetail }) {
  const t = useT();
  const totalLessons = c.sections.reduce((a, s) => a + s.chapters.reduce((b, ch) => b + ch.lessons.length, 0), 0);
  const ratingTotal = c.ratingDistribution.reduce((a, b) => a + b, 0);
  return (
    <main id="main" className="pb-28 lg:pb-12">
      <div className="mx-auto max-w-[1200px] px-4 pt-5 lg:px-8">
        {c.breadcrumb.length > 0 && (
          <nav aria-label={t('course.breadcrumb')} className="mb-4 overflow-x-auto scrollbar-none">
            <ol className="flex items-center gap-1 whitespace-nowrap text-[13px] text-muted">
              {c.breadcrumb.map((b, i) => (
                <li key={b.href} className="flex items-center gap-1">
                  {i > 0 && <ChevronRight className="size-3.5" aria-hidden />}
                  <Link to={b.href} className="latin hover:text-text">{b.label}</Link>
                </li>
              ))}
            </ol>
          </nav>
        )}
        {c.status !== 'published' && <p className="mb-4 rounded-control bg-warning-soft px-3 py-2 text-sm text-warning-fg">{t('course.previewOnly', { status: t(`courseStatus.${c.status}`) })}</p>}
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="min-w-0">
            <header>
              <div className="flex flex-wrap gap-2">
                <Badge tone="primary">{t(`examType.${c.examType}`)} · {t(`courseType.${c.courseType}`)}</Badge>
                {c.courseCode && <Badge className="latin">{c.courseCode}</Badge>}
              </div>
              <h1 className="latin mt-3 text-[28px] font-bold leading-snug lg:text-[34px]">{c.title}</h1>
              <p className="mt-3 text-[17px] text-muted">{c.shortDescription}</p>
              <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm">
                <li className="flex items-center gap-1.5"><Star className="size-4 fill-warning text-warning" aria-hidden /><span className="font-semibold tnum">{c.ratingCount ? localDigits(c.ratingAvg.toFixed(1), t.locale) : '—'}</span><span className="text-muted">({t('course.reviewsN', { n: localDigits(c.ratingCount, t.locale) })})</span></li>
                <li className="flex items-center gap-1.5 text-muted"><Users className="size-4" aria-hidden />{t('course.studentsN', { n: formatNumber(c.enrollmentCount, t.locale) })}</li>
                <li className="flex items-center gap-1.5 text-muted"><Clock className="size-4" aria-hidden />{duration(c.totalDurationSec, t.locale)} · {t('course.lessonsN', { n: localDigits(totalLessons, t.locale) })}</li>
                <li className="flex items-center gap-1.5 text-muted"><BookOpen className="size-4" aria-hidden />{t(`explore.lang.${c.language as 'bn'}`)}</li>
              </ul>
              {c.compatibility !== 'general' && (
                <div className={cn('mt-5 flex items-start gap-3 rounded-card border p-4', c.compatibility === 'match' ? 'border-success/30 bg-success-soft' : 'border-warning/30 bg-warning-soft')}>
                  {c.compatibility === 'match' ? <ShieldCheck className="mt-0.5 size-5 shrink-0 text-success-fg" aria-hidden /> : <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning-fg" aria-hidden />}
                  <div className="min-w-0">
                    <CompatibilityBadge value={c.compatibility} />
                    <p className="latin mt-1 text-sm">{c.targets.map((x) => x.label).join(' · ')}</p>
                  </div>
                </div>
              )}
            </header>

            <SellerCard c={c} />

            {c.learnings.length > 0 && (
              <section className="mt-8" aria-labelledby="learn-h">
                <h2 id="learn-h" className="text-xl font-semibold">{t('course.learn')}</h2>
                <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                  {c.learnings.map((l) => (
                    <li key={l} className="flex gap-2.5 rounded-control border border-border bg-surface p-3 text-[15px]">
                      <Check className="mt-0.5 size-5 shrink-0 text-success" aria-hidden /> <span className="latin">{l}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <Curriculum c={c} totalLessons={totalLessons} />

            <section className="mt-8" aria-labelledby="res-h">
              <h2 id="res-h" className="text-xl font-semibold">{t('course.resources')}</h2>
              <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  [PlayCircle, t('course.res.videos', { n: localDigits(c.resources.videos, t.locale) })],
                  [FileText, t('course.res.notes', { n: localDigits(c.resources.notes, t.locale) })],
                  [Layers, c.resources.previousQuestionYears.length ? t('course.res.pq', { years: c.resources.previousQuestionYears.map((y) => localDigits(y, t.locale)).join(', ') }) : t('course.res.noPq')],
                  [Timer, t('course.res.mock', { n: localDigits(c.resources.mockTests, t.locale) })],
                ].map(([Icon, label], i) => {
                  const I = Icon as typeof PlayCircle;
                  return (
                    <li key={i} className="surface-card flex flex-col gap-2 p-3.5 text-sm">
                      <I className="size-5 text-primary" strokeWidth={1.75} aria-hidden />
                      {label as string}
                    </li>
                  );
                })}
              </ul>
            </section>

            {c.notFor.length > 0 && (
              <section className="mt-8 rounded-card border border-border bg-surface-2 p-5" aria-labelledby="notfor-h">
                <h2 id="notfor-h" className="text-lg font-semibold">{t('course.notFor')}</h2>
                <ul className="mt-3 flex flex-col gap-2 text-[15px]">
                  {c.notFor.map((n) => <li key={n} className="flex gap-2"><span className="mt-2.5 size-1.5 shrink-0 rounded-full bg-muted" aria-hidden />{n}</li>)}
                </ul>
              </section>
            )}

            <p className="mt-6 flex items-start gap-2 rounded-control border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning-fg">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden /> {t('brand.disclaimerTopics')}
            </p>

            <section className="mt-8" aria-labelledby="rev-h">
              <h2 id="rev-h" className="text-xl font-semibold">{t('course.reviews')}</h2>
              <div className="mt-4 grid gap-6 sm:grid-cols-[180px_minmax(0,1fr)]">
                <div>
                  <p className="text-5xl font-bold tnum">{c.ratingCount ? localDigits(c.ratingAvg.toFixed(1), t.locale) : '—'}</p>
                  <p className="mt-1 flex gap-0.5" aria-hidden>{[1, 2, 3, 4, 5].map((i) => <Star key={i} className={cn('size-4', i <= Math.round(c.ratingAvg) ? 'fill-warning text-warning' : 'text-border-strong')} />)}</p>
                  <p className="mt-1 text-sm text-muted">{t('course.reviewsN', { n: localDigits(c.ratingCount, t.locale) })}</p>
                </div>
                <ul className="flex flex-col gap-1.5">
                  {[5, 4, 3, 2, 1].map((r) => {
                    const n = c.ratingDistribution[r - 1] ?? 0;
                    const pct = ratingTotal ? (n / ratingTotal) * 100 : 0;
                    return (
                      <li key={r} className="flex items-center gap-3 text-sm">
                        <span className="w-8 tnum">{localDigits(r, t.locale)} ★</span>
                        <span className="h-2 flex-1 overflow-hidden rounded-full bg-surface-2"><span className="block h-full rounded-full bg-warning" style={{ width: `${pct}%` }} /></span>
                        <span className="w-10 text-right text-muted tnum">{localDigits(Math.round(pct), t.locale)}%</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
              <ReviewsList courseId={c.id} />
            </section>

            {c.faq.length > 0 && (
              <section className="mt-8" aria-labelledby="faq-h2">
                <h2 id="faq-h2" className="text-xl font-semibold">{t('landing.faq.title')}</h2>
                <div className="mt-4 flex flex-col gap-2">
                  {c.faq.map((f) => (
                    <details key={f.q} className="group surface-card px-4 py-3 [&_summary::-webkit-details-marker]:hidden">
                      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 font-medium">{f.q}<ChevronDown className="size-5 shrink-0 text-muted transition-transform group-open:rotate-180" aria-hidden /></summary>
                      <p className="mt-2 text-[15px] text-muted">{f.a}</p>
                    </details>
                  ))}
                </div>
              </section>
            )}
          </div>
          <aside className="hidden lg:block">
            <div className="sticky top-20"><BuyCard c={c} /></div>
          </aside>
        </div>
      </div>
      <MobileBuyBar c={c} />
    </main>
  );
}

function SellerCard({ c }: { c: CourseDetail }) {
  const t = useT();
  return (
    <Link to={`/t/${c.seller.slug}`} className="surface-card mt-6 flex items-center gap-4 p-4 hover:border-border-strong">
      <Avatar name={c.seller.name} size={52} />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 font-semibold">{c.seller.name}{c.seller.verified && <BadgeCheck className="size-5 text-success" aria-label={t('course.verified')} />}</p>
        {c.seller.headline && <p className="latin truncate text-sm text-muted">{c.seller.headline}</p>}
        <p className="mt-0.5 text-[13px] text-muted">
          ★ {localDigits(c.seller.ratingAvg.toFixed(1), t.locale)} · {t('course.studentsN', { n: formatNumber(c.seller.studentCount, t.locale) })} · {t('course.coursesN', { n: localDigits(c.seller.courseCount, t.locale) })}
        </p>
      </div>
      <ChevronRight className="size-5 shrink-0 text-muted" aria-hidden />
    </Link>
  );
}

function Curriculum({ c, totalLessons }: { c: CourseDetail; totalLessons: number }) {
  const t = useT();
  const [open, setOpen] = useState<Set<string>>(() => new Set(c.sections.slice(0, 1).map((s) => s.id)));
  const allOpen = open.size === c.sections.length;
  const learnHref = (lessonId: string) => (c.viewer.enrolled ? `/learn/${c.id}/${lessonId}` : `/learn/${c.id}/${lessonId}?preview=1`);
  return (
    <section className="mt-8" aria-labelledby="cur-h">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h2 id="cur-h" className="text-xl font-semibold">{t('course.curriculum')}</h2>
          <p className="text-sm text-muted">{t('course.curSummary', { s: localDigits(c.sections.length, t.locale), l: localDigits(totalLessons, t.locale), d: duration(c.totalDurationSec, t.locale) })}</p>
        </div>
        <button type="button" onClick={() => setOpen(allOpen ? new Set() : new Set(c.sections.map((s) => s.id)))} className="text-sm font-semibold text-primary-fg hover:underline">{allOpen ? t('course.collapseAll') : t('course.expandAll')}</button>
      </div>
      <ol className="mt-4 flex flex-col gap-3">
        {c.sections.map((s) => {
          const isOpen = open.has(s.id);
          const lessons = s.chapters.flatMap((ch) => ch.lessons);
          return (
            <li key={s.id} className="surface-card overflow-hidden">
              <button type="button" aria-expanded={isOpen} onClick={() => setOpen((o) => { const n = new Set(o); if (n.has(s.id)) n.delete(s.id); else n.add(s.id); return n; })} className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left hover:bg-surface-2">
                <span className="min-w-0">
                  <span className="latin block font-semibold">{s.title}</span>
                  <span className="text-[13px] text-muted">{t('course.lessonsN', { n: localDigits(lessons.length, t.locale) })} · {duration(lessons.reduce((a, l) => a + l.durationSec, 0), t.locale)}</span>
                </span>
                <ChevronDown className={cn('size-5 shrink-0 text-muted transition-transform', isOpen && 'rotate-180')} aria-hidden />
              </button>
              {isOpen && (
                <div className="border-t border-border">
                  {s.chapters.map((ch) => (
                    <div key={ch.id}>
                      {s.chapters.length > 1 && <p className="latin bg-surface-2/60 px-4 py-1.5 text-xs font-semibold text-muted">{ch.title}</p>}
                      <ul>
                        {ch.lessons.map((l) => {
                          const canPlay = l.isPreview || c.viewer.enrolled;
                          const Icon = l.type === 'video' ? PlayCircle : FileText;
                          const row = (
                            <>
                              <span className={cn('flex size-7 shrink-0 items-center justify-center rounded-full', canPlay ? 'bg-primary-soft text-primary-fg' : 'bg-surface-2 text-muted')}>
                                {canPlay ? <Icon className="size-4" aria-hidden /> : <Lock className="size-3.5" aria-label={t('course.locked')} />}
                              </span>
                              <span className="latin min-w-0 flex-1 text-[15px]">{l.title}</span>
                              {l.isImportant && <Badge tone="warning" icon={<Star className="size-3 fill-current" aria-hidden />}>{t('course.important')}</Badge>}
                              {l.isPreview && !c.viewer.enrolled && <Badge tone="primary">{t('course.preview')}</Badge>}
                              {l.durationSec > 0 && <span className="w-12 shrink-0 text-right text-[13px] text-muted tnum">{duration(l.durationSec, t.locale)}</span>}
                            </>
                          );
                          return (
                            <li key={l.id} className="border-t border-border first:border-t-0">
                              {canPlay ? <Link to={learnHref(l.id)} className="flex items-center gap-3 px-4 py-2.5 hover:bg-surface-2">{row}</Link> : <div className="flex items-center gap-3 px-4 py-2.5">{row}</div>}
                            </li>
                          );
                        })}
                      </ul>
                    </div>
                  ))}
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}

function ReviewsList({ courseId }: { courseId: string }) {
  void courseId; // reviews list arrives with Phase 7 (GET /courses/:id/reviews)
  return null;
}

function usePurchase(c: CourseDetail) {
  const nav = useNavigate();
  const authed = useAuth((s) => s.status === 'authed');
  return () => {
    if (!authed) return nav(`/auth?next=${encodeURIComponent(`/checkout/${c.id}`)}`);
    nav(c.viewer.enrolled ? `/learn/${c.id}/${c.viewer.resumeLessonId ?? c.viewer.firstLessonId}` : `/checkout/${c.id}`);
  };
}

function BuyCard({ c }: { c: CourseDetail }) {
  const t = useT();
  const qc = useQueryClient();
  const authed = useAuth((s) => s.status === 'authed');
  const nav = useNavigate();
  const buy = usePurchase(c);
  const off = c.compareAtPrice && c.compareAtPrice > c.price ? Math.round((1 - c.price / c.compareAtPrice) * 100) : 0;
  const preview = c.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons)).find((l) => l.isPreview);
  const wish = useMutation({
    mutationFn: () => (c.viewer.wishlisted ? api(contracts.wishlist.remove, { params: { courseId: c.id } }) : api(contracts.wishlist.add, { params: { courseId: c.id } })),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['course', c.slug] });
      toast.success(c.viewer.wishlisted ? t('course.unsaved') : t('course.saved'));
    },
  });
  const share = async () => {
    const url = `https://shikkhasathi.com/c/${c.slug}`;
    try {
      if (navigator.share) await navigator.share({ title: c.title, url });
      else {
        await navigator.clipboard.writeText(url);
        toast.success(t('course.linkCopied'));
      }
    } catch {
      /* cancelled */
    }
  };
  return (
    <div className="surface-card overflow-hidden">
      <div className="relative aspect-video" style={{ background: `linear-gradient(135deg, ${c.coverColor ?? '#4F46E5'} 0%, ${c.coverColor ?? '#4F46E5'}bb 100%)` }}>
        {c.coverUrl && <img src={c.coverUrl} alt="" width={640} height={360} className="size-full object-cover" />}
        {preview && (
          <Link to={`/learn/${c.id}/${preview.id}?preview=1`} className="absolute inset-0 flex items-center justify-center bg-slate-950/20 text-white hover:bg-slate-950/30" aria-label={t('course.watchPreview')}>
            <span className="flex size-14 items-center justify-center rounded-full bg-white/95 text-primary shadow-e2"><PlayCircle className="size-8" aria-hidden /></span>
          </Link>
        )}
      </div>
      <div className="p-5">
        {c.viewer.enrolled ? (
          <Badge tone="success" icon={<Check className="size-3.5" aria-hidden />}>{t('course.enrolled')}</Badge>
        ) : (
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold tnum">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
            {off > 0 && (
              <>
                <span className="text-muted line-through tnum">{formatTaka(c.compareAtPrice!, t.locale)}</span>
                <Badge tone="danger">{t('course.off', { p: localDigits(off, t.locale) })}</Badge>
              </>
            )}
          </div>
        )}
        <Button block size="lg" className="mt-4" onClick={buy}>
          {c.viewer.enrolled ? t('course.continue') : c.price === 0 ? t('course.enrollFree') : t('course.buyNow')}
        </Button>
        {preview && !c.viewer.enrolled && (
          <Button block variant="secondary" className="mt-2" onClick={() => nav(`/learn/${c.id}/${preview.id}?preview=1`)} icon={<PlayCircle className="size-4" aria-hidden />}>{t('course.watchPreview')}</Button>
        )}
        <ul className="mt-5 flex flex-col gap-2 text-sm">
          <li className="flex items-center gap-2"><PlayCircle className="size-4 text-muted" aria-hidden />{t('course.inc.video', { d: duration(c.totalDurationSec, t.locale) })}</li>
          {c.resources.notes > 0 && <li className="flex items-center gap-2"><FileText className="size-4 text-muted" aria-hidden />{t('course.inc.notes')}</li>}
          {c.resources.previousQuestionYears.length > 0 && <li className="flex items-center gap-2"><Layers className="size-4 text-muted" aria-hidden />{t('course.inc.pq')}</li>}
          <li className="flex items-center gap-2"><Clock className="size-4 text-muted" aria-hidden />{c.accessType === 'lifetime' ? t('course.inc.lifetime') : c.accessDays ? t('course.inc.days', { n: localDigits(c.accessDays, t.locale) }) : t('course.inc.untilExam')}</li>
        </ul>
        {c.price > 0 && <p className="mt-4 flex items-start gap-2 text-xs text-muted"><ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />{t('course.refundNote')}</p>}
        <div className="mt-4 flex gap-2 border-t border-border pt-4">
          <Button variant="ghost" size="sm" className="flex-1" onClick={() => (authed ? wish.mutate() : nav('/auth'))} icon={<Heart className={cn('size-4', c.viewer.wishlisted && 'fill-danger text-danger')} aria-hidden />}>
            {c.viewer.wishlisted ? t('course.savedShort') : t('course.save')}
          </Button>
          <Button variant="ghost" size="sm" className="flex-1" onClick={() => void share()} icon={<Share2 className="size-4" aria-hidden />}>{t('course.share')}</Button>
        </div>
        <p className="mt-3 text-center text-xs text-muted">{t('course.updated', { d: formatDate(c.updatedAt, t.locale, { day: 'numeric', month: 'long', year: 'numeric' }) })}</p>
      </div>
    </div>
  );
}

function MobileBuyBar({ c }: { c: CourseDetail }) {
  const t = useT();
  const buy = usePurchase(c);
  const off = c.compareAtPrice && c.compareAtPrice > c.price ? Math.round((1 - c.price / c.compareAtPrice) * 100) : 0;
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 flex items-center gap-3 border-t border-border bg-surface/95 px-4 py-3 pb-[max(12px,env(safe-area-inset-bottom))] lg:hidden">
      {!c.viewer.enrolled && (
        <div className="min-w-0">
          <p className="text-xl font-bold leading-tight tnum">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</p>
          {off > 0 && <p className="text-xs text-muted"><span className="line-through tnum">{formatTaka(c.compareAtPrice!, t.locale)}</span> · {t('course.off', { p: localDigits(off, t.locale) })}</p>}
        </div>
      )}
      <Button size="lg" className="flex-1" onClick={buy}>{c.viewer.enrolled ? t('course.continue') : c.price === 0 ? t('course.enrollFree') : t('course.buyNow')}</Button>
    </div>
  );
}

function CourseSkeleton() {
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8" aria-busy>
      <Skeleton className="h-4 w-72" />
      <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div>
          <Skeleton className="h-6 w-40" />
          <Skeleton className="mt-3 h-10 w-full" />
          <Skeleton className="mt-3 h-5 w-3/4" />
          <Skeleton className="mt-6 h-20 rounded-card" />
          <Skeleton className="mt-6 h-64 rounded-card" />
        </div>
        <Skeleton className="hidden h-[480px] rounded-card lg:block" />
      </div>
    </div>
  );
}
