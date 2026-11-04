# ShikkhaSathi website

React 19 · TypeScript · Vite · Tailwind 4 · TanStack Query · pnpm. All web interfaces in one app: the public site, student dashboard, Teacher Studio (`/teach`) and Admin (`/admin`, lazy chunk).

```bash
pnpm install
pnpm dev            # http://localhost:5173 — proxies /api to the backend on :8080 (VITE_API_PROXY)
pnpm typecheck && pnpm lint && pnpm test
pnpm build && pnpm check:bundle
pnpm e2e            # Playwright; see playwright.config.ts
```

- `src/api/`: typed API layer.
  - `contracts/` holds Zod schemas mirroring the Spring Boot API. They are checked against the backend's OpenAPI (`test/contracts/openapi-drift.test.ts`) and exported to the backend's contract tests (`pnpm gen:contracts`).
  - `endpoints.gen.ts` is the zod-free map used by the client (`pnpm gen:endpoints`).
  - `openapi.gen.ts` holds the types generated from the backend (`pnpm gen:api-types`).
- `src/app/`: router, shells and guards. `src/features/<area>/`: pages and components per product area. `src/components/`: design system. `src/lib/`: api client, auth, i18n (bn/en) and utils. `src/styles/tokens/`: design tokens, shared with the mobile app through `scripts/gen-tokens.mjs`.
- See `docs/ARCHITECTURE.md` and `docs/RUNBOOK.md`.
