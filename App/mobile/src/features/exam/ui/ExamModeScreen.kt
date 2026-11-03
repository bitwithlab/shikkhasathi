package com.shikkhasathi.features.exam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.ClipboardCheck
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Timer
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SectionHeader
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
import com.shikkhasathi.core.i18n.durationText
import com.shikkhasathi.core.model.ExamPlanDetail
import com.shikkhasathi.core.model.PlanItem
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.exam.presentation.ExamModeUiState
import com.shikkhasathi.features.exam.presentation.ExamModeViewModel
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** What a tap in Exam Mode opens. */
data class ExamNavigation(
    val openLesson: (courseId: String, lessonId: String) -> Unit,
    val openMock: (testId: String) -> Unit,
    val openFlashcards: (planId: String) -> Unit,
    val openMockTests: () -> Unit,
    val onBack: (() -> Unit)? = null,
)

/** Exam Mode (bottom bar Exam, or `/exam-mode/:planId`): countdown, readiness, today's plan, syllabus, practice. */
@Composable
fun ExamModeScreen(
    planId: String?,
    navigation: ExamNavigation,
) {
    val koin = getKoin()
    val vm =
        rememberSsViewModel(key = "exam:${planId.orEmpty()}") {
            if (planId == null) koin.get<ExamModeViewModel>() else koin.get<ExamModeViewModel> { parametersOf(planId) }
        }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.messageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        ExamModeContent(state, vm, navigation)
        state.form?.let { form ->
            PlanFormSheet(form = form, onChange = vm::updateForm, onSubmit = vm::submitForm, onDismiss = vm::closeForm)
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamModeContent(
    state: ExamModeUiState,
    vm: ExamModeViewModel,
    nav: ExamNavigation,
) {
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = SsSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            nav.onBack?.let { back ->
                IconButton(onClick = back) { Icon(Lucide.ArrowLeft, contentDescription = Str.CommonBack.t(), tint = palette.text) }
            }
            Text(
                Str.NavExamMode.t(),
                style = MaterialTheme.typography.headlineSmall,
                color = palette.text,
                modifier = Modifier.weight(1f).padding(start = SsSpace.s2),
            )
            IconButton(onClick = vm::openForm) { Icon(Lucide.Plus, contentDescription = Str.ExamAddPlan.t(), tint = palette.primaryFg) }
        }
        when (val plans = state.plans) {
            LoadState.Loading -> LoadingExam()
            is LoadState.Failed -> ErrorState(message = plans.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready -> {
                if (state.switchable.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SsSpace.s4),
                        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)
                    ) {
                        items(state.switchable, key = { it.id }) { p ->
                            FilterChip(
                                selected = p.id == state.selectedPlanId,
                                onClick = { vm.selectPlan(p.id) },
                                label = { Text(listOfNotNull(p.courseCode, p.subjectName).first()) },
                                shape = SsShape.chip,
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.primarySoft,
                                        selectedLabelColor = palette.primaryFg
                                    ),
                            )
                        }
                    }
                }
                when (val detail = state.detail) {
                    null ->
                        EmptyState(
                            icon = Lucide.CalendarClock,
                            title = Str.ExamEmptyTitle.t(),
                            body = Str.ExamEmptyBody.t(),
                            actionLabel = Str.ExamAddPlan.t(),
                            onAction = vm::openForm,
                            modifier = Modifier.padding(horizontal = SsSpace.s4),
                        )
                    LoadState.Loading -> LoadingExam()
                    is LoadState.Failed ->
                        ErrorState(
                            message = detail.message.t(),
                            onRetry = vm::retry,
                            modifier = Modifier.padding(SsSpace.s4)
                        )
                    is LoadState.Ready -> PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh) { PlanDetail(detail.data, nav) }
                }
            }
        }
    }
}

@Composable
private fun LoadingExam() {
    Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
        SkeletonBox(Modifier.fillMaxWidth().height(150.dp), shape = SsShape.card)
        SkeletonCard()
        SkeletonCard()
    }
}

