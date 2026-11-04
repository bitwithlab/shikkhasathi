# Progress

Resumable log. A new session reads **Current state** and continues from **Next step**.

## Current state
- **Current phase:** all build phases 0–12 are done in code. What remains needs real accounts or hardware (see **Owner actions** below).
- **Next step:** on a machine with the Android SDK and Xcode, compile the mobile UI and fix any API drift (`docs/MOBILE_NOTES.md` → Phase 11 checklist). Then do the owner actions: Supabase projects + env, Brevo DNS, payment merchant accounts, Google OAuth verification, deploy.
- After changing any contract run `pnpm gen:openapi` (regenerates OpenAPI + the client endpoint map; a test fails if stale).
- **Run locally:** see `docs/RUNBOOK.md` §1. Local dev here uses the system Postgres 16 (`pg_ctlcluster 16 main start`, password `postgres`) and `redis-server --daemonize yes`. Seed: `cd Web/server && npx tsx src/database/seed/index.ts`; demo password `shikkhasathi123`.
- **Dev payments:** with no gateway configured, checkout uses the Fake gateway. `/api/v1/payments/fake/:trxId` is a hosted page with success and fail buttons (it does not exist in production).
- **Known blockers:** no Docker daemon (use local Postgres 16 + Redis, ADR-002); Google Maven/Android SDK unreachable → Compose UI not compiled here (ADR-012); Meilisearch binary not downloadable (ADR-005).

## Phase checklist

### Phase 0 — Audit & plan ✅
- [x] Moved spec files into `docs/` (ADR-001)
- [x] Inventoried `Reference/` → `docs/SCREEN_MAP.md`
- [x] Inspected existing folders (only READMEs + specs existed; no code)
- [x] `docs/ARCHITECTURE.md`, `docs/DESIGN_SYSTEM.md`, `docs/DECISIONS.md`, phase plan (this file)

### Phase 1 — Foundation ✅
Plan:
1. pnpm workspace (root `package.json`, `pnpm-workspace.yaml`), TS base config, ESLint flat config, Prettier.
2. `Web/shared`: tokens.json + generators (CSS vars, Tailwind preset, Compose theme); enums; Zod contracts for health, auth, me, settings; OpenAPI generator.
3. `Web/server`: Zod env, Fastify app (helmet, cors, cookies, rate limit, error envelope), CORE + CONTENT Drizzle schemas (all tables from spec §7 placed per §0B), migrations, RLS deny-all migration + test, health endpoint with DB health, settings module (AES-256-GCM, cache, env fallback, audit), Brevo email adapters + `email.send` queue + templates + test-send, auth (OTP, email/password, Google, refresh rotation), RBAC guards, worker entry.
4. `Web/client`: Vite + React 19 + Tailwind (token preset), i18n bn/en, API client + TanStack Query, shells (Public, Student sidebar, Seller, Admin) with guards, auth screens, admin integrations screen.
5. `App/mobile`: Gradle KMP skeleton (android, ios, src/core, shared, features), theme from tokens.
6. `infra/docker-compose.yml`, Dockerfile, Caddyfile, GitHub Actions CI; `scripts/check-all.sh`.
- [x] workspace + tooling (pnpm, TS strict, ESLint, Prettier)
- [x] shared (tokens + generator, enums, contracts, OpenAPI generation, zod-free endpoint map)
- [x] server foundation (Zod env, Fastify, contract routes + RBAC, error envelope, two Drizzle DBs, migrations, RLS deny-all + test, health/system-health)
- [x] settings/integrations (AES-256-GCM, admin → env → default, cache, audit, test buttons) + Brevo API/SMTP/Fake + `email.send` queue + email_logs + bn/en templates
- [x] auth: phone OTP, email+password (argon2id), Google ID token, refresh rotation + reuse detection, web cookie + CSRF, sessions list/revoke
- [x] client: tokens → Tailwind, i18n (feature-split), API client, shells (Public, Student sidebar, Seller, Admin) + guards, Auth page, Admin Integrations + System health, landing v1
- [x] mobile skeleton: Gradle KMP (`:shared` + `:android` + iOS host), theme from tokens, Ktor client with single-flight refresh, token stores, nav (bottom bar + drawer), auth + home placeholder ViewModels — 26 JVM tests green (`cd App/mobile && ./gradlew :shared:desktopTest`); Compose UI/Android/iOS compile only on an SDK machine (ADR-012)
- [x] infra (docker-compose, Dockerfile, Caddyfile) + CI workflow + `scripts/check-all.sh`
- [x] docs: RUNBOOK (incl. Brevo SPF/DKIM/DMARC), API, DB_SCHEMA, TESTING, PERFORMANCE

