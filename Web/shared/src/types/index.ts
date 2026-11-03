// Shared enums — the single list of allowed values for client, server and (via OpenAPI) mobile.

export const ROLES = ['student', 'seller', 'admin'] as const;
export type Role = (typeof ROLES)[number];

export const USER_STATUSES = ['active', 'suspended', 'pending_deletion', 'deleted'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

export const PLATFORMS = ['web', 'android', 'ios'] as const;
export type Platform = (typeof PLATFORMS)[number];

export const LOCALES = ['bn', 'en'] as const;
export type Locale = (typeof LOCALES)[number];

export const INSTITUTION_TYPES = ['public', 'private', 'college', 'polytechnic'] as const;
export type InstitutionType = (typeof INSTITUTION_TYPES)[number];

export const INSTITUTION_STATUSES = ['pending', 'approved', 'merged', 'rejected'] as const;
export type InstitutionStatus = (typeof INSTITUTION_STATUSES)[number];

export const REQUEST_STATUSES = ['pending', 'approved', 'rejected', 'merged'] as const;

export const EXAM_TYPES = ['midterm', 'final', 'quiz', 'lab', 'viva', 'admission', 'general'] as const;
export type ExamType = (typeof EXAM_TYPES)[number];

export const COURSE_TYPES = ['crash', 'full', 'solve', 'suggestion', 'lab'] as const;
export type CourseType = (typeof COURSE_TYPES)[number];

export const COURSE_STATUSES = [
  'draft',
  'in_review',
  'changes_requested',
  'rejected',
  'published',
  'unlisted',
  'archived',
] as const;
export type CourseStatus = (typeof COURSE_STATUSES)[number];

export const ACCESS_TYPES = ['lifetime', 'days', 'until_exam'] as const;
export type AccessType = (typeof ACCESS_TYPES)[number];

export const LESSON_TYPES = ['video', 'pdf', 'text', 'quiz', 'mock'] as const;
export type LessonType = (typeof LESSON_TYPES)[number];

export const VIDEO_PROVIDERS = ['bunny', 'cloudflare', 'mux', 'external_link', 'fake'] as const;
export type VideoProviderName = (typeof VIDEO_PROVIDERS)[number];

export const DOCUMENT_KINDS = ['notes', 'pdf', 'previous_questions', 'slides', 'link'] as const;
export type DocumentKind = (typeof DOCUMENT_KINDS)[number];

export const ORDER_STATUSES = ['pending', 'paid', 'failed', 'cancelled', 'refunded', 'partially_refunded'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export const PAYMENT_METHODS = ['bkash', 'nagad', 'rocket', 'card'] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

export const PAYMENT_GATEWAYS = ['sslcommerz', 'bkash', 'fake'] as const;
export type PaymentGatewayName = (typeof PAYMENT_GATEWAYS)[number];

export const PAYMENT_STATUSES = ['initiated', 'pending', 'succeeded', 'failed', 'refunded'] as const;

export const REFUND_STATUSES = ['requested', 'approved', 'rejected', 'processed'] as const;
export const PAYOUT_STATUSES = ['requested', 'approved', 'paid', 'rejected'] as const;
export const PAYOUT_METHODS = ['bkash', 'nagad', 'bank'] as const;
export const LEDGER_TYPES = ['sale', 'commission', 'refund', 'payout', 'adjustment'] as const;

export const COUPON_SCOPES = ['platform', 'seller', 'course'] as const;
export const COUPON_TYPES = ['percent', 'fixed'] as const;

export const SELLER_VERIFICATION_STATUSES = ['none', 'pending', 'verified', 'rejected'] as const;
export type SellerVerificationStatus = (typeof SELLER_VERIFICATION_STATUSES)[number];
export const SELLER_VERIFICATION_METHODS = ['university_email', 'id_card', 'linkedin', 'reference'] as const;

export const ENROLLMENT_STATUSES = ['active', 'expired', 'refunded', 'revoked'] as const;

export const CLASSROOM_CONNECTION_STATUSES = ['active', 'needs_reauth', 'workspace_blocked', 'error', 'syncing'] as const;
export type ClassroomConnectionStatus = (typeof CLASSROOM_CONNECTION_STATUSES)[number];

/** Student-facing status of a Classroom assignment (derived from submission state + due date). */
export const ASSIGNMENT_STATUSES = ['pending', 'turned_in', 'graded', 'missing'] as const;
export type AssignmentStatus = (typeof ASSIGNMENT_STATUSES)[number];

export const SUBMISSION_STATES = ['NEW', 'CREATED', 'TURNED_IN', 'RETURNED', 'RECLAIMED_BY_STUDENT'] as const;

export const FEED_KINDS = ['assignment', 'material', 'announcement', 'graded'] as const;
export type FeedKind = (typeof FEED_KINDS)[number];

export const NOTIFICATION_TYPES = [
  'classroom.assignment_new',
  'classroom.material_new',
  'classroom.announcement',
  'classroom.graded',
  'classroom.due_24h',
  'classroom.due_2h',
  'classroom.overdue',
  'exam.daily_plan',
  'course.qa_answered',
  'seller.qa_new',
  'order.paid',
  'order.refunded',
  'request.published',
  'seller.course_decision',
  'seller.payout_paid',
  'seller.new_enrollment',
  'seller.application_result',
  'admin.broadcast',
] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

export const NOTIFICATION_CHANNELS = ['in_app', 'push', 'email', 'sms'] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNELS)[number];

export const COURSE_REQUEST_STATUSES = ['open', 'teacher_working', 'published', 'closed'] as const;
export type CourseRequestStatus = (typeof COURSE_REQUEST_STATUSES)[number];

export const REPORT_STATUSES = ['open', 'reviewing', 'resolved', 'dismissed'] as const;

export const INTEGRATION_PROVIDERS = ['brevo', 'payment', 'sms', 'video', 'google', 'push', 'search', 'business'] as const;
export type IntegrationProvider = (typeof INTEGRATION_PROVIDERS)[number];

export const EMAIL_TEMPLATES = [
  'welcome',
  'email_otp',
  'password_reset',
  'order_paid',
  'refund',
  'seller_application_result',
  'course_review_decision',
  'payout_paid',
  'classroom_deadline',
  'admin_broadcast',
  'test',
] as const;
export type EmailTemplate = (typeof EMAIL_TEMPLATES)[number];

export const EMAIL_STATUSES = ['queued', 'sent', 'failed'] as const;
export const EMAIL_PROVIDERS = ['brevo_api', 'brevo_smtp', 'fake'] as const;

export const THEMES = ['system', 'light', 'dark'] as const;
export type Theme = (typeof THEMES)[number];

export const OTP_PURPOSES = ['login', 'verify_phone', 'email_verify', 'password_reset'] as const;

export const SEARCH_SORTS = ['best', 'newest', 'rating', 'price_asc', 'price_desc', 'popular'] as const;
export type SearchSort = (typeof SEARCH_SORTS)[number];

/** Money is integer paisa everywhere (৳1 = 100 paisa). */
export type Paisa = number;
