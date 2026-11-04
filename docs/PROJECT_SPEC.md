# ShikkhaSathi (শিক্ষাসাথী) — Full Project Specification

> **Location:** `docs/PROJECT_SPEC.md` · **Status:** v1.1 (2026-11-03) · **Owner:** Mahdi · **Repo:** https://github.com/bitwithlab/shikkhasathi
> This is the **single source of truth** for what ShikkhaSathi is and how it works. It supersedes `docs/CAMPUS_MARKETPLACE_SPEC.md` (kept as background) wherever they differ. Coding rules live in the root `CLAUDE.md`; the one-page brief is `docs/PROJECT.md`; screen prompts are in `docs/UI_PROMPTS.md`.

---

## Table of contents
1. Overview
2. Users, roles & permissions
3. Architecture & tech stack
4. Repository structure
5. Information architecture (navigation & routes)
6. Feature specifications
7. Data model
8. API specification
9. Background jobs & integrations
10. Notifications
11. Security, privacy & compliance
12. Performance & reliability
13. Design system summary
14. Language, content & tone
15. Testing & quality
16. Environments, deployment & configuration
17. Analytics & success metrics
18. Roadmap & launch checklist
19. Risks
20. Open questions

---

## 1. Overview

### 1.1 Identity
| Item | Value |
|---|---|
| Name | **ShikkhaSathi** / **শিক্ষাসাথী** (former working name "CampusCourse" — not used) |
| Domain | **shikkhasathi.com** |
| Tagline (bn) | আপনার ক্লাস, আপনার কোর্স, আপনার পরীক্ষার প্রস্তুতি — এক জায়গায়। |
| Tagline (en) | Your classes. Your courses. Your exam prep. All in one place. |
| Market | University, college and polytechnic students in Bangladesh |
| Platforms | Web (all roles) · Mobile app (Android first, iOS from the same code) |
| App id | `com.shikkhasathi.app` |

### 1.2 Problem
- Students juggle many Google Classroom classes and miss assignments, materials and notices.
- Before exams there is no time to watch full courses; good teachers are hard to find outside one's own university.
- Notes, previous questions and suggestions are scattered across Facebook groups and Drive links.

### 1.3 Product pillars
1. **Unified Academic Dashboard** — Google Classroom (read-only) + purchased courses + exams in one home screen. **This is the home page.**
2. **Exam-Prep Course Marketplace** — teachers (sellers) from any institution create short, exam-focused courses and choose who they are for; students find them by university, subject or course code and pay with bKash/Nagad.
3. **Exam Mode** — countdown, daily plan, important topics, previous questions, flashcards, mock tests.

### 1.4 Core principle: Create ≠ Discover
**Seller decides the target audience** (institution, department, semester, batch, section, course code). **Student decides how to search** (any combination, or free text). Targets are stored separately from courses (one course → many targets).

### 1.5 Non-goals (v1)
- Not a university management system (no attendance, official results, fee collection).
- No writing to Google Classroom (no submissions, no posts).
- No guarantee claims ("এটাই পরীক্ষায় আসবে"). Language is always "সম্ভাব্য গুরুত্বপূর্ণ টপিক".

---

## 2. Users, roles & permissions

| Role | Description |
|---|---|
| Guest | Not logged in; browses public pages and preview lessons |
| Student | Uses dashboard, Classroom, buys and learns courses |
| Seller (Teacher) | Creates and sells courses; UI calls this "শিক্ষক / Teacher Studio". A seller can also be a student |
| Admin | Platform operations |
| Support (later) | Tickets and refund requests, read-mostly |
| Institution Moderator (later) | Verifies syllabus/course codes for one institution |

### Permission matrix (v1)
| Capability | Guest | Student | Seller | Admin |
|---|:-:|:-:|:-:|:-:|
| Browse/search courses, preview lessons | ✅ | ✅ | ✅ | ✅ |
| Home dashboard, Classroom, notifications | – | ✅ | ✅ | – |
| Buy course, learn, review, Q&A ask | – | ✅ | ✅ | – |
| Exam Mode, mock tests, notes | – | ✅ | ✅ | – |
| Create/edit own courses, answer Q&A, coupons, payouts | – | – | ✅ (verified) | – |
| Verify sellers/institutions, review courses | – | – | – | ✅ |
| Refunds, payouts approval, user management, settings | – | – | – | ✅ |

Unverified sellers may draft courses but cannot submit them for review.

---

## 3. Architecture & tech stack

### 3.1 System diagram
```
                         shikkhasathi.com (Caddy, TLS)
 Browser ──▶ Caddy ─┬─ /api/* · /sitemap.xml · /c|u|s|t/<slug> ──▶ backend (Spring Boot, SS_ROLE=api)
                    └─ everything else ──▶ website (static React build, prerendered public pages)
 Mobile ───────────────────────────────▶ /api/v1
 backend modules ──▶ Supabase CORE db + CONTENT db (Flyway) · Redis (cache, rate limit, job queue) · Meilisearch
                 adapters (Real + Fake)
       ┌──────────────┬───────────────┬──────────────┬──────────────┬─────────────┐
   Google APIs    Payment gateway   Video provider   Supabase Storage  SMS gateway   FCM / APNs   Brevo
 (Classroom +     (SSLCommerz/      (Bunny/CF/Mux,   (signed URLs)                    Web Push     (email)
  Pub/Sub push)    aamarPay/bKash)   HLS + signed)

 Worker (same backend image, SS_ROLE=worker; Redis job queue + schedules): Classroom sync, Pub/Sub consumer,
 reminders, notifications fan-out, search indexing, payments reconcile, video processing, exam plans.
```

