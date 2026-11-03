package com.shikkhasathi.features.classroom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.features.classroom.presentation.ASSIGNMENT_FILTERS
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.AssignmentsUiState
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** All assignments with status and class filters (instant, on-device). */
@Composable
fun AssignmentsScreen(
    courseId: String?,
    onBack: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "assignments-$courseId") { koin.get<AssignmentsViewModel> { parametersOf(courseId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    AssignmentsContent(state, onBack, vm::setStatus, vm::setCourse, vm::clearFilters, vm::retry)
}

@Composable
fun AssignmentsContent(
    state: AssignmentsUiState,
    onBack: () -> Unit,
    onStatus: (AssignmentFilter) -> Unit,
    onCourse: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
) {
    var sheet by remember { mutableStateOf<AssignmentRowUi?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.NavAssignments.t(), onBack = onBack)
        LazyRow(
            contentPadding = PaddingValues(horizontal = SsSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        ) {
            items(ASSIGNMENT_FILTERS, key = { it.first.wire }) { (filter, label) ->
                SsFilterChip(label = label.t(), selected = state.status == filter, onClick = { onStatus(filter) })
            }
        }
        if (state.classes.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SsSpace.s4),
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
            ) {
                item(
                    key = "all"
                ) { SsFilterChip(Str.AssignmentsFilterAllClasses.t(), state.courseId == null, onClick = { onCourse(null) }) }
                items(state.classes, key = { it.id }) { c ->
                    SsFilterChip(label = c.name, selected = state.courseId == c.id, onClick = { onCourse(c.id) })
                }
            }
        }
        when (val rows = state.rows) {
            LoadState.Loading ->
                Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                    repeat(5) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), shape = SsShape.control) }
                }
            is LoadState.Failed -> ErrorState(message = rows.message.t(), onRetry = onRetry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready ->
                if (rows.data.isEmpty()) {
                    EmptyState(
                        icon = Lucide.ClipboardList,
                        title = if (state.isFiltered) Str.AssignmentsEmptyTitle.t() else Str.HomeAllDone.t(),
                        body = if (state.isFiltered) Str.AssignmentsEmptyBody.t() else Str.HomeAllDoneBody.t(),
                        actionLabel = if (state.isFiltered) Str.AssignmentsClearFilters.t() else null,
                        onAction = if (state.isFiltered) onClearFilters else null,
                        modifier = Modifier.padding(horizontal = SsSpace.s4),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s3, bottom = SsSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(SsSpace.s2),
                    ) {
                        items(rows.data, key = { it.id }) { row -> AssignmentRow(row, onClick = { sheet = row }) }
                    }
                }
        }
    }
    sheet?.let { AssignmentSheet(row = it, onDismiss = { sheet = null }) }
}

/** Filter chip in our tokens (selected = primary-soft). M3 gives it a 48dp touch target. */
@Composable
fun SsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = SsTheme.palette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        shape = SsShape.chip,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = palette.surface,
                labelColor = palette.text,
                selectedContainerColor = palette.primarySoft,
                selectedLabelColor = palette.primaryFg,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = palette.border,
                selectedBorderColor = palette.primary,
            ),
    )
}
