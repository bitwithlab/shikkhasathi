package com.shikkhasathi.features.classroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.ShieldAlert
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Youtube
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.components.StatusChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.FeedKind
import com.shikkhasathi.core.model.Material
import com.shikkhasathi.core.model.MaterialKind
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.core.model.chipSpec
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectCopy
import com.shikkhasathi.features.classroom.presentation.ConnectPhase
import com.shikkhasathi.features.classroom.presentation.ConnectProblem
import com.shikkhasathi.features.classroom.presentation.FeedRowUi
import com.shikkhasathi.features.classroom.presentation.Urgency
import org.koin.compose.getKoin

/** Stripe/text colour of an urgency level. Neutral uses the course colour so rows stay scannable by class. */
@Composable
fun Urgency.color(courseColor: String): Color {
    val palette = SsTheme.palette
    return when (this) {
        Urgency.Danger -> palette.danger
        Urgency.Warning -> palette.warning
        Urgency.Success -> palette.success
        Urgency.Neutral -> hexColor(courseColor, palette.primary)
    }
}

private fun Urgency.tone(): StatusTone =
    when (this) {
        Urgency.Danger -> StatusTone.Danger
        Urgency.Warning -> StatusTone.Warning
        Urgency.Success -> StatusTone.Success
        Urgency.Neutral -> StatusTone.Neutral
    }

/**
 * Assignment Row (DESIGN_SYSTEM.md): urgency/course stripe · title · course · due time · time left · status chip.
 * The whole row is one 48dp+ target and reads as one sentence for TalkBack.
 */
@Composable
fun AssignmentRow(
    row: AssignmentRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCourse: Boolean = true,
) {
    val palette = SsTheme.palette
    val due = row.dueLabel.t()
    val left = row.timeLeft?.t()
    val status =
        row.status
            .chipSpec()
            .label
            .t()
    val description = listOfNotNull(row.title, row.courseName.takeIf { showCourse }, due, left, status).joinToString(", ")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 64.dp)
                .clip(SsShape.control)
                .background(palette.surface)
                .border(1.dp, if (row.urgency == Urgency.Danger) palette.danger.copy(alpha = 0.3f) else palette.border, SsShape.control)
                .clickable(role = Role.Button, onClickLabel = Str.GcOpenInClassroom.t(), onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(row.urgency.color(row.courseColor)))
        Column(
            Modifier.weight(1f).padding(start = SsSpace.s3, top = SsSpace.s3, bottom = SsSpace.s3, end = SsSpace.s2),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (showCourse) {
                Text(
                    row.courseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                Icon(Lucide.Clock, contentDescription = null, tint = palette.muted, modifier = Modifier.size(14.dp))
                Text(
                    due,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.urgency == Urgency.Danger) palette.dangerFg else palette.muted,
                    maxLines = 1,
                )
                if (left != null) SsChip(text = left, tone = row.urgency.tone())
            }
        }
        StatusChip(row.status, modifier = Modifier.padding(end = SsSpace.s3))
    }
}

fun FeedKind.icon(): ImageVector =
    when (this) {
        FeedKind.Assignment -> Lucide.ClipboardList
        FeedKind.Material -> Lucide.FileText
        FeedKind.Announcement -> Lucide.Megaphone
        FeedKind.Graded -> Lucide.Star
    }

/** Feed Item (DESIGN_SYSTEM.md): kind icon tile · title · class · time · unread dot. */
@Composable
fun FeedRow(
    row: FeedRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    val kind = row.kindLabel.t()
    val ago = row.timeAgo.t()
    val unread = Str.GcUnread.t()
    val description = listOfNotNull(unread.takeIf { row.unread }, kind, row.title, row.courseName, ago).joinToString(", ")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(SsShape.control)
                .background(if (row.unread) palette.primarySoft.copy(alpha = 0.5f) else Color.Transparent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = SsSpace.s2, vertical = SsSpace.s2)
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        val tint = hexColor(row.courseColor, palette.primary)
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), SsShape.control), contentAlignment = Alignment.Center) {
            Icon(row.kind.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$kind · ${row.courseName} · $ago",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.unread) Box(Modifier.size(8.dp).background(palette.primary, SsShape.chip))
    }
}

