package com.shikkhasathi.features.explore

import com.shikkhasathi.core.model.PriceFilter
import com.shikkhasathi.core.model.SearchResponse
import com.shikkhasathi.core.model.Suggestion
import com.shikkhasathi.core.model.SuggestionType
import com.shikkhasathi.features.explore.presentation.ExploreViewModel
import com.shikkhasathi.features.testing.FakeCatalogRepository
import com.shikkhasathi.features.testing.card
import com.shikkhasathi.features.testing.networkError
import com.shikkhasathi.features.testing.uid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun opensWithBestRankedBrowseThenPagesWithoutDuplicates() =
        test {
            val repo = FakeCatalogRepository()
            repo.pages = repo.pages + ("c2" to SearchResponse(items = listOf(card(2), card(3)), total = 3, nextCursor = null))
            val vm = ExploreViewModel(repo)
            runCurrent()
            assertEquals(
                listOf(uid(1), uid(2)),
                vm.state.value.results
                    .map { it.id }
            )
            assertEquals(
                "best",
                repo.searches
                    .single()
                    .first.sort.wire
            )

            vm.loadMore()
            runCurrent()
            assertEquals(
                listOf(uid(1), uid(2), uid(3)),
                vm.state.value.results
                    .map { it.id }
            )
            assertTrue(vm.state.value.endReached)
            vm.loadMore()
            runCurrent()
            assertEquals(2, repo.searches.size, "no request after the last page")
            vm.clear()
        }

    @Test
    fun suggestionsAreDebouncedAndOnlyTheLastQueryIsAsked() =
        test {
            val repo =
                FakeCatalogRepository().apply {
                    suggestions =
                        listOf(Suggestion(SuggestionType.Code, "c1", "CSE-2203", "Data Structures", "/explore?courseCode=CSE-2203"))
                }
            val vm = ExploreViewModel(repo)
            runCurrent()
            vm.onInputChange("D")
            vm.onInputChange("DS")
            advanceTimeBy(100)
            vm.onInputChange("DS ")
            vm.onInputChange("DSA")
            advanceTimeBy(ExploreViewModel.SUGGEST_DEBOUNCE_MS + 1)
            assertEquals(listOf("DSA"), repo.suggestQueries, "one request, for the settled text; 1 char is never asked")
            assertEquals(1, vm.state.value.suggestions.size)

            vm.selectSuggestion(
                vm.state.value.suggestions
                    .first()
            )
            runCurrent()
            val last = repo.searches.last().first
            assertEquals("CSE-2203", last.courseCode)
            assertEquals("", last.query)
            assertTrue(
                vm.state.value.suggestions
                    .isEmpty()
            )
            vm.clear()
        }

    @Test
    fun courseSuggestionOpensItsPageInsteadOfSearching() =
        test {
            val repo = FakeCatalogRepository()
            val vm = ExploreViewModel(repo)
            runCurrent()
            vm.selectSuggestion(Suggestion(SuggestionType.Course, uid(1), "DS Midterm Crash", null, "/c/ds-midterm-1"))
            assertEquals("/c/ds-midterm-1", vm.state.value.openLink)
            vm.linkOpened()
            assertNull(vm.state.value.openLink)
            assertEquals(1, repo.searches.size)
            vm.clear()
        }

    @Test
    fun aSlowOldAnswerNeverReplacesTheNewSearch() =
        test {
            val repo = FakeCatalogRepository()
            val vm = ExploreViewModel(repo)
            runCurrent()
            repo.gate = CompletableDeferred() // the next search hangs
            vm.setPrice(PriceFilter.Free)
            runCurrent()
            repo.pages = mapOf(null to SearchResponse(items = listOf(card(9)), total = 1))
            vm.setExamType("final")
            runCurrent()
            assertEquals(
                listOf(uid(9)),
                vm.state.value.results
                    .map { it.id }
            )
            val filters = vm.state.value.filters
            assertEquals(PriceFilter.Free, filters.price)
            assertEquals("final", filters.examType)
            assertEquals(2, filters.activeCount)

            vm.clearFilters()
            runCurrent()
            assertEquals(0, vm.state.value.filters.activeCount)
            vm.clear()
        }

    @Test
    fun searchFailureShowsErrorThenRetryRecovers() =
        test {
            val repo = FakeCatalogRepository().apply { searchError = networkError }
            val vm = ExploreViewModel(repo, initialQuery = "DBMS")
            runCurrent()
            assertNotNull(vm.state.value.error)
            assertEquals(
                "DBMS",
                repo.searches
                    .single()
                    .first.query
            )
            repo.searchError = null
            vm.retry()
            runCurrent()
            assertNull(vm.state.value.error)
            assertEquals(2, vm.state.value.results.size)
            vm.clear()
        }
}
