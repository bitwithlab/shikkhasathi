package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.DhakaZone
import com.shikkhasathi.core.common.time.dhakaDate
import com.shikkhasathi.core.common.time.dhakaDateTime
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.core.model.CalendarEvent
import com.shikkhasathi.core.model.CalendarEventKind
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.time.Clock

/** First day of a calendar month (day = 1). */
data class MonthKey(
    val year: Int,
    val month: Int,
) {
    val firstDay: LocalDate get() = LocalDate(year, month, 1)

    fun next(): MonthKey = firstDay.plus(DatePeriod(months = 1)).let { MonthKey(it.year, it.month.number) }

    fun previous(): MonthKey = firstDay.minus(DatePeriod(months = 1)).let { MonthKey(it.year, it.month.number) }

    companion object {
        fun of(date: LocalDate) = MonthKey(date.year, date.month.number)
    }
}

data class CalendarDayUi(
    val date: LocalDate,
    val dayOfMonth: Int,
    val deadlines: Int,
    val exams: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** TalkBack: "১২ নভেম্বর: ২টি ডেডলাইন, ০টি পরীক্ষা". */
    val description: UiText,
)

data class CalendarEventRowUi(
    val id: String,
    val kind: CalendarEventKind,
    val title: String,
    val courseName: String?,
    val color: String,
    val time: UiText,
    val status: AssignmentStatus?,
    val link: String,
)

data class CalendarUiState(
    val month: MonthKey,
    val title: UiText,
    /** Weekday header (Sunday first), short names. */
    val weekdays: List<UiText>,
    /** Rows of 7 cells; null cells pad the first/last week. */
    val weeks: List<List<CalendarDayUi?>> = emptyList(),
    val selected: LocalDate,
    val selectedTitle: UiText,
    val selectedEvents: List<CalendarEventRowUi> = emptyList(),
    val load: LoadState<Unit> = LoadState.Loading,
)

/**
 * Month grid of deadlines and exams (`GET /classroom/calendar?from&to`), bucketed by **Asia/Dhaka** day.
 * Weeks start on Sunday: Bangladesh's weekend (Friday + Saturday) then sits together at the end of each row.
 */
class CalendarViewModel(
    private val repository: ClassroomRepository,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val today: LocalDate = clock.now().dhakaDate()
    private val byMonth = mutableMapOf<MonthKey, List<CalendarEvent>>()

    private val mutableState =
        MutableStateFlow(
            CalendarUiState(
                month = MonthKey.of(today),
                title = monthTitle(MonthKey.of(today)),
                weekdays = WEEK_ORDER.map { UiText.Weekday(it.isoDayNumber, short = true) },
                selected = today,
                selectedTitle = dayTitle(today),
            ),
        )
    val state: StateFlow<CalendarUiState> = mutableState.asStateFlow()

    init {
        showMonth(MonthKey.of(today), select = today)
    }

    fun nextMonth() =
        state.value.month
            .next()
            .let { showMonth(it, select = it.firstDay) }

    fun previousMonth() =
        state.value.month
            .previous()
            .let { showMonth(it, select = it.firstDay) }

    fun goToToday() = showMonth(MonthKey.of(today), select = today)

    fun select(date: LocalDate) {
        if (MonthKey.of(date) != state.value.month) return showMonth(MonthKey.of(date), select = date)
        mutableState.update { it.copy(selected = date) }
        render()
    }

    fun retry() = showMonth(state.value.month, select = state.value.selected, force = true)

    private fun showMonth(
        month: MonthKey,
        select: LocalDate,
        force: Boolean = false,
    ) {
        mutableState.update { it.copy(month = month, title = monthTitle(month), selected = select) }
        if (!force && byMonth.containsKey(month)) return render()
        mutableState.update { it.copy(load = LoadState.Loading, weeks = grid(month, emptyMap(), select), selectedEvents = emptyList()) }
        scope.launch {
            val from = month.firstDay.atStartOfDayIn(DhakaZone)
            val to = month.next().firstDay.atStartOfDayIn(DhakaZone)
            runCatching { repository.calendar(from, to) }
                .onSuccess { events ->
                    byMonth[month] = events
                    if (state.value.month == month) render()
                }.onFailure { e ->
                    if (state.value.month == month) mutableState.update { it.copy(load = LoadState.Failed(e.toUiTextOrGeneric())) }
                }
        }
    }

    private fun render() {
        val s = state.value
        val events = byMonth[s.month].orEmpty()
        val byDay = bucketByDhakaDay(events)
        mutableState.update {
            it.copy(
                weeks = grid(s.month, byDay, s.selected),
                selectedTitle = dayTitle(s.selected),
                selectedEvents = byDay[s.selected].orEmpty().map { e -> e.toRowUi() },
                load = LoadState.Ready(Unit),
            )
        }
    }

    private fun grid(
        month: MonthKey,
        byDay: Map<LocalDate, List<CalendarEvent>>,
        selected: LocalDate,
    ): List<List<CalendarDayUi?>> = monthGrid(month, today, selected, byDay)

    companion object {
        val WEEK_ORDER: List<DayOfWeek> =
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )

        /** Events grouped by the Dhaka calendar day of `at`, each day sorted by time. Unparseable times are dropped. */
        fun bucketByDhakaDay(events: List<CalendarEvent>): Map<LocalDate, List<CalendarEvent>> =
            events
                .mapNotNull { e -> e.at.toInstantOrNull()?.let { it to e } }
                .sortedBy { it.first }
                .groupBy({ it.first.dhakaDate() }, { it.second })

        fun monthGrid(
            month: MonthKey,
            today: LocalDate,
            selected: LocalDate,
            byDay: Map<LocalDate, List<CalendarEvent>>,
        ): List<List<CalendarDayUi?>> {
            val first = month.firstDay
            val days = generateSequence(first) { it.plus(DatePeriod(days = 1)) }.takeWhile { it.month == first.month }.toList()
            val lead = WEEK_ORDER.indexOf(first.dayOfWeek)
            val cells: List<CalendarDayUi?> =
                List(lead) { null } +
                    days.map { d ->
                        val events = byDay[d].orEmpty()
                        val deadlines = events.count { it.kind == CalendarEventKind.Assignment }
                        val exams = events.count { it.kind == CalendarEventKind.Exam }
                        CalendarDayUi(
                            date = d,
                            dayOfMonth = d.day,
                            deadlines = deadlines,
                            exams = exams,
                            isToday = d == today,
                            isSelected = d == selected,
                            description =
                                Str.CalendarDayA11y.asUiText(
                                    "date" to UiText.DayMonth(d.day, d.month.number),
                                    "summary" to Str.WeekSummary.asUiText("d" to deadlines, "e" to exams),
                                ),
                        )
                    }
            val padded = cells + List((DAYS - cells.size % DAYS) % DAYS) { null }
            return padded.chunked(DAYS)
        }

        private const val DAYS = 7
    }
}

private fun monthTitle(m: MonthKey): UiText = UiText.MonthYear(m.month, m.year)

private fun dayTitle(d: LocalDate): UiText = UiText.DayMonth(d.day, d.month.number)

private fun CalendarEvent.toRowUi(): CalendarEventRowUi {
    val local = at.toInstantOrNull()?.dhakaDateTime()
    return CalendarEventRowUi(
        id = id,
        kind = kind,
        title = title,
        courseName = courseName,
        color = color,
        time = local?.let { UiText.ClockTime(it.hour, it.minute) } ?: UiText.Raw(""),
        status = status,
        link = link,
    )
}
