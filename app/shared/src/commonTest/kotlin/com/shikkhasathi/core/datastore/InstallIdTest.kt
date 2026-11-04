package com.shikkhasathi.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstallIdTest {
    @Test
    fun createdOnceAndKept() {
        val prefs = InMemoryKeyValueStore()
        var made = 0
        val first = InstallId(prefs) { "id-${++made}-abcdefgh" }.value
        assertEquals("id-1-abcdefgh", first)
        assertEquals(first, InstallId(prefs) { error("must not regenerate") }.value)
        assertEquals(1, made)
    }

    @Test
    fun randomIdsAreUuidsAndDiffer() {
        val a = randomId()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(a), a)
        assertNotEquals(a, randomId())
        assertTrue(
            InstallId(InMemoryKeyValueStore(mapOf(InstallId.KEY to "short"))).value.length >= InstallId.MIN_LENGTH,
            "too-short stored id replaced"
        )
    }
}
