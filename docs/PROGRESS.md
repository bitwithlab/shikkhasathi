# Progress

Resumable log. A new session reads **Current state** and continues from **Next step**.

## Current state
- **Current phase:** 1 — Foundation
- **Next step:** create pnpm workspace (`Web/shared`, `Web/server`, `Web/client`) + `infra/` + `scripts/`.
- **Known blockers:** no Docker daemon (use local Postgres 16 + Redis, ADR-002); Android SDK not downloadable (ADR-006); Meilisearch binary not downloadable (ADR-005).

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
- [ ] workspace + tooling
- [ ] shared (tokens, contracts, OpenAPI)
- [ ] server foundation (env, app, DBs, RLS, health)
- [ ] settings/integrations + Brevo + email queue
- [ ] auth (OTP, email, Google) + RBAC
- [ ] client shells + auth + admin integrations
- [ ] mobile skeleton
- [ ] infra + CI

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
- **2026-11-03** — Phase 0 done: docs moved, screen map, architecture, design system, decisions. Next: Phase 1 workspace.
