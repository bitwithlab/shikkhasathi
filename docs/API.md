# API

Base: `https://shikkhasathi.com/api/v1` (same origin as the web app). JSON only.

- **Contracts:** every endpoint is a Zod contract in `Web/shared/src/contracts/*` (`method`, `path`, `auth`, `params`, `query`, `body`, `response`). The server registers routes from them (`common/route.ts`), validates input, enforces auth/RBAC and parses the response through the schema (unknown fields such as hashes are stripped).
- **OpenAPI:** generated from the contracts — `pnpm gen:openapi` → `docs/api/openapi.json` and `Web/shared/openapi.json` (mobile). Served live at `/api/v1/openapi.json` outside production.
- **Auth:** `Authorization: Bearer <access JWT>` (15 min). Web keeps the refresh token in the httpOnly `ss_rt` cookie (path `/api/v1/auth`) and must send `X-CSRF-Token` = `ss_csrf` cookie on `/auth/refresh` and `/auth/logout`. Mobile sends `refreshToken` in the body and receives the rotated one in the response.
- **Auth levels:** `public`, `optional`, `user`, `seller`, `verified_seller`, `admin`.
- **Errors:** `{ "error": { "code": "RATE_LIMITED", "message": "<Bangla, user-facing>", "details": {…} } }`. Codes: `BAD_REQUEST 400`, `VALIDATION_FAILED 422` (details = `[{path, message}]`), `UNAUTHORIZED 401`, `FORBIDDEN 403`, `NOT_FOUND 404`, `CONFLICT 409`, `RATE_LIMITED 429` (details.retryAfterSec), `PAYMENT_FAILED 402`, `INTEGRATION_DISABLED 503`, `CLASSROOM_REAUTH 409`, `CLASSROOM_WORKSPACE_BLOCKED 409`, `CLASSROOM_QUOTA 429`, `INTERNAL 500`.
- **Pagination:** cursor — `?cursor=&limit=` (default 20, max 100) → `{ items, nextCursor }`.
- **Rate limits:** global 300/min/IP on `/api`; OTP request 5/h/phone (+20/h/IP), resend after 60 s; login 10/15 min; payment init 10/h; Classroom manual sync 1/min; search 60/min.

## Endpoint groups (status)
| Group | Endpoints | Phase |
|---|---|---|
| System | `GET /health`, `GET /config`, `GET /admin/system-health` | 1 ✅ |
| Auth | `POST /auth/otp/request`, `/auth/otp/verify`, `/auth/login`, `/auth/register`, `/auth/google`, `/auth/refresh`, `/auth/logout`, `/auth/email-otp`, `/auth/password-reset`; `GET/PATCH/DELETE /me`, `POST /me/verify-email`, `POST /me/password`, `GET /me/sessions`, `DELETE /me/sessions/:id` | 1 ✅ |
| Admin integrations | `GET /admin/integrations`, `PUT /admin/integrations/:provider`, `POST /admin/integrations/:provider/test` | 1 ✅ |
| Catalog, Classroom, Dashboard, Notifications, Seller, Search, Orders, Learning, Exam, Mock, Requests, Admin | see spec §8 | 2–10 |

The full, always-current list is the OpenAPI file.
