package com.shikkhasathi.features.home

import com.shikkhasathi.core.testing.sampleMe
import com.shikkhasathi.features.auth.FakeAuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.home.presentation.HomeViewModel
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun greetingUsesFirstNameAndAcademicChip() {
        val repo = FakeAuthRepository()
        val vm = HomeViewModel(repo)
        assertNull(vm.state.value.firstName)

        repo.sessionFlow.value = SessionState.SignedIn(sampleMe("রাফি আহমেদ"))

        assertEquals("রাফি", vm.state.value.firstName)
        assertEquals("MU · CSE · Semester 5", vm.state.value.academicLine)
        assertFalse(vm.state.value.classroomConnected)
        vm.clear()
    }
}
