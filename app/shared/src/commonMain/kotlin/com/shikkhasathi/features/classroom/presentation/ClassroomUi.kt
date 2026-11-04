package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.time.dueLabel
import com.shikkhasathi.core.common.time.relativeTime
import com.shikkhasathi.core.common.time.timeLeft
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.Assignment
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.core.model.FeedItem
import com.shikkhasathi.core.model.FeedKind
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.features.auth.presentation.toUiText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Urgency stripe of an assignment row (web `urgency()` in classroom/parts.tsx). */
enum class Urgency { Danger, Warning, Neutral, Success }

private val DANGER_WITHIN = 24.hours
private val WARNING_WITHIN = 72.hours

fun urgencyOf(
    status: AssignmentStatus,
    due: Instant?,
    now: Instant,
): Urgency =
    when {
        status == AssignmentStatus.Missing -> Urgency.Danger
        status == AssignmentStatus.TurnedIn || status == AssignmentStatus.Graded -> Urgency.Success
        due == null -> Urgency.Neutral
        due - now < DANGER_WITHIN -> Urgency.Danger
        due - now < WARNING_WITHIN -> Urgency.Warning
        else -> Urgency.Neutral
    }

/** One assignment row, identical on Home, Assignments and the class stream (DESIGN_SYSTEM "Assignment Row"). */
data class AssignmentRowUi(
    val id: String,
    val title: String,
    val courseName: String,
    val courseColor: String,
    val status: AssignmentStatus,
    val dueLabel: UiText,
    /** "আর ৫ ঘণ্টা" for pending work, else null. */
    val timeLeft: UiText?,
    val urgency: Urgency,
    /** The full DTO, for the detail sheet. */
    val assignment: Assignment,
)

fun Assignment.toRowUi(now: Instant): AssignmentRowUi {
    val due = dueAt.toInstantOrNull()
    return AssignmentRowUi(
        id = id,
        title = title,
        courseName = courseName,
        courseColor = courseColor,
        status = status,
        dueLabel = due?.let { dueLabel(it, now) } ?: Str.GcNoDue.asUiText(),
        timeLeft = if (status == AssignmentStatus.Pending && due != null) timeLeft(due, now) else null,
        urgency = urgencyOf(status, due, now),
        assignment = this,
    )
}

/** One row of the নতুন আপডেট feed. */
data class FeedRowUi(
    val id: String,
    val kind: FeedKind,
    val kindLabel: Str,
    val title: String,
    val courseName: String,
    val courseColor: String,
    val timeAgo: UiText,
    val unread: Boolean,
    val link: String?,
)

fun FeedKind.label(): Str =
    when (this) {
        FeedKind.Assignment -> Str.GcKindAssignment
        FeedKind.Material -> Str.GcKindMaterial
        FeedKind.Announcement -> Str.GcKindAnnouncement
        FeedKind.Graded -> Str.GcKindGraded
    }

fun FeedItem.toRowUi(now: Instant): FeedRowUi =
    FeedRowUi(
        id = id,
        kind = kind,
        kindLabel = kind.label(),
        title = title,
        courseName = courseName,
        courseColor = courseColor,
        timeAgo = occurredAt.toInstantOrNull()?.let { relativeTime(it, now) } ?: UiText.Raw(""),
        unread = !read,
        link = alternateLink,
    )

/**
 * Bangla copy for Classroom failures (CLAUDE.md §0A "Failure copy"). [lastSyncAt] feeds the quota message
 * ("শেষ সিঙ্ক: ১০ মিনিট আগে").
 */
fun ApiError.toClassroomUiText(
    lastSyncAt: Instant?,
    now: Instant,
): UiText =
    when (code) {
        ApiError.CLASSROOM_REAUTH -> Str.GcReauthBody.asUiText()
        ApiError.CLASSROOM_WORKSPACE_BLOCKED -> Str.GcBlockedBody.asUiText()
        ApiError.CLASSROOM_QUOTA -> quotaText(lastSyncAt, now)
        ApiError.RATE_LIMITED -> Str.GcSyncRateLimited.asUiText()
        else -> toUiText()
    }

fun quotaText(
    lastSyncAt: Instant?,
    now: Instant,
): UiText = Str.GcQuota.asUiText("when" to (lastSyncAt?.let { relativeTime(it, now) } ?: Str.GcNotSynced.asUiText()))
