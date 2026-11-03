# Mobile notes (`App/mobile`)

Working notes for the Compose Multiplatform app. Structure and build commands are in `App/README.md`.

## What is verified where

| Part | Cloud container (Linux, no Google Maven) | Dev machine |
|---|---|---|
| Headless shared code: DTOs, Ktor `ApiClient`, token store interface, repositories, ViewModels, strings, Koin graph | compiled and unit-tested (`:shared:desktopTest`) | same |
| Compose UI (`**/ui/**`, `core/designsystem/**`) | ktlint and detekt only (`-Pss.compose=true`); **not compiled** | `./gradlew :shared:compileKotlinDesktop -Pss.compose=true` or the Android build |
| `:android` app module | not configured (no SDK, no AGP) | `./gradlew :android:assembleDebug` |
| iOS (`iosMain`, Keychain store, `MainViewController`) | not compiled (needs macOS) | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |

Why Compose cannot compile here: Compose Multiplatform 1.9 publishes its JVM/desktop artifacts as
aliases of Google's `androidx.compose.*`, `androidx.collection`, `androidx.annotation`,
`androidx.lifecycle` and `androidx.savedstate` artifacts. Those exist only on Google Maven, and in
this container `maven.google.com` redirects to `dl.google.com`, which is blocked. Every
`org.jetbrains.*`, Koin, Ktor and Lucide coordinate does resolve from Maven Central
(`./gradlew :shared:dependencies --configuration desktopCompileClasspath -Pss.compose=true`
reports only `androidx.*` as FAILED).

**First thing to do on a machine with the SDK:** run `./gradlew :android:assembleDebug` and
`./gradlew :shared:compileKotlinDesktop -Pss.compose=true`, then fix any Compose API drift. The UI
was written against CMP 1.9.3 / material3 1.9.0 / navigation 2.9.1 / lifecycle 2.9.5 without a compiler.

## iOS host setup (one time, on macOS)

1. Xcode → New Project → iOS App "iosApp", SwiftUI, saved in `App/mobile/ios/`. Delete the
   generated Swift files and add `iosApp/iOSApp.swift`, `ContentView.swift`, `Info.plist`, and
   `iosApp.entitlements` from this repo.
2. Build Phases → New Run Script (before *Compile Sources*):
   `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`.
3. Build Settings: `FRAMEWORK_SEARCH_PATHS += $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`,
   `OTHER_LDFLAGS += -framework Shared`, `ENABLE_USER_SCRIPT_SANDBOXING = NO`, and a user-defined
   `SS_API_BASE_URL` (Debug: your LAN server, e.g. `http://192.168.0.10:3000/api/v1`; Release:
   `https://shikkhasathi.com/api/v1`).