@Suppress("CyclomaticComplexMethod") // flat list of optional sections, each a simple `if`
@Composable
private fun PlanDetail(
    plan: ExamPlanDetail,
    nav: ExamNavigation,
) {
    val palette = SsTheme.palette
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s3, bottom = SsSpace.s8),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
    ) {
        item(key = "countdown") { Countdown(plan) }
        item(key = "today") {
            SsCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = if (plan.today?.isRevision == true) Str.ExamRevisionDay.t() else Str.ExamTodayPlan.t(),
                    icon = Lucide.CalendarClock,
                    badge =
                        plan.today
                            ?.totalMinutes
                            ?.takeIf { it > 0 }
                            ?.let { m -> { SsChip(text = durationText(m * 60).t()) } },
                )
                val items = plan.today?.items.orEmpty()
                if (items.isEmpty()) {
                    Text(Str.ExamTodayEmpty.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                } else {
                    items.forEach { PlanItemRow(it, plan.id, nav) }
                }
            }
        }
        if (plan.syllabus.isNotEmpty()) {
            item(key = "syllabus") {
                SsCard(Modifier.fillMaxWidth()) {
                    SectionHeader(title = Str.ExamSyllabus.t(), icon = Lucide.ClipboardCheck)
                    plan.syllabus.forEach { ch ->
                        val p = if (ch.total == 0) 0f else ch.done.toFloat() / ch.total
                        Row(Modifier.fillMaxWidth().padding(vertical = SsSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ch.status == "done") Lucide.CircleCheck else Lucide.Circle,
                                contentDescription = null,
                                tint = if (ch.status == "done") palette.success else palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = SsSpace.s2)) {
                                Text(ch.title, style = MaterialTheme.typography.bodyMedium, color = palette.text, maxLines = 1)
                                LinearProgressIndicator(
                                    progress = { p },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .height(4.dp)
                                            .clip(SsShape.chip),
                                    color = palette.success,
                                    trackColor = palette.surface2,
                                    drawStopIndicator = {},
                                )
                            }
                            Text(
                                UiText.Count(ch.done).t() + "/" + UiText.Count(ch.total).t(),
                                style = MaterialTheme.typography.labelSmall.numeric,
                                color = palette.muted
                            )
                        }
                    }
                }
            }
        }
        if (plan.important.isNotEmpty()) {
            item(key = "important") {
                SsCard(Modifier.fillMaxWidth()) {
                    SectionHeader(title = Str.ExamImportant.t(), icon = Lucide.Star, iconTint = palette.warning)
                    plan.important.take(MAX_IMPORTANT).forEach { l ->
                        ClickRow(l.title, durationText(l.durationSec).t(), l.done) { nav.openLesson(l.courseId, l.lessonId) }
                    }
                    Text(
                        Str.CourseExamDisclaimer.t(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = SsSpace.s2)
                    )
                }
            }
        }
        item(key = "practice") {
            SsCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = Str.ExamPractice.t(),
                    icon = Lucide.Layers,
                    actionLabel = Str.CommonSeeAll.t(),
                    onAction = nav.openMockTests
                )
                plan.mockTests.forEach { m ->
                    val sub = m.bestScore?.let { Str.MockBestScore.t("p" to it) } ?: Str.MockQuestions.t("n" to m.questionCount)
                    ClickRow(m.title, sub, done = m.bestScore != null) { nav.openMock(m.id) }
                }
                if (plan.flashcards.total > 0) {
                    ClickRow(
                        Str.ExamFlashcards.t(),
                        Str.ExamFlashcardsKnown.t("known" to plan.flashcards.known, "total" to plan.flashcards.total),
                        done = plan.flashcards.known == plan.flashcards.total,
                    ) { nav.openFlashcards(plan.id) }
                }
                if (plan.mockTests.isEmpty() && plan.flashcards.total == 0) {
                    Text(Str.ExamNoPractice.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
        }
        if (plan.revisionUnlocked) {
            item(key = "revision") {
                SsCard(Modifier.fillMaxWidth(), container = palette.warningSoft) {
                    SectionHeader(title = Str.ExamRevisionPack.t(), icon = Lucide.Repeat, iconTint = palette.warningFg)
                    Text(
                        Str.ExamRevisionPackBody.t(
                            "lessons" to plan.revisionPack.importantLessons,
                            "notes" to plan.revisionPack.notes,
                            "wrong" to plan.revisionPack.wrongAnswers,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text,
                    )
                }
            }
        }
    }
}

