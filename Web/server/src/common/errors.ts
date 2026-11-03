import type { ErrorCode } from '@shikkhasathi/shared';

const STATUS: Record<ErrorCode, number> = {
  BAD_REQUEST: 400,
  VALIDATION_FAILED: 422,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  RATE_LIMITED: 429,
  PAYMENT_FAILED: 402,
  INTEGRATION_DISABLED: 503,
  CLASSROOM_REAUTH: 409,
  CLASSROOM_WORKSPACE_BLOCKED: 409,
  CLASSROOM_QUOTA: 429,
  INTERNAL: 500,
};

/** Default Bangla messages — friendly and actionable (spec §14). */
const MESSAGE: Record<ErrorCode, string> = {
  BAD_REQUEST: 'অনুরোধটি ঠিক বোঝা যায়নি।',
  VALIDATION_FAILED: 'কিছু তথ্য ঠিক নেই, একবার দেখে নিন।',
  UNAUTHORIZED: 'অনুগ্রহ করে আবার লগইন করুন।',
  FORBIDDEN: 'এই কাজের অনুমতি আপনার নেই।',
  NOT_FOUND: 'যা খুঁজছেন তা পাওয়া যায়নি।',
  CONFLICT: 'এটি আগেই করা হয়েছে।',
  RATE_LIMITED: 'একটু বেশি চেষ্টা হয়ে গেছে। কিছুক্ষণ পর আবার চেষ্টা করুন।',
  PAYMENT_FAILED: 'পেমেন্ট সম্পন্ন হয়নি। চিন্তা নেই, আপনার টাকা কাটা হয়নি।',
  INTEGRATION_DISABLED: 'এই সার্ভিসটি এখন চালু নেই।',
  CLASSROOM_REAUTH: 'Google Classroom-এর সংযোগ বিচ্ছিন্ন হয়েছে। আবার সংযুক্ত করুন।',
  CLASSROOM_WORKSPACE_BLOCKED: 'আপনার বিশ্ববিদ্যালয়ের Google Workspace বাইরের অ্যাপ বন্ধ রেখেছে।',
  CLASSROOM_QUOTA: 'Google এখন ব্যস্ত। একটু পরে আবার সিঙ্ক হবে।',
  INTERNAL: 'দুঃখিত, আমাদের দিক থেকে সমস্যা হয়েছে। আবার চেষ্টা করুন।',
};

export class AppError extends Error {
  readonly status: number;
  constructor(
    readonly code: ErrorCode,
    message?: string,
    readonly details?: unknown,
  ) {
    super(message ?? MESSAGE[code]);
    this.status = STATUS[code];
  }
}

export const badRequest = (m?: string, d?: unknown) => new AppError('BAD_REQUEST', m, d);
export const unauthorized = (m?: string) => new AppError('UNAUTHORIZED', m);
export const forbidden = (m?: string) => new AppError('FORBIDDEN', m);
export const notFound = (m?: string) => new AppError('NOT_FOUND', m);
export const conflict = (m?: string, d?: unknown) => new AppError('CONFLICT', m, d);
export const rateLimited = (m?: string, d?: unknown) => new AppError('RATE_LIMITED', m, d);
export const validationFailed = (m?: string, d?: unknown) => new AppError('VALIDATION_FAILED', m, d);
