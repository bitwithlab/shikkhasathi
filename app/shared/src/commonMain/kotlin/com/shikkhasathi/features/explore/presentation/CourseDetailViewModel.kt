package com.shikkhasathi.features.explore.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.CourseDetail
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.explore.data.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one primary CTA of the course page (CLAUDE.md §7: one primary action per screen). */
sealed interface CourseCta {
    /** Enrolled: continue where they stopped (or start at the first lesson). */
    data class Continue(val courseId: String, val lessonId: String?, val started: Boolean) : CourseCta

    /** Not enrolled: go to checkout (free courses enroll there instantly). */
    data class Buy(val courseId: String, val slug: String, val price: Int, val free: Boolean) : CourseCta

    /** The teacher looking at their own course. */
    data class Preview(val courseId: String, val lessonId: String?) : CourseCta
}

fun CourseDetail.cta(): CourseCta =
    when {
        viewer.isOwner -> CourseCta.Preview(id, viewer.firstLessonId)
        viewer.enrolled -> CourseCta.Continue(id, viewer.resumeLessonId ?: viewer.firstLessonId, started = viewer.resumeLessonId != null)
        else -> CourseCta.Buy(id, slug, price, free = isFree)
    }

data class CourseDetailUiState(
    val course: LoadState<CourseDetail> = LoadState.Loading,
    val wishlisted: Boolean = false,
    val message: UiText? = null,
) {
    val cta: CourseCta? get() = course.dataOrNull?.cta()

    /** Preview lessons anyone can watch before buying. */
    val previewLessonIds: List<String>
        get() =
            course.dataOrNull
                ?.sections
                ?.flatMap { s -> s.chapters.flatMap { c -> c.lessons.filter { it.isPreview }.map { it.id } } }
                .orEmpty()
}

/** Course details (`GET /courses/:slug`) with an optimistic wishlist toggle. */
class CourseDetailViewModel(
    private val slug: String,
    private val repository: CatalogRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(CourseDetailUiState())
    val state: StateFlow<CourseDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Called when the screen comes back (e.g. after checkout) so "কিনুন" turns into "পড়া শুরু করুন". */
    fun refresh() = load(silent = true)

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun toggleWishlist() {
        val course = state.value.course.dataOrNull ?: return
        val next = !state.value.wishlisted
        mutableState.update { it.copy(wishlisted = next) }
        scope.launch {
            try {
                repository.setWishlisted(course.id, next)
            } catch (e: ApiException) {
                mutableState.update { it.copy(wishlisted = !next, message = e.error.toUiText()) }
            }
        }
    }

    private fun load(silent: Boolean = false) {
        if (!silent || state.value.course !is LoadState.Ready) mutableState.update { it.copy(course = LoadState.Loading) }
        scope.launch {
            try {
                val course = repository.course(slug)
                mutableState.update { it.copy(course = LoadState.Ready(course), wishlisted = course.viewer.wishlisted) }
            } catch (e: ApiException) {
                if (silent && state.value.course is LoadState.Ready) return@launch
                mutableState.update { it.copy(course = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }
}
