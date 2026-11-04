import { z } from 'zod';
import { CursorQuery, Ok, endpoint, page } from './common';
import { ASSIGNMENT_STATUSES, CLASSROOM_CONNECTION_STATUSES, FEED_KINDS, NOTIFICATION_CHANNELS, NOTIFICATION_TYPES, PLATFORMS } from '@/types';

/** Minimal read-only scopes (CLAUDE.md §0A). Anything more must be logged in docs/DECISIONS.md. */
export const CLASSROOM_SCOPES = [
  'openid',
  'email',
  'profile',
  'https://www.googleapis.com/auth/classroom.courses.readonly',
  'https://www.googleapis.com/auth/classroom.coursework.me.readonly',
  'https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly',
  'https://www.googleapis.com/auth/classroom.announcements.readonly',
  'https://www.googleapis.com/auth/classroom.push-notifications',
] as const;

export const Material = z.object({
  kind: z.enum(['drive', 'link', 'youtube', 'form']),
  title: z.string(),
  url: z.string().nullable(),
  thumbnailUrl: z.string().nullable(),
  mime: z.string().nullable(),
});
export type Material = z.infer<typeof Material>;

export const ClassroomStatus = z.object({
  connected: z.boolean(),
  status: z.enum(CLASSROOM_CONNECTION_STATUSES).nullable(),
  email: z.string().nullable(),
  lastSyncAt: z.string().nullable(),
  lastFullSyncAt: z.string().nullable(),
  nextManualSyncAt: z.string().nullable(),
  errorCode: z.string().nullable(),
  courseCount: z.number().int(),
  isFake: z.boolean(),
});
export type ClassroomStatus = z.infer<typeof ClassroomStatus>;

export const ClassCourse = z.object({
  id: z.string(), // gc course id
  name: z.string(),
  section: z.string().nullable(),
  room: z.string().nullable(),
  teacherNames: z.array(z.string()),
  color: z.string(),
  alternateLink: z.string().nullable(),
  unreadCount: z.number().int(),
  pendingCount: z.number().int(),
  nextDueAt: z.string().nullable(),
});
export type ClassCourse = z.infer<typeof ClassCourse>;

export const Assignment = z.object({
  id: z.string(), // gc coursework id
  courseId: z.string(),
  courseName: z.string(),
  courseColor: z.string(),
  title: z.string(),
  description: z.string().nullable(),
  workType: z.string(),
  dueAt: z.string().nullable(),
  maxPoints: z.number().nullable(),
  status: z.enum(ASSIGNMENT_STATUSES),
  late: z.boolean(),
  grade: z.number().nullable(),
  materials: z.array(Material),
  alternateLink: z.string().nullable(),
  postedAt: z.string().nullable(),
});
export type Assignment = z.infer<typeof Assignment>;

export const FeedItem = z.object({
  id: z.uuid(),
  kind: z.enum(FEED_KINDS),
  refId: z.string(),
  courseId: z.string(),
  courseName: z.string(),
  courseColor: z.string(),
  title: z.string(),
  body: z.string().nullable(),
  materials: z.array(Material),
  grade: z.object({ assigned: z.number().nullable(), max: z.number().nullable() }).nullable(),
  alternateLink: z.string().nullable(),
  occurredAt: z.string(),
  read: z.boolean(),
});
export type FeedItem = z.infer<typeof FeedItem>;

export const StreamItem = z.object({
  id: z.string(),
  kind: z.enum(['assignment', 'material', 'announcement']),
  title: z.string(),
  body: z.string().nullable(),
  author: z.string().nullable(),
  materials: z.array(Material),
  postedAt: z.string().nullable(),
  alternateLink: z.string().nullable(),
  assignment: Assignment.nullable(),
});

export const AssignmentsQuery = z.object({
  status: z.enum([...ASSIGNMENT_STATUSES, 'all', 'upcoming']).default('all'),
  courseId: z.string().optional(),
  from: z.iso.datetime({ offset: true }).optional(),
  to: z.iso.datetime({ offset: true }).optional(),
});

export const CalendarEvent = z.object({
  id: z.string(),
  kind: z.enum(['assignment', 'exam']),
  title: z.string(),
  at: z.string(),
  courseName: z.string().nullable(),
  color: z.string(),
  status: z.enum(ASSIGNMENT_STATUSES).nullable(),
  link: z.string(),
});

export const ConnectUrlQuery = z.object({ returnTo: z.string().max(200).optional() });

