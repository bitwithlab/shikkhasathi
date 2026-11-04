package com.shikkhasathi.features.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.clockText
import com.shikkhasathi.core.i18n.durationText
import com.shikkhasathi.core.model.LessonType
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.core.model.Question
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.learning.presentation.LessonNotesViewModel
import com.shikkhasathi.features.learning.presentation.LessonQaViewModel
import com.shikkhasathi.features.learning.presentation.LessonRowUi
import com.shikkhasathi.features.learning.presentation.OutlineUi
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

// ---------------------------------------------------------------- লেসন

@Composable
fun LessonsPanel(
    outline: OutlineUi,
    currentLessonId: String?,
    onSelect: (String) -> Unit,
) {
    val palette = SsTheme.palette
    val progress = Str.HomeProgress.t("p" to outline.progressPercent)
    LazyColumn(contentPadding = PaddingValues(bottom = SsSpace.s8)) {
        item(key = "progress") {
            Column(Modifier.padding(SsSpace.s4)) {
                Text(
                    Str.PlayerProgress.t("done" to outline.completedLessons, "total" to outline.totalLessons) + " · " + progress,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.successFg,
                )
                LinearProgressIndicator(
                    progress = { outline.progressPercent / 100f },
                    modifier =
                        Modifier.fillMaxWidth().padding(top = SsSpace.s2).height(6.dp).clip(SsShape.chip).semantics {
                            contentDescription =
                                progress
                        },
                    color = palette.success,
                    trackColor = palette.surface2,
                    drawStopIndicator = {},
                )
            }
        }
        outline.sections.forEach { section ->
            item(key = "s-${section.id}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    modifier = Modifier.padding(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s3),
                )
            }
            section.chapters.forEach { chapter ->
                item(key = "c-${chapter.id}") {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.muted,
                        modifier = Modifier.padding(horizontal = SsSpace.s4, vertical = SsSpace.s1),
                    )
                }
                items(chapter.lessons, key = { it.id }) { row -> LessonRow(row, row.id == currentLessonId, onClick = { onSelect(row.id) }) }
            }
        }
    }
}

/** Lesson Row (shared anatomy with web): state icon · title · ⭐ important · duration; current row highlighted. */
@Composable
private fun LessonRow(
    row: LessonRowUi,
    current: Boolean,
    onClick: () -> Unit,
) {
    val palette = SsTheme.palette
    val state =
        when {
            row.locked -> Str.LessonLocked.t()
            row.completed -> Str.PlayerCompleted.t()
            else -> ""
        }
    val duration = durationText(row.durationSec).t()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (current) palette.primarySoft else palette.bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = current
                contentDescription = listOf(row.title, duration, state).filter { it.isNotEmpty() }.joinToString(", ")
            }.padding(horizontal = SsSpace.s4, vertical = SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        val (icon, tint) =
            when {
                row.locked -> Lucide.Lock to palette.muted
                row.completed -> Lucide.CircleCheck to palette.success
                row.type == LessonType.Video -> Lucide.CirclePlay to palette.primary
                else -> Lucide.FileText to palette.primary
            }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.locked) palette.muted else palette.text,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        if (row.important) Icon(Lucide.Star, contentDescription = null, tint = palette.warning, modifier = Modifier.size(16.dp))
        if (row.preview && row.locked.not()) SsChip(text = Str.LessonPreview.t(), tone = StatusTone.Success)
        if (row.durationSec > 0) Text(duration, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

// ---------------------------------------------------------------- নোট

@Composable
fun NotesPanel(
    lessonId: String,
    currentPositionSec: () -> Int?,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "notes:$lessonId") { koin.get<LessonNotesViewModel> { parametersOf(lessonId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
        item(key = "editor") {
            Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = vm::onDraft,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(Str.NotesPlaceholder.t(), color = palette.muted) },
                    minLines = 2,
                    shape = SsShape.control,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.editingId == null) {
                        Checkbox(
                            checked = state.attachTime,
                            onCheckedChange = vm::setAttachTime,
                            colors = CheckboxDefaults.colors(checkedColor = palette.primary),
                        )
                        Text(
                            Str.NotesAttachTime.t(),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.text,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.bookmark(currentPositionSec()) }) {
                            Icon(Lucide.Bookmark, contentDescription = Str.NotesBookmark.t(), tint = palette.primaryFg)
                        }
                    } else {
                        TextButton(onClick = vm::cancelEdit) { Text(Str.CommonCancel.t(), color = palette.muted) }
                        Box(Modifier.weight(1f))
                    }
                    SsButton(
                        text = Str.CommonSave.t(),
                        onClick = { vm.save(currentPositionSec()) },
                        loading = state.saving,
                        enabled = state.draft.isNotBlank(),
                    )
                }
                state.message?.let { Text(it.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted) }
            }
        }
        when (val notes = state.notes) {
            LoadState.Loading -> item(key = "loading") { SkeletonBox(Modifier.fillMaxWidth().height(64.dp), shape = SsShape.control) }
            is LoadState.Failed -> item(key = "error") { ErrorState(message = notes.message.t(), onRetry = vm::retry) }
            is LoadState.Ready ->
                if (notes.data.isEmpty()) {
                    item(
                        key = "empty"
                    ) { EmptyState(icon = Lucide.NotebookPen, title = Str.NotesLessonEmptyTitle.t(), body = Str.NotesLessonEmptyBody.t()) }
                } else {
                    items(
                        notes.data,
                        key = { it.id }
                    ) { note -> NoteRow(note, onEdit = { vm.startEdit(note) }, onDelete = { vm.delete(note.id) }) }
                }
        }
    }
}

