// Root build. Plugin versions come from gradle/libs.versions.toml.
//
// The Android Gradle Plugin is put on the build classpath only when an Android SDK is present
// (`ss.android`, computed in settings.gradle.kts). Without it, AGP is never resolved, so the
// headless JVM build works on machines that cannot reach Google Maven.
buildscript {
    val ssAndroid = gradle.extra["ss.android"] as Boolean
    dependencies {
        if (ssAndroid) {
            classpath(libs.android.gradlePlugin)
            classpath(libs.google.services.gradlePlugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlintCli.get())
        android.set(false)
        // Generated from Web/shared/src/tokens/tokens.json by scripts/gen-tokens.mjs.
        filter { exclude { it.file.name == "Tokens.kt" } }
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // :shared keeps its sources in App/mobile/src (CLAUDE.md §3), not in the module folder.
        if (project.name == "shared") source.setFrom(rootProject.files("src"))
    }
    // detekt 1.23.x embeds Kotlin 2.0.21; keep its own classpath on that version even though the
    // project compiles with a newer Kotlin (known detekt/KGP mismatch, fixed in detekt 2.x).
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
        }
    }
}
