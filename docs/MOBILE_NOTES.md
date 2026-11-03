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