### 3.2 Stack
| Layer | Choice | Notes |
|---|---|---|
| Website (`website/`) | React 19 + Vite + TypeScript, React Router (lazy routes), TanStack Query, Zustand (UI only), Tailwind + Radix UI, React Hook Form + Zod, hls.js, Lucide | One app for public/student/seller/admin; seller & admin are separate lazy chunks |
| Server | Java 21 + Spring Boot 4 (Web MVC, Security, Data JPA, Validation, Actuator), Maven Wrapper | `backend/`; also serves the sitemap and SEO heads (ADR-025) |
| ORM / DB | Hibernate/JPA + JDBC, Flyway migrations, on **Supabase Postgres — two projects: CORE and CONTENT** | Table placement in §7.0; server-only access via pooler; RLS deny-all |
| Cache / queue | Redis (Spring Data Redis) + Redis job queue (`common/jobs`) | |
| Search | Meilisearch | Bangla + English, typo tolerance, synonyms |
| Contracts | OpenAPI generated by the backend (`docs/api/openapi.json`) + per-endpoint JSON Schemas tested on every response | Website types generated from OpenAPI; drift test |
| Mobile | Kotlin + Jetpack Compose (Compose Multiplatform), Koin, Ktor, kotlinx.serialization, Room KMP, DataStore, Coil 3, Navigation Compose | Media3 (Android) / AVPlayer (iOS) via expect/actual |
| Video | Bunny Stream (default) behind an adapter | HLS, signed URLs |
| Storage | Supabase Storage (CORE: invoices, verification-docs, avatars · CONTENT: course-docs, course-covers) | Private buckets + API-issued signed URLs |
| Email | **Brevo** transactional API (SMTP fallback) | Credentials editable in Admin → Settings → Email; env fallback |
| Config | Bootstrap secrets in env; all integrations editable in Admin panel (encrypted) | §16.1 |
| Payments | SSLCommerz or aamarPay (bKash, Nagad, Rocket, cards); bKash tokenized optional | Adapter interface |
| Auth | Phone OTP, email + password, Google sign-in; JWT access + refresh | |
| Push | FCM (Android/web), APNs (iOS), Web Push | |
| Infra | Docker, docker-compose, Caddy/Nginx, GitHub Actions, Sentry, daily DB backup | VPS to start |

---

## 4. Repository structure

```
ShikkhaSathi/
├── CLAUDE.md · README.md
├── Reference/                         # design reference (Stitch exports) — read only
├── backend/                           # Java 21 + Spring Boot (pom.xml, mvnw, Dockerfile, .env.example)
│   └── src/main/java/com/shikkhasathi/{config,common,security,integration,<module>}/
│       └── <module>/{controller,service,repository,entity,dto,mapper,adapter}
│   └── src/main/resources/{application.yml,application-dev.yml,db/migration/{core,content},db/seed}
├── website/src/                       # React + Vite (own package.json, Dockerfile)
│   ├── app/  api/  types/  styles/tokens/  components/  lib/
│   └── features/{auth,marketplace,courses,learning,student,seller,admin}/
├── app/                               # Compose Multiplatform
│   ├── android/  ios/ (ShikkhaSathi.xcodeproj, Sources/)  assets/
│   └── shared/src/{commonMain,androidMain,iosMain,…}/kotlin/com/shikkhasathi/{core,shared,features}/
├── docs/                              # ALL documentation
├── infra/                             # docker-compose, Caddyfile
└── scripts/
```

Backend modules (packages under `com.shikkhasathi`): `auth, user, student, teacher, admin, catalog, course, marketplace, search, payment, learning, exam, classroom, assignment, notification, integration, file, health`. Originally specified as: `auth, users, catalog (institutions/departments/programs/subjects), courses, search, orders, payments, enrollments, learning, exam, mocktest, reviews, qa, requests, classroom, notifications, seller, payouts, admin, files`.

---

## 5. Information architecture

### 5.1 Web routes
| Area | Route | Screen |
|---|---|---|
| Public | `/` | Landing (logged-in → redirect `/home`) |
| | `/explore` | Search & filters |
| | `/universities`, `/u/:slug` | University list / detail |
| | `/subjects`, `/s/:slug` | Subject list / detail |
| | `/c/:slug` | Course details |
| | `/t/:sellerSlug` | Teacher storefront |
| | `/privacy`, `/terms`, `/refund`, `/about`, `/contact` | Policy & info pages |
| Auth | `/auth`, `/auth/otp`, `/onboarding` | Login/sign-up, OTP, 5-step onboarding |
| Student (sidebar shell) | `/home` | **Dashboard** |
| | `/classroom`, `/classroom/:courseId` | Classroom classes / class stream |
| | `/classroom/assignments` | All assignments |
| | `/classroom/calendar` | Calendar |
| | `/my-courses` | Purchased courses |
| | `/learn/:courseId/:lessonId` | Player |
| | `/checkout/:courseId`, `/checkout/result` | Checkout + result |
| | `/exam-mode`, `/exam-mode/:planId` | Exam Mode |
| | `/mock-tests`, `/mock-test/:id`, `/mock-test/:id/result` | Mock tests |
| | `/notes` | Notes & bookmarks |
| | `/requests` | Course request board |
| | `/notifications` | Notification center |
| | `/profile`, `/settings` | Profile, settings, devices, payments history |
| Seller | `/teach`, `/teach/courses`, `/teach/courses/new`, `/teach/courses/:id/edit`, `/teach/students`, `/teach/qa`, `/teach/reviews`, `/teach/analytics`, `/teach/coupons`, `/teach/earnings`, `/teach/verification`, `/teach/storefront` | Teacher Studio |
| Admin | `/admin`, `/admin/sellers`, `/admin/institutions`, `/admin/catalog`, `/admin/courses/review`, `/admin/orders`, `/admin/refunds`, `/admin/payouts`, `/admin/users`, `/admin/reports`, `/admin/notifications`, `/admin/settings`, `/admin/audit` | Admin panel |

### 5.2 Student sidebar (order)
হোম · আমার ক্লাসরুম · অ্যাসাইনমেন্ট · ক্যালেন্ডার · আমার কোর্স · কোর্স খুঁজুন · Exam Mode · Mock Test · নোট ও বুকমার্ক · কোর্স অনুরোধ · নোটিফিকেশন · প্রোফাইল ও সেটিংস · (role switch) Teacher Studio / Admin

### 5.3 Mobile navigation
Bottom bar: **হোম · ক্লাসরুম · কোর্স · Exam · প্রোফাইল**. Drawer: কোর্স খুঁজুন, Mock Test, নোট, কোর্স অনুরোধ, নোটিফিকেশন, সেটিংস, Teacher Studio (lite: sales, Q&A, reviews). Admin is web-only.

---

## 6. Feature specifications

Each feature lists: purpose · behaviour · rules · states. All screens have loading skeleton, empty, error states.

### 6.1 Authentication & onboarding
- **Login methods:** phone OTP (primary, 6 digits, 2-min expiry, resend after 60 s, max 5/hour per number), email + password, Google sign-in.
- **Sign-up role:** Student by default; "শিক্ষক হিসেবে যোগ দিন" starts seller application (same account).
- **Onboarding (5 steps, skippable after step 1):** 1 University (search + "আমার ইউনিভার্সিটি নেই" request) → 2 Department & program → 3 Semester, batch, section → 4 **Connect Google Classroom** (optional, explains benefits + privacy) → 5 Next exam (subject, type, date; toggles Exam Mode). Success screen → `/home`.
- **Sessions:** access token 15 min, refresh token 30 days rotating; device list in settings; max 3 active devices for course playback.

