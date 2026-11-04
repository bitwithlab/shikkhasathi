# Architecture

ShikkhaSathi is a monorepo with three apps that share one API:
- `backend/`: Java 21, Spring Boot.
- `website/`: React 19, Vite.
- `app/`: Kotlin, Compose Multiplatform for Android and iOS.

The website and the mobile app both talk only to `/api/v1`, on one public origin (`https://shikkhasathi.com`) behind Caddy.

## System diagram

```
                       shikkhasathi.com (Caddy, TLS)
Browser ──▶ Caddy ─┬─ /api/* · /sitemap.xml · /c|u|s|t/<slug> ──▶ backend (Spring Boot, SS_ROLE=api)
                   └─ everything else ──▶ website (static Vite build + prerendered public pages)
Mobile app ───────────────────────────────▶ /api/v1
backend: controller (thin, @Auth, Bean Validation) ─▶ service (@Transactional) ─▶ repository
          ├─▶ CORE db    (Supabase project 1; JPA + JDBC, Flyway core/)
          ├─▶ CONTENT db (Supabase project 2; JPA + JDBC, Flyway content/)
          └─▶ adapters (Real + Fake): Redis · Meilisearch · Supabase Storage · Brevo · payment gateways
                                       · video provider · Google APIs · SMS · FCM
worker (same image, SS_ROLE=worker): Redis job queue (common/jobs) + @Scheduled schedules — email.send,
  notifications.dispatch, classroom sync/renewal, reminders, search indexing, payments reconcile, ledger, exam
```

## Folder layout

| Path | Role |
|---|---|
| `backend/src/main/java/com/shikkhasathi/<module>` | `controller/` `service/` `repository/` `entity/` (generated) `dto/` `mapper/` `adapter/` per module — see `docs/BACKEND.md` |
| `backend/src/main/java/com/shikkhasathi/{config,common,security,integration}` | two datasources + Flyway, errors/envelope, cursor pagination, crypto, jobs, audit, JWT + RBAC interceptor, admin-editable integration settings |
| `backend/src/main/resources/db/migration/{core,content}` | Flyway migrations per database (`afterMigrate.sql` = RLS on every table) |
| `backend/src/main/resources/db/seed` | demo data (dev profile) |
| `backend/src/test/resources/contracts/endpoints.json` | per-endpoint JSON Schemas — the contract every response is tested against |
| `docs/api/openapi.json` | OpenAPI generated from the backend (`OpenApiExportTest`), committed |
| `website/src/app` | router, providers, shells (Public, Student, Seller, Admin), guards |
| `website/src/features/*` | feature routes; `seller` and `admin` are separate lazy chunks |
| `website/src/api` | typed API client: contracts (Zod, request validation + inferred types), `openapi.gen.ts` (types generated from the backend OpenAPI) |
| `website/src/styles/tokens` | `tokens.json` → CSS vars + Tailwind preset, and the Compose theme (`scripts/gen-tokens.mjs`) |
| `app/shared/src/<sourceSet>/kotlin/com/shikkhasathi/{core,shared,features}` | all mobile code (KMP); `app/android` host app, `app/ios` Xcode project |
| `infra/` | docker-compose, public Caddyfile |

## Two databases

| CORE (people, money, private) | CONTENT (catalog + learning material) |
|---|---|
| users, auth_otps, sessions, profiles, orders, payments, invoices, refunds, coupons, enrollments, ledger, payouts, progress, notes, reviews, Q&A, exam plans, mock attempts, requests, wishlists, Google connection + `classroom_*`, notifications, settings, integration_settings, email_logs, audit_logs | institutions, departments, programs, subjects, courses, targets, sections/chapters/lessons, video_assets, documents, revisions, bundles, mock tests, flashcards |

- No cross-DB foreign keys. Cross-DB references are uuids validated in services; joins happen in services with batched `id = ANY($1)` lookups.
- Cross-DB flows (payment → enrollment) are idempotent: the CORE `orders`/`payments` rows are the outbox; the enrollment step is keyed on `(student_id, course_id)` unique and safely re-runnable by `payments.reconcile`.
- The app connects through the pooler in transaction mode; the JDBC URL gets `prepareThreshold=0`. Flyway migrations use the direct URL.
- RLS is enabled with no policies on every table in both DBs. `afterMigrate.sql` applies it, and `CompatibilityTest` fails if any table lacks it.
- Local: two Postgres databases (docker-compose `db-core`, `db-content`, or one local Postgres server with two databases).

## Configuration precedence

1. Bootstrap env only: DB URLs, Supabase URL/service keys, `REDIS_URL`, `ENCRYPTION_KEY`, `JWT_*`, `APP_URL`.
2. Everything else: `integration.service.IntegrationService` → `integration_settings` (AES-256-GCM) → env fallback → disabled (Fake adapter outside production). Cached 60 s, invalidated on save, secrets write-only/masked, audit-logged, test button per integration.

## Key flows

### Auth
- Phone OTP: `POST /auth/otp/request` (rate-limited 5/h/phone, code hashed, 2 min expiry) → SMS adapter → `POST /auth/otp/verify` → user upsert → tokens.
- Email + password (argon2id) and Google sign-in (ID token verified server-side).
- Access JWT 15 min; refresh token 30 days, opaque, hashed in `sessions`, rotated on every refresh with reuse detection (reuse revokes the session family). Web: refresh in httpOnly `SameSite=Lax` cookie + CSRF double-submit header; mobile: bearer + secure storage.
- RBAC: `@Auth(Level.USER|SELLER|VERIFIED_SELLER|ADMIN)` on each controller method, enforced by `security.AuthInterceptor` before validation runs. The same interceptor applies rate limits and blocks suspended accounts.

### Google Classroom (read-only)
connect-url (state = signed JWT w/ user id + PKCE verifier) → Google consent → `/api/v1/classroom/oauth/callback` (web) or `POST /classroom/connect {serverAuthCode}` (mobile) → exchange → refresh token encrypted into `google_connections` → `classroom.fullSync` job → `classroom_*` cache + feed items → Pub/Sub registrations per course. Updates arrive at `POST /webhooks/google-pubsub` → `classroom.incrementalSync`; a 15-min poll covers users active in the last 7 days; `reminders.deadlines` every 5 min. Disconnect revokes at Google + deletes cache + registrations. Details: `docs/CLASSROOM_INTEGRATION.md`.

### Checkout
`POST /orders` (pending, server-priced, coupon applied) → `POST /orders/:id/pay` → gateway adapter init (hosted page URL) → gateway callback/webhook → **server validation call** → `payments` row unique `(gateway, gateway_trx_id)` → order `paid` → enrollments (idempotent) → ledger (sale, commission, 7-day hold) → invoice PDF to Storage → `email.send` (order paid + invoice) → notification. `payments.reconcile` hourly re-validates pending.

### Video
Seller upload → `POST /seller/uploads` → video provider create (Bunny TUS) or `external_link` → `video.processed` webhook. Playback: `GET /lessons/:id/playback` checks enrollment + device limit → short-lived signed HLS URL + watermark payload.

## Serving

**Production**

The `website` container holds the Vite build in Caddy:
- hashed assets get an immutable cache;
- public routes are served as prerendered HTML;
- other paths fall back to the SPA.

The `backend` container serves:
- `/api/v1`;
- the live `sitemap.xml`;
- course, university, subject and teacher detail pages, with a server-rendered `<head>` around the website's `index.html`.

The public Caddy (`infra/Caddyfile`) routes between the two containers.

**Development**

The Vite dev server proxies `/api` to the backend on :8080, so the browser sees one origin.
