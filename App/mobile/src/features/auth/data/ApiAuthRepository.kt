package com.shikkhasathi.features.auth.data

import com.shikkhasathi.core.datastore.TokenStore
import com.shikkhasathi.core.datastore.toStoredSession
import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.model.GoogleLoginBody
import com.shikkhasathi.core.model.LoginBody
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.OtpRequestBody
import com.shikkhasathi.core.model.OtpRequestResponse
import com.shikkhasathi.core.model.OtpVerifyBody
import com.shikkhasathi.core.model.RefreshBody
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [AuthRepository] over the `/api/v1/auth/…` endpoints (Web/shared/src/contracts/auth.ts). */
class ApiAuthRepository(
    private val api: ApiClient,
    private val tokenStore: TokenStore,
    private val device: DeviceInfo,
    appScope: CoroutineScope,
) : AuthRepository {
    private val state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = state.asStateFlow()

    init {
        appScope.launch { api.sessionExpired.collect { state.value = SessionState.SignedOut } }
    }

    override suspend fun restore() {
        val stored = tokenStore.read()
        if (stored == null) {
            state.value = SessionState.SignedOut
            return
        }
        // Show the cached profile immediately, then refresh it; offline launches keep the cached one.
        stored.user?.let { state.value = SessionState.SignedIn(it) }
        try {
            val me = api.get<Me>("me")
            tokenStore.read()?.let { tokenStore.write(it.copy(user = me)) }
            state.value = SessionState.SignedIn(me)
        } catch (_: ApiException) {
            // Refresh rejected (store cleared) or nothing cached to show: back to sign-in.
            if (tokenStore.read() == null || stored.user == null) state.value = SessionState.SignedOut
        }
    }

    override suspend fun requestOtp(phoneE164: String): OtpRequestResponse =
        api.post("auth/otp/request", OtpRequestBody(phoneE164), authenticated = false)

    override suspend fun verifyOtp(phoneE164: String, code: String): AuthTokens =
        signIn(
            api.post(
                "auth/otp/verify",
                OtpVerifyBody(phone = phoneE164, code = code, deviceId = device.deviceId, deviceName = device.deviceName, platform = device.platform),
                authenticated = false,
            ),
        )

    override suspend fun loginWithEmail(email: String, password: String): AuthTokens =
        signIn(
            api.post(
                "auth/login",
                LoginBody(email = email, password = password, deviceId = device.deviceId, deviceName = device.deviceName, platform = device.platform),
                authenticated = false,
            ),
        )

    override suspend fun loginWithGoogle(idToken: String): AuthTokens =
        signIn(
            api.post(
                "auth/google",
                GoogleLoginBody(idToken = idToken, deviceId = device.deviceId, deviceName = device.deviceName, platform = device.platform),
                authenticated = false,
            ),
        )

    override suspend fun logout() {
        val refreshToken = tokenStore.read()?.refreshToken
        try {
            if (refreshToken != null) api.post<RefreshBody, OkResponse>("auth/logout", RefreshBody(refreshToken), authenticated = false)
        } catch (_: ApiException) {
            // Signing out locally must always work, even offline; the server session expires on its own.
        } finally {
            tokenStore.clear()
            state.value = SessionState.SignedOut
        }
    }

    private suspend fun signIn(tokens: AuthTokens): AuthTokens {
        tokenStore.write(tokens.toStoredSession())
        state.value = SessionState.SignedIn(tokens.user)
        return tokens
    }
}
