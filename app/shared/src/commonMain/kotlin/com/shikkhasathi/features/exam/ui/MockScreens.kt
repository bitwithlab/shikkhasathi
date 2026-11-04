package com.shikkhasathi.features.exam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ClipboardCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.clockText
import com.shikkhasathi.core.i18n.durationText
import com.shikkhasathi.core.model.MockSummary
import com.shikkhasathi.core.model.ReviewQuestion
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.exam.presentation.MockAttemptUiState
import com.shikkhasathi.features.exam.presentation.MockAttemptViewModel
import com.shikkhasathi.features.exam.presentation.MockResultViewModel
import com.shikkhasathi.features.exam.presentation.MockTestsViewModel
import com.shikkhasathi.shared.navigation.MockAttemptRoute
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

// ---------------------------------------------------------------- list

@Composable
fun MockTestsScreen(
    onBack: () -> Unit,
    onStart: (testId: String, resumeAttemptId: String?) -> Unit,
    onExplore: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<MockTestsViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.NavMockTests.t(), onBack = onBack)
        when (val tests = state.tests) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    repeat(3) { SkeletonCard() }
                }
            is LoadState.Failed -> ErrorState(message = tests.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready ->
                if (tests.data.isEmpty()) {
                    EmptyState(
                        icon = Lucide.ClipboardCheck,
                        title = Str.MockTestsEmptyTitle.t(),
                        body = Str.MockTestsEmptyBody.t(),
                        actionLabel = Str.NavExplore.t(),
                        onAction = onExplore,
                        modifier = Modifier.padding(horizontal = SsSpace.s4),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                        items(tests.data, key = { it.id }) { MockCard(it, onStart) }
                    }
                }
        }
    }
}