### 6.2 Home dashboard (the home page)
**Widgets in order:**
| # | Widget | Data | Behaviour |
|---|---|---|---|
| 1 | Header | profile, sync status, unread count | refresh button triggers manual sync (rate-limited to 1/min) |
| 2 | সামনের অ্যাসাইনমেন্ট | classroom coursework + my submissions | groups Overdue / আজ / আগামীকাল / এই সপ্তাহ; status chips; max 6 rows + "সব দেখুন" |
| 3 | নতুন আপডেট | classroom feed (new coursework, materials, announcements, graded) | newest first, unread dot, mark-all-read |
| 4 | Exam countdown | exam_plans | shows nearest exam, readiness % |
| 5 | পড়া চালিয়ে যান | enrollments + progress | resume last lesson |
| 6 | এই সপ্তাহ | due dates + exams | 7-day strip, tap a day → filtered list |
| 7 | আপনার জন্য কোর্স | ranking §6.5 | 4–8 cards |

**Rules:** widgets load independently in parallel (one failing never blanks the page); dashboard API returns a single aggregate payload (`GET /api/v1/dashboard`) with per-widget sections and per-section error flags; cached data shown instantly then refreshed. Not-connected state shows the connect card in place of widgets 2–3.

### 6.3 Google Classroom integration (read-only)
- **Connect:** OAuth consent with scopes `openid email profile classroom.courses.readonly classroom.coursework.me.readonly classroom.courseworkmaterials.readonly classroom.announcements.readonly classroom.push-notifications`. Web: auth-code flow. Mobile: server auth code (Android Credential Manager + Authorization API; iOS GoogleSignIn). Tokens stored server-side, encrypted (AES-256-GCM with key from env/KMS).
- **Data pulled:** active courses (name, section, room, teacher names, alternateLink), courseWork (title, description, due date/time, max points, materials, alternateLink), my studentSubmissions (state, late flag, assigned grade), courseWorkMaterials, announcements.
- **Sync strategy:**
  1. Initial full sync on connect (job `classroom.fullSync`).
  2. Pub/Sub push: register feeds (COURSE_WORK_CHANGES per course, COURSE_ROSTER_CHANGES); consumer job maps events → targeted refetch; registrations renewed before expiry (job `classroom.renewRegistrations`, daily).
  3. Polling fallback every ~15 min for users active in the last 7 days; on app open; manual refresh.
  4. Diff against cache → emit notification events (new / changed / graded).
- **Screens:** class list (cards with color, unread count), class stream (tabs: Stream, Assignments, Materials), all assignments (filters: status, class, due range), calendar (month/week, dots for due dates & exams), assignment detail sheet ("Classroom-এ খুলুন").
- **Disconnect:** revoke token at Google, delete cache, stop registrations.
- **Errors:** `invalid_grant` → reconnect prompt; Workspace admin restriction → explanatory screen; 429/quota → exponential backoff, show last sync time.
- **Disclosure:** "Google-এর সঙ্গে যুক্ত নয়" near Classroom features; Google API Services User Data Policy compliance (limited use).

### 6.4 Notifications
See §10.

### 6.5 Discovery: search, browse, ranking
- **Smart search box:** one input for university, subject, course code, seller name; autocomplete grouped by type; recent searches; Bangla + English + transliteration ("ডাটা স্ট্রাকচার" = "data structure" = "DS").
- **Filters:** university, department, semester, subject, course code, batch, section, exam type, course type, price (free/paid/range), language, rating ≥, duration. None required.
- **Sort:** best match (default), newest, rating, price ↑/↓, popular.
- **Compatibility badge:** ✅ "আপনার ইউনিভার্সিটির সিলেবাস" when a course target matches the student's institution+department; ⚠️ "ভিন্ন সিলেবাস হতে পারে" otherwise.
- **Zero results:** illustration + "এই কোর্স চাই" → course request prefilled.
- **Ranking score:**
```
score = match_weight          # 100 own inst+dept+semester+batch, 80 inst+dept+semester, 50 same subject other inst, 30 general, 10 related
      + 4 * rating_bayesian   # 0–5
      + exam_proximity_boost  # up to +40 when student's exam for that subject is ≤14 days and course exam_type matches
      + popularity            # log(1 + enrollments_last_30d) * 5
      + 5 if seller verified
      + freshness             # up to +5 for courses updated in last 60 days
```
Implemented as a pure function with unit tests; Meilisearch handles text relevance, server re-ranks top N.
- **Browse pages:** universities grid (type filter: Public/Private/College/Polytechnic) → university detail (departments tabs, semester pills, subjects, courses); subjects list → subject detail.

### 6.6 Course details
Breadcrumb (University › Dept › Semester › Subject) · title · short description · seller card (verified ✔, rating, students) · chips (exam type, course type, language, duration) · compatibility badge · "যা শিখবেন" · curriculum (collapsible; preview ▶, ⭐ important, durations) · resources summary · "এই কোর্স কাদের জন্য নয়" · reviews (distribution + list) · FAQ · disclaimer "সম্ভাব্য গুরুত্বপূর্ণ টপিক — নিশ্চয়তা নয়" · sticky buy card (price ৳, strikethrough, % off, coupon, Buy, Preview, includes list, refund note, share, wishlist). Mobile: sticky bottom bar. Enrolled users see "শেখা চালিয়ে যান".

### 6.7 Checkout & payments
- **Flow:** create order (`pending`) → choose method (bKash, Nagad, Rocket, card) → redirect/hosted page → gateway callback + **server-side validation API call** → order `paid` → enrollment created → invoice PDF generated → success screen. Client-side "success" is never trusted.
- **Idempotency:** unique (gateway, gateway_trx_id); webhook handler is idempotent; order state machine `pending → paid | failed | cancelled`, `paid → refunded | partially_refunded`.
- **Coupons:** percent/fixed, scope (platform / seller / course), usage limits, per-user limit, expiry, min amount.
- **Bundles:** a bundle is a product containing courses (e.g. Midterm + Final); buying it enrolls all.
- **Refunds:** student requests within 48 h if < 20% watched; admin approves; ledger reversal; gateway refund API where supported, else manual with record.
- **Revenue split:** platform commission % per seller (default 20%; launch partners 0–10% for 3 months); recorded in ledger on payment; available balance after a 7-day hold.
- **Payouts:** seller requests (min ৳500) to bKash/Nagad/bank; admin approves → marks paid with reference; ledger entry.
- **Invoices:** PDF with ShikkhaSathi branding, order id, items, VAT line if configured.
- **Failure copy:** "পেমেন্ট সম্পন্ন হয়নি। চিন্তা নেই, আপনার টাকা কাটা হয়নি।" + retry.

