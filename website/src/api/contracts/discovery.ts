import { z } from 'zod';
import { CursorQuery, IdParams, Ok, SlugParams, endpoint, page } from './common';
import { RecommendedCourse } from './classroom';
import { COURSE_REQUEST_STATUSES, COURSE_TYPES, EXAM_TYPES, LESSON_TYPES, SEARCH_SORTS } from '@/types';

/** Course card everywhere (search, browse, recommendations, storefront). */
export const CourseCardDto = RecommendedCourse.extend({
  shortDescription: z.string(),
  courseType: z.enum(COURSE_TYPES),
  lessonCount: z.number().int(),
  totalDurationSec: z.number().int(),
  enrollmentCount: z.number().int(),
  sellerSlug: z.string().nullable(),
  score: z.number().optional(),
});
export type CourseCardDto = z.infer<typeof CourseCardDto>;

export const SearchQuery = z.object({
  q: z.string().trim().max(120).optional(),
  institutionId: z.uuid().optional(),
  departmentId: z.uuid().optional(),
  semester: z.coerce.number().int().min(1).max(16).optional(),
  subjectId: z.uuid().optional(),
  courseCode: z.string().trim().max(30).optional(),
  batch: z.string().trim().max(12).optional(),
  section: z.string().trim().max(12).optional(),
  examType: z.enum(EXAM_TYPES).optional(),
  courseType: z.enum(COURSE_TYPES).optional(),
  price: z.enum(['free', 'paid']).optional(),
  minPrice: z.coerce.number().int().min(0).optional(),
  maxPrice: z.coerce.number().int().min(0).optional(),
  language: z.enum(['bn', 'en', 'mixed']).optional(),
  rating: z.coerce.number().min(0).max(5).optional(),
  duration: z.enum(['short', 'medium', 'long']).optional(),
  sellerId: z.uuid().optional(),
  sort: z.enum(SEARCH_SORTS).default('best'),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(60).default(24),
});
export type SearchQuery = z.infer<typeof SearchQuery>;

export const SearchResponse = z.object({
  items: z.array(CourseCardDto),
  total: z.number().int(),
  nextCursor: z.string().nullable(),
  /** Search-term interpretation shown to the user, e.g. "DS" → "Data Structures". */
  interpreted: z.array(z.object({ term: z.string(), meaning: z.string() })),
  engine: z.enum(['meilisearch', 'postgres']),
});

export const Suggestion = z.object({ type: z.enum(['institution', 'subject', 'code', 'teacher', 'course']), id: z.string(), label: z.string(), sub: z.string().nullable(), href: z.string() });
export const SuggestResponse = z.object({ items: z.array(Suggestion) });

export const PublicLesson = z.object({ id: z.uuid(), title: z.string(), type: z.enum(LESSON_TYPES), durationSec: z.number().int(), isPreview: z.boolean(), isImportant: z.boolean() });
export const CourseDetail = CourseCardDto.extend({
  description: z.string(),
  learnings: z.array(z.string()),
  notFor: z.array(z.string()),
  faq: z.array(z.object({ q: z.string(), a: z.string() })),
  language: z.string(),
  accessType: z.string(),
  accessDays: z.number().int().nullable(),
  publishedAt: z.string().nullable(),
  updatedAt: z.string(),
  breadcrumb: z.array(z.object({ label: z.string(), href: z.string() })),
  targets: z.array(z.object({ label: z.string(), institutionSlug: z.string().nullable(), courseCode: z.string().nullable(), semester: z.number().int().nullable(), batches: z.array(z.string()) })),
  seller: z.object({ id: z.uuid(), slug: z.string(), name: z.string(), headline: z.string().nullable(), verified: z.boolean(), ratingAvg: z.number(), studentCount: z.number().int(), courseCount: z.number().int() }),
  sections: z.array(z.object({ id: z.uuid(), title: z.string(), chapters: z.array(z.object({ id: z.uuid(), title: z.string(), lessons: z.array(PublicLesson) })) })),
  resources: z.object({ videos: z.number().int(), notes: z.number().int(), previousQuestionYears: z.array(z.number().int()), mockTests: z.number().int(), flashcards: z.number().int() }),
  ratingDistribution: z.array(z.number().int()).length(5),
  viewer: z.object({ enrolled: z.boolean(), wishlisted: z.boolean(), firstLessonId: z.uuid().nullable(), resumeLessonId: z.uuid().nullable(), isOwner: z.boolean() }),
  status: z.string(),
});
export type CourseDetail = z.infer<typeof CourseDetail>;

export const Storefront = z.object({
  id: z.uuid(),
  slug: z.string(),
  name: z.string(),
  headline: z.string().nullable(),
  bio: z.string().nullable(),
  coverUrl: z.string().nullable(),
  verified: z.boolean(),
  institutionName: z.string().nullable(),
  institutionsTaught: z.array(z.string()),
  stats: z.object({ students: z.number().int(), courses: z.number().int(), ratingAvg: z.number(), ratingCount: z.number().int() }),
  courses: z.array(CourseCardDto),
});

