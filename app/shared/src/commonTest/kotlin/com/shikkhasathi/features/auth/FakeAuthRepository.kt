package com.shikkhasathi.features.auth

import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.OtpRequestResponse
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.core.testing.sampleTokens
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.GoogleAuth
import com.shikkhasathi.features.auth.data.GoogleAuthResult
import com.shikkhasathi.features.auth.data.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository : AuthRepository {
    val sessionFlow = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = sessionFlow

    var otpResponse = OtpRequestResponse(sent = true, expiresInSec = 300, resendInSec = 60)
    var failWith: ApiError? = null
    val otpRequests = mutableListOf<String>()
    val verifications = mutableListOf<Pair<String, String>>()
    val emailLogins = mutableListOf<Pair<String, String>>()
    var tokens: AuthTokens = sampleTokens()

    private fun maybeFail() {
        failWith?.let { throw ApiException(it) }
    }

    override suspend fun restore() = Unit

    override suspend fun requestOtp(phoneE164: String): OtpRequestResponse {
        otpRequests += phoneE164
        maybeFail()
        return otpResponse
    }

    override suspend fun verifyOtp(phoneE164: String, code: String): AuthTokens {
        verifications += phoneE164 to code
        maybeFail()
        sessionFlow.value = SessionState.SignedIn(tokens.user)
        return tokens
    }

    override suspend fun loginWithEmail(email: String, password: String): AuthTokens {
        emailLogins += email to password
        maybeFail()
        sessionFlow.value = SessionState.SignedIn(tokens.user)
        return tokens
    }

    override suspend fun loginWithGoogle(idToken: String): AuthTokens {
        maybeFail()
        return tokens
    }

    override suspend fun logout() {
        sessionFlow.value = SessionState.SignedOut
    }
}

class FakeGoogleAuth(var result: GoogleAuthResult = GoogleAuthResult.Unavailable) : GoogleAuth {
    override suspend fun signIn(): GoogleAuthResult = result
}