### Phase 2 — Master data ✅
Plan: catalog contracts → service (batched cross-DB counts, alias search incl. Bangla) → admin catalog CRUD + institution-request queue (approve / merge / reject, duplicate hints) → academic profile + onboarding endpoint (+ optional exam plan) → seed (12 institutions, 20 subjects, 49 course codes, demo student profiles) → web onboarding (5 steps + success, per reference) and admin pages → tests.
- [x] `GET /institutions` (search name/short/alias/Bangla, type filter, cursor), `/institutions/:slug`, `/departments`, `/programs`, `/subjects`, `/subjects/:slug`, `POST /institution-requests`, `PUT /me/academic-profile`, `POST /me/onboarding`
- [x] admin: request queue with similar-name detection, approve/merge/reject (audit-logged), institutions/departments/programs/subjects/institution-subject CRUD, merge duplicates (moves CONTENT refs + CORE student profiles)
- [x] seed: MU, NSU, BRACU (+9 more), departments/programs, subject master with aliases (DS = Data Structures, DBMS…), course codes (CSE-2203…), demo student profiles
- [x] web: onboarding 5 steps + success (refs campuscourse_3/1/2/4), admin Institution requests + Catalog pages
- [x] tests: 7 catalog integration tests; endpoint-map sync test
- Universities directory / university detail / subjects pages are built in Phase 5 together with course grids.

### Phase 3 — Home dashboard + Google Classroom ✅
- [x] OAuth connect/disconnect, full/incremental sync, Pub/Sub + renewal, reminders, notifications center + push, `/home`, `/classroom/*`, calendar, notifications (web + mobile), fake Classroom seed, `docs/CLASSROOM_INTEGRATION.md`

### Phase 4 — Teacher Studio ✅
- [x] seller verification, 5-step wizard + live preview, targets, curriculum builder, uploads, Draft→Review→Published

### Phase 5 — Discovery ✅
- [x] search + autocomplete, filters, ranking, browse pages, course details, request board

### Phase 6 — Checkout ✅
- [x] gateway adapters (SSLCommerz: bKash/Nagad/Rocket/card · bKash tokenized · Fake), server-priced orders with idempotency key, idempotent callback + IPN (unique trx id, amount check), `payments.fulfil` (enrollment, ledger sale/commission with hold, coupon redemption, invoice PDF → `invoices` bucket, email, notifications), `payments.reconcile` (stuck payments + unfulfilled orders), coupons, bundles, refunds (48 h / <20 % watched → admin approve → gateway/manual refund, ledger reversal, audit), seller coupons/bundles/ledger, admin orders/refunds API
- [x] web: `/checkout/:courseId` (+ `/checkout/bundle?slug=`), `/checkout/result` (polls the order; server status wins over the URL)
- [x] tests: 9 server integration (orders.test.ts), 3 client component (checkout.test.tsx)
- Admin orders/refunds pages and seller earnings/coupons pages → Phase 10 (APIs exist).

