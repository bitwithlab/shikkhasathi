package com.shikkhasathi.features.classroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.designsystem.LocalAppLocale
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.components.StatusChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.core.i18n.DateText
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.Urgency

/**
 * Assignment detail sheet (tap on any assignment row). Read-only: the one primary action deep-links into
 * Google Classroom via `alternateLink` ("Classroom-এ খুলুন"); we never submit on the student's behalf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSheet(
    row: AssignmentRowUi,
    onDismiss: () -> Unit,
) {
    val palette = SsTheme.palette
    val uri = LocalUriHandler.current
    val a = row.assignment
    val locale = LocalAppLocale.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SsSpace.s5)
                .padding(bottom = SsSpace.s6)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                Box(Modifier.size(10.dp).background(hexColor(a.courseColor, palette.primary), SsShape.chip))
                Text(a.courseName, style = MaterialTheme.typography.labelLarge, color = palette.muted)
            }
            Text(a.title, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                StatusChip(row.status)
                if (a.late) SsChip(text = Str.GcLate.t(), tone = StatusTone.Warning)
                row.timeLeft?.let {
                    SsChip(
                        text = it.t(),
                        tone =
                            if (row.urgency ==
                                Urgency.Danger
                            ) {
                                StatusTone.Danger
                            } else {
                                StatusTone.Warning
                            }
                    )
                }
            }
            DetailLine(Str.GcDue.t(), row.dueLabel.t())
            val grade = a.grade
            val max = a.maxPoints
            if (grade != null) {
                DetailLine(
                    Str.GcGrade.t(),
                    Str.GcGradeValue.t(
                        "grade" to points(grade, locale),
                        "max" to (max?.let { points(it, locale) } ?: "—")
                    )
                )
            } else if (max != null) {
                DetailLine(Str.GcPoints.t(), points(max, locale))
            }
            a.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.text)
            }
            if (a.materials.isNotEmpty()) {
                Text(Str.GcMaterials.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
                a.materials.forEach { m -> MaterialRow(m) }
            }
            Row(
                Modifier.fillMaxWidth().background(palette.surface2, SsShape.control).padding(SsSpace.s3),
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
            ) {
                Icon(Lucide.Info, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
                Text(Str.GcReadOnlyNote.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            a.alternateLink?.let { link ->
                SsButton(
                    text = Str.GcOpenInClassroom.t(),
                    onClick = { runCatching { uri.openUri(link) } },
                    leadingIcon = Lucide.ExternalLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    val palette = SsTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
        Icon(Lucide.Clock, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
        Text("$label:", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium.numeric, color = palette.text)
    }
}

/** 18.0 → "১৮", 7.5 → "৭.৫". */
private fun points(
    value: Double,
    locale: AppLocale,
): String = DateText.digits(locale, if (value % 1.0 == 0.0) value.toLong().toString() else value.toString())
