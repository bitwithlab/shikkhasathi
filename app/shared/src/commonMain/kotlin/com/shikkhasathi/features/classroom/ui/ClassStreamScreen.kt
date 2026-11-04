package com.shikkhasathi.features.classroom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.StreamKind
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.ClassStreamData
import com.shikkhasathi.features.classroom.presentation.ClassStreamUiState
import com.shikkhasathi.features.classroom.presentation.ClassStreamViewModel
import com.shikkhasathi.features.classroom.presentation.StreamRowUi
import com.shikkhasathi.features.classroom.presentation.StreamTab
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** Per-class screen: স্ট্রিম · অ্যাসাইনমেন্ট · ম্যাটেরিয়াল. */
@Composable
fun ClassStreamScreen(
    courseId: String,
    title: String,
    onBack: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "stream-$courseId") { koin.get<ClassStreamViewModel> { parametersOf(courseId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    ClassStreamContent(state, title, onBack, vm::selectTab, vm::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassStreamContent(
    state: ClassStreamUiState,
    fallbackTitle: String,
    onBack: () -> Unit,
    onSelectTab: (StreamTab) -> Unit,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    var sheet by remember { mutableStateOf<AssignmentRowUi?>(null) }
    val data = (state.content as? LoadState.Ready)?.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = data?.course?.name ?: fallbackTitle, onBack = onBack)
        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = palette.bg,
            // Default PrimaryIndicator uses colorScheme.primary = the indigo token.
            contentColor = palette.primaryFg,
        ) {
            StreamTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(tab.label.t(), style = MaterialTheme.typography.titleSmall) },
                    selectedContentColor = palette.primaryFg,
                    unselectedContentColor = palette.muted,
                )
            }
        }
        when (val content = state.content) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    repeat(3) { SkeletonCard() }
                }
            is LoadState.Failed -> ErrorState(message = content.message.t(), onRetry = onRetry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready -> TabBody(state.tab, content.data, onOpenAssignment = { sheet = it })
        }
    }
    sheet?.let { AssignmentSheet(row = it, onDismiss = { sheet = null }) }
}

@Composable
private fun TabBody(
    tab: StreamTab,
    data: ClassStreamData,
    onOpenAssignment: (AssignmentRowUi) -> Unit,
) {
    val padding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s4, bottom = SsSpace.s8)
    when (tab) {
        StreamTab.Stream ->
            if (data.stream.isEmpty()) {
                EmptyState(
                    icon = Lucide.Megaphone,
                    title = Str.ClassroomStreamEmptyTitle.t(),
                    body = Str.ClassroomStreamEmptyBody.t(),
                    modifier = Modifier.padding(horizontal = SsSpace.s4),
                )
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                    items(data.stream, key = { it.id }) { row -> StreamPost(row, onOpenAssignment) }
                }
            }
        StreamTab.Assignments ->
            if (data.assignments.isEmpty()) {
                EmptyState(
                    icon = Lucide.FolderOpen,
                    title = Str.ClassroomAssignmentsEmptyTitle.t(),
                    body = Str.ClassroomStreamEmptyBody.t(),
                    modifier = Modifier.padding(horizontal = SsSpace.s4),
                )
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    items(
                        data.assignments,
                        key = { it.id }
                    ) { row -> AssignmentRow(row, onClick = { onOpenAssignment(row) }, showCourse = false) }
                }
            }
        StreamTab.Materials ->
            if (data.materials.isEmpty()) {
                EmptyState(
                    icon = Lucide.FolderOpen,
                    title = Str.ClassroomMaterialsEmptyTitle.t(),
                    body = Str.ClassroomMaterialsEmptyBody.t(),
                    modifier = Modifier.padding(horizontal = SsSpace.s4),
                )
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    items(data.materials, key = { it.key }) { m ->
                        MaterialRow(m.material, subtitle = listOfNotNull(m.fromTitle, m.postedAgo?.t()).joinToString(" · "))
                    }
                }
            }
    }
}

/** A stream post: kind + author + time, title, body, attachments; assignments open the sheet. */
@Composable
private fun StreamPost(
    row: StreamRowUi,
    onOpenAssignment: (AssignmentRowUi) -> Unit,
) {
    val palette = SsTheme.palette
    val uri = LocalUriHandler.current
    val assignment = row.assignment
    if (row.kind == StreamKind.Assignment && assignment != null) {
        AssignmentRow(assignment, onClick = { onOpenAssignment(assignment) }, showCourse = false)
        return
    }
    SsCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                Icon(
                    if (row.kind == StreamKind.Material) Lucide.FileText else Lucide.Megaphone,
                    contentDescription = null,
                    tint = palette.primaryFg,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    listOfNotNull(row.kindLabel.t(), row.author, row.postedAgo?.t()).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.title.isNotBlank()) Text(row.title, style = MaterialTheme.typography.titleSmall, color = palette.text)
            row.body?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.text) }
            row.materials.forEach { MaterialRow(it) }
            row.link?.let { link ->
                SsButton(
                    text = Str.GcOpenInClassroom.t(),
                    onClick = { runCatching { uri.openUri(link) } },
                    variant = SsButtonVariant.Ghost,
                    leadingIcon = Lucide.ExternalLink,
                )
            }
        }
    }
}