private const val MAX_IMPORTANT = 6

/** Countdown Card — gradient allowed here only (CLAUDE.md §7), with the readiness ring. */
@Composable
private fun Countdown(plan: ExamPlanDetail) {
    val palette = SsTheme.palette
    val examType = Str.fromKey("examType.${plan.examType}")?.t() ?: plan.examType
    val days = if (plan.daysLeft <= 0) Str.ExamToday.t() else Str.ExamDaysLeft.t("n" to plan.daysLeft)
    val readiness = Str.ExamReadiness.t("p" to plan.readiness)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(SsShape.card)
            .background(Brush.linearGradient(listOf(palette.primary, palette.violet)))
            .semantics(mergeDescendants = true) {
                contentDescription =
                    listOfNotNull(examType, plan.courseCode, plan.subjectName, days, readiness).joinToString(", ")
            }.padding(SsSpace.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    Icon(Lucide.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        listOfNotNull(examType, plan.courseCode).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Text(plan.subjectName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                Text(days, style = MaterialTheme.typography.headlineMedium.numeric, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    Str.ExamBreakdown.t("lessons" to plan.readinessBreakdown.lessons, "mock" to (plan.readinessBreakdown.mock ?: 0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    progress = { plan.readiness.coerceIn(0, 100) / 100f },
                    modifier = Modifier.size(80.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 7.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(UiText.Count(plan.readiness).t() + "%", style = MaterialTheme.typography.titleMedium.numeric, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlanItemRow(
    item: PlanItem,
    planId: String,
    nav: ExamNavigation,
) {
    val (title, sub) =
        when (item.kind) {
            "lesson" ->
                (item.title.orEmpty()) to
                    listOfNotNull(item.chapterTitle, item.durationSec?.let { durationText(it).t() }).joinToString(" · ")
            "mock" -> (item.title ?: Str.NavMockTests.t()) to Str.MockQuestions.t("n" to (item.questionCount ?: 0))
            "flashcards" -> Str.ExamFlashcards.t() to Str.ExamFlashcardsToday.t("n" to (item.count ?: 0))
            else -> (item.title ?: Str.ExamRevision.t()) to ""
        }
    val onClick: (() -> Unit)? =
        when {
            item.kind == "lesson" && item.courseId != null && item.lessonId != null -> {
                { nav.openLesson(item.courseId, item.lessonId) }
            }
            item.kind == "mock" && item.testId != null -> {
                { nav.openMock(item.testId) }
            }
            item.kind == "flashcards" -> {
                { nav.openFlashcards(planId) }
            }
            else -> null
        }
    ClickRow(title, sub, item.done, important = item.isImportant, onClick = onClick)
}

@Composable
private fun ClickRow(
    title: String,
    sub: String,
    done: Boolean,
    important: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val palette = SsTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        Icon(
            if (done) Lucide.CircleCheck else Lucide.Circle,
            contentDescription = if (done) Str.PlayerCompleted.t() else null,
            tint = if (done) palette.success else palette.borderStrong,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = palette.text, maxLines = 2)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
        }
        if (important) SsChip(text = Str.LessonImportant.t(), tone = StatusTone.Warning)
        if (onClick != null && !done) SsButton(text = Str.ExamStart.t(), onClick = onClick, variant = SsButtonVariant.Ghost)
    }
}
