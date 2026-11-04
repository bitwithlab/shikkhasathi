package com.shikkhasathi.features.explore.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.CourseCard
import com.shikkhasathi.core.model.PriceFilter
import com.shikkhasathi.core.model.SearchFilters
import com.shikkhasathi.core.model.SearchInterpretation
import com.shikkhasathi.core.model.SearchSort
import com.shikkhasathi.core.model.Suggestion
import com.shikkhasathi.core.model.SuggestionType
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.explore.data.CatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    /** What is typed in the search box (searched only on submit / suggestion). */
    val input: String = "",
    val filters: SearchFilters = SearchFilters(),
    val suggestions: List<Suggestion> = emptyList(),
    val results: List<CourseCard> = emptyList(),
    val total: Int = 0,
    /** "DS" → "Data Structures" hints from the server. */
    val interpreted: List<SearchInterpretation> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** One-shot: a suggestion pointed at a page (`/c/…`, `/t/…`); the screen opens it and calls [ExploreViewModel.linkOpened]. */
    val openLink: String? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && results.isEmpty()
    val canLoadMore: Boolean get() = !(loading || loadingMore || endReached) && error == null
}

/**
 * কোর্স খুঁজুন: smart search (`GET /search`) with autocomplete (`GET /search/suggest`, debounced),
 * filters (exam type, free/paid, sort) and cursor paging. A new search cancels the one in flight, so a slow
 * answer for an old query never replaces the current results.
 */
@Suppress("TooManyFunctions") // one intent per search control
class ExploreViewModel(
    private val repository: CatalogRepository,
    initialQuery: String? = null,
    private val pageSize: Int = CatalogRepository.DEFAULT_PAGE,
) : SsViewModel() {
    private val mutableState =
        MutableStateFlow(ExploreUiState(input = initialQuery.orEmpty(), filters = SearchFilters(query = initialQuery.orEmpty())))
    val state: StateFlow<ExploreUiState> = mutableState.asStateFlow()

    private var cursor: String? = null
    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    init {
        search()
    }

    fun onInputChange(text: String) {
        mutableState.update { it.copy(input = text) }
        suggestJob?.cancel()
        val q = text.trim()
        if (q.length < MIN_SUGGEST_CHARS) {
            mutableState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob =
            scope.launch {
                delay(SUGGEST_DEBOUNCE_MS)
                // Autocomplete is a convenience: a failure just shows no suggestions.
                val items = runCatching { repository.suggest(q) }.getOrDefault(emptyList())
                if (state.value.input.trim() == q) mutableState.update { it.copy(suggestions = items.take(MAX_SUGGESTIONS)) }
            }
    }

    fun submit() {
        suggestJob?.cancel()
        mutableState.update { it.copy(suggestions = emptyList(), filters = it.filters.copy(query = it.input.trim())) }
        search()
    }

    /** A course or teacher suggestion opens its page; subject / code / university ones search for it. */
    fun selectSuggestion(suggestion: Suggestion) {
        suggestJob?.cancel()
        when (suggestion.type) {
            SuggestionType.Course, SuggestionType.Teacher ->
                mutableState.update { it.copy(suggestions = emptyList(), openLink = suggestion.href) }
            SuggestionType.Code ->
                applyFilters(
                    input = suggestion.label
                ) { it.copy(query = "", courseCode = suggestion.label, subjectId = null, institutionId = null) }
            SuggestionType.Subject ->
                applyFilters(input = suggestion.label) { it.copy(query = "", subjectId = suggestion.id, courseCode = null) }
            SuggestionType.Institution ->
                applyFilters(input = suggestion.label) { it.copy(query = "", institutionId = suggestion.id) }
        }
    }

    fun linkOpened() = mutableState.update { it.copy(openLink = null) }

    fun setExamType(examType: String?) = applyFilters { it.copy(examType = examType) }

    fun setPrice(price: PriceFilter?) = applyFilters { it.copy(price = price) }

    fun setSort(sort: SearchSort) = applyFilters { it.copy(sort = sort) }

    fun clearFilters() = applyFilters(input = "") { SearchFilters() }

    fun retry() = search()

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun loadMore() {
        if (!state.value.canLoadMore) return
        val filters = state.value.filters
        mutableState.update { it.copy(loadingMore = true) }
        searchJob =
            scope.launch {
                try {
                    val page = repository.search(filters, cursor = cursor, limit = pageSize)
                    val known = state.value.results.mapTo(HashSet()) { it.id }
                    cursor = page.nextCursor
                    mutableState.update { s ->
                        s.copy(
                            results = s.results + page.items.filter { it.id !in known },
                            loadingMore = false,
                            endReached = page.nextCursor == null,
                        )
                    }
                } catch (e: ApiException) {
                    mutableState.update { it.copy(loadingMore = false, message = e.error.toUiText()) }
                }
            }
    }

    private fun applyFilters(
        input: String? = null,
        change: (SearchFilters) -> SearchFilters,
    ) {
        mutableState.update { s -> s.copy(filters = change(s.filters), input = input ?: s.input, suggestions = emptyList()) }
        search()
    }

    private fun search() {
        searchJob?.cancel()
        val filters = state.value.filters
        mutableState.update { it.copy(loading = true, error = null, endReached = false) }
        searchJob =
            scope.launch {
                try {
                    val page = repository.search(filters, cursor = null, limit = pageSize)
                    cursor = page.nextCursor
                    mutableState.update {
                        it.copy(
                            results = page.items,
                            total = page.total,
                            interpreted = page.interpreted,
                            loading = false,
                            endReached = page.nextCursor == null,
                        )
                    }
                } catch (e: ApiException) {
                    mutableState.update { it.copy(loading = false, error = e.error.toUiText(), results = emptyList()) }
                }
            }
    }

    companion object {
        const val SUGGEST_DEBOUNCE_MS = 250L
        const val MIN_SUGGEST_CHARS = 2
        const val MAX_SUGGESTIONS = 8
    }
}