export const ConnectBody = z.object({
  /** Server auth code from Android Credential Manager / iOS GoogleSignIn (or the web code when not using the redirect). */
  code: z.string().min(4),
  platform: z.enum(PLATFORMS).default('android'),
  redirectUri: z.string().optional(),
});

export const classroom = {
  connectUrl: endpoint({ method: 'GET', path: '/classroom/connect-url', summary: 'Google consent URL (read-only Classroom scopes, signed state + PKCE)', tag: 'classroom', auth: 'user', query: ConnectUrlQuery, response: z.object({ url: z.string(), fake: z.boolean() }) }),
  connect: endpoint({ method: 'POST', path: '/classroom/connect', summary: 'Exchange a server auth code (mobile) and start full sync', tag: 'classroom', auth: 'user', body: ConnectBody, response: ClassroomStatus }),
  status: endpoint({ method: 'GET', path: '/classroom/status', summary: 'Connection + sync status', tag: 'classroom', auth: 'user', response: ClassroomStatus }),
  disconnect: endpoint({ method: 'DELETE', path: '/classroom/connection', summary: 'Revoke at Google, stop push, delete all cached Classroom data', tag: 'classroom', auth: 'user', response: Ok }),
  sync: endpoint({ method: 'POST', path: '/classroom/sync', summary: 'Manual refresh (1/min)', tag: 'classroom', auth: 'user', response: ClassroomStatus }),
  courses: endpoint({ method: 'GET', path: '/classroom/courses', summary: 'Classes', tag: 'classroom', auth: 'user', response: z.object({ items: z.array(ClassCourse) }) }),
  stream: endpoint({ method: 'GET', path: '/classroom/courses/:id/stream', summary: 'Class stream: assignments, materials, announcements', tag: 'classroom', auth: 'user', params: z.object({ id: z.string().min(1).max(64) }), response: z.object({ course: ClassCourse, items: z.array(StreamItem) }) }),
  assignments: endpoint({ method: 'GET', path: '/classroom/assignments', summary: 'All assignments (filters: status, class, due range)', tag: 'classroom', auth: 'user', query: AssignmentsQuery, response: z.object({ items: z.array(Assignment) }) }),
  assignment: endpoint({ method: 'GET', path: '/classroom/assignments/:id', summary: 'Assignment detail', tag: 'classroom', auth: 'user', params: z.object({ id: z.string().min(1).max(64) }), response: Assignment }),
  calendar: endpoint({ method: 'GET', path: '/classroom/calendar', summary: 'Deadlines + exams in a range', tag: 'classroom', auth: 'user', query: z.object({ from: z.iso.datetime({ offset: true }), to: z.iso.datetime({ offset: true }) }), response: z.object({ items: z.array(CalendarEvent) }) }),
  feed: endpoint({ method: 'GET', path: '/classroom/feed', summary: 'New from your classes (newest first)', tag: 'classroom', auth: 'user', query: CursorQuery.extend({ courseId: z.string().optional(), unread: z.coerce.boolean().optional() }), response: page(FeedItem) }),
  feedRead: endpoint({ method: 'POST', path: '/classroom/feed/read', summary: 'Mark feed items read (ids or all)', tag: 'classroom', auth: 'user', body: z.object({ ids: z.array(z.uuid()).max(200).optional(), all: z.boolean().optional() }), response: Ok }),
};

// ---------------------------------------------------------------- notifications

export const NotificationItem = z.object({
  id: z.uuid(),
  type: z.enum(NOTIFICATION_TYPES),
  title: z.string(),
  body: z.string(),
  link: z.string().nullable(),
  createdAt: z.string(),
  read: z.boolean(),
});
export type NotificationItem = z.infer<typeof NotificationItem>;

export const PreferenceRow = z.object({ type: z.enum(NOTIFICATION_TYPES), channel: z.enum(NOTIFICATION_CHANNELS), enabled: z.boolean() });

