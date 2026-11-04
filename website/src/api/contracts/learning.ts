import { z } from 'zod';
import { CursorQuery, IdParams, Ok, endpoint, page } from './common';
import { LESSON_TYPES } from '@/types';

// ---------------------------------------------------------------- My Courses

export const MyCourse = z.object({
  courseId: z.uuid(),
  slug: z.string(),
  title: z.string(),
  courseCode: z.string().nullable(),
  institutionShort: z.string().nullable(),
  examType: z.string(),
  sellerName: z.string(),
  coverColor: z.string().nullable(),
  progressPercent: z.number().int(),
  completedLessons: z.number().int(),
  totalLessons: z.number().int(),
  resumeLessonId: z.uuid().nullable(),
  resumeLessonTitle: z.string().nullable(),
  lastAccessedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  accessUntil: z.string().nullable(),
  /** access ends within 7 days */
  expiringSoon: z.boolean(),
  expired: z.boolean(),
});
export type MyCourse = z.infer<typeof MyCourse>;

// ---------------------------------------------------------------- Player

export const PlayerLesson = z.object({
  id: z.uuid(),
  title: z.string(),
  type: z.enum(LESSON_TYPES),
  durationSec: z.number().int(),
  isPreview: z.boolean(),
  isImportant: z.boolean(),
  completed: z.boolean(),
  lastPositionSec: z.number().int(),
  /** not enrolled and not a preview */
  locked: z.boolean(),
});
export const PlayerOutline = z.object({
  course: z.object({ id: z.uuid(), slug: z.string(), title: z.string(), courseCode: z.string().nullable(), sellerId: z.uuid(), sellerName: z.string(), sellerSlug: z.string().nullable() }),
  enrolled: z.boolean(),
  isOwner: z.boolean(),
  accessUntil: z.string().nullable(),
  progressPercent: z.number().int(),
  completedLessons: z.number().int(),
  totalLessons: z.number().int(),
  resumeLessonId: z.uuid().nullable(),
  sections: z.array(z.object({ id: z.uuid(), title: z.string(), chapters: z.array(z.object({ id: z.uuid(), title: z.string(), lessons: z.array(PlayerLesson) })) })),
  resources: z.array(z.object({ id: z.uuid(), kind: z.string(), title: z.string(), year: z.number().int().nullable() })),
  myReview: z.object({ id: z.uuid(), rating: z.number().int(), body: z.string().nullable() }).nullable(),
  /** enrolled and ≥ 20% progress */
  canReview: z.boolean(),
});
export type PlayerOutline = z.infer<typeof PlayerOutline>;

export const LessonDetail = z.object({
  id: z.uuid(),
  courseId: z.uuid(),
  title: z.string(),
  type: z.enum(LESSON_TYPES),
  durationSec: z.number().int(),
  isImportant: z.boolean(),
  body: z.string().nullable(),
  /** pdf lessons: short-lived signed URL */
  document: z.object({ id: z.uuid(), title: z.string(), url: z.string(), pages: z.number().int().nullable() }).nullable(),
  video: z.object({ kind: z.enum(['hls', 'youtube', 'external']), status: z.string() }).nullable(),
  completed: z.boolean(),
  lastPositionSec: z.number().int(),
  prevLessonId: z.uuid().nullable(),
  nextLessonId: z.uuid().nullable(),
});
export type LessonDetail = z.infer<typeof LessonDetail>;

export const PlaybackBody = z.object({ deviceId: z.string().min(8).max(80), deviceName: z.string().max(80).optional() });
export const Playback = z.object({
  kind: z.enum(['hls', 'youtube', 'external']),
  url: z.string(),
  expiresAt: z.string(),
  /** one active stream per user — send it with each heartbeat */
  streamToken: z.string(),
  watermark: z.string(),
  startAtSec: z.number().int(),
});
export type Playback = z.infer<typeof Playback>;

export const ProgressBody = z.object({ positionSec: z.number().int().min(0).max(86_400), deltaSec: z.number().int().min(0).max(60), streamToken: z.string().max(80).optional() });
export const ProgressResponse = z.object({ completed: z.boolean(), progressPercent: z.number().int(), streamActive: z.boolean() });

