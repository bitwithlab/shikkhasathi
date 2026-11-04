package com.shikkhasathi.features.home

import com.shikkhasathi.features.home.data.CachedDashboard
import com.shikkhasathi.features.home.data.FileDashboardCache
import com.shikkhasathi.features.testing.FIXTURE_NOW
import com.shikkhasathi.features.testing.dashboardFixture
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileDashboardCacheTest {
    @Test
    fun writesReadsAndClears() =
        runTest {
            val dir = Files.createTempDirectory("ss-cache").toFile()
            val file = Path(dir.absolutePath, "cache", "dashboard.json")
            val cache = FileDashboardCache(file)
            assertNull(cache.read())

            val entry = CachedDashboard(dashboardFixture(), FIXTURE_NOW)
            cache.write(entry)
            assertEquals(entry, FileDashboardCache(file).read(), "survives a new instance (process restart)")

            java.io.File(file.toString()).writeText("{ not json")
            assertNull(cache.read(), "corrupt file reads as no cache")

            cache.write(entry)
            cache.clear()
            assertNull(cache.read())
            dir.deleteRecursively()
        }
}
