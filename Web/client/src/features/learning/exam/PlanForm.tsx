import './i18n';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, CalendarDays, FlaskConical, GraduationCap, Hourglass, ListChecks, NotebookPen } from 'lucide-react';
import type { ExamPlanSummary } from '@shikkhasathi/shared';
import { Button, Field, Input, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

const TYPES = [
  { id: 'midterm', icon: NotebookPen },
  { id: 'final', icon: GraduationCap },
  { id: 'quiz', icon: ListChecks },
  { id: 'lab', icon: FlaskConical },
] as const;

const dhakaYmd = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);
const dhakaHm = (d: Date) => new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Dhaka', hour: '2-digit', minute: '2-digit', hour12: false }).format(d);

export function PlanForm({ plan, onDone }: { plan?: ExamPlanSummary; onDone: (p: ExamPlanSummary) => void }) {
  const t = useT();
  const qc = useQueryClient();
  const my = useQuery({ queryKey: ['my-courses'], queryFn: () => api(contracts.learning.myCourses, { query: { filter: 'all' } }), staleTime: 60_000 });
  const courses = (my.data?.items ?? []).filter((c) => !c.expired);
  const init = plan ? new Date(plan.examDate) : new Date(Date.now() + 7 * 86_400_000);
  const [v, setV] = useState({
    subjectName: plan?.subjectName ?? '',
    courseCode: plan?.courseCode ?? '',
    examType: (plan?.examType ?? 'midterm') as 'midterm',
    date: dhakaYmd(init),
    time: plan ? dhakaHm(init) : '10:00',
    courseIds: plan?.courseIds ?? ([] as string[]),
  });
  const today = dhakaYmd(new Date());
  const daysLeft = useMemo(() => Math.round((Date.parse(`${v.date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000), [v.date, today]);
  const dateOk = !!v.date && daysLeft >= 0;

  const m = useMutation({
    mutationFn: () => {
      const body = { subjectName: v.subjectName.trim(), courseCode: v.courseCode.trim() || null, examType: v.examType, examDate: `${v.date}T${v.time || '10:00'}:00+06:00`, courseIds: v.courseIds };
      return plan ? api(contracts.exam.update, { params: { id: plan.id }, body }) : api(contracts.exam.create, { body });
    },
    onSuccess: (p) => {
      toast.success(plan ? t('common.saved') : t('ex.created'));
      void qc.invalidateQueries({ queryKey: ['exam-plans'] });
      void qc.invalidateQueries({ queryKey: ['exam-plan'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      onDone(p);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  const pickCourse = (c: (typeof courses)[number]) => {
    const on = v.courseIds.includes(c.courseId);
    setV((x) => ({
      ...x,
      courseIds: on ? x.courseIds.filter((id) => id !== c.courseId) : [...x.courseIds, c.courseId].slice(0, 5),
      subjectName: !on && !x.subjectName ? c.title.split(/\s+[—–-]\s+/)[0]!.trim() : x.subjectName,
      courseCode: !on && !x.courseCode && c.courseCode ? c.courseCode : x.courseCode,
      examType: !on && !plan && ['midterm', 'final', 'quiz', 'lab'].includes(c.examType) ? (c.examType as 'midterm') : x.examType,
    }));
  };

  return (
    <form
      className="flex flex-col gap-6"
      onSubmit={(e) => {
        e.preventDefault();
        if (v.subjectName.trim().length >= 2 && dateOk) m.mutate();
      }}
    >
      <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_160px]">
        <Field label={t('ex.subject')}>{(p) => <Input {...p} value={v.subjectName} onChange={(e) => setV({ ...v, subjectName: e.target.value })} placeholder={t('ex.subjectPh')} required minLength={2} />}</Field>
        <Field label="Course code">{(p) => <Input {...p} value={v.courseCode} onChange={(e) => setV({ ...v, courseCode: e.target.value.toUpperCase() })} placeholder="CSE-2203" className="latin" />}</Field>
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-semibold">{t('ex.type')}</legend>
        <div className="grid grid-cols-2 gap-3">
          {TYPES.map(({ id, icon: Icon }) => {
            const on = v.examType === id;
            return (
              <label key={id} className={cn('flex cursor-pointer items-center gap-3 rounded-card border bg-surface p-3 transition-[border-color,box-shadow] duration-150 has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-primary/20', on ? 'border-2 border-primary shadow-e1' : 'border-border hover:border-border-strong')}>
                <input type="radio" name="examType" value={id} checked={on} onChange={() => setV({ ...v, examType: id as 'midterm' })} className="sr-only" />
                <span className={cn('grid size-10 shrink-0 place-items-center rounded-control', on ? 'bg-primary text-on-primary' : 'bg-surface-2 text-muted')}><Icon className="size-5" aria-hidden /></span>
                <span className="min-w-0">
                  <span className={cn('block truncate font-semibold', on && 'text-primary-fg')}>{t(`ex.type.${id}`)}</span>
                  <span className="block truncate text-xs text-muted">{t(`ex.type.${id}Sub`)}</span>
                </span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="rounded-card border border-border bg-surface p-4">
        <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_140px]">
          <Field label={t('ex.date')} error={v.date && !dateOk ? t('ex.dateErr') : undefined}>{(p) => <Input {...p} type="date" min={today} value={v.date} onChange={(e) => setV({ ...v, date: e.target.value })} required className="latin tnum" />}</Field>
          <Field label={t('ex.time')}>{(p) => <Input {...p} type="time" value={v.time} onChange={(e) => setV({ ...v, time: e.target.value })} className="latin tnum" />}</Field>
        </div>
        {dateOk && (
          <p className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-warning-soft px-3 py-1 text-sm font-semibold text-warning-fg">
            <Hourglass className="size-4" aria-hidden />
            {t('ex.left', { n: localDigits(daysLeft, t.locale) })}
          </p>
        )}
      </div>

      <fieldset>
        <legend className="text-sm font-semibold">{t('ex.courses')}</legend>
        <p className="mt-0.5 text-sm text-muted">{courses.length ? t('ex.coursesHint') : t('ex.noCourses')}</p>
        {courses.length > 0 && (
          <ul className="mt-3 flex flex-col gap-2">
            {courses.map((c) => {
              const on = v.courseIds.includes(c.courseId);
              return (
                <li key={c.courseId}>
                  <label className={cn('flex cursor-pointer items-center gap-3 rounded-control border p-3 transition-colors', on ? 'border-primary bg-primary-soft/50' : 'border-border hover:bg-surface-2')}>
                    <input type="checkbox" checked={on} onChange={() => pickCourse(c)} className="size-5 shrink-0 accent-[var(--color-primary)]" />
                    <BookOpen className="size-4 shrink-0 text-muted" aria-hidden />
                    <span className="latin min-w-0 flex-1 truncate text-sm font-medium">{c.title}</span>
                    {c.courseCode && <span className="latin hidden shrink-0 text-xs text-muted sm:inline">{c.courseCode}</span>}
                  </label>
                </li>
              );
            })}
          </ul>
        )}
      </fieldset>

      <Button type="submit" size="lg" block loading={m.isPending} disabled={v.subjectName.trim().length < 2 || !dateOk} icon={<CalendarDays className="size-5" aria-hidden />}>
        {plan ? t('ex.save') : t('ex.create')}
      </Button>
    </form>
  );
}
