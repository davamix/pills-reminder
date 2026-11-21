package io.github.davamix.pillsreminder.debug

import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.validate
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The debug tools' test treatments (D-15, P5 SPEC §4.3.9). */
class TestSchedulesTest {
    private val now = nowAt(nov(23, 16, 7)).withSecond(42)

    /** The duration of the only medicine of the only treatment. */
    private fun PillsData.duration() =
        treatments
            .single()
            .medicines
            .single()
            .schedule.duration

    @Test
    fun `a test treatment is valid with minutes allowed, and only with them`() {
        val draft = TestSchedules.draft(TestSettings(), now)

        assertEquals(emptyList<Any>(), draft.validate(allowMinutes = true))
        assertFalse(draft.validate(allowMinutes = false).isEmpty())
    }

    @Test
    fun `its first take is so many whole minutes after now, every so many minutes, for a day`() {
        val draft = TestSchedules.draft(TestSettings(intervalMinutes = 45, firstInMinutes = 2), now)

        val medicine = draft.medicines.single()
        assertEquals("Test", draft.name)
        assertEquals("Test A", medicine.name)
        assertEquals("1 tablet", medicine.dose)
        assertEquals(nov(23, 16, 9), medicine.firstTake)
        assertEquals(45, medicine.intervalAmount)
        assertEquals(IntervalUnit.MINUTES, medicine.intervalUnit)
        val saved = PillsData().saveTreatment(draft, now)
        assertEquals(MedicineDuration.Days(1), saved.duration())
        assertEquals(32L, saved.takesOf(medicine.id)?.count, "24 h every 45 min")
    }

    @Test
    fun `a first take in 0 minutes is now, and two medicines fall due together`() {
        val draft = TestSchedules.draft(TestSettings(firstInMinutes = 0, twoMedicines = true), now)

        assertEquals(listOf("Test A", "Test B"), draft.medicines.map { it.name })
        assertEquals(setOf(nov(23, 16, 7)), draft.medicines.map { it.firstTake }.toSet())
    }

    @Test
    fun `with no end it has no last take`() {
        val draft = TestSchedules.draft(TestSettings(noEnd = true), now)
        val saved = PillsData().saveTreatment(draft, now)

        assertEquals(MedicineDuration.NoEnd, saved.duration())
    }

    @Test
    fun `its IDs are new each time and start with the prefix, which tells test treatments apart`() {
        val first = TestSchedules.draft(TestSettings(twoMedicines = true), now)
        val second = TestSchedules.draft(TestSettings(twoMedicines = true), now)
        val saved = PillsData().saveTreatment(first, now)

        assertNotEquals(first.id, second.id)
        assertTrue((listOf(first.id.value) + first.medicines.map { it.id.value }).all { it.startsWith(TestSchedules.PREFIX) })
        assertEquals(2, first.medicines.distinctBy { it.id }.size)
        val treatment = saved.treatments.single()
        assertTrue(TestSchedules.isTest(treatment))
        assertFalse(TestSchedules.isTest(treatment.copy(id = TreatmentId("x"))))
    }

    @Test
    fun `an interval under 1 minute or a first take in the past is refused`() {
        assertThrows<IllegalArgumentException> { TestSchedules.draft(TestSettings(intervalMinutes = 0), now) }
        assertThrows<IllegalArgumentException> { TestSchedules.draft(TestSettings(firstInMinutes = -1), now) }
    }
}
