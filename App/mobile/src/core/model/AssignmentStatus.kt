package com.shikkhasathi.core.model

import com.shikkhasathi.core.i18n.Str

/**
 * Google Classroom submission status as shown on assignment rows and the home dashboard
 * (CLAUDE.md §0A: Pending / Turned in / Graded / Missing).
 */
enum class AssignmentStatus(val wire: String) {
    Pending("pending"),
    TurnedIn("turned_in"),
    Graded("graded"),
    Missing("missing"),
    ;

    companion object {
        /** Unknown values fall back to [Pending] so a new server status never crashes the dashboard. */
        fun fromWire(value: String?): AssignmentStatus = entries.firstOrNull { it.wire == value } ?: Pending
    }
}

/** Semantic colour family of a chip; the design system maps each tone to token colours. */
enum class StatusTone { Primary, Success, Warning, Danger, Neutral }

/** The single source of truth for how a status chip looks: tone + label. */
data class StatusChipSpec(
    val tone: StatusTone,
    val label: Str,
)

fun AssignmentStatus.chipSpec(): StatusChipSpec =
    when (this) {
        AssignmentStatus.Pending -> StatusChipSpec(StatusTone.Warning, Str.StatusPending)
        AssignmentStatus.TurnedIn -> StatusChipSpec(StatusTone.Success, Str.StatusTurnedIn)
        AssignmentStatus.Graded -> StatusChipSpec(StatusTone.Primary, Str.StatusGraded)
        AssignmentStatus.Missing -> StatusChipSpec(StatusTone.Danger, Str.StatusMissing)
    }
