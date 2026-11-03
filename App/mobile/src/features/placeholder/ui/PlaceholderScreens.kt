package com.shikkhasathi.features.placeholder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ClipboardCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Presentation
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.shared.ui.ScreenHeader

/**
 * Destinations whose features are not built yet (Phase 1). Each one is a real screen with its final title
 * and a specific Bangla empty state — replaced feature by feature (docs/PROGRESS.md).
 */
object PlaceholderScreens {
    @Composable
    fun MyCourses(onExplore: () -> Unit) =
        Placeholder(Str.NavCourses, Lucide.BookOpen, Str.CoursesEmptyTitle, Str.CoursesEmptyBody, action = Str.NavExplore to onExplore)

    @Composable
    fun ExamMode() = Placeholder(Str.NavExam, Lucide.CalendarClock, Str.ExamEmptyTitle, Str.ExamEmptyBody)

    @Composable
    fun Explore(onBack: () -> Unit) = Placeholder(Str.NavExplore, Lucide.Search, Str.ExploreEmptyTitle, Str.ExploreEmptyBody, onBack)

    @Composable
    fun MockTests(onBack: () -> Unit) =
        Placeholder(Str.NavMockTests, Lucide.ClipboardCheck, Str.MockTestsEmptyTitle, Str.MockTestsEmptyBody, onBack)

    @Composable
    fun Notes(onBack: () -> Unit) = Placeholder(Str.NavNotes, Lucide.NotebookPen, Str.NotesEmptyTitle, Str.NotesEmptyBody, onBack)

    @Composable
    fun Requests(onBack: () -> Unit) =
        Placeholder(Str.NavRequests, Lucide.MessageSquarePlus, Str.RequestsEmptyTitle, Str.RequestsEmptyBody, onBack)

    @Composable
    fun Settings(onBack: () -> Unit) = Placeholder(Str.NavSettings, Lucide.Settings, Str.SettingsEmptyTitle, Str.SettingsEmptyBody, onBack)

    @Composable
    fun TeacherStudio(onBack: () -> Unit) =
        Placeholder(Str.NavTeacherStudio, Lucide.Presentation, Str.StudioEmptyTitle, Str.StudioEmptyBody, onBack)
}

@Composable
private fun Placeholder(
    title: Str,
    icon: ImageVector,
    emptyTitle: Str,
    emptyBody: Str,
    onBack: (() -> Unit)? = null,
    action: Pair<Str, () -> Unit>? = null,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = title.t(), onBack = onBack)
        EmptyState(
            icon = icon,
            title = emptyTitle.t(),
            body = emptyBody.t(),
            actionLabel = action?.first?.t(),
            onAction = action?.second,
            modifier = Modifier.padding(horizontal = SsSpace.s4),
        )
    }
}
