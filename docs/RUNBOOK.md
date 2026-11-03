# Runbook

## 1. Local setup

Requirements: Node 22, pnpm 10, PostgreSQL 16 (or Docker), Redis 7. Optional: Meilisearch, JDK 21 + Android SDK (mobile).

```bash
pnpm install
```

```bash
cp .env.example .env
```

Databases — either Docker:

```bash
docker compose -f infra/docker-compose.yml up -d db-core db-content redis meilisearch
```

(then set `CORE_DATABASE_URL=postgres://postgres:postgres@localhost:5433/shikkhasathi_core`, `CONTENT_DATABASE_URL=…@localhost:5434/shikkhasathi_content`)

…or one local Postgres with two databases:

```bash
createdb shikkhasathi_core && createdb shikkhasathi_content && createdb shikkhasathi_core_test && createdb shikkhasathi_content_test
```

Migrate + seed, then run:

```bash
pnpm db:migrate && pnpm db:seed
```

```bash
pnpm dev
```

Open http://localhost:5173 (Vite proxies `/api` to Fastify on :3000). For the worker (email queue, Classroom sync, reminders):

```bash
pnpm --filter @shikkhasathi/server dev:worker
```

Demo accounts (password `shikkhasathi123`, change with `SEED_PASSWORD`): `admin@shikkhasathi.com` (admin), `sadman@demo.shikkhasathi.com` (student), `rakib@demo.shikkhasathi.com` (verified teacher). Phone OTP works locally: the code is shown on screen in development (Fake SMS provider).

Production-like local run (server serves the built client on one origin):

```bash
pnpm --filter @shikkhasathi/client build && pnpm --filter @shikkhasathi/server dev
```

→ http://localhost:3000

## 2. Environment variables

See `.env.example`. **Bootstrap (env only):** `CORE_DATABASE_URL`, `CORE_DIRECT_URL`, `CONTENT_DATABASE_URL`, `CONTENT_DIRECT_URL`, `SUPABASE_*_URL`, `SUPABASE_*_SERVICE_ROLE_KEY`, `REDIS_URL`, `ENCRYPTION_KEY` (64 hex chars: `openssl rand -hex 32`), `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `APP_URL`. The server refuses to start in production with default secrets.

**Everything else** is set in **Admin → সেটিংস → Integrations** (stored AES-256-GCM encrypted in CORE `integration_settings`); env vars of the same name are the fallback. Without either, the Fake adapter is used outside production and the feature is disabled in production.

> Rotating `ENCRYPTION_KEY` makes stored integration secrets and Google refresh tokens unreadable. Re-enter integration secrets in the Admin panel and ask users to reconnect Classroom after a rotation.

## 3. Supabase (two projects)

1. Create projects **ShikkhaSathi Core** and **ShikkhaSathi Content** (region close to Dhaka, e.g. ap-south-1 / Singapore).
2. For each: Project Settings → Database → copy
   - *Transaction pooler* URL (port **6543**) → `CORE_DATABASE_URL` / `CONTENT_DATABASE_URL` (+ `DB_POOLER=true`)
   - *Direct connection* URL (port **5432**) → `CORE_DIRECT_URL` / `CONTENT_DIRECT_URL`
   - Project URL + `service_role` key → `SUPABASE_*_URL`, `SUPABASE_*_SERVICE_ROLE_KEY` (server only; never ship to clients)
3. Storage → create private buckets: CORE `invoices`, `verification-docs`, `avatars`; CONTENT `course-docs` (private) and `course-covers` (public).
4. Run `pnpm db:migrate` as a release step (uses the direct URLs). It enables RLS on every table and fails if any table lacks it. We do **not** use Supabase Auth; the Data API exposes nothing (RLS deny-all).
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

```bash
docker build -f infra/Dockerfile --target server -t shikkhasathi/server .
```

```bash
docker build -f infra/Dockerfile --target worker -t shikkhasathi/worker .
```

Release steps: run migrations (`node dist/migrate.js` with `*_DIRECT_URL`), start `server` and `worker`, Caddy (`infra/Caddyfile`) terminates TLS for `shikkhasathi.com`, redirects `admin.shikkhasathi.com` to `/admin`. CI (`.github/workflows/ci.yml`) runs lint → typecheck → tests → build → bundle budget.

## 6. Backups & restore
- Supabase: daily automatic backups (Pro plan: PITR). Additionally a nightly `pg_dump` of both databases, encrypted (`age`) to off-site storage, 30-day retention.
- Monthly restore test into a scratch project; verify `pnpm db:migrate` is a no-op and Admin health is green.

## 7. Common failures
| Symptom | Fix |
|---|---|
| `prepared statement "…" does not exist` | App URL points at the transaction pooler without `DB_POOLER=true` (or port 6543) |
| Migrations hang | Using the pooler URL for migrations — use `*_DIRECT_URL` |
| `RLS disabled on: …` at migrate | A new table was created outside migrations; add it via a migration (RLS is auto-enabled) |
| Emails stuck "queued" | Worker not running, or Redis unreachable; check Admin → সিস্টেম হেলথ queues |
| OTP not arriving in production | SMS integration not configured (Admin → SMS) — the API returns `INTEGRATION_DISABLED` |
| 429 on login/OTP | Rate limits (OTP 5/h per phone, login 10/15 min) — expected |

## 8. Manual steps that can't be done from the cloud dev environment
- Android/iOS builds (Android SDK / Xcode) — see `App/README.md`.
- Google OAuth verification, Pub/Sub topic creation, Brevo domain DNS, payment gateway merchant onboarding, SMS sender-id approval — see the relevant sections above and `docs/CLASSROOM_INTEGRATION.md`.
