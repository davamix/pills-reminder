package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.assertInvariants
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.status
import io.github.davamix.pillsreminder.core.schedule.take
import io.github.davamix.pillsreminder.core.schedule.takesOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EndTreatmentTest {
    private val wednesday1200 = nowAt(nov(25, 12))

    /** Every take of both medicines before Wed 25 Nov 08:00, answered Taken. */
    private val answered: PillsData =
        Example.data.copy(
            answers =
                listOf(Example.amoxicillinId, Example.atroventId)
                    .flatMap { id ->
                        listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8), nov(24, 16), nov(25, 0)).map { TakeKey(id, it) }
                    }.associateWith { Answer.Taken(it.plannedAt) },
        )

    @Test
    fun `ending keeps the takes up to now, makes the Due one Missed at once, and the treatment Finished`() {
        val ended = checkNotNull(answered.endTreatment(Example.treatmentId, wednesday1200))

        assertEquals(nov(25, 12), ended.treatment(Example.treatmentId)?.endedAt)
        for (id in listOf(Example.amoxicillinId, Example.atroventId)) {
            val takes = checkNotNull(ended.takesOf(id))
            assertEquals(7L, takes.count)
            assertEquals(nov(25, 8), takes.last)
            assertEquals(TakeState.MISSED, ended.take(TakeKey(id, nov(25, 8)), wednesday1200)?.state)
            assertNull(takes.nextTake(wednesday1200))
        }
        assertEquals(TreatmentStatus.FINISHED, ended.status(Example.treatmentId, wednesday1200))
        assertEquals(answered.answers, ended.answers)
        assertInvariants(ended, wednesday1200)
    }

    @Test
    fun `a take marked Taken early is kept with its answer`() {
        val early = TakeKey(Example.amoxicillinId, nov(25, 16))
        val data =
            answered.copy(
                answers =
                    answered.answers + (TakeKey(Example.amoxicillinId, nov(25, 8)) to Answer.Taken(nov(25, 8))) +
                        (early to Answer.Taken(nov(25, 11))),
            )
        val ended = checkNotNull(data.endTreatment(Example.treatmentId, wednesday1200))
        val takes = checkNotNull(ended.takesOf(Example.amoxicillinId))

        assertEquals(8L, takes.count)
        assertEquals(nov(25, 16), takes.last)
        assertEquals(TakeState.TAKEN, ended.take(early, wednesday1200)?.state)
        assertEquals(7L, ended.takesOf(Example.atroventId)?.count)
        assertInvariants(ended, wednesday1200)
    }

    @Test
    fun `a no-end medicine gets a count and a last take`() {
        val noEnd = Example.amoxicillin.copy(schedule = Example.amoxicillin.schedule.copy(duration = MedicineDuration.NoEnd))
        val data = PillsData(listOf(Example.treatment.copy(medicines = listOf(noEnd))))
        val ended = checkNotNull(data.endTreatment(Example.treatmentId, wednesday1200))

        assertNull(data.takesOf(Example.amoxicillinId)?.count)
        assertEquals(7L, ended.takesOf(Example.amoxicillinId)?.count)
        assertEquals(nov(25, 8), ended.takesOf(Example.amoxicillinId)?.last)
    }

    @Test
    fun `the end is in whole minutes`() {
        val ended = checkNotNull(Example.data.endTreatment(Example.treatmentId, wednesday1200.withSecond(40)))

        assertEquals(nov(25, 12), ended.treatment(Example.treatmentId)?.endedAt)
    }

    @Test
    fun `a treatment can't be ended twice, nor one that doesn't exist`() {
        val ended = checkNotNull(Example.data.endTreatment(Example.treatmentId, wednesday1200))

        assertNull(ended.endTreatment(Example.treatmentId, nowAt(nov(26, 12))))
        assertNull(Example.data.endTreatment(TreatmentId("unknown"), wednesday1200))
    }

    @Test
    fun `a treatment ended before its first take has no takes left`() {
        val ended = checkNotNull(Example.data.endTreatment(Example.treatmentId, nowAt(nov(22, 12))))

        assertEquals(0L, ended.takesOf(Example.amoxicillinId)?.count)
        assertEquals(TreatmentStatus.FINISHED, ended.status(Example.treatmentId, nowAt(nov(24, 12))))
        assertEquals(emptyList<LocalDateTime>(), ended.answers.keys.map { it.plannedAt })
    }
}
