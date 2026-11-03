# ShikkhaSathi mobile (`App/mobile`)

Kotlin + Compose Multiplatform. Android first; iOS builds from the same code. It talks only to the
website's API (`/api/v1`, contracts in `Web/shared/src/contracts`); there is no mobile-only backend.
The API base URL is set per build (Android `BuildConfig.API_BASE_URL` from `gradle.properties`
`ss.apiBaseUrl` / `ss.apiBaseUrlDebug`; iOS Info.plist `SSApiBaseUrl`).

## Layout

```
App/mobile/
├── settings.gradle.kts      # detects Android SDK → ss.android / ss.compose switches
├── build.gradle.kts         # plugins, ktlint + detekt for all modules
├── gradle/libs.versions.toml
├── shared/build.gradle.kts  # :shared — KMP module; its sources are in ../src
├── android/                 # :android — app host (MainActivity, manifest, FCM, R8, baseline profile)
├── ios/iosApp/              # SwiftUI host for the shared Compose UI (iOSApp.swift, ContentView.swift)
├── assets/fonts/            # Hind Siliguri + Inter (see README there)
└── src/
    ├── core/                # designsystem · network (Ktor) · model (DTOs) · datastore · i18n · common
    ├── shared/              # navigation · di (Koin) · ui (App root)
    └── features/            # auth · home · profile · placeholder (screens not built yet)
```

Inside each of `src/core`, `src/shared`, `src/features`:

| Folder | Source set |
|---|---|
| package folders (`network/`, `auth/data/` …) | `commonMain` |
| `androidMain/`, `iosMain/`, `desktopMain/` | platform `actual`s |
| `commonTest/`, `desktopTest/` | tests |

Packages are `com.shikkhasathi.<layer>.<folder path>` (e.g. `src/features/auth/data` →
`com.shikkhasathi.features.auth.data`). **Compose rule:** files under any `ui/` folder and all of
`core/designsystem/` are Compose UI; everything else (DTOs, Ktor client, token store, repositories,
ViewModels, strings) is plain Kotlin. Features follow UDF: `presentation/XViewModel` (intents →
immutable `XUiState`) → `ui/XScreen` (stateless content composable + thin wrapper).

Targets: `androidTarget` (only when an Android SDK is found), `iosX64` / `iosArm64` /
`iosSimulatorArm64` (compiled on macOS only, skipped elsewhere), and `jvm("desktop")`, a test
harness that runs the shared logic on any JVM.

### Build switches

| Property | Default | Effect |
|---|---|---|
| `ss.android` | `true` when `local.properties` `sdk.dir`, `ANDROID_HOME` or `ANDROID_SDK_ROOT` points at an SDK | adds AGP, `androidTarget()` and the `:android` module |
| `ss.compose` | `true` when `ss.android` is true or the host is macOS | compiles the Compose UI. When it is false, only the headless code is built, so tests run on machines without Google Maven |

Override with `-Pss.android=…` / `-Pss.compose=…`.

## Android (dev machine with the Android SDK)

```bash
cd App/mobile
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew :android:installDebug                       # debug build talks to http://10.0.2.2:3000/api/v1
./gradlew :android:assembleRelease                    # R8 full mode + baseline profile
```

For push, put the Firebase `google-services.json` in `android/`. It is git-ignored, and the
google-services plugin is applied only when the file is there.

## iOS (macOS + Xcode 16)

```bash
cd App/mobile && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # builds Shared.framework
```

Create the Xcode app target once (File → New → Project → App, folder `ios/`), add
`iosApp/iOSApp.swift`, `ContentView.swift`, `Info.plist`, `iosApp.entitlements`, and add a
"Run Script" build phase before *Compile Sources*:

```bash
cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Set `SS_API_BASE_URL` per build configuration. Details are in `docs/MOBILE_NOTES.md`.

## Tests and checks (any machine, including the cloud container)

```bash
cd App/mobile
./gradlew :shared:desktopTest           # ViewModels, ApiClient (Ktor MockEngine), status chips, strings, DI graph
./gradlew ktlintCheck detekt            # add -Pss.compose=true to lint the UI files too (no compile needed)
```

## Strings

All copy is in `src/core/i18n/Strings.kt` (`enum Str`, Bangla + English). Keys use the web
client's i18n names (`auth.title`, `nav.home`, `home.greeting` …) and i18next-style `{{name}}`
placeholders, so the same key means the same text on web and mobile. Composables call `Str.X.t()`.
ViewModels emit `UiText`, which is resolved in the UI. Server error messages are already Bangla
and are shown as they arrive.
