package com.shikkhasathi.features.downloads.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileHandle
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

/**
 * AES-GCM and key wrapping on iOS come from CryptoKit (`AES.GCM.seal/open`) and a Keychain-stored,
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` wrapping key — Swift APIs, so the Swift host implements
 * this protocol and calls [installIosOfflineCrypto] at launch (same pattern as `IosServerAuthCodeProvider`).
 * Until then offline downloads are disabled on iOS.
 */
interface IosOfflineCrypto {
    /** Returns `iv || ciphertext || tag` (CryptoKit `SealedBox.combined`). */
    fun seal(
        key: ByteArray,
        plain: ByteArray,
        aad: ByteArray,
    ): ByteArray

    /** Returns null when authentication fails. */
    fun open(
        key: ByteArray,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray?

    fun wrap(raw: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray?
}

private var installed: IosOfflineCrypto? = null

fun installIosOfflineCrypto(crypto: IosOfflineCrypto) {
    installed = crypto
}

val iosOfflineCryptoAvailable: Boolean get() = installed != null

private fun crypto(): IosOfflineCrypto = installed ?: throw OfflineCryptoException("offline crypto not installed by the iOS host")

class IosAeadCipher : AeadCipher {
    override fun seal(
        key: ByteArray,
        plain: ByteArray,
        aad: ByteArray,
    ): ByteArray = crypto().seal(key, plain, aad)

    override fun open(
        key: ByteArray,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray = crypto().open(key, sealed, aad) ?: throw OfflineCryptoException("authentication failed")

    @OptIn(ExperimentalForeignApi::class)
    override fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        if (size > 0) out.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
        return out
    }
}

class IosKeyWrapper : KeyWrapper {
    override fun wrap(raw: ByteArray): ByteArray = crypto().wrap(raw)

    override fun unwrap(wrapped: ByteArray): ByteArray = crypto().unwrap(wrapped) ?: throw OfflineCryptoException("unwrap failed")
}

/** Containers in Application Support/ShikkhaSathi/offline (excluded from iCloud backup by the host). */
class IosOfflineFileStore(private val dir: Path) : OfflineFileStore {
    override fun sink(name: String): Sink {
        SystemFileSystem.createDirectories(dir)
        return SystemFileSystem.sink(Path(dir, name)).buffered()
    }

    override fun open(name: String): RandomReadable? {
        val path = Path(dir, name)
        val size = SystemFileSystem.metadataOrNull(path)?.size ?: return null
        val handle = NSFileHandle.fileHandleForReadingAtPath(path.toString()) ?: return null
        return FileHandleReadable(handle, size)
    }

    override fun delete(name: String) = SystemFileSystem.delete(Path(dir, name), mustExist = false)

    override fun exists(name: String): Boolean = SystemFileSystem.exists(Path(dir, name))
}

@OptIn(ExperimentalForeignApi::class)
private class FileHandleReadable(
    private val handle: NSFileHandle,
    override val size: Long,
) : RandomReadable {
    override fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position >= size || length == 0) return -1
        handle.seekToFileOffset(position.convert())
        val data = handle.readDataOfLength(length.convert())
        val n = data.length.toInt()
        if (n == 0) return -1
        dest.usePinned { memcpy(it.addressOf(offset), data.bytes, n.convert()) }
        return n
    }

    override fun close() = handle.closeFile()
}
