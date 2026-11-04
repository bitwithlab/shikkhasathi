package com.shikkhasathi.features.downloads.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Video
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.sizeText
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.downloads.data.DownloadStatus
import com.shikkhasathi.features.downloads.data.OfflineKind
import com.shikkhasathi.features.downloads.data.OfflineValidity
import com.shikkhasathi.features.downloads.presentation.DownloadRowUi
import com.shikkhasathi.features.downloads.presentation.DownloadsViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

/** ডাউনলোড (drawer): encrypted offline files, their validity, delete. Tap a ready one → its lesson. */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenLesson: (courseId: String, lessonId: String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<DownloadsViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.NavDownloads.t(), onBack = onBack)
        if (state.rows.isEmpty()) {
            EmptyState(Lucide.Download, Str.DownloadsEmptyTitle.t(), Str.DownloadsEmptyBody.t(), Modifier.padding(horizontal = SsSpace.s4))
        } else {
            Text(
                Str.DownloadsTotal.t("size" to sizeText(state.totalBytes)),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = SsSpace.s4),
            )
            LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                items(state.rows, key = { it.lessonId }) { row ->
                    DownloadRow(row, onOpen = { onOpenLesson(row.courseId, row.lessonId) }, onDelete = { vm.remove(row.lessonId) })
                }
                item(key = "note") { Text(Str.DownloadsPrivacyNote.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted) }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = SsTheme.palette
    val (statusText, tone) =
        when {
            row.status == DownloadStatus.Downloading || row.status == DownloadStatus.Queued ->
                Str.DownloadsDownloading.t() to
                    StatusTone.Primary
            row.status == DownloadStatus.Failed -> Str.DownloadsFailed.t() to StatusTone.Danger
            row.validity == OfflineValidity.NeedsOnlineCheck -> Str.DownloadsNeedsOnline.t() to StatusTone.Warning
            row.validity == OfflineValidity.AccessEnded -> Str.DownloadsExpired.t() to StatusTone.Danger
            else -> Str.DownloadsReady.t() to StatusTone.Success
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(palette.surface, SsShape.control)
            .then(if (row.canOpen) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = listOf(row.title, row.courseTitle, statusText).joinToString(", ") }
            .padding(SsSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        Box(Modifier.size(40.dp).background(palette.primarySoft, SsShape.control), contentAlignment = Alignment.Center) {
            Icon(if (row.kind == OfflineKind.Video) Lucide.Video else Lucide.FileText, contentDescription = null, tint = palette.primaryFg)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 2)
            Text(row.courseTitle, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                SsChip(text = statusText, tone = tone)
                if (row.sizeBytes > 0) Text(sizeText(row.sizeBytes).t(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                row.accessUntil?.let {
                    Text(
                        Str.MyCoursesAccessUntil.t("date" to it),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                }
            }
            row.percent?.takeIf { row.status == DownloadStatus.Downloading }?.let { p ->
                LinearProgressIndicator(
                    progress = { p / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(SsShape.chip),
                    color = palette.primary,
                    trackColor = palette.surface2,
                    drawStopIndicator = {},
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(Lucide.Trash2, contentDescription = Str.CommonDelete.t(), tint = palette.muted) }
    }
}
