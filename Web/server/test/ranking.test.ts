import { describe, expect, it } from 'vitest';
import { bayesianRating, compatibility, examProximityBoost, freshness, matchWeight, popularity, rankScore, type RankCourse, type RankStudent } from '../src/modules/search/ranking';
import { expandQuery, normalizeCode } from '../src/modules/search/synonyms';

const MU = 'mu', CSE = 'cse', DS = 'ds', ALGO = 'algo';
const student: RankStudent = { institutionId: MU, departmentId: CSE, semester: 4, batch: '56', subjectIds: [DS], exams: [] };
const T = (o: Partial<RankCourse['targets'][number]>) => ({ isGeneral: false, institutionId: null, departmentId: null, semester: null, subjectMasterId: null, batches: [], ...o });
const course = (o: Partial<RankCourse> = {}): RankCourse => ({ examType: 'midterm', ratingAvg: 4.5, ratingCount: 50, enrollments30d: 20, sellerVerified: true, updatedAt: new Date('2026-11-01'), targets: [T({ institutionId: MU, departmentId: CSE, semester: 4, subjectMasterId: DS, batches: ['56'] })], ...o });

describe('ranking (spec §6.5)', () => {
  it('match weights 100 / 80 / 50 / 30 / 10', () => {
    expect(matchWeight(course(), student)).toBe(100);
    expect(matchWeight(course({ targets: [T({ institutionId: MU, departmentId: CSE, semester: 4, subjectMasterId: DS })] }), student)).toBe(80);
    expect(matchWeight(course({ targets: [T({ institutionId: 'nsu', departmentId: 'x', semester: 4, subjectMasterId: DS })] }), student)).toBe(50);
    expect(matchWeight(course({ targets: [T({ isGeneral: true, subjectMasterId: ALGO })] }), student)).toBe(30);
    expect(matchWeight(course({ targets: [T({ institutionId: 'nsu', subjectMasterId: ALGO })] }), student)).toBe(10);
    expect(matchWeight(course({ targets: [T({ institutionId: 'nsu', subjectMasterId: ALGO }), T({ institutionId: MU, departmentId: CSE, semester: 4, subjectMasterId: DS })] }), student)).toBe(80);
  });
  it('bayesian rating pulls small samples toward the prior', () => {
    expect(bayesianRating(5, 1)).toBeCloseTo((4 * 10 + 5) / 11, 5);
    expect(bayesianRating(5, 1000)).toBeGreaterThan(4.98);
    expect(bayesianRating(0, 0)).toBe(4);
  });
  it('exam proximity boost only for matching subject + exam type within 14 days', () => {
    const now = new Date('2026-11-03T00:00:00Z');
    const s = { ...student, exams: [{ subjectMasterId: DS, examType: 'midterm', examDate: new Date('2026-11-06T00:00:00Z') }] };
    expect(examProximityBoost(course(), s, now)).toBeCloseTo(40 * (1 - 3 / 15), 5);
    expect(examProximityBoost(course({ examType: 'final' }), s, now)).toBe(0);
    expect(examProximityBoost(course(), { ...s, exams: [{ ...s.exams[0]!, examDate: new Date('2026-11-30') }] }, now)).toBe(0);
  });
  it('popularity, freshness, verified bonus', () => {
    expect(popularity(0)).toBe(0);
    expect(popularity(Math.E - 1)).toBeCloseTo(5, 5);
    const now = new Date('2026-11-03');
    expect(freshness(now, now)).toBe(5);
    expect(freshness(new Date('2026-08-01'), now)).toBe(0);
    const a = rankScore(course(), student, now);
    const b = rankScore(course({ sellerVerified: false }), student, now);
    expect(a - b).toBeCloseTo(5, 5);
  });
  it('own-syllabus course outranks a more popular other-university course', () => {
    const now = new Date('2026-11-03');
    const own = rankScore(course({ ratingAvg: 4.2, enrollments30d: 10 }), student, now);
    const other = rankScore(course({ targets: [T({ institutionId: 'nsu', departmentId: 'x', subjectMasterId: DS })], ratingAvg: 5, ratingCount: 500, enrollments30d: 400 }), student, now);
    expect(own).toBeGreaterThan(other);
  });
  it('compatibility badge', () => {
    expect(compatibility(course().targets, student)).toBe('match');
    expect(compatibility([T({ institutionId: 'nsu', departmentId: 'x' })], student)).toBe('mismatch');
    expect(compatibility([T({ isGeneral: true })], student)).toBe('general');
    expect(compatibility([T({ institutionId: MU, departmentId: CSE })], null)).toBe('mismatch');
  });
});

describe('query expansion', () => {
  it('Bangla, English and abbreviation map to one subject', () => {
    for (const q of ['DS', 'ডাটা স্ট্রাকচার', 'data structure']) expect(expandQuery(q).terms).toContain('data structures');
    expect(expandQuery('DS').interpreted[0]).toEqual({ term: 'ds', meaning: 'Data Structures' });
  });
  it('extracts course codes and exam words', () => {
    const e = expandQuery('cse 2203 midterm');
    expect(e.code).toBe('CSE2203');
    expect(e.examType).toBe('midterm');
    expect(normalizeCode('CSE-2203')).toBe(normalizeCode('cse2203'));
  });
});