### 6.8 Learning
- **Player:** HLS adaptive (360p/480p/720p), speed 0.5–2×, captions, chapter markers, resume from last position, keyboard shortcuts (web), moving watermark (student phone last 4 + id), "Mark complete", next lesson, lesson-level doubt input. Low-data mode forces 360p.
- **Access control:** signed playback URL (≤ 2 h) issued only to enrolled users within access period; device limit; concurrent stream limit 1.
- **Progress:** heartbeat every 15 s (`watched_seconds`), lesson complete at ≥ 90% or manual; course progress % = completed lessons / total.
- **My Courses:** grid with progress rings, filters (in progress / completed / expiring).
- **Notes & bookmarks:** timestamped notes per lesson, bookmarks, searchable.
- **Q&A:** per lesson; seller answers; notifications both ways.
- **Reviews:** verified purchase only, after ≥ 20% progress; 1–5 stars + text; one per user per course; seller can reply once.
- **Offline (mobile):** encrypted downloads, expire with access, max 3 devices.

### 6.9 Exam Mode
- Student creates an exam plan: subject, exam type, date, (optional) linked courses.
- System generates a **study plan**: distributes chapters/lessons of linked courses (⭐ important first) across days until the exam, leaving the last day for revision; recomputes when progress changes.
- Screen: gradient countdown card + readiness ring (weighted: lessons done 60%, mock score 30%, flashcards 10%) · আজকের প্ল্যান checklist · syllabus checklist by chapter (বাকি / চলছে / শেষ) · ⭐ important topics · short lectures · previous questions by year · flashcards (swipe) · mock test card · "পরীক্ষার আগের রাত" revision pack (unlocked T-1 day).
- Confetti when the daily plan is completed.

### 6.10 Mock tests
- Created by sellers inside courses (MCQ v1; single correct answer; optional explanation; chapter tag).
- Timed (default 30:00), navigator drawer, flag for review, auto-submit on timeout, resume if app closed (server holds attempt).
- Result: score ring, correct/wrong/skipped, chapter-wise bars, review wrong answers, retry (new attempt, shuffled).

### 6.11 Course request board
Students post requests (institution, subject, exam type, note); others upvote; sellers click "আমি বানাব" (status → Teacher working); when a linked course is published, requesters are notified (status → Published). Duplicate detection by (institution, subject master, exam type).

### 6.12 Teacher Studio (seller)
- **Application & verification:** profile, institution, proof (university email OTP / ID card upload / LinkedIn / reference), payout details. Admin approves → Verified ✔ badge.
- **Dashboard:** KPIs (total earnings, this month, students, avg rating), revenue chart 7/30/90 d, top courses, recent enrollments, unanswered Q&A, balance + "টাকা তুলুন", verification banner.
- **Course wizard (5 steps, autosave, live preview card):**
  1. Basics — title, short description, cover image, intro video, language.
  2. Target — institutions (multi) or "General / Cross-University", department, program, academic level, semester, subject (autocomplete from Subject Master; "propose new subject"), course code, batch chips, section chips, exam type, course type. Multiple targets allowed.
  3. Content — Section → Chapter → Lesson builder (drag & drop); lesson types: video, PDF, text note, quiz, mock test; mark Preview / ⭐ Important; resumable uploads with progress; syllabus coverage list.
  4. Pricing — price, discount, access type (lifetime / N days / until exam date), coupons, bundle membership.
  5. Review & publish — validation checklist, declaration (own content, no guarantee claims) → "রিভিউয়ের জন্য পাঠান".
- **Course states:** draft → in_review → published | changes_requested | rejected; published → unlisted/archived. Edits to a published course create a revision that goes through review for title/target/price changes; content additions go live immediately but are flagged for spot review.
- Other pages: students list, Q&A inbox, reviews (reply), analytics (views, conversion, completion), coupons CRUD, earnings ledger + payouts, storefront settings (slug, bio, cover).

### 6.13 Admin panel
Dashboard (GMV, orders, new users, pending approvals, refund rate) · Seller verification queue · Institution requests (approve, merge duplicates, aliases, logo) · Catalog (departments, programs, subject master, institution subjects/course codes) · Course review queue (table + side drawer with checklist: content quality, no guarantee claims, correct targeting, copyright) · Orders & refunds · Payouts approval · Users (search, roles, suspend) · Reports/moderation (copyright reports, abuse) · Broadcast notifications · Settings (commission defaults, refund window, feature flags) · **Integrations** (Brevo email with "টেস্ট মেইল পাঠান", payment gateway, SMS, video provider, Google OAuth + Pub/Sub, push, search; secrets write-only and masked; "সংযোগ পরীক্ষা করুন" per integration) · **System health** (CORE + CONTENT database status, migration version, queue status; read-only) · Email logs · Audit log. Every admin action is audit-logged.

### 6.14 Profile & settings
Academic info (editable; changing institution re-ranks feed) · Google Classroom connection (status, last sync, disconnect) · notification preferences per type × channel · devices (sign out remotely) · payment history + invoices · language (বাংলা/English) · theme (system/light/dark) · low-data mode · delete account (30-day grace, then hard delete except legally required financial records).

---

## 7. Data model

### 7.0 Two Supabase databases
| Database | Tables (sections below) |
|---|---|
| **CORE** — people, money, private data | §7.1 Identity · §7.4 Commerce (incl. **invoices**) · learning *activity* from §7.5 (`lesson_progress`, `notes`, `bookmarks`, `questions`, `answers`, `reviews`, `exam_plans`, `mock_attempts`, `mock_answers`, `wishlists`, `course_requests`, `course_request_votes`) · §7.6 Classroom · §7.7 Notifications & system |
| **CONTENT** — catalog & learning material | §7.2 Catalog · §7.3 Courses & content (incl. **video links/assets and document links**) · learning *material* from §7.5 (`mock_tests`, `mock_questions`, `mock_options`, `flashcards`) |

Rules:
- No foreign keys across databases. Cross-references are uuids validated in services; cross-database reads are batched.
- Each database has its own migrations and seed.
- Access is server-only (pooler port 6543 for the app, direct 5432 for migrations).
- RLS is enabled with no policies on every table.
- Storage buckets live with the data they belong to.

All tables have `id` (uuid v7), `created_at` and `updated_at`; soft delete (`deleted_at`) where noted. Money is stored as integer **paisa**.

