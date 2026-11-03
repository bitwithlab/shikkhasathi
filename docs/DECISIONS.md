# Decisions (ADR log)

Format: **ID · date · decision** — context → choice → consequence.

### ADR-001 · 2026-11-03 · Spec files moved into `docs/`
Root had `PROJECT_SPEC.md`, `PROJECT (1).md`, duplicate `UI_PROMPTS.md`. → Moved to `docs/PROJECT_SPEC.md`, `docs/PROJECT.md`; removed the root duplicate. The old placeholder folders `docs/api`, `docs/architecture`, `docs/database`, `docs/deployment` (which described `/student/*`, `/seller/*` routes) were superseded by `CLAUDE.md` §5.2 routes and replaced by `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/DB_SCHEMA.md`, `docs/RUNBOOK.md`.

### ADR-002 · 2026-11-03 · Local databases without Docker
The cloud dev container has no Docker daemon but has PostgreSQL 16 and Redis installed. → `infra/docker-compose.yml` defines `db-core`, `db-content`, `redis`, `meilisearch` for real dev machines; scripts also support a single local Postgres server with two databases (`shikkhasathi_core`, `shikkhasathi_content`). Same migrations everywhere. Tests use the two local databases (`*_test`).

### ADR-003 · 2026-11-03 · uuid v7 generated in the application
Postgres 16 has no native uuidv7. → ids generated in the server (`uuidv7` package) via Drizzle `$defaultFn`; columns are `uuid`.

### ADR-004 · 2026-11-03 · Payment gateway default = SSLCommerz, adapter interface
Spec open question #1. → `PaymentGateway` interface with `SslcommerzGateway` (covers bKash, Nagad, Rocket, cards through one hosted page), `BkashTokenizedGateway` (direct bKash), and `FakeGateway` (dev/tests; simulates hosted page + callback). Admin selects the active gateway in Integrations. aamarPay can be added as another adapter without schema change.

### ADR-005 · 2026-11-03 · Search fallback = Postgres FTS when Meilisearch is not configured
Meilisearch binaries can't be downloaded in the cloud container. → `SearchClient` interface: `MeiliSearchClient` (real) and `PostgresSearchClient` (CONTENT DB `ILIKE`/`tsvector` + synonym expansion) used when Meili is disabled. Ranking (§6.5) is applied by the server on top of either.

### ADR-006 · 2026-11-03 · Mobile: Android build needs the Android SDK (not downloadable here)
`dl.google.com` is blocked in this environment, so the Android SDK can't be installed. → `App/mobile` is a full Compose Multiplatform Gradle project with `androidTarget`, iOS targets and an extra `jvm("desktop")` target. Shared code (ViewModels, repositories, UI state, network models) compiles and is unit-tested on the JVM here; the Android app module and iOS host are written in full and built on a machine with the SDK/Xcode (`docs/RUNBOOK.md`).

### ADR-007 · 2026-11-03 · Video default = Bunny Stream; `external_link` supported
Spec open question #2. → `VideoProvider` with `BunnyStreamProvider` (TUS upload, token-authenticated HLS URLs), `ExternalLinkProvider` (already-hosted links e.g. unlisted YouTube/Drive — playback URL passed through only to enrolled users), and `FakeVideoProvider` (serves a public sample HLS in dev).

### ADR-008 · 2026-11-03 · SMS provider adapter = generic HTTP (BulkSMSBD-style) + Fake
Spec open question #6. → `SmsProvider` with `HttpSmsProvider` (configurable endpoint/API key/sender id from Admin) and `FakeSmsProvider` (logs to console, never logs OTP in production; in dev the OTP is printed so local login works).

### ADR-009 · 2026-11-03 · Default commission 20%, weekly payouts, 7-day hold
Spec open question #4. → `settings.commission_default = 20`, `payout_min = 50000` paisa (৳500), ledger hold 7 days. Editable in Admin → সেটিংস.

### ADR-010 · 2026-11-03 · Web auth transport
Web uses httpOnly `ss_rt` refresh cookie (path `/api/v1/auth`, SameSite=Lax, Secure in prod) + access token kept in memory and sent as `Authorization: Bearer`. CSRF: since the cookie is only accepted on `/auth/refresh` and `/auth/logout`, those require the `X-CSRF-Token` header matching the `ss_csrf` cookie (double-submit). Mobile sends the refresh token in the body.

### ADR-011 · 2026-11-03 · First launch university = Metropolitan University (Sylhet)
Spec open question #3. → Seed and landing copy lead with Metropolitan University, CSE; NSU and BRAC also seeded.

