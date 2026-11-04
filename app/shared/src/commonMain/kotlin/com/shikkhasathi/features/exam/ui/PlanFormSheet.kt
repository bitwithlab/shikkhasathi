package com.shikkhasathi.features.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.EXAM_TYPES
import com.shikkhasathi.features.exam.presentation.PlanForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Exam start times offered as chips (university exams start on the hour or half hour). */
private val TIMES = listOf(9 to 0, 9 to 30, 10 to 0, 11 to 0, 14 to 0, 15 to 0)

/** "নতুন পরীক্ষা যোগ করুন": subject, code, type, date (Material date picker), start time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanFormSheet(
    form: PlanForm,
    onChange: ((PlanForm) -> PlanForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = SsTheme.palette
    var picking by remember { mutableStateOf(false) }
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.primarySoft,
            selectedLabelColor = palette.primaryFg
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SsSpace.s4, vertical = SsSpace.s2),
            verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
        ) {
            Text(Str.ExamAddPlan.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
            OutlinedTextField(
                value = form.subjectName,
                onValueChange = { v -> onChange { it.copy(subjectName = v.take(PlanForm.SUBJECT_MAX)) } },
                label = { Text(Str.ExamFormSubject.t()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = SsShape.control,
            )
            OutlinedTextField(
                value = form.courseCode,
                onValueChange = { v -> onChange { it.copy(courseCode = v.take(PlanForm.CODE_MAX)) } },
                label = { Text(Str.ExamFormCode.t()) },
                placeholder = { Text("CSE-2203") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = SsShape.control,
            )
            Text(Str.ExamFormType.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                EXAM_TYPES.forEach { type ->
                    FilterChip(
                        selected = form.examType == type,
                        onClick = { onChange { it.copy(examType = type) } },
                        label = { Text(Str.fromKey("examType.$type")?.t() ?: type) },
                        shape = SsShape.chip,
                        colors = chipColors,
                    )
                }
            }
            SsButton(
                text =
                    form.date?.let { UiText.DayMonth(it.day, it.month.number).t() + " " + UiText.Count(it.year).t() }
                        ?: Str.ExamFormPickDate.t(),
                onClick = { picking = true },
                variant = SsButtonVariant.Secondary,
                leadingIcon = Lucide.CalendarDays,
            )
            Text(Str.ExamFormTime.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                TIMES.forEach { (h, m) ->
                    FilterChip(
                        selected = form.hour == h && form.minute == m,
                        onClick = { onChange { it.copy(hour = h, minute = m) } },
                        label = { Text(UiText.ClockTime(h, m).t()) },
                        shape = SsShape.chip,
                        colors = chipColors,
                    )
                }
            }
            form.error?.let {
                Text(
                    it.t(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.dangerFg,
                    modifier =
                        Modifier.semantics {
                            liveRegion =
                                LiveRegionMode.Polite
                        }
                )
            }
            SsButton(
                text = Str.CommonSave.t(),
                onClick = onSubmit,
                loading = form.saving,
                modifier = Modifier.fillMaxWidth().padding(bottom = SsSpace.s6)
            )
        }
    }
    if (picking) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    pickerState.selectedDateMillis?.let { ms -> onChange { it.copy(date = utcDate(ms)) } }
                }) { Text(Str.CommonOk.t()) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(Str.CommonCancel.t()) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** The Material date picker reports the chosen day as UTC midnight. */
private fun utcDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
