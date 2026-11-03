package com.shikkhasathi.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.TriangleAlert
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.SectionHeader
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.Compatibility
import com.shikkhasathi.core.model.ContinueWidget
import com.shikkhasathi.core.model.DueGroupKey
import com.shikkhasathi.core.model.ExamWidget
import com.shikkhasathi.core.model.RecommendedCourse
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.FeedRowUi
import com.shikkhasathi.features.classroom.ui.AssignmentRow
import com.shikkhasathi.features.classroom.ui.FeedRow
import com.shikkhasathi.features.home.presentation.DueSoonUi
import com.shikkhasathi.features.home.presentation.FeedUi
import com.shikkhasathi.features.home.presentation.WeekDayUi
import com.shikkhasathi.features.home.presentation.Widget

private val WidgetPadding = Modifier.padding(horizontal = SsSpace.s4)

/** Compact per-widget failure: the rest of the dashboard keeps working. */
@Composable
private fun WidgetError(
    title: String,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    SsCard(modifier = WidgetPadding.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
            Icon(Lucide.TriangleAlert, contentDescription = null, tint = palette.warningFg, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.text)
                Text(Str.HomeWidgetError.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            TextButton(onClick = onRetry) { Text(Str.CommonRetry.t(), color = palette.primaryFg) }
        }
    }
}

// ---------------------------------------------------------------- 2. সামনের অ্যাসাইনমেন্ট

