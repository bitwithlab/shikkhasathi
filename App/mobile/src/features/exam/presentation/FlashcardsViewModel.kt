package com.shikkhasathi.features.exam.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.Flashcard
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.exam.data.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashcardsUiState(
    val deck: LoadState<List<Flashcard>> = LoadState.Loading,
    val index: Int = 0,
    val flipped: Boolean = false,
    /** card id → known, for this session. */
    val answers: Map<String, Boolean> = emptyMap(),
    val message: UiText? = null,
) {
    val cards: List<Flashcard> get() = deck.dataOrNull.orEmpty()
    val current: Flashcard? get() = cards.getOrNull(index)
    val finished: Boolean get() = deck is LoadState.Ready && index >= cards.size
    val knownCount: Int get() = answers.count { it.value }
    val notYetCount: Int get() = answers.count { !it.value }
}

/** Flashcards of an exam plan (`GET /exam-plans/:id/flashcards`, unknown first): flip → "জানি" / "এখনো না". */
class FlashcardsViewModel(
    private val planId: String,
    private val repository: ExamRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(FlashcardsUiState())
    val state: StateFlow<FlashcardsUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun flip() = mutableState.update { if (it.current == null) it else it.copy(flipped = !it.flipped) }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Records the answer, moves to the next card; the server write is fire-and-forget with a retry message. */
    fun answer(known: Boolean) {
        val card = state.value.current ?: return
        mutableState.update { it.copy(answers = it.answers + (card.id to known), index = it.index + 1, flipped = false) }
        scope.launch {
            try {
                repository.reviewCard(card.id, known)
            } catch (e: ApiException) {
                mutableState.update { it.copy(message = e.error.toUiText()) }
            }
        }
    }

    /** "আবার দেখুন": only the cards marked "এখনো না" this round (all cards if every one was known). */
    fun restart() {
        val s = state.value
        val again = s.cards.filter { s.answers[it.id] == false }.ifEmpty { s.cards }
        mutableState.update { it.copy(deck = LoadState.Ready(again), index = 0, flipped = false, answers = emptyMap()) }
    }

    private fun load() {
        mutableState.update { it.copy(deck = LoadState.Loading, index = 0, flipped = false, answers = emptyMap()) }
        scope.launch {
            try {
                val cards = repository.flashcards(planId)
                mutableState.update { it.copy(deck = LoadState.Ready(cards)) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(deck = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }
}