export const discovery = {
  search: endpoint({ method: 'GET', path: '/search', summary: 'Smart search with filters, ranking (spec §6.5)', tag: 'search', auth: 'optional', query: SearchQuery, response: SearchResponse }),
  suggest: endpoint({ method: 'GET', path: '/search/suggest', summary: 'Autocomplete grouped by type', tag: 'search', auth: 'optional', query: z.object({ q: z.string().trim().min(1).max(80) }), response: SuggestResponse }),
  course: endpoint({ method: 'GET', path: '/courses/:slug', summary: 'Public course details', tag: 'courses', auth: 'optional', params: SlugParams, response: CourseDetail }),
  storefront: endpoint({ method: 'GET', path: '/teachers/:slug', summary: 'Teacher storefront', tag: 'courses', auth: 'optional', params: SlugParams, response: Storefront }),
  recommendations: endpoint({ method: 'GET', path: '/recommendations', summary: 'Courses for you', tag: 'dashboard', auth: 'user', response: z.object({ items: z.array(CourseCardDto) }) }),
  featured: endpoint({ method: 'GET', path: '/featured', summary: 'Landing: crash courses by university + verified teachers', tag: 'courses', auth: 'public', query: z.object({ institutionId: z.uuid().optional() }), response: z.object({ courses: z.array(CourseCardDto), teachers: z.array(z.object({ slug: z.string(), name: z.string(), headline: z.string().nullable(), institutionName: z.string().nullable(), ratingAvg: z.number(), studentCount: z.number().int(), courseCount: z.number().int() })), stats: z.object({ students: z.number().int(), courses: z.number().int(), institutions: z.number().int() }) }) }),
};

// ---------------------------------------------------------------- course request board

export const CourseRequest = z.object({
  id: z.uuid(),
  institutionId: z.uuid().nullable(),
  institutionShort: z.string().nullable(),
  subjectId: z.uuid().nullable(),
  subjectName: z.string(),
  courseCode: z.string().nullable(),
  examType: z.enum(EXAM_TYPES),
  note: z.string().nullable(),
  status: z.enum(COURSE_REQUEST_STATUSES),
  voteCount: z.number().int(),
  voted: z.boolean(),
  mine: z.boolean(),
  claimedByName: z.string().nullable(),
  linkedCourseSlug: z.string().nullable(),
  createdAt: z.string(),
});
export const CreateCourseRequest = z.object({
  institutionId: z.uuid().nullable(),
  subjectId: z.uuid().nullable().optional(),
  subjectName: z.string().trim().min(2).max(120),
  courseCode: z.string().trim().max(30).nullable().optional(),
  examType: z.enum(EXAM_TYPES),
  note: z.string().trim().max(500).optional(),
});

export const requests = {
  list: endpoint({ method: 'GET', path: '/course-requests', summary: 'Request board (most voted first)', tag: 'requests', auth: 'optional', query: CursorQuery.extend({ status: z.enum([...COURSE_REQUEST_STATUSES, 'all']).default('all'), institutionId: z.uuid().optional(), q: z.string().max(80).optional(), sort: z.enum(['votes', 'newest']).default('votes') }), response: page(CourseRequest) }),
  create: endpoint({ method: 'POST', path: '/course-requests', summary: 'Ask for a course (dedupes into an upvote)', tag: 'requests', auth: 'user', body: CreateCourseRequest, response: z.object({ request: CourseRequest, duplicate: z.boolean() }), status: 201 }),
  vote: endpoint({ method: 'POST', path: '/course-requests/:id/vote', summary: 'Toggle upvote', tag: 'requests', auth: 'user', params: IdParams, response: CourseRequest }),
  claim: endpoint({ method: 'POST', path: '/course-requests/:id/claim', summary: '"আমি বানাব" — teacher claims (optionally links a course)', tag: 'requests', auth: 'seller', params: IdParams, body: z.object({ courseId: z.uuid().optional() }), response: CourseRequest }),
};

export const wishlist = {
  add: endpoint({ method: 'POST', path: '/wishlist/:courseId', summary: 'Save course', tag: 'courses', auth: 'user', params: z.object({ courseId: z.uuid() }), response: Ok }),
  remove: endpoint({ method: 'DELETE', path: '/wishlist/:courseId', summary: 'Unsave course', tag: 'courses', auth: 'user', params: z.object({ courseId: z.uuid() }), response: Ok }),
  list: endpoint({ method: 'GET', path: '/me/wishlist', summary: 'Saved courses', tag: 'courses', auth: 'user', response: z.object({ items: z.array(CourseCardDto) }) }),
};