4. Capabilities: Push Notifications, Associated Domains (already in the entitlements).
5. GoogleSignIn SDK (Swift Package `GoogleSignIn-iOS`) for Classroom connect: add `GIDClientID` (iOS client) and
   `GIDServerClientID` (the server's Web client id) to Info.plist, implement `IosServerAuthCodeProvider` in Swift
   (sample in `src/features/iosMain/classroom/GoogleAuthorization.ios.kt`) and call
   `installGoogleCodeProvider(...)` at launch. Until then Classroom connect shows "unavailable" on iOS.

## Implementation notes

- **Token refresh** lives in `ApiClient.send`, not in Ktor's `Auth` plugin. On a 401 it runs one
  single-flight `POST auth/refresh` with `{ refreshToken }` in the body (ADR-010), stores the rotated
  pair and replays the request. 400/401/403 from refresh clears the session and emits `sessionExpired`.
  5xx/429 keeps the session and surfaces the original 401.
- **Secure storage:** Android uses AES-256-GCM with a non-exportable `AndroidKeyStore` key, and the
  ciphertext goes in private SharedPreferences. The file is excluded from backup and device transfer
  (`data_extraction_rules.xml`, `allowBackup=false`). This replaces `androidx.security:security-crypto`,
  which is deprecated. iOS uses a Keychain generic password with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
  Desktop stores tokens in memory only.
- **ViewModels** extend a framework-free `SsViewModel` so they test on the JVM. The UI holds each one
  in an androidx `ViewModel` (`rememberSsViewModel`), so it survives configuration changes and is
  scoped to its NavBackStackEntry.
- **Fonts:** the platform default stands in until the TTFs are added (`App/mobile/assets/fonts/README.md`).
- **Google sign-in:** the button calls `GoogleAuth`. Every platform currently binds
  `UnavailableGoogleAuth`, which shows a Bangla message.
- **Baseline profile:** `android/src/main/baseline-prof.txt` is a hand-written, wildcard
  placeholder that covers the app's own packages. Replace it with a generated profile from a
  `:baselineprofile` Macrobenchmark module once the startup journey is stable.
- **App Links / Universal Links** need `/.well-known/assetlinks.json` (release-key SHA-256) and
  `/.well-known/apple-app-site-association` served by `Web/server`.

## Home dashboard + Google Classroom (Phase: dashboard)

- **Screens:** `HomeScreen` (widgets in §0A order), `ClassroomScreen` (bottom bar ক্লাসরুম), `ClassStreamScreen`
  (স্ট্রিম / অ্যাসাইনমেন্ট / ম্যাটেরিয়াল tabs), `AssignmentsScreen` (status + class filters), `CalendarScreen`,
  `NotificationsScreen` (drawer → নোটিফিকেশন and the home bell), `AssignmentSheet`. Drawer gained অ্যাসাইনমেন্ট and
  ক্যালেন্ডার entries (same order as the web sidebar).
- **Per-widget state:** `GET /dashboard` returns `{ data, error }` per widget; `HomeViewModel` maps each to
  `Widget.Loading/Hidden/Failed/Ready`, so one failing widget shows its own compact error card.
- **Offline cache:** `DashboardCache` (interface) stores the last `/dashboard` answer + `savedAt`. Home paints it
  first, then loads from the network; a network failure with a cache shows "অফলাইন — শেষ সংরক্ষিত তথ্য দেখাচ্ছি".
  Implementations: `InMemoryDashboardCache` (desktop/tests) and `FileDashboardCache` (JSON file via kotlinx-io —
  Android `filesDir/cache/dashboard.json`, iOS Application Support). **Room KMP is the planned Android
  implementation (CLAUDE.md §6)** — add it on a machine with Google Maven by implementing `DashboardCache` with a
  one-row entity (`id = 1`, `json`, `savedAt`) and binding it in `FeaturesPlatform.android.kt`. The cache is wiped on
  sign-out (`DashboardCacheJanitor`).
- **Pull-to-refresh:** `POST /classroom/sync` then `GET /dashboard?fresh=1` (bypasses the server's 60 s aggregate
  cache). `RATE_LIMITED` → "এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন"; `CLASSROOM_QUOTA` → shows the
  last-synced time. The data on screen is kept in both cases.
- **Time:** `kotlinx-datetime` 0.7.1 (Maven Central). Every day decision uses **Asia/Dhaka** (`core/common/time`),
  matching the server's due groups and week strip. Bangla relative time / clock text ("সিঙ্ক হয়েছে ২ মিনিট আগে",
  "আজ রাত ১১:৫৯") is rendered from `UiText` (`ClockTime`, `DayMonth`, `Weekday`, `Count`, `MonthYear`) so it follows
  the app locale. The calendar week starts on **Sunday** so Bangladesh's Friday–Saturday weekend sits together.
- **Links:** notification / calendar `link`s are web paths; `routeForLink()` opens the matching screen, anything
  else opens `https://shikkhasathi.com` + path.

### Google Classroom connect (server auth code)

`GoogleAuthorization` → `POST /classroom/connect { code, platform }`. Scopes = `CLASSROOM_SCOPES` of the contract
(`openid email profile` + the five Classroom scopes).

