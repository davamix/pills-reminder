package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.MADRID
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What a reminder says (RM-1, TR-6), and its fingerprint (P5-D5). */
class ReminderContentTest {
    private val amoxicillin1600 = TakeKey(StoreExample.amoxicillinId, nov(23, 16))

    private fun withFood(medicine: Medicine) =
        if (medicine.id == StoreExample.amoxicillinId) medicine.copy(instructions = "with food") else medicine

    private val withFood = PillsData(listOf(StoreExample.treatment.copy(medicines = StoreExample.treatment.medicines.map(::withFood))))

    @Test
    fun `a reminder names the medicine, the dose, the treatment and the instructions`() {
        val content = checkNotNull(withFood.reminderContent(amoxicillin1600, MADRID))

        assertEquals(amoxicillin1600, content.key)
        assertEquals("Amoxicillin", content.medicine)
        assertEquals("1 capsule", content.dose)
        assertEquals("Example", content.treatment)
        assertEquals("with food", content.instructions)
        assertEquals(Instant.parse("2026-11-23T15:00:00Z"), content.dueAt)
    }

    @Test
    fun `without instructions they are empty`() {
        val content = checkNotNull(PillsData(listOf(StoreExample.treatment)).reminderContent(amoxicillin1600, MADRID))

        assertEquals("", content.instructions)
    }

    @Test
    fun `the time is the planned time in the given zone, moved forward in a daylight saving gap`() {
        val data = PillsData(listOf(StoreExample.treatment))

        assertEquals(Instant.parse("2026-11-23T16:00:00Z"), data.reminderContent(amoxicillin1600, ZoneId.of("Europe/London"))?.dueAt)
        val inTheGap = TakeKey(StoreExample.amoxicillinId, LocalDateTime.of(2026, 3, 29, 2, 30))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), data.reminderContent(inTheGap, MADRID)?.dueAt)
    }

    @Test
    fun `a take whose medicine is gone has no content`() {
        assertNull(withFood.reminderContent(TakeKey(MedicineId("removed"), nov(23, 16)), MADRID))
    }

    @Test
    fun `the fingerprint changes with every part the reminder shows, and holds none of it`() {
        val content = checkNotNull(withFood.reminderContent(amoxicillin1600, MADRID))
        val changed =
            listOf(
                content.copy(medicine = "Amoxicillin 500"),
                content.copy(dose = "2 capsules"),
                content.copy(treatment = "Example 2"),
                content.copy(instructions = ""),
                content.copy(dueAt = content.dueAt.plusSeconds(60)),
            )

        assertEquals(content.fingerprint, content.copy().fingerprint)
        for (other in changed) assertNotEquals(content.fingerprint, other.fingerprint)
        assertTrue(content.fingerprint.matches(Regex("[0-9a-f]{24}")), content.fingerprint)
        for (text in listOf("Amoxicillin", "capsule", "Example", "food")) assertFalse(text in content.fingerprint)
    }
}