### 7.1 Identity
| Table | Key fields |
|---|---|
| `users` | role[] (student/seller/admin), name, phone (unique, nullable), email (unique, nullable), password_hash, avatar_url, locale, status, deleted_at |
| `auth_otps` | phone, code_hash, purpose, expires_at, attempts |
| `sessions` | user_id, refresh_token_hash, device_id, device_name, platform, last_seen_at, revoked_at |
| `student_profiles` | user_id, institution_id, department_id, program_id, semester, batch, section, low_data_mode |
| `seller_profiles` | user_id, slug, bio, cover_url, institution_id, verification_status, verification_method, commission_rate, payout_method, payout_account_masked |

### 7.2 Catalog
| Table | Key fields |
|---|---|
| `institutions` | name, slug, type (public/private/college/polytechnic), logo_url, aliases[], status (pending/approved/merged), merged_into_id |
| `institution_requests` | requested_by, name, type, note, status |
| `departments` | institution_id, name, short_name |
| `programs` | department_id, name, level |
| `subject_masters` | name, slug, aliases[], category |
| `institution_subjects` | institution_id, department_id, program_id, semester, subject_master_id, course_code |

### 7.3 Courses & content
| Table | Key fields |
|---|---|
| `courses` | seller_id, title, slug, short_description, description, cover_url, intro_video_id, language, exam_type, course_type, price, compare_at_price, access_type, access_days, status, published_at, rating_avg, rating_count, enrollment_count, deleted_at |
| `course_targets` | course_id, institution_id (null = general), department_id, program_id, semester, subject_master_id, institution_subject_id, course_code, batches[], sections[], is_general |
| `course_sections` / `course_chapters` | course_id / section_id, title, position |
| `lessons` | chapter_id, title, type (video/pdf/text/quiz/mock), position, duration_sec, video_asset_id, document_id, body, is_preview, is_important |
| `video_assets` | provider (bunny/cloudflare/mux/external_link), provider_video_id, external_url, status, duration_sec |
| `documents` | course_id, owner_id, kind (notes/pdf/previous_questions/slides/link), title, storage_bucket, storage_key, external_url, mime, size |
| `course_revisions` | course_id, payload_json, status, reviewer_id, notes |
| `bundles` / `bundle_items` | title, price / bundle_id, course_id |

### 7.4 Commerce
| Table | Key fields |
|---|---|
| `orders` | student_id, status, subtotal, discount, total, coupon_id, currency |
| `order_items` | order_id, course_id or bundle_id, price |
| `payments` | order_id, gateway, gateway_trx_id (unique per gateway), amount, status, raw_payload, validated_at |
| `coupons` | code, scope, seller_id, course_id, type, value, max_uses, per_user_limit, min_amount, starts_at, expires_at |
| `coupon_redemptions` | coupon_id, user_id, order_id |
| `enrollments` | student_id, course_id, order_id, access_until, status |
| `refunds` | order_id, requested_by, reason, status, amount, processed_by |
| `ledger_entries` | seller_id, type (sale/commission/refund/payout/adjustment), amount (+/-), order_id, payout_id, available_at, balance_after |
| `payouts` | seller_id, amount, method, status, reference, processed_by |
| `invoices` | order_id, user_id, number (e.g. SS-2026-000123), billing_name, billing_phone, billing_email, items_json, subtotal, discount, total, storage_key (PDF in CORE `invoices` bucket), emailed_at |

### 7.5 Learning
| Table | Key fields |
|---|---|
| `lesson_progress` | student_id, lesson_id, watched_seconds, last_position_sec, completed_at |
| `notes` / `bookmarks` | student_id, lesson_id, timestamp_sec, body |
| `questions` / `answers` | lesson_id, author_id, body, status |
| `reviews` | course_id, student_id, rating, body, seller_reply, verified |
| `exam_plans` | student_id, subject_master_id, exam_type, exam_date, course_ids[], plan_json, readiness |
| `mock_tests` / `mock_questions` / `mock_options` | course_id, title, duration_sec / test_id, body, explanation, chapter_tag / question_id, body, is_correct |
| `mock_attempts` / `mock_answers` | test_id, student_id, started_at, submitted_at, score / attempt_id, question_id, option_id, flagged |
| `flashcards` | course_id, front, back |
| `course_requests` / `course_request_votes` | requester_id, institution_id, subject_master_id, exam_type, note, status, linked_course_id / request_id, user_id |
| `wishlists` | user_id, course_id |

### 7.6 Classroom (external cache — never mixed with marketplace tables)
| Table | Key fields |
|---|---|
| `google_connections` | user_id, google_user_id, email, refresh_token_enc, scopes[], status, last_full_sync_at, last_sync_at, error_code |
| `classroom_courses` | user_id, gc_course_id, name, section, room, teacher_names, alternate_link, color, state |
| `classroom_coursework` | user_id, gc_course_id, gc_coursework_id, title, description, due_at, max_points, materials_json, alternate_link, updated_at_gc |
| `classroom_submissions` | user_id, gc_coursework_id, state, late, assigned_grade, updated_at_gc |
| `classroom_materials` | user_id, gc_course_id, gc_material_id, title, materials_json, alternate_link |
| `classroom_announcements` | user_id, gc_course_id, gc_announcement_id, text, alternate_link, updated_at_gc |
| `classroom_feed_items` | user_id, kind, ref_id, occurred_at, read_at |
| `classroom_push_registrations` | user_id, feed_type, gc_course_id, registration_id, expires_at |

### 7.7 Notifications & system
| Table | Key fields |
|---|---|
| `notifications` | user_id, type, title, body, data_json, dedupe_key (unique), read_at |
| `notification_preferences` | user_id, type, channel (in_app/push/email/sms), enabled |
| `push_tokens` | user_id, platform, token, last_seen_at |
| `reports` | reporter_id, target_type, target_id, reason, status |
| `audit_logs` | actor_id, action, entity, entity_id, before_json, after_json, ip |
| `settings` | key, value_json (non-secret business settings) |
| `integration_settings` | provider (brevo/payment/sms/video/google/push/search), key, value_encrypted, is_secret, updated_by, updated_at |
| `email_logs` | to_email, template, locale, status (queued/sent/failed), provider (brevo_api/brevo_smtp/fake), provider_message_id, error, related_entity, created_at |

### 7.8 Indexes (minimum)
`course_targets(institution_id, department_id, semester, subject_master_id)`, `course_targets(course_code)`, `courses(status, published_at)`, `enrollments(student_id, course_id) unique`, `lesson_progress(student_id, lesson_id) unique`, `payments(gateway, gateway_trx_id) unique`, `classroom_coursework(user_id, due_at)`, `classroom_feed_items(user_id, occurred_at desc)`, `notifications(user_id, read_at, created_at desc)`, `ledger_entries(seller_id, created_at)`.

