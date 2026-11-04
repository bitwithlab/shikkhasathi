# ShikkhaSathi mobile (`app/`)

Kotlin + Compose Multiplatform. Android first; iOS builds from the same code. It talks only to the
Spring Boot API (`/api/v1`, OpenAPI in `docs/api/openapi.json`); there is no mobile-only backend.
The API base URL is set per build (Android `BuildConfig.API_BASE_URL` from `gradle.properties`
`ss.apiBaseUrl` / `ss.apiBaseUrlDebug`; iOS Info.plist `SSApiBaseUrl`).

## Layout

```
app/
├── settings.gradle.kts      # detects Android SDK → ss.android / ss.compose switches
├── build.gradle.kts         # plugins, ktlint + detekt for all modules
├── gradle.properties        # API base URLs, build switches
├── gradle/libs.versions.toml
├── android/                 # :android — app host (MainActivity, manifest, FCM, R8, baseline profile)
├── baselineprofile/         # :baselineprofile — macrobenchmark generator for :android
├── ios/
│   ├── ShikkhaSathi.xcodeproj   # iOS app target, embeds the Kotlin `Shared` framework
│   └── Sources/                 # iOSApp.swift, ContentView.swift, Info.plist, entitlements
├── shared/                  # :shared — KMP module with ALL app code (UI + logic)
│   └── src/
│       ├── commonMain/kotlin/com/shikkhasathi/{core,shared,features}/…
│       ├── commonTest/  androidMain/  iosMain/  desktopMain/  desktopTest/   (same package tree)
└── assets/fonts/            # Hind Siliguri + Inter (see README there)
```

The package tree has three layers: `core` (designsystem · network (Ktor) · model (DTOs) · datastore ·
i18n · player · common), `shared` (navigation · di (Koin) · ui (App root) · platform) and `features`
(auth · home · classroom · explore · learning · exam · notifications · profile …).

**Compose rule:** files under any `ui/` folder and all of `com/shikkhasathi/core/designsystem/` are
Compose UI; everything else (DTOs, Ktor client, token store, repositories, ViewModels, strings) is
plain Kotlin. Features follow UDF: `presentation/XViewModel` (intents → immutable `XUiState`) →
`ui/XScreen` (stateless content composable + thin wrapper).

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
cd app
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew :android:installDebug                       # debug build talks to http://10.0.2.2:8080/api/v1
./gradlew :android:assembleRelease                    # R8 full mode + baseline profile
```

For push, put the Firebase `google-services.json` in `android/`. It is git-ignored, and the
google-services plugin is applied only when the file is there.

## iOS (macOS + Xcode 16)

```bash
cd app && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # optional: builds Shared.framework once
open ios/ShikkhaSathi.xcodeproj
```

The **Compile Kotlin Framework** build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
before Swift compiles, and `FRAMEWORK_SEARCH_PATHS` points at `shared/build/xcode-frameworks`.
`SS_API_BASE_URL` is a build setting (Debug: `http://localhost:8080/api/v1`, Release:
`https://shikkhasathi.com/api/v1`). Set your signing team in *Signing & Capabilities* before running
on a device. Details are in `docs/MOBILE_NOTES.md`.

## Tests and checks (any machine, including the cloud container)

```bash
cd app
./gradlew :shared:desktopTest           # ViewModels, ApiClient (Ktor MockEngine), status chips, strings, DI graph
./gradlew ktlintCheck detekt            # add -Pss.compose=true to lint the UI files too (no compile needed)
```

## Strings

All copy is in `shared/src/commonMain/kotlin/com/shikkhasathi/core/i18n/Strings.kt` (`enum Str`, Bangla + English). Keys use the web
client's i18n names (`auth.title`, `nav.home`, `home.greeting` …) and i18next-style `{{name}}`
placeholders, so the same key means the same text on web and mobile. Composables call `Str.X.t()`.
ViewModels emit `UiText`, which is resolved in the UI. Server error messages are already Bangla
and are shown as they arrive.
