// :shared — the Compose Multiplatform module that holds ALL app code (UI + logic) for Android and iOS.
//
// Standard KMP layout: src/<sourceSet>/kotlin/com/shikkhasathi/<layer>/…
//   sourceSet: commonMain, commonTest, androidMain, iosMain, desktopMain, desktopTest
//   layer:     core (designsystem, network, model, datastore, player) · shared (navigation, strings, di, app root) · features (auth, home, …)
//
// Compose rule: any file under a `ui/` directory, plus everything in `core/designsystem/`, is Compose UI.
// When `ss.compose=false` (no Google Maven, e.g. the cloud container) those files and the Compose
// dependencies are left out, and the remaining headless code — models, Ktor client, token store,
// repositories, ViewModels — still compiles and is tested on the JVM via the `desktop` target.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val ssAndroid = gradle.extra["ss.android"] as Boolean
val ssCompose = gradle.extra["ss.compose"] as Boolean

if (ssAndroid) apply(plugin = "com.android.library")
if (ssCompose) apply(plugin = "org.jetbrains.kotlin.plugin.compose")

val composeOnly = listOf("**/ui/**", "com/shikkhasathi/core/designsystem/**")

kotlin {
    if (ssAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        if (!ssCompose) all { kotlin.exclude(composeOnly) }

        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                api(libs.koin.core)
                if (ssCompose) {
                    api(libs.compose.runtime)
                    api(libs.compose.foundation)
                    api(libs.compose.ui)
                    api(libs.compose.material3)
                    implementation(libs.androidx.lifecycle.viewmodel.compose)
                    implementation(libs.androidx.lifecycle.runtime.compose)
                    implementation(libs.androidx.navigation.compose)
                    implementation(libs.lucide.icons)
                    implementation(libs.koin.compose)
                }
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        iosMain {
            dependencies { implementation(libs.ktor.client.darwin) }
        }
        val desktopMain by getting {
            dependencies { implementation(libs.ktor.client.okhttp) }
        }
        if (ssAndroid) {
            androidMain {
                dependencies {
                    implementation(libs.ktor.client.okhttp)
                    // AuthorizationClient for the Classroom server auth code (GoogleAuthorization.android.kt).
                    implementation(libs.play.services.auth)
                    // ActivityResultLauncher for the consent screen (GoogleAuthorizationBridge).
                    implementation(libs.androidx.activity.compose)
                    // Lesson player: ExoPlayer + HLS (core/androidMain/player/ExoMediaPlayer.kt).
                    implementation(libs.media3.exoplayer)
                    implementation(libs.media3.exoplayer.hls)
                }
            }
        }
    }
}

if (ssCompose) {
    extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
        // Our @Serializable DTOs are immutable data classes from a non-Compose package: declare them stable.
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
        // `-Pss.composeReports` writes stability/skippability reports to shared/build/compose (CLAUDE.md §6).
        if (providers.gradleProperty("ss.composeReports").isPresent) {
            reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        }
    }
}

if (ssAndroid) {
    // Configured dynamically so this script compiles without AGP on the classpath.
    extensions.getByName("android").withGroovyBuilder {
        setProperty("namespace", "com.shikkhasathi.shared")
        setProperty(
            "compileSdk",
            libs.versions.android.compileSdk
                .get()
                .toInt()
        )
        "defaultConfig" {
            setProperty(
                "minSdk",
                libs.versions.android.minSdk
                    .get()
                    .toInt()
            )
        }
        "compileOptions" {
            setProperty("sourceCompatibility", JavaVersion.VERSION_17)
            setProperty("targetCompatibility", JavaVersion.VERSION_17)
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/Tokens.kt")
    if (!ssCompose) exclude(composeOnly)
}
