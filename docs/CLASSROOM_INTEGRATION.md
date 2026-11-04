# Google Classroom integration (read-only)

Code:
- **Backend:** `backend/src/main/java/com/shikkhasathi/`
  - `classroom/adapter` (Real + Fake Google client, `ClassroomClients`)
  - `classroom/service` (connect, sync, push, reminders, jobs)
  - `assignment/` (assignment list, detail, calendar and due-soon views)
  - `notification/`
  - `student/service/DashboardService`
- **Web UI:** `website/src/features/student/{home,classroom}`
- **Mobile:** `app/shared/src/commonMain/kotlin/com/shikkhasathi/features/{home,classroom}`

## Scopes (minimal — CLAUDE.md §0A)
`openid email profile`, `classroom.courses.readonly`, `classroom.coursework.me.readonly`, `classroom.courseworkmaterials.readonly`, `classroom.announcements.readonly`, `classroom.push-notifications`. Defined once in `backend/…/classroom/adapter/ClassroomMapping.java` (`CLASSROOM_SCOPES`; the website mirror is `website/src/api/contracts/classroom.ts`); `ClassroomTest` asserts every scope is read-only or the push scope.
- **Not requested:** `classroom.rosters.readonly` → teacher names are empty for real accounts (shown as section/room instead); roster push feeds are not registered (ADR-014).

## Connect flows
| Client | Flow |
|---|---|
| Web | `GET /classroom/connect-url?returnTo=` → Google consent (auth-code, `access_type=offline`, `prompt=consent`, PKCE S256, `login_hint`) → `GET /api/v1/classroom/oauth/callback?code&state` → server exchanges → redirect to `returnTo?classroom=connected|denied|workspace|error`. `state` is a 10-minute signed token carrying user id, PKCE verifier, return path. |
| Android | Google Identity `AuthorizationClient.authorize(...requestOfflineAccess(webClientId))` → server auth code → `POST /classroom/connect {code, platform}` |
| iOS | GoogleSignIn SDK `serverAuthCode` → same endpoint |

Tokens never reach clients. The refresh token (and cached access token) are AES-256-GCM encrypted in CORE `google_connections` with `ENCRYPTION_KEY`. Without a Google client id in Admin/env (non-production) the **Fake** client is used: the consent URL points straight at the callback with a `fake-*` code and a realistic MU CSE dataset (5 classes, assignments in every state, materials, announcements, grades) anchored to the connection time, with a few items "arriving" 20 min–6 h later so push/poll/notifications can be demoed.

## Sync
| Trigger | Job | Notes |
|---|---|---|
| connect / reconnect | `classroom.fullSync` | builds cache + feed **silently** (no notification burst), then registers push |
| Pub/Sub push | `POST /webhooks/google-pubsub?token=…` → `classroom.pubsubConsume` → `classroom.incrementalSync` (course-scoped) | verification token from Admin → Google; job id dedupes bursts per 30 s |
| polling | `classroom.poll` every 15 min | users with `last_active_at` in 7 days, last sync > 15 min, not in quota backoff |
| manual | `POST /classroom/sync` | 1/min per user (+ route limit 3/min/IP); runs inline; dashboard cache dropped |
| renewal | `classroom.renewRegistrations` daily | re-registers `COURSE_WORK_CHANGES` per course when < 2 days to expiry |

Each run is recorded in `classroom_sync_runs`. Diffing: new coursework/material/announcement → feed item + notification; submission newly `RETURNED` with a grade → `graded` feed item + notification; removed items are deleted; removed courses purge all their rows.

## Student-facing status
`RETURNED` → Graded · `TURNED_IN` → Turned in · past due and not turned in → Missing · otherwise Pending.

## Notifications (spec §10)
`classroom.assignment_new`, `material_new`, `announcement`, `graded` (in-app + push); `due_24h`, `due_2h` (in-app + push, email via `classroom_deadline` template when the user enables email); `overdue` once (in-app). Dedupe key `user:type:ref` (unique index). Per type × channel toggles in Profile. Quiet hours 23:00–07:00 Asia/Dhaka for non-urgent push (`due_2h` is urgent). > 3 Classroom pushes in 10 min → one summary push. Reminders job runs every 5 min (windows ±30 min) and skips turned-in work.

## Errors
| Condition | Detection | State / UX |
|---|---|---|
| Revoked/expired grant | token endpoint `invalid_grant` or API 401 | `needs_reauth` → "আবার সংযুক্ত করুন" card; manual sync returns `CLASSROOM_REAUTH` |
| University Workspace blocks third-party apps | 403 with `ClassroomApiDisabled`/permission/admin wording | `workspace_blocked` → explanation + "ask IT or use a personal account" |
| Quota | 429 / `RESOURCE_EXHAUSTED` / `rateLimitExceeded` | exponential backoff via `next_sync_allowed_at` (5 min → 2 h), last-synced time stays visible |

