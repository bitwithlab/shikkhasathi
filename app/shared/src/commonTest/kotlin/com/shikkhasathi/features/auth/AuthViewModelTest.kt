package com.shikkhasathi.features.auth

import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.features.auth.presentation.AuthIntent
import com.shikkhasathi.features.auth.presentation.AuthStep
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeAuthRepository
    private lateinit var google: FakeGoogleAuth
    private lateinit var vm: AuthViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeAuthRepository()
        google = FakeGoogleAuth()
        vm = AuthViewModel(repo, google)
    }

    @AfterTest
    fun tearDown() {
        vm.clear()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun otpRequestSuccessShowsOtpStepAndCountsDownResendTimer() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("01712-345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()

            val s = vm.state.value
            assertEquals(AuthStep.Otp, s.step)
            assertEquals("+8801712345678", s.phoneE164)
            assertEquals(listOf("+8801712345678"), repo.otpRequests)
            assertEquals(60, s.resendInSec)
            assertFalse(s.canResend)
            assertFalse(s.isSubmitting)

            advanceTimeBy(1_001)
            assertEquals(59, vm.state.value.resendInSec)

            advanceTimeBy(59_000)
            assertEquals(0, vm.state.value.resendInSec)
            assertTrue(vm.state.value.canResend)
        }

    @Test
    fun resendIsIgnoredWhileTimerRunsAndAllowedAfter() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("01712345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()
            vm.onIntent(AuthIntent.ResendOtp)
            runCurrent()
            assertEquals(1, repo.otpRequests.size)

            advanceTimeBy(60_001)
            vm.onIntent(AuthIntent.ResendOtp)
            runCurrent()
            assertEquals(2, repo.otpRequests.size)
            assertEquals(60, vm.state.value.resendInSec)
        }

    @Test
    fun invalidPhoneShowsBanglaFieldErrorWithoutCallingApi() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("12345"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()

            assertEquals(
                "সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)",
                vm.state.value.fieldError
                    ?.resolve(AppLocale.Bn)
            )
            assertTrue(repo.otpRequests.isEmpty())
            assertEquals(AuthStep.Phone, vm.state.value.step)
        }

    @Test
    fun verifySuccessAuthenticates() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("01712345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()

            vm.onIntent(AuthIntent.OtpChanged("১২৩৪৫৬".map { '0' + (it - '০') }.joinToString(""))) // auto-verifies at 6 digits
            runCurrent()

            val s = vm.state.value
            assertTrue(s.isAuthenticated)
            assertEquals("রাফি আহমেদ", s.signedInUser?.name)
            assertEquals(listOf("+8801712345678" to "123456"), repo.verifications)
            assertNull(s.error)
            advanceUntilIdle() // timer was cancelled: nothing left to run
        }

    @Test
    fun apiErrorOnVerifyShowsServerBanglaMessageAndClearsCode() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("01712345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()
            repo.failWith = ApiError(ApiError.UNAUTHORIZED, "কোডটি সঠিক নয় বা মেয়াদ শেষ হয়ে গেছে", 401)

            vm.onIntent(AuthIntent.OtpChanged("654321"))
            runCurrent()

            val s = vm.state.value
            assertFalse(s.isAuthenticated)
            assertEquals("কোডটি সঠিক নয় বা মেয়াদ শেষ হয়ে গেছে", s.error?.resolve(AppLocale.Bn))
            assertEquals("", s.otp)
            assertFalse(s.isSubmitting)
            assertEquals(AuthStep.Otp, s.step)
        }

    @Test
    fun networkAndRateLimitErrorsUseLocalBanglaCopy() =
        test {
            repo.failWith = ApiError.network()
            vm.onIntent(AuthIntent.PhoneChanged("01712345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()
            assertEquals(
                Str.ErrorNetwork.bn,
                vm.state.value.error
                    ?.resolve(AppLocale.Bn)
            )
            assertEquals(AuthStep.Phone, vm.state.value.step)

            repo.failWith = ApiError(ApiError.RATE_LIMITED, "Too many requests", 429)
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()
            assertEquals(
                "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।",
                vm.state.value.error
                    ?.resolve(AppLocale.Bn)
            )
        }

    @Test
    fun emailLoginValidatesThenAuthenticates() =
        test {
            vm.onIntent(AuthIntent.ShowEmailLogin)
            vm.onIntent(AuthIntent.EmailChanged("rafi@"))
            vm.onIntent(AuthIntent.EmailLogin)
            assertEquals(
                Str.AuthEmailInvalid.bn,
                vm.state.value.fieldError
                    ?.resolve()
            )

            vm.onIntent(AuthIntent.EmailChanged(" Rafi@Example.com "))
            vm.onIntent(AuthIntent.EmailLogin)
            assertEquals(
                Str.AuthPasswordRequired.bn,
                vm.state.value.fieldError
                    ?.resolve()
            )

            vm.onIntent(AuthIntent.PasswordChanged("secret-123"))
            vm.onIntent(AuthIntent.EmailLogin)
            runCurrent()

            assertEquals(listOf("rafi@example.com" to "secret-123"), repo.emailLogins)
            assertTrue(vm.state.value.isAuthenticated)
            assertEquals("", vm.state.value.password, "password is not kept in state after sign-in")
        }

    @Test
    fun googleUnavailableExplainsInBangla() =
        test {
            vm.onIntent(AuthIntent.GoogleLogin)
            runCurrent()
            assertEquals(
                Str.AuthGoogleUnavailable.bn,
                vm.state.value.error
                    ?.resolve()
            )
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun changeNumberGoesBackAndStopsTimer() =
        test {
            vm.onIntent(AuthIntent.PhoneChanged("01712345678"))
            vm.onIntent(AuthIntent.RequestOtp)
            runCurrent()
            vm.onIntent(AuthIntent.ChangeNumber)
            advanceTimeBy(5_000)

            assertEquals(AuthStep.Phone, vm.state.value.step)
            assertEquals(0, vm.state.value.resendInSec)
        }
}