@Composable
private fun MockCard(
    m: MockSummary,
    onStart: (String, String?) -> Unit,
) {
    val palette = SsTheme.palette
    SsCard(Modifier.fillMaxWidth()) {
        Text(m.courseTitle, style = MaterialTheme.typography.labelMedium, color = palette.muted, maxLines = 1)
        Text(m.title, style = MaterialTheme.typography.titleSmall, color = palette.text)
        Text(
            Str.MockQuestions.t("n" to m.questionCount) + " · " + durationText(m.durationSec).t(),
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        Row(
            Modifier.padding(top = SsSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)
        ) {
            m.bestScore?.let { SsChip(text = Str.MockBestScore.t("p" to it), tone = StatusTone.Success) }
            Box(Modifier.weight(1f))
            SsButton(
                text =
                    when {
                        m.inProgressAttemptId != null -> Str.MockResume.t()
                        m.attempts > 0 -> Str.MockRetake.t()
                        else -> Str.ExamStart.t()
                    },
                onClick = { onStart(m.id, m.inProgressAttemptId) },
                leadingIcon = Lucide.Timer,
                variant = if (m.inProgressAttemptId != null) SsButtonVariant.Primary else SsButtonVariant.Secondary,
            )
        }
    }
}

// ---------------------------------------------------------------- runner

@Composable
fun MockAttemptScreen(
    route: MockAttemptRoute,
    onBack: () -> Unit,
    onFinished: (attemptId: String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "attempt:${route.testId}") { koin.get<MockAttemptViewModel> { parametersOf(route) } }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finishedAttemptId) { state.finishedAttemptId?.let(onFinished) }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.messageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (val a = state.attempt) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(48.dp))
                    SkeletonCard()
                }
            is LoadState.Failed ->
                Column {
                    ScreenHeader(title = Str.NavMockTests.t(), onBack = onBack)
                    ErrorState(message = a.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
                }
            is LoadState.Ready -> Runner(state, vm, onBack)
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
    if (state.confirmSubmit) {
        AlertDialog(
            onDismissRequest = vm::dismissSubmit,
            title = { Text(Str.MockSubmitTitle.t()) },
            text = {
                Text(
                    if (state.unansweredCount >
                        0
                    ) {
                        Str.MockSubmitUnanswered.t("n" to state.unansweredCount)
                    } else {
                        Str.MockSubmitAllAnswered.t()
                    },
                )
            },
            confirmButton = { TextButton(onClick = vm::submit) { Text(Str.MockSubmit.t()) } },
            dismissButton = { TextButton(onClick = vm::dismissSubmit) { Text(Str.MockKeepGoing.t()) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("CyclomaticComplexMethod") // flat list of optional sections, each a simple `if`
@Composable
private fun Runner(
    state: MockAttemptUiState,
    vm: MockAttemptViewModel,
    onBack: () -> Unit,
) {
    val palette = SsTheme.palette
    val attempt = (state.attempt as LoadState.Ready).data
    val question = attempt.questions.getOrNull(state.index) ?: return
    val answer = state.answers[question.id]
    val timer = clockText(state.remainingSec).t()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = SsSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = Str.CommonBack.t(), tint = palette.text) }
            Text(
                attempt.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (state.failed.isNotEmpty()) {
                Icon(
                    Lucide.CloudOff,
                    contentDescription = Str.MockUnsaved.t(),
                    tint = palette.warningFg,
                    modifier = Modifier.padding(end = SsSpace.s2)
                )
            }
            // Timer: amber in the last minute; announced politely so TalkBack users hear it then.
            SsChip(
                text = timer,
                tone = if (state.lowTime) StatusTone.Warning else StatusTone.Primary,
                icon = Lucide.Timer,
                modifier =
                    Modifier.padding(end = SsSpace.s2).semantics {
                        contentDescription = Str.MockTimeLeft.t().let { "$it $timer" }
                        if (state.lowTime) liveRegion = LiveRegionMode.Polite
                    },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SsSpace.s4),
            verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Str.MockQuestionOf.t("n" to state.index + 1, "total" to state.questionCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.muted,
                    modifier = Modifier.weight(1f),
                )
                question.chapterTag?.let { SsChip(text = it) }
                IconButton(onClick = { vm.toggleFlag(question.id) }) {
                    Icon(
                        Lucide.Flag,
                        contentDescription = if (answer?.flagged == true) Str.MockUnflag.t() else Str.MockFlag.t(),
                        tint = if (answer?.flagged == true) palette.warning else palette.muted,
                    )
                }
            }
            Text(question.body, style = MaterialTheme.typography.titleMedium, color = palette.text)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                question.options.forEachIndexed { i, option ->
                    val chosen = answer?.optionId == option.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) palette.primary else palette.border, SsShape.control)
                            .background(if (chosen) palette.primarySoft else palette.surface, SsShape.control)
                            .selectable(selected = chosen, onClick = { vm.choose(question.id, option.id) }, role = Role.RadioButton)
                            .padding(horizontal = SsSpace.s3, vertical = SsSpace.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
                    ) {
                        Box(
                            Modifier.size(28.dp).background(if (chosen) palette.primary else palette.surface2, SsShape.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                OPTION_LETTERS.getOrElse(i) {
                                    "?"
                                },
                                color = if (chosen) palette.onPrimary else palette.text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Text(option.body, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                    }
                }
            }
            // Question palette: answered = filled, flagged = amber, current = outlined.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SsSpace.s1), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                attempt.questions.forEachIndexed { i, q ->
                    val a = state.answers[q.id]
                    val bg =
                        when {
                            a?.flagged == true -> palette.warningSoft
                            a?.optionId != null -> palette.primarySoft
                            else -> palette.surface
                        }
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(
                                if (i ==
                                    state.index
                                ) {
                                    2.dp
                                } else {
                                    1.dp
                                },
                                if (i == state.index) palette.primary else palette.border,
                                SsShape.control
                            ).background(bg, SsShape.control)
                            .selectable(selected = i == state.index, onClick = { vm.goTo(i) }, role = Role.Tab),
                        contentAlignment = Alignment.Center,
                    ) { Text(UiText.Count(i + 1).t(), style = MaterialTheme.typography.labelMedium.numeric, color = palette.text) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(SsSpace.s4), horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
            SsButton(text = Str.MockPrevious.t(), onClick = vm::previous, enabled = state.index > 0, variant = SsButtonVariant.Secondary)
            Box(Modifier.weight(1f))
            if (state.index < state.questionCount - 1) {
                SsButton(text = Str.MockNext.t(), onClick = vm::next, variant = SsButtonVariant.Secondary)
            }
            SsButton(text = Str.MockSubmit.t(), onClick = vm::requestSubmit, loading = state.submitting)
        }
    }
}

private val OPTION_LETTERS = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ")

// ---------------------------------------------------------------- result

@Composable
fun MockResultScreen(
    attemptId: String,
    onBack: () -> Unit,
    onRetake: (testId: String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "result:$attemptId") { koin.get<MockResultViewModel> { parametersOf(attemptId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.MockResultTitle.t(), onBack = onBack)
        when (val r = state.result) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    repeat(2) { SkeletonCard() }
                }
            is LoadState.Failed -> ErrorState(message = r.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready -> {
                val res = r.data
                val review = if (state.onlyMistakes) res.review.filter { it.chosenOptionId != it.correctOptionId } else res.review
                LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    item(key = "score") {
                        SsCard(Modifier.fillMaxWidth()) {
                            Text(res.title, style = MaterialTheme.typography.labelLarge, color = palette.muted)
                            Text(
                                UiText.Count(res.score).t() + "%",
                                style = MaterialTheme.typography.displaySmall.numeric,
                                color = if (res.score >= PASS_SCORE) palette.successFg else palette.warningFg,
                            )
                            Text(
                                Str.MockScoreLine.t("correct" to res.correct, "wrong" to res.wrong, "skipped" to res.skipped),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text,
                            )
                            Text(
                                Str.MockTimeUsed.t(
                                    "time" to clockText(res.timeUsedSec)
                                ) + " · " + Str.MockBestScore.t("p" to res.bestScore),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                            )
                            if (res.autoSubmitted) {
                                Text(
                                    Str.MockAutoSubmitted.t(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.warningFg
                                )
                            }
                            SsButton(text = Str.MockRetake.t(), onClick = {
                                onRetake(res.testId)
                            }, variant = SsButtonVariant.Secondary, modifier = Modifier.padding(top = SsSpace.s2))
                        }
                    }
                    if (res.chapters.isNotEmpty()) {
                        item(key = "chapters") {
                            SsCard(Modifier.fillMaxWidth()) {
                                Text(Str.MockByChapter.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
                                res.chapters.forEach { c ->
                                    Row(Modifier.fillMaxWidth().padding(top = SsSpace.s1)) {
                                        Text(
                                            c.tag,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.text,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            UiText.Count(c.correct).t() + "/" + UiText.Count(c.total).t(),
                                            style = MaterialTheme.typography.bodyMedium.numeric,
                                            color = if (c.correct * 2 < c.total) palette.dangerFg else palette.text,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "filter") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Str.MockOnlyMistakes.t(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = state.onlyMistakes, onCheckedChange = vm::setOnlyMistakes)
                        }
                    }
                    items(review, key = { it.questionId }) { ReviewCard(it) }
                }
            }
        }
    }
}

private const val PASS_SCORE = 60

@Composable
private fun ReviewCard(q: ReviewQuestion) {
    val palette = SsTheme.palette
    SsCard(Modifier.fillMaxWidth()) {
        Text(q.body, style = MaterialTheme.typography.titleSmall, color = palette.text)
        q.options.forEachIndexed { i, o ->
            val correct = o.id == q.correctOptionId
            val chosenWrong = o.id == q.chosenOptionId && !correct
            val label =
                when {
                    correct -> Str.MockCorrectAnswer.t()
                    chosenWrong -> Str.MockYourAnswer.t()
                    else -> null
                }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SsSpace.s1)
                    .background(
                        when {
                            correct -> palette.successSoft
                            chosenWrong -> palette.dangerSoft
                            else -> palette.surface
                        },
                        SsShape.small,
                    ).padding(SsSpace.s2)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
            ) {
                Text(OPTION_LETTERS.getOrElse(i) { "?" }, style = MaterialTheme.typography.labelLarge, color = palette.muted)
                Text(o.body, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
                label?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (correct) palette.successFg else palette.dangerFg
                    )
                }
            }
        }
        if (q.chosenOptionId ==
            null
        ) {
            Text(
                Str.MockSkipped.t(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = SsSpace.s1)
            )
        }
        q.explanation?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted, modifier = Modifier.padding(top = SsSpace.s2))
        }
    }
}
