# Runbook

## 1. Local setup

Requirements:
- **Java 21** (Temurin or similar; Maven itself comes from the wrapper);
- **Node 22 + pnpm 10** for the website;
- **Docker** for the data services, or local PostgreSQL 16 + Redis 7;
- optional: Meilisearch (without it, search uses Postgres full-text);
- mobile builds: Android SDK, and Xcode 16 on macOS.

### 1.1 Data services

```bash
docker compose -f infra/docker-compose.yml up -d db-core db-content redis meilisearch
```

This exposes CORE Postgres on **5433**, CONTENT Postgres on **5434**, Redis on 6379 and Meilisearch on 7700. Data lives in the named volumes `core-data`, `content-data` and `meili-data`. Never run `docker compose down -v` unless you mean to delete the data.

The alternative is one local Postgres with two databases (then use port 5432 in `backend/.env`):

```bash
createdb shikkhasathi_core && createdb shikkhasathi_content
```

### 1.2 Backend (Spring Boot, http://localhost:8080)

```bash
cp backend/.env.example backend/.env
```

```bash
cd backend && ./mvnw spring-boot:run
```

On Windows (PowerShell):

```powershell
cd backend; copy .env.example .env; .\mvnw.cmd spring-boot:run
```

- **Startup:**
  - Flyway migrates both databases.
  - In the default `dev` profile the demo data is loaded into empty databases.
  - The API and the job workers run in the same process (`SS_ROLE=all`).
- **Existing databases** created by the previous Node migrator are *baselined* at V1 automatically. Their data is kept.
- **Endpoints:**
  - OpenAPI: http://localhost:8080/api/v1/openapi.json (Swagger UI at `/api/v1/docs`);
  - health: `/actuator/health`.

### 1.3 Website (Vite, http://localhost:5173)

```bash
cd website && pnpm install && pnpm dev
```

Vite proxies `/api` and `/sitemap.xml` to `http://localhost:8080`. Override the target with `VITE_API_PROXY`.

### 1.4 Everything in containers

```bash
docker compose -f infra/docker-compose.yml --profile app up -d --build
```

This starts backend (API), worker, website and Caddy at http://localhost:8000. The backend reads `backend/.env` when it exists.

**Demo accounts** (password `shikkhasathi123`):

| Account | Role |
|---|---|
| `admin@shikkhasathi.com` | admin |
| `sadman@demo.shikkhasathi.com` | student |
| `rakib@demo.shikkhasathi.com` | verified teacher |

Phone OTP works locally: in development the code is shown on screen (Fake SMS provider).

### 1.5 Mobile

See `app/README.md`.
- **Android:** `cd app && ./gradlew :android:installDebug`. The debug build uses `http://10.0.2.2:8080/api/v1`.
- **iOS:** open `app/ios/ShikkhaSathi.xcodeproj` in Xcode.

## 2. Environment variables

