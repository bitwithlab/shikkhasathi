/**
 * Course ranking (spec §6.5) — pure and deterministic.
 *
 * score = match_weight              100 own inst+dept+semester+batch · 80 inst+dept+semester · 50 same subject other inst · 30 general · 10 related
 *       + 4 × rating_bayesian       0–5
 *       + exam_proximity_boost      up to +40 when the student's exam for that subject is ≤ 14 days and the course exam type matches
 *       + log(1 + enrollments_30d) × 5
 *       + 5 if seller verified
 *       + freshness                 up to +5 for courses updated in the last 60 days
 */

export interface RankTarget {
  isGeneral: boolean;
  institutionId: string | null;
  departmentId: string | null;
  semester: number | null;
  subjectMasterId: string | null;
  batches: string[];
}

export interface RankCourse {
  examType: string;
  ratingAvg: number;
  ratingCount: number;
  enrollments30d: number;
  sellerVerified: boolean;
  updatedAt: Date;
  targets: RankTarget[];
}

export interface RankStudent {
  institutionId: string | null;
  departmentId: string | null;
  semester: number | null;
  batch: string | null;
  /** subjects the student is studying (from their institution's subjects for their semester), for "related" */
  subjectIds: string[];
  exams: { subjectMasterId: string | null; examType: string; examDate: Date }[];
}

export const BAYES_PRIOR = 4.0;
export const BAYES_WEIGHT = 10;

export function bayesianRating(avg: number, count: number, prior = BAYES_PRIOR, weight = BAYES_WEIGHT) {
  if (count <= 0) return prior;
  return (prior * weight + avg * count) / (weight + count);
}

export function matchWeight(c: Pick<RankCourse, 'targets'>, s: RankStudent | null): number {
  if (!s) return c.targets.some((t) => t.isGeneral) ? 30 : 10;
  let best = 0;
  for (const t of c.targets) {
    const sameInstDept = !!s.institutionId && t.institutionId === s.institutionId && !!s.departmentId && t.departmentId === s.departmentId;
    const sameSubject = !!t.subjectMasterId && s.subjectIds.includes(t.subjectMasterId);
    let w: number;
    if (sameInstDept && s.semester !== null && t.semester === s.semester) w = s.batch && t.batches.includes(s.batch) ? 100 : 80;
    else if (sameInstDept) w = 60; // own programme, another semester
    else if (sameSubject) w = 50; // same subject, other institution (or general)
    else if (t.isGeneral) w = 30;
    else w = 10;
    best = Math.max(best, w);
  }
  return best;
}

export function examProximityBoost(c: Pick<RankCourse, 'targets' | 'examType'>, s: RankStudent | null, now: Date): number {
  if (!s) return 0;
  let best = 0;
  const subjects = new Set(c.targets.map((t) => t.subjectMasterId).filter(Boolean));
  for (const e of s.exams) {
    if (!e.subjectMasterId || !subjects.has(e.subjectMasterId)) continue;
    if (e.examType !== c.examType) continue;
    const days = (e.examDate.getTime() - now.getTime()) / 86_400_000;
    if (days < 0 || days > 14) continue;
    best = Math.max(best, 40 * (1 - days / 15));
  }
  return best;
}

export const popularity = (enrollments30d: number) => Math.log(1 + Math.max(0, enrollments30d)) * 5;

export function freshness(updatedAt: Date, now: Date) {
  const days = (now.getTime() - updatedAt.getTime()) / 86_400_000;
  return days < 0 ? 5 : Math.max(0, 5 * (1 - days / 60));
}

export function rankScore(c: RankCourse, s: RankStudent | null, now = new Date()) {
  return (
    matchWeight(c, s) +
    4 * bayesianRating(c.ratingAvg, c.ratingCount) +
    examProximityBoost(c, s, now) +
    popularity(c.enrollments30d) +
    (c.sellerVerified ? 5 : 0) +
    freshness(c.updatedAt, now)
  );
}

/** Compatibility badge: ✅ when a target matches the student's institution + department. */
export function compatibility(targets: RankTarget[], s: Pick<RankStudent, 'institutionId' | 'departmentId'> | null): 'match' | 'mismatch' | 'general' {
  if (targets.length > 0 && targets.every((t) => t.isGeneral)) return 'general';
  // unknown student (guest / no academic profile): never claim a mismatch we can't know
  if (!s?.institutionId) return 'general';
  if (targets.some((t) => t.institutionId === s.institutionId && (!s.departmentId || !t.departmentId || t.departmentId === s.departmentId))) return 'match';
  return targets.some((t) => t.isGeneral) ? 'general' : 'mismatch';
}