@Composable
fun NoteRow(
    note: Note,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    subtitle: String? = null,
    onOpen: (() -> Unit)? = null,
) {
    val palette = SsTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.surface, SsShape.control)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(SsSpace.s3),
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                note.timestampSec?.let { SsChip(text = clockText(it).t(), tone = StatusTone.Primary) }
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1) }
            }
            Text(note.body, style = MaterialTheme.typography.bodyMedium, color = palette.text)
        }
        if (onEdit !=
            null
        ) {
            IconButton(onClick = onEdit) { Icon(Lucide.Pencil, contentDescription = Str.CommonEdit.t(), tint = palette.muted) }
        }
        IconButton(onClick = onDelete) { Icon(Lucide.Trash2, contentDescription = Str.CommonDelete.t(), tint = palette.muted) }
    }
}

// ---------------------------------------------------------------- প্রশ্নোত্তর

@Composable
fun QaPanel(
    lessonId: String,
    currentPositionSec: () -> Int?,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "qa:$lessonId") { koin.get<LessonQaViewModel> { parametersOf(lessonId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    LazyColumn(contentPadding = PaddingValues(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
        item(key = "ask") {
            Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                val draftError = state.draftError
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = vm::onDraft,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(Str.QaPlaceholder.t(), color = palette.muted) },
                    minLines = 2,
                    isError = draftError != null,
                    supportingText = draftError?.let { { Text(it.t()) } },
                    shape = SsShape.control,
                )
                SsButton(
                    text = Str.QaAsk.t(),
                    onClick = { vm.ask(currentPositionSec()) },
                    loading = state.asking,
                    variant = SsButtonVariant.Secondary
                )
                state.message?.let { Text(it.t(), style = MaterialTheme.typography.bodySmall, color = palette.successFg) }
            }
        }
        when (val qs = state.questions) {
            LoadState.Loading -> item(key = "loading") { SkeletonBox(Modifier.fillMaxWidth().height(80.dp), shape = SsShape.control) }
            is LoadState.Failed -> item(key = "error") { ErrorState(message = qs.message.t(), onRetry = vm::retry) }
            is LoadState.Ready ->
                if (qs.data.isEmpty()) {
                    item(
                        key = "empty"
                    ) { EmptyState(icon = Lucide.MessageCircle, title = Str.QaEmptyTitle.t(), body = Str.QaEmptyBody.t()) }
                } else {
                    items(qs.data, key = { it.id }) { QuestionCard(it) }
                    if (state.nextCursor != null) {
                        item(key = "more") {
                            TextButton(
                                onClick = vm::loadMore,
                                enabled = !state.loadingMore
                            ) { Text(Str.CommonLoadMore.t(), color = palette.primaryFg) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun QuestionCard(q: Question) {
    val palette = SsTheme.palette
    Column(
        Modifier.fillMaxWidth().background(palette.surface, SsShape.control).padding(SsSpace.s3),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s1)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(q.authorName, style = MaterialTheme.typography.labelMedium, color = palette.muted)
            q.timestampSec?.let { SsChip(text = clockText(it).t(), tone = StatusTone.Primary) }
            if (q.status == "answered") SsChip(text = Str.QaAnswered.t(), tone = StatusTone.Success)
        }
        Text(q.body, style = MaterialTheme.typography.bodyMedium, color = palette.text)
        q.answers.forEach { a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SsSpace.s3,
                        top = SsSpace.s1
                    ).background(palette.surface2, SsShape.small)
                    .padding(SsSpace.s2)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                    Text(a.authorName, style = MaterialTheme.typography.labelMedium, color = palette.text)
                    if (a.isSeller) SsChip(text = Str.QaTeacher.t(), tone = StatusTone.Primary)
                }
                Text(a.body, style = MaterialTheme.typography.bodyMedium, color = palette.text)
            }
        }
    }
}
