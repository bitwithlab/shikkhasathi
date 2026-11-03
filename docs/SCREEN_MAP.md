# Screen Map

Every reference screen (`Reference/stitch_campuscourse_design_system/`) and every spec screen → web route / mobile screen → status.
"CampusCourse" in the reference is rebranded **ShikkhaSathi / শিক্ষাসাথী** everywhere.

Status legend: ⬜ not started · 🟨 in progress · ✅ done (real API + seed, loading/empty/error, light/dark, 360–1440px)

## Reference screens

| Reference folder | What it shows | Web route | Mobile screen | Phase | Status |
|---|---|---|---|---|---|
| `campuscourse_landing_page_desktop_1440px` | Landing: navbar, hero + smart search + popular chips, trust strip, 3-step "কীভাবে কাজ করে", Midterm crash course carousel w/ university tabs, Exam Mode split showcase, verified teachers, testimonials, teacher CTA band, FAQ, footer | `/` | — (web only; app opens on Auth/Home) | 1→5 | ⬜ |
| `campuscourse_3` | Onboarding step 1 — university picker (search, initials tiles, city chip, "আমার ইউনিভার্সিটি তালিকায় নেই? অনুরোধ করুন") | `/onboarding` (step 1) | `OnboardingScreen` | 2 | ✅ |
| `campuscourse_1` | Onboarding step 2 — department list with icons + program list (Day/Evening/Diploma) | `/onboarding` (step 2) | `OnboardingScreen` | 2 | ✅ |
| `campuscourse_2` | Onboarding step 3 — semester grid 1st–8th, batch stepper + quick chips, section tiles | `/onboarding` (step 3) | `OnboardingScreen` | 2 | ✅ |
| `campuscourse_4` | Onboarding success — confetti, "আপনার জন্য কোর্স সাজানো হচ্ছে…", ready list, countdown pill, CTA to dashboard | `/onboarding` (done) | `OnboardingScreen` | 2 | ✅ |
| `campuscourse_desktop_dashboard` | Student desktop dashboard with sidebar: hero continue-learning card, exam countdown, pending assignments & deadlines, notice board, notes quick access, recently watched, my courses rings, recommendations | `/home` | — | 3, 9 | ⬜ |
| `campuscourse_mobile_dashboard` | Same dashboard at 360px with bottom bar | `/home` (mobile web) | `HomeScreen` | 3, 9 | ⬜ |
| `campuscourse_desktop` | Explore desktop — filter sidebar, search w/ autocomplete, active chips, result count, sort, card grid | `/explore` | — | 5 | ⬜ |
| `campuscourse_mobile` | Explore mobile — search on top, filter sheet, card list ("২৪টি কোর্স পাওয়া গেছে") | `/explore` (mobile web) | `ExploreScreen` | 5 | ⬜ |
| `campuscourse_zero_results` | Zero results — illustration + "এই কোর্স চাই" | `/explore` (empty state) | `ExploreScreen` empty | 5 | ⬜ |
| `campuscourse_universities_directory` | Universities grid with type filter + search | `/universities` | `UniversitiesScreen` | 2/5 | ⬜ |
| `campuscourse_university_detail` | University hero, stats, department tabs, semester pills, subjects, course grid | `/u/:slug` | `UniversityDetailScreen` | 2/5 | ⬜ |
| `campuscourse_data_structures_desktop` | Course details desktop — breadcrumb, teacher card, chips, compatibility badge, learn list, curriculum, resources, "কাদের জন্য নয়", reviews, FAQ, sticky buy card | `/c/:slug` | — | 5 | ⬜ |
| `campuscourse_data_structures_mobile` | Course details mobile with sticky bottom buy bar | `/c/:slug` (mobile web) | `CourseDetailScreen` | 5 | ⬜ |
| `campuscourse_exam_mode` | Exam Mode — gradient countdown + readiness ring, daily plan, syllabus checklist, important topics, short lectures, previous questions, flashcards, mock test card, last-night pack | `/exam-mode`, `/exam-mode/:planId` | `ExamModeScreen` | 8 | ⬜ |
| `academic_calm/DESIGN.md` | Design system tokens & component rules | `docs/DESIGN_SYSTEM.md` | Compose theme | 1 | ✅ |
| `শিক্ষাসাথী Educational Logo.png` | Brand logo (cap + open book, indigo/violet, amber tassel) | favicon, navbar, emails | launcher icon | 1 | ✅ web |

## Spec screens without a reference image (designed from `docs/UI_PROMPTS.md` + design system)

| Screen (UI_PROMPTS #) | Web route | Mobile screen | Phase | Status |
|---|---|---|---|---|
| Auth: phone OTP / email / Google (#19) | `/auth`, `/auth/otp` | `AuthScreen`, `OtpScreen` | 1 | ✅ web · 🟨 mobile (UI uncompiled, ADR-012) |
| Onboarding step 4 Connect Classroom, step 5 Next exam (#3) | `/onboarding` | `OnboardingScreen` | 2/3 | ⬜ |
| Classroom class list / class stream / assignments / calendar (#13) | `/classroom`, `/classroom/:courseId`, `/classroom/assignments`, `/classroom/calendar` | `ClassroomScreen`, `ClassStreamScreen`, `AssignmentsScreen`, `CalendarScreen` | 3 | ⬜ |
| Assignment detail sheet | sheet on any of the above | `AssignmentSheet` | 3 | ⬜ |
| Notifications center | `/notifications` | `NotificationsScreen` | 3 | ⬜ |
| Subjects list / detail (#6) | `/subjects`, `/s/:slug` | `SubjectsScreen` | 5 | ⬜ |
| Course request board (#14) | `/requests` | `RequestsScreen` | 5 | ⬜ |
| Checkout + processing/success/failure (#8) | `/checkout/:courseId`, `/checkout/result` | `CheckoutScreen` | 6 | ✅ web |
| Course player (#10) | `/learn/:courseId/:lessonId` | `PlayerScreen` | 7 | ✅ web |
| My Courses (#9) | `/my-courses` | `MyCoursesScreen` | 7 | ✅ web |
| Notes & bookmarks | `/notes` | `NotesScreen` | 7 | ✅ web |
| Mock test pre/question/submit/result (#12) | `/mock-tests`, `/mock-test/:id`, `/mock-test/:id/result` | `MockTestScreen` | 8 | ⬜ |
| Profile & settings (#20) | `/profile`, `/settings` | `ProfileScreen`, `SettingsScreen` | 3/9 | ⬜ |
| Teacher dashboard (#15) | `/teach` | `SellerLiteScreen` | 4/10 | ⬜ |
| Course creator wizard (#16) | `/teach/courses/new`, `/teach/courses/:id/edit` | — | 4 | ⬜ |
| Teacher storefront (#17) | `/t/:sellerSlug` (public), `/teach/storefront` (edit) | — | 4/10 | ⬜ |
| Teacher Studio: courses, students, Q&A, reviews, analytics, coupons, earnings, verification | `/teach/*` | Seller-lite (sales, Q&A, reviews) | 4/10 | ⬜ |
| Admin panel (#18): dashboard, sellers, institutions, catalog, review queue + drawer, orders, refunds, payouts, users, reports, broadcasts, settings + integrations, system health, email logs, audit | `/admin/*` | — (web only) | 1/10 | ⬜ |
| Policy pages | `/privacy`, `/terms`, `/refund`, `/about`, `/contact` | WebView links | 3 | ⬜ |
| Shared states sheet (#21): skeletons, empty, 404, offline, toasts | everywhere, `*` 404 | everywhere | 1→12 | ⬜ |
