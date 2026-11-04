package com.shikkhasathi.features.exam

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.model.AttemptAnswer
import com.shikkhasathi.features.exam.presentation.ExamModeViewModel
import com.shikkhasathi.features.exam.presentation.FlashcardsViewModel
import com.shikkhasathi.features.exam.presentation.MockAttemptViewModel
import com.shikkhasathi.features.exam.presentation.dhakaIso
import com.shikkhasathi.features.testing.FakeExamRepository
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.SchedulerClock
import com.shikkhasathi.features.testing.apiError
import com.shikkhasathi.features.testing.attempt
import com.shikkhasathi.features.testing.networkError
import com.shikkhasathi.features.testing.uid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun examModeOpensTheNearestActivePlanOrTheLinkedOne() =
        test {
            val repo = FakeExamRepository()
            val vm = ExamModeViewModel(repo, clock = FixedClock())
            runCurrent()
            assertEquals(uid(402), vm.state.value.selectedPlanId, "past plan skipped")
            assertEquals(2, vm.state.value.switchable.size)

            val linked = ExamModeViewModel(repo, requestedPlanId = uid(403), clock = FixedClock())
            runCurrent()
            assertEquals(uid(403), (linked.state.value.detail as LoadState.Ready).data.id)

            val empty = ExamModeViewModel(FakeExamRepository().apply { plansValue = emptyList() }, clock = FixedClock())
            runCurrent()
            assertNull(empty.state.value.detail, "no plan → invite to create one")
            listOf(vm, linked, empty).forEach { it.clear() }
        }

    @Test
    fun createPlanValidatesThenOpensTheNewPlan() =
        test {
            val repo = FakeExamRepository()
            val vm = ExamModeViewModel(repo, clock = FixedClock())
            runCurrent()
            vm.openForm()
            vm.submitForm()
            assertNotNull(
                vm.state.value.form
                    ?.error,
                "subject required"
            )
            vm.updateForm {
                it.copy(
                    subjectName = "Database Management",
                    courseCode = "CSE-2207",
                    examType = "final",
                    date = LocalDate(2026, 11, 2)
                )
            }
            vm.submitForm()
            assertNotNull(
                vm.state.value.form
                    ?.error,
                "yesterday is in the past (Dhaka)"
            )
            vm.updateForm { it.copy(date = LocalDate(2026, 12, 14), hour = 9, minute = 30) }
            vm.submitForm()
            runCurrent()
            val input = repo.created.single()
            assertEquals("2026-12-14T09:30:00+06:00", input.examDate)
            assertEquals("CSE-2207", input.courseCode)
            assertNull(vm.state.value.form)
            assertEquals(uid(409), vm.state.value.selectedPlanId)
            assertEquals("2026-01-05T07:05:00+06:00", dhakaIso(LocalDate(2026, 1, 5), 7, 5))
            vm.clear()
        }

    @Test
    fun flashcardsRecordAnswersAndRestartWithTheUnknownOnes() =
        test {
            val repo = FakeExamRepository()
            val vm = FlashcardsViewModel(uid(402), repo)
            runCurrent()
            vm.flip()
            assertTrue(vm.state.value.flipped)
            vm.answer(known = true)
            vm.answer(known = false)
            vm.answer(known = false)
            runCurrent()
            assertTrue(vm.state.value.finished)
            assertEquals(1, vm.state.value.knownCount)
            assertEquals(2, vm.state.value.notYetCount)
            assertEquals(3, repo.reviews.size)
            vm.restart()
            assertEquals(
                listOf(uid(482), uid(483)),
                vm.state.value.cards
                    .map { it.id }
            )
            vm.clear()
        }

    @Test
    fun mockTimerUsesTheServerClockAndAutoSubmits() =
        test {
            // Device clock is 2 minutes behind the server: 30 min test started at server 04:00, device says 03:58.
            val repo =
                FakeExamRepository().apply {
                    attemptValue =
                        attempt(deadline = "2026-11-03T04:30:00Z", serverNow = "2026-11-03T04:00:00Z")
                }
            val clock = SchedulerClock(testScheduler, start = kotlin.time.Instant.parse("2026-11-03T03:58:00Z"))
            val vm = MockAttemptViewModel(uid(440), null, repo, clock)
            runCurrent()
            assertEquals(1800, vm.state.value.remainingSec)
            advanceTimeBy(60_001)
            assertEquals(1740, vm.state.value.remainingSec)
            advanceTimeBy(1_740_000)
            runCurrent()
            assertEquals(1, repo.submits)
            assertEquals(uid(450), vm.state.value.finishedAttemptId)
            assertNotNull(vm.state.value.message, "told it was submitted for them")
            vm.clear()
        }

    @Test
    fun autosaveIsOrderedLastWriteWinsAndFailuresAreResentOnSubmit() =
        test {
            val repo = FakeExamRepository()
            val vm = MockAttemptViewModel(uid(440), null, repo, SchedulerClock(testScheduler))
            runCurrent()
            val q1 = uid(461)
            vm.choose(q1, uid(481))
            vm.choose(q1, uid(482))
            runCurrent()
            assertEquals(listOf(uid(482), uid(482)), repo.saves.map { it.optionId }, "both sends carry the latest choice")

            repo.saveError = networkError
            vm.toggleFlag(uid(462))
            runCurrent()
            assertEquals(setOf(uid(462)), vm.state.value.failed)
            assertEquals(1, vm.state.value.answeredCount)
            assertEquals(2, vm.state.value.unansweredCount)

            repo.saveError = null
            vm.requestSubmit()
            assertTrue(vm.state.value.confirmSubmit)
            vm.submit()
            runCurrent()
            assertEquals(true, repo.saves.last().flagged)
            assertEquals(uid(462), repo.saves.last().questionId)
            assertEquals(uid(450), vm.state.value.finishedAttemptId)
            vm.choose(q1, uid(483))
            assertEquals(
                uid(482),
                vm.state.value.answers[q1]
                    ?.optionId,
                "locked after submit"
            )
            vm.clear()
        }

    @Test
    fun resumeRestoresAnswersAndAConflictMeansAlreadySubmitted() =
        test {
            val repo =
                FakeExamRepository().apply {
                    attemptValue =
                        attempt(answers = listOf(AttemptAnswer(uid(461), uid(481)), AttemptAnswer(uid(462), null, flagged = true)))
                }
            val vm = MockAttemptViewModel(uid(440), uid(450), repo, SchedulerClock(testScheduler))
            runCurrent()
            assertTrue(vm.state.value.resumed)
            assertEquals(1, vm.state.value.index, "opens at the first unanswered question")
            assertEquals(
                true,
                vm.state.value.answers[uid(462)]
                    ?.flagged
            )

            repo.saveError = apiError("CONFLICT")
            repo.submitError = apiError("CONFLICT")
            vm.choose(uid(463), uid(501))
            runCurrent()
            assertEquals(uid(450), vm.state.value.finishedAttemptId)
            vm.clear()
        }

    @Test
    fun alreadySubmittedAttemptGoesStraightToTheResult() =
        test {
            val repo = FakeExamRepository().apply { attemptValue = attempt(submitted = true) }
            val vm = MockAttemptViewModel(uid(440), uid(450), repo, SchedulerClock(testScheduler))
            runCurrent()
            assertEquals(uid(450), vm.state.value.finishedAttemptId)
            assertEquals(0, repo.submits)
            assertEquals(
                3,
                vm.state.value.attempt.dataOrNull
                    ?.questions
                    ?.size
            )
            vm.clear()
        }
}