---

## 8. API specification

Base: `https://shikkhasathi.com/api/v1`. JSON. Auth: `Authorization: Bearer <access>` (mobile) / httpOnly cookie (web). Cursor pagination: `?cursor=&limit=` → `{ items, nextCursor }`. Error envelope: `{ error: { code, message, details? } }` with Bangla-friendly `message`. Contracts: backend OpenAPI (`docs/api/openapi.json`) + per-endpoint JSON Schemas (`backend/src/test/resources/contracts`); OpenAPI at `/api/v1/openapi.json` (non-prod) .

| Group | Endpoints |
|---|---|
| Auth | `POST /auth/otp/request`, `POST /auth/otp/verify`, `POST /auth/login`, `POST /auth/register`, `POST /auth/google`, `POST /auth/refresh`, `POST /auth/logout`, `GET /me`, `PATCH /me`, `GET/DELETE /me/sessions/:id` |
| Profile | `PUT /me/academic-profile`, `GET/PUT /me/notification-preferences`, `POST /me/push-tokens`, `DELETE /me` |
| Catalog | `GET /institutions`, `GET /institutions/:slug`, `POST /institution-requests`, `GET /departments?institutionId=`, `GET /programs?departmentId=`, `GET /subjects`, `GET /subjects/:slug` |
| Search | `GET /search?q=&filters…&sort=`, `GET /search/suggest?q=` |
| Courses | `GET /courses/:slug`, `GET /courses/:id/curriculum`, `GET /courses/:id/reviews`, `POST /courses/:id/reviews`, `POST/DELETE /wishlist/:courseId` |
| Dashboard | `GET /dashboard` (aggregate), `GET /recommendations` |
| Classroom | `GET /classroom/connect-url`, `POST /classroom/connect` (code), `DELETE /classroom/connection`, `GET /classroom/status`, `POST /classroom/sync`, `GET /classroom/courses`, `GET /classroom/courses/:id/stream`, `GET /classroom/assignments?status=&from=&to=`, `GET /classroom/calendar?from=&to=`, `GET /classroom/feed`, `POST /classroom/feed/read` |
| Webhooks | `POST /webhooks/google-pubsub`, `POST /webhooks/payments/:gateway`, `POST /webhooks/video/:provider` |
| Orders | `POST /orders`, `GET /orders/:id`, `POST /orders/:id/pay`, `GET /payments/callback/:gateway`, `GET /me/orders`, `GET /orders/:id/invoice`, `POST /orders/:id/refund-request`, `POST /coupons/validate` |
| Learning | `GET /me/courses`, `GET /lessons/:id/playback` (signed URL), `POST /lessons/:id/progress`, `POST /lessons/:id/complete`, `GET/POST/PATCH/DELETE /notes`, `GET/POST /lessons/:id/questions`, `POST /questions/:id/answers` |
| Exam | `GET/POST /exam-plans`, `GET/PATCH/DELETE /exam-plans/:id`, `POST /exam-plans/:id/tasks/:taskId/toggle`, `GET /courses/:id/flashcards` |
| Mock tests | `GET /mock-tests/:id`, `POST /mock-tests/:id/attempts`, `PATCH /attempts/:id` (answers), `POST /attempts/:id/submit`, `GET /attempts/:id/result` |
| Requests | `GET/POST /course-requests`, `POST /course-requests/:id/vote`, `POST /course-requests/:id/claim` |
| Notifications | `GET /notifications`, `POST /notifications/read`, `GET /notifications/unread-count` |
| Seller | `POST /seller/apply`, `GET /seller/dashboard`, `GET/POST /seller/courses`, `GET/PATCH /seller/courses/:id`, `PUT /seller/courses/:id/targets`, `PUT /seller/courses/:id/curriculum`, `POST /seller/uploads` (presigned / video create), `POST /seller/courses/:id/submit`, `GET /seller/students`, `GET /seller/questions`, `GET/POST/PATCH /seller/coupons`, `GET /seller/ledger`, `POST /seller/payouts`, `GET /seller/analytics`, `PATCH /seller/storefront` |
| Admin | `GET /admin/stats`, `GET/PATCH /admin/sellers/:id/verification`, `GET/PATCH /admin/institution-requests/:id`, CRUD `/admin/catalog/*`, `GET /admin/courses/review`, `POST /admin/courses/:id/decision`, `GET /admin/orders`, `PATCH /admin/refunds/:id`, `PATCH /admin/payouts/:id`, `GET/PATCH /admin/users/:id`, `GET/PATCH /admin/reports/:id`, `POST /admin/broadcasts`, `GET/PUT /admin/settings`, `GET /admin/audit-logs` |

Rate limits (per IP + user): OTP request 5/hour/phone, login 10/15 min, payment init 10/hour, classroom sync 1/min, search 60/min.

---

## 9. Background jobs & integrations

| Job (Redis job queue) | Trigger | Purpose |
|---|---|---|
| `classroom.fullSync` | on connect, on reconnect | Pull all classes, coursework, submissions, materials, announcements |
| `classroom.incrementalSync` | Pub/Sub event, every 15 min (active users), manual | Fetch changes, diff, emit feed + notification events |
| `classroom.pubsubConsume` | push subscription to `/webhooks/google-pubsub` | Validate, map to user/course, enqueue incremental sync |
| `classroom.renewRegistrations` | daily | Renew push registrations before expiry |
| `reminders.deadlines` | every 5 min | 24 h / 2 h before due, overdue nudge (deduped) |
| `notifications.dispatch` | on event | Fan out to in-app, FCM, APNs, web push, SMS per preferences |
| `search.indexCourse` | course publish/update | Upsert Meilisearch docs (course × target) |
| `payments.reconcile` | hourly | Re-validate pending payments with gateway |
| `ledger.release` | daily | Move held earnings to available after 7 days |
| `video.processed` | provider webhook | Mark asset ready, store duration |
| `exam.replan` | daily + on progress | Recompute study plans |
| `email.send` | on event | Render template, send via Brevo API (SMTP fallback), retry with backoff, write `email_logs` |
| `cleanup` | daily | Expired OTPs, sessions, orphan uploads, deleted accounts after grace |

Integrations are behind interfaces in the backend modules' `adapter/` packages: `PaymentGateway`, `VideoProvider`, `ObjectStorage` (Supabase Storage), `EmailProvider` (Brevo API / Brevo SMTP), `SmsProvider`, `PushProvider`, `GoogleClassroomClient`, `SearchClient`. Each has a fake implementation for local dev and tests. Each reads its credentials from the settings module (Admin panel value → env fallback → disabled/fake).

