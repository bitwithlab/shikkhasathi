// :shared — the Compose Multiplatform module that holds ALL app code (UI + logic) for Android and iOS.
//
// Sources live outside this folder, in App/mobile/src (CLAUDE.md §3):
//
//   src/core/       designsystem, network, model, datastore, common
//   src/shared/     navigation, strings, di, app root
//   src/features/   auth, home, …
//
// Inside each of those three layer folders:
//   <package dirs>/                 commonMain code (e.g. src/core/network/ApiClient.kt)
//   androidMain/ iosMain/ desktopMain/   platform `actual`s for that layer
//   commonTest/ desktopTest/        tests for that layer
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

val srcRoot = rootProject.layout.projectDirectory.dir("src")
val layers = listOf("core", "shared", "features")
val platformDirs = listOf("androidMain", "iosMain", "desktopMain", "commonTest", "desktopTest")
val composeOnly = listOf("**/ui/**", "designsystem/**")

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
        // Map each KMP source set onto the layer folders.
        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.fromLayers(sub: String?) {
            kotlin.setSrcDirs(layers.map { if (sub == null) srcRoot.dir(it) else srcRoot.dir("$it/$sub") })
            if (sub == null) kotlin.exclude(platformDirs.map { "$it/**" })
            if (!ssCompose) kotlin.exclude(composeOnly)
            resources.setSrcDirs(emptyList<Any>())
        }

        commonMain {
            fromLayers(null)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
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
            fromLayers("commonTest")
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        iosMain {
            fromLayers("iosMain")
            dependencies { implementation(libs.ktor.client.darwin) }
        }
        val desktopMain by getting {
            fromLayers("desktopMain")
            dependencies { implementation(libs.ktor.client.okhttp) }
        }
        val desktopTest by getting { fromLayers("desktopTest") }
        if (ssAndroid) {
            androidMain {
                fromLayers("androidMain")
                dependencies { implementation(libs.ktor.client.okhttp) }
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
