# ShikkhaSathi (শিক্ষাসাথী)

**shikkhasathi.com** — a unified academic dashboard (Google Classroom, read-only) plus a multi-university exam-prep course marketplace for Bangladeshi students.

| Folder | What |
|---|---|
| `backend/` | Java 21 + Spring Boot 4 (Maven Wrapper): REST API `/api/v1`, job workers, Flyway migrations for the two databases (CORE + CONTENT), OpenAPI |
| `website/` | React 19 + Vite + TypeScript (pnpm): public site, student panel (`/home` dashboard), Teacher Studio (`/teach`), Admin (`/admin`) |
| `app/` | Kotlin + Compose Multiplatform: `android/`, `ios/` (Xcode project), `shared/` (all app code) |
| `infra/` | `docker-compose.yml` (db-core, db-content, redis, meilisearch, backend, worker, website, caddy), `Caddyfile` |
| `scripts/` | design-token generator, entity generator, `check-all.sh` |
| `docs/` | all documentation — start with `docs/RUNBOOK.md`, `docs/ARCHITECTURE.md`, `docs/BACKEND.md` |

## Quick start (development)

1. Data services (Postgres ×2, Redis, Meilisearch):

```bash
docker compose -f infra/docker-compose.yml up -d db-core db-content redis meilisearch
```

2. Backend on http://localhost:8080. It migrates both databases and, in the default `dev` profile, seeds demo data into empty ones. Copy `backend/.env.example` to `backend/.env` (its DB URLs already match the compose ports):

```bash
cd backend && ./mvnw spring-boot:run
```

On Windows (PowerShell): `cd backend; .\mvnw.cmd spring-boot:run`.

3. Website on http://localhost:5173 (proxies `/api` to :8080):

```bash
cd website && pnpm install && pnpm dev
```

4. Mobile: see `app/README.md`. The Android debug build talks to `http://10.0.2.2:8080/api/v1`.

Everything runs in containers too: `docker compose -f infra/docker-compose.yml --profile app up -d --build` serves the site on http://localhost:8000.

Everything works without real credentials (Fake adapters for email, SMS, payments, video, Classroom, push, search). Add real credentials in **Admin → সেটিংস → Integrations**.
