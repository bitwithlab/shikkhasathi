package com.shikkhasathi.core.common.time

import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Every "which day is it" decision in the app uses Asia/Dhaka, the same zone the server buckets due groups and
 * the week strip in (dashboard.service.ts). A student travelling abroad still sees their university's days.
 */
val DhakaZone: TimeZone = TimeZone.of("Asia/Dhaka")

/** Parses an ISO-8601 timestamp with offset (`2026-11-03T17:59:00.000Z`), or null for null/garbage. */
fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun Instant.dhakaDateTime(): LocalDateTime = toLocalDateTime(DhakaZone)

fun Instant.dhakaDate(): LocalDate = dhakaDateTime().date

/** `YYYY-MM-DD` (WeekDay.date) → LocalDate, or null. */
fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private const val DAYS_IN_WEEK = 7
private const val HOURS_SHOWN_AS_LEFT = 48

/** "এইমাত্র" · "২ মিনিট আগে" · "৩ ঘণ্টা আগে" · "২ দিন আগে" · older than a week → "১২ নভেম্বর". Future times read "এইমাত্র". */
fun relativeTime(
    then: Instant,
    now: Instant,
): UiText {
    val ago = now - then
    return when {
        ago < 1.minutes -> Str.TimeJustNow.asUiText()
        ago < 1.hours -> Str.TimeMinutesAgo.asUiText("n" to ago.inWholeMinutes)
        ago < 24.hours -> Str.TimeHoursAgo.asUiText("n" to ago.inWholeHours)
        ago.inWholeDays < DAYS_IN_WEEK -> Str.TimeDaysAgo.asUiText("n" to ago.inWholeDays)
        else -> then.dhakaDate().let { UiText.DayMonth(it.day, it.month.number) }
    }
}

/** Header sync label: "সিঙ্ক হয়েছে ২ মিনিট আগে" / "সিঙ্ক হচ্ছে…" / "এখনো সিঙ্ক হয়নি". */
fun syncLabel(
    lastSyncAt: Instant?,
    now: Instant,
    syncing: Boolean,
): UiText =
    when {
        syncing -> Str.GcSyncing.asUiText()
        lastSyncAt == null -> Str.GcNotSynced.asUiText()
        else -> Str.GcSyncedAgo.asUiText("when" to relativeTime(lastSyncAt, now))
    }

/** "আজ রাত ১১:৫৯" · "আগামীকাল সকাল ১০:০০" · "গতকাল …" · "বৃহঃ, ১২ নভেম্বর, বিকাল ৪:০০" (web `dueLabel`). */
fun dueLabel(
    due: Instant,
    now: Instant,
): UiText {
    val local = due.dhakaDateTime()
    val time = UiText.ClockTime(local.hour, local.minute)
    return when (now.dhakaDate().daysUntil(local.date)) {
        0 -> Str.DueTodayAt.asUiText("time" to time)
        1 -> Str.DueTomorrowAt.asUiText("time" to time)
        -1 -> Str.DueYesterdayAt.asUiText("time" to time)
        else ->
            Str.DueDateAt.asUiText(
                "day" to UiText.Weekday(local.date.dayOfWeek.isoDayNumber, short = true),
                "date" to UiText.DayMonth(local.date.day, local.date.month.number),
                "time" to time,
            )
    }
}

/** "আর ৪৫ মিনিট" · "আর ৫ ঘণ্টা" · "৩ দিন বাকি"; null once the time has passed. */
fun timeLeft(
    due: Instant,
    now: Instant,
): UiText? {
    val left = due - now
    return when {
        !left.isPositive() -> null
        left < 1.hours -> Str.DueMinutesLeft.asUiText("n" to maxOf(1, left.inWholeMinutes))
        left.inWholeHours < HOURS_SHOWN_AS_LEFT -> Str.DueHoursLeft.asUiText("n" to left.inWholeHours)
        else -> Str.DueDaysLeft.asUiText("n" to left.inWholeDays)
    }
}

/** The seven Dhaka days starting at [start]. */
fun weekFrom(start: LocalDate): List<LocalDate> = (0 until DAYS_IN_WEEK).map { start.plus(DatePeriod(days = it)) }
