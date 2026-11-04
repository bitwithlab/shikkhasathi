package com.shikkhasathi.features.exam.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.common.time.dhakaDate
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.EXAM_TYPES
import com.shikkhasathi.core.model.ExamPlanDetail
import com.shikkhasathi.core.model.ExamPlanInput
import com.shikkhasathi.core.model.ExamPlanSummary
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.exam.data.ExamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** "নতুন পরীক্ষা যোগ করুন" form. Date/time are Asia/Dhaka wall-clock values. */
data class PlanForm(
    val subjectName: String = "",
    val courseCode: String = "",
    val examType: String = "midterm",
    val date: LocalDate? = null,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    companion object {
        const val DEFAULT_HOUR = 10
        const val SUBJECT_MIN = 2
        const val SUBJECT_MAX = 160
        const val CODE_MAX = 30
    }
}

data class ExamModeUiState(
    val plans: LoadState<List<ExamPlanSummary>> = LoadState.Loading,
    val selectedPlanId: String? = null,
    /** Null when there is no plan yet (the screen shows the form invite). */
    val detail: LoadState<ExamPlanDetail>? = null,
    /** Non-null while the create-plan sheet is open. */
    val form: PlanForm? = null,
    val message: UiText? = null,
) {
    /** Upcoming plans first (the server orders them); past ones are kept but not offered in the switcher. */
    val switchable: List<ExamPlanSummary> get() = plans.dataOrNull.orEmpty().filter { it.active && !it.past }
}

/**
 * Exam Mode (`/exam-plans`, `/exam-plans/:id`): pick the nearest active plan (or the one a link asked for),
 * show its countdown, readiness ring, today's checklist, syllabus, important lessons, mock tests and flashcards.
 * The plan is recomputed by the server on every read (ADR-023), so [refresh] after studying shows new ticks.
 */
@Suppress("TooManyFunctions") // one intent per form field / control
class ExamModeViewModel(
    private val repository: ExamRepository,
    private val requestedPlanId: String? = null,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(ExamModeUiState())
    val state: StateFlow<ExamModeUiState> = mutableState.asStateFlow()
    private var detailJob: Job? = null

    init {
        loadPlans(prefer = requestedPlanId)
    }

    fun retry() = loadPlans(prefer = state.value.selectedPlanId ?: requestedPlanId)

    fun refresh() {
        val id = state.value.selectedPlanId ?: return loadPlans(prefer = null)
        loadDetail(id, silent = true)
    }

    fun selectPlan(planId: String) {
        if (planId == state.value.selectedPlanId) return
        loadDetail(planId, silent = false)
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    // ------------------------------------------------------------------ create plan

    fun openForm() = mutableState.update { it.copy(form = PlanForm()) }

    fun closeForm() = mutableState.update { it.copy(form = null) }

    fun updateForm(change: (PlanForm) -> PlanForm) =
        mutableState.update { s ->
            s.copy(
                form = s.form?.let { change(it).copy(error = null) }
            )
        }

    fun submitForm() {
        val form = state.value.form ?: return
        if (form.saving) return
        val error = validate(form)
        if (error != null) {
            mutableState.update { it.copy(form = form.copy(error = error)) }
            return
        }
        val date = requireNotNull(form.date)
        val input =
            ExamPlanInput(
                subjectName = form.subjectName.trim(),
                courseCode = form.courseCode.trim().ifEmpty { null },
                examType = form.examType,
                examDate = dhakaIso(date, form.hour, form.minute),
            )
        mutableState.update { it.copy(form = form.copy(saving = true)) }
        scope.launch {
            try {
                val created = repository.createPlan(input)
                mutableState.update { it.copy(form = null, message = Str.ExamPlanCreated.asUiText()) }
                loadPlans(prefer = created.id)
            } catch (e: ApiException) {
                mutableState.update { it.copy(form = it.form?.copy(saving = false, error = e.error.toUiText())) }
            }
        }
    }

    private fun validate(form: PlanForm): UiText? {
        val today = clock.now().dhakaDate()
        return when {
            form.subjectName.trim().length < PlanForm.SUBJECT_MIN -> Str.ExamFormSubjectRequired.asUiText()
            form.examType !in EXAM_TYPES -> Str.ExamFormTypeRequired.asUiText()
            form.date == null -> Str.ExamFormDateRequired.asUiText()
            form.date < today -> Str.ExamFormDatePast.asUiText()
            else -> null
        }
    }

    // ------------------------------------------------------------------ loading

    private fun loadPlans(prefer: String?) {
        mutableState.update { it.copy(plans = LoadState.Loading) }
        scope.launch {
            try {
                val plans = repository.plans()
                mutableState.update { it.copy(plans = LoadState.Ready(plans)) }
                val target =
                    prefer?.takeIf { id -> plans.any { it.id == id } }
                        ?: plans.firstOrNull { it.active && !it.past }?.id
                if (target == null) {
                    mutableState.update { it.copy(selectedPlanId = null, detail = null) }
                } else {
                    loadDetail(target, silent = false)
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(plans = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    private fun loadDetail(
        planId: String,
        silent: Boolean,
    ) {
        detailJob?.cancel()
        mutableState.update {
            it.copy(
                selectedPlanId = planId,
                detail =
                    if (silent &&
                        it.detail is LoadState.Ready
                    ) {
                        it.detail
                    } else {
                        LoadState.Loading
                    }
            )
        }
        detailJob =
            scope.launch {
                try {
                    val detail = repository.plan(planId)
                    mutableState.update { it.copy(detail = LoadState.Ready(detail)) }
                } catch (e: ApiException) {
                    mutableState.update { s ->
                        if (silent &&
                            s.detail is LoadState.Ready
                        ) {
                            s.copy(message = e.error.toUiText())
                        } else {
                            s.copy(detail = LoadState.Failed(e.error.toUiText()))
                        }
                    }
                }
            }
    }
}

/** `2026-11-20` 10:00 in Dhaka → `2026-11-20T10:00:00+06:00` (Bangladesh has no DST). */
fun dhakaIso(
    date: LocalDate,
    hour: Int,
    minute: Int,
): String = "${date}T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00+06:00"
