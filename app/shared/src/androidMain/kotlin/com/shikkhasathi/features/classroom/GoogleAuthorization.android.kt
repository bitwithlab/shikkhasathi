package com.shikkhasathi.features.classroom.data

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Holds the current activity and an `ActivityResultLauncher` for Google's consent screen.
 * `MainActivity.onCreate` calls [attach] before `setContent` (launchers must be registered before STARTED).
 */
class GoogleAuthorizationBridge {
    private var activity: WeakReference<ComponentActivity>? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<Intent?>? = null

    fun attach(host: ComponentActivity) {
        activity = WeakReference(host)
        launcher =
            host.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                pending?.complete(if (result.resultCode == Activity.RESULT_OK) result.data else null)
                pending = null
            }
    }

    internal val current: ComponentActivity? get() = activity?.get()

    /** Shows the consent screen; returns its result Intent, or null when the student backed out. */
    internal suspend fun launch(request: IntentSenderRequest): Intent? {
        val l = launcher ?: return null
        val deferred = CompletableDeferred<Intent?>()
        pending?.complete(null)
        pending = deferred
        l.launch(request)
        return deferred.await()
    }
}

/**
 * Google Identity Services `AuthorizationClient` (play-services-auth ≥ 20.7): requests the Classroom scopes with
 * `requestOfflineAccess(serverClientId, forceCodeForRefreshToken = true)` so the result carries a server auth
 * code the API can exchange for a refresh token. Untested on a device from the cloud container (no SDK).
 */
class AndroidGoogleAuthorization(
    private val bridge: GoogleAuthorizationBridge,
    private val config: GoogleClientConfig,
) : GoogleAuthorization {
    override suspend fun authorize(): GoogleAuthorizationResult {
        val clientId = config.serverClientId?.takeIf { it.isNotBlank() }
        val activity = bridge.current
        if (clientId == null || activity == null) return GoogleAuthorizationResult.Unavailable
        val client = Identity.getAuthorizationClient(activity)
        val request =
            AuthorizationRequest
                .builder()
                .setRequestedScopes(CLASSROOM_SCOPES.map { Scope(it) })
                .requestOfflineAccess(clientId, true)
                .build()
        return try {
            val first = client.authorize(request).awaitResult()
            // Already granted → the result carries the code; otherwise show Google's consent screen first.
            val final = if (first.hasResolution()) resolve(client, first) else first
            val code = final?.serverAuthCode
            when {
                final == null -> GoogleAuthorizationResult.Cancelled
                code != null -> GoogleAuthorizationResult.Success(code)
                else -> GoogleAuthorizationResult.Failed("no server auth code")
            }
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                GoogleAuthorizationResult.Cancelled
            } else {
                GoogleAuthorizationResult.Failed("gms ${e.statusCode}")
            }
        }
    }

    /** Launches the consent screen; null when the student backs out. */
    private suspend fun resolve(
        client: AuthorizationClient,
        pendingResult: AuthorizationResult,
    ): AuthorizationResult? {
        val sender = pendingResult.pendingIntent?.intentSender ?: return null
        val data = bridge.launch(IntentSenderRequest.Builder(sender).build()) ?: return null
        return client.getAuthorizationResultFromIntent(data)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
