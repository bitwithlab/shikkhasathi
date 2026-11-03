package com.shikkhasathi.features.classroom.data

/**
 * Asks Google for read-only Classroom access and returns a one-time **server auth code**, which the app sends to
 * `POST /classroom/connect` (the server exchanges it; refresh tokens never live on the device, CLAUDE.md §0A).
 *
 * Platform implementations (bound by `featuresPlatformModule()`):
 * - Android: Google Identity `AuthorizationClient` with [CLASSROOM_SCOPES] + `requestOfflineAccess(serverClientId)`.
 * - iOS: GoogleSignIn SDK (`GIDSignIn.signIn(withPresenting:hint:additionalScopes:)` → `serverAuthCode`) via a Swift bridge.
 * - Desktop (dev harness): a `fake…` code, which the API accepts outside production (FakeClassroomClient).
 */
interface GoogleAuthorization {
    suspend fun authorize(): GoogleAuthorizationResult
}

sealed interface GoogleAuthorizationResult {
    data class Success(val serverAuthCode: String) : GoogleAuthorizationResult

    /** The student closed the consent screen. */
    data object Cancelled : GoogleAuthorizationResult

    /** No Play services / no server client id configured / iOS bridge not installed. */
    data object Unavailable : GoogleAuthorizationResult

    data class Failed(val reason: String) : GoogleAuthorizationResult
}

/** Server OAuth client id (Web application type) that the auth code is minted for; from build config. */
data class GoogleClientConfig(
    val serverClientId: String?,
)

/**
 * Scopes requested on mobile — exactly `CLASSROOM_SCOPES` in Web/shared contracts/classroom.ts: `openid email profile`
 * (the server reads the Google account email from the exchanged id_token) plus the five Classroom scopes. All are
 * read-only except `push-notifications`, which only lets the server register Pub/Sub change notifications.
 */
val CLASSROOM_SCOPES: List<String> =
    listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/classroom.courses.readonly",
        "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
        "https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly",
        "https://www.googleapis.com/auth/classroom.announcements.readonly",
        "https://www.googleapis.com/auth/classroom.push-notifications",
    )

/** Dev/test implementation: a code the non-production API turns into a fake Classroom account. */
class FakeGoogleAuthorization(private val code: String = "fake-mobile-dev") : GoogleAuthorization {
    override suspend fun authorize(): GoogleAuthorizationResult = GoogleAuthorizationResult.Success(code)
}
