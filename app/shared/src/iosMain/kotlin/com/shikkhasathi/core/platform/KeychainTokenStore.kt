@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.shikkhasathi.core.datastore

import com.shikkhasathi.core.network.SsJson
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Session storage in the iOS Keychain: one generic-password item, readable after first unlock,
 * never synced to iCloud or restored to another device (`…ThisDeviceOnly`).
 */
class KeychainTokenStore(
    private val service: String = "com.shikkhasathi.session",
    private val account: String = "default",
) : TokenStore {
    private val lock = Mutex()

    override suspend fun read(): StoredSession? =
        lock.withLock {
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status =
                    withQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) {
                        SecItemCopyMatching(it, result.ptr)
                    }
                if (status != errSecSuccess) return@withLock null
                val data = CFBridgingRelease(result.value) as? NSData ?: return@withLock null
                val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return@withLock null
                runCatching { SsJson.decodeFromString(StoredSession.serializer(), text) }.getOrNull()
            }
        }

    override suspend fun write(session: StoredSession) =
        lock.withLock {
            val text = SsJson.encodeToString(StoredSession.serializer(), session)
            val data = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding) ?: return@withLock
            withQuery { SecItemDelete(it) }
            val dataRef = CFBridgingRetain(data)
            try {
                withQuery(kSecValueData to dataRef, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) {
                    SecItemAdd(it, null)
                }
            } finally {
                CFRelease(dataRef)
            }
            Unit
        }

    override suspend fun clear() =
        lock.withLock {
            withQuery { SecItemDelete(it) }
            Unit
        }

    private inline fun <T> withQuery(
        vararg extra: Pair<CFTypeRef?, CFTypeRef?>,
        block: (CFMutableDictionaryRef?) -> T,
    ): T {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(account)
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            extra.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }
}
