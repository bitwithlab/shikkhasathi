// :android — the Android application (included only when an Android SDK is present; see settings.gradle.kts).
// All UI and logic live in :shared (App/mobile/src); this module is the thin host: Application (Koin),
// MainActivity (App()), manifest (App Links, permissions), FCM service, R8 + baseline profile.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val apiBaseUrl = providers.gradleProperty("ss.apiBaseUrl").get()
val apiBaseUrlDebug = providers.gradleProperty("ss.apiBaseUrlDebug").get()

android {
    namespace = "com.shikkhasathi.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shikkhasathi.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // Bangla first, English second; drop every other locale from dependencies' resources.
        resourceConfigurations += listOf("bn", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            // Signing: configure `signingConfigs.release` from CI secrets (docs/RUNBOOK.md); never commit keystores.
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Installs the baseline profile (src/main/baseline-prof.txt) on devices without Play cloud profiles.
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

// Firebase config is per environment and not committed; the plugin is applied only when the file exists.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
