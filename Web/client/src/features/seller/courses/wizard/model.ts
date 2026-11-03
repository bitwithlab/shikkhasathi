import type { SellerCourse } from '@shikkhasathi/shared';
import type { COURSE_TYPES, EXAM_TYPES } from '@shikkhasathi/shared/types';
import { newKey } from '../../lib/curriculum';
import { paisaToTaka, takaToPaisa } from '../../parts';

export type ExamType = (typeof EXAM_TYPES)[number];
export type CourseType = (typeof COURSE_TYPES)[number];

export interface Basics {
  title: string;
  shortDescription: string;
  description: string;
  learnings: string[];
  notFor: string[];
  faq: { q: string; a: string }[];
  coverUrl: string | null;
  coverColor: string | null;
  language: 'bn' | 'en' | 'mixed';
  examType: ExamType;
  courseType: CourseType;
}

export interface Pricing {
  free: boolean;
  price: string;
  compare: string;
  accessType: 'lifetime' | 'days' | 'until_exam';
  accessDays: string;
  /** yyyy-mm-dd (Asia/Dhaka) */
  accessUntil: string;
}

export interface TargetDraft {
  key: string;
  isGeneral: boolean;
  institutionId: string | null;
  institutionShort: string | null;
  institutionName: string | null;
  departmentId: string | null;
  programId: string | null;
  semester: number | null;
  subjectMasterId: string | null;
  subjectName: string | null;
  courseCode: string;
  batches: string[];
  sections: string[];
}

export const COVER_COLORS = ['#4F46E5', '#0EA5E9', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#14B8A6', '#F97316'];

export const basicsFrom = (c: SellerCourse): Basics => ({
  title: c.title,
  shortDescription: c.shortDescription,
  description: c.description,
  learnings: c.learnings.length ? c.learnings : [''],
  notFor: c.notFor.length ? c.notFor : [''],
  faq: c.faq,
  coverUrl: c.coverUrl ?? null,
  coverColor: c.coverColor ?? null,
  language: c.language,
  examType: c.examType,
  courseType: c.courseType,
});

export function basicsBody(b: Basics) {
  const list = (xs: string[], max: number) => xs.map((x) => x.trim().slice(0, 200)).filter(Boolean).slice(0, max);
  return {
    ...(b.title.trim().length >= 5 ? { title: b.title.trim() } : {}),
    shortDescription: b.shortDescription.trim(),
    description: b.description.trim(),
    learnings: list(b.learnings, 15),
    notFor: list(b.notFor, 10),
    faq: b.faq.map((f) => ({ q: f.q.trim(), a: f.a.trim() })).filter((f) => f.q && f.a).slice(0, 15),
    coverUrl: b.coverUrl,
    coverColor: b.coverColor,
    language: b.language,
    examType: b.examType,
    courseType: b.courseType,
  };
}

const dhakaDate = (iso: string) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(new Date(iso));

export const pricingFrom = (c: SellerCourse): Pricing => ({
  free: c.price === 0,
  price: c.price === 0 ? '' : paisaToTaka(c.price),
  compare: paisaToTaka(c.compareAtPrice ?? null),
  accessType: c.accessType,
  accessDays: c.accessDays ? String(c.accessDays) : '',
  accessUntil: c.accessUntil ? dhakaDate(c.accessUntil) : '',
});

export type PricingErrors = Partial<Record<'price' | 'compare' | 'accessDays' | 'accessUntil', string>>;

/** Validate the pricing draft → PATCH body (paisa) or field errors (i18n keys). */
export function pricingBody(p: Pricing): { body: { price: number; compareAtPrice: number | null; accessType: Pricing['accessType']; accessDays: number | null; accessUntil: string | null } | null; errors: PricingErrors } {
  const errors: PricingErrors = {};
  const price = p.free ? 0 : takaToPaisa(p.price);
  const compare = p.free ? null : takaToPaisa(p.compare);
  if (price == null) errors.price = 'wiz.price.err.required';
  else if (Number.isNaN(price) || price > 5_000_000) errors.price = 'wiz.price.err.invalid';
  else if (price > 0 && price < 5000) errors.price = 'wiz.price.err.min';
  if (compare != null && (Number.isNaN(compare) || compare > 5_000_000)) errors.compare = 'wiz.price.err.invalid';
  else if (compare != null && price != null && !Number.isNaN(price) && compare <= price) errors.compare = 'wiz.price.err.compare';
  let accessDays: number | null = null;
  let accessUntil: string | null = null;
  if (p.accessType === 'days') {
    const d = Number(p.accessDays);
    if (!Number.isInteger(d) || d < 1 || d > 3650) errors.accessDays = 'wiz.price.err.days';
    else accessDays = d;
  }
  if (p.accessType === 'until_exam') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(p.accessUntil)) errors.accessUntil = 'wiz.price.err.until';
    else accessUntil = `${p.accessUntil}T23:59:59+06:00`;
  }
  // price under ৳50 is a checklist warning, not a save blocker
  const blocking = Object.entries(errors).some(([k, v]) => !(k === 'price' && v === 'wiz.price.err.min'));
  if (blocking) return { body: null, errors };
  return { body: { price: price!, compareAtPrice: compare ?? null, accessType: p.accessType, accessDays, accessUntil }, errors };
}

export function targetsFrom(c: SellerCourse): TargetDraft[] {
  if (!c.targets.length) return [emptyTarget()];
  return c.targets.map((x) => ({
    key: x.id,
    isGeneral: x.isGeneral,
    institutionId: x.institutionId ?? null,
    institutionShort: x.institutionShort,
    institutionName: null,
    departmentId: x.departmentId ?? null,
    programId: x.programId ?? null,
    semester: x.semester ?? null,
    subjectMasterId: x.subjectMasterId ?? null,
    subjectName: x.subjectName,
    courseCode: x.courseCode ?? '',
    batches: x.batches,
    sections: x.sections,
  }));
}

export const emptyTarget = (): TargetDraft => ({ key: newKey(), isGeneral: false, institutionId: null, institutionShort: null, institutionName: null, departmentId: null, programId: null, semester: null, subjectMasterId: null, subjectName: null, courseCode: '', batches: [], sections: [] });

export type TargetIssue = 'institution' | 'subject' | null;
export function targetIssue(t: TargetDraft): TargetIssue {
  if (!t.isGeneral && !t.institutionId) return 'institution';
  if (!t.subjectMasterId && !t.courseCode.trim()) return 'subject';
  return null;
}

export const targetsBody = (ts: TargetDraft[]) => ({
  targets: ts.map((x) => ({
    isGeneral: x.isGeneral,
    institutionId: x.isGeneral ? null : x.institutionId,
    departmentId: x.isGeneral ? null : x.departmentId,
    programId: x.isGeneral ? null : x.programId,
    semester: x.semester,
    subjectMasterId: x.subjectMasterId,
    courseCode: x.courseCode.trim() ? x.courseCode.trim().toUpperCase().replace(/\s+/g, '-').slice(0, 30) : null,
    batches: x.batches,
    sections: x.sections,
  })),
});
