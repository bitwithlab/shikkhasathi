import './i18n';
import { Link } from 'react-router';
import { BellRing, CalendarClock, CheckCircle2, ChevronDown, ClipboardCheck, CreditCard, FileText, Lock, PlayCircle, Search, Star, Timer, Zap } from 'lucide-react';
import { Badge, Button } from '@/components';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { LandingCourses, LandingTeachers } from './landing/LiveSections';
import { SearchBox } from './SearchBox';

const POPULAR = ['Metropolitan University', 'Data Structures', 'CSE-2203', 'Algorithms', 'Midterm', 'BRAC CSE-111'];

export default function LandingPage() {
  const t = useT();
  return (
    <main id="main">
      <Hero />
      <TrustStrip />
      <DashboardBand />
      <HowItWorks />
      <LandingCourses />
      <ExamModeShowcase />
      <LandingTeachers />
      <TeacherCta />
      <Faq />
      <p className="sr-only">{t('brand.tagline')}</p>
    </main>
  );
}

function Hero() {
  const t = useT();
  return (
    <section className="relative overflow-hidden">
      <div className="gradient-brand absolute inset-x-0 top-0 -z-10 h-[520px] opacity-[0.07]" aria-hidden />
      <div className="mx-auto grid max-w-[1200px] [&>*]:min-w-0 items-center gap-10 px-4 pb-14 pt-10 lg:grid-cols-[1.1fr_0.9fr] lg:px-8 lg:pb-20 lg:pt-16">
        <div className="min-w-0">
          <Badge tone="primary" wrap className="mb-5">
            {t('landing.hero.kicker')}
          </Badge>
          <h1 className="text-[32px] font-bold leading-[1.3] sm:text-[44px]">
            {t('landing.hero.title1')}
            <br />
            <span className="text-primary">{t('landing.hero.title2')}</span>
          </h1>
          <p className="mt-4 max-w-xl text-[17px] text-muted">{t('landing.hero.body')}</p>
          <div className="mt-7 max-w-xl">
            <SearchBox />
          </div>
          <div className="mt-4 flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
            <span className="shrink-0 text-sm text-muted">{t('landing.hero.popular')}</span>
            {POPULAR.map((p) => (
              <Link key={p} to={`/explore?q=${encodeURIComponent(p)}`} className="latin shrink-0 rounded-full border border-border bg-surface px-3 py-1 text-[13px] font-medium hover:border-primary hover:text-primary-fg">
                {p}
              </Link>
            ))}
          </div>
          <ul className="mt-6 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted">
            <li className="flex items-center gap-1.5">
              <Zap className="size-4 text-warning" aria-hidden /> {t('landing.hero.instant')}
            </li>
            <li className="flex items-center gap-1.5">
              <CreditCard className="size-4 text-success" aria-hidden /> {t('landing.hero.pay')}
            </li>
          </ul>
        </div>
        <HeroCollage />
      </div>
    </section>
  );
}

