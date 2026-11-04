package com.shikkhasathi.features.downloads

import com.shikkhasathi.features.downloads.data.ByteArrayReadable
import com.shikkhasathi.features.downloads.data.ChunkedAead
import com.shikkhasathi.features.downloads.data.JceAeadCipher
import com.shikkhasathi.features.downloads.data.JvmOfflineFileStore
import com.shikkhasathi.features.downloads.data.OfflineCryptoException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** The "SSE1" offline container with real AES-GCM (JCE). */
class ChunkedAeadTest {
    private val cipher = JceAeadCipher()
    private val key = cipher.randomBytes(32)
    private val chunk = 64

    private fun encrypt(
        plain: ByteArray,
        k: ByteArray = key,
    ): ByteArray {
        val out = Buffer()
        val n = ChunkedAead(cipher, k, chunk).encrypt(Buffer().apply { write(plain) }, out)
        assertEquals(plain.size.toLong(), n)
        return out.readByteArray()
    }

    @Test
    fun roundTripsEverySizeAroundChunkBoundaries() {
        for (size in listOf(0, 1, 63, 64, 65, 128, 1000)) {
            val plain = Random(size).nextBytes(size)
            val sealed = encrypt(plain)
            assertFalse(sealed.toList().windowed(8).any { w -> plain.size >= 8 && w == plain.take(8) }, "plain text must not appear")
            val reader = ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable(sealed))
            assertEquals(size.toLong(), reader.length, "size $size")
            assertContentEquals(plain, reader.readAll(), "size $size")
        }
    }

    @Test
    fun randomAccessReadsAcrossChunks() {
        val plain = ByteArray(1000) { (it % 251).toByte() }
        val reader = ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable(encrypt(plain)))
        val dest = ByteArray(150)
        assertEquals(150, reader.read(60, dest))
        assertContentEquals(plain.copyOfRange(60, 210), dest)
        assertEquals(40, reader.read(960, dest), "short read at the end")
        assertEquals(-1, reader.read(1000, dest))
    }

    @Test
    fun tamperingTruncationAndWrongKeyAreRejected() {
        val plain = Random(7).nextBytes(300)
        val sealed = encrypt(plain)

        val flipped =
            sealed.copyOf().also {
                it[ChunkedAead.HEADER_BYTES + 40] = (it[ChunkedAead.HEADER_BYTES + 40].toInt() xor 1).toByte()
            }
        assertFailsWith<OfflineCryptoException> { ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable(flipped)).readAll() }

        // Cut exactly after the second stored chunk: every remaining chunk is intact but none is marked last.
        val stored = chunk + 28
        val truncated = sealed.copyOf(ChunkedAead.HEADER_BYTES + 2 * stored)
        assertFailsWith<OfflineCryptoException> { ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable(truncated)) }

        assertFailsWith<OfflineCryptoException> { ChunkedAead(cipher, cipher.randomBytes(32), chunk).reader(ByteArrayReadable(sealed)) }
        assertFailsWith<OfflineCryptoException> {
            ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable("not a container at all, sorry".encodeToByteArray()))
        }
    }

    @Test
    fun chunksCannotBeSwappedBetweenFiles() {
        val a = encrypt(ByteArray(200) { 1 })
        val b = encrypt(ByteArray(200) { 2 })
        val stored = chunk + 28
        val mixed = a.copyOf()
        b.copyInto(mixed, ChunkedAead.HEADER_BYTES, ChunkedAead.HEADER_BYTES, ChunkedAead.HEADER_BYTES + stored) // b's chunk 0 into a
        val reader = ChunkedAead(cipher, key, chunk).reader(ByteArrayReadable(mixed))
        assertFailsWith<OfflineCryptoException> { reader.read(0, ByteArray(10)) }
    }

    @Test
    fun worksThroughRealFiles() {
        val dir = Files.createTempDirectory("ss-offline").toFile()
        val store = JvmOfflineFileStore(dir)
        val plain = Random(3).nextBytes(5_000)
        store.sink("lesson-1.sse").use { ChunkedAead(cipher, key).encrypt(Buffer().apply { write(plain) }, it) }
        val readable = requireNotNull(store.open("lesson-1.sse"))
        ChunkedAead(cipher, key).reader(readable).use { assertContentEquals(plain, it.readAll()) }
        store.delete("lesson-1.sse")
        assertFalse(store.exists("lesson-1.sse"))
        dir.deleteRecursively()
    }
}
