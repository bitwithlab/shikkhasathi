# Progress

Resumable log. A new session reads **Current state** and continues from **Next step**.

## Current state
- **Current phase:** 1 — Foundation (only the mobile skeleton remains) → then Phase 2 Master data
- **Next step:** finish/verify `App/mobile` skeleton (`./gradlew :shared:desktopTest`), commit; start Phase 2 (catalog API + admin approval + onboarding screens + seed).
- **Run locally:** see `docs/RUNBOOK.md` §1. Local dev here uses the system Postgres 16 (`pg_ctlcluster 16 main start`) and `redis-server --daemonize yes`.
- **Known blockers:** no Docker daemon (use local Postgres 16 + Redis, ADR-002); Google Maven/Android SDK unreachable → Compose UI not compiled here (ADR-012); Meilisearch binary not downloadable (ADR-005).

## Phase checklist

### Phase 0 — Audit & plan ✅
- [x] Moved spec files into `docs/` (ADR-001)
- [x] Inventoried `Reference/` → `docs/SCREEN_MAP.md`
- [x] Inspected existing folders (only READMEs + specs existed; no code)
- [x] `docs/ARCHITECTURE.md`, `docs/DESIGN_SYSTEM.md`, `docs/DECISIONS.md`, phase plan (this file)

### Phase 1 — Foundation
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

### Phase 2 — Master data
- [ ] institutions/departments/programs/subjects API + admin approval + institution requests + seed; onboarding screens

### Phase 3 — Home dashboard + Google Classroom
- [ ] OAuth connect/disconnect, full/incremental sync, Pub/Sub + renewal, reminders, notifications center + push, `/home`, `/classroom/*`, calendar, notifications (web + mobile), fake Classroom seed, `docs/CLASSROOM_INTEGRATION.md`

### Phase 4 — Teacher Studio
- [ ] seller verification, 5-step wizard + live preview, targets, curriculum builder, uploads, Draft→Review→Published

### Phase 5 — Discovery
- [ ] search + autocomplete, filters, ranking, browse pages, course details, request board

### Phase 6 — Checkout
- [ ] gateway adapters, idempotent webhook, enrollment, coupons, bundles, invoice PDF + email, refunds, ledger

### Phase 7 — Learning
- [ ] player, progress, My Courses, Q&A, reviews, wishlist, notes/bookmarks

### Phase 8 — Exam Mode & Mock Test
- [ ] exam plan, countdown, daily plan, syllabus checklist, flashcards, timed mock test + results

### Phase 9 — Dashboard completion
- [ ] real widgets wired, Classroom edge cases, verification checklist

### Phase 10 — Admin + seller completion
- [ ] all admin sections, payouts, reports, analytics, seller earnings

### Phase 11 — Mobile parity
- [ ] every student screen, offline downloads, push, baseline profiles

### Phase 12 — Polish & hardening
- [ ] design/a11y/perf/SEO/security audits, E2E, RUNBOOK

## Google OAuth verification prerequisites
- [ ] `shikkhasathi.com` verified in Search Console
- [ ] `/privacy` and `/terms` live
- [ ] consent screen with ShikkhaSathi name + logo + support email
- [ ] scope justification + demo video submitted
- [ ] Testing mode with test users until verified

## Session log
- **2026-11-03** — Phase 0 done: docs moved, screen map, architecture, design system, decisions.
- **2026-11-03** — Phase 1: workspace, shared, server foundation (55 tests green: server 30, client 21, shared 4), client shells/auth/admin integrations, infra, CI, docs. User asked to commit directly on `main` — all work now goes to `main`.