/** Right-side collage: countdown card + two course cards (static preview, honest numbers). */
function HeroCollage() {
  const t = useT();
  return (
    <div className="relative mx-auto hidden w-full max-w-md lg:block" aria-hidden>
      <div className="gradient-brand rounded-card p-5 text-white shadow-e3">
        <div className="flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 text-sm font-semibold">
            <Timer className="size-4" /> Midterm · CSE-2203
          </span>
          <span className="rounded-full bg-white/15 px-2.5 py-0.5 text-xs">{t('landing.collage.daysLeft')}</span>
        </div>
        <p className="mt-3 text-[28px] font-bold leading-tight">Data Structures</p>
        <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/20">
          <div className="h-full w-[62%] rounded-full bg-white" />
        </div>
        <p className="mt-2 text-sm text-white/85">{t('landing.collage.ready')}</p>
      </div>
      <div className="relative -mt-3 ml-8 rounded-card border border-border bg-surface p-4 shadow-e2">
        <div className="flex items-center justify-between gap-2">
          <Badge tone="success" icon={<CheckCircle2 className="size-3.5" />}>
            {t('compat.match')}
          </Badge>
          <span className="latin text-xs font-semibold text-muted">MU · Sylhet</span>
        </div>
        <p className="latin mt-2 font-semibold">Data Structures & Algorithms (CSE-2203)</p>
        <div className="mt-2 flex items-center justify-between text-sm">
          <span className="flex items-center gap-1 text-muted">
            <Star className="size-4 fill-warning text-warning" /> <span className="latin tnum">4.9</span>
          </span>
          <span className="latin tnum">
            <span className="mr-1.5 text-xs text-muted line-through">৳{localDigits(599, t.locale)}</span>
            <span className="font-bold text-text">৳{localDigits(299, t.locale)}</span>
          </span>
        </div>
      </div>
      <div className="relative -mt-2 mr-10 rounded-card border border-border bg-surface p-4 shadow-e1">
        <div className="flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-control bg-danger-soft text-danger">
            <ClipboardCheck className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="latin truncate text-sm font-semibold">CSE-2204 Lab 2: BST Deletion</p>
            <p className="text-xs text-muted">Google Classroom · {t('landing.collage.dueTonight')}</p>
          </div>
          <Badge tone="danger">{t('status.pending')}</Badge>
        </div>
      </div>
    </div>
  );
}

