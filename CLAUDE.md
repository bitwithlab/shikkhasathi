# CLAUDE.md — ShikkhaSathi (শিক্ষাসাথী)

> এই ফাইলটি এই রিপোজিটরির জন্য Claude Code-এর প্রধান নির্দেশনা। প্রতিটি সেশনের শুরুতে এটি পড়ুন এবং অক্ষরে অক্ষরে মানুন।
> Instructions below are in English for precision. UI copy stays Bangla-first.

**Product name: ShikkhaSathi** (Bangla: শিক্ষাসাথী) · **Domain: shikkhasathi.com**
The spec files call it "CampusCourse" — that was a working name. **Everywhere in code, UI, package names, meta tags, emails and store listings use "ShikkhaSathi".** Product brief: `docs/PROJECT.md`.

| Host | Purpose |
|---|---|
| `shikkhasathi.com` | Unified web platform (public site, student, seller/teacher, admin) served by `Web/server` |
| `shikkhasathi.com/api/v1` | REST API (same server) |
| `admin.shikkhasathi.com` | Optional alias → same app, opens `/admin` |
| `media.shikkhasathi.com` | Static assets / CDN (video stays on the video provider with signed URLs) |
| Mobile bundle id | `com.shikkhasathi.app` (Android + iOS) |

---

## 0. Mission

Build the **complete, production-quality** ShikkhaSathi platform — a **unified academic dashboard (Google Classroom)** plus a **multi-university exam-prep course marketplace** for Bangladeshi students — end to end:

| Surface | Stack | Folder |
|---|---|---|
| All web interfaces (public, student, seller/teacher, admin) | **React + Vite + TypeScript** | `Web/client` |
| Website + API server | **Node.js + TypeScript (Fastify)**, PostgreSQL, Redis | `Web/server` |
| Shared types, Zod schemas, API contracts, design tokens | TypeScript | `Web/shared` |
| Mobile app (Android first, iOS from the same code) | **Kotlin + Jetpack Compose (Compose Multiplatform)** | `App/mobile` |
| Infra / deploy | Docker, docker-compose, CI, Nginx/Caddy | `infra/` |
| Dev scripts (codegen, check-all, seed wrappers) | Bash / Node | `scripts/` |
| **All documentation** | Markdown | **`docs/`** |

Done means: every screen in the design reference exists, works against the real API with seed data, has loading/empty/error states, looks hand-crafted (not AI-generated), and is fast.

---

## 0A. Core experience — Home = Unified Academic Dashboard (TOP PRIORITY)

**The logged-in student's home page IS the dashboard.** Not a course feed. It shows the student's whole academic life in one place: Google Classroom is the main live data source, purchased ShikkhaSathi courses sit alongside it. All other features live in the **sidebar** (web) / bottom bar + drawer (mobile).

### Home dashboard (`/home` on web, `HomeScreen` on mobile) — widgets in this order
1. **Header:** greeting ("আসসালামু আলাইকুম, [নাম]"), academic chip (University · Dept · Semester), Classroom sync status ("সিঙ্ক হয়েছে ২ মিনিট আগে" + refresh), notification bell with unread count.
2. **সামনের অ্যাসাইনমেন্ট (Due soon):** Classroom assignments grouped Overdue / আজ / আগামীকাল / এই সপ্তাহ; course name, due time, status chip (Pending / Turned in / Graded / Missing), red/amber urgency; tap → detail sheet with "Classroom-এ খুলুন" deep link.
3. **নতুন আপডেট (New from your classes):** unified feed — new assignments, materials uploaded by teachers (PDF/slides/links), announcements, grades returned; newest first, unread highlighted.
4. **Upcoming exam countdown** (if set) → Exam Mode.
5. **পড়া চালিয়ে যান (Continue learning):** last purchased course with progress + resume.
6. **এই সপ্তাহ:** 7-day strip with dots for deadlines and exams.
7. **আপনার জন্য কোর্স:** recommendations (small, last — never pushes academic info down).

**Not-connected state:** calm, prominent card "Google Classroom সংযুক্ত করুন — সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায়" + privacy line ("আমরা শুধু পড়ার অনুমতি চাই"); course/exam widgets still render. Never an empty page.