export const notifications = {
  list: endpoint({ method: 'GET', path: '/notifications', summary: 'Notification center', tag: 'notifications', auth: 'user', query: CursorQuery.extend({ unread: z.coerce.boolean().optional() }), response: page(NotificationItem) }),
  read: endpoint({ method: 'POST', path: '/notifications/read', summary: 'Mark read (ids or all)', tag: 'notifications', auth: 'user', body: z.object({ ids: z.array(z.uuid()).max(200).optional(), all: z.boolean().optional() }), response: Ok }),
  unreadCount: endpoint({ method: 'GET', path: '/notifications/unread-count', summary: 'Unread count for the bell', tag: 'notifications', auth: 'user', response: z.object({ count: z.number().int() }) }),
  preferences: endpoint({ method: 'GET', path: '/me/notification-preferences', summary: 'Per type × channel toggles', tag: 'notifications', auth: 'user', response: z.object({ items: z.array(PreferenceRow) }) }),
  updatePreferences: endpoint({ method: 'PUT', path: '/me/notification-preferences', summary: 'Update toggles', tag: 'notifications', auth: 'user', body: z.object({ items: z.array(PreferenceRow).max(200) }), response: z.object({ items: z.array(PreferenceRow) }) }),
  registerPush: endpoint({ method: 'POST', path: '/me/push-tokens', summary: 'Register FCM/APNs/Web Push token', tag: 'notifications', auth: 'user', body: z.object({ platform: z.enum(PLATFORMS), token: z.string().min(10).max(4096) }), response: Ok }),
  unregisterPush: endpoint({ method: 'DELETE', path: '/me/push-tokens', summary: 'Remove a push token (logout)', tag: 'notifications', auth: 'user', body: z.object({ token: z.string().min(10).max(4096) }), response: Ok }),
};

// ---------------------------------------------------------------- dashboard (aggregate)

const Section = <T extends z.ZodType>(data: T) => z.object({ data: data.nullable(), error: z.string().nullable() });

export const DueGroup = z.object({ key: z.enum(['overdue', 'today', 'tomorrow', 'this_week']), items: z.array(Assignment) });

export const ExamWidget = z.object({
  planId: z.uuid(),
  subjectName: z.string(),
  courseCode: z.string().nullable(),
  examType: z.string(),
  examDate: z.string(),
  daysLeft: z.number().int(),
  readiness: z.number().int(),
});

export const ContinueWidget = z.object({
  courseId: z.uuid(),
  courseSlug: z.string(),
  title: z.string(),
  courseCode: z.string().nullable(),
  sellerName: z.string(),
  progressPercent: z.number().int(),
  completedLessons: z.number().int(),
  totalLessons: z.number().int(),
  lastLessonId: z.uuid().nullable(),
  lastLessonTitle: z.string().nullable(),
  coverColor: z.string().nullable(),
});

export const WeekDay = z.object({ date: z.string(), deadlines: z.number().int(), exams: z.number().int() });

export const RecommendedCourse = z.object({
  id: z.uuid(),
  slug: z.string(),
  title: z.string(),
  courseCode: z.string().nullable(),
  institutionShort: z.string().nullable(),
  sellerName: z.string(),
  sellerVerified: z.boolean(),
  examType: z.string(),
  price: z.number().int(),
  compareAtPrice: z.number().int().nullable(),
  ratingAvg: z.number(),
  ratingCount: z.number().int(),
  coverUrl: z.string().nullable(),
  coverColor: z.string().nullable(),
  compatibility: z.enum(['match', 'mismatch', 'general']),
});
export type RecommendedCourse = z.infer<typeof RecommendedCourse>;

export const Dashboard = z.object({
  header: z.object({ name: z.string(), institutionShort: z.string().nullable(), departmentShort: z.string().nullable(), semester: z.number().int().nullable(), unreadNotifications: z.number().int(), classroom: ClassroomStatus }),
  dueSoon: Section(z.object({ groups: z.array(DueGroup), total: z.number().int() })),
  feed: Section(z.object({ items: z.array(FeedItem), unread: z.number().int() })),
  exam: Section(ExamWidget),
  continueLearning: Section(ContinueWidget),
  week: Section(z.array(WeekDay)),
  recommendations: Section(z.array(RecommendedCourse)),
  generatedAt: z.string(),
});
export type Dashboard = z.infer<typeof Dashboard>;

export const dashboard = {
  get: endpoint({ method: 'GET', path: '/dashboard', summary: 'Home dashboard aggregate (per-widget sections + error flags)', tag: 'dashboard', auth: 'user', response: Dashboard }),
};

// ---------------------------------------------------------------- webhooks

export const PubSubPush = z.object({
  message: z.object({ data: z.string().optional(), attributes: z.record(z.string(), z.string()).optional(), messageId: z.string().optional() }),
  subscription: z.string().optional(),
});

export const webhooks = {
  googlePubsub: endpoint({ method: 'POST', path: '/webhooks/google-pubsub', summary: 'Classroom push notifications via Pub/Sub push subscription', tag: 'webhooks', auth: 'public', query: z.object({ token: z.string().optional() }), body: PubSubPush, response: Ok }),
};