### ADR-012 · 2026-11-03 · Mobile: only headless shared code compiles in the cloud container (corrects ADR-006)
`maven.google.com` redirects to the blocked `dl.google.com`, so no `androidx.*` artifact resolves — and Compose Multiplatform's JVM artifacts depend on them. → `App/mobile` has two Gradle switches: `ss.android` (Android SDK present) and `ss.compose` (SDK present or macOS host). With both off (this container), the `:shared` module compiles and tests the headless code — DTOs, Ktor client, token store, repositories, ViewModels, strings, Koin graph — on `jvm("desktop")`. Compose UI (`ui/` folders + `core/designsystem/`) is written in full and linted, but compiled only on a dev machine with the SDK/Xcode (`docs/MOBILE_NOTES.md`). Kotlin 2.2.21, CMP 1.9.3, Ktor 3.3.3, Koin 4.1.1.

### ADR-013 · 2026-11-03 · Mobile architecture details
- One `:shared` KMP module reads `App/mobile/src/{core,shared,features}`; platform code sits in `androidMain/`, `iosMain/`, `desktopMain/` subfolders of each layer.
- ViewModels are framework-free (`SsViewModel`, wrapped in an androidx ViewModel by the UI), so they test on the JVM.
- Token refresh is a custom single-flight 401 → `POST /auth/refresh` (refresh token in the body) inside `ApiClient`, not Ktor's Auth plugin (that plugin needs a `WWW-Authenticate` header and keeps tokens after logout).
- Token storage: Android Keystore AES-GCM key (no deprecated security-crypto), iOS Keychain (this-device-only), desktop in memory.
- Strings: `enum Str` keyed by the web i18n key names; server error messages (already Bangla) are shown verbatim.
- Icons: Lucide via `com.composables:icons-lucide`. Status chips: Pending amber, Turned in emerald, Graded indigo, Missing red.
- Hind Siliguri / Inter TTFs are not yet in `assets/fonts` (platform default font until added — see that folder's README).

### ADR-014 · 2026-11-03 · No roster scope: teacher names and roster push omitted for real accounts
Teacher names need `classroom.rosters.readonly`, which is not in the minimal scope list (CLAUDE.md §0A). → We don't request it: `teacher_names` is empty for real Google accounts (the UI shows section/room), and only `COURSE_WORK_CHANGES` push feeds are registered (roster feeds need the roster scope). Class-list changes are picked up by polling/manual sync. The fake dataset includes teacher names for demo only.

### ADR-015 · 2026-11-03 · First full sync is silent
A new connection may import dozens of existing items. → The initial full sync writes feed items but creates no notifications; only later changes notify. Prevents a notification burst on connect.

### ADR-016 · 2026-11-03 · Disabled in-app channel still stores the notification (as read)
Dedupe needs the row even if the user switched the in-app channel off. → The row is inserted with `read_at` set, so it never shows as unread; push/email still follow their own toggles.

### ADR-017 · 2026-11-03 · Meilisearch: one document per course with target arrays
Spec §5 says "index courses × targets". → One document per course with `institutionIds`, `departmentIds`, `semesters`, `subjectIds`, `courseCodesNorm` arrays (filterable). Meili array filters behave like "any target matches", which is what course × target documents give, without duplicate hits per course. Meili supplies text relevance + typo tolerance + synonyms; the server re-ranks the top 300 with the §6.5 score. Without Meili, a Postgres engine does synonym expansion (Bangla/English/abbreviations), course-code normalisation and weighted text scoring.

### ADR-018 · 2026-11-03 · Duplicate course request = upvote
Spec §6.11 "duplicate detection by (institution, subject master, exam type)". → `course_requests.dedupe_key` is unique; creating a duplicate returns the existing request (`duplicate: true`) and records the user's vote.

### ADR-019 · 2026-11-03 · Mobile dashboard & Classroom details
- `kotlinx-datetime` for Asia/Dhaka day bucketing on device. Calendar weeks start on Sunday so the Fri–Sat weekend sits together.
- Assignments are fetched once (`status=all`) and filtered on device (instant, works offline).
- Dashboard offline cache is a JSON file (`DashboardCache`) until Room KMP can be resolved on an SDK machine.
- Feed / notification mark-read is optimistic with rollback.
- Android Classroom connect: Identity `AuthorizationClient` + `requestOfflineAccess(serverClientId)` → server auth code → `POST /classroom/connect`; server client id via the `ss.googleServerClientId` Gradle property.
