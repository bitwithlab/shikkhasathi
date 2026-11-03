# Testing

| Layer | Tool | Where | Command |
|---|---|---|---|
| Shared contracts | Vitest | `Web/shared/test` | `pnpm --filter @shikkhasathi/shared test` |
| Server (unit + integration over HTTP via `app.inject`) | Vitest + real Postgres (two test DBs) + Redis db 15 | `Web/server/test` | `pnpm --filter @shikkhasathi/server test` |
| Client components + lib | Vitest + Testing Library (jsdom) | `Web/client/test` | `pnpm --filter @shikkhasathi/client test` |
| E2E | Playwright | `Web/client/e2e` | `pnpm --filter @shikkhasathi/client e2e` (Phase 12) |
| Mobile | kotlin.test on JVM (desktop target) | `App/mobile` | `cd App/mobile && ./gradlew desktopTest` |
| Everything | | | `bash scripts/check-all.sh` |

Server tests need `shikkhasathi_core_test` and `shikkhasathi_content_test` (override with `TEST_CORE_DATABASE_URL` / `TEST_CONTENT_DATABASE_URL`). Global setup runs migrations (and the RLS check); each test truncates all tables and flushes Redis db 15. Jobs run **inline** in tests (`NODE_ENV=test`), and all external services use Fake adapters (`FakeEmailProvider.outbox`, `FakeSmsProvider.outbox`, …), so no network is needed.

Rules: every route gets an integration test; payments and Classroom sync are tested with fakes; i18n dictionaries are tested for bn/en key + placeholder parity and banned guarantee phrases.

## End-to-end (Playwright)
`Web/client/e2e/*.spec.ts`: guest discovery + SEO, student dashboard, checkout with the dev gateway → unlocked → player → mark complete, Exam Mode + mock test → result, teacher/admin access, and no horizontal scroll at phone width (Pixel 7 project).
```bash
cd Web/server && npx tsx src/database/seed/index.ts && npx tsx src/server.ts   # in one terminal (after `pnpm --filter @shikkhasathi/client build`)
cd Web/client && pnpm e2e                                                       # E2E_BASE_URL defaults to http://localhost:3000
```
The specs share one seeded database and run serially. Chromium is taken from `PW_CHROMIUM`, falling back to `/opt/pw-browsers/chromium` when present. Latest run: 17/17 passed.

Also: `Web/server/test/rbac.test.ts` sweeps every contract endpoint (anonymous → 401, student on admin/seller → 403).