---

## 10. Notifications

| Type | Trigger | Default channels |
|---|---|---|
| `classroom.assignment_new` | new coursework in a connected class | in-app, push |
| `classroom.material_new` | new material uploaded | in-app, push |
| `classroom.announcement` | new announcement | in-app, push |
| `classroom.graded` | submission graded/returned | in-app, push |
| `classroom.due_24h` / `due_2h` | reminder | in-app, push (SMS optional) |
| `classroom.overdue` | once after due if not turned in | in-app |
| `exam.daily_plan` | morning of each plan day (08:00 Asia/Dhaka) | push |
| `course.qa_answered` / `seller.qa_new` | Q&A | in-app, push |
| `order.paid` / `order.refunded` | payment events | in-app, push, email |
| `request.published` | requested course published | in-app, push |
| `seller.course_decision` / `seller.payout_paid` / `seller.new_enrollment` | seller events | in-app, push, email |
| `admin.broadcast` | admin | chosen |

The email channel is delivered through Brevo (§16.1).
Rules: dedupe key per (user, type, ref); quiet hours 23:00–07:00 for non-urgent push; batch Classroom bursts (> 3 items in 10 min → one summary push); every type toggleable per channel; deep links to the right screen.

---

## 11. Security, privacy & compliance

- OWASP ASVS L2 as the baseline; Helmet-equivalent headers, strict CSP, CSRF protection for cookie auth, input validation everywhere (Bean Validation on the backend, Zod in website forms).
- Passwords: argon2id. OTPs hashed. Refresh tokens hashed + rotated with reuse detection.
- Google tokens encrypted at rest; minimal scopes; limited-use compliance; no sale/sharing of Classroom data; delete on disconnect.
- Video: signed URLs, watermark, device & concurrent-stream limits, DMCA/report flow.
- Payments: server-side validation, idempotency, reconciliation, no card data touches our servers.
- RBAC on every route; admin actions audited; principle of least privilege for DB users.
- Supabase: RLS enabled with no policies on every table in both databases, so the anon key can read nothing. The service-role key and DB URLs are server-only. A CI test fails if any table lacks RLS.
- Integration secrets (Brevo, gateways, SMS, Google client secret, etc.) are stored AES-256-GCM-encrypted in CORE `integration_settings`, are write-only via the API, masked in the UI, and every change is audit-logged.
- Privacy policy, terms, refund policy, copyright/takedown policy published at `shikkhasathi.com`. Data deletion on request. Final legal text reviewed by a lawyer.
- Backups: daily encrypted DB backup, 30-day retention, monthly restore test.

---

## 12. Performance & reliability

| Target | Value |
|---|---|
| Landing initial JS | ≤ 150 KB gzip |
| Student shell + `/home` | ≤ 200 KB gzip |
| Any route chunk | ≤ 100 KB gzip |
| Lighthouse mobile | Perf ≥ 90, A11y ≥ 95, BP ≥ 95, SEO ≥ 95 |
| Web vitals | LCP < 2.0 s, CLS < 0.05, INP < 200 ms |
| API p95 | < 200 ms (reads), < 500 ms (writes); `/dashboard` < 300 ms from cache |
| Android cold start | < 1.5 s mid-range device |
| Uptime | 99.5% (v1) |

Techniques: prerendered public pages, route splitting, Redis caching (dashboard aggregate 60 s, catalog 1 h), HTTP caching with ETags, brotli, CDN for assets, DB indexes (§7.8), N+1 checks, background work in workers, Baseline Profiles on Android. Observability: structured logs (pino), Sentry (web, server, mobile), health checks, job dashboards, uptime monitor.

---

## 13. Design system summary

Full rules: `CLAUDE.md` §7 and `docs/DESIGN_SYSTEM.md`. Visual reference: `Reference/stitch_campuscourse_design_system/`.
- Mood: calm, focused, confident; mobile-first; Bangla-first.
- Colors: Indigo `#4F46E5` / dark `#818CF8`; Emerald `#10B981`; Amber `#F59E0B`; Danger `#EF4444`; Background `#F8FAFC` / `#0B1020`; Surface `#FFFFFF` / `#131A2E`; Text `#0F172A` / `#E2E8F0`; Muted `#64748B` / `#94A3B8`; Border `#E2E8F0` / `#232C45`.
- Type: Hind Siliguri (bn, line-height 1.7) + Inter (en, tabular numbers). Scale 12/14/16/18/20/24/30/36/48.
- Shape: 8px grid; radius 16/12/full; soft layered shadow + 1px border.
- Key components: Course Card, Assignment Row, Feed Item, Countdown Card, Progress Ring, Compatibility Badge, Lesson Row, Week Strip, Sidebar, Bottom Sheet, Data Table, Skeletons, Empty states, Toasts.
- Anti-AI-look rules apply to every screen.

---

## 14. Language, content & tone

- Bangla default, English toggle; all strings in i18n files (`bn.json`, `en.json`), mobile string resources generated from the same keys.
- Numbers: Bangla digits in Bangla UI for dates/counts (configurable), prices with ৳; dates in Asia/Dhaka.
- Tone: friendly, simple, encouraging. ✅ "আপনার Midterm আর ৫ দিন পর। চলুন গুছিয়ে নিই।" ❌ "ইউজারের পরীক্ষা নিকটবর্তী।"
- Errors are sympathetic and actionable.
- Disclaimers: "সম্ভাব্য গুরুত্বপূর্ণ টপিক — নিশ্চয়তা নয়", "কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়", "Google-এর সঙ্গে যুক্ত নয়".

---

## 15. Testing & quality

| Layer | Tools | Minimum |
|---|---|---|
| Shared | Vitest | contract schemas round-trip |
| Server | Vitest + test Postgres (Testcontainers) | services unit-tested; every route has an integration test; payments & Classroom sync with fakes |
| Client | Vitest + Testing Library, Playwright | component tests for design-system; E2E: sign-up → onboarding → dashboard; search → buy → learn; seller create → admin approve |
| Mobile | JUnit/kotlin.test, Compose UI tests, Macrobenchmark | ViewModels unit-tested; dashboard & player UI tests; startup benchmark |
| Quality gates | ESLint, `tsc --noEmit`, Prettier, ktlint, detekt, Lighthouse CI, bundle-size check | CI blocks merge on failure |

---

## 16. Environments, deployment & configuration

