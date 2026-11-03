package com.shikkhasathi.features.learning.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.Bookmark
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.learning.data.LearningRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotesTab { Notes, Bookmarks }

data class NotesUiState(
    val tab: NotesTab = NotesTab.Notes,
    val query: String = "",
    val notes: LoadState<List<Note>> = LoadState.Loading,
    val bookmarks: LoadState<List<Bookmark>> = LoadState.Loading,
    val message: UiText? = null,
)

/** নোট ও বুকমার্ক (drawer): every note across courses (searchable) and every bookmark; tap → that lesson. */
class NotesViewModel(
    private val repository: LearningRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        loadNotes()
    }

    fun selectTab(tab: NotesTab) {
        mutableState.update { it.copy(tab = tab) }
        if (tab == NotesTab.Bookmarks && state.value.bookmarks !is LoadState.Ready) loadBookmarks()
    }

    fun onQuery(text: String) {
        mutableState.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob =
            scope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                fetchNotes()
            }
    }

    fun retry() = if (state.value.tab == NotesTab.Notes) loadNotes() else loadBookmarks()

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun deleteNote(id: String) {
        val before = state.value.notes.dataOrNull ?: return
        mutableState.update { it.copy(notes = LoadState.Ready(before.filterNot { n -> n.id == id })) }
        scope.launch {
            try {
                repository.deleteNote(id)
            } catch (e: ApiException) {
                mutableState.update { it.copy(notes = LoadState.Ready(before), message = e.error.toUiText()) }
            }
        }
    }

    fun deleteBookmark(id: String) {
        val before = state.value.bookmarks.dataOrNull ?: return
        mutableState.update { it.copy(bookmarks = LoadState.Ready(before.filterNot { b -> b.id == id })) }
        scope.launch {
            try {
                repository.deleteBookmark(id)
            } catch (e: ApiException) {
                mutableState.update { it.copy(bookmarks = LoadState.Ready(before), message = e.error.toUiText()) }
            }
        }
    }

    private fun loadNotes() {
        searchJob?.cancel()
        mutableState.update { it.copy(notes = LoadState.Loading) }
        searchJob = scope.launch { fetchNotes() }
    }

    private suspend fun fetchNotes() {
        try {
            val page = repository.notes(query = state.value.query)
            mutableState.update { it.copy(notes = LoadState.Ready(page.items)) }
        } catch (e: ApiException) {
            mutableState.update { it.copy(notes = LoadState.Failed(e.error.toUiText())) }
        }
    }

    private fun loadBookmarks() {
        mutableState.update { it.copy(bookmarks = LoadState.Loading) }
        scope.launch {
            try {
                val items = repository.bookmarks().items
                mutableState.update { it.copy(bookmarks = LoadState.Ready(items)) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(bookmarks = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
