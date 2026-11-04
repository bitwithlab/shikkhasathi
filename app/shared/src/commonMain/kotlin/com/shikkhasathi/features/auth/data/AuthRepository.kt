package com.shikkhasathi.features.auth.data

import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.model.OtpRequestResponse
import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {
    /** Reading secure storage at launch. */
    data object Restoring : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: Me) : SessionState
}

/** Every call throws `ApiException` on failure; ViewModels turn it into Bangla copy. */
interface AuthRepository {
    val session: StateFlow<SessionState>

    suspend fun restore()

    suspend fun requestOtp(phoneE164: String): OtpRequestResponse

    suspend fun verifyOtp(phoneE164: String, code: String): AuthTokens

    suspend fun loginWithEmail(email: String, password: String): AuthTokens

    suspend fun loginWithGoogle(idToken: String): AuthTokens

    suspend fun logout()
}
