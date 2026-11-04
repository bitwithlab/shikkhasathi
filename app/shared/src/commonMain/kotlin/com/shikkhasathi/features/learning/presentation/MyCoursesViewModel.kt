package com.shikkhasathi.features.learning.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.dhakaDate
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.MyCourse
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.learning.data.LearningRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.number

data class MyCourseUi(
    val courseId: String,
    val slug: String,
    val title: String,
    val meta: String,
    val coverColor: String?,
    val progressPercent: Int,
    val completedLessons: Int,
    val totalLessons: Int,
    val resumeLessonId: String?,
    val resumeLessonTitle: String?,
    val completed: Boolean,
    val expired: Boolean,
    val expiringSoon: Boolean,
    /** "১২ নভেম্বর" — shown as "মেয়াদ: …" when access is time-limited. */
    val accessUntil: UiText?,
)

data class MyCoursesUiState(
    val filter: MyCoursesFilter = MyCoursesFilter.All,
    val courses: LoadState<List<MyCourseUi>> = LoadState.Loading,
    val refreshing: Boolean = false,
)

/** আমার কোর্স (`GET /me/courses?filter=`): one list per filter chip, cached while the screen lives. */
class MyCoursesViewModel(
    private val repository: LearningRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(MyCoursesUiState())
    val state: StateFlow<MyCoursesUiState> = mutableState.asStateFlow()
    private val cache = mutableMapOf<MyCoursesFilter, List<MyCourseUi>>()
    private var job: Job? = null

    init {
        load(MyCoursesFilter.All)
    }

    fun selectFilter(filter: MyCoursesFilter) {
        if (filter == state.value.filter && state.value.courses is LoadState.Ready) return
        load(filter)
    }

    fun retry() = load(state.value.filter)

    /** Pull-to-refresh and coming back from the player (progress changed). */
    fun refresh() {
        cache.clear()
        load(state.value.filter, silent = true)
    }

    private fun load(
        filter: MyCoursesFilter,
        silent: Boolean = false,
    ) {
        job?.cancel()
        val cached = cache[filter]
        mutableState.update {
            it.copy(
                filter = filter,
                courses = cached?.let { c -> LoadState.Ready(c) } ?: if (silent) it.courses else LoadState.Loading,
                refreshing = silent,
            )
        }
        if (cached != null && !silent) return
        job =
            scope.launch {
                try {
                    val items = repository.myCourses(filter).map { it.toUi() }
                    cache[filter] = items
                    mutableState.update { it.copy(courses = LoadState.Ready(items), refreshing = false) }
                } catch (e: ApiException) {
                    mutableState.update { s ->
                        if (s.courses is LoadState.Ready) {
                            s.copy(
                                refreshing = false
                            )
                        } else {
                            s.copy(courses = LoadState.Failed(e.error.toUiText()), refreshing = false)
                        }
                    }
                }
            }
    }
}

internal fun MyCourse.toUi(): MyCourseUi =
    MyCourseUi(
        courseId = courseId,
        slug = slug,
        title = title,
        meta = listOfNotNull(courseCode, institutionShort, sellerName).joinToString(" · "),
        coverColor = coverColor,
        progressPercent = progressPercent.coerceIn(0, MAX_PERCENT),
        completedLessons = completedLessons,
        totalLessons = totalLessons,
        resumeLessonId = resumeLessonId,
        resumeLessonTitle = resumeLessonTitle,
        completed = completedAt != null,
        expired = expired,
        expiringSoon = expiringSoon,
        accessUntil = accessUntil.toInstantOrNull()?.dhakaDate()?.let { UiText.DayMonth(it.day, it.month.number) },
    )

private const val MAX_PERCENT = 100
