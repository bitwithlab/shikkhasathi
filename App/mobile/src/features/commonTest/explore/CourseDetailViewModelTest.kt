package com.shikkhasathi.features.explore

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.features.explore.presentation.CourseCta
import com.shikkhasathi.features.explore.presentation.CourseDetailViewModel
import com.shikkhasathi.features.explore.presentation.cta
import com.shikkhasathi.features.testing.FakeCatalogRepository
import com.shikkhasathi.features.testing.courseDetail
import com.shikkhasathi.features.testing.networkError
import com.shikkhasathi.features.testing.uid
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun oneCtaPerViewer() {
        assertEquals(CourseCta.Buy(uid(100), "ds-midterm-cse-2203", 349, free = false), courseDetail().cta())
        assertEquals(true, (courseDetail(price = 0).cta() as CourseCta.Buy).free)
        assertEquals(CourseCta.Continue(uid(100), uid(201), started = false), courseDetail(enrolled = true).cta())
        assertEquals(CourseCta.Continue(uid(100), uid(205), started = true), courseDetail(enrolled = true, resume = uid(205)).cta())
        assertIs<CourseCta.Preview>(courseDetail(enrolled = true, owner = true).cta())
    }

    @Test
    fun wishlistIsOptimisticAndRollsBack() {
        val repo = FakeCatalogRepository()
        val vm = CourseDetailViewModel("ds-midterm-cse-2203", repo)
        assertIs<LoadState.Ready<*>>(vm.state.value.course)
        vm.toggleWishlist()
        assertTrue(vm.state.value.wishlisted)
        repo.wishlistError = networkError
        vm.toggleWishlist()
        assertTrue(vm.state.value.wishlisted, "removal failed → still saved")
        assertNotNull(vm.state.value.message)
        assertEquals(listOf(uid(100) to true, uid(100) to false), repo.wishlistCalls)
        vm.clear()
    }

    @Test
    fun refreshAfterCheckoutTurnsBuyIntoContinue() {
        val repo = FakeCatalogRepository()
        val vm = CourseDetailViewModel("ds-midterm-cse-2203", repo)
        assertIs<CourseCta.Buy>(vm.state.value.cta)
        repo.detail = courseDetail(enrolled = true)
        vm.refresh()
        assertIs<CourseCta.Continue>(vm.state.value.cta)
        assertFalse(vm.state.value.course is LoadState.Loading)
        vm.clear()
    }
}
