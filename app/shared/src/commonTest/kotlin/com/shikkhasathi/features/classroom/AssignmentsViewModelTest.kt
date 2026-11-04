package com.shikkhasathi.features.classroom

import com.shikkhasathi.core.model.Assignment
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.features.testing.FakeClassroomRepository
import com.shikkhasathi.features.testing.FixedClock
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentsViewModelTest {
    private fun a(
        id: String,
        status: AssignmentStatus,
        dueAt: String?,
        courseId: String = "gc-2203",
        courseName: String = "CSE-2203 Data Structures",
    ) = Assignment(
        id = id,
        courseId = courseId,
        courseName = courseName,
        courseColor = "#4F46E5",
        title = "Task $id",
        workType = "ASSIGNMENT",
        dueAt = dueAt,
        status = status,
    )

    private val list =
        listOf(
            a("late", AssignmentStatus.Missing, "2026-11-01T17:59:00Z"),
            a("soon", AssignmentStatus.Pending, "2026-11-03T17:59:00Z"),
            a("later", AssignmentStatus.Pending, "2026-11-10T17:59:00Z", "gc-2201", "CSE-2201 OOP II"),
            a("nodue", AssignmentStatus.Pending, null),
            a("pastPending", AssignmentStatus.Pending, "2026-11-02T10:00:00Z"),
            a("done", AssignmentStatus.TurnedIn, "2026-11-02T17:59:00Z", "gc-2201", "CSE-2201 OOP II"),
            a("graded", AssignmentStatus.Graded, "2026-10-20T17:59:00Z"),
        )

    private val repo = FakeClassroomRepository().apply { assignmentsValue = list }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultsToUpcomingSoonestFirst() {
        val vm = AssignmentsViewModel(repo, clock = FixedClock())
        assertEquals(listOf("soon", "later"), vm.visibleRows.map { it.id })
        assertEquals(listOf("assignments:all"), repo.calls, "fetched once; filters are local")
        vm.clear()
    }

    @Test
    fun statusFilterAndOrdering() {
        val vm = AssignmentsViewModel(repo, clock = FixedClock())

        vm.setStatus(AssignmentFilter.Pending)
        assertEquals(listOf("pastPending", "soon", "later", "nodue"), vm.visibleRows.map { it.id }, "no due date sorts last")

        vm.setStatus(AssignmentFilter.Missing)
        assertEquals(listOf("late"), vm.visibleRows.map { it.id })

        vm.setStatus(AssignmentFilter.All)
        assertEquals(
            listOf("late", "pastPending", "soon", "later", "nodue", "done", "graded"),
            vm.visibleRows.map { it.id },
            "open work soonest-first, then finished work newest-first",
        )
        assertEquals(listOf("assignments:all"), repo.calls)
        vm.clear()
    }

    @Test
    fun classFilterCombinesWithStatusAndClears() {
        val vm = AssignmentsViewModel(repo, clock = FixedClock())
        assertEquals(
            listOf("CSE-2201 OOP II", "CSE-2203 Data Structures"),
            vm.state.value.classes
                .map { it.name }
        )

        vm.setStatus(AssignmentFilter.All)
        vm.setCourse("gc-2201")
        assertEquals(listOf("later", "done"), vm.visibleRows.map { it.id })
        assertTrue(vm.state.value.isFiltered)

        vm.setStatus(AssignmentFilter.Graded)
        assertTrue(vm.visibleRows.isEmpty(), "empty state, not an error")

        vm.clearFilters()
        assertFalse(vm.state.value.isFiltered)
        assertEquals(list.size, vm.visibleRows.size)
        vm.clear()
    }

    @Test
    fun startsFilteredToAClassWhenOpenedFromItsStream() {
        val vm = AssignmentsViewModel(repo, initialCourseId = "gc-2201", clock = FixedClock())
        assertEquals(listOf("later"), vm.visibleRows.map { it.id })
        vm.clear()
    }
}
