package com.shikkhasathi.features.classroom

import com.shikkhasathi.core.model.CalendarEvent
import com.shikkhasathi.core.model.CalendarEventKind
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.features.classroom.presentation.MonthKey
import com.shikkhasathi.features.testing.FakeClassroomRepository
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.apiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private fun ev(
        id: String,
        at: String,
        kind: CalendarEventKind = CalendarEventKind.Assignment,
    ) = CalendarEvent(
        id = id,
        kind = kind,
        title = id,
        at = at,
        courseName = "CSE-2203",
        color = "#4F46E5",
        link = "/classroom/assignments/$id"
    )

    private val events =
        listOf(
            // 23:59 Dhaka on 3 Nov is 17:59Z on 3 Nov — same day.
            ev("lab4", "2026-11-03T17:59:00Z"),
            // 00:30 Dhaka on 4 Nov is 18:30Z on 3 Nov — must land on the 4th, not the UTC date.
            ev("quiz", "2026-11-03T18:30:00Z"),
            ev("mid", "2026-11-15T03:00:00Z", CalendarEventKind.Exam),
            // 05:59 Dhaka on 1 Nov is 23:59Z on 31 Oct.
            ev("early", "2026-10-31T23:59:00Z"),
        )

    private val repo = FakeClassroomRepository().apply { calendarValue = events }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun bucketsByDhakaDayNotUtc() {
        val byDay = CalendarViewModel.bucketByDhakaDay(events)
        assertEquals(listOf("lab4"), byDay[LocalDate(2026, 11, 3)]?.map { it.id })
        assertEquals(listOf("quiz"), byDay[LocalDate(2026, 11, 4)]?.map { it.id })
        assertEquals(listOf("early"), byDay[LocalDate(2026, 11, 1)]?.map { it.id })
        assertNull(byDay[LocalDate(2026, 10, 31)])
    }

    @Test
    fun monthGridStartsSundayWithDots() {
        val vm = CalendarViewModel(repo, FixedClock())
        val s = vm.state.value

        assertEquals(MonthKey(2026, 11), s.month)
        assertEquals("নভেম্বর ২০২৬", s.title.resolve())
        assertEquals("রবি", s.weekdays.first().resolve())
        // 1 Nov 2026 is a Sunday → no leading blanks; 30 days → 5 rows, last row padded.
        assertEquals(5, s.weeks.size)
        assertTrue(s.weeks.all { it.size == 7 })
        assertEquals(1, s.weeks[0][0]?.dayOfMonth)
        assertNull(s.weeks[4][2])

        val nov3 = s.weeks.flatten().first { it?.dayOfMonth == 3 }!!
        assertEquals(1, nov3.deadlines)
        assertTrue(nov3.isToday && nov3.isSelected)
        assertEquals("৩ নভেম্বর: ১টি ডেডলাইন, ০টি পরীক্ষা", nov3.description.resolve())
        assertEquals(
            1,
            s.weeks
                .flatten()
                .first { it?.dayOfMonth == 15 }!!
                .exams
        )

        // Fetch range is the Dhaka month: 1 Nov 00:00 +06 → 1 Dec 00:00 +06.
        assertEquals(Instant.parse("2026-10-31T18:00:00Z") to Instant.parse("2026-11-30T18:00:00Z"), repo.calendarRanges.single())
        assertEquals(listOf("lab4"), s.selectedEvents.map { it.id })
        assertEquals(
            "রাত ১১:৫৯",
            s.selectedEvents
                .single()
                .time
                .resolve()
        )
        vm.clear()
    }

    @Test
    fun selectingADayListsItsEventsAndMonthsPage() {
        val vm = CalendarViewModel(repo, FixedClock())
        vm.select(LocalDate(2026, 11, 4))
        assertEquals(
            listOf("quiz"),
            vm.state.value.selectedEvents
                .map { it.id }
        )
        assertEquals(
            "রাত ১২:৩০",
            vm.state.value.selectedEvents
                .single()
                .time
                .resolve()
        )

        vm.nextMonth()
        assertEquals(MonthKey(2026, 12), vm.state.value.month)
        // December 2026 starts on a Tuesday → two leading blanks.
        assertNull(vm.state.value.weeks[0][1])
        assertEquals(
            1,
            vm.state.value.weeks[0][2]
                ?.dayOfMonth
        )
        assertEquals(2, repo.calendarRanges.size)

        vm.previousMonth()
        assertEquals(2, repo.calendarRanges.size, "November is cached")
        vm.clear()
    }

    @Test
    fun failureShowsRetryableError() {
        repo.calendarError = apiError(ApiError.INTERNAL, status = 500)
        val vm = CalendarViewModel(repo, FixedClock())
        assertIs<com.shikkhasathi.core.common.LoadState.Failed>(vm.state.value.load)
        repo.calendarError = null
        vm.retry()
        assertIs<com.shikkhasathi.core.common.LoadState.Ready<*>>(vm.state.value.load)
        vm.clear()
    }
}
