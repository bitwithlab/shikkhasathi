package com.shikkhasathi.features.auth.data

import org.koin.dsl.module

// Replace with the native implementation when Google sign-in ships (see GoogleAuth).
actual fun featuresPlatformModule() = module { single<GoogleAuth> { UnavailableGoogleAuth } }
