package com.shikkhasathi.features.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.learning.presentation.MyCourseUi
import com.shikkhasathi.features.learning.presentation.MyCoursesUiState
import com.shikkhasathi.features.learning.presentation.MyCoursesViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

/** আমার কোর্স (bottom bar কোর্স): purchased courses with progress, filter chips, resume. */
@Composable
fun MyCoursesScreen(
    onResume: (courseId: String, lessonId: String?) -> Unit,
    onExplore: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<MyCoursesViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    MyCoursesContent(state, vm::selectFilter, vm::refresh, vm::retry, onResume, onExplore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCoursesContent(
    state: MyCoursesUiState,
    onFilter: (MyCoursesFilter) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onResume: (String, String?) -> Unit,
    onExplore: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.NavMyCourses.t())
        LazyRow(
            contentPadding = PaddingValues(horizontal = SsSpace.s4, vertical = SsSpace.s1),
            horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        ) {
            items(MyCoursesFilter.entries, key = { it.name }) { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(filterLabel(f).t()) },
                    shape = SsShape.chip,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primarySoft,
                            selectedLabelColor = palette.primaryFg
                        ),
                )
            }
        }
        when (val courses = state.courses) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(120.dp), shape = SsShape.card) }
                }
            is LoadState.Failed -> ErrorState(message = courses.message.t(), onRetry = onRetry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready ->
                if (courses.data.isEmpty()) {
                    EmptyState(
                        icon = Lucide.BookOpen,
                        title = if (state.filter == MyCoursesFilter.All) Str.CoursesEmptyTitle.t() else Str.MyCoursesFilterEmpty.t(),
                        body = Str.CoursesEmptyBody.t(),
                        actionLabel = Str.NavExplore.t(),
                        onAction = onExplore,
                        modifier = Modifier.padding(horizontal = SsSpace.s4),
                    )
                } else {
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s2, bottom = SsSpace.s8),
                            verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
                        ) {
                            items(
                                courses.data,
                                key = { it.courseId }
                            ) { c -> MyCourseCard(c, onResume = { onResume(c.courseId, c.resumeLessonId) }) }
                        }
                    }
                }
        }
    }
}

private fun filterLabel(f: MyCoursesFilter): Str =
    when (f) {
        MyCoursesFilter.All -> Str.AssignmentsFilterAll
        MyCoursesFilter.InProgress -> Str.MyCoursesInProgress
        MyCoursesFilter.Completed -> Str.MyCoursesCompleted
        MyCoursesFilter.Expiring -> Str.MyCoursesExpiring
    }

@Composable
private fun MyCourseCard(
    course: MyCourseUi,
    onResume: () -> Unit,
) {
    val palette = SsTheme.palette
    val progress = Str.HomeProgress.t("p" to course.progressPercent)
    SsCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(hexColor(course.coverColor, palette.primary)))
            Column(Modifier.weight(1f).padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                    when {
                        course.expired -> SsChip(text = Str.MyCoursesExpired.t(), tone = StatusTone.Danger)
                        course.expiringSoon -> SsChip(text = Str.MyCoursesExpiringSoon.t(), tone = StatusTone.Warning)
                        course.completed -> SsChip(text = Str.MyCoursesCompleted.t(), tone = StatusTone.Success)
                        else -> Unit
                    }
                }
                Text(
                    course.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(course.meta, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                course.resumeLessonTitle?.let {
                    Text(
                        Str.HomeLastLesson.t("title" to it),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        maxLines = 1
                    )
                }
                Text(
                    progress,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.successFg,
                    modifier = Modifier.padding(top = SsSpace.s1)
                )
                LinearProgressIndicator(
                    progress = { course.progressPercent / 100f },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(SsShape.chip)
                            .semantics { contentDescription = progress },
                    color = palette.success,
                    trackColor = palette.surface2,
                    drawStopIndicator = {},
                )
                course.accessUntil?.let {
                    Text(Str.MyCoursesAccessUntil.t("date" to it), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                }
                if (!course.expired) {
                    SsButton(
                        text = if (course.progressPercent == 0) Str.CourseStart.t() else Str.HomeResume.t(),
                        onClick = onResume,
                        leadingIcon = Lucide.CirclePlay,
                        variant = if (course.completed) SsButtonVariant.Secondary else SsButtonVariant.Primary,
                        modifier = Modifier.padding(top = SsSpace.s2),
                    )
                }
            }
        }
    }
}
