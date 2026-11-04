package com.shikkhasathi.features.classroom.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implemented in Swift by the iOS host with the GoogleSignIn SDK (Swift Package `GoogleSignIn-iOS`):
 *
 * ```swift
 * final class GoogleCodeProvider: IosServerAuthCodeProvider {
 *   func requestServerAuthCode(scopes: [String], completion: @escaping (String?, String?) -> Void) {
 *     guard let root = UIApplication.shared.firstKeyWindow?.rootViewController else { return completion(nil, "no window") }
 *     // Info.plist: GIDClientID (iOS client) + GIDServerClientID (the server's Web client id) → serverAuthCode is set.
 *     GIDSignIn.sharedInstance.signIn(withPresenting: root, hint: nil, additionalScopes: scopes) { result, error in
 *       if let e = error as NSError?, e.code == GIDSignInError.canceled.rawValue { return completion(nil, nil) }
 *       completion(result?.serverAuthCode, error?.localizedDescription)
 *     }
 *   }
 * }
 * // iOSApp.init: IosGoogleAuthorizationKt.installGoogleCodeProvider(provider: GoogleCodeProvider())
 * ```
 */
interface IosServerAuthCodeProvider {
    /** Calls [completion] with (code, null) on success, (null, null) when cancelled, (null, reason) on failure. */
    fun requestServerAuthCode(
        scopes: List<String>,
        completion: (String?, String?) -> Unit,
    )
}

private var installedProvider: IosServerAuthCodeProvider? = null

/** Called once by the Swift host at launch. */
fun installGoogleCodeProvider(provider: IosServerAuthCodeProvider) {
    installedProvider = provider
}

class IosGoogleAuthorization : GoogleAuthorization {
    override suspend fun authorize(): GoogleAuthorizationResult {
        val provider = installedProvider ?: return GoogleAuthorizationResult.Unavailable
        return suspendCancellableCoroutine { cont ->
            provider.requestServerAuthCode(CLASSROOM_SCOPES) { code, error ->
                val result =
                    when {
                        code != null -> GoogleAuthorizationResult.Success(code)
                        error == null -> GoogleAuthorizationResult.Cancelled
                        else -> GoogleAuthorizationResult.Failed(error)
                    }
                if (cont.isActive) cont.resume(result)
            }
        }
    }
}
