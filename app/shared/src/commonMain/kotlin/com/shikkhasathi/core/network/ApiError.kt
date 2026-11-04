package com.shikkhasathi.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `{ error: { code, message, details? } }` — the one error envelope of the API (website/src/api/contracts/common.ts). */
@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

/** An API failure as the app sees it. [status] is the HTTP status, or 0 when the request never got an answer. */
data class ApiError(
    val code: String,
    val message: String,
    val status: Int,
    val details: JsonElement? = null,
) {
    val isNetwork: Boolean get() = code == NETWORK

    companion object {
        // Mirrors ERROR_CODES in website/src/api/contracts/common.ts, plus client-only codes.
        const val BAD_REQUEST = "BAD_REQUEST"
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val FORBIDDEN = "FORBIDDEN"
        const val NOT_FOUND = "NOT_FOUND"
        const val CONFLICT = "CONFLICT"
        const val RATE_LIMITED = "RATE_LIMITED"
        const val PAYMENT_FAILED = "PAYMENT_FAILED"
        const val INTEGRATION_DISABLED = "INTEGRATION_DISABLED"
        const val CLASSROOM_REAUTH = "CLASSROOM_REAUTH"
        const val CLASSROOM_WORKSPACE_BLOCKED = "CLASSROOM_WORKSPACE_BLOCKED"
        const val CLASSROOM_QUOTA = "CLASSROOM_QUOTA"
        const val INTERNAL = "INTERNAL"

        /** Client-only: no connection / timeout / DNS. */
        const val NETWORK = "NETWORK"

        /** Client-only: the server answered something that is not our envelope (proxy page, HTML 502…). */
        const val UNEXPECTED_RESPONSE = "UNEXPECTED_RESPONSE"

        fun network(cause: Throwable? = null) = ApiError(NETWORK, cause?.message ?: "network error", 0)
    }
}

class ApiException(val error: ApiError, cause: Throwable? = null) : Exception("${error.code}: ${error.message}", cause)

/** `details.retryAfterSec` of RATE_LIMITED / CLASSROOM_QUOTA errors, when the server sent it. */
val ApiError.retryAfterSec: Int?
    get() =
        (details as? kotlinx.serialization.json.JsonObject)
            ?.get("retryAfterSec")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.content
            ?.toDoubleOrNull()
            ?.toInt()
