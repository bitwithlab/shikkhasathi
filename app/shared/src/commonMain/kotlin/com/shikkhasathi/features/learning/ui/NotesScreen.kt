package com.shikkhasathi.features.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.clockText
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.learning.presentation.NotesTab
import com.shikkhasathi.features.learning.presentation.NotesViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

/** নোট ও বুকমার্ক (drawer). Tap an entry → that lesson in the player. */
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenLesson: (courseId: String, lessonId: String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<NotesViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.NavNotes.t(), onBack = onBack)
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = palette.bg, contentColor = palette.primaryFg) {
            Tab(selected = state.tab == NotesTab.Notes, onClick = { vm.selectTab(NotesTab.Notes) }, text = { Text(Str.PlayerTabNotes.t()) })
            Tab(
                selected = state.tab == NotesTab.Bookmarks,
                onClick = { vm.selectTab(NotesTab.Bookmarks) },
                text = { Text(Str.NotesBookmarks.t()) }
            )
        }
        if (state.tab == NotesTab.Notes) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                modifier = Modifier.fillMaxWidth().padding(SsSpace.s4),
                placeholder = { Text(Str.NotesSearch.t(), color = palette.muted) },
                leadingIcon = { Icon(Lucide.Search, contentDescription = null, tint = palette.muted) },
                singleLine = true,
                shape = SsShape.control,
            )
            when (val notes = state.notes) {
                LoadState.Loading -> Loading()
                is LoadState.Failed -> ErrorState(message = notes.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
                is LoadState.Ready ->
                    if (notes.data.isEmpty()) {
                        EmptyState(
                            Lucide.NotebookPen,
                            Str.NotesEmptyTitle.t(),
                            Str.NotesEmptyBody.t(),
                            Modifier.padding(horizontal = SsSpace.s4)
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                            items(notes.data, key = { it.id }) { n ->
                                NoteRow(
                                    note = n,
                                    onEdit = null,
                                    onDelete = { vm.deleteNote(n.id) },
                                    subtitle = listOf(n.courseTitle, n.lessonTitle).filter { it.isNotBlank() }.joinToString(" · "),
                                    onOpen = { onOpenLesson(n.courseId, n.lessonId) },
                                )
                            }
                        }
                    }
            }
        } else {
            when (val bookmarks = state.bookmarks) {
                LoadState.Loading -> Loading()
                is LoadState.Failed ->
                    ErrorState(
                        message = bookmarks.message.t(),
                        onRetry = vm::retry,
                        modifier = Modifier.padding(SsSpace.s4)
                    )
                is LoadState.Ready ->
                    if (bookmarks.data.isEmpty()) {
                        EmptyState(
                            Lucide.Bookmark,
                            Str.NotesBookmarksEmpty.t(),
                            Str.NotesEmptyBody.t(),
                            Modifier.padding(horizontal = SsSpace.s4)
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                            items(bookmarks.data, key = { it.id }) { b ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(palette.surface, SsShape.control)
                                        .clickable(role = Role.Button) { onOpenLesson(b.courseId, b.lessonId) }
                                        .padding(SsSpace.s3),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
                                ) {
                                    b.timestampSec?.let { SsChip(text = clockText(it).t(), tone = StatusTone.Primary) }
                                    Column(Modifier.weight(1f)) {
                                        Text(b.lessonTitle, style = MaterialTheme.typography.bodyMedium, color = palette.text)
                                        Text(b.courseTitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
                                    }
                                    IconButton(onClick = { vm.deleteBookmark(b.id) }) {
                                        Icon(Lucide.Trash2, contentDescription = Str.CommonDelete.t(), tint = palette.muted)
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
        repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(64.dp), shape = SsShape.control) }
    }
}
