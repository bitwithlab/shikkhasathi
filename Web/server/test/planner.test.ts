import { describe, expect, it } from 'vitest';
import { generatePlan, readiness, type PlanLesson } from '../src/modules/exam/planner';

const L = (i: number, o: Partial<PlanLesson> = {}): PlanLesson => ({ id: `l${i}`, courseId: 'c', title: `Lesson ${i}`, chapterTitle: 'Ch', durationSec: 600, type: 'video', isImportant: false, doneBefore: false, doneToday: false, ...o });

describe('study planner', () => {
  it('spreads remaining lessons over study days, keeps the last day for revision, ⭐ first', () => {
    const lessons = Array.from({ length: 10 }, (_, i) => L(i, { isImportant: i === 7 || i === 8 }));
    const days = generatePlan({ today: '2026-11-03', examDay: '2026-11-09', lessons, mocks: [{ id: 'm1', title: 'Mock 1', questionCount: 20, attempted: false }], flashcards: { total: 30, known: 5 } });
    expect(days).toHaveLength(6);
    expect(days.map((d) => d.date)).toEqual(['2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07', '2026-11-08']);
    const rev = days.at(-1)!;
    expect(rev.isRevision).toBe(true);
    expect(rev.items.some((i) => i.kind === 'revision')).toBe(true);
    expect(rev.items.some((i) => i.kind === 'mock')).toBe(true);
    expect(rev.items.some((i) => i.kind === 'flashcards')).toBe(true);
    expect(rev.items.some((i) => i.kind === 'lesson')).toBe(false);
    const first = days[0]!.items.filter((i) => i.kind === 'lesson');
    expect(first[0]!.kind === 'lesson' && first[0]!.lesson.id).toBe('l7');
    const planned = days.flatMap((d) => d.items.filter((i) => i.kind === 'lesson'));
    expect(planned).toHaveLength(10);
    const mins = days.slice(0, 5).map((d) => d.totalMinutes);
    expect(Math.max(...mins) - Math.min(...mins)).toBeLessThanOrEqual(10);
  });

  it('excludes lessons done before today; keeps today’s completed ones ticked', () => {
    const days = generatePlan({ today: '2026-11-03', examDay: '2026-11-05', lessons: [L(1, { doneBefore: true }), L(2, { doneToday: true }), L(3)], mocks: [], flashcards: { total: 0, known: 0 } });
    const all = days.flatMap((d) => d.items);
    expect(all.find((i) => i.kind === 'lesson' && i.lesson.id === 'l1')).toBeUndefined();
    expect(days[0]!.items[0]).toMatchObject({ kind: 'lesson', done: true });
  });

  it('exam tomorrow: a single day with everything; exam today or past: no plan', () => {
    const one = generatePlan({ today: '2026-11-03', examDay: '2026-11-04', lessons: [L(1), L(2)], mocks: [{ id: 'm', title: 'M', questionCount: 5, attempted: true }], flashcards: { total: 0, known: 0 } });
    expect(one).toHaveLength(1);
    expect(one[0]!.isRevision).toBe(false);
    expect(one[0]!.items.filter((i) => i.kind === 'lesson')).toHaveLength(2);
    expect(generatePlan({ today: '2026-11-04', examDay: '2026-11-04', lessons: [L(1)], mocks: [], flashcards: { total: 0, known: 0 } })).toEqual([]);
  });

  it('text lessons get a 10-minute reading budget', () => {
    const days = generatePlan({ today: '2026-11-03', examDay: '2026-11-04', lessons: [L(1, { durationSec: 0, type: 'text' })], mocks: [], flashcards: { total: 0, known: 0 } });
    expect(days[0]!.totalMinutes).toBe(10);
  });

  it('readiness weights 60/30/10 and renormalises missing parts', () => {
    expect(readiness({ lessonsDone: 5, lessonsTotal: 10, mockScore: 80, flashKnown: 10, flashTotal: 10 }).total).toBe(Math.round(50 * 0.6 + 80 * 0.3 + 100 * 0.1));
    expect(readiness({ lessonsDone: 5, lessonsTotal: 10, mockScore: null, flashKnown: 0, flashTotal: 0 }).total).toBe(50);
    expect(readiness({ lessonsDone: 10, lessonsTotal: 10, mockScore: null, flashKnown: 0, flashTotal: 10 })).toMatchObject({ total: Math.round((100 * 60 + 0 * 10) / 70), mock: null, flashcards: 0 });
    expect(readiness({ lessonsDone: 0, lessonsTotal: 0, mockScore: null, flashKnown: 0, flashTotal: 0 }).total).toBe(0);
  });
});
