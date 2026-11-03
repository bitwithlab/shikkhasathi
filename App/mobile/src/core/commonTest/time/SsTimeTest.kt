package com.shikkhasathi.core.common.time

import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.core.i18n.toBanglaDigits
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SsTimeTest {
    // Tuesday 3 Nov 2026, 10:00 Dhaka.
    private val now = Instant.parse("2026-11-03T04:00:00Z")

    @Test
    fun relativeTimeInBanglaDigits() {
        assertEquals("এইমাত্র", relativeTime(now - 20.seconds, now).resolve())
        assertEquals("২ মিনিট আগে", relativeTime(now - 2.minutes, now).resolve())
        assertEquals("৫৯ মিনিট আগে", relativeTime(now - 59.minutes, now).resolve())
        assertEquals("৩ ঘণ্টা আগে", relativeTime(now - 3.hours, now).resolve())
        assertEquals("৬ দিন আগে", relativeTime(now - 6.days, now).resolve())
        assertEquals("১৮ অক্টোবর", relativeTime(now - 16.days, now).resolve())
        assertEquals("এইমাত্র", relativeTime(now + 5.minutes, now).resolve(), "clock skew never shows negative")
        assertEquals("2 min ago", relativeTime(now - 2.minutes, now).resolve(AppLocale.En))
    }

    @Test
    fun syncLabelStates() {
        assertEquals("সিঙ্ক হয়েছে ২ মিনিট আগে", syncLabel(now - 2.minutes, now, syncing = false).resolve())
        assertEquals("সিঙ্ক হচ্ছে…", syncLabel(now, now, syncing = true).resolve())
        assertEquals("এখনো সিঙ্ক হয়নি", syncLabel(null, now, syncing = false).resolve())
    }

    @Test
    fun dueLabelsUseDhakaDays() {
        assertEquals("আজ রাত ১১:৫৯", dueLabel(Instant.parse("2026-11-03T17:59:00Z"), now).resolve())
        // 00:30 on the 4th in Dhaka, although still the 3rd in UTC.
        assertEquals("আগামীকাল রাত ১২:৩০", dueLabel(Instant.parse("2026-11-03T18:30:00Z"), now).resolve())
        assertEquals("গতকাল বিকাল ৪:০০", dueLabel(Instant.parse("2026-11-02T10:00:00Z"), now).resolve())
        assertEquals("বৃহঃ, ১২ নভেম্বর, সকাল ১০:০০", dueLabel(Instant.parse("2026-11-12T04:00:00Z"), now).resolve())
        assertEquals("Tomorrow 12:30 AM", dueLabel(Instant.parse("2026-11-03T18:30:00Z"), now).resolve(AppLocale.En))
    }

    @Test
    fun timeLeftForUrgency() {
        assertEquals("আর ৪৫ মিনিট", timeLeft(now + 45.minutes, now)?.resolve())
        assertEquals("আর ১ মিনিট", timeLeft(now + 10.seconds, now)?.resolve())
        assertEquals("আর ৪৭ ঘণ্টা", timeLeft(now + 47.hours, now)?.resolve())
        assertEquals("৩ দিন বাকি", timeLeft(now + 3.days + 2.hours, now)?.resolve())
        assertNull(timeLeft(now - 1.minutes, now))
    }

    @Test
    fun dhakaDateAndDigits() {
        assertEquals(LocalDate(2026, 11, 4), Instant.parse("2026-11-03T18:00:00Z").dhakaDate())
        assertEquals(LocalDate(2026, 11, 3), Instant.parse("2026-11-03T17:59:59Z").dhakaDate())
        assertNull("not a date".toInstantOrNull())
        assertNull(null.toInstantOrNull())
        assertEquals("০১২৩৪৫৬৭৮৯ CSE-২২০৩", toBanglaDigits("0123456789 CSE-2203"))
        assertEquals(7, weekFrom(LocalDate(2026, 11, 3)).size)
    }
}
