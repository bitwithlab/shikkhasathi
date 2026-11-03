/**
 * Exam study-plan generator (spec §6.9) — pure and deterministic.
 * Distributes remaining lessons of linked courses (⭐ important first, curriculum order otherwise)
 * across the days until the exam, balancing minutes per day. The last day before the exam is revision
 * (mock tests + flashcards + important lessons recap) when there are ≥ 2 days left.
 */
export interface PlanLesson {
  id: string;
  courseId: string;
  title: string;
  chapterTitle: string;
  durationSec: number;
  type: string;
  isImportant: boolean;
  /** completed before today (Dhaka) — excluded from the plan */
  doneBefore: boolean;
  /** completed today — stays in today's list, ticked */
  doneToday: boolean;
}
export interface PlanMock {
  id: string;
  title: string;
  questionCount: number;
  attempted: boolean;
}
export type GenItem =
  | { kind: 'lesson'; lesson: PlanLesson; done: boolean }
  | { kind: 'mock'; mock: PlanMock; done: boolean }
  | { kind: 'flashcards'; count: number; done: boolean }
  | { kind: 'revision'; done: boolean };
export interface GenDay {
  date: string;
  items: GenItem[];
  totalMinutes: number;
  isRevision: boolean;
}

/** Text/PDF lessons have no duration — budget 10 minutes of reading. */
export const effectiveMinutes = (l: Pick<PlanLesson, 'durationSec' | 'type'>) => (l.durationSec > 0 ? Math.max(1, Math.round(l.durationSec / 60)) : 10);

const addDays = (ymd: string, n: number) => {
  const d = new Date(`${ymd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
export const daysBetween = (a: string, b: string) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);

export function generatePlan(input: { today: string; examDay: string; lessons: PlanLesson[]; mocks: PlanMock[]; flashcards: { total: number; known: number } }): GenDay[] {
  const { today, examDay } = input;
  const span = daysBetween(today, examDay); // days strictly before the exam day
  if (span <= 0) return [];
  const pending = input.lessons.filter((l) => !l.doneBefore);
  // ⭐ first, then curriculum order (stable)
  const ordered = [...pending.filter((l) => l.isImportant), ...pending.filter((l) => !l.isImportant)];
  const hasRevisionDay = span >= 2;
  const studyDays = hasRevisionDay ? span - 1 : span;
  const days: GenDay[] = Array.from({ length: span }, (_, i) => ({ date: addDays(today, i), items: [], totalMinutes: 0, isRevision: hasRevisionDay && i === span - 1 }));

  // balanced greedy fill: each day gets ≈ total / studyDays minutes; a day always takes at least one lesson
  const total = ordered.reduce((a, l) => a + effectiveMinutes(l), 0);
  const target = studyDays ? total / studyDays : total;
  let d = 0;
  let acc = 0;
  for (const l of ordered) {
    const m = effectiveMinutes(l);
    const day = days[d]!;
    // move to the next study day once today's budget is reached (never past the last study day)
    if (day.items.length > 0 && acc + m / 2 > target * (d + 1) && d < studyDays - 1) d++;
    const into = days[d]!;
    into.items.push({ kind: 'lesson', lesson: l, done: l.doneToday });
    into.totalMinutes += m;
    acc += m;
  }

  // revision day (or the only day): important recap + mocks + flashcards
  const last = days[days.length - 1]!;
  if (hasRevisionDay) last.items.push({ kind: 'revision', done: false });
  const untried = input.mocks.filter((m) => !m.attempted);
  for (const m of untried.length ? untried : input.mocks.slice(0, 1)) last.items.push({ kind: 'mock', mock: m, done: m.attempted });
  if (input.flashcards.total > 0) last.items.push({ kind: 'flashcards', count: input.flashcards.total, done: input.flashcards.known >= input.flashcards.total });
  // with many days left, put a first mock midway so students get early feedback
  if (span >= 6 && untried.length > 1) {
    const mid = days[Math.floor(studyDays / 2)]!;
    const m = untried[0]!;
    mid.items.push({ kind: 'mock', mock: m, done: m.attempted });
    last.items = last.items.filter((x) => !(x.kind === 'mock' && x.mock.id === m.id));
  }
  return days;
}

/**
 * Readiness (0–100): lessons 60 % · mock score 30 % · flashcards 10 %.
 * Components without content (no mock tests / no flashcards) are dropped and the weights renormalised.
 */
export function readiness(r: { lessonsDone: number; lessonsTotal: number; mockScore: number | null; flashKnown: number; flashTotal: number }) {
  const parts: [number, number][] = [];
  const lessons = r.lessonsTotal ? Math.round((r.lessonsDone / r.lessonsTotal) * 100) : 0;
  parts.push([lessons, 60]);
  const mock = r.mockScore;
  if (mock !== null) parts.push([mock, 30]);
  const flash = r.flashTotal ? Math.round((r.flashKnown / r.flashTotal) * 100) : null;
  if (flash !== null) parts.push([flash, 10]);
  const w = parts.reduce((a, [, x]) => a + x, 0);
  const total = Math.round(parts.reduce((a, [v, x]) => a + v * x, 0) / w);
  return { total: Math.max(0, Math.min(100, total)), lessons, mock, flashcards: flash };
}
