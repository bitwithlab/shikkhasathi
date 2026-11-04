package com.shikkhasathi.features.learning

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.model.MyCourse
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.features.learning.presentation.LessonNotesViewModel
import com.shikkhasathi.features.learning.presentation.LessonQaViewModel
import com.shikkhasathi.features.learning.presentation.MyCoursesViewModel
import com.shikkhasathi.features.learning.presentation.NotesViewModel
import com.shikkhasathi.features.testing.FakeLearningRepository
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun myCourse(
        n: Int,
        accessUntil: String? = null,
    ) = MyCourse(
        courseId = uid(n),
        slug = "c-$n",
        title = "Course $n",
        courseCode = "CSE-2203",
        sellerName = "তানভীর",
        progressPercent = 140,
        accessUntil = accessUntil
    )

    @Test
    fun myCoursesCachesEachFilterAndRefreshRefetches() =
        test {
            val repo =
                FakeLearningRepository().apply {
                    courses =
                        mapOf(
                            MyCoursesFilter.All to listOf(myCourse(1, "2026-11-30T18:00:00Z"), myCourse(2)),
                            MyCoursesFilter.Completed to listOf(myCourse(2))
                        )
                }
            val vm = MyCoursesViewModel(repo)
            runCurrent()
            val all = (vm.state.value.courses as LoadState.Ready).data
            assertEquals(2, all.size)
            assertEquals(100, all[0].progressPercent, "clamped")
            assertEquals(
                com.shikkhasathi.core.i18n.UiText
                    .DayMonth(1, 12),
                all[0].accessUntil,
                "Dhaka date: 30 Nov 18:00Z = 1 Dec"
            )
            assertEquals("CSE-2203 · তানভীর", all[0].meta)

            vm.selectFilter(MyCoursesFilter.Completed)
            runCurrent()
            vm.selectFilter(MyCoursesFilter.All)
            runCurrent()
            assertEquals(listOf(MyCoursesFilter.All, MyCoursesFilter.Completed), repo.courseRequests, "cached on the way back")
            vm.refresh()
            runCurrent()
            assertEquals(3, repo.courseRequests.size)
            vm.clear()
        }

    @Test
    fun lessonNotesSaveWithTimestampEditAndDeleteRollback() =
        test {
            val repo = FakeLearningRepository()
            val vm = LessonNotesViewModel(uid(202), repo)
            runCurrent()
            vm.onDraft("  BST insert: বাম দিকে ছোট  ")
            vm.save(positionSec = 95)
            runCurrent()
            vm.setAttachTime(false)
            vm.onDraft("সারাংশ")
            vm.save(positionSec = 300)
            runCurrent()
            assertEquals(listOf("list:", "add:95:BST insert: বাম দিকে ছোট", "add:null:সারাংশ"), repo.noteCalls)
            val notes = vm.state.value.notes.dataOrNull!!
            assertEquals(listOf(95, null), notes.map { it.timestampSec }, "time-ordered, untimed last")

            vm.startEdit(notes[0])
            vm.onDraft("BST insert (edited)")
            vm.save(positionSec = 10)
            runCurrent()
            assertEquals(
                "BST insert (edited)",
                vm.state.value.notes.dataOrNull!![0]
                    .body
            )

            repo.noteError = networkError
            vm.delete(notes[1].id)
            runCurrent()
            assertEquals(
                2,
                vm.state.value.notes.dataOrNull!!
                    .size,
                "rolled back"
            )
            assertNotNull(vm.state.value.message)

            vm.bookmark(120)
            runCurrent()
            assertTrue("bookmark:120" in repo.noteCalls)
            vm.clear()
        }

    @Test
    fun qaValidatesThenAsksAtTheCurrentTime() =
        test {
            val repo = FakeLearningRepository()
            val vm = LessonQaViewModel(uid(202), repo)
            runCurrent()
            vm.onDraft("কেন?")
            vm.ask(positionSec = 61)
            assertNotNull(vm.state.value.draftError)
            assertTrue(repo.asks.isEmpty())

            vm.onDraft("AVL rotation-এ height কীভাবে আপডেট হয়?")
            vm.ask(positionSec = 61)
            runCurrent()
            assertEquals<List<Pair<String, Int?>>>(listOf("AVL rotation-এ height কীভাবে আপডেট হয়?" to 61), repo.asks)
            assertEquals(
                1,
                vm.state.value.questions.dataOrNull!!
                    .size
            )
            assertEquals("", vm.state.value.draft)
            vm.clear()
        }

    @Test
    fun notesScreenSearchIsDebounced() =
        test {
            val repo =
                FakeLearningRepository().apply {
                    notes =
                        mutableListOf(Note(uid(1), uid(100), uid(201), body = "heap sort"), Note(uid(2), uid(100), uid(202), body = "BST"))
                }
            val vm = NotesViewModel(repo)
            runCurrent()
            assertEquals(
                2,
                vm.state.value.notes.dataOrNull!!
                    .size
            )
            vm.onQuery("h")
            vm.onQuery("he")
            vm.onQuery("heap")
            advanceTimeBy(NotesViewModel.SEARCH_DEBOUNCE_MS + 1)
            assertEquals(listOf("list:", "list:heap"), repo.noteCalls)
            assertEquals(
                listOf("heap sort"),
                vm.state.value.notes.dataOrNull!!
                    .map { it.body }
            )
            vm.clear()
        }
}