// ---------------------------------------------------------------- notes & bookmarks

export const Note = z.object({ id: z.uuid(), courseId: z.uuid(), lessonId: z.uuid(), lessonTitle: z.string(), courseTitle: z.string(), timestampSec: z.number().int().nullable(), body: z.string(), createdAt: z.string(), updatedAt: z.string() });
export const Bookmark = z.object({ id: z.uuid(), courseId: z.uuid(), lessonId: z.uuid(), lessonTitle: z.string(), courseTitle: z.string(), timestampSec: z.number().int().nullable(), body: z.string().nullable(), createdAt: z.string() });
export type Note = z.infer<typeof Note>;
export type Bookmark = z.infer<typeof Bookmark>;

// ---------------------------------------------------------------- Q&A

export const Answer = z.object({ id: z.uuid(), body: z.string(), authorName: z.string(), isSeller: z.boolean(), createdAt: z.string() });
export const Question = z.object({ id: z.uuid(), lessonId: z.uuid(), lessonTitle: z.string().nullable(), body: z.string(), timestampSec: z.number().int().nullable(), authorName: z.string(), mine: z.boolean(), status: z.enum(['open', 'answered', 'hidden']), createdAt: z.string(), answers: z.array(Answer) });
export type Question = z.infer<typeof Question>;

// ---------------------------------------------------------------- reviews

export const Review = z.object({ id: z.uuid(), rating: z.number().int().min(1).max(5), body: z.string().nullable(), authorName: z.string(), createdAt: z.string(), sellerReply: z.string().nullable(), sellerRepliedAt: z.string().nullable(), verified: z.boolean() });
export type Review = z.infer<typeof Review>;
export const ReviewBody = z.object({ rating: z.number().int().min(1).max(5), body: z.string().trim().max(2000).optional() });

const CourseIdParams = z.object({ courseId: z.uuid() });

