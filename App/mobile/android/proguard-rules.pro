# R8 full mode is on (gradle.properties: android.enableR8.fullMode=true, the AGP 8 default).
# kotlinx.serialization, Ktor, Koin and Compose ship their own consumer rules; only gaps are listed here.

# Keep line numbers for readable crash reports; hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: our @Serializable DTOs (core/model, core/network, core/datastore) are looked up
# through their generated companions; the library rules cover this, the line below documents the intent.
-keep,includedescriptorclasses class com.shikkhasathi.**$$serializer { *; }
-keepclassmembers class com.shikkhasathi.** {
    *** Companion;
}

# Navigation type-safe routes are @Serializable objects resolved by class name.
-keep class com.shikkhasathi.shared.navigation.** { *; }

# Ktor / OkHttp optional dependencies that are not on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class io.ktor.** { volatile <fields>; }
