package com.shikkhasathi.features.auth.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.BdPhone
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.GoogleAuth
import com.shikkhasathi.features.auth.data.GoogleAuthResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { Phone, Otp, Email }

/** Immutable screen state for the sign-in flow. */
data class AuthUiState(
    val step: AuthStep = AuthStep.Phone,
    val phoneInput: String = "",
    /** Normalised `+8801XXXXXXXXX` once an OTP was requested. */
    val phoneE164: String? = null,
    val otp: String = "",
    val resendInSec: Int = 0,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val fieldError: UiText? = null,
    val error: UiText? = null,
    /** Shown only in development builds where the server's fake SMS provider returns the code. */
    val devCode: String? = null,
    val signedInUser: Me? = null,
) {
    val canResend: Boolean get() = step == AuthStep.Otp && resendInSec == 0 && !isSubmitting
    val isAuthenticated: Boolean get() = signedInUser != null
}

sealed interface AuthIntent {
    data class PhoneChanged(val value: String) : AuthIntent

    data object RequestOtp : AuthIntent

    data class OtpChanged(val value: String) : AuthIntent

    data object VerifyOtp : AuthIntent

    data object ResendOtp : AuthIntent

    data object ChangeNumber : AuthIntent

    data object ShowEmailLogin : AuthIntent

    data object ShowPhoneLogin : AuthIntent

    data class EmailChanged(val value: String) : AuthIntent

    data class PasswordChanged(val value: String) : AuthIntent

    data object EmailLogin : AuthIntent

    data object GoogleLogin : AuthIntent

    data object DismissError : AuthIntent
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val googleAuth: GoogleAuth,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private var timer: Job? = null

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.PhoneChanged ->
                mutableState.update {
                    it.copy(
                        phoneInput =
                            intent.value
                                .filter { c ->
                                    c.isDigit() || c == '+'
                                }.take(PHONE_MAX),
                        fieldError = null
                    )
                }
            AuthIntent.RequestOtp -> requestOtp()
            is AuthIntent.OtpChanged -> onOtpChanged(intent.value)
            AuthIntent.VerifyOtp -> verifyOtp()
            AuthIntent.ResendOtp -> if (state.value.canResend) requestOtp()
            AuthIntent.ChangeNumber -> {
                timer?.cancel()
                mutableState.update {
                    it.copy(
                        step = AuthStep.Phone,
                        otp = "",
                        resendInSec = 0,
                        devCode = null,
                        error = null,
                        fieldError = null
                    )
                }
            }
            AuthIntent.ShowEmailLogin -> mutableState.update { it.copy(step = AuthStep.Email, error = null, fieldError = null) }
            AuthIntent.ShowPhoneLogin -> mutableState.update { it.copy(step = AuthStep.Phone, error = null, fieldError = null) }
            is AuthIntent.EmailChanged -> mutableState.update { it.copy(email = intent.value.trim(), fieldError = null) }
            is AuthIntent.PasswordChanged -> mutableState.update { it.copy(password = intent.value, fieldError = null) }
            AuthIntent.EmailLogin -> emailLogin()
            AuthIntent.GoogleLogin -> googleLogin()
            AuthIntent.DismissError -> mutableState.update { it.copy(error = null) }
        }
    }

    private fun requestOtp() {
        val current = state.value
        if (current.isSubmitting) return
        val phone = current.phoneE164.takeIf { current.step == AuthStep.Otp } ?: BdPhone.normalize(current.phoneInput)
        if (phone == null) {
            mutableState.update { it.copy(fieldError = Str.AuthPhoneInvalid.asUiText()) }
            return
        }
        submit {
            val response = repository.requestOtp(phone)
            mutableState.update {
                it.copy(
                    step = AuthStep.Otp,
                    phoneE164 = phone,
                    otp = "",
                    devCode = response.devCode,
                    resendInSec = response.resendInSec.takeIf { s -> s > 0 } ?: DEFAULT_RESEND_SEC,
                )
            }
            startResendTimer()
        }
    }

    private fun onOtpChanged(value: String) {
        val digits = value.filter { it.isDigit() }.take(OTP_LENGTH)
        mutableState.update { it.copy(otp = digits, fieldError = null, error = null) }
        if (digits.length == OTP_LENGTH) verifyOtp()
    }

    private fun verifyOtp() {
        val current = state.value
        val phone = current.phoneE164 ?: return
        if (current.isSubmitting) return
        if (current.otp.length != OTP_LENGTH) {
            mutableState.update { it.copy(fieldError = Str.AuthOtpInvalid.asUiText()) }
            return
        }
        submit(onError = { mutableState.update { it.copy(otp = "") } }) {
            val tokens = repository.verifyOtp(phone, current.otp)
            timer?.cancel()
            mutableState.update { it.copy(signedInUser = tokens.user) }
        }
    }

    private fun emailLogin() {
        val current = state.value
        if (current.isSubmitting) return
        when {
            !EMAIL.matches(current.email) -> mutableState.update { it.copy(fieldError = Str.AuthEmailInvalid.asUiText()) }
            current.password.isEmpty() -> mutableState.update { it.copy(fieldError = Str.AuthPasswordRequired.asUiText()) }
            else ->
                submit {
                    val tokens = repository.loginWithEmail(current.email.lowercase(), current.password)
                    mutableState.update { it.copy(signedInUser = tokens.user, password = "") }
                }
        }
    }

    private fun googleLogin() {
        if (state.value.isSubmitting) return
        submit {
            when (val result = googleAuth.signIn()) {
                is GoogleAuthResult.Success -> {
                    val tokens = repository.loginWithGoogle(result.idToken)
                    mutableState.update { it.copy(signedInUser = tokens.user) }
                }
                GoogleAuthResult.Cancelled -> Unit
                GoogleAuthResult.Unavailable -> mutableState.update { it.copy(error = Str.AuthGoogleUnavailable.asUiText()) }
                is GoogleAuthResult.Failed -> mutableState.update { it.copy(error = Str.ErrorGeneric.asUiText()) }
            }
        }
    }

    private fun startResendTimer() {
        timer?.cancel()
        timer =
            scope.launch {
                while (state.value.resendInSec > 0) {
                    delay(TICK_MS)
                    mutableState.update { it.copy(resendInSec = (it.resendInSec - 1).coerceAtLeast(0)) }
                }
            }
    }

    private fun submit(
        onError: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        mutableState.update { it.copy(isSubmitting = true, error = null, fieldError = null) }
        scope.launch {
            try {
                block()
            } catch (e: ApiException) {
                onError()
                mutableState.update { it.copy(error = e.error.toUiText()) }
            } finally {
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    companion object {
        const val OTP_LENGTH = 6
        const val DEFAULT_RESEND_SEC = 60
        private const val PHONE_MAX = 14
        private const val TICK_MS = 1_000L
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/**
 * Server messages are already Bangla (Web/server common/errors.ts), so they are shown as-is;
 * transport failures, rate limits and unexpected answers get our own sympathetic copy.
 */
fun ApiError.toUiText(): UiText =
    when {
        isNetwork -> Str.ErrorNetwork.asUiText()
        code == ApiError.RATE_LIMITED -> Str.ErrorRateLimited.asUiText()
        code == ApiError.INTERNAL || code == ApiError.UNEXPECTED_RESPONSE || message.isBlank() -> Str.ErrorGeneric.asUiText()
        else -> UiText.Raw(message)
    }
