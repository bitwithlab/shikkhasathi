package com.shikkhasathi.features.notifications

import com.shikkhasathi.core.model.NotificationItem
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.features.notifications.presentation.NotificationCategory
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
import com.shikkhasathi.features.testing.FakeNotificationsRepository
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.networkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private fun n(
        i: Int,
        read: Boolean = false,
        type: String = "classroom.due_24h",
    ) = NotificationItem(
        id = "0190f0a3-0000-7000-8000-${i.toString().padStart(12, '0')}",
        type = type,
        title = "CSE-2203 Lab $i জমার সময় কাল",
        body = "আগামীকাল রাত ১১:৫৯-এর মধ্যে জমা দিন",
        link = "/classroom/assignments/cw-$i",
        createdAt = "2026-11-03T0${i % 4}:00:00.000Z",
        read = read,
    )

    private val pages =
        mapOf(
            null to Page(listOf(n(1), n(2, read = true), n(3, type = "classroom.graded")), nextCursor = "c2"),
            // Page 2 repeats item 3 (a new notification shifted the window) — must not duplicate.
            "c2" to Page(listOf(n(3, type = "classroom.graded"), n(4), n(5, read = true)), nextCursor = null),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun pagesWithCursorDedupesAndStopsAtTheEnd() {
        val repo = FakeNotificationsRepository(pages)
        val vm = NotificationsViewModel(repo, FixedClock())
        assertEquals(3, vm.state.value.items.size)
        assertFalse(vm.state.value.endReached)
        assertEquals(
            NotificationCategory.Grade,
            vm.state.value.items[2]
                .category
        )

        vm.loadMore()
        assertEquals(5, vm.state.value.items.size)
        assertEquals(
            vm.state.value.items
                .map { it.id }
                .distinct(),
            vm.state.value.items
                .map { it.id }
        )
        assertTrue(vm.state.value.endReached)

        vm.loadMore()
        assertEquals(listOf(null, "c2"), repo.requestedCursors, "no request after the last page")
        vm.clear()
    }

    @Test
    fun markAllReadIsOptimisticAndRevertsOnFailure() {
        val repo = FakeNotificationsRepository(pages)
        val vm = NotificationsViewModel(repo, FixedClock())
        assertEquals(2, vm.state.value.unreadCount)

        repo.markReadError = networkError
        vm.markAllRead()
        assertEquals(2, vm.state.value.unreadCount, "reverted")
        assertNotNull(vm.state.value.message)

        repo.markReadError = null
        vm.markAllRead()
        assertEquals(0, vm.state.value.unreadCount)
        assertEquals(listOf("all", "all"), repo.markReadCalls)

        vm.markAllRead()
        assertEquals(2, repo.markReadCalls.size, "nothing unread → no request")
        vm.clear()
    }

    @Test
    fun openingARowMarksOnlyThatOneRead() {
        val repo = FakeNotificationsRepository(pages)
        val vm = NotificationsViewModel(repo, FixedClock())
        val first =
            vm.state.value.items
                .first()
        vm.open(first.id)
        assertFalse(
            vm.state.value.items
                .first()
                .unread
        )
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(listOf(first.id), repo.markReadCalls)
        vm.clear()
    }

    @Test
    fun firstPageFailureThenRetry() {
        val repo = FakeNotificationsRepository(pages).apply { listError = networkError }
        val vm = NotificationsViewModel(repo, FixedClock())
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)

        repo.listError = null
        vm.retry()
        assertEquals(3, vm.state.value.items.size)
        vm.clear()
    }

    @Test
    fun emptyCenter() {
        val vm = NotificationsViewModel(FakeNotificationsRepository(emptyMap()), FixedClock())
        assertTrue(vm.state.value.isEmpty)
        assertTrue(vm.state.value.endReached)
        vm.clear()
    }
}
