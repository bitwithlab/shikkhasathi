package com.shikkhasathi.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BdPhoneTest {
    @Test
    fun normalisesLikeTheServerContract() {
        assertEquals("+8801712345678", BdPhone.normalize("01712345678"))
        assertEquals("+8801712345678", BdPhone.normalize("01712-345678"))
        assertEquals("+8801712345678", BdPhone.normalize("8801712345678"))
        assertEquals("+8801712345678", BdPhone.normalize(" +880 1712 345678 "))
        assertEquals("+8801712345678", BdPhone.normalize("০১৭১২৩৪৫৬৭৮"))
    }

    @Test
    fun rejectsInvalidNumbers() {
        assertNull(BdPhone.normalize("01212345678"))
        assertNull(BdPhone.normalize("0171234567"))
        assertNull(BdPhone.normalize("hello"))
    }

    @Test
    fun prettyPrints() = assertEquals("01712-345678", BdPhone.pretty("+8801712345678"))
}