function TrustStrip() {
  const t = useT();
  const unis = ['Metropolitan University', 'North South University', 'BRAC University', 'AIUB', 'UIU', 'SUST', 'Daffodil International University', 'East West University'];
  return (
    <section className="border-y border-border bg-surface" aria-label={t('landing.trust.label')}>
      <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
        <p className="text-center text-sm text-muted">{t('landing.trust.line')}</p>
        <ul className="mt-4 flex gap-6 overflow-x-auto scrollbar-none md:flex-wrap md:justify-center">
          {unis.map((u) => (
            <li key={u} className="latin shrink-0 text-sm font-semibold text-muted/90">
              {u}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/** Pillar 1: the unified dashboard. Split layout, list-style — not another card grid. */
function DashboardBand() {
  const t = useT();
  const items = [
    { icon: ClipboardCheck, k: 'landing.dash.p1' },
    { icon: FileText, k: 'landing.dash.p2' },
    { icon: BellRing, k: 'landing.dash.p3' },
    { icon: CalendarClock, k: 'landing.dash.p4' },
  ];
  return (
    <section className="mx-auto grid max-w-[1200px] [&>*]:min-w-0 gap-10 px-4 py-16 lg:grid-cols-2 lg:items-center lg:px-8">
      <div>
        <p className="text-sm font-semibold text-primary-fg">{t('landing.dash.kicker')}</p>
        <h2 className="mt-2 text-[28px] font-bold leading-snug">{t('landing.dash.title')}</h2>
        <p className="mt-3 text-muted">{t('landing.dash.body')}</p>
        <ul className="mt-6 flex flex-col gap-4">
          {items.map(({ icon: Icon, k }) => (
            <li key={k} className="flex gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-control bg-primary-soft text-primary-fg">
                <Icon className="size-[18px]" strokeWidth={1.75} aria-hidden />
              </span>
              <span className="pt-1.5">{t(k)}</span>
            </li>
          ))}
        </ul>
        <p className="mt-5 flex items-center gap-1.5 text-xs text-muted">
          <Lock className="size-3.5" aria-hidden /> {t('landing.dash.privacy')} · {t('brand.disclaimerGoogle')}
        </p>
      </div>
      <div className="surface-card p-4 sm:p-5" aria-hidden>
        <p className="text-sm font-semibold">{t('home.dueSoon')}</p>
        {[
          { g: t('due.overdue'), tone: 'danger', title: 'MAT-2101 Assignment 3', cls: 'Linear Algebra · Sec A', s: 'missing' },
          { g: t('due.today'), tone: 'danger', title: 'CSE-2204 Lab 2: BST Deletion', cls: 'DS Lab · Sec A', s: 'pending' },
          { g: t('due.tomorrow'), tone: 'warning', title: 'CSE-2205 Quiz 2: Java Threads', cls: 'OOP II', s: 'turned_in' },
          { g: t('due.thisWeek'), tone: 'neutral', title: 'ENG-1101 Essay Draft', cls: 'English', s: 'graded' },
        ].map((r) => (
          <div key={r.title} className="mt-3">
            <p className={cn('text-xs font-semibold', r.tone === 'danger' ? 'text-danger-fg' : r.tone === 'warning' ? 'text-warning-fg' : 'text-muted')}>{r.g}</p>
            <div className="mt-1.5 flex items-center gap-3 rounded-control border border-border px-3 py-2.5">
              <span className={cn('h-8 w-1 rounded-full', r.tone === 'danger' ? 'bg-danger' : r.tone === 'warning' ? 'bg-warning' : 'bg-border-strong')} />
              <span className="min-w-0 flex-1">
                <span className="latin block truncate text-sm font-medium">{r.title}</span>
                <span className="latin block text-xs text-muted">{r.cls}</span>
              </span>
              <Badge tone={r.s === 'missing' ? 'danger' : r.s === 'pending' ? 'warning' : r.s === 'graded' ? 'primary' : 'success'}>{t(`status.${r.s === 'turned_in' ? 'turnedIn' : r.s}`)}</Badge>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function HowItWorks() {
  const t = useT();
  const steps = [
    { icon: Search, k: 'find' },
    { icon: CreditCard, k: 'pay' },
    { icon: PlayCircle, k: 'learn' },
  ];
  return (
    <section className="border-y border-border bg-surface">
      <div className="mx-auto max-w-[1200px] px-4 py-16 lg:px-8">
        <div className="max-w-2xl">
          <h2 className="text-[28px] font-bold">{t('landing.how.title')}</h2>
          <p className="mt-2 text-muted">{t('landing.how.body')}</p>
        </div>
        <ol className="mt-10 grid gap-8 md:grid-cols-3 md:gap-6">
          {steps.map(({ icon: Icon, k }, i) => (
            <li key={k} className="relative">
              <div className="flex items-center gap-3">
                <span className="latin flex size-11 items-center justify-center rounded-full border-2 border-primary text-lg font-bold text-primary-fg">{localDigits(i + 1, t.locale)}</span>
                {i < steps.length - 1 && <span className="hidden h-px flex-1 bg-border md:block" aria-hidden />}
              </div>
              <h3 className="mt-4 flex items-center gap-2 text-lg font-semibold">
                <Icon className="size-5 text-primary" strokeWidth={1.75} aria-hidden /> {t(`landing.how.${k}.title`)}
              </h3>
              <p className="mt-1.5 text-[15px] text-muted">{t(`landing.how.${k}.body`)}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function ExamModeShowcase() {
  const t = useT();
  const rows = [
    { state: 'done', title: 'Time Complexity ও Big-O', meta: t('landing.exam.r1') },
    { state: 'now', title: 'Binary Search Tree (BST)', meta: t('landing.exam.r2'), important: true },
    { state: 'locked', title: 'Graph Traversal (BFS & DFS)', meta: t('landing.exam.r3'), important: true },
  ];
  return (
    <section className="mx-auto grid max-w-[1200px] [&>*]:min-w-0 gap-10 px-4 py-16 lg:grid-cols-[0.95fr_1.05fr] lg:items-center lg:px-8">
      <div className="order-2 mx-auto w-full max-w-sm rounded-[28px] border border-border bg-surface p-3 shadow-e3 lg:order-1" aria-hidden>
        <div className="gradient-brand rounded-[20px] p-4 text-white">
          <p className="latin text-xs font-semibold opacity-90">Exam Mode · CSE-2203</p>
          <p className="mt-1 text-2xl font-bold">{t('landing.exam.hoursLeft')}</p>
          <p className="text-sm opacity-90">{t('landing.exam.roadmap')}</p>
        </div>
        <ul className="mt-3 flex flex-col gap-2">
          {rows.map((r, i) => (
            <li key={r.title} className={cn('flex items-center gap-3 rounded-control border px-3 py-2.5', r.state === 'now' ? 'border-primary bg-primary-soft' : 'border-border')}>
              <span className={cn('flex size-7 items-center justify-center rounded-full text-xs font-bold', r.state === 'done' ? 'bg-success text-white' : r.state === 'now' ? 'bg-primary text-on-primary' : 'bg-surface-2 text-muted')}>
                {r.state === 'done' ? <CheckCircle2 className="size-4" /> : r.state === 'locked' ? <Lock className="size-3.5" /> : <PlayCircle className="size-4" />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">
                  {t('landing.exam.lecture', { n: localDigits(String(i + 1).padStart(2, '0'), t.locale) })}: <span className="latin">{r.title}</span>
                </span>
                <span className="block text-xs text-muted">{r.meta}</span>
              </span>
              {r.important && <Star className="size-4 shrink-0 fill-warning text-warning" />}
            </li>
          ))}
        </ul>
        <div className="mt-2 flex items-center gap-2 rounded-control bg-warning-soft px-3 py-2 text-sm text-warning-fg">
          <FileText className="size-4" /> {t('landing.exam.cheatsheet')}
        </div>
      </div>
      <div className="order-1 lg:order-2">
        <p className="text-sm font-semibold text-warning-fg">{t('landing.exam.kicker')}</p>
        <h2 className="mt-2 text-[28px] font-bold leading-snug">{t('landing.exam.title')}</h2>
        <p className="mt-3 text-muted">{t('landing.exam.body')}</p>
        <ul className="mt-6 flex flex-col gap-4">
          {['b1', 'b2', 'b3', 'b4'].map((b) => (
            <li key={b} className="flex gap-3">
              <CheckCircle2 className="mt-1 size-5 shrink-0 text-success" strokeWidth={1.75} aria-hidden />
              <span>
                <span className="block font-semibold">{t(`landing.exam.${b}.title`)}</span>
                <span className="block text-[15px] text-muted">{t(`landing.exam.${b}.body`)}</span>
              </span>
            </li>
          ))}
        </ul>
        <Button asChild className="mt-7" icon={<Timer className="size-4" aria-hidden />}>
          <Link to="/auth">{t('landing.exam.cta')}</Link>
        </Button>
        <p className="mt-3 text-xs text-muted">{t('brand.disclaimerTopics')}</p>
      </div>
    </section>
  );
}

function TeacherCta() {
  const t = useT();
  return (
    <section className="mx-auto max-w-[1200px] px-4 py-12 lg:px-8">
      <div className="flex flex-col gap-6 rounded-card bg-[#1E1B4B] p-8 text-white md:flex-row md:items-center md:justify-between md:p-10">
        <div className="max-w-xl">
          <h2 className="text-2xl font-bold leading-snug">{t('landing.teach.title')}</h2>
          <p className="mt-2 text-white/80">{t('landing.teach.body')}</p>
        </div>
        <Button asChild size="lg" variant="secondary" className="shrink-0 border-transparent">
          <Link to="/auth?mode=register&role=teacher">{t('footer.teach')}</Link>
        </Button>
      </div>
    </section>
  );
}

function Faq() {
  const t = useT();
  const items = ['q1', 'q2', 'q3', 'q4', 'q5'];
  return (
    <section className="mx-auto max-w-3xl px-4 py-12 lg:px-8" aria-labelledby="faq-h">
      <h2 id="faq-h" className="text-center text-[28px] font-bold">
        {t('landing.faq.title')}
      </h2>
      <div className="mt-8 flex flex-col gap-3">
        {items.map((k) => (
          <details key={k} className="group surface-card px-5 py-4 [&_summary::-webkit-details-marker]:hidden">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold">
              {t(`landing.faq.${k}.q`)}
              <ChevronDown className="size-5 shrink-0 text-muted transition-transform group-open:rotate-180" aria-hidden />
            </summary>
            <p className="mt-3 text-[15px] text-muted">{t(`landing.faq.${k}.a`)}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
