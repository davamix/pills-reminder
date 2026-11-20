package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TreatmentStatusTest {
    private val id = Example.treatmentId

    private fun PillsData.statusAt(now: LocalDateTime) = status(id, nowAt(now))

    private val noEnd =
        Example.treatment.copy(
            medicines = listOf(Example.amoxicillin.copy(schedule = Example.amoxicillin.schedule.copy(duration = MedicineDuration.NoEnd))),
        )

    @Test
    fun `a treatment is Upcoming until its first take, then Active, then Finished once every take is Missed`() {
        val data = Example.data

        assertEquals(TreatmentStatus.UPCOMING, data.statusAt(nov(23, 7, 59)))
        assertEquals(TreatmentStatus.ACTIVE, data.statusAt(nov(23, 8)))
        assertEquals(TreatmentStatus.ACTIVE, data.statusAt(nov(30, 7, 59)))
        assertEquals(TreatmentStatus.FINISHED, data.statusAt(nov(30, 8)))
    }

    @Test
    fun `a treatment is Finished as soon as its last take is answered`() {
        val all =
            Example.treatment.medicines.flatMap { medicine ->
                val takes = MedicineTakes(medicine, null, emptyMap())
                takes.takes(LocalDateTime.MIN, LocalDateTime.MAX, nowAt(nov(23, 12))).map { it.key }.toList()
            }
        val data = Example.data.copy(answers = all.associateWith { Answer.Taken(it.plannedAt) })

        assertEquals(TreatmentStatus.FINISHED, data.statusAt(nov(30, 0, 1)))
        val withoutLast = data.copy(answers = data.answers - TakeKey(Example.amoxicillinId, nov(30, 0)))
        assertEquals(TreatmentStatus.ACTIVE, withoutLast.statusAt(nov(30, 0, 1)))
    }

    @Test
    fun `a treatment with a no-end medicine stays Active until it is ended`() {
        val data = PillsData(listOf(noEnd))

        assertEquals(TreatmentStatus.ACTIVE, data.statusAt(nov(23, 8).plusYears(5)))
        val ended = PillsData(listOf(noEnd.copy(endedAt = nov(25, 12))))
        assertEquals(TreatmentStatus.FINISHED, ended.statusAt(nov(25, 12)))
        assertEquals(TreatmentStatus.FINISHED, ended.statusAt(nov(25, 13)))
    }

    @Test
    fun `a treatment ended before its first take is Finished, not Upcoming`() {
        val data = PillsData(listOf(Example.treatment.copy(endedAt = nov(22, 12))))

        assertEquals(TreatmentStatus.FINISHED, data.statusAt(nov(22, 13)))
    }

    @Test
    fun `several treatments can be Active at the same time`() {
        val other =
            Example.treatment.copy(
                id = TreatmentId("other"),
                medicines = listOf(Example.atrovent.copy(id = MedicineId("atrovent-2"))),
            )
        val data = PillsData(listOf(Example.treatment, other))

        assertEquals(TreatmentStatus.ACTIVE, data.status(Example.treatmentId, nowAt(nov(24, 12))))
        assertEquals(TreatmentStatus.ACTIVE, data.status(TreatmentId("other"), nowAt(nov(24, 12))))
    }

    @Test
    fun `a Finished treatment stays in the data`() {
        assertEquals(TreatmentStatus.FINISHED, Example.data.statusAt(nov(30, 9)))
        assertEquals(listOf(Example.treatment), Example.data.treatments)
    }

    @Test
    fun `the first take is the earliest of its medicines, which can start at different times`() {
        val later = Example.atrovent.copy(schedule = schedule(nov(23, 12), 8, IntervalUnit.HOURS, MedicineDuration.Days(5)))
        val earlier = Example.amoxicillin.copy(schedule = schedule(nov(23, 10), 8, IntervalUnit.HOURS, MedicineDuration.Days(7)))
        val data = PillsData(listOf(Example.treatment.copy(medicines = listOf(later, earlier))))

        assertEquals(nov(23, 10), data.firstTake(id))
        assertEquals(TreatmentStatus.UPCOMING, data.statusAt(nov(23, 9, 59)))
        assertEquals(TreatmentStatus.ACTIVE, data.statusAt(nov(23, 10)))
    }

    @Test
    fun `an unknown treatment has no status and no first take`() {
        assertNull(Example.data.status(TreatmentId("unknown"), nowAt(nov(23, 12))))
        assertNull(Example.data.firstTake(TreatmentId("unknown")))
    }
}
