package com.shikkhasathi.features.home.ui

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.WifiOff
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.ContinueWidget
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.ui.AssignmentSheet
import com.shikkhasathi.features.classroom.ui.ConnectClassroomHost
import com.shikkhasathi.features.home.presentation.ClassroomCard
import com.shikkhasathi.features.home.presentation.HomeUiState
import com.shikkhasathi.features.home.presentation.HomeViewModel
import kotlinx.coroutines.delay
import org.koin.compose.getKoin

private const val TICK_MS = 60_000L

/** Where the home widgets lead. One object so the screen signature stays readable. */
data class HomeNavigation(
    val openDrawer: () -> Unit,
    val openNotifications: () -> Unit,
    val openAssignments: () -> Unit,
    val openCalendar: () -> Unit,
    val openExamMode: () -> Unit,
    val openMyCourses: (ContinueWidget) -> Unit,
    val exploreCourses: () -> Unit,
    /** A recommended course → its page (`/c/:slug`). */
    val openCourse: (slug: String) -> Unit = { exploreCourses() },
)

/**
 * Home = unified academic dashboard (CLAUDE.md §0A), widgets in this exact order:
 * header · সামনের অ্যাসাইনমেন্ট · নতুন আপডেট · exam countdown · পড়া চালিয়ে যান · এই সপ্তাহ · আপনার জন্য কোর্স.
 * Cached data paints first; pull-to-refresh syncs Classroom and reloads.
 */
@Composable
fun HomeScreen(navigation: HomeNavigation) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<HomeViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        while (true) {
            delay(TICK_MS)
            vm.tick()
        }
    }
    HomeContent(
        state = state,
        navigation = navigation,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onConnected = vm::refresh,
        onMarkAllFeedRead = vm::markAllFeedRead,
        onOpenFeedItem = vm::openFeedItem,
        onMessageShown = vm::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    navigation: HomeNavigation,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onConnected: () -> Unit,
    onMarkAllFeedRead: () -> Unit,
    onOpenFeedItem: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    var sheet by remember { mutableStateOf<AssignmentRowUi?>(null) }
    val uri = LocalUriHandler.current
    val error = state.error

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = SsSpace.s8),
                verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
            ) {
                item(key = "header") { HomeHeader(state, navigation, onRefresh) }
                when {
                    error != null ->
                        item(key = "error") {
                            ErrorState(message = error.t(), onRetry = onRetry, modifier = Modifier.padding(horizontal = SsSpace.s4))
                        }
                    state.loading -> item(key = "skeleton") { HomeSkeleton() }
                    else -> {
                        if (state.offline) item(key = "offline") { OfflineBanner() }
                        if (state.classroomCard != ClassroomCard.None) {
                            item(key = "connect") {
                                ConnectClassroomHost(onConnected = onConnected, modifier = Modifier.padding(horizontal = SsSpace.s4))
                            }
                        }
                        item(key = "dueSoon") {
                            DueSoonWidget(state.dueSoon, onSeeAll = navigation.openAssignments, onOpen = { sheet = it }, onRetry = onRetry)
                        }
                        item(key = "feed") {
                            FeedWidget(
                                widget = state.feed,
                                onMarkAllRead = onMarkAllFeedRead,
                                onOpen = { row ->
                                    onOpenFeedItem(row.id)
                                    row.link?.let { link -> runCatching { uri.openUri(link) } }
                                },
                                onRetry = onRetry,
                            )
                        }
                        item(key = "exam") { ExamWidgetCard(state.exam, onOpenExamMode = navigation.openExamMode, onRetry = onRetry) }
                        item(key = "continue") {
                            ContinueLearningWidget(
                                state.continueLearning,
                                onResume = navigation.openMyCourses,
                                onExplore = navigation.exploreCourses,
                                onRetry = onRetry,
                            )
                        }
                        item(key = "week") { WeekWidget(state.week, onOpenCalendar = navigation.openCalendar, onRetry = onRetry) }
                        item(key = "recommendations") {
                            RecommendationsWidget(
                                state.recommendations,
                                onExplore = navigation.exploreCourses,
                                onOpenCourse = navigation.openCourse,
                                onRetry = onRetry,
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
    sheet?.let { AssignmentSheet(row = it, onDismiss = { sheet = null }) }
}

/** Greeting, academic chip, sync status + refresh, drawer and bell (with unread count). */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    navigation: HomeNavigation,
    onRefresh: () -> Unit,
) {
    val palette = SsTheme.palette
    val header = state.header
    Column(Modifier.fillMaxWidth().padding(start = SsSpace.s1, end = SsSpace.s1, top = SsSpace.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = navigation.openDrawer) {
                Icon(Lucide.Menu, contentDescription = Str.NavOpenMenu.t(), tint = palette.text)
            }
            Text(
                Str.HomeGreeting.t("name" to (header.firstName ?: Str.HomeGreetingFallbackName.t())),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            val unread = header.unreadNotifications
            IconButton(onClick = navigation.openNotifications) {
                BadgedBox(
                    badge = {
                        if (unread > 0) {
                            Badge(containerColor = palette.danger, contentColor = palette.onPrimary) {
                                Text(UiText.Count(unread.coerceAtMost(MAX_BADGE)).t())
                            }
                        }
                    },
                ) {
                    Icon(
                        Lucide.Bell,
                        contentDescription = if (unread > 0) Str.HomeBellUnread.t("n" to unread) else Str.NavNotifications.t(),
                        tint = palette.text,
                    )
                }
            }
        }
        Column(
            Modifier.padding(start = SsSpace.s3 + SsSpace.s8, end = SsSpace.s3),
            verticalArrangement = Arrangement.spacedBy(SsSpace.s1),
        ) {
            if (header.academicParts.isNotEmpty()) {
                SsChip(text = header.academicParts.joinToString(" · ") { it.t() }, icon = Lucide.GraduationCap, tone = StatusTone.Neutral)
            }
            state.syncLabel?.let { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SsSpace.s1),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(16.dp))
                    Text(
                        label.t(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
    }
}

private const val MAX_BADGE = 99

@Composable
private fun OfflineBanner() {
    val palette = SsTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SsSpace.s4)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
    ) {
        Icon(Lucide.WifiOff, contentDescription = null, tint = palette.warningFg, modifier = Modifier.size(16.dp))
        Text(Str.HomeOffline.t(), style = MaterialTheme.typography.bodySmall, color = palette.warningFg)
    }
}

/** Mirrors the dashboard layout so nothing jumps when data arrives. */
@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(horizontal = SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(24.dp))
        SkeletonCard()
        SkeletonCard()
        SkeletonBox(Modifier.fillMaxWidth().height(120.dp))
        SkeletonCard()
    }
}