@Composable
fun DueSoonWidget(
    widget: Widget<DueSoonUi>,
    onSeeAll: () -> Unit,
    onOpen: (AssignmentRowUi) -> Unit,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    when (widget) {
        Widget.Hidden -> Unit
        Widget.Loading -> SkeletonCard(WidgetPadding)
        Widget.Failed -> WidgetError(Str.HomeDueSoon.t(), onRetry)
        is Widget.Ready -> {
            val due = widget.data
            SsCard(modifier = WidgetPadding.fillMaxWidth()) {
                SectionHeader(
                    title = Str.HomeDueSoon.t(),
                    icon = Lucide.ClipboardList,
                    actionLabel =
                        if (due.hasMore) "${Str.CommonSeeAll.t()} (${UiText.Count(due.total).t()})" else Str.CommonSeeAll.t(),
                    onAction = onSeeAll,
                )
                if (due.isEmpty) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = SsSpace.s3),
                        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(28.dp))
                        Column {
                            Text(Str.HomeAllDone.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
                            Text(Str.HomeAllDoneBody.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        }
                    }
                }
                due.groups.forEach { group ->
                    Text(
                        group.label.t(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (group.key == DueGroupKey.Overdue) palette.dangerFg else palette.muted,
                        modifier = Modifier.padding(top = SsSpace.s3, bottom = SsSpace.s2),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                        group.rows.forEach { row -> AssignmentRow(row, onClick = { onOpen(row) }) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 3. নতুন আপডেট

@Composable
fun FeedWidget(
    widget: Widget<FeedUi>,
    onMarkAllRead: () -> Unit,
    onOpen: (FeedRowUi) -> Unit,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    when (widget) {
        Widget.Hidden -> Unit
        Widget.Loading -> SkeletonCard(WidgetPadding)
        Widget.Failed -> WidgetError(Str.HomeFeed.t(), onRetry)
        is Widget.Ready -> {
            val feed = widget.data
            SsCard(modifier = WidgetPadding.fillMaxWidth()) {
                SectionHeader(
                    title = Str.HomeFeed.t(),
                    icon = Lucide.Megaphone,
                    badge =
                        if (feed.unread > 0) {
                            { SsChip(text = Str.HomeNewCount.t("n" to feed.unread), tone = StatusTone.Primary) }
                        } else {
                            null
                        },
                    actionLabel = if (feed.unread > 0) Str.HomeMarkAllRead.t() else null,
                    onAction = onMarkAllRead,
                )
                if (feed.items.isEmpty()) {
                    Text(
                        Str.HomeFeedEmpty.t(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(vertical = SsSpace.s3),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                    feed.items.forEach { row -> FeedRow(row, onClick = { onOpen(row) }) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 4. exam countdown

/** Countdown Card — the only gradient surface in the app besides the landing hero (CLAUDE.md §7). */
@Composable
fun ExamWidgetCard(
    widget: Widget<ExamWidget?>,
    onOpenExamMode: () -> Unit,
    onRetry: () -> Unit,
) {
    when (widget) {
        Widget.Hidden -> Unit
        Widget.Loading -> SkeletonBox(WidgetPadding.fillMaxWidth().height(140.dp), shape = SsShape.card)
        Widget.Failed -> WidgetError(Str.ExamOpen.t(), onRetry)
        is Widget.Ready -> {
            val exam = widget.data
            if (exam == null) NoExamCard(onOpenExamMode) else CountdownCard(exam, onOpenExamMode)
        }
    }
}

@Composable
private fun CountdownCard(
    exam: ExamWidget,
    onOpen: () -> Unit,
) {
    val palette = SsTheme.palette
    val onGradient = Color.White
    val examType = Str.fromKey("examType.${exam.examType}")?.t() ?: exam.examType
    val days = if (exam.daysLeft == 0) Str.ExamToday.t() else Str.ExamDaysLeft.t("n" to exam.daysLeft)
    val readiness = Str.ExamReadiness.t("p" to exam.readiness)
    val description = listOfNotNull(examType, exam.courseCode, exam.subjectName, days, readiness).joinToString(", ")
    Box(
        WidgetPadding
            .fillMaxWidth()
            .clip(SsShape.card)
            .background(Brush.linearGradient(listOf(palette.primary, palette.violet)))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(SsSpace.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    Icon(Lucide.Timer, contentDescription = null, tint = onGradient, modifier = Modifier.size(16.dp))
                    Text(
                        listOfNotNull(examType, exam.courseCode).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = onGradient.copy(alpha = 0.85f),
                    )
                }
                Text(exam.subjectName, style = MaterialTheme.typography.titleLarge, color = onGradient, maxLines = 2)
                Text(days, style = MaterialTheme.typography.headlineMedium.numeric, color = onGradient, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                    Text(Str.ExamOpen.t(), color = onGradient, style = MaterialTheme.typography.labelLarge)
                }
            }
            // Readiness ring.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { exam.readiness.coerceIn(0, 100) / 100f },
                    modifier = Modifier.size(72.dp),
                    color = onGradient,
                    trackColor = onGradient.copy(alpha = 0.25f),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(UiText.Count(exam.readiness).t() + "%", style = MaterialTheme.typography.titleSmall.numeric, color = onGradient)
            }
        }
    }
}

@Composable
private fun NoExamCard(onAdd: () -> Unit) {
    val palette = SsTheme.palette
    SsCard(modifier = WidgetPadding.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
            Box(Modifier.size(44.dp).background(palette.warningSoft, SsShape.control), contentAlignment = Alignment.Center) {
                Icon(Lucide.Timer, contentDescription = null, tint = palette.warningFg, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(Str.HomeNoExam.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
                Text(Str.HomeNoExamBody.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            SsButton(text = Str.HomeAddExam.t(), onClick = onAdd, variant = SsButtonVariant.Secondary)
        }
    }
}

// ---------------------------------------------------------------- 5. পড়া চালিয়ে যান

@Composable
fun ContinueLearningWidget(
    widget: Widget<ContinueWidget?>,
    onResume: (ContinueWidget) -> Unit,
    onExplore: () -> Unit,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    when (widget) {
        Widget.Hidden -> Unit
        Widget.Loading -> SkeletonCard(WidgetPadding)
        Widget.Failed -> WidgetError(Str.HomeCoursesTitle.t(), onRetry)
        is Widget.Ready -> {
            val course = widget.data
            SsCard(modifier = WidgetPadding.fillMaxWidth()) {
                SectionHeader(title = Str.HomeCoursesTitle.t(), icon = Lucide.CirclePlay, iconTint = palette.success)
                if (course == null) {
                    EmptyState(
                        icon = Lucide.BookOpen,
                        title = Str.HomeCoursesEmptyTitle.t(),
                        body = Str.HomeCoursesEmptyBody.t(),
                        actionLabel = Str.NavExplore.t(),
                        onAction = onExplore,
                        modifier = Modifier.padding(top = 0.dp),
                    )
                } else {
                    val progress = Str.HomeProgress.t("p" to course.progressPercent)
                    Text(course.title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 2)
                    Text(
                        listOfNotNull(course.courseCode, course.sellerName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                    course.lastLessonTitle?.let {
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
                        modifier = Modifier.padding(top = SsSpace.s2)
                    )
                    LinearProgressIndicator(
                        progress = { course.progressPercent.coerceIn(0, 100) / 100f },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = SsSpace.s2)
                                .height(6.dp)
                                .clip(SsShape.chip)
                                .semantics { contentDescription = progress },
                        color = palette.success,
                        trackColor = palette.surface2,
                        drawStopIndicator = {},
                    )
                    SsButton(text = Str.HomeResume.t(), onClick = { onResume(course) }, leadingIcon = Lucide.CirclePlay)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 6. এই সপ্তাহ

@Composable
fun WeekWidget(
    widget: Widget<List<WeekDayUi>>,
    onOpenCalendar: () -> Unit,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    when (widget) {
        Widget.Hidden -> Unit
        Widget.Loading -> SkeletonBox(WidgetPadding.fillMaxWidth().height(96.dp), shape = SsShape.card)
        Widget.Failed -> WidgetError(Str.HomeWeek.t(), onRetry)
        is Widget.Ready -> {
            SsCard(modifier = WidgetPadding.fillMaxWidth()) {
                SectionHeader(
                    title = Str.HomeWeek.t(),
                    icon = Lucide.CalendarDays,
                    actionLabel = Str.NavCalendar.t(),
                    onAction = onOpenCalendar,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                    widget.data.forEach { day -> WeekDayCell(day, onOpenCalendar, Modifier.weight(1f)) }
                }
                Row(
                    Modifier.padding(top = SsSpace.s3),
                    horizontalArrangement = Arrangement.spacedBy(SsSpace.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(palette.danger, Str.HomeLegendDeadline.t())
                    LegendDot(palette.warning, Str.HomeLegendExam.t())
                }
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    day: WeekDayUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    val description = day.description.t()
    Column(
        modifier =
            modifier
                .heightIn(min = 64.dp)
                .clip(SsShape.control)
                .background(if (day.isToday) palette.primarySoft else Color.Transparent)
                .border(1.dp, if (day.isToday) palette.primary else Color.Transparent, SsShape.control)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(vertical = SsSpace.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            day.weekday.t(),
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) palette.primaryFg else palette.muted,
            maxLines = 1
        )
        Text(
            UiText.Count(day.dayOfMonth).t(),
            style = MaterialTheme.typography.titleSmall.numeric,
            color = if (day.isToday) palette.primaryFg else palette.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            if (day.deadlines > 0) Box(Modifier.size(6.dp).background(palette.danger, SsShape.chip))
            if (day.exams > 0) Box(Modifier.size(6.dp).background(palette.warning, SsShape.chip))
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
        Box(Modifier.size(8.dp).background(color, SsShape.chip))
        Text(label, style = MaterialTheme.typography.bodySmall, color = SsTheme.palette.muted)
    }
}

// ---------------------------------------------------------------- 7. আপনার জন্য কোর্স (small, last)

@Composable
fun RecommendationsWidget(
    widget: Widget<List<RecommendedCourse>>,
    onExplore: () -> Unit,
    onRetry: () -> Unit,
    onOpenCourse: (slug: String) -> Unit = { onExplore() },
) {
    when (widget) {
        Widget.Hidden, Widget.Loading -> Unit
        Widget.Failed -> WidgetError(Str.HomeForYou.t(), onRetry)
        is Widget.Ready -> {
            if (widget.data.isEmpty()) return
            Column {
                SectionHeader(
                    title = Str.HomeForYou.t(),
                    actionLabel = Str.CommonSeeAll.t(),
                    onAction = onExplore,
                    modifier = WidgetPadding,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SsSpace.s4),
                    horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
                ) {
                    items(widget.data, key = { it.id }) { course -> SmallCourseCard(course, onClick = { onOpenCourse(course.slug) }) }
                }
            }
        }
    }
}

/** Small Course Card: cover colour band · code · 2-line title · teacher (verified) · ৳ price with strikethrough. */
@Composable
private fun SmallCourseCard(
    course: RecommendedCourse,
    onClick: () -> Unit,
) {
    val palette = SsTheme.palette
    val price = Str.CoursePrice.t("price" to course.price)
    SsCard(modifier = Modifier.width(220.dp), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(hexColor(course.coverColor, palette.primary)))
        Column(Modifier.padding(SsSpace.s3), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
            course.courseCode?.let { SsChip(text = it, tone = StatusTone.Primary) }
            Text(
                course.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Text(course.sellerName, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                if (course.sellerVerified) {
                    Icon(
                        Lucide.BadgeCheck,
                        contentDescription = Str.CourseVerified.t(),
                        tint = palette.success,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                Text(price, style = MaterialTheme.typography.titleSmall.numeric, color = palette.text)
                course.compareAtPrice?.takeIf { it > course.price }?.let {
                    Text(
                        Str.CoursePrice.t("price" to it),
                        style = MaterialTheme.typography.bodySmall.numeric,
                        color = palette.muted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            when (course.compatibility) {
                Compatibility.Match -> SsChip(text = Str.CompatMatch.t(), tone = StatusTone.Success)
                Compatibility.Mismatch -> SsChip(text = Str.CompatMismatch.t(), tone = StatusTone.Warning)
                Compatibility.General -> Unit
            }
        }
    }
}
