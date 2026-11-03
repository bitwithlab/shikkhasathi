# ShikkhaSathi (শিক্ষাসাথী)

**shikkhasathi.com** — a unified academic dashboard (Google Classroom, read-only) plus a multi-university exam-prep course marketplace for Bangladeshi students.

| Folder | What |
|---|---|
| `Web/client` | React 19 + Vite — public site, student panel (`/home` dashboard), Teacher Studio (`/teach`), Admin (`/admin`) |
| `Web/server` | Node 22 + Fastify — REST API `/api/v1`, BullMQ worker, serves the built client |
| `Web/shared` | Zod contracts (→ OpenAPI), enums, design tokens |
| `App/mobile` | Kotlin + Compose Multiplatform (Android first, iOS from the same code) |
| `infra/` | docker-compose (db-core, db-content, redis, meilisearch), Dockerfile, Caddyfile |
| `scripts/` | token generator, bundle budget check, check-all |
| `docs/` | all documentation — start with `docs/PROGRESS.md` and `docs/RUNBOOK.md` |

Quick start:

```bash
pnpm install && cp .env.example .env && pnpm db:migrate && pnpm db:seed && pnpm dev
```

Everything runs without real credentials (Fake adapters for email, SMS, payments, video, Classroom, push, search). Add real credentials in **Admin → সেটিংস → Integrations**.
