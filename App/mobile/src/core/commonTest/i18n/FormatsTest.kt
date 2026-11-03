package com.shikkhasathi.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatsTest {
    private fun UiText.bn() = resolve(AppLocale.Bn)

    private fun UiText.en() = resolve(AppLocale.En)

    @Test
    fun durations() {
        assertEquals("১ মিনিট", durationText(20).bn())
        assertEquals("৪৫ মিনিট", durationText(45 * 60).bn())
        assertEquals("২ ঘণ্টা ১০ মিনিট", durationText(130 * 60).bn())
        assertEquals("3 h", durationText(3 * 3600).en())
    }

    @Test
    fun clocksAndMoney() {
        assertEquals("৫:০৯", clockText(309).bn())
        assertEquals("১:০৫:০৯", clockText(3909).bn())
        assertEquals("0:00", clockText(-4).en())
        assertEquals("৳৩৪৯", takaText(349).bn())
        assertEquals("৳1,499", takaText(1499).en())
    }

    @Test
    fun sizes() {
        assertEquals("৮৫০ KB", sizeText(850L * 1024).bn())
        assertEquals("2.4 MB", sizeText((2.4 * 1024 * 1024).toLong()).en())
        assertEquals("১.১ GB", sizeText((1.1 * 1024 * 1024 * 1024).toLong()).bn())
    }
}