### Sidebar navigation (web, student) — label / route
হোম `/home` · আমার ক্লাসরুম `/classroom` (class list → per-class stream, assignments, materials, announcements) · অ্যাসাইনমেন্ট `/classroom/assignments` · ক্যালেন্ডার `/classroom/calendar` · আমার কোর্স `/my-courses` · কোর্স খুঁজুন `/explore` · Exam Mode `/exam-mode` · Mock Test `/mock-tests` · নোট ও বুকমার্ক `/notes` · কোর্স অনুরোধ `/requests` · নোটিফিকেশন `/notifications` · প্রোফাইল ও সেটিংস `/profile`. Collapsible on desktop, off-canvas on mobile web. Users with the seller role see a "Teacher Studio" switch at the bottom; admins see "Admin".

**Mobile app:** bottom bar = হোম · ক্লাসরুম · কোর্স · Exam · প্রোফাইল; the rest in a drawer from the home header.

### Google Classroom integration
- **Read-only, student side.** Never post, submit or edit in Classroom; deep-link via `alternateLink`.
- **OAuth scopes (minimal):** `openid email profile`, `classroom.courses.readonly`, `classroom.coursework.me.readonly`, `classroom.courseworkmaterials.readonly`, `classroom.announcements.readonly`, `classroom.push-notifications`. Anything more must be logged in `docs/DECISIONS.md`.
- **Tokens server-side only:** web uses the authorization-code flow; mobile obtains a **server auth code** (Android: Credential Manager + Google Authorization API; iOS: GoogleSignIn SDK) and sends it to the API. Refresh tokens encrypted at rest; disconnect = revoke + delete cached data.
- **Sync:** full sync on connect → `classroom_*` cache tables (external source, separate from marketplace tables, spec §10). Then:
  - **Push:** Classroom push notifications via Google Cloud Pub/Sub registrations (course-work changes per course, roster changes); a worker consumes the topic and does incremental fetches; a scheduled job renews expiring registrations.
  - **Fallback polling:** incremental sync ~every 15 min for users active in the last 7 days, plus on app open / pull-to-refresh (rate-limited).
- **Notifications we generate** (in-app center + FCM/APNs push + web push; SMS optional): new assignment, new material, new announcement, grade returned, reminders 24 h and 2 h before due, one overdue nudge. Each type toggleable in Settings. Dedupe by (user, item id, event type).
- **Failure copy (Bangla, friendly):** revoked token → "আবার সংযুক্ত করুন"; university Workspace blocks third-party apps → explain + suggest asking IT / using personal account; quota errors → back off, show last-synced time.
- **Google verification prerequisites** (track in `docs/PROGRESS.md`): verified domain `shikkhasathi.com`, live `/privacy` and `/terms`, consent screen with ShikkhaSathi name + logo, scope justification + demo video. Until verified the app runs in Testing mode with limited test users.

---

## 1. Sources of truth (read before writing any code)

1. `CLAUDE.md` (this file)
2. **`docs/PROJECT_SPEC.md` — the full specification (features, data model, API, jobs, notifications, security, performance). Single source of truth.**
3. `docs/PROJECT.md` — one-page product brief.
3b. `docs/CAMPUS_MARKETPLACE_SPEC.md` — original spec, background only; `PROJECT_SPEC.md` wins on any difference.
4. `docs/UI_PROMPTS.md` — screen list, design tokens, copy, refinement rules.
5. `Reference/stitch_campuscourse_design_system/` — visual reference (Stitch exports: per-screen `code.html` / `screen.png`, maybe `DESIGN.md`). **Look at the PNGs with the Read tool** before building each screen. Replace "CampusCourse" with "ShikkhaSathi".
6. `docs/PROGRESS.md` — what's done (create if missing).
7. `README.md`, `.env.example`.

If the spec files are not in `docs/` yet, move them there first.

