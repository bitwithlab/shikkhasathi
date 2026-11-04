package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Mirrors of website/src/api/contracts/auth.ts (Zod is the source of truth).
 * Keep field names and optionality identical; `SsJson` omits nulls on encode so Zod `.optional()`
 * fields are left out rather than sent as `null`.
 */

@Serializable
enum class Role {
    @SerialName("student")
    Student,

    @SerialName("seller")
    Seller,

    @SerialName("admin")
    Admin,
}

@Serializable
enum class ClientPlatform {
    @SerialName("web")
    Web,

    @SerialName("android")
    Android,

    @SerialName("ios")
    Ios,
}

@Serializable
enum class AppLocaleCode {
    @SerialName("bn")
    Bn,

    @SerialName("en")
    En,
}

@Serializable
enum class ThemePreference {
    @SerialName("system")
    System,

    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark,
}

@Serializable
enum class SellerVerificationStatus {
    @SerialName("none")
    None,

    @SerialName("pending")
    Pending,

    @SerialName("verified")
    Verified,

    @SerialName("rejected")
    Rejected,
}

@Serializable
data class AcademicSummary(
    val institutionId: String? = null,
    val institutionName: String? = null,
    val institutionShort: String? = null,
    val departmentId: String? = null,
    val departmentName: String? = null,
    val departmentShort: String? = null,
    val programId: String? = null,
    val programName: String? = null,
    val semester: Int? = null,
    val batch: String? = null,
    val section: String? = null,
)

@Serializable
data class SellerSummary(
    val slug: String,
    val verificationStatus: SellerVerificationStatus,
)

@Serializable
data class Me(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val avatarUrl: String? = null,
    val roles: List<Role> = emptyList(),
    val locale: AppLocaleCode = AppLocaleCode.Bn,
    val theme: ThemePreference = ThemePreference.System,
    val lowDataMode: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val hasPassword: Boolean = false,
    val academic: AcademicSummary = AcademicSummary(),
    val seller: SellerSummary? = null,
    val createdAt: String,
) {
    val isSeller: Boolean get() = Role.Seller in roles
}

@Serializable
data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    /** Returned in the body only for mobile clients; web gets an httpOnly cookie. */
    val refreshToken: String? = null,
    val user: Me,
    val isNewUser: Boolean = false,
)

@Serializable
data class OtpRequestBody(
    val phone: String,
)

@Serializable
data class OtpRequestResponse(
    val sent: Boolean,
    val expiresInSec: Int,
    val resendInSec: Int,
    /** Only present outside production with the fake SMS provider, so local login works. */
    val devCode: String? = null,
)

@Serializable
data class OtpVerifyBody(
    val phone: String,
    val code: String,
    val name: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: ClientPlatform,
)

@Serializable
data class LoginBody(
    val email: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: ClientPlatform,
)

@Serializable
data class GoogleLoginBody(
    /** Google ID token from Credential Manager (Android) / GoogleSignIn (iOS). */
    val idToken: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: ClientPlatform,
)

@Serializable
data class RefreshBody(
    val refreshToken: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean,
)

/** Who is calling: sent with every sign-in so the server can list devices (Settings → signed-in devices). */
data class DeviceInfo(
    val platform: ClientPlatform,
    val deviceName: String? = null,
    val deviceId: String? = null,
) {
    companion object {
        /** `Device.deviceName` max length in the auth contract. */
        const val MAX_NAME_LENGTH = 120
    }
}
