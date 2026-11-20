package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.nov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeleteTreatmentTest {
    private val otherMedicine = Example.atrovent.copy(id = MedicineId("atrovent-2"))
    private val other = Example.treatment.copy(id = TreatmentId("other"), medicines = listOf(otherMedicine))
    private val otherAnswer = TakeKey(otherMedicine.id, nov(23, 8)) to Answer.Taken(nov(23, 8))

    private val data =
        PillsData(
            treatments = listOf(Example.treatment, other),
            answers =
                mapOf(
                    TakeKey(Example.amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8)),
                    TakeKey(Example.atroventId, nov(23, 8)) to Answer.Skipped,
                    otherAnswer,
                ),
        )

    @Test
    fun `deleting a treatment removes it and every answer of its medicines, and nothing else`() {
        val deleted = checkNotNull(data.deleteTreatment(Example.treatmentId))

        assertEquals(listOf(other), deleted.treatments)
        assertEquals(mapOf(otherAnswer), deleted.answers)
    }

    @Test
    fun `a treatment that doesn't exist can't be deleted`() {
        assertNull(data.deleteTreatment(TreatmentId("unknown")))
    }
}