### Deliberate deviations from the spec
- Spec §16 says Next.js + FastAPI → **React + Vite client and a Node.js/TypeScript server** in one unified `Web/` platform (user's folder architecture, §3). Shared types/contracts live in `Web/shared`.
- Spec says "Android later" → **mobile app is in scope now** (Compose Multiplatform: Android first, iOS from the same code).
- Spec puts Google Classroom in V1.5 and makes Home a course feed → **Classroom is core (Phase 3); Home is the dashboard** (§0A).
- Spec "Teacher" = code name **seller** (folder `features/seller`, module `seller`); the UI still says "শিক্ষক / Teacher Studio".
- Brand "CampusCourse" → **ShikkhaSathi**.
- Everything else in the spec (data model §15, ranking §6, payments §8, risks §18) stands. Conflicts → record in `docs/DECISIONS.md`, pick the option closest to the spec's intent.

---

## 2. Documentation rule (IMPORTANT)

**Every non-code file you create — plans, notes, ADRs, API docs, screen maps, checklists, reports, test plans — goes inside `docs/`.** Never put `.md` files in the root, `Web/`, `App/` or elsewhere (a short `README.md` per top-level app is the only exception).

| File | Purpose |
|---|---|
| `docs/PROJECT_SPEC.md` | Full specification (provided — update it when scope changes, never contradict it silently) |
| `docs/PROJECT.md` | Product brief (provided — update if scope changes) |
| `docs/PROGRESS.md` | Phase checklist; tick items; short log per session (date, changes, next step) |
| `docs/ARCHITECTURE.md` | System diagram, folder layout, data flow, auth/payment/sync flows |
| `docs/DESIGN_SYSTEM.md` | Tokens, component inventory, do/don't — shared by web and mobile |
| `docs/SCREEN_MAP.md` | Every reference screen → web route / mobile screen → status |
| `docs/API.md` | Endpoint overview, error envelope, pagination, link to generated OpenAPI |
| `docs/DB_SCHEMA.md` | Tables, relations, indexes, migration list |
| `docs/CLASSROOM_INTEGRATION.md` | Scopes, token storage, sync + Pub/Sub, notification rules, verification checklist |
| `docs/DECISIONS.md` | ADR-style log |
| `docs/TESTING.md` | Test strategy + how to run suites |
| `docs/RUNBOOK.md` | Local setup, env vars, deploy, backup/restore, common failures |
| `docs/PERFORMANCE.md` | Budgets + latest measured numbers |

---

## 3. Repository layout (USER-DEFINED — follow exactly)

```
ShikkhaSathi/
├── CLAUDE.md
├── README.md
├── .env.example
├── Reference/                         # design reference — READ ONLY
├── Web/                               # one unified web platform (pnpm workspace: client, server, shared)
│   ├── client/                        # all website interfaces (React + Vite)
│   │   └── src/
│   │       ├── app/                   # entry, router, providers, layouts (PublicShell, StudentShell w/ sidebar, SellerShell, AdminShell), route guards
│   │       ├── components/            # design-system UI (Button, Card, CourseCard, Badge, ProgressRing, Sheet, Table, Skeleton, EmptyState…)
│   │       ├── features/
│   │       │   ├── auth/              # OTP/email login, onboarding (incl. "Connect Google Classroom" step)
│   │       │   ├── marketplace/       # landing, explore/search, universities, subjects, course details, checkout, course requests
│   │       │   ├── courses/           # course data hooks/components shared by marketplace, learning, seller
│   │       │   ├── learning/          # player, my courses, exam mode, mock tests, notes/bookmarks, Q&A, reviews
│   │       │   ├── student/           # HOME DASHBOARD, classroom (Google Classroom), calendar, notifications, profile/settings
│   │       │   ├── seller/            # Teacher Studio: dashboard, course wizard, students, Q&A, analytics, coupons, earnings, storefront
│   │       │   └── admin/             # admin panel (lazy-loaded chunk only)
│   │       └── lib/                   # api client + TanStack Query setup, i18n (bn/en), utils, analytics
│   ├── server/                        # website + API server (Node.js + TypeScript)
│   │   └── src/
│   │       ├── api/v1/                # route registration per module, versioned; thin handlers only
│   │       ├── config/                # env parsing (Zod), constants
│   │       ├── database/              # Drizzle schema, migrations, seed
│   │       ├── modules/               # domain modules: auth, catalog, courses, search, orders, payments, learning, exam, classroom, notifications, seller, admin
│   │       │                          #   each: *.routes.ts, *.service.ts, *.repository.ts, *.jobs.ts
│   │       ├── services/              # external adapters: payment gateways, video provider, google, meilisearch, storage, sms, push
│   │       └── common/                # errors, middleware, auth guards/RBAC, logger, pagination, idempotency, rate limit
│   └── shared/                        # shared types + API contracts
│       └── src/
│           ├── contracts/             # Zod request/response schemas per endpoint (single source of truth → OpenAPI)
│           ├── types/                 # inferred TS types, enums (roles, exam types, statuses)
│           └── tokens/                # design tokens JSON (generates CSS vars + Tailwind preset + Compose theme)
├── App/
│   └── mobile/                        # Compose Multiplatform project
│       ├── android/                   # Android application module (MainActivity, manifest, baseline profile, FCM)
│       ├── ios/                       # Xcode project hosting the shared Compose UI (APNs, GoogleSignIn)
│       ├── assets/                    # fonts (Hind Siliguri, Inter), illustrations, icons
│       └── src/
│           ├── core/                  # designsystem (theme from tokens), network (Ktor), database, datastore, model, player (expect/actual)
│           ├── shared/                # cross-feature UI components, utils, navigation
│           └── features/              # auth, onboarding, home, classroom, explore, coursedetail, checkout, player, mycourses, exammode, mocktest, notifications, profile, seller-lite
├── docs/                              # ALL documentation
├── infra/                             # docker-compose (postgres, redis, meilisearch, server), Dockerfiles, CI, reverse proxy
└── scripts/                           # gen-openapi, gen-tokens, gen-mobile-client, check-all.sh
```

Inspect what already exists in these folders first; extend, don't blindly overwrite. Never edit `Reference/`.

---

## 4. Server rules (`Web/server`)

- Node.js 22 LTS, TypeScript strict, **Fastify**, **Drizzle ORM** + drizzle-kit migrations, PostgreSQL 16, Redis, **BullMQ** for jobs (separate worker entry `src/worker.ts`).
- Validation + contracts: every route validates with the Zod schema from `Web/shared/contracts`; OpenAPI is generated from those schemas (`scripts/gen-openapi`) and consumed by the mobile app.
- Layering: `api/v1` (HTTP) → `modules/*/service` (business logic) → `repository` (DB). No business logic in handlers. External calls only through `services/` adapters.
- Data model: follow spec §15 exactly (institutions → departments → programs → institution_subjects; subject_masters; courses + **course_targets** one-to-many; sections/lessons; enrollments; progress; orders/payments; ledger; exam_plans; course_requests) + `classroom_*` cache tables + `notifications`, `push_tokens`.
- Search: **Meilisearch** (Bangla + English, typo tolerance, synonyms DS = Data Structures, DBMS = Database Management); index courses × targets; Postgres FTS fallback.
- Ranking: spec §6 score as a pure, unit-tested function.
- Auth: phone OTP (primary) + email + Google; JWT access (short) + refresh (httpOnly cookie on web, secure storage on mobile). Roles: guest, student, seller, admin. RBAC guards in `common/`.
- Payments: gateway adapter interface (SSLCommerz / aamarPay / bKash tokenized). **Idempotent webhooks** (unique trx id), server-side verification, then auto-enroll. Ledger-based seller earnings.
- Video: provider adapter (Bunny Stream / Cloudflare Stream / Mux), HLS, short-lived signed URLs, device limit, watermark data from API.
- Course workflow Draft → Review → Published (admin queue).
- Cursor pagination on every list; one error envelope; rate limits on OTP, login, payment init, manual Classroom refresh.
- Soft delete + audit log for payments, refunds, payouts, course status changes.
- The server also serves the built client (`Web/client/dist`) with long-cache hashed assets, compression (brotli), and prerendered public pages (§5.4).
- Seed (`database/seed`): 3 universities (Metropolitan University, North South University, BRAC University), departments, 5+ subjects (e.g. Data Structures CSE-2203), 10+ realistic Bangla demo courses with lessons, sellers (verified + pending), students, orders, reviews, **plus a fake Classroom dataset** (classes, assignments, materials, announcements, grades) so the dashboard works locally without Google.

---

## 5. Web client rules (`Web/client`)

### 5.1 Stack
- React 19, Vite, TypeScript strict.
- React Router with route-level `lazy()` splitting. Each feature exports its own routes; `app/` composes them.
- TanStack Query + a typed client built on `Web/shared/contracts`. Zustand only for small UI state.
- Tailwind CSS driven by tokens from `Web/shared/tokens`. Radix UI primitives, styled by us (no default shadcn look).
- React Hook Form + Zod (same schemas as the server).
- Lucide icons (1.75px stroke). Charts only in seller/admin, lazy-loaded.
- Video: hls.js in a custom player shell (speed, quality, captions, chapters, resume, shortcuts, moving watermark).
- i18n in `lib/i18n` (bn default, en toggle). **No hardcoded UI text.**

### 5.2 Routes
- Public: `/`, `/explore`, `/universities`, `/u/:slug`, `/subjects`, `/c/:slug`, `/t/:sellerSlug`, `/privacy`, `/terms`, `/refund`.
- Auth: `/auth`, `/onboarding`.
- Student shell (sidebar, §0A): `/home`, `/classroom`, `/classroom/:courseId`, `/classroom/assignments`, `/classroom/calendar`, `/my-courses`, `/learn/:courseId/:lessonId`, `/checkout/:id`, `/exam-mode`, `/mock-tests`, `/mock-test/:id`, `/notes`, `/requests`, `/notifications`, `/profile`. Logged-in users on `/` → redirect to `/home`.
- Seller shell: `/teach/*` (dashboard, courses, create wizard, students, Q&A, reviews, analytics, coupons, earnings, storefront settings).
- Admin shell: `/admin/*` (dashboard, seller verification, institution requests, course review queue + side drawer, subject master, payments & refunds, payouts, reports/moderation, users, settings, analytics). **Admin and seller code must be in separate lazy chunks — never downloaded by students.**

### 5.3 Performance budgets (record in `docs/PERFORMANCE.md`)
- Initial JS for landing ≤ **150 KB gzip**; student shell + `/home` ≤ 200 KB gzip; any route chunk ≤ 100 KB gzip.
- Lighthouse mobile: Performance ≥ 90, Accessibility ≥ 95, Best Practices ≥ 95, SEO ≥ 95.
- LCP < 2.0 s (simulated 4G), CLS < 0.05, INP < 200 ms.
- Route splitting, prefetch on hover/viewport, self-hosted subset fonts with `font-display: swap`, AVIF/WebP with explicit dimensions, lazy images, virtualized long lists, skeletons (no spinners), stale-while-revalidate caching, dashboard widgets load in parallel and render independently.
- No heavy libs on public pages. Check bundle visualizer every phase.

### 5.4 SEO
- Prerender public routes at build time (landing, universities, subjects, course details, seller storefront); per-page title/meta/Open Graph, JSON-LD (`Course`, `Organization`, `BreadcrumbList`), `sitemap.xml`, `robots.txt`, canonical `https://shikkhasathi.com/...`.

---

## 6. Mobile rules (`App/mobile`)

- **Kotlin + Jetpack Compose via Compose Multiplatform.** Android is the priority and must be fully done first; iOS builds from the same `src/` code (platform specifics via `expect/actual`).
- Material 3 with a **fully custom theme** generated from `Web/shared/tokens` (no default M3 purple). Fonts from `assets/`.
- Architecture: UDF — `ViewModel` → immutable `UiState` → stateless composables; Koin DI; Coroutines + Flow.
- Network: Ktor client + kotlinx.serialization; models generated/validated from the server's OpenAPI.
- Local data: Room (KMP) for offline cache (dashboard data, my courses, progress, exam plan), DataStore for prefs; tokens in Android Keystore / iOS Keychain.
- Navigation Compose (type-safe routes); bottom bar + drawer per §0A.
- Media: Android Media3 ExoPlayer (HLS), iOS AVPlayer — behind a common player interface; watermark overlay, resume, speed. Offline downloads encrypted and tied to account/device (WorkManager on Android).
- Images: Coil 3. Push: FCM (Android), APNs (iOS). Google auth per §0A. App Links / Universal Links for `https://shikkhasathi.com/...`.
- **Performance:** Android cold start < 1.5 s on a mid-range device; Baseline + Startup Profiles; R8 full mode; stable UI state classes, `key` in lazy lists, no work in composition; check Compose compiler stability reports. Record in `docs/PERFORMANCE.md`.
- Light/dark, font scaling to 200% without clipping Bangla text, TalkBack/VoiceOver labels, 48dp targets.

---

## 7. Design rules — beautiful, **never "AI-generated looking"**

Before building a screen: open its reference PNG, match layout and hierarchy, then raise quality with the rules below. Stitch HTML is a **visual reference only** — re-implement with our components and tokens; never paste Stitch markup into the codebase.

### Tokens (spec §14 / UI_PROMPTS Step 0)
Indigo `#4F46E5` primary, Emerald `#10B981` success/progress, Amber `#F59E0B` countdown/important, Danger `#EF4444`; light + dark sets; 8px grid; radius 16 (cards) / 12 (buttons, inputs) / full (chips); soft layered shadow + 1px border; Hind Siliguri (Bangla, line-height 1.7) + Inter (English, tabular numerals).

### Anti-"AI slop" checklist (reject your own output if any are true)
- ❌ Purple/violet gradients everywhere — allowed **only** on the landing hero and exam countdown card.
- ❌ Emoji as icons (Lucide only; emoji only in user content or one celebratory moment).
- ❌ Glassmorphism, neon glows, blurred blobs, random 3D shapes.
- ❌ Every section as identical centered cards in a 3-column grid — vary rhythm (split layouts, asymmetric grids, lists, full-bleed bands).
- ❌ Generic copy ("Unlock your potential", "Seamless experience", lorem ipsum) — use specific Bangla copy.
- ❌ Flat hierarchy — one dominant element and one primary CTA per screen.
- ❌ Over-rounded everything, heavy shadows, centered paragraphs.
- ❌ Fake stock avatars/stats — use illustrated avatars/initials and realistic numbers.
- ✅ Real Bangladeshi data: universities, course codes (CSE-2203), ৳ prices with strikethrough, batch/section.
- ✅ Typographic polish: no clipped যুক্তাক্ষর, aligned numerals, comfortable Bangla line-height.
- ✅ Purposeful details: urgency colors on deadlines, compatibility badge, ⭐ important lessons, readiness ring, honest "এই কোর্স কাদের জন্য নয়".
- ✅ Motion 150–250 ms ease-out, 1–2px card lift, confetti only on real milestones; respect reduced-motion.
- ✅ The dashboard must feel calm and scannable, like a well-organized planner — not a busy admin console.

### Every screen must have
Loading skeleton · empty state (illustration + next action) · error state (sympathetic Bangla + retry) · hover/focus/pressed/disabled states · light + dark · 360px and 1440px (web) · WCAG AA · 44px (web) / 48dp (mobile) targets.

Components are identical across web and mobile (Course Card, assignment row, badges, progress ring, lesson row, countdown card) — same anatomy, same tokens.

---

## 8. Skills & agents to use

Use the skills available in this environment when they fit (skip any not installed):

- **frontend-design** — before designing/reshaping any web screen.
- **10x-team** / **principal-architect** / **cto** — phase planning, cross-cutting decisions.
- **senior-engineer** / **sde** — implementation; **dba** — schema, indexes, migrations; **security-engineer** — auth, OAuth tokens, payments, video protection; **devops-engineer** / **sre** — Docker, CI, deploy, monitoring.
- **qa-engineer** / **testing-strategy** — test plans; **code-review** — review each phase before marking done.
- **design-system** / **design-critique** / **accessibility-review** / **ux-copy** — tokens, screen reviews, a11y, Bangla microcopy.
- **seo-check** / **technical-seo** / **schema-markup** — public pages.
- Use sub-agents for independent parallel work (e.g. server module vs client screens vs mobile screens of the same phase), giving each the relevant sections of this file.

---

## 9. Build phases (update `docs/PROGRESS.md` as you go)

Do phases in order; don't start the next until the current one passes its checks.

| # | Phase | Must deliver |
|---|---|---|
| 0 | Audit & plan | Inventory `Reference/` → `docs/SCREEN_MAP.md`; inspect existing folders; write `docs/ARCHITECTURE.md`, `docs/DESIGN_SYSTEM.md`, phase plan in `docs/PROGRESS.md` |
| 1 | Foundation | pnpm workspace (`client`, `server`, `shared`); docker-compose (Postgres, Redis, Meilisearch); Fastify skeleton + Drizzle + migrations; auth (OTP + email + Google login); tokens → CSS vars + Tailwind preset + Compose theme; client shells (Public, Student sidebar, Seller, Admin) with guards; mobile project skeleton (android + ios + src modules); OpenAPI generation; CI lint/type/test |
| 2 | Master data | Institutions/departments/programs/subjects + admin approval + institution requests; seed |
| 3 | **Home dashboard + Google Classroom** | OAuth connect/disconnect; full + incremental sync; Pub/Sub push + renewal job; notification center + push + reminders; `/home` with all §0A widgets (course/exam widgets show placeholders until later phases); `/classroom/*`, calendar, notifications screens on web + mobile; fake Classroom seed; `docs/CLASSROOM_INTEGRATION.md` |
| 4 | Teacher Studio (seller) | Seller verification, 5-step course wizard with live preview, course targets, section/chapter/lesson builder, video + PDF upload, Draft→Review→Published |
| 5 | Discovery | Meilisearch indexing, smart search + autocomplete, filters, ranking, browse by university/subject, course details, request-a-course |
| 6 | Checkout | Gateway adapter, bKash/Nagad, idempotent webhook, enrollment, coupons, bundles, invoice PDF, refunds, ledger |
| 7 | Learning | Player (web + mobile), progress, My Courses, Q&A, reviews, wishlist, notes/bookmarks |
| 8 | Exam Mode & Mock Test | Exam plan, countdown, daily plan, syllabus checklist, flashcards, timed mock test + results |
| 9 | Dashboard completion | Real course progress, exam countdown, recommendations wired into `/home`; Classroom edge cases tested; Google OAuth verification submitted |
| 10 | Admin + seller completion | All admin sections, payouts approval, reports, analytics; seller earnings/payout |
| 11 | Mobile parity | Every student screen on Android (then iOS build), offline downloads, push, baseline profiles |
| 12 | Polish & hardening | Design audit vs reference + §7, dark mode, a11y, performance budgets, SEO, security review, E2E tests, `docs/RUNBOOK.md` |

### Definition of Done (per feature)
- Matches reference layout; passes §7 in light + dark, mobile + desktop.
- Real API + seed data (no mocked UI in final state).
- Tests: server unit/integration (Vitest + Testcontainers or a test DB), client component + E2E happy path (Vitest + Playwright), mobile ViewModel unit + key UI tests.
- Lint/type-check clean: ESLint + `tsc --noEmit` (all Web packages), ktlint/detekt (mobile).
- Performance budget not regressed.
- `docs/PROGRESS.md` updated.

---

## 10. Working rules

- Read before you write; extend existing code rather than rewriting it.
- Respect the folder architecture in §3 — don't invent new top-level folders.
- Small, focused commits with clear messages.
- No secrets in code — `.env` + keep `.env.example` updated.
- Never edit `Reference/`.
- Never claim "will come in the exam" — use "সম্ভাব্য গুরুত্বপূর্ণ টপিক" and show the disclaimer on course pages. Footer: "কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়"; near Classroom features: "Google-এর সঙ্গে যুক্ত নয়".
- Brand is always "ShikkhaSathi" / "শিক্ষাসাথী"; canonical URLs use `https://shikkhasathi.com`.
- Ambiguity → choose the simplest option that keeps the architecture intact, log it in `docs/DECISIONS.md`, continue.
- End of every session: update `docs/PROGRESS.md` with what was done and the exact next step.
