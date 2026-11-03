package com.shikkhasathi.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun keysAreUniqueAndDotted() {
        val keys = Str.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate i18n key")
        assertTrue(keys.all { Regex("^[a-z][A-Za-z]*(\\.[a-z][A-Za-z]*)+$").matches(it) }, "keys must look like web i18n keys")
    }

    @Test
    fun interpolatesWithBanglaDigits() {
        assertEquals("৫৯ সেকেন্ড পর আবার কোড পাঠাতে পারবেন", Str.AuthOtpResendIn.text(AppLocale.Bn, "seconds" to 59))
        assertEquals("You can resend in 59s", Str.AuthOtpResendIn.text(AppLocale.En, "seconds" to 59))
        assertEquals("আসসালামু আলাইকুম, রাফি", Str.HomeGreeting.text(AppLocale.Bn, "name" to "রাফি"))
    }

    @Test
    fun everyEntryHasBothLanguagesAndNoLeftoverPlaceholderMismatch() {
        Str.entries.forEach { s ->
            assertTrue(s.bn.isNotBlank() && s.en.isNotBlank(), s.key)
            val ph = Regex("\\{\\{(\\w+)}}")
            assertEquals(ph.findAll(s.bn).map { it.value }.toSet(), ph.findAll(s.en).map { it.value }.toSet(), s.key)
        }
    }
}
