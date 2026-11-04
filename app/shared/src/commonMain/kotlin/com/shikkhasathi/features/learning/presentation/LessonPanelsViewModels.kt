package com.shikkhasathi.features.learning.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.core.model.Question
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.learning.data.LearningRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LessonNotesUiState(
    val notes: LoadState<List<Note>> = LoadState.Loading,
    val draft: String = "",
    /** Attach the current video time to the note (default on for videos). */
    val attachTime: Boolean = true,
    val saving: Boolean = false,
    val editingId: String? = null,
    val message: UiText? = null,
)

/** Player → নোট tab: timestamped notes of one lesson (`/notes?lessonId=`), plus a quick bookmark. */
@Suppress("TooManyFunctions") // one intent per editor control
class LessonNotesViewModel(
    private val lessonId: String,
    private val repository: LearningRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(LessonNotesUiState())
    val state: StateFlow<LessonNotesUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun onDraft(text: String) = mutableState.update { it.copy(draft = text.take(NOTE_MAX)) }

    fun setAttachTime(on: Boolean) = mutableState.update { it.copy(attachTime = on) }

    fun startEdit(note: Note) = mutableState.update { it.copy(editingId = note.id, draft = note.body) }

    fun cancelEdit() = mutableState.update { it.copy(editingId = null, draft = "") }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Saves the draft: a new note at [positionSec] (when attached), or the edit of [LessonNotesUiState.editingId]. */
    fun save(positionSec: Int?) {
        val s = state.value
        val body = s.draft.trim()
        if (body.isEmpty() || s.saving) return
        mutableState.update { it.copy(saving = true) }
        scope.launch {
            try {
                val editing = s.editingId
                val saved =
                    if (editing != null) {
                        repository.editNote(editing, body)
                    } else {
                        repository.addNote(lessonId, positionSec?.takeIf { s.attachTime }, body)
                    }
                val current =
                    state.value.notes.dataOrNull
                        .orEmpty()
                val list = if (editing != null) current.map { if (it.id == saved.id) saved else it } else listOf(saved) + current
                mutableState.update {
                    it.copy(
                        notes = LoadState.Ready(list.sortedByTimestamp()),
                        draft = "",
                        editingId = null,
                        saving = false
                    )
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(saving = false, message = e.error.toUiText()) }
            }
        }
    }

    fun delete(noteId: String) {
        val before = state.value.notes.dataOrNull ?: return
        mutableState.update { it.copy(notes = LoadState.Ready(before.filterNot { n -> n.id == noteId })) }
        scope.launch {
            try {
                repository.deleteNote(noteId)
            } catch (e: ApiException) {
                mutableState.update { it.copy(notes = LoadState.Ready(before), message = e.error.toUiText()) }
            }
        }
    }

    fun bookmark(positionSec: Int?) {
        scope.launch {
            val message =
                try {
                    repository.addBookmark(lessonId, positionSec)
                    Str.NotesBookmarked.asUiText()
                } catch (e: ApiException) {
                    e.error.toUiText()
                }
            mutableState.update { it.copy(message = message) }
        }
    }

    private fun load() {
        mutableState.update { it.copy(notes = LoadState.Loading) }
        scope.launch {
            try {
                val page = repository.notes(lessonId = lessonId)
                mutableState.update { it.copy(notes = LoadState.Ready(page.items.sortedByTimestamp())) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(notes = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    private fun List<Note>.sortedByTimestamp() = sortedWith(compareBy(nullsLast()) { it.timestampSec })

    companion object {
        const val NOTE_MAX = 5000
    }
}

data class LessonQaUiState(
    val questions: LoadState<List<Question>> = LoadState.Loading,
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val draft: String = "",
    val asking: Boolean = false,
    val draftError: UiText? = null,
    val message: UiText? = null,
)

/** Player → প্রশ্নোত্তর tab (`/lessons/:id/questions`): read the thread, ask a doubt at the current time. */
class LessonQaViewModel(
    private val lessonId: String,
    private val repository: LearningRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(LessonQaUiState())
    val state: StateFlow<LessonQaUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun onDraft(text: String) = mutableState.update { it.copy(draft = text.take(ASK_MAX), draftError = null) }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun loadMore() {
        val s = state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        mutableState.update { it.copy(loadingMore = true) }
        scope.launch {
            try {
                val page = repository.questions(lessonId, cursor)
                val known =
                    state.value.questions.dataOrNull
                        .orEmpty()
                val ids = known.mapTo(HashSet()) { it.id }
                mutableState.update {
                    it.copy(
                        questions =
                            LoadState.Ready(
                                known +
                                    page.items.filter { q ->
                                        q.id !in ids
                                    }
                            ),
                        nextCursor = page.nextCursor,
                        loadingMore = false
                    )
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(loadingMore = false, message = e.error.toUiText()) }
            }
        }
    }

    fun ask(positionSec: Int?) {
        val body = state.value.draft.trim()
        if (state.value.asking) return
        if (body.length < ASK_MIN) {
            mutableState.update { it.copy(draftError = Str.QaTooShort.asUiText()) }
            return
        }
        mutableState.update { it.copy(asking = true) }
        scope.launch {
            try {
                val q = repository.ask(lessonId, body, positionSec)
                val list =
                    listOf(q) +
                        state.value.questions.dataOrNull
                            .orEmpty()
                mutableState.update {
                    it.copy(
                        questions = LoadState.Ready(list),
                        draft = "",
                        asking = false,
                        message = Str.QaAsked.asUiText()
                    )
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(asking = false, message = e.error.toUiText()) }
            }
        }
    }

    private fun load() {
        mutableState.update { it.copy(questions = LoadState.Loading) }
        scope.launch {
            try {
                val page = repository.questions(lessonId)
                mutableState.update { it.copy(questions = LoadState.Ready(page.items), nextCursor = page.nextCursor) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(questions = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    companion object {
        const val ASK_MIN = 5
        const val ASK_MAX = 3000
    }
}
