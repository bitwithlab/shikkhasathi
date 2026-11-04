package com.shikkhasathi.features.notifications.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Timer
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.features.notifications.presentation.NotificationCategory
import com.shikkhasathi.features.notifications.presentation.NotificationRowUi
import com.shikkhasathi.features.notifications.presentation.NotificationsUiState
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

private const val PREFETCH_DISTANCE = 4

/** Notification center (drawer → নোটিফিকেশন, home bell). Tapping a row marks it read and opens its link. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<NotificationsViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    NotificationsContent(
        state = state,
        onBack = onBack,
        onOpen = { row ->
            vm.open(row.id)
            row.link?.let(onOpenLink)
        },
        onMarkAllRead = vm::markAllRead,
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onMessageShown = vm::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkAllRead: () -> Unit,
    onLoadMore: () -> Unit,
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
    val error = state.error
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last =
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) onLoadMore() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(title = Str.NavNotifications.t(), onBack = onBack, modifier = Modifier.weight(1f))
                if (state.unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead, modifier = Modifier.padding(end = SsSpace.s2)) {
                        Text(Str.HomeMarkAllRead.t(), color = palette.primaryFg, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            when {
                state.loading ->
                    Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                        repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(68.dp), shape = SsShape.control) }
                    }
                error != null -> ErrorState(message = error.t(), onRetry = onRetry, modifier = Modifier.padding(SsSpace.s4))
                state.isEmpty ->
                    EmptyState(
                        icon = Lucide.BellOff,
                        title = Str.NotificationsEmptyTitle.t(),
                        body = Str.NotificationsEmptyBody.t(),
                        modifier = Modifier.padding(horizontal = SsSpace.s4),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = SsSpace.s2, end = SsSpace.s2, bottom = SsSpace.s8),
                        ) {
                            items(state.items, key = { it.id }) { row -> NotificationRow(row, onClick = { onOpen(row) }) }
                            if (state.loadingMore) {
                                item(key = "more") {
                                    Box(Modifier.fillMaxWidth().padding(SsSpace.s4), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), color = palette.primary, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

private fun NotificationCategory.icon(): ImageVector =
    when (this) {
        NotificationCategory.Assignment -> Lucide.ClipboardList
        NotificationCategory.Material -> Lucide.FileText
        NotificationCategory.Announcement -> Lucide.Megaphone
        NotificationCategory.Grade -> Lucide.Star
        NotificationCategory.Reminder -> Lucide.Timer
        NotificationCategory.Exam -> Lucide.CalendarClock
        NotificationCategory.Course -> Lucide.BookOpen
        NotificationCategory.Order -> Lucide.Receipt
        NotificationCategory.Other -> Lucide.Bell
    }

@Composable
private fun NotificationRow(
    row: NotificationRowUi,
    onClick: () -> Unit,
) {
    val palette = SsTheme.palette
    val ago = row.timeAgo.t()
    val unreadLabel = Str.GcUnread.t()
    val description = listOfNotNull(unreadLabel.takeIf { row.unread }, row.title, row.body, ago).joinToString(", ")
    val (tileBg, tileFg) =
        when (row.category) {
            NotificationCategory.Reminder -> palette.warningSoft to palette.warningFg
            NotificationCategory.Grade -> palette.successSoft to palette.successFg
            else -> palette.primarySoft to palette.primaryFg
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(if (row.unread) palette.primarySoft.copy(alpha = 0.45f) else Color.Transparent, SsShape.control)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = SsSpace.s3, vertical = SsSpace.s3),
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(tileBg, SsShape.control), contentAlignment = Alignment.Center) {
            Icon(row.category.icon(), contentDescription = null, tint = tileFg, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.body,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(ago, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
        if (row.unread) Box(Modifier.padding(top = SsSpace.s2).size(8.dp).background(palette.primary, SsShape.chip))
    }
}
