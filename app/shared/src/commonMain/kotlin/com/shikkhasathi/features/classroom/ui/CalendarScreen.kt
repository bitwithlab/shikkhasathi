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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
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
import com.shikkhasathi.core.designsystem.components.StatusChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.CalendarEventKind
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.classroom.presentation.CalendarDayUi
import com.shikkhasathi.features.classroom.presentation.CalendarEventRowUi
import com.shikkhasathi.features.classroom.presentation.CalendarUiState
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import kotlinx.datetime.LocalDate
import org.koin.compose.getKoin

/** Month grid with deadline (red) / exam (amber) dots; the selected day's list below. Days are Asia/Dhaka. */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<CalendarViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    CalendarContent(state, onBack, vm::previousMonth, vm::nextMonth, vm::goToToday, vm::select, vm::retry, onOpenLink)
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val palette = SsTheme.palette
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, bottom = SsSpace.s8),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        item(key = "header") { ScreenHeader(title = Str.NavCalendar.t(), onBack = onBack) }
        item(key = "month") {
            SsCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(SsSpace.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(Lucide.ChevronLeft, contentDescription = Str.CalendarPrevMonth.t(), tint = palette.text)
                    }
                    Text(
                        state.title.t(),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onToday) { Text(Str.DueToday.t(), color = palette.primaryFg) }
                    IconButton(onClick = onNext) {
                        Icon(Lucide.ChevronRight, contentDescription = Str.CalendarNextMonth.t(), tint = palette.text)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = SsSpace.s1)) {
                    state.weekdays.forEach { day ->
                        Text(
                            day.t(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                state.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            } else {
                                DayCell(day, onClick = { onSelect(day.date) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item(key = "selectedTitle") {
            Text(
                state.selectedTitle.t(),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.padding(top = SsSpace.s2).semantics { heading() },
            )
        }
        when (val load = state.load) {
            LoadState.Loading ->
                items(
                    2,
                    key = { "sk-$it" }
                ) { SkeletonBox(Modifier.fillMaxWidth().height(64.dp), shape = SsShape.control) }
            is LoadState.Failed -> item(key = "error") { ErrorState(message = load.message.t(), onRetry = onRetry) }
            is LoadState.Ready ->
                if (state.selectedEvents.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(icon = Lucide.CalendarCheck, title = Str.CalendarDayEmpty.t(), body = Str.CalendarDayEmptyBody.t())
                    }
                } else {
                    items(state.selectedEvents, key = { it.kind.name + it.id }) { EventRow(it, onOpen = { onOpenLink(it.link) }) }
                }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDayUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    val description = day.description.t()
    Column(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .padding(2.dp)
                .background(
                    when {
                        day.isSelected -> palette.primary
                        day.isToday -> palette.primarySoft
                        else -> Color.Transparent
                    },
                    SsShape.control,
                ).border(1.dp, if (day.isToday && !day.isSelected) palette.primary else Color.Transparent, SsShape.control)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    selected = day.isSelected
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            UiText.Count(day.dayOfMonth).t(),
            style = MaterialTheme.typography.bodyMedium.numeric,
            color = if (day.isSelected) palette.onPrimary else palette.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            if (day.deadlines >
                0
            ) {
                Box(Modifier.size(5.dp).background(if (day.isSelected) palette.onPrimary else palette.danger, SsShape.chip))
            }
            if (day.exams > 0) Box(Modifier.size(5.dp).background(palette.warning, SsShape.chip))
        }
    }
}

@Composable
private fun EventRow(
    event: CalendarEventRowUi,
    onOpen: () -> Unit,
) {
    val palette = SsTheme.palette
    val isExam = event.kind == CalendarEventKind.Exam
    SsCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (isExam) palette.warning else hexColor(event.color, palette.primary)))
            Column(Modifier.weight(1f).padding(SsSpace.s3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 2)
                Text(
                    listOfNotNull(event.time.t(), event.courseName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
            Box(Modifier.padding(end = SsSpace.s3)) {
                val status = event.status
                when {
                    isExam -> SsChip(text = Str.HomeLegendExam.t(), tone = StatusTone.Warning)
                    status != null -> StatusChip(status)
                }
            }
        }
    }
}