## Disconnect
`DELETE /classroom/connection`: delete push registrations at Google, revoke the token, delete every `classroom_*` row, Classroom-derived notifications, and the connection; dashboard cache dropped. Test: `test/classroom.test.ts`.

## Dashboard
`GET /dashboard` returns per-widget `{data, error}` sections so one failing widget never blanks the page; cached 60 s in Redis and invalidated after sync/feed-read/disconnect. Web keeps the last payload in `localStorage` and renders it instantly, then refreshes.

## Google Cloud setup (manual)
1. Google Cloud project "ShikkhaSathi" → enable **Google Classroom API** and **Cloud Pub/Sub API**.
2. OAuth consent screen: External, app name **ShikkhaSathi**, logo, support email `support@shikkhasathi.com`, authorized domain `shikkhasathi.com`, privacy `https://shikkhasathi.com/privacy`, terms `https://shikkhasathi.com/terms`, add the scopes above. Testing mode + test users until verified.
3. Credentials → OAuth client (Web): redirect URI `https://shikkhasathi.com/api/v1/classroom/oauth/callback` (and `http://localhost:5173/api/v1/classroom/oauth/callback` for dev — Vite proxies it to the backend). Android client (package `com.shikkhasathi.app` + SHA-1) and iOS client.
4. Pub/Sub: topic `projects/<id>/topics/classroom`; grant `classroom-notifications@system.gserviceaccount.com` the **Pub/Sub Publisher** role on it; push subscription to `https://shikkhasathi.com/api/v1/webhooks/google-pubsub?token=<random>`.
5. Admin → সেটিংস → Google OAuth + Classroom: client id/secret, Android/iOS client ids, topic, push verification token → "সংযোগ পরীক্ষা করুন".

## Verification checklist (track in docs/PROGRESS.md)
Owner actions (they need the ShikkhaSathi Google Cloud project; they can't be done from the codebase):
- [ ] `shikkhasathi.com` verified in Google Search Console (DNS TXT record)
- [x] `/privacy` (with the Google API Services User Data Policy / Limited Use statement) and `/terms`: implemented, and they go live with the first deploy
- [ ] OAuth consent screen: app name **ShikkhaSathi**, logo (120×120 PNG from `app/assets`), support email, authorised domain `shikkhasathi.com`, home page `https://shikkhasathi.com`, privacy and terms links
- [ ] Scopes added exactly as in §Scopes, with the justification text below
- [ ] Demo video uploaded (unlisted YouTube), script below
- [ ] Test users added while in Testing mode, then "Publish app" → submit for verification

### Scope justification (paste into the verification form)
| Scope | Justification |
|---|---|
| `classroom.courses.readonly` | Shows the student's enrolled Google Classroom classes on their ShikkhaSathi dashboard so they can see every class in one place. Read-only; we never create or change classes. |
| `classroom.coursework.me.readonly` | Lists the student's own assignments, due dates and submission status/grades so we can show "due soon" deadlines and send reminders 24 h and 2 h before they are due. Only the signed-in student's own work is read. |
| `classroom.courseworkmaterials.readonly` | Shows new materials (slides, PDFs, links) teachers post, in a unified "new from your classes" feed with a link back to Classroom. |
| `classroom.announcements.readonly` | Shows class announcements (room changes, syllabus notices) in the same feed and as notifications. |
| `classroom.push-notifications` | Registers for Classroom course-work change notifications via Cloud Pub/Sub so the dashboard updates within minutes without frequent polling. This reduces API usage. |
| `openid email profile` | Identifies the Google account the student connects and shows which account is linked. |

Data handling statement: tokens are stored server-side only, encrypted with AES-256-GCM, and never sent to the browser or mobile app. Classroom data is cached only to render the student's own dashboard, is never shared with teachers on ShikkhaSathi, other students or third parties, is not used for ads or model training, and is deleted when the student disconnects (we also revoke the token).

### Demo video script (≈ 2 minutes)
1. Open `https://shikkhasathi.com`, sign in as a test student, and go to Home, which shows the "Google Classroom সংযুক্ত করুন" card.
2. Click connect. The Google consent screen shows each read-only scope; pause on it.
3. After returning, the dashboard shows due-soon assignments, the new-from-classes feed, and the calendar dots.
4. Open an assignment, then "Classroom-এ খুলুন" deep link to show we only link out (no posting or submitting).
5. Show a notification (new material / reminder).
6. Go to Profile → Classroom, click "সংযোগ বিচ্ছিন্ন করুন": the data disappears from the dashboard, and the Google account permissions page no longer lists ShikkhaSathi.
