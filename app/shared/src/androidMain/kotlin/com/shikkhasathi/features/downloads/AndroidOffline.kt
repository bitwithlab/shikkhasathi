package com.shikkhasathi.features.downloads.data

import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.File
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM through the JCE (same code as the desktop one). */
class JceAeadCipher : AeadCipher {
    private val random = SecureRandom()

    override fun seal(
        key: ByteArray,
        plain: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val iv = randomBytes(AeadCipher.IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AeadCipher.TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plain)
    }

    override fun open(
        key: ByteArray,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        if (sealed.size < AeadCipher.OVERHEAD) throw OfflineCryptoException("sealed chunk too short")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(AeadCipher.TAG_BITS, sealed, 0, AeadCipher.IV_BYTES)
            )
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, AeadCipher.IV_BYTES, sealed.size - AeadCipher.IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw OfflineCryptoException("authentication failed", e)
        }
    }

    override fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Containers as files in [dir] (Android: `noBackupFilesDir/offline`). */
class JvmOfflineFileStore(private val dir: File) : OfflineFileStore {
    override fun sink(name: String): Sink {
        dir.mkdirs()
        return File(dir, safe(name)).outputStream().asSink().buffered()
    }

    override fun open(name: String): RandomReadable? {
        val file = File(dir, safe(name))
        return if (file.isFile) RandomAccessReadable(RandomAccessFile(file, "r")) else null
    }

    override fun delete(name: String) {
        File(dir, safe(name)).delete()
    }

    override fun exists(name: String): Boolean = File(dir, safe(name)).isFile

    private fun safe(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

class RandomAccessReadable(private val file: RandomAccessFile) : RandomReadable {
    override val size: Long get() = file.length()

    override fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        file.seek(position)
        return file.read(dest, offset, length)
    }

    override fun close() = file.close()
}

/**
 * Wraps content keys with a non-exportable AES-256-GCM key in the Android Keystore (alias `ss_offline_wrap`),
 * so downloads are unreadable on any other device or after the app's keys are wiped.
 */
class KeystoreKeyWrapper : KeyWrapper {
    private val keyStore =
        java.security.KeyStore
            .getInstance(PROVIDER)
            .apply { load(null) }

    override fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(raw)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(AeadCipher.TAG_BITS, wrapped, 0, AeadCipher.IV_BYTES))
        return cipher.doFinal(wrapped, AeadCipher.IV_BYTES, wrapped.size - AeadCipher.IV_BYTES)
    }

    private fun key(): javax.crypto.SecretKey {
        (keyStore.getEntry(ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            android.security.keystore.KeyGenParameterSpec
                .Builder(
                    ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AeadCipher.KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "ss_offline_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