| Platform | Implementation | Status |
|---|---|---|
| Android | `AndroidGoogleAuthorization`: `Identity.getAuthorizationClient(activity).authorize(AuthorizationRequest…requestOfflineAccess(serverClientId, true))`; consent `PendingIntent` launched through `GoogleAuthorizationBridge` (registered in `MainActivity.onCreate`) | written, **not compiled or run** (no SDK here) |
| iOS | `IosGoogleAuthorization` calls a Swift `IosServerAuthCodeProvider` (GoogleSignIn `serverAuthCode`) | interface only; Swift side to write |
| Desktop | `DesktopGoogleAuthorization` sends `fake-desktop-dev`; the API accepts `fake…` codes outside production | works against a dev server |

Android needs the server's **Web** OAuth client id at build time: `-Pss.googleServerClientId=…apps.googleusercontent.com`
(or `gradle.properties`) → `BuildConfig.GOOGLE_SERVER_CLIENT_ID` → `AppConfig.googleServerClientId`. Empty = the
connect card says Google connection is unavailable. `play-services-auth` is pinned at 21.3.0; check the latest on
an SDK machine.

**Verify on a dev machine:** compile (`-Pss.compose=true` / `:android:assembleDebug`), then on a device: connect →
consent → classes appear; revoke access in Google account settings → home shows "আবার সংযুক্ত করুন"; pull twice
within a minute → the rate-limit message.

## Phase 11 — mobile parity (student screens, player, exam, checkout, offline, push)

### What is done (headless code compiled + unit-tested on the JVM, UI written and linted)

