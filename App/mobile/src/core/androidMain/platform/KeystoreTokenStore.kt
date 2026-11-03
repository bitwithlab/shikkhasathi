package com.shikkhasathi.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.shikkhasathi.core.network.SsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Session storage backed by the Android Keystore.
 *
 * The session JSON is encrypted with AES-256-GCM using a non-exportable key that lives in the
 * `AndroidKeyStore` provider (hardware-backed where the device supports it); only `iv + ciphertext`
 * is written to a private SharedPreferences file, which is excluded from backups in the app manifest.
 * This is what `androidx.security:security-crypto` did, without the deprecated library.
 * If the key is invalidated (e.g. device restored to another phone) the session is dropped and the user signs in again.
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var cache: StoredSession? = null
    private var loaded = false

    override suspend fun read(): StoredSession? =
        lock.withLock {
            if (!loaded) {
                cache = withContext(Dispatchers.IO) { load() }
                loaded = true
            }
            cache
        }

    override suspend fun write(session: StoredSession) =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val plain = SsJson.encodeToString(StoredSession.serializer(), session).encodeToByteArray()
                val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
                val blob = cipher.iv + cipher.doFinal(plain)
                prefs.edit().putString(KEY_SESSION, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
            }
            cache = session
            loaded = true
        }

    override suspend fun clear() =
        lock.withLock {
            withContext(Dispatchers.IO) { prefs.edit().remove(KEY_SESSION).apply() }
            cache = null
            loaded = true
        }

    private fun load(): StoredSession? {
        val encoded = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            val plain = cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            SsJson.decodeFromString(StoredSession.serializer(), plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            prefs.edit().remove(KEY_SESSION).apply()
            null
        } catch (e: IllegalArgumentException) {
            prefs.edit().remove(KEY_SESSION).apply()
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val PREFS = "ss_secure_session"
        const val KEY_SESSION = "session"
        const val KEY_ALIAS = "ss_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
