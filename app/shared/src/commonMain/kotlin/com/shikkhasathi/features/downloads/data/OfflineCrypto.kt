package com.shikkhasathi.features.downloads.data

import com.shikkhasathi.core.datastore.KeyValueStore
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-256-GCM, provided per platform (JCE on Android/desktop, CryptoKit through the Swift host on iOS).
 * `seal` returns `iv(12) || ciphertext || tag(16)` with a fresh random IV; `open` throws [OfflineCryptoException]
 * when the tag does not verify (tampered, truncated, wrong key or wrong AAD).
 */
interface AeadCipher {
    fun seal(
        key: ByteArray,
        plain: ByteArray,
        aad: ByteArray,
    ): ByteArray

    fun open(
        key: ByteArray,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray

    /** Cryptographically secure random bytes (SecureRandom / SecRandomCopyBytes). */
    fun randomBytes(size: Int): ByteArray

    companion object {
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val OVERHEAD = IV_BYTES + TAG_BYTES
        const val TAG_BITS = TAG_BYTES * Byte.SIZE_BITS
        const val KEY_BITS = KEY_BYTES * Byte.SIZE_BITS
    }
}

/**
 * Wraps the per-account content key with a device-bound key that never leaves secure hardware
 * (Android Keystore AES-GCM key, non-exportable; iOS Keychain `ThisDeviceOnly`). A copied file or a restored
 * backup on another phone therefore cannot be decrypted (downloads are tied to account **and** device).
 */
interface KeyWrapper {
    fun wrap(raw: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray
}

class OfflineCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * One random content key per signed-in account, stored only in wrapped form. Deleting it (sign-out, account switch)
 * crypto-shreds every download of that account even if a file deletion fails.
 */
@OptIn(ExperimentalEncodingApi::class)
class ContentKeyStore(
    private val prefs: KeyValueStore,
    private val wrapper: KeyWrapper,
    private val cipher: AeadCipher,
) {
    fun keyFor(userId: String): ByteArray {
        val name = prefName(userId)
        prefs.getString(name)?.let { stored ->
            runCatching { return wrapper.unwrap(Base64.decode(stored)) }
            // Key invalidated (e.g. lock screen removed on some devices): start over, old files become unreadable.
        }
        val fresh = cipher.randomBytes(AeadCipher.KEY_BYTES)
        prefs.putString(name, Base64.encode(wrapper.wrap(fresh)))
        return fresh
    }

    fun forget(userId: String) = prefs.remove(prefName(userId))

    private fun prefName(userId: String) = "dl_key_$userId"
}

/** Random-access reads of a stored file (the encrypted container). */
interface RandomReadable : AutoCloseable {
    val size: Long