| Area | Screens (Compose, `ui/`) | Logic (`data/` + `presentation/`, tested) |
|---|---|---|
| Discovery | `ExploreScreen` (search box, debounced autocomplete, filter chips, sort menu, paging), `CourseDetailScreen` (compatibility badge + targets, curriculum, resources, "এই কোর্স কাদের জন্য নয়", disclaimers, one pinned CTA) | `CatalogRepository` (`GET /search`, `/search/suggest`, `/courses/:slug`, wishlist), `ExploreViewModel` (stale answers cancelled), `CourseDetailViewModel` (`CourseCta`: buy / continue / owner preview, optimistic wishlist) |
| Checkout | `CheckoutScreen` (summary, coupon, bKash/Nagad/Rocket/card, waiting, success, failed), `CheckoutResultScreen` (gateway return App Link) | `OrdersRepository`, `CheckoutViewModel` (one idempotency key per coupon, `POST /orders` → `POST /orders/:id/pay` → `ExternalBrowser` (Custom Tab) → poll `GET /orders/:id` every 3 s + on resume), `CheckoutResultViewModel` |
| My Courses + player | `MyCoursesScreen`, `PlayerScreen` (16:9 video, moving watermark, ±10 s, seek bar, speed menu, takeover overlay "এখানে চালান", PDF/text lessons, mark complete, prev/next), tabs লেসন · নোট · প্রশ্নোত্তর, `NotesScreen` (drawer) | `LearningRepository`, `MyCoursesViewModel`, `PlayerViewModel`, `LessonNotesViewModel`, `LessonQaViewModel`, `NotesViewModel`, `WatchTimeTracker` |
| Exam | `ExamModeScreen` (countdown card + readiness ring, today's plan, syllabus, important topics, practice, "পরীক্ষার আগের রাত"), `PlanFormSheet` (M3 date picker + time chips), `FlashcardsScreen`, `MockTestsScreen`, `MockAttemptScreen`, `MockResultScreen` | `ExamRepository`, `ExamModeViewModel`, `FlashcardsViewModel`, `MockTestsViewModel`, `MockAttemptViewModel`, `MockResultViewModel` |
| Offline | `DownloadsScreen` (drawer → ডাউনলোড), "অফলাইনে রাখুন" on PDF lessons | `OfflineDownloadManager`, `ChunkedAead` ("SSE1" container), `ContentKeyStore`, `DownloadPolicy`, `KtorDownloadTransport`, `DownloadsViewModel` |
| Push + links | — | `PushRegistrar` (`POST`/`DELETE /me/push-tokens`), `AppLinkBus`, `routeForLink` (web paths **and** `https://shikkhasathi.com/...`), `SignOut` |

Navigation: bottom bar unchanged (হোম · ক্লাসরুম · কোর্স · Exam · প্রোফাইল); কোর্স = `MyCoursesScreen`, Exam = `ExamModeScreen`.
The drawer gained **ডাউনলোড**. Course requests, settings and Teacher Studio are still placeholders (web has them).
All copy is in `Str` (bn + en, keys follow the web catalogue: `mock.*`, `checkout.*`, `player.*` …); numbers go through
`core/i18n/Formats.kt` (`durationText`, `clockText`, `sizeText`, `takaText`) so Bangla numerals follow the locale.

### Behaviour worth knowing

- **Install id** (`core/datastore/InstallId`): random UUID kept in `KeyValueStore` (Android `ss_prefs`, iOS `NSUserDefaults`).
  It is now also the `deviceId` of sign-in, and the `deviceId` of `POST /lessons/:id/playback` (3-device limit, ADR-022).
- **Heartbeat** (`PlayerViewModel`): every 15 s while playing, plus on pause, lesson change, app background (`ON_STOP`)
  and screen close (sent from the app scope so it survives the ViewModel). `deltaSec` = real playback only
  (`WatchTimeTracker`: forward progress, jumps > 2×speed are seeks, divided by speed, ≤ 60). `streamActive:false` → pause
  + `VideoState.TakenOver`; "এখানে চালান" asks for a new playback grant and resumes at the current position. Offline
  heartbeat failures never stop playback (same as web). The video window is `FLAG_SECURE` on Android.
- **Mock timer** runs on the server clock: skew = `serverNow − device time` at the answer. Autosave is serialised (one
  mutex, the value is read at send time → last write wins). Failed saves are resent before submit. A `409` on save or
  submit means the server already closed the attempt → go to the result. At 0 the attempt auto-submits.
- **Checkout** never trusts the gateway's redirect status: the order is always re-read from the API. A failed order
  drops its idempotency key so "আবার চেষ্টা করুন" creates a fresh order. Return URL `/checkout/result?order=…` is an App Link.
- **Offline downloads** (non-UI part, done for documents):
  - container "SSE1": 64 KB chunks, AES-256-GCM, random IV per chunk, AAD = fileId ‖ chunk index ‖ last-flag →
    seekable (ExoPlayer `DataSource` / PDF renderer can read any range), detects tampering, reordering, chunk swaps
    between files and truncation;
  - one random content key per account, stored only **wrapped** by a device-bound key (`KeyWrapper`: Android Keystore
    AES-GCM non-exportable key; iOS Keychain via CryptoKit from the Swift host). Sign-out deletes files **and** the key;
  - plain bytes never touch disk; files live in `noBackupFilesDir/offline` (Android) / Application Support (iOS);
  - `DownloadPolicy`: purge if another account or another install (restored backup), stop at enrollment `accessUntil`,
    require an online check every 14 days (an outline load calls `verified()`; `enrolled:false` deletes the course's files).
- **Push**: tokens come from the host (Android `SsMessagingService.onNewToken` + `FirebaseMessaging.token` in
  `MainActivity`; iOS `AppDelegate` → `IosHostBridgeKt.onApnsToken`). Registered once per (account, token), retried with
  backoff; `SignOut` unregisters before logging out. Notification taps carry `data.link` → `AppLinkBus` → `routeForLink`
  (`/learn/{c}/{l}?tab=qa`, `/exam-mode/{id}`, `/mock-test/{id}[/result]`, `/c/{slug}`, `/checkout/result?order=`, …).
  Links that arrive before sign-in are replayed once the shell is up.

### Needs an SDK machine / Xcode (not compilable here)

1. **Compile the UI**: `./gradlew :shared:compileKotlinDesktop -Pss.compose=true` and `:android:assembleDebug`. New Compose
   APIs used: `PrimaryTabRow`, `FilterChip`, `ModalBottomSheet`, `DatePickerDialog`, `PullToRefreshBox`,
   `LifecycleResumeEffect` / `LifecycleEventEffect`, `AndroidView(onRelease=)`, `UIKitView`. Fix any API drift.
   Lucide icon names used for the first time (verify they exist in icons-lucide 1.1.0): `ArrowUpDown`, `SearchX`, `X`,
   `Heart`, `Ban`, `ChevronDown/Up/Left/Right`, `CircleCheck`, `Circle`, `Lock`, `Clock`, `Flag`, `CloudOff`, `Pencil`,
   `Trash2`, `Bookmark`, `MessageCircle`, `Play`, `Pause`, `RotateCcw`, `RotateCw`, `Download`, `ExternalLink`,
   `MonitorSmartphone`, `Smartphone`, `CreditCard`, `TriangleAlert`, `Layers`, `Repeat`, `Plus`, `Video`, `CalendarDays`.
2. **Media3** (`media3-exoplayer` + `-hls` 1.8.0, added to the catalog for `androidMain` only): run a lesson on a device —
   resume position, speed, takeover from the web, heartbeat in the server log, black screenshots (FLAG_SECURE).
3. **Custom Tabs**: `CustomTabsBrowser` builds the Custom Tabs intent by hand (no `androidx.browser` in the catalog).
   Optionally switch to `androidx.browser:browser` `CustomTabsIntent` for toolbar colours.
4. **Video downloads**: bind a real `VideoDownloader` on Android — Media3 `DownloadManager` + `HlsDownloader` writing to a
   `SimpleCache` whose sink/source are wrapped with the account content key (Media3 `AesCipherDataSink`/`Source`, or a
   custom `DataSource` over `EncryptedReader`), run from a `DownloadService` (WorkManager scheduler, `foregroundServiceType=dataSync`).
   Playback of an offline video: `CacheDataSource` with the same key, gated by `DownloadManager.validity()`.
   `androidx.security:security-crypto` / `EncryptedFile` is deprecated, so the chunked container replaces it.
   iOS: `AVAssetDownloadURLSession` in the Swift host. Until then `UnsupportedVideoDownloader` refuses video downloads.
5. **iOS Swift side**: implement `IosOfflineCrypto` (CryptoKit `AES.GCM` + Keychain wrapping key) and call
   `installIosOfflineCrypto(...)` at launch; the new `AppDelegate` in `iOSApp.swift` forwards APNs tokens and
   notification links. The APNs token can arrive before Koin starts (it is started by `MainViewController()`); move
   `initKoin` into `AppDelegate.didFinishLaunching` if tokens are dropped.
6. **Baseline + Startup profile**: module `:baselineprofile` (`com.android.test` + `androidx.baselineprofile` 1.4.1,
   Gradle Managed Device `pixel6Api34`), included only with `ss.android`. Generate:
   `./gradlew :android:generateReleaseBaselineProfile` (writes `android/src/release/generated/baselineProfiles/`;
   sign in once on the device first so the journey reaches হোম). Measure cold start:
   `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` on a mid-range phone → `docs/PERFORMANCE.md`
   (budget < 1.5 s). The benchmark build types copy the release signing config — set a debug signing config for local
   runs if release signing comes only from CI. The old hand-written `android/src/main/baseline-prof.txt` stays as a
   fallback until the generated profile is committed.
7. **App Links**: `assetlinks.json` / `apple-app-site-association` must list the release key / team id, and the server's
   checkout return URL must be `https://shikkhasathi.com/checkout/result?...` (it is: `orders.service.ts`).

### How to run (cloud container)

```bash
cd App/mobile
./gradlew --offline :shared:desktopTest                                   # 129 JVM tests (was 68)
./gradlew --offline :shared:ktlintCheck :shared:detekt                   # headless sources
./gradlew --offline :shared:ktlintCheck :shared:detekt -Pss.compose=true # + Compose UI sources (lint only)
```