See `backend/.env.example`. **Bootstrap (env only):** `CORE_DATABASE_URL`, `CORE_DIRECT_URL`, `CONTENT_DATABASE_URL`, `CONTENT_DIRECT_URL`, `SUPABASE_*_URL`, `SUPABASE_*_SERVICE_ROLE_KEY`, `REDIS_URL`, `ENCRYPTION_KEY` (64 hex chars: `openssl rand -hex 32`), `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `APP_URL`. The server refuses to start in production with default secrets.

**Everything else** is set in **Admin → সেটিংস → Integrations** (stored AES-256-GCM encrypted in CORE `integration_settings`); env vars of the same name are the fallback. Without either, the Fake adapter is used outside production and the feature is disabled in production.

> Rotating `ENCRYPTION_KEY` makes stored integration secrets and Google refresh tokens unreadable. Re-enter integration secrets in the Admin panel and ask users to reconnect Classroom after a rotation.

## 3. Supabase (two projects)

1. Create projects **ShikkhaSathi Core** and **ShikkhaSathi Content** (region close to Dhaka, e.g. ap-south-1 / Singapore).
2. For each: Project Settings → Database → copy
   - *Transaction pooler* URL (port **6543**) → `CORE_DATABASE_URL` / `CONTENT_DATABASE_URL` (+ `DB_POOLER=true`)
   - *Direct connection* URL (port **5432**) → `CORE_DIRECT_URL` / `CONTENT_DIRECT_URL`
   - Project URL + `service_role` key → `SUPABASE_*_URL`, `SUPABASE_*_SERVICE_ROLE_KEY` (server only; never ship to clients)
3. Storage → create private buckets: CORE `invoices`, `verification-docs`, `avatars`; CONTENT `course-docs` (private) and `course-covers` (public).
4. Migrations run when the backend starts (Flyway, through the direct URLs when `*_DIRECT_URL` is set). `afterMigrate.sql` enables RLS on every table, and the admin health page reports any table without it. We do **not** use Supabase Auth; the Data API exposes nothing (RLS deny-all).
5. Admin → সিস্টেম হেলথ shows connection, latency, migration version and RLS status for both databases.

## 4. Email — Brevo (app.brevo.com)

1. Brevo → **Senders, Domains & Dedicated IPs → Domains → Add domain** `shikkhasathi.com`.
2. Add the DNS records Brevo shows (values are account-specific — copy them exactly):

| Type | Host | Value (example shape) | Purpose |
|---|---|---|---|
| TXT | `@` | `brevo-code:xxxxxxxxxxxxxxxx` | domain ownership |
| TXT | `@` | `v=spf1 include:spf.brevo.com ~all` | SPF (merge with any existing SPF into **one** record, e.g. `v=spf1 include:_spf.google.com include:spf.brevo.com ~all`) |
| CNAME | `brevo1._domainkey` | `b1.shikkhasathi-com.dkim.brevo.com` | DKIM key 1 |
| CNAME | `brevo2._domainkey` | `b2.shikkhasathi-com.dkim.brevo.com` | DKIM key 2 |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@shikkhasathi.com; fo=1` | DMARC (start with `p=none`, move to `quarantine` after 2–4 weeks of clean reports) |

3. Wait for Brevo to show the domain as *Authenticated*, then add sender `no-reply@shikkhasathi.com` (name "ShikkhaSathi") and reply-to `support@shikkhasathi.com`.
4. Brevo → **SMTP & API → API keys → Generate** (v3). Optional SMTP fallback: same page, SMTP tab → login + SMTP key (host `smtp-relay.brevo.com`, port 587).
5. ShikkhaSathi **Admin → সেটিংস → Email (Brevo)**: paste API key, sender name/email, reply-to, optional SMTP → **সেভ করুন** → **টেস্ট মেইল পাঠান**. The result and time are shown; every send is in **Admin → ইমেইল লগ**.
6. Troubleshooting: `401` = wrong key; `400 sender not valid` = sender/domain not verified; mails in spam = check DKIM/DMARC alignment and warm up volume.

## 5. Deploy (VPS, Docker)

Images:

```bash
docker build -t shikkhasathi/backend backend
```

```bash
docker build -t shikkhasathi/website website
```

- **Backend image:** one Spring Boot jar, run twice:
  - `SS_ROLE=api` serves HTTP;
  - `SS_ROLE=worker` runs the job queues and the schedules: email, notifications, Classroom sync and renewal, reminders, payment reconcile, search indexing.
  - Set `SPRING_PROFILES_ACTIVE=prod` and `NODE_ENV=production` on both. Production refuses to start with the dev secrets.
- **Website image:** the Vite build, served by Caddy. `pnpm build` runs the Vite build, then the SSR build of the landing page, then `scripts/prerender.mjs`, which writes `dist/<route>/index.html` and `dist/index.prerendered.html`.
- **Public reverse proxy** (`infra/Caddyfile`, TLS for `shikkhasathi.com`):
  - `/api/*`, `/sitemap.xml` (live from the database) and `/c|u|s|t/<slug>` go to the backend. Those detail pages get a server-rendered `<head>` wrapped around the website's `index.html`, fetched from `WEBSITE_INDEX_URL`.
  - Everything else goes to the website.
  - `admin.shikkhasathi.com` redirects to `/admin`.

