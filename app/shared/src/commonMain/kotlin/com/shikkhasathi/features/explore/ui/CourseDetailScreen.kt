package com.shikkhasathi.features.explore.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.durationText
import com.shikkhasathi.core.model.Compatibility
import com.shikkhasathi.core.model.CourseDetail
import com.shikkhasathi.core.model.PublicLesson
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.explore.presentation.CourseCta
import com.shikkhasathi.features.explore.presentation.CourseDetailViewModel
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** Course page: details, curriculum and the one primary CTA (buy / continue) pinned at the bottom. */
@Composable
fun CourseDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onCheckout: (courseId: String, slug: String) -> Unit,
    onLearn: (courseId: String, lessonId: String?) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "course:$slug") { koin.get<CourseDetailViewModel> { parametersOf(slug) } }
    val state by vm.state.collectAsStateWithLifecycle()
    // Coming back from checkout: "কিনুন" must turn into "পড়া শুরু করুন" without a manual refresh.
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
    val palette = SsTheme.palette
    Box(Modifier.fillMaxSize()) {
        when (val course = state.course) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(24.dp))
                    SkeletonBox(Modifier.fillMaxWidth().height(64.dp))
                    SkeletonCard()
                    SkeletonCard()
                }
            is LoadState.Failed ->
                Column {
                    BackRow(onBack, wishlisted = null, onToggleWishlist = {})
                    ErrorState(message = course.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
                }
            is LoadState.Ready ->
                Column(Modifier.fillMaxSize()) {
                    BackRow(onBack, wishlisted = state.wishlisted, onToggleWishlist = vm::toggleWishlist)
                    CourseBody(course.data, modifier = Modifier.weight(1f))
                    state.cta?.let { cta ->
                        Surface(color = palette.surface, shadowElevation = 4.dp) {
                            CtaBar(course.data, cta, onCheckout, onLearn)
                        }
                    }
                }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun BackRow(
    onBack: () -> Unit,
    wishlisted: Boolean?,
    onToggleWishlist: () -> Unit,
) {
    val palette = SsTheme.palette
    Row(Modifier.fillMaxWidth().padding(horizontal = SsSpace.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = Str.CommonBack.t(), tint = palette.text) }
        Box(Modifier.weight(1f))
        if (wishlisted != null) {
            val label = if (wishlisted) Str.CourseWishlistRemove.t() else Str.CourseWishlistAdd.t()
            IconButton(onClick = onToggleWishlist, modifier = Modifier.semantics { stateDescription = label }) {
                Icon(Lucide.Heart, contentDescription = label, tint = if (wishlisted) palette.danger else palette.muted)
            }
        }
    }
}

