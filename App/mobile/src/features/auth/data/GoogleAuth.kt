package com.shikkhasathi.features.auth.data

import org.koin.core.module.Module

/**
 * Native Google sign-in that yields an ID token for `POST /auth/google`.
 *
 * Planned implementations (CLAUDE.md §0A): Android — Credential Manager (`GetGoogleIdOption`, server client id
 * from Admin → Integrations); iOS — GoogleSignIn SDK. Until they land, platforms bind [UnavailableGoogleAuth]
 * and the button tells the user to use their mobile number instead.
 */
interface GoogleAuth {
    suspend fun signIn(): GoogleAuthResult
}

sealed interface GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult

    data object Cancelled : GoogleAuthResult

    data object Unavailable : GoogleAuthResult

    data class Failed(val reason: String) : GoogleAuthResult
}

object UnavailableGoogleAuth : GoogleAuth {
    override suspend fun signIn(): GoogleAuthResult = GoogleAuthResult.Unavailable
}

/** Platform bindings for features (currently [GoogleAuth]). */
expect fun featuresPlatformModule(): Module