Release steps:
1. Build and push both images.
2. Start the backend once. Flyway migrates and the service becomes ready on `/actuator/health/readiness`.
3. Start the worker and the website.
4. Reload Caddy.

CI (`.github/workflows/ci.yml`) runs these jobs:

| Job | Checks |
|---|---|
| tokens | the generated tokens match the committed files |
| backend | `mvnw verify` against Postgres + Redis, and the committed OpenAPI is up to date |
| website | lint, typecheck, tests, build, bundle budget |
| mobile | `gradlew check` |

## 6. Backups & restore
- Supabase: daily automatic backups (Pro plan: PITR). Additionally a nightly `pg_dump` of both databases, encrypted (`age`) to off-site storage, 30-day retention.
- Monthly restore test into a scratch project; start the backend against it (Flyway must report no pending migrations) and check Admin health is green.

## 7. Common failures
| Symptom | Fix |
|---|---|
| `prepared statement "…" does not exist` | App URL points at the transaction pooler without `DB_POOLER=true` (or port 6543 — then `prepareThreshold=0` is added automatically) |
| Migrations hang | Using the pooler URL for migrations — use `*_DIRECT_URL` |
| Admin health shows `RLS disabled on: …` | A table was created outside Flyway; add it in a migration (`afterMigrate.sql` enables RLS) |
| `Validate failed: Migrations have failed validation` | A committed migration file was edited. Never edit an applied `V<n>__*.sql`; add a new one |
| `Schema-validation: missing column …` at startup | A migration changed a table but the entities were not regenerated: `python3 scripts/gen-entities.py` |
| Emails stuck "queued" | Worker not running, or Redis unreachable; check Admin → সিস্টেম হেলথ queues |
| OTP not arriving in production | SMS integration not configured (Admin → SMS) — the API returns `INTEGRATION_DISABLED` |
| 429 on login/OTP | Rate limits (OTP 5/h per phone, login 10/15 min) — expected |
| Paid order but course still locked | Fulfilment runs inline on payment confirmation. If it failed, `payments.fulfil` retries and `payments.reconcile` (hourly, needs the worker) re-enqueues paid-but-unfulfilled orders. Check `orders.fulfilled_at` and the worker logs |
| Payment stuck "pending" | The gateway callback never arrived. `payments.reconcile` re-queries the gateway after 10 min and fails it after 24 h |
| Student can't play video ("সর্বোচ্চ ৩টি ডিভাইস") | Device limit (Redis `pd:<userId>`, 30-day window). Support can clear it with `redis-cli del pd:<userId>` |
| Suspended user still sees data | Access tokens die within 15 min, and `susp:<id>` blocks them sooner. Check Redis is reachable |

## 8. Checks before a release

```bash
bash scripts/check-all.sh
```

This runs the backend `mvnw verify`, the website lint/typecheck/test/build plus the bundle budget (landing ≤ 150 KB, home ≤ 200 KB, chunks ≤ 100 KB), and the mobile JVM tests plus lint.

```bash
cd website && E2E_BASE_URL=https://staging.shikkhasathi.com pnpm e2e
```

Run the E2E suite against a seeded staging stack. Re-run Lighthouse (mobile) on `/`, a course page and `/explore`, then record the numbers in `docs/PERFORMANCE.md`.

## 9. Manual steps that can't be done from the cloud dev environment
- Android/iOS builds (Android SDK / Xcode): see `app/README.md`.
- Google OAuth verification, Pub/Sub topic creation, Brevo domain DNS, payment gateway merchant onboarding, SMS sender-id approval — see the relevant sections above and `docs/CLASSROOM_INTEGRATION.md`.
