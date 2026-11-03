package com.shikkhasathi.features.classroom.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.School
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.classroom.presentation.ClassRowUi
import com.shikkhasathi.features.classroom.presentation.ClassroomUiState
import com.shikkhasathi.features.classroom.presentation.ClassroomViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

/** Bottom-bar ক্লাসরুম tab: every Google Classroom class, with pending work and the next deadline. */
@Composable
fun ClassroomScreen(
    onOpenClass: (id: String, name: String) -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<ClassroomViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    ClassroomContent(state, onOpenClass, onOpenAssignments, onOpenCalendar, vm::refresh, vm::retry, vm::messageShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomContent(
    state: ClassroomUiState,
    onOpenClass: (id: String, name: String) -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenCalendar: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val palette = SsTheme.palette
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, bottom = SsSpace.s8),
                verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
            ) {
                item(key = "title") { ScreenHeader(title = Str.NavClassroom.t()) }
                state.syncLabel?.let { label ->
                    item(key = "sync") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                            Text(
                                label.t(),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                                Icon(
                                    Lucide.RefreshCw,
                                    contentDescription = Str.GcRefresh.t(),
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                state.connect?.let { item(key = "connect") { ConnectClassroomHost(onConnected = onRetry) } }
                if (state.connect == null) {
                    item(key = "shortcuts") {
                        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                            ShortcutCard(Str.NavAssignments.t(), Lucide.ClipboardList, onOpenAssignments, Modifier.weight(1f))
                            ShortcutCard(Str.NavCalendar.t(), Lucide.CalendarDays, onOpenCalendar, Modifier.weight(1f))
                        }
                    }
                }
                when (val classes = state.classes) {
                    LoadState.Loading ->
                        items(
                            3,
                            key = { "skeleton-$it" }
                        ) { SkeletonBox(Modifier.fillMaxWidth().height(88.dp), shape = SsShape.card) }
                    is LoadState.Failed -> item(key = "error") { ErrorState(message = classes.message.t(), onRetry = onRetry) }
                    is LoadState.Ready ->
                        if (classes.data.isEmpty() && state.connect == null) {
                            item(key = "empty") {
                                EmptyState(
                                    icon = Lucide.School,
                                    title = Str.ClassroomClassesEmptyTitle.t(),
                                    body = Str.ClassroomClassesEmptyBody.t(),
                                    actionLabel = Str.GcRefresh.t(),
                                    onAction = onRefresh,
                                )
                            }
                        } else {
                            items(classes.data, key = { it.id }) { row -> ClassCard(row, onClick = { onOpenClass(row.id, row.name) }) }
                        }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

@Composable
private fun ShortcutCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    SsCard(modifier = modifier.heightIn(min = 56.dp), onClick = onClick, contentPadding = PaddingValues(SsSpace.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
            Icon(icon, contentDescription = null, tint = palette.primaryFg, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = palette.text, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
        }
    }
}

/** One class: colour stripe, name, section/teacher, pending + new counts, next deadline. */
@Composable
private fun ClassCard(
    row: ClassRowUi,
    onClick: () -> Unit,
) {
    val palette = SsTheme.palette
    val pending = Str.ClassroomPendingCount.t("n" to row.pendingCount)
    val unread = Str.HomeNewCount.t("n" to row.unreadCount)
    val next = row.nextDue?.let { Str.ClassroomNextDue.t("when" to it) }
    val description =
        listOfNotNull(
            row.name,
            row.subtitle,
            row.teachers,
            pending.takeIf { row.pendingCount > 0 },
            unread.takeIf { row.unreadCount > 0 },
            next,
        ).joinToString(", ")
    SsCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(hexColor(row.color, palette.primary)))
            Column(Modifier.weight(1f).padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                listOfNotNull(row.subtitle, row.teachers).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2), modifier = Modifier.padding(top = SsSpace.s1)) {
                    if (row.pendingCount > 0) SsChip(text = pending, tone = StatusTone.Warning)
                    if (row.unreadCount > 0) SsChip(text = unread, tone = StatusTone.Primary)
                }
                next?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted) }
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = SsSpace.s3).size(18.dp),
            )
        }
    }
}
