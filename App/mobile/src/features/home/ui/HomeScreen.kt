package com.shikkhasathi.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.School
import com.composables.icons.lucide.ShieldCheck
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.home.presentation.HomeUiState
import com.shikkhasathi.features.home.presentation.HomeViewModel
import org.koin.compose.getKoin

/**
 * Home = unified academic dashboard (CLAUDE.md §0A). Phase 1 renders the header and the not-connected
 * Classroom card; the due-soon list, feed, countdown and week strip slot in below as their features land.
 */
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onConnectClassroom: () -> Unit,
    onExploreCourses: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<HomeViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, onOpenDrawer, onOpenNotifications, onConnectClassroom, onExploreCourses)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onConnectClassroom: () -> Unit,
    onExploreCourses: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = SsSpace.s6),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
    ) {
        HomeHeader(state, onOpenDrawer, onOpenNotifications)
        if (!state.classroomConnected) {
            ClassroomConnectCard(onConnect = onConnectClassroom, modifier = Modifier.padding(horizontal = SsSpace.s4))
        }
        Column(Modifier.padding(horizontal = SsSpace.s4)) {
            Text(
                Str.HomeCoursesTitle.t(),
                style = MaterialTheme.typography.titleMedium,
                color = SsTheme.palette.text,
                modifier = Modifier.semantics { heading() },
            )
            EmptyState(
                icon = Lucide.BookOpen,
                title = Str.HomeCoursesEmptyTitle.t(),
                body = Str.HomeCoursesEmptyBody.t(),
                actionLabel = Str.NavExplore.t(),
                onAction = onExploreCourses,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(Modifier.fillMaxWidth().padding(start = SsSpace.s1, end = SsSpace.s1, top = SsSpace.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Lucide.Menu, contentDescription = Str.NavOpenMenu.t(), tint = palette.text)
            }
            Text(
                Str.HomeGreeting.t("name" to (state.firstName ?: Str.HomeGreetingFallbackName.t())),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onOpenNotifications) {
                Icon(Lucide.Bell, contentDescription = Str.NavNotifications.t(), tint = palette.text)
            }
        }
        state.academicLine?.let {
            SsChip(
                text = it,
                icon = Lucide.GraduationCap,
                tone = StatusTone.Neutral,
                modifier = Modifier.padding(start = SsSpace.s3 + SsSpace.s8, top = SsSpace.s1),
            )
        }
    }
}

/** Calm, prominent not-connected card — never an empty page (CLAUDE.md §0A). */
@Composable
private fun ClassroomConnectCard(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    SsCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(SsSpace.s5)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s4), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).background(palette.primarySoft, SsShape.control), contentAlignment = Alignment.Center) {
                Icon(Lucide.School, contentDescription = null, tint = palette.primaryFg, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Text(Str.HomeClassroomConnectTitle.t(), style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text(Str.HomeClassroomConnectBody.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = SsSpace.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = SsSpace.s3),
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.successFg, modifier = Modifier.size(16.dp))
                Text(Str.HomeClassroomConnectPrivacy.t(), style = MaterialTheme.typography.bodySmall, color = palette.successFg)
            }
            SsButton(text = Str.HomeClassroomConnectCta.t(), onClick = onConnect)
        }
    }
}