    /** Reads up to [length] bytes at [position] into [dest]; returns the count, or -1 at the end. */
    fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

/**
 * Streaming, seekable encryption for offline files ("SSE1" container):
 *
 * ```
 * header  = "SSE1" | version(1) | chunkSize(u32 BE) | fileId(16 random bytes)          (25 bytes)
 * chunk i = iv(12) | AES-GCM(plain[i·chunkSize ..], aad = fileId | i(u64 BE) | last(1)) | tag(16)
 * ```
 *
 * Every chunk but the last holds exactly `chunkSize` plain bytes, so byte `p` lives in chunk `p / chunkSize` —
 * a player or PDF renderer can seek without decrypting the whole file. The AAD binds each chunk to its file and
 * position (no swapping chunks between files or reordering) and marks the final chunk, so a truncated file fails
 * to open instead of silently playing a shorter video. Per-chunk random IVs keep Android Keystore-style
 * "randomized encryption required" keys usable too.
 */
class ChunkedAead(
    private val cipher: AeadCipher,
    private val key: ByteArray,
    val chunkSize: Int = DEFAULT_CHUNK,
) {
    init {
        require(key.size == AeadCipher.KEY_BYTES) { "AES-256 key expected" }
        require(chunkSize in MIN_CHUNK..MAX_CHUNK) { "chunk size out of range" }
    }

    /** Encrypts [input] to [output]; returns the number of plain bytes. */
    fun encrypt(
        input: Source,
        output: Sink,
        onProgress: (Long) -> Unit = {},
    ): Long {
        val fileId = cipher.randomBytes(FILE_ID_BYTES)
        output.write(header(fileId))
        var index = 0L
        var total = 0L
        var current = readFull(input)
        while (true) {
            val next = if (current.size == chunkSize) readFull(input) else ByteArray(0)
            val last = next.isEmpty()
            output.write(cipher.seal(key, current, aad(fileId, index, last)))
            total += current.size
            onProgress(total)
            if (last) break
            current = next
            index++
        }
        output.flush()
        return total
    }

    /** Opens a container for random-access decryption; validates the header and the final chunk. */
    fun reader(file: RandomReadable): EncryptedReader = EncryptedReader(file, cipher, key)

    private fun readFull(input: Source): ByteArray {
        val buffer = ByteArray(chunkSize)
        var filled = 0
        while (filled < chunkSize) {
            val n = input.readAtMostTo(buffer, filled, chunkSize)
            if (n <= 0) break
            filled += n
        }
        return if (filled == chunkSize) buffer else buffer.copyOf(filled)
    }

    private fun header(fileId: ByteArray): ByteArray =
        MAGIC + byteArrayOf(VERSION) + chunkSize.toBytes() + fileId

    companion object {
        const val DEFAULT_CHUNK = 64 * 1024
        const val MIN_CHUNK = 16
        const val MAX_CHUNK = 4 * 1024 * 1024
        const val FILE_ID_BYTES = 16
        const val VERSION: Byte = 1
        val MAGIC: ByteArray = "SSE1".encodeToByteArray()
        val HEADER_BYTES: Int = MAGIC.size + 1 + Int.SIZE_BYTES + FILE_ID_BYTES

        internal fun aad(
            fileId: ByteArray,
            index: Long,
            last: Boolean,
        ): ByteArray = fileId + index.toBytes() + byteArrayOf(if (last) 1 else 0)
    }
}

/** Random-access plaintext view of an "SSE1" container. Decrypted chunks are cached one at a time. */
class EncryptedReader internal constructor(
    private val file: RandomReadable,
    private val cipher: AeadCipher,
    private val key: ByteArray,
) : AutoCloseable {
    private val fileId: ByteArray
    val chunkSize: Int
    private val chunkCount: Long

    /** Plain length in bytes. */
    val length: Long

    private var cachedIndex = -1L
    private var cached = ByteArray(0)

    init {
        val header = ByteArray(ChunkedAead.HEADER_BYTES)
        if (readExactly(0, header) != header.size || !header.copyOfRange(0, ChunkedAead.MAGIC.size).contentEquals(ChunkedAead.MAGIC)) {
            throw OfflineCryptoException("not an offline container")
        }
        if (header[ChunkedAead.MAGIC.size] != ChunkedAead.VERSION) throw OfflineCryptoException("unsupported container version")
        chunkSize = header.readInt(ChunkedAead.MAGIC.size + 1)
        if (chunkSize !in ChunkedAead.MIN_CHUNK..ChunkedAead.MAX_CHUNK) throw OfflineCryptoException("bad chunk size")
        fileId = header.copyOfRange(ChunkedAead.HEADER_BYTES - ChunkedAead.FILE_ID_BYTES, ChunkedAead.HEADER_BYTES)
        val body = file.size - ChunkedAead.HEADER_BYTES
        val stored = chunkSize + AeadCipher.OVERHEAD
        if (body < AeadCipher.OVERHEAD) throw OfflineCryptoException("truncated container")
        chunkCount = (body + stored - 1) / stored
        val lastStored = body - (chunkCount - 1) * stored
        if (lastStored < AeadCipher.OVERHEAD) throw OfflineCryptoException("truncated container")
        length = (chunkCount - 1) * chunkSize + (lastStored - AeadCipher.OVERHEAD)
        // Opening the final chunk proves the file is complete (its AAD carries last = 1).
        chunk(chunkCount - 1)
    }

    /** Reads plain bytes at [position]; returns the count, or -1 at the end. */
    fun read(
        position: Long,
        dest: ByteArray,
        offset: Int = 0,
        size: Int = dest.size - offset,
    ): Int {
        if (position >= length) return -1
        var done = 0
        var pos = position
        while (done < size && pos < length) {
            val index = pos / chunkSize
            val plain = chunk(index)
            val inChunk = (pos - index * chunkSize).toInt()
            val n = minOf(size - done, plain.size - inChunk)
            plain.copyInto(dest, offset + done, inChunk, inChunk + n)
            done += n
            pos += n
        }
        return done
    }

    /** Whole plaintext (small files such as PDFs). */
    fun readAll(): ByteArray {
        require(length <= Int.MAX_VALUE) { "file too large to read at once" }
        val out = ByteArray(length.toInt())
        read(0, out)
        return out
    }

    override fun close() = file.close()

    private fun chunk(index: Long): ByteArray {
        if (index == cachedIndex) return cached
        val stored = chunkSize + AeadCipher.OVERHEAD
        val start = ChunkedAead.HEADER_BYTES + index * stored
        val size = minOf(stored.toLong(), file.size - start).toInt()
        val sealed = ByteArray(size)
        if (readExactly(start, sealed) != size) throw OfflineCryptoException("short read")
        val plain = openChunk(sealed, index)
        cachedIndex = index
        cached = plain
        return plain
    }

    @Suppress("TooGenericExceptionCaught") // any platform crypto failure means "does not verify"
    private fun openChunk(
        sealed: ByteArray,
        index: Long,
    ): ByteArray =
        try {
            cipher.open(key, sealed, ChunkedAead.aad(fileId, index, last = index == chunkCount - 1))
        } catch (e: OfflineCryptoException) {
            throw e
        } catch (e: Exception) {
            throw OfflineCryptoException("chunk $index failed to verify", e)
        }

    private fun readExactly(
        position: Long,
        dest: ByteArray,
    ): Int {
        var done = 0
        while (done < dest.size) {
            val n = file.read(position + done, dest, done, dest.size - done)
            if (n <= 0) break
            done += n
        }
        return done
    }
}

private const val BYTE_MASK = 0xFF

/** Big-endian bytes. */
private fun Int.toBytes(): ByteArray = ByteArray(Int.SIZE_BYTES) { i -> (this ushr (Byte.SIZE_BITS * (Int.SIZE_BYTES - 1 - i))).toByte() }

private fun Long.toBytes(): ByteArray =
    ByteArray(Long.SIZE_BYTES) { i -> (this ushr (Byte.SIZE_BITS * (Long.SIZE_BYTES - 1 - i))).toByte() }

private fun ByteArray.readInt(at: Int): Int =
    (0 until Int.SIZE_BYTES).fold(0) { acc, i -> (acc shl Byte.SIZE_BITS) or (this[at + i].toInt() and BYTE_MASK) }