fun Material.icon(): ImageVector =
    when (kind) {
        MaterialKind.Drive -> Lucide.FileText
        MaterialKind.Youtube -> Lucide.Youtube
        MaterialKind.Form -> Lucide.ClipboardList
        MaterialKind.Link -> Lucide.Link2
    }

/** A material attachment: opens its URL (Drive / YouTube / link) outside the app. */
@Composable
fun MaterialRow(
    material: Material,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = SsTheme.palette
    val uri = LocalUriHandler.current
    val url = material.url
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(SsShape.control)
                .background(palette.surface2)
                .then(if (url != null) Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(url) } } else Modifier)
                .padding(horizontal = SsSpace.s3, vertical = SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        Icon(material.icon(), contentDescription = null, tint = palette.primaryFg, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                material.title,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1) }
        }
        if (url != null) Icon(Lucide.ExternalLink, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
    }
}

/**
 * Not-connected / needs-reauth / workspace-blocked / quota card (web `ConnectCard`). Calm and prominent: one
 * icon tile, specific Bangla copy, one primary action, and the privacy footer
 * "আমরা শুধু পড়ার অনুমতি চাই · Google-এর সঙ্গে যুক্ত নয়".
 */
@Composable
fun ClassroomConnectCard(
    copy: ConnectCopy,
    busy: Boolean,
    error: String?,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val (icon, tileBg, tileFg) =
        when (copy.problem) {
            ConnectProblem.None -> Triple(Lucide.Bell, palette.primarySoft, palette.primaryFg)
            ConnectProblem.NeedsReauth, ConnectProblem.Quota -> Triple(Lucide.TriangleAlert, palette.warningSoft, palette.warningFg)
            ConnectProblem.WorkspaceBlocked -> Triple(Lucide.ShieldAlert, palette.dangerSoft, palette.dangerFg)
        }
    SsCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(SsSpace.s5), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s4), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).background(tileBg, SsShape.card), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tileFg, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                    Text(copy.title.t(), style = MaterialTheme.typography.titleMedium, color = palette.text)
                    Text(copy.body.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                }
            }
            copy.help?.let { help ->
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(Str.GcBlockedWhy.t(), style = MaterialTheme.typography.labelLarge, color = palette.primaryFg)
                }
                if (showHelp) {
                    Text(
                        help.t(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        modifier = Modifier.fillMaxWidth().background(palette.surface2, SsShape.control).padding(SsSpace.s3),
                    )
                }
            }
            copy.cta?.let { cta ->
                SsButton(text = cta.t(), onClick = onConnect, loading = busy, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.dangerFg) }
        }
        HorizontalDivider(color = palette.border)
        Row(
            Modifier.fillMaxWidth().background(palette.bg).padding(horizontal = SsSpace.s5, vertical = SsSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        ) {
            Icon(Lucide.Lock, contentDescription = null, tint = palette.muted, modifier = Modifier.size(14.dp))
            Text(
                "${Str.HomeClassroomConnectPrivacy.t()} · ${Str.BrandDisclaimerGoogle.t()}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

/** Stateful connect card: drives [ConnectClassroomViewModel] and reports a successful connection. */
@Composable
fun ConnectClassroomHost(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "connect-classroom") { koin.get<ConnectClassroomViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    val latestOnConnected by rememberUpdatedState(onConnected)
    val justConnected = state.phase == ConnectPhase.Connected && state.message != null
    LaunchedEffect(justConnected) {
        if (justConnected) {
            vm.messageShown()
            latestOnConnected()
        }
    }
    ClassroomConnectCard(
        copy = state.copy,
        busy = state.busy,
        error = state.error?.t(),
        onConnect = vm::connect,
        modifier = modifier,
    )
}
