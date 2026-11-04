package com.shikkhasathi.features.exam.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.Attempt
import com.shikkhasathi.core.model.AttemptAnswer
import com.shikkhasathi.core.model.AttemptResult
import com.shikkhasathi.core.model.MockSummary
import com.shikkhasathi.core.model.SaveAnswerBody
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.exam.data.ExamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// ---------------------------------------------------------------- list

data class MockTestsUiState(
    val tests: LoadState<List<MockSummary>> = LoadState.Loading,
)

/** Mock Test list across my courses (`GET /mock-tests`). */
class MockTestsViewModel(
    private val repository: ExamRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(MockTestsUiState())
    val state: StateFlow<MockTestsUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun refresh() = load(silent = true)

    private fun load(silent: Boolean = false) {
        if (!silent || state.value.tests !is LoadState.Ready) mutableState.value = MockTestsUiState()
        scope.launch {
            try {
                val items = repository.mockTests()
                mutableState.value = MockTestsUiState(LoadState.Ready(items))
            } catch (e: ApiException) {
                if (!silent ||
                    state.value.tests !is LoadState.Ready
                ) {
                    mutableState.value = MockTestsUiState(LoadState.Failed(e.error.toUiText()))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- timed attempt

data class AnswerUi(
    val optionId: String? = null,
    val flagged: Boolean = false,
)

data class MockAttemptUiState(
    val attempt: LoadState<Attempt> = LoadState.Loading,
    val index: Int = 0,
    val answers: Map<String, AnswerUi> = emptyMap(),
    /** Whole seconds left on the server's clock. */
    val remainingSec: Int = 0,
    val pendingSaves: Int = 0,
    /** Questions whose last save failed; resent before submit. */
    val failed: Set<String> = emptySet(),
    val submitting: Boolean = false,
    val confirmSubmit: Boolean = false,
    /** Resumed an attempt that already had answers ("আগের উত্তরগুলো ফিরিয়ে আনা হয়েছে"). */
    val resumed: Boolean = false,
    /** Set once submitted (by the student or the timer): the screen navigates to the result. */
    val finishedAttemptId: String? = null,
    val message: UiText? = null,
) {
    val questionCount: Int get() = attempt.dataOrNull?.questions?.size ?: 0
    val answeredCount: Int get() = answers.values.count { it.optionId != null }
    val unansweredCount: Int get() = questionCount - answeredCount
    val lowTime: Boolean get() = remainingSec in 1..LOW_TIME_SEC

    companion object {
        const val LOW_TIME_SEC = 60
    }
}

/**
 * A timed mock test (spec §9, ADR-023):
 * - `POST /mock-tests/:id/attempts` starts or **resumes** the open attempt (questions/options keep their order);
 * - the countdown runs on the **server clock**: skew = `serverNow` − device time at the answer, so a phone with
 *   a wrong clock still shows the right time left;
 * - every choice / flag is autosaved (`PUT /mock-attempts/:id/answers`) in order, last write wins;
 *   failed saves are retried before submit; a 409 means the server already closed the attempt → go to the result;
 * - at 0 the attempt is submitted automatically.
 */
@Suppress("TooManyFunctions") // one intent per runner control
class MockAttemptViewModel(
    private val testId: String,
    private val attemptId: String?,
    private val repository: ExamRepository,
    private val clock: Clock = Clock.System,
    private val tickMs: Long = TICK_MS,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(MockAttemptUiState())
    val state: StateFlow<MockAttemptUiState> = mutableState.asStateFlow()

    private var skew: Duration = Duration.ZERO
    private var deadline: Instant? = null
    private var timerJob: Job? = null
    private val saveLock = Mutex()

    init {
        load()
    }

    fun retry() = load()

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun goTo(index: Int) = mutableState.update { it.copy(index = index.coerceIn(0, (it.questionCount - 1).coerceAtLeast(0))) }

    fun next() = goTo(state.value.index + 1)

    fun previous() = goTo(state.value.index - 1)

    fun choose(
        questionId: String,
        optionId: String,
    ) {
        if (!canAnswer()) return
        mutableState.update { s ->
            val current = s.answers[questionId] ?: AnswerUi()
            // Tapping the chosen option again clears it.
            val next = current.copy(optionId = if (current.optionId == optionId) null else optionId)
            s.copy(answers = s.answers + (questionId to next))
        }
        save(questionId)
    }

    fun toggleFlag(questionId: String) {
        if (!canAnswer()) return
        mutableState.update { s ->
            val current = s.answers[questionId] ?: AnswerUi()
            s.copy(answers = s.answers + (questionId to current.copy(flagged = !current.flagged)))
        }
        save(questionId)
    }

    /** "জমা দিন" → confirm dialog (shows how many are unanswered). */
    fun requestSubmit() = mutableState.update { it.copy(confirmSubmit = true) }

    fun dismissSubmit() = mutableState.update { it.copy(confirmSubmit = false) }

    fun submit() = submitInternal()

    private fun canAnswer(): Boolean =
        state.value.attempt is LoadState.Ready && !state.value.submitting && state.value.finishedAttemptId == null

    private fun load() {
        mutableState.update { it.copy(attempt = LoadState.Loading) }
        scope.launch {
            try {
                val a = if (attemptId != null) repository.attempt(attemptId) else repository.startAttempt(testId)
                if (a.submitted) {
                    mutableState.update { it.copy(attempt = LoadState.Ready(a), finishedAttemptId = a.id) }
                    return@launch
                }
                val receivedAt = clock.now()
                skew = (a.serverNow.toInstantOrNull() ?: receivedAt) - receivedAt
                deadline = a.deadlineAt.toInstantOrNull()
                val answers = a.answers.toUiMap()
                val firstOpen = a.questions.indexOfFirst { q -> answers[q.id]?.optionId == null }.coerceAtLeast(0)
                mutableState.update {
                    it.copy(
                        attempt = LoadState.Ready(a),
                        answers = answers,
                        index = firstOpen,
                        resumed = a.answers.any { x -> x.optionId != null },
                        remainingSec = remaining(),
                    )
                }
                startTimer()
            } catch (e: ApiException) {
                mutableState.update { it.copy(attempt = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    private fun remaining(): Int {
        val end = deadline ?: return 0
        val serverNow = clock.now() + skew
        return ((end - serverNow).inWholeMilliseconds.coerceAtLeast(0) + MS - 1).div(MS).toInt()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob =
            scope.launch {
                while (isActive) {
                    val left = remaining()
                    mutableState.update { it.copy(remainingSec = left) }
                    if (left <= 0) {
                        submitInternal(auto = true)
                        break
                    }
                    delay(tickMs.milliseconds)
                }
            }
    }

    private fun save(questionId: String) {
        val id =
            state.value.attempt.dataOrNull
                ?.id ?: return
        mutableState.update { it.copy(pendingSaves = it.pendingSaves + 1) }
        scope.launch {
            try {
                saveLock.withLock {
                    // Read the value at send time: a later tap on the same question supersedes an earlier one.
                    val v = state.value.answers[questionId] ?: AnswerUi()
                    repository.saveAnswer(id, SaveAnswerBody(questionId = questionId, optionId = v.optionId, flagged = v.flagged))
                }
                mutableState.update { it.copy(failed = it.failed - questionId) }
            } catch (e: ApiException) {
                if (e.error.code == ApiError.CONFLICT) {
                    submitInternal(auto = true)
                } else {
                    mutableState.update { it.copy(failed = it.failed + questionId) }
                }
            } finally {
                mutableState.update { it.copy(pendingSaves = (it.pendingSaves - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun submitInternal(auto: Boolean = false) {
        val a = state.value.attempt.dataOrNull ?: return
        if (state.value.submitting || state.value.finishedAttemptId != null) return
        mutableState.update { it.copy(submitting = true, confirmSubmit = false) }
        timerJob?.cancel()
        scope.launch {
            try {
                // Flush answers whose save failed (ignore errors: submit scores what the server has).
                state.value.failed.forEach { qid ->
                    val v = state.value.answers[qid] ?: AnswerUi()
                    runCatching { saveLock.withLock { repository.saveAnswer(a.id, SaveAnswerBody(qid, v.optionId, v.flagged)) } }
                }
                repository.submit(a.id)
                mutableState.update {
                    it.copy(submitting = false, finishedAttemptId = a.id, message = if (auto) Str.MockAutoSubmitted.asUiText() else null)
                }
            } catch (e: ApiException) {
                if (e.error.code == ApiError.CONFLICT) {
                    // Already submitted (deadline passed on the server, or another device): the result exists.
                    mutableState.update { it.copy(submitting = false, finishedAttemptId = a.id) }
                } else {
                    mutableState.update { it.copy(submitting = false, message = e.error.toUiText()) }
                    if (!auto) startTimer()
                }
            }
        }
    }

    companion object {
        const val TICK_MS = 1_000L
        private const val MS = 1_000L
    }
}

/** Answers from the server (resume) as the UI map. */
internal fun List<AttemptAnswer>.toUiMap(): Map<String, AnswerUi> = associate { it.questionId to AnswerUi(it.optionId, it.flagged) }

// ---------------------------------------------------------------- result

data class MockResultUiState(
    val result: LoadState<AttemptResult> = LoadState.Loading,
    /** Review filter: every question, or only wrong + skipped ones. */
    val onlyMistakes: Boolean = false,
)

/** Score, chapter breakdown and answer review (`GET /mock-attempts/:id/result`). */
class MockResultViewModel(
    private val attemptId: String,
    private val repository: ExamRepository,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(MockResultUiState())
    val state: StateFlow<MockResultUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun setOnlyMistakes(on: Boolean) = mutableState.update { it.copy(onlyMistakes = on) }

    private fun load() {
        mutableState.update { it.copy(result = LoadState.Loading) }
        scope.launch {
            try {
                val r = repository.result(attemptId)
                mutableState.update { it.copy(result = LoadState.Ready(r)) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(result = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }
}