@Composable
private fun CourseBody(
    course: CourseDetail,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    var expandedSection by rememberSaveable { mutableStateOf(course.sections.firstOrNull()?.id) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, bottom = SsSpace.s6),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
    ) {
        item(key = "head") { CourseHead(course) }
        if (course.learnings.isNotEmpty()) {
            item(key = "learn") {
                SsCard(Modifier.fillMaxWidth()) {
                    SectionTitle(Str.CourseLearnings.t())
                    course.learnings.forEach { BulletRow(Lucide.CircleCheck, palette.success, it) }
                }
            }
        }
        item(key = "curriculumTitle") {
            Column {
                SectionTitle(Str.CourseCurriculum.t())
                Text(
                    Str.CourseLessonsLength.t("n" to course.lessonCount, "length" to durationText(course.totalDurationSec)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        }
        items(course.sections, key = { it.id }) { section ->
            val open = expandedSection == section.id
            SsCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { expandedSection = if (open) null else section.id }
                        .padding(horizontal = SsSpace.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(section.title, style = MaterialTheme.typography.titleSmall, color = palette.text, modifier = Modifier.weight(1f))
                    Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, contentDescription = null, tint = palette.muted)
                }
                if (open) {
                    section.chapters.forEach { chapter ->
                        HorizontalDivider(color = palette.border)
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.muted,
                            modifier = Modifier.padding(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s3),
                        )
                        chapter.lessons.forEach { PublicLessonRow(it) }
                    }
                }
            }
        }
        item(key = "resources") { ResourcesCard(course) }
        if (course.notFor.isNotEmpty()) {
            item(key = "notFor") {
                SsCard(Modifier.fillMaxWidth(), container = palette.surface2) {
                    SectionTitle(Str.CourseNotFor.t())
                    course.notFor.forEach { BulletRow(Lucide.Ban, palette.muted, it) }
                }
            }
        }
        if (course.faq.isNotEmpty()) {
            item(key = "faq") {
                Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    SectionTitle(Str.CourseFaq.t())
                    course.faq.forEach { f ->
                        Text(f.q, style = MaterialTheme.typography.titleSmall, color = palette.text)
                        Text(f.a, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                    }
                }
            }
        }
        item(key = "disclaimer") {
            Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Text(Str.CourseExamDisclaimer.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                Text(Str.BrandDisclaimerUniversity.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
    }
}

@Composable
private fun CourseHead(course: CourseDetail) {
    val palette = SsTheme.palette
    val examType = Str.fromKey("examType.${course.examType}")?.t() ?: course.examType
    Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
            course.courseCode?.let { SsChip(text = it, tone = StatusTone.Primary) }
            SsChip(text = examType)
            course.institutionShort?.let { SsChip(text = it) }
        }
        Text(
            course.title,
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text,
            modifier = Modifier.semantics { heading() }
        )
        if (course.shortDescription.isNotBlank()) {
            Text(
                course.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }
        TeacherLine(course.seller.name, course.seller.verified)
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3), verticalAlignment = Alignment.CenterVertically) {
            if (course.ratingCount > 0) RatingText(course.ratingAvg, course.ratingCount)
            Text(Str.CourseStudents.t("n" to course.enrollmentCount), style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        // Compatibility is the purposeful detail of this page (CLAUDE.md §7): which syllabus it was made for.
        if (course.targets.isNotEmpty() || course.compatibility != Compatibility.General) {
            SsCard(Modifier.fillMaxWidth()) {
                CompatibilityBadge(course.compatibility)
                course.targets.take(MAX_TARGETS).forEach { target ->
                    Text(
                        listOfNotNull(
                            target.label,
                            target.courseCode,
                            target.semester?.let { Str.HomeSemester.t("n" to it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text,
                        modifier = Modifier.padding(top = SsSpace.s1),
                    )
                }
            }
        }
    }
}

private const val MAX_TARGETS = 4

@Composable
private fun PublicLessonRow(lesson: PublicLesson) {
    val palette = SsTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = SsSpace.s4, vertical = SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
    ) {
        Icon(
            if (lesson.type == "video") Lucide.CirclePlay else Lucide.FileText,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(lesson.title, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f), maxLines = 2)
        if (lesson.isImportant) {
            Icon(
                Lucide.Star,
                contentDescription = Str.LessonImportant.t(),
                tint = palette.warning,
                modifier = Modifier.size(16.dp)
            )
        }
        if (lesson.isPreview) SsChip(text = Str.LessonPreview.t(), tone = StatusTone.Success)
        if (lesson.durationSec >
            0
        ) {
            Text(durationText(lesson.durationSec).t(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
    }
}

@Composable
private fun ResourcesCard(course: CourseDetail) {
    val r = course.resources
    val palette = SsTheme.palette
    SsCard(Modifier.fillMaxWidth()) {
        SectionTitle(Str.CourseResources.t())
        val rows =
            listOfNotNull(
                Str.ResourceVideos.t("n" to r.videos).takeIf { r.videos > 0 },
                Str.ResourceNotes.t("n" to r.notes).takeIf { r.notes > 0 },
                r.previousQuestionYears.takeIf { it.isNotEmpty() }?.let { years ->
                    Str.ResourcePreviousQuestions.t("years" to years.joinToString(", ") { UiText.Count(it).t() })
                },
                Str.ResourceMockTests.t("n" to r.mockTests).takeIf { r.mockTests > 0 },
                Str.ResourceFlashcards.t("n" to r.flashcards).takeIf { r.flashcards > 0 },
            )
        rows.forEach { BulletRow(Lucide.CircleCheck, palette.primary, it) }
    }
}

@Composable
private fun CtaBar(
    course: CourseDetail,
    cta: CourseCta,
    onCheckout: (String, String) -> Unit,
    onLearn: (String, String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(SsSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        when (cta) {
            is CourseCta.Buy -> {
                Column(Modifier.weight(1f)) { PriceText(course.price, course.compareAtPrice) }
                SsButton(
                    text = if (cta.free) Str.CourseEnrollFree.t() else Str.CourseBuy.t(),
                    onClick = { onCheckout(cta.courseId, cta.slug) },
                )
            }
            is CourseCta.Continue ->
                SsButton(
                    text = if (cta.started) Str.HomeResume.t() else Str.CourseStart.t(),
                    leadingIcon = Lucide.CirclePlay,
                    onClick = { onLearn(cta.courseId, cta.lessonId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            is CourseCta.Preview ->
                SsButton(
                    text = Str.CoursePreviewOwn.t(),
                    onClick = { onLearn(cta.courseId, cta.lessonId) },
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = SsTheme.palette.text,
        modifier = Modifier.padding(bottom = SsSpace.s2).semantics { heading() },
    )
}

@Composable
private fun BulletRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        Modifier.padding(vertical = SsSpace.s1),
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SsTheme.palette.text)
    }
}
