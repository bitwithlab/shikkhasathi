package com.shikkhasathi.core.model

import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.core.i18n.Str
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusChipTest {
    @Test
    fun eachStatusMapsToItsToneAndBanglaLabel() {
        val expected =
            mapOf(
                AssignmentStatus.Pending to (StatusTone.Warning to "বাকি"),
                AssignmentStatus.TurnedIn to (StatusTone.Success to "জমা হয়েছে"),
                AssignmentStatus.Graded to (StatusTone.Primary to "গ্রেড হয়েছে"),
                AssignmentStatus.Missing to (StatusTone.Danger to "মিস হয়েছে"),
            )
        AssignmentStatus.entries.forEach { status ->
            val spec = status.chipSpec()
            assertEquals(expected.getValue(status), spec.tone to spec.label.text(AppLocale.Bn), "status $status")
        }
    }

    @Test
    fun englishLabelsMatchClassroomWording() {
        assertEquals(
            listOf("Pending", "Turned in", "Graded", "Missing"),
            AssignmentStatus.entries.map { it.chipSpec().label.text(AppLocale.En) },
        )
    }

    @Test
    fun wireValuesRoundTripAndUnknownFallsBackToPending() {
        AssignmentStatus.entries.forEach { assertEquals(it, AssignmentStatus.fromWire(it.wire)) }
        assertEquals(AssignmentStatus.Pending, AssignmentStatus.fromWire("reclaimed_by_student"))
        assertEquals(AssignmentStatus.Pending, AssignmentStatus.fromWire(null))
        assertEquals(Str.StatusMissing, AssignmentStatus.fromWire("missing").chipSpec().label)
    }
}