### Phase 7 — Learning ✅
- [x] server `modules/learning`: outline with lock state, lesson access (active enrollment within access period / preview / owner), signed playback (≤ 2 h; device limit 3 per 30 days; single active stream with takeover), heartbeat (15 s, capped deltas, 90 % auto-complete), manual complete, course progress %, My Courses (filters), notes + bookmarks (search), lesson Q&A (notifications both ways, seller inbox), verified reviews (≥ 20 % progress, one per course, rating aggregate, seller reply once), course resource signed URLs; dashboard continue-learning is real
- [x] web: `/learn/:courseId/:lessonId` (custom HLS player: speed, quality, low-data 360p, captions, resume, shortcuts, moving watermark; PDF + text lessons; curriculum sidebar / sheet; notes, Q&A, resources, review tabs), `/my-courses`, `/notes`
- [x] tests: 6 server integration, 5 client
- Wishlist was done in Phase 5. Teacher Q&A/reviews pages → Phase 10 (APIs exist).

### Phase 8 — Exam Mode & Mock Test ✅
- [x] server `modules/exam`: plans (owned courses only), pure planner (⭐ first, balanced minutes, last day = revision, mid-way mock when ≥ 6 days), readiness 60/30/10 with renormalisation (stored for the dashboard), syllabus checklist, ⭐ topics, short lectures, previous questions, revision pack (T-1), flashcards + reviews, timed attempts (server-held, shuffled questions/options, autosave, resume, auto-submit after deadline + 30 s grace), results with chapter breakdown + review, seller MCQ/flashcard authoring, `exam.replan` + `exam.dailyPlan` jobs
- [x] web: `/exam-mode` (create form per reference → detail with gradient countdown + readiness ring, today's checklist with confetti, syllabus, upcoming days, flashcard swipe deck), `/mock-tests`, `/mock-test/:id` (timer with server-skew, navigator, flag, keyboard 1–6/←/→, offline retry), `/mock-test/:attemptId/result`
- [x] seed: DS/Algorithms mock tests + flashcards; demo student enrolled with a DS midterm plan
- [x] tests: server 6 integration + 5 planner unit; client 2
- Seller mock/flashcard authoring UI → Phase 10 (APIs exist).

### Phase 9 — Dashboard completion ✅
- [x] every `/home` widget uses real data: Classroom due-soon + feed, exam countdown + readiness (from exam plans), continue learning (from progress), week strip (deadlines + exams), recommendations (excluding owned)
- [x] Classroom edge cases tested: revoked token → reauth, Workspace blocked → friendly state + no polling, quota → doubling backoff capped at 2 h keeping last-synced data, push registration renewal window, disconnect wipes cache, rate-limited manual sync
- [x] verification pack in `docs/CLASSROOM_INTEGRATION.md` (scope justifications, data-handling statement, demo video script). Submitting it needs the owner's Google Cloud account; see the owner checklist below

### Phase 10 — Admin + seller completion ✅
- [x] server `modules/ops`: payouts (request → approve → paid with ledger debit and notification/email), admin users (search, roles, suspend with immediate token cutoff), reports + moderation (hide/restore/dismiss, rating recompute), broadcasts (audience preview, in-app/push/email), email logs, redacted audit log, platform analytics, seller students + analytics, `GET /seller/bundles`
- [x] admin web: dashboard (KPIs, work queue, chart, top lists), orders, refunds, payouts, users, reports, broadcasts, email logs, audit
- [x] Teacher Studio web: earnings + payouts, coupons + bundles, students, Q&A inbox, reviews + reply, analytics, mock test/flashcard authoring, dashboard balance card
- [x] student: payment history, receipts and refund requests in Profile
- [x] tests: server ops 5, client admin 10 + seller 15

### Phase 11 — Mobile parity ✅ (code) · compile pending on an SDK machine
- [x] every student screen as ViewModel + Compose screen: explore/search, course page, checkout (Custom Tab + polling + App Link result), My Courses, player (Media3 / AVPlayer expect/actual, watch-time heartbeat, takeover, watermark, `FLAG_SECURE`), notes/Q&A, Exam Mode + flashcards, mock runner + result, downloads
- [x] offline downloads core: AES-256-GCM chunked encrypted files, per-account key wrapped by Keystore/Keychain, expiry policy tied to enrollment access + 14-day online check (video HLS downloader still to do on device)
- [x] push registration + deep-link router (`/learn`, `/exam-mode`, `/mock-test`, `/c`, `/checkout`, full https links), FCM service, APNs bridge, baseline-profile module
- [x] JVM tests 68 → 129; ktlint + detekt clean
- [ ] compile Compose/Android/iOS on an SDK machine; device-test playback; generate the baseline profile; measure cold start

### Phase 12 — Polish & hardening ✅
- [x] SEO: request-time head + JSON-LD for course/university/subject/teacher, live sitemap, prerendered public pages
- [x] performance: SSR landing with inlined CSS and a no-shift swap, 2 font files per script; Lighthouse mobile Perf 79–81, A11y/BP/SEO 100 (numbers in `docs/PERFORMANCE.md`)
- [x] accessibility: Lighthouse 100 on public pages (chip roles, button names, AA colours)
- [x] security: RBAC sweep test over every endpoint, suspended-token cutoff, audit-log redaction
- [x] E2E: Playwright 17 specs (guest, student checkout → player, exam/mock, roles, phone widths); it found and fixed the paid-but-not-enrolled bug
- [x] seed: real review rows (no fake aggregates)
- [x] RUNBOOK: build pipeline, failure table, release checks
- [x] design audit: dark mode sweep on student screens, compatibility-badge honesty for guests, de-duplicated CTAs; security headers (Permissions-Policy, referrer policy)

## Google OAuth verification prerequisites
- [ ] `shikkhasathi.com` verified in Search Console
- [x] `/privacy` and `/terms` implemented (live with first deploy)
- [ ] consent screen with ShikkhaSathi name + logo + support email
- [ ] scope justification + demo video submitted
- [ ] Testing mode with test users until verified

## Session log
- **2026-11-03** — Phase 0 done: docs moved, screen map, architecture, design system, decisions.
- **2026-11-03** — Phase 1: workspace, shared, server foundation (55 tests green: server 30, client 21, shared 4), client shells/auth/admin integrations, infra, CI, docs. User asked to commit directly on `main` — all work now goes to `main`.
- **2026-11-03** — Phases 3–5 done: Classroom (connect, sync, push, reminders, notifications), `/home` dashboard, `/classroom/*`; Teacher Studio server (verification, wizard API, curriculum, uploads, review queue); discovery (search + ranking, autocomplete, browse pages, course details, storefront, requests).
- **2026-11-03** — Phase 6 done: checkout server + web (see checklist). Server 85 tests, client 63 tests green. Landing initial is 146.6 KB gzip after splitting landing i18n out. Next: Phase 7.
- **2026-11-03** — Teacher Studio web UI done: apply/verification, dashboard, courses list, 5-step wizard with autosave + live preview, curriculum drag & drop, TUS/PDF uploads, storefront. Fixed zod-4 `.partial()` default injection with `patchOf()` (ADR-021). Seller follow-ups: `reviewNote` in the course list, a documents-delete endpoint → Phase 10.
- **2026-11-03** — Phase 7 done: learning server + player/My Courses/Notes web. Server 92 tests, client 70. hls.js light build (87 KB, lazy, player only). Next: Phase 8.
- **2026-11-03** — Phase 8 done: Exam Mode + mock tests (server + web). Server 103 tests, client 73. Next: Phase 9.
- **2026-11-03** — Phase 9 done: dashboard fully real, Classroom edge-case tests (+3), Google verification pack. Next: Phase 10.
- **2026-11-03** — Phase 10 done (admin + Teacher Studio + ops APIs). Phase 12 mostly done: SEO, perf, a11y 100, RBAC sweep, E2E 17/17, real reviews. Server 117 tests, client 103, e2e 17. Mobile parity sub-agent running.
- **2026-11-03** — Phases 11 and 12 done. Final run: lint ✅, typecheck ✅, server 119 tests, shared 7, client 103, E2E 17, mobile JVM 129; landing 148.3 KB, home 170.9 KB gzip.

## Final summary (2026-11-03)
**Built.** One unified web platform (React + Vite client, Fastify API, shared Zod contracts → OpenAPI) on two Supabase Postgres databases (CORE + CONTENT, RLS deny-all), plus a Compose Multiplatform app.
- **Students:** Classroom-first home dashboard, Google Classroom sync (push + polling, reminders), discovery with ranking + compatibility badges, bKash/Nagad/card checkout with idempotent payments + invoices + refunds, a custom HLS player with progress, notes, Q&A and reviews, Exam Mode (planner, readiness, flashcards) and timed mock tests.
- **Teachers:** Teacher Studio (verification, course wizard, curriculum, uploads, coupons/bundles, earnings and payouts, Q&A, reviews, analytics, mock authoring).
- **Admins:** full admin panel (catalog, reviews, sellers, orders/refunds/payouts, users, moderation, broadcasts, email/audit logs, analytics, integrations with encrypted secrets, health).

**Quality.**
- **Tests:** 119 server integration/unit (including an RBAC sweep over every endpoint), 103 client, 17 Playwright E2E, 129 mobile JVM.
- **Lighthouse (mobile, public pages):** Accessibility, Best Practices and SEO 100; Performance 79–81 (simulated slow 4G).
- **Bundles:** within budget.

**Known gaps.**
- Simulated LCP 3.1–3.9 s against the 2.0 s target (observed 0.4 s locally); levers are listed in `docs/PERFORMANCE.md`.
- Mobile UI not yet compiled.
- Video offline downloads not done.

**Owner actions** (need real accounts / hardware):
- Create the two Supabase projects and set the env vars (`docs/RUNBOOK.md` §3).
- Brevo sender + SPF/DKIM/DMARC (§4).
- Payment merchant accounts → Admin → Integrations.
- Google Cloud OAuth client + Pub/Sub + verification (`docs/CLASSROOM_INTEGRATION.md`).
- SMS sender ID.
- Deploy with `infra/` (Docker + Caddy).
- Build and sign the Android/iOS apps.

## Refactor: monorepo + Spring Boot backend (2026-11-04) ✅
See ADR-025, `docs/BACKEND.md`, `docs/RUNBOOK.md`.

**Layout:** `backend/` (Java 21 + Spring Boot 4, Maven Wrapper) · `website/` (React + Vite, pnpm) · `app/` (KMP: android, ios with Xcode project, shared) · `infra/` · `scripts/` · `docs/`. The old Node server (`Web/`) is removed.

**API:** all 179 contract endpoints are ported with the same paths, JSON, errors, cookies, rate limits and cache headers. The non-contract routes are ported too: payment callbacks and webhooks, the fake payment page, file routes, the Classroom OAuth callback, the sitemap and the SEO heads.

**Data:** Flyway V1 is identical to the Drizzle 0000 migration. Existing databases are baselined and their data kept (dev DB: 29 users and 21 courses before and after). Docker volume names are unchanged.

**Verified (2026-11-04)**

| Area | Result |
|---|---|
| Backend (`./mvnw verify`) | 187/187 tests, 0 failures, incl. `ContractCoverageTest` and `CompatibilityTest` |
| Website (`pnpm lint`, `typecheck`, `test`, `build`, `check:bundle`) | 111/111 tests; landing 148.4 KB, home 171.0 KB gzip |
| Mobile (`./gradlew :shared:desktopTest ktlintCheck detekt`) | 129/129 tests, lint clean |
| Docker stack (db-core, db-content, redis, meilisearch, backend, worker, website, caddy) | all healthy |
| Playwright E2E through Caddy at :8000 | 17/17 |

**Not verified here**
- The Docker *build* stages: the sandbox proxy blocks Maven/Alpine downloads inside build containers. The runtime stages were built from the locally built jar and dist and tested in compose.
- The Android/iOS compile (no SDK or Xcode in the cloud container).

**Next step:** run `docker compose -f infra/docker-compose.yml --profile app up -d --build` on a machine with normal internet, and open `app/ios/ShikkhaSathi.xcodeproj` in Xcode once to set the signing team.
