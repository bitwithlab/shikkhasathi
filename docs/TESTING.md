# Testing

| Layer | Tool | Where | Command |
|---|---|---|---|
| Backend (unit + integration over HTTP with MockMvc) | JUnit 5 + AssertJ, real Postgres (two test DBs) + Redis db 15 | `backend/src/test/java` | `cd backend && ./mvnw test` |
| API contract | JSON Schema per endpoint (`backend/src/test/resources/contracts/endpoints.json`) | every backend test calls `contract("group.name", resp)` | same |
| Contract coverage + RBAC sweep | `ContractCoverageTest` | backend | same |
| Website contracts + OpenAPI drift | Vitest | `website/test/contracts` | `cd website && pnpm test` |
| Website components + lib | Vitest + Testing Library (jsdom) | `website/test` | `cd website && pnpm test` |
| E2E | Playwright | `website/e2e` | `cd website && pnpm e2e` |
| Mobile | kotlin.test on the JVM (desktop target) | `app/shared/src/commonTest`, `desktopTest` | `cd app && ./gradlew :shared:desktopTest` |
| Everything | | | `bash scripts/check-all.sh` |

## Backend
- **Test databases:** the tests create `ss_test_core` and `ss_test_content` on `localhost:5432` (user and password `postgres`) when they are missing.
  - On context start, Flyway migrates them and `afterMigrate.sql` turns on RLS.
  - Every test truncates all tables and flushes Redis db 15.
- **Environment overrides:**
  - `TEST_PG_HOST` and `TEST_PG_PASSWORD` change the Postgres connection.
  - `TEST_DB_PREFIX`, `TEST_REDIS_DB` and `TEST_STORAGE_DIR` let two checkouts run their suites at the same time.
- **No network needed:**
  - Jobs run **inline**.
  - Every external service uses its Fake adapter (`FakeEmailProvider`, `FakeSmsProvider`, `FakeGateway`, `FakeVideoProvider`, the fake Classroom client, …).
  - Adapters call out through `HttpTransport`, which tests replace.
- **Special-purpose tests:**
  - `CompatibilityTest` proves the backend reads what the previous Node API wrote: AES-GCM ciphertexts, refresh-token HMACs, JWT signatures, cursors, argon2id hashes and the seed. It also checks that RLS is on.
  - `ContractCoverageTest` checks that every contract endpoint exists. Anonymous callers must get 401, and students calling admin or seller routes must get 403 before validation runs.
  - `OpenApiExportTest` writes `docs/api/openapi.json`. CI fails when the committed copy is stale. The website generates `src/api/openapi.gen.ts` from it (`pnpm gen:api-types`), and `openapi-drift.test.ts` checks that every endpoint the website calls exists in it.

**Rules:**
- every route gets an integration test with a contract check;
- payments and Classroom sync are tested with fakes;
- i18n dictionaries are tested for bn/en key and placeholder parity, and for banned guarantee phrases.

## End-to-end (Playwright)
`website/e2e/*.spec.ts` (latest run 2026-11-04: 17/17 against the Spring Boot backend + worker through Caddy in docker compose) covers:
- guest discovery and SEO;
- the student dashboard;
- checkout with the dev gateway, then the unlocked course, the player and "mark complete";
- Exam Mode and a mock test through to the result;
- teacher and admin access;
- no horizontal scroll at phone width (Pixel 7 project).

Run it against the Java backend serving a seeded database, with the built website in front of it (`pnpm preview` proxies `/api` to :8080):

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd website && pnpm build && pnpm preview
```

```bash
cd website && E2E_BASE_URL=http://localhost:4173 pnpm e2e
```

The specs share one seeded database and run serially. Chromium comes from `PW_CHROMIUM`, falling back to `/opt/pw-browsers/chromium` when present.
