package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.Assignment
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class ClassOption(
    val id: String,
    val name: String,
    val color: String,
)

data class AssignmentsUiState(
    val status: AssignmentFilter = AssignmentFilter.Upcoming,
    /** Null = all classes. */
    val courseId: String? = null,
    /** Every class that has at least one assignment, for the class filter chips. */
    val classes: List<ClassOption> = emptyList(),
    /** Rows after both filters, ordered for scanning (see [AssignmentsViewModel.filter]). */
    val rows: LoadState<List<AssignmentRowUi>> = LoadState.Loading,
) {
    val isFiltered: Boolean get() = status != AssignmentFilter.All || courseId != null
}

/** Label of each status filter chip, in the order they appear. */
val ASSIGNMENT_FILTERS: List<Pair<AssignmentFilter, Str>> =
    listOf(
        AssignmentFilter.Upcoming to Str.AssignmentsFilterUpcoming,
        AssignmentFilter.All to Str.AssignmentsFilterAll,
        AssignmentFilter.Pending to Str.StatusPending,
        AssignmentFilter.Missing to Str.StatusMissing,
        AssignmentFilter.TurnedIn to Str.StatusTurnedIn,
        AssignmentFilter.Graded to Str.StatusGraded,
    )

/**
 * All assignments across classes. The list is fetched once (`status=all`) and filtered on the device, so
 * switching chips is instant and works offline; pull-to-refresh / retry fetches again.
 */
class AssignmentsViewModel(
    private val repository: ClassroomRepository,
    initialCourseId: String? = null,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(AssignmentsUiState(courseId = initialCourseId))
    val state: StateFlow<AssignmentsUiState> = mutableState.asStateFlow()

    private var all: List<Assignment>? = null

    init {
        load()
    }

    fun retry() = load()

    fun setStatus(filter: AssignmentFilter) {
        mutableState.update { it.copy(status = filter) }
        render()
    }

    fun setCourse(courseId: String?) {
        mutableState.update { it.copy(courseId = courseId) }
        render()
    }

    fun clearFilters() {
        mutableState.update { it.copy(status = AssignmentFilter.All, courseId = null) }
        render()
    }

    private fun load() {
        mutableState.update { it.copy(rows = LoadState.Loading) }
        scope.launch {
            runCatching { repository.assignments(AssignmentFilter.All) }
                .onSuccess { list ->
                    all = list
                    mutableState.update { s ->
                        val classes = list.distinctBy { it.courseId }.map { ClassOption(it.courseId, it.courseName, it.courseColor) }
                        s.copy(classes = classes.sortedBy { it.name })
                    }
                    render()
                }.onFailure { e -> mutableState.update { it.copy(rows = LoadState.Failed(e.toUiTextOrGeneric())) } }
        }
    }

    private fun render() {
        val list = all ?: return
        val s = state.value
        val now = clock.now()
        mutableState.update { it.copy(rows = LoadState.Ready(filter(list, s.status, s.courseId, now).map { a -> a.toRowUi(now) })) }
    }

    /** Current rows, for tests and the "N items" summary. */
    val visibleRows: List<AssignmentRowUi> get() =
        state.value.rows.dataOrNull
            .orEmpty()

    companion object {
        /**
         * Pure filter + order. Upcoming = pending with a due date still ahead. Open work (pending/missing) is
         * ordered soonest-due first; finished work (turned in/graded) most-recent-due first; no due date last.
         */
        fun filter(
            list: List<Assignment>,
            status: AssignmentFilter,
            courseId: String?,
            now: Instant,
        ): List<Assignment> {
            val matching =
                list.filter { a ->
                    val due = a.dueAt.toInstantOrNull()
                    val statusOk =
                        when (status) {
                            AssignmentFilter.All -> true
                            AssignmentFilter.Upcoming -> a.status == AssignmentStatus.Pending && due != null && due >= now
                            AssignmentFilter.Pending -> a.status == AssignmentStatus.Pending
                            AssignmentFilter.TurnedIn -> a.status == AssignmentStatus.TurnedIn
                            AssignmentFilter.Graded -> a.status == AssignmentStatus.Graded
                            AssignmentFilter.Missing -> a.status == AssignmentStatus.Missing
                        }
                    statusOk && (courseId == null || a.courseId == courseId)
                }
            val (open, done) = matching.partition { it.status == AssignmentStatus.Pending || it.status == AssignmentStatus.Missing }
            val byDue = compareBy<Assignment, Instant?>(nullsLast<Instant>()) { it.dueAt.toInstantOrNull() }
            val byDueDesc = compareByDescending<Assignment, Instant?>(nullsFirst<Instant>()) { it.dueAt.toInstantOrNull() }
            return open.sortedWith(byDue) + done.sortedWith(byDueDesc.thenBy { it.title })
        }
    }
}