| Env | URL | Notes |
|---|---|---|
| local | `localhost` | docker-compose (db-core, db-content, redis, meilisearch) or Supabase CLI; fake providers; seed data incl. fake Classroom |
| staging | `staging.shikkhasathi.com` | separate Supabase CORE + CONTENT projects; sandbox gateways; Google OAuth testing mode |
| production | `shikkhasathi.com` | production Supabase CORE + CONTENT projects |

Deploy: Docker images for `server` (serves client build) and `worker`; Caddy/Nginx with TLS; GitHub Actions (lint → test → build → deploy); DB migrations run as a release step.

Environment variables (see `.env.example`):
- **Bootstrap (env only):**
  - app: `NODE_ENV, APP_URL, API_URL`
  - CORE database: `CORE_DATABASE_URL` (pooler, 6543), `CORE_DIRECT_URL` (5432), `SUPABASE_CORE_URL`, `SUPABASE_CORE_SERVICE_ROLE_KEY`
  - CONTENT database: `CONTENT_DATABASE_URL`, `CONTENT_DIRECT_URL`, `SUPABASE_CONTENT_URL`, `SUPABASE_CONTENT_SERVICE_ROLE_KEY`
  - secrets & infra: `REDIS_URL, ENCRYPTION_KEY, JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, SENTRY_DSN`
- **Integrations (env fallback; Admin panel overrides):**
  - email: `BREVO_API_KEY, BREVO_SENDER_NAME, BREVO_SENDER_EMAIL, BREVO_REPLY_TO, BREVO_SMTP_HOST, BREVO_SMTP_PORT, BREVO_SMTP_LOGIN, BREVO_SMTP_KEY`
  - Google: `GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_ANDROID_CLIENT_ID, GOOGLE_IOS_CLIENT_ID, GOOGLE_PUBSUB_TOPIC, GOOGLE_PUBSUB_VERIFICATION_TOKEN`
  - payments: `PAYMENT_GATEWAY, SSLCOMMERZ_STORE_ID, SSLCOMMERZ_STORE_PASSWORD, SSLCOMMERZ_SANDBOX`
  - video: `VIDEO_PROVIDER, BUNNY_STREAM_LIBRARY_ID, BUNNY_STREAM_API_KEY, BUNNY_TOKEN_KEY`
  - SMS: `SMS_PROVIDER, SMS_API_KEY, SMS_SENDER_ID`
  - push: `FCM_SERVICE_ACCOUNT_JSON, APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY, WEB_PUSH_PUBLIC_KEY, WEB_PUSH_PRIVATE_KEY`
  - search: `MEILI_HOST, MEILI_MASTER_KEY`

### 16.1 Configuration precedence & Admin-panel settings
1. **Bootstrap secrets** (database URLs, Supabase service keys, Redis, encryption/JWT keys) come **only from env**. The Admin panel shows database health read-only and never edits these.
2. **Integration credentials and business settings** are editable in **Admin → সেটিংস → Integrations**. They are stored encrypted in CORE `integration_settings`. **Effective value** = Admin panel value if set → else env var → else the integration is disabled (its Fake adapter is used outside production).
3. Values are cached for ~60 s and invalidated on save. Secrets are write-only and masked (`••••1234`). Each integration has a test button. Every change is audit-logged.
4. **Brevo email:**
   - Admin fields: API key, sender name, sender email (verified in Brevo), reply-to, optional SMTP fallback (smtp-relay.brevo.com:587 + SMTP login/key).
   - Buttons: "টেস্ট মেইল পাঠান" sends a branded test email to the admin.
   - Before going live: add Brevo's SPF/DKIM/DMARC DNS records for `shikkhasathi.com` (documented in `docs/RUNBOOK.md`).

---

## 17. Analytics & success metrics

- **North Star:** weekly students who used ShikkhaSathi to prepare for an exam (dashboard active + course/exam activity).
- Acquisition: sign-ups by institution, referral %. Activation: onboarding completed, Classroom connected %, first course viewed/bought.
- Engagement: dashboard DAU/WAU, assignments viewed before due, notification open rate.
- Revenue: GMV, conversion, AOV, bundle attach rate, refund rate.
- Learning: completion rate, watch time, mock test scores.
- Supply: active sellers, subject coverage, request board fulfilment.
- Search: zero-result rate.
Event tracking via a privacy-respecting analytics tool (self-hosted PostHog or Plausible); no Classroom content in analytics.

---

## 18. Roadmap & launch checklist

Build phases are defined in `CLAUDE.md` §9 (0 Audit → 1 Foundation → 2 Master data → **3 Dashboard + Classroom** → 4 Teacher Studio → 5 Discovery → 6 Checkout → 7 Learning → 8 Exam & Mock → 9 Dashboard completion → 10 Admin/seller completion → 11 Mobile parity → 12 Polish & hardening).

### Launch checklist
- [ ] Domain `shikkhasathi.com` DNS, TLS, email (support@, no-reply@)
- [ ] Google Search Console domain verification
- [ ] Google OAuth consent screen + scope verification approved
- [ ] Privacy, Terms, Refund, Copyright pages live
- [ ] Payment gateway merchant account live (trade license, bank account)
- [ ] Video provider + token auth configured
- [ ] SMS sender ID approved
- [ ] Play Store listing (and later App Store) under ShikkhaSathi
- [ ] First university: 5–10 verified sellers, ≥ 1 quality course per key subject
- [ ] Backups + monitoring + alerting verified
- [ ] Launch 2–3 weeks before the target university's midterm season

---

## 19. Risks

| Risk | Mitigation |
|---|---|
| Google verification delay | Start early; build in testing mode; dashboard still valuable via courses/exams |
| University Workspace blocks third-party apps | Clear in-app explanation; test with target university accounts before launch |
| Video piracy | Signed URLs, watermark, device limits, takedown flow |
| Copyright (university questions/books) | Seller declaration, report button, takedown policy |
| Use of university names/logos | "Not affiliated" disclaimer; no official claims |
| Fake or low-quality sellers | Verification, review queue, ratings, refunds |
| Payment fraud / double charge | Idempotency, reconciliation, server validation |
| Teacher-side university policies | Terms place responsibility on sellers |
| Cold start (no supply/demand) | One university first, low commission for launch partners, campus ambassadors, request board |

---

## 20. Open questions

Decide and record in `docs/DECISIONS.md`:
1. Payment aggregator: SSLCommerz vs aamarPay (fees, settlement time, bKash/Nagad coverage).
2. Video provider final choice and cost per GB.
3. First launch university and subject list.
4. Default commission rate and payout schedule (weekly vs monthly).
5. iOS release timing.
6. SMS provider for OTP and reminders.
