import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { PublicShell } from './shells/PublicShell';
import { RedirectIfAuthed, RequireAuth } from './guards';
import { NotFoundPage, RouteError } from './pages';

/** Lazy route helper: each page is its own chunk; seller/admin code never loads for students. */
const page = (load: () => Promise<{ default: React.ComponentType }>): Pick<RouteObject, 'lazy'> => ({
  lazy: async () => ({ Component: (await load()).default }),
});

export const routes: RouteObject[] = [
  {
    errorElement: <RouteError />,
    children: [
      // ---------------- public
      {
        element: <PublicShell />,
        children: [
          { index: true, element: <RedirectIfAuthed><LandingRoute /></RedirectIfAuthed> },
          { path: 'explore', ...page(() => import('@/features/marketplace/ExplorePage')) },
          { path: 'universities', ...page(() => import('@/features/marketplace/UniversitiesPage')) },
          { path: 'u/:slug', ...page(() => import('@/features/marketplace/UniversityPage')) },
          { path: 'subjects', ...page(() => import('@/features/marketplace/SubjectsPage')) },
          { path: 's/:slug', ...page(() => import('@/features/marketplace/SubjectPage')) },
          { path: 'c/:slug', ...page(() => import('@/features/marketplace/CoursePage')) },
          { path: 't/:sellerSlug', ...page(() => import('@/features/marketplace/StorefrontPage')) },
          { path: 'privacy', ...page(() => import('@/features/marketplace/legal/PrivacyPage')) },
          { path: 'terms', ...page(() => import('@/features/marketplace/legal/TermsPage')) },
          { path: 'refund', ...page(() => import('@/features/marketplace/legal/RefundPage')) },
          { path: 'about', ...page(() => import('@/features/marketplace/legal/AboutPage')) },
          { path: 'contact', ...page(() => import('@/features/marketplace/legal/ContactPage')) },
        ],
      },
      // ---------------- auth
      { path: 'auth', element: <RedirectIfAuthed><AuthRoute /></RedirectIfAuthed> },
      { path: 'onboarding', element: <RequireAuth allowOnboarding><OnboardingRoute /></RequireAuth> },
      // ---------------- student (sidebar shell)
      {
        element: (
          <RequireAuth>
            <StudentShell />
          </RequireAuth>
        ),
        children: [
          { path: 'home', ...page(() => import('@/features/student/home/HomePage')) },
          { path: 'classroom', ...page(() => import('@/features/student/classroom/ClassroomPage')) },
          { path: 'classroom/assignments', ...page(() => import('@/features/student/classroom/AssignmentsPage')) },
          { path: 'classroom/calendar', ...page(() => import('@/features/student/classroom/CalendarPage')) },
          { path: 'classroom/:courseId', ...page(() => import('@/features/student/classroom/ClassStreamPage')) },
          { path: 'notifications', ...page(() => import('@/features/student/NotificationsPage')) },
          { path: 'profile', ...page(() => import('@/features/student/profile/ProfilePage')) },
          { path: 'settings', element: <Navigate to="/profile" replace /> },
          { path: 'my-courses', ...page(() => import('@/features/learning/MyCoursesPage')) },
          { path: 'checkout/result', ...page(() => import('@/features/marketplace/checkout/CheckoutResultPage')) },
          { path: 'checkout/:courseId', ...page(() => import('@/features/marketplace/checkout/CheckoutPage')) },
          { path: 'exam-mode', ...page(() => import('@/features/learning/exam/ExamModePage')) },
          { path: 'exam-mode/:planId', ...page(() => import('@/features/learning/exam/ExamModePage')) },
          { path: 'mock-tests', ...page(() => import('@/features/learning/mock/MockTestsPage')) },
          { path: 'mock-test/:id', ...page(() => import('@/features/learning/mock/MockTestPage')) },
          { path: 'mock-test/:id/result', ...page(() => import('@/features/learning/mock/MockResultPage')) },
          { path: 'notes', ...page(() => import('@/features/learning/NotesPage')) },
          { path: 'requests', ...page(() => import('@/features/marketplace/RequestsPage')) },
        ],
      },
      // player is full-screen (no sidebar)
      { path: 'learn/:courseId/:lessonId', element: <RequireAuth><PlayerRoute /></RequireAuth> },
      // ---------------- seller (Teacher Studio) — separate lazy chunks
      {
        path: 'teach',
        element: (
          <RequireAuth role="seller">
            <SellerShell />
          </RequireAuth>
        ),
        children: [
          { index: true, ...page(() => import('@/features/seller/SellerDashboardPage')) },
          { path: 'courses', ...page(() => import('@/features/seller/courses/SellerCoursesPage')) },
          { path: 'courses/new', ...page(() => import('@/features/seller/courses/CourseWizardPage')) },
          { path: 'courses/:id/edit', ...page(() => import('@/features/seller/courses/CourseWizardPage')) },
          { path: 'students', ...page(() => import('@/features/seller/SellerStudentsPage')) },
          { path: 'qa', ...page(() => import('@/features/seller/SellerQaPage')) },
          { path: 'reviews', ...page(() => import('@/features/seller/SellerReviewsPage')) },
          { path: 'analytics', ...page(() => import('@/features/seller/SellerAnalyticsPage')) },
          { path: 'coupons', ...page(() => import('@/features/seller/SellerCouponsPage')) },
          { path: 'earnings', ...page(() => import('@/features/seller/SellerEarningsPage')) },
          { path: 'verification', ...page(() => import('@/features/seller/SellerVerificationPage')) },
          { path: 'storefront', ...page(() => import('@/features/seller/SellerStorefrontPage')) },
        ],
      },
      // non-sellers hitting /teach/apply see the application (student shell)
      { path: 'teach-apply', element: <RequireAuth><SellerApplyRoute /></RequireAuth> },
      // ---------------- admin — separate lazy chunks
      {
        path: 'admin',
        element: (
          <RequireAuth role="admin">
            <AdminShell />
          </RequireAuth>
        ),
        children: [
          { index: true, ...page(() => import('@/features/admin/AdminDashboardPage')) },
          { path: 'sellers', ...page(() => import('@/features/admin/AdminSellersPage')) },
          { path: 'institutions', ...page(() => import('@/features/admin/AdminInstitutionsPage')) },
          { path: 'catalog', ...page(() => import('@/features/admin/AdminCatalogPage')) },
          { path: 'courses/review', ...page(() => import('@/features/admin/AdminReviewPage')) },
          { path: 'orders', ...page(() => import('@/features/admin/AdminOrdersPage')) },
          { path: 'refunds', ...page(() => import('@/features/admin/AdminRefundsPage')) },
          { path: 'payouts', ...page(() => import('@/features/admin/AdminPayoutsPage')) },
          { path: 'users', ...page(() => import('@/features/admin/AdminUsersPage')) },
          { path: 'reports', ...page(() => import('@/features/admin/AdminReportsPage')) },
          { path: 'notifications', ...page(() => import('@/features/admin/AdminBroadcastsPage')) },
          { path: 'settings', ...page(() => import('@/features/admin/settings/AdminSettingsPage')) },
          { path: 'health', ...page(() => import('@/features/admin/AdminHealthPage')) },
          { path: 'email-logs', ...page(() => import('@/features/admin/AdminEmailLogsPage')) },
          { path: 'audit', ...page(() => import('@/features/admin/AdminAuditPage')) },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

// Small wrappers so guarded top-level pages are still lazy chunks.
import { lazy, Suspense } from 'react';
import { Skeleton } from '@/components';
const fallback = (
  <div className="mx-auto max-w-3xl p-6">
    <Skeleton className="h-10 w-1/2" />
    <Skeleton className="mt-4 h-64 w-full" />
  </div>
);
const StudentShellL = lazy(() => import('./shells/StudentShell'));
const SellerShellL = lazy(() => import('./shells/SellerShell'));
const AdminShellL = lazy(() => import('./shells/AdminShell'));
function StudentShell() {
  return <Suspense fallback={fallback}><StudentShellL /></Suspense>;
}
function SellerShell() {
  return <Suspense fallback={fallback}><SellerShellL /></Suspense>;
}
function AdminShell() {
  return <Suspense fallback={fallback}><AdminShellL /></Suspense>;
}
const Landing = lazy(() => import('@/features/marketplace/LandingPage'));
const Auth = lazy(() => import('@/features/auth/AuthPage'));
const Onboarding = lazy(() => import('@/features/auth/onboarding/OnboardingPage'));
const Player = lazy(() => import('@/features/learning/player/PlayerPage'));
const SellerApply = lazy(() => import('@/features/seller/SellerApplyPage'));
function LandingRoute() {
  return <Suspense fallback={fallback}><Landing /></Suspense>;
}
function AuthRoute() {
  return <Suspense fallback={fallback}><Auth /></Suspense>;
}
function OnboardingRoute() {
  return <Suspense fallback={fallback}><Onboarding /></Suspense>;
}
function PlayerRoute() {
  return <Suspense fallback={fallback}><Player /></Suspense>;
}
function SellerApplyRoute() {
  return <Suspense fallback={fallback}><SellerApply /></Suspense>;
}

export const router = createBrowserRouter(routes);
