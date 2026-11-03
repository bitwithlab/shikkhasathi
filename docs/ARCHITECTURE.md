# Architecture

ShikkhaSathi = one web platform (`Web/`: client + server + shared) on one origin (`https://shikkhasathi.com`) + a Compose Multiplatform mobile app (`App/mobile`) that talks to the same `/api/v1`.

## System diagram

```
                 shikkhasathi.com (one origin, one port behind Caddy)
Browser ─────▶ Web/server (Fastify, Node 22)
                ├─ static: Web/client/dist (hashed assets, brotli, prerendered public HTML)
                └─ /api/v1/*  ──▶ api/v1 (thin handlers, Zod contracts from Web/shared)
Mobile app ───▶                    └─▶ modules/*/service ─▶ repository ─▶ CORE db  (Supabase project 1)
                                                         └─────────────▶ CONTENT db (Supabase project 2)
                                   services/* adapters ──▶ Redis · Meilisearch · Supabase Storage · Brevo
                                                           · Payment gateway · Video provider · Google APIs
                                                           · SMS · FCM/APNs/Web Push
Worker (src/worker.ts, BullMQ on Redis): email.send, classroom.*, reminders, notifications.dispatch,
search.indexCourse, payments.reconcile, ledger.release, exam.replan, cleanup
```

## Folder layout

See `CLAUDE.md` §3 — followed exactly. Highlights:

| Path | Role |
|---|---|
| `Web/shared/src/contracts` | Zod request/response schemas per endpoint → server validation, typed client, OpenAPI |
| `Web/shared/src/types` | enums (roles, exam types, statuses), inferred types |
| `Web/shared/src/tokens` | `tokens.json` → CSS vars, Tailwind preset, Compose theme (`scripts/gen-tokens`) |
| `Web/server/src/config` | Zod-parsed env (bootstrap only) |
| `Web/server/src/database/core` · `/content` | two Drizzle schemas, clients, migration folders, seeds |
| `Web/server/src/modules/<m>` | `*.routes.ts` (registered under `api/v1`), `*.service.ts`, `*.repository.ts`, `*.jobs.ts` |
| `Web/server/src/services` | external adapters, each with Real + Fake implementation |
| `Web/server/src/common` | errors/envelope, auth guards + RBAC, logger, pagination, idempotency, rate limit |
| `Web/client/src/app` | router, providers, shells (Public, Student, Seller, Admin), guards |
| `Web/client/src/features/*` | feature routes; `seller` and `admin` are separate lazy chunks |
| `App/mobile` | Gradle KMP project: `android/` app module, `ios/` host, `src/` shared code |

## Two databases

| CORE (people, money, private) | CONTENT (catalog + learning material) |
|---|---|
| users, auth_otps, sessions, profiles, orders, payments, invoices, refunds, coupons, enrollments, ledger, payouts, progress, notes, reviews, Q&A, exam plans, mock attempts, requests, wishlists, Google connection + `classroom_*`, notifications, settings, integration_settings, email_logs, audit_logs | institutions, departments, programs, subjects, courses, targets, sections/chapters/lessons, video_assets, documents, revisions, bundles, mock tests, flashcards |

- No cross-DB foreign keys. Cross-DB references are uuids validated in services; joins happen in services with batched `id = ANY($1)` lookups.
- Cross-DB flows (payment → enrollment) are idempotent: the CORE `orders`/`payments` rows are the outbox; the enrollment step is keyed on `(student_id, course_id)` unique and safely re-runnable by `payments.reconcile`.
- App connects via the pooler (transaction mode, `prepare: false`); drizzle-kit migrations use the direct URL.
- RLS enabled, no policies, on every table in both DBs (a migration adds it; a test fails if any table lacks it).
- Local: two Postgres databases (docker-compose `db-core`, `db-content`, or one local Postgres server with two databases).

## Configuration precedence

1. Bootstrap env only: DB URLs, Supabase URL/service keys, `REDIS_URL`, `ENCRYPTION_KEY`, `JWT_*`, `APP_URL`.
2. Everything else: `modules/settings` → `integration_settings` (AES-256-GCM) → env fallback → disabled (Fake adapter outside production). Cached 60 s, invalidated on save, secrets write-only/masked, audit-logged, test button per integration.

## Key flows

### Auth
- Phone OTP: `POST /auth/otp/request` (rate-limited 5/h/phone, code hashed, 2 min expiry) → SMS adapter → `POST /auth/otp/verify` → user upsert → tokens.
- Email + password (argon2id) and Google sign-in (ID token verified server-side).
- Access JWT 15 min; refresh token 30 days, opaque, hashed in `sessions`, rotated on every refresh with reuse detection (reuse revokes the session family). Web: refresh in httpOnly `SameSite=Lax` cookie + CSRF double-submit header; mobile: bearer + secure storage.
- RBAC: `requireAuth`, `requireRole('seller'|'admin')`, `requireVerifiedSeller` guards in `common/auth`.

### Google Classroom (read-only)
connect-url (state = signed JWT w/ user id + PKCE verifier) → Google consent → `/api/v1/classroom/oauth/callback` (web) or `POST /classroom/connect {serverAuthCode}` (mobile) → exchange → refresh token encrypted into `google_connections` → `classroom.fullSync` job → `classroom_*` cache + feed items → Pub/Sub registrations per course. Updates arrive at `POST /webhooks/google-pubsub` → `classroom.incrementalSync`; a 15-min poll covers users active in the last 7 days; `reminders.deadlines` every 5 min. Disconnect revokes at Google + deletes cache + registrations. Details: `docs/CLASSROOM_INTEGRATION.md`.

### Checkout
`POST /orders` (pending, server-priced, coupon applied) → `POST /orders/:id/pay` → gateway adapter init (hosted page URL) → gateway callback/webhook → **server validation call** → `payments` row unique `(gateway, gateway_trx_id)` → order `paid` → enrollments (idempotent) → ledger (sale, commission, 7-day hold) → invoice PDF to Storage → `email.send` (order paid + invoice) → notification. `payments.reconcile` hourly re-validates pending.

### Video
Seller upload → `POST /seller/uploads` → video provider create (Bunny TUS) or `external_link` → `video.processed` webhook. Playback: `GET /lessons/:id/playback` checks enrollment + device limit → short-lived signed HLS URL + watermark payload.

## Serving
Production: Fastify serves `Web/client/dist` with immutable cache for hashed assets, prerendered HTML for public routes, SPA fallback for non-`/api` paths, brotli/gzip. Dev: Vite dev server proxies `/api` to Fastify so the browser uses one origin.
