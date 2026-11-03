// ShikkhaSathi mobile — Compose Multiplatform project (Android first, iOS from the same code).
//
// Two build switches are computed here and shared with every build script through `gradle.extra`:
//
//   ss.android  — true when an Android SDK is found (local.properties `sdk.dir`, ANDROID_HOME or
//                 ANDROID_SDK_ROOT). Gates the Android Gradle Plugin, the `androidTarget()` of :shared
//                 and the :android application module.
//   ss.compose  — true when Compose UI sources should be compiled. Compose Multiplatform pulls
//                 androidx.* artifacts that are only published on Google Maven, so a machine without
//                 Google Maven (this cloud container, see docs/DECISIONS.md ADR-006) builds the
//                 *headless* half of :shared only: models, network, datastore, repositories and
//                 ViewModels — everything that is unit-tested on the JVM.
//                 Default: true if the Android SDK is present or the host is macOS.
//
// Both can be forced with -Pss.android=true|false / -Pss.compose=true|false (or in gradle.properties).
import java.util.Properties

// Maven Central is addressed via repo1.maven.org: same content as repo.maven.apache.org, and the latter
// rate-limits bursts (HTTP 429) behind some corporate proxies.
pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "shikkhasathi-mobile"

fun flag(name: String): Boolean? =
    providers.gradleProperty(name).orNull?.trim()?.lowercase()?.let { it == "true" }

val localProps =
    Properties().apply {
        val f = file("local.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }

val sdkDir: String? =
    localProps.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")

val androidSdkPresent = sdkDir != null && file(sdkDir).resolve("platforms").isDirectory
val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

val ssAndroid = flag("ss.android") ?: androidSdkPresent
val ssCompose = flag("ss.compose") ?: (ssAndroid || isMacHost)

gradle.extra["ss.android"] = ssAndroid
gradle.extra["ss.compose"] = ssCompose

include(":shared")
if (ssAndroid) {
    check(ssCompose) { "ss.android=true requires ss.compose=true (the app hosts the Compose UI)." }
    include(":android")
    // Macrobenchmark module that generates the Baseline + Startup profile on a device (docs/MOBILE_NOTES.md).
    include(":baselineprofile")
}

println("ShikkhaSathi: ss.android=$ssAndroid, ss.compose=$ssCompose")