export const learning = {
  myCourses: endpoint({ method: 'GET', path: '/me/courses', summary: 'My Courses with progress (filter in progress / completed / expiring)', tag: 'learning', auth: 'user', query: z.object({ filter: z.enum(['all', 'in_progress', 'completed', 'expiring']).default('all') }), response: z.object({ items: z.array(MyCourse) }) }),
  outline: endpoint({ method: 'GET', path: '/learn/:courseId', summary: 'Player outline: curriculum with progress + lock state', tag: 'learning', auth: 'user', params: CourseIdParams, response: PlayerOutline }),
  lesson: endpoint({ method: 'GET', path: '/lessons/:id', summary: 'Lesson content (enrolled, preview, or owner)', tag: 'learning', auth: 'user', params: IdParams, response: LessonDetail }),
  playback: endpoint({ method: 'POST', path: '/lessons/:id/playback', summary: 'Signed playback URL (≤ 2 h), device limit 3, one stream at a time', tag: 'learning', auth: 'user', params: IdParams, body: PlaybackBody, response: Playback }),
  progress: endpoint({ method: 'POST', path: '/lessons/:id/progress', summary: 'Heartbeat (~15 s): position + watched delta; auto-complete at 90%', tag: 'learning', auth: 'user', params: IdParams, body: ProgressBody, response: ProgressResponse }),
  complete: endpoint({ method: 'POST', path: '/lessons/:id/complete', summary: 'Mark lesson complete / not complete', tag: 'learning', auth: 'user', params: IdParams, body: z.object({ completed: z.boolean() }), response: ProgressResponse }),

  document: endpoint({ method: 'GET', path: '/documents/:id/url', summary: 'Signed URL for a course resource (enrolled / owner)', tag: 'learning', auth: 'user', params: IdParams, response: z.object({ url: z.string(), title: z.string() }) }),

  notes: endpoint({ method: 'GET', path: '/notes', summary: 'My notes (search, course filter)', tag: 'learning', auth: 'user', query: CursorQuery.extend({ courseId: z.uuid().optional(), lessonId: z.uuid().optional(), q: z.string().max(80).optional() }), response: page(Note) }),
  createNote: endpoint({ method: 'POST', path: '/notes', summary: 'Add a timestamped note', tag: 'learning', auth: 'user', body: z.object({ lessonId: z.uuid(), timestampSec: z.number().int().min(0).nullable().optional(), body: z.string().trim().min(1).max(5000) }), response: Note, status: 201 }),
  updateNote: endpoint({ method: 'PATCH', path: '/notes/:id', summary: 'Edit note', tag: 'learning', auth: 'user', params: IdParams, body: z.object({ body: z.string().trim().min(1).max(5000) }), response: Note }),
  deleteNote: endpoint({ method: 'DELETE', path: '/notes/:id', summary: 'Delete note', tag: 'learning', auth: 'user', params: IdParams, response: Ok }),
  bookmarks: endpoint({ method: 'GET', path: '/bookmarks', summary: 'My bookmarks', tag: 'learning', auth: 'user', query: CursorQuery.extend({ courseId: z.uuid().optional(), lessonId: z.uuid().optional() }), response: page(Bookmark) }),
  createBookmark: endpoint({ method: 'POST', path: '/bookmarks', summary: 'Bookmark a moment / lesson', tag: 'learning', auth: 'user', body: z.object({ lessonId: z.uuid(), timestampSec: z.number().int().min(0).nullable().optional(), body: z.string().trim().max(200).optional() }), response: Bookmark, status: 201 }),
  deleteBookmark: endpoint({ method: 'DELETE', path: '/bookmarks/:id', summary: 'Remove bookmark', tag: 'learning', auth: 'user', params: IdParams, response: Ok }),

  questions: endpoint({ method: 'GET', path: '/lessons/:id/questions', summary: 'Lesson Q&A', tag: 'learning', auth: 'user', params: IdParams, query: CursorQuery, response: page(Question) }),
  ask: endpoint({ method: 'POST', path: '/lessons/:id/questions', summary: 'Ask a doubt (notifies teacher)', tag: 'learning', auth: 'user', params: IdParams, body: z.object({ body: z.string().trim().min(5).max(3000), timestampSec: z.number().int().min(0).nullable().optional() }), response: Question, status: 201 }),
  answer: endpoint({ method: 'POST', path: '/questions/:id/answers', summary: 'Answer (teacher or enrolled student; notifies asker)', tag: 'learning', auth: 'user', params: IdParams, body: z.object({ body: z.string().trim().min(2).max(5000) }), response: Question, status: 201 }),

  reviews: endpoint({ method: 'GET', path: '/courses/:courseId/reviews', summary: 'Course reviews (public)', tag: 'courses', auth: 'optional', params: CourseIdParams, query: CursorQuery.extend({ rating: z.coerce.number().int().min(1).max(5).optional() }), response: page(Review) }),
  review: endpoint({ method: 'PUT', path: '/courses/:courseId/review', summary: 'Write / edit my review (verified purchase, ≥ 20% progress)', tag: 'learning', auth: 'user', params: CourseIdParams, body: ReviewBody, response: Review }),
};

export const SellerQuestion = Question.extend({ courseId: z.uuid(), courseTitle: z.string() });

export const sellerLearning = {
  questions: endpoint({ method: 'GET', path: '/seller/questions', summary: 'Q&A inbox across my courses', tag: 'seller', auth: 'seller', query: CursorQuery.extend({ status: z.enum(['open', 'answered', 'all']).default('open') }), response: page(SellerQuestion) }),
  reviews: endpoint({ method: 'GET', path: '/seller/reviews', summary: 'Reviews across my courses', tag: 'seller', auth: 'seller', query: CursorQuery, response: page(Review.extend({ courseId: z.uuid(), courseTitle: z.string() })) }),
  replyReview: endpoint({ method: 'PUT', path: '/seller/reviews/:id/reply', summary: 'Reply to a review (once)', tag: 'seller', auth: 'seller', params: IdParams, body: z.object({ reply: z.string().trim().min(2).max(2000) }), response: Review }),
};
